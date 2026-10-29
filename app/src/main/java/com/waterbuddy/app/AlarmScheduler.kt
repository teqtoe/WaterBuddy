package com.waterbuddy.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * பேட்டரி சேமிப்பு ரகசியம்: background service எதுவும் ஓடாது.
 * Android-ஓட AlarmManager-கிட்ட "இந்த நேரத்துல எழுப்பு"ன்னு சொல்லிட்டு app தூங்கிடும்.
 * அலர்ட் நேரத்துல மட்டும் ஒரு நொடி எழுந்து notification காட்டி, அடுத்த நாளுக்கு செட் பண்ணிடும்.
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
    fun rescheduleAll(ctx: Context) {
        ReminderStore.init(ctx)
        val list = ReminderStore.reminders.value
        list.forEach { cancel(ctx, it.id) }
        if (!ReminderStore.masterOn.value) return
        list.filter { it.enabled }.forEach { schedule(ctx, it) }
    }

    fun schedule(ctx: Context, r: Reminder, after: Long = System.currentTimeMillis()) {
        setAt(ctx, nextTrigger(r.hour, r.minute, after), pendingIntent(ctx, r.id))
    }

    fun cancel(ctx: Context, id: Int) {
        am(ctx).cancel(pendingIntent(ctx, id))
    }

    /** "டெஸ்ட்" — சில வினாடிகளில் ஒரு அலர்ட். Phone lock பண்ணி பாக்கலாம். */
    fun scheduleTest(ctx: Context, seconds: Int = 10) {
        setAt(ctx, System.currentTimeMillis() + seconds * 1000L, pendingIntent(ctx, -1, test = true))
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setAt(ctx: Context, at: Long, pi: PendingIntent) {
        val am = am(ctx)
        try {
            if (canExact(ctx)) {
                // Doze mode-லயும் சரியான நேரத்துக்கு வரும்
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // Exact permission இல்லைனா: சில நிமிஷம் தள்ளி வரலாம், ஆனா கண்டிப்பா வரும்
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
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
}
