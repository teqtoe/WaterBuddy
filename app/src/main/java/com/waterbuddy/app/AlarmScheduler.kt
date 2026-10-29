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
 *
 * setAlarmClock() பயன்படுத்துறோம் — இது Android-லயே மிக நம்பகமான அலாரம்.
 * Doze mode, Xiaomi/Vivo/Oppo battery saver எல்லாத்தையும் தாண்டி சரியான நேரத்துக்கு வரும்.
 */
object AlarmScheduler {
    const val ACTION_ALARM = "com.waterbuddy.app.ALARM"
    const val EXTRA_ID = "id"
    const val EXTRA_TEST = "test"
    private const val TEST_REQUEST_CODE = 900_000

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(ctx: Context, id: Int, test: Boolean = false): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TEST, test)
        return PendingIntent.getBroadcast(
            ctx,
            if (test) TEST_REQUEST_CODE else id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    /** எல்லா அலர்ட்ஸையும் cancel பண்ணி, ON-ல இருக்குறத மட்டும் மறுபடி செட் பண்ணும். */
    fun rescheduleAll(ctx: Context, reason: String? = null) {
        ReminderStore.init(ctx)
        val list = ReminderStore.reminders.value
        list.forEach { cancel(ctx, it.id) }
        if (!ReminderStore.masterOn.value) {
            if (reason != null) DebugLog.add(ctx, "[$reason] Master OFF — அலர்ட் எதுவும் செட் இல்லை")
            return
        }
        val active = list.filter { it.enabled }
        active.forEach { schedule(ctx, it, log = false) }
        if (reason != null) DebugLog.add(ctx, "[$reason] ${active.size} அலர்ட் செட் ஆச்சு")
    }

    fun schedule(ctx: Context, r: Reminder, after: Long = System.currentTimeMillis(), log: Boolean = true) {
        val at = nextTrigger(r.hour, r.minute, after)
        val mode = setAt(ctx, at, pendingIntent(ctx, r.id))
        if (log) DebugLog.add(ctx, "அடுத்த அலர்ட் ${fmt(at)} ($mode)")
    }

    fun cancel(ctx: Context, id: Int) {
        am(ctx).cancel(pendingIntent(ctx, id))
    }

    /** "டெஸ்ட்" — சில வினாடிகளில் ஒரு அலர்ட். Phone lock பண்ணி பாக்கலாம். */
    fun scheduleTest(ctx: Context, seconds: Int = 10) {
        val at = System.currentTimeMillis() + seconds * 1000L
        val mode = setAt(ctx, at, pendingIntent(ctx, -1, test = true))
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

    private fun nextTrigger(hour: Int, minute: Int, after: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= after) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun fmt(t: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(t))
}
