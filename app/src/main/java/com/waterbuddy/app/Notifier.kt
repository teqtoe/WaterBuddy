package com.waterbuddy.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CH_ALERT = "water_alerts"
    private const val CH_FEEDBACK = "water_feedback"
    private const val ID_REMINDER = 1001
    private const val ID_FEEDBACK = 1002
    private const val BLUE = 0xFF0288D1.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "தண்ணீர் அலர்ட்", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "தண்ணி குடிக்க ஞாபகப்படுத்தும் அலர்ட்"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FEEDBACK, "Buddy பதில்", NotificationManager.IMPORTANCE_LOW).apply {
                description = "நீங்க பதில் சொன்னதும் வரும் சின்ன செய்தி"
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun showReminder(ctx: Context) {
        ensureChannels(ctx)
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return

        val screenIntent = Intent(ctx, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreen = PendingIntent.getActivity(
            ctx, 0, screenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(BLUE)
            .setContentTitle("💧 தண்ணி குடிக்கிற நேரம்!")
            .setContentText("உங்க Water Buddy வெயிட் பண்றார்… ஒரு கிளாஸ் குடிங்க!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)   // lock-ல இருந்தா முழு திரை கேரக்டர்
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .setTimeoutAfter(30 * 60_000L)           // 30 நிமிஷம் கழிச்சு தானா போயிடும்
            .addAction(0, "குடிச்சேன் 💧", actionIntent(ctx, ActionReceiver.ACTION_DRANK, 1))
            .addAction(0, "இப்போ வேணாம்", actionIntent(ctx, ActionReceiver.ACTION_SKIP, 2))
            .build()

        try {
            nmc.notify(ID_REMINDER, n)
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun showFeedback(ctx: Context, drank: Boolean) {
        ensureChannels(ctx)
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val count = ReminderStore.today.value.drank
        val n = NotificationCompat.Builder(ctx, CH_FEEDBACK)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(BLUE)
            .setContentTitle(if (drank) "😄 சூப்பர்! நீங்க Hydration Hero!" else "😢 ஹ்ம்ம்… சரி பரவால்ல")
            .setContentText(
                if (drank) "இன்னைக்கு $count / ${ReminderStore.DAILY_GOAL} கிளாஸ் முடிஞ்சது"
                else "அடுத்த அலர்ட்ல கண்டிப்பா குடிக்கணும், சரியா?"
            )
            .setTimeoutAfter(6_000L)
            .setAutoCancel(true)
            .build()
        try {
            nmc.notify(ID_FEEDBACK, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelReminder(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_REMINDER)
    }

    private fun actionIntent(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
