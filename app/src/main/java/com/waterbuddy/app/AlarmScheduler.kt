package com.waterbuddy.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * பேட்டரி சேமிப்பு: background service எதுவும் ஓடாது.
 * Android-ஓட AlarmManager-கிட்ட "இந்த நேரத்துல எழுப்பு"ன்னு சொல்லிட்டு app தூங்கிடும்.
 * setAlarmClock() — Doze / battery saver எல்லாத்தையும் தாண்டி சரியான நேரத்துக்கு வரும்.
 */
object AlarmScheduler {
    const val ACTION_ALARM = "com.waterbuddy.app.ALARM"
    const val EXTRA_ID = "id"
    const val EXTRA_TEST = "test"
    const val EXTRA_SNOOZE = "snooze"
    private const val SNOOZE_BASE = 500_000
    private const val TEST_CODE = 900_000

    /** டெஸ்ட் அலர்ட்டுக்கான போலி id-கள் */
    const val TEST_WATER = -1
    const val TEST_MEETING = -2

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(ctx: Context, id: Int, code: Int, test: Boolean = false, snooze: Boolean = false): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TEST, test)
            .putExtra(EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    /** எல்லா அலர்ட்ஸையும் cancel பண்ணி, ON-ல இருக்குறத மட்டும் மறுபடி செட் பண்ணும். */
    fun rescheduleAll(ctx: Context, reason: String? = null) {
        ReminderStore.init(ctx)
        val list = ReminderStore.reminders.value
        list.forEach { am(ctx).cancel(pendingIntent(ctx, it.id, it.id)) }
        if (!ReminderStore.masterOn.value) {
            if (reason != null) DebugLog.add(ctx, "[$reason] Master OFF — அலர்ட் எதுவும் செட் இல்லை")
            return
        }
        var n = 0
        list.filter { it.enabled }.forEach { if (schedule(ctx, it, log = false)) n++ }
        if (reason != null) DebugLog.add(ctx, "[$reason] $n அலர்ட் செட் ஆச்சு")
    }

    /** அடுத்த நேரத்துக்கு செட் பண்ணும். இனிமே வர நேரம் இல்லைனா (கடந்த ஒரு தடவை அலர்ட்) false. */
    fun schedule(ctx: Context, r: Reminder, after: Long = System.currentTimeMillis(), log: Boolean = true): Boolean {
        val at = nextTrigger(r, after) ?: return false
        val mode = setAt(ctx, at, pendingIntent(ctx, r.id, r.id))
        if (log) DebugLog.add(ctx, "அடுத்து ${r.kind.emoji} ${r.displayTitle}: ${fmt(at)} ($mode)")
        return true
    }

    fun cancel(ctx: Context, id: Int) {
        am(ctx).cancel(pendingIntent(ctx, id, id))
        am(ctx).cancel(pendingIntent(ctx, id, SNOOZE_BASE + id, snooze = true))
    }

    fun scheduleSnooze(ctx: Context, id: Int, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val code = SNOOZE_BASE + (if (id < 0) 1000 - id else id)
        setAt(ctx, at, pendingIntent(ctx, id, code, test = id < 0, snooze = true))
        DebugLog.add(ctx, "⏰ $minutes நிமிஷத்துல மறுபடி (${fmt(at)})")
    }

    /** "டெஸ்ட்" — சில வினாடிகளில் ஒரு அலர்ட். Phone lock பண்ணி பாக்கலாம். */
    fun scheduleTest(ctx: Context, testId: Int = TEST_WATER, seconds: Int = 10) {
        val at = System.currentTimeMillis() + seconds * 1000L
        val mode = setAt(ctx, at, pendingIntent(ctx, testId, TEST_CODE - testId, test = true))
        DebugLog.add(ctx, "டெஸ்ட் அலர்ட் ${fmt(at)}-க்கு செட் ($mode)")
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setAt(ctx: Context, at: Long, pi: PendingIntent): String {
        val am = am(ctx)
        return try {
            if (canExact(ctx)) {
                val show = PendingIntent.getActivity(
                    ctx, 777, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                "alarm-clock"
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                "inexact — exact permission இல்லை!"
            }
        } catch (e: Exception) {
            DebugLog.add(ctx, "setAlarm பிழை: ${e.javaClass.simpleName}: ${e.message}")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            "inexact fallback"
        }
    }

    /** [after]-க்கு அப்புறம் வரும் முதல் நேரம். ஒரு தடவை அலர்ட் கடந்து போயிருந்தா null. */
    fun nextTrigger(r: Reminder, after: Long): Long? {
        val c = Calendar.getInstance()
        if (r.isOnce) {
            if (r.onceDate == 0) return null
            c.set(r.onceDate / 10000, r.onceDate / 100 % 100 - 1, r.onceDate % 100, r.hour, r.minute, 0)
            c.set(Calendar.MILLISECOND, 0)
            return if (c.timeInMillis > after) c.timeInMillis else null
        }
        c.timeInMillis = after
        c.set(Calendar.HOUR_OF_DAY, r.hour); c.set(Calendar.MINUTE, r.minute)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        for (i in 0..7) {
            val dow = c.get(Calendar.DAY_OF_WEEK) - 1     // 0 = ஞாயிறு
            if (c.timeInMillis > after && (r.days and (1 shl dow)) != 0) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun fmt(t: Long): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(t))
}
