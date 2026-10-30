package com.waterbuddy.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Popup notification எதுவும் இல்லை. இங்க இருக்குறது:
 *  - Lock screen-ல Buddy-ஐ திறக்க Android கேக்குற "full-screen" அனுமதி (சத்தம் இல்லாதது, Buddy வந்ததும் உடனே மறையும்)
 *  - Overlay ஓடும்போது Android கட்டாயமா கேக்குற சின்ன அமைதியான notification
 */
object Notifier {
    private const val CH_ALERT = "buddy_lock_alert"
    private const val CH_RUNNING = "buddy_running"
    const val ID_ALERT = 1001
    const val ID_RUNNING = 1003
    private const val BLUE = 0xFF4F46E5.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // பழைய (popup) channel-களை நீக்கு
        listOf("water_alerts", "water_feedback", "buddy_alert_silent").forEach { nm.deleteNotificationChannel(it) }
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Lock screen Buddy", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lock screen-ல Buddy-ஐ திறக்க மட்டும் (சத்தம் இல்லை)"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Buddy திரையில் இருக்கும்போது", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Buddy திரையில் வரும் சில வினாடிகள் மட்டும்"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun screenIntent(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, ReminderActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 10_000 + (id and 0xFFFF), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Lock-ல இருக்கும்போது Buddy திரையை திறக்கும். */
    @SuppressLint("MissingPermission")
    fun showFullScreen(ctx: Context, id: Int) {
        ensureChannels(ctx)
        val r = AlertFlow.resolve(id)
        val pi = screenIntent(ctx, id)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setColor(BLUE)
            .setContentTitle("${r.kind.emoji} ${r.displayTitle}")
            .setContentText(r.message.ifBlank { AlertFlow.defaultMessage(r.kind) })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // குறிப்பு: setSilent() போடக்கூடாது — அது full-screen திறக்கிறதை தடுக்கும். Channel-லயே சத்தம் இல்லை.
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_ALERT, n)
            DebugLog.add(ctx, "🔒 Lock screen-ல Buddy திறக்குறேன்")
        } catch (e: Exception) {
            DebugLog.add(ctx, "❌ Lock screen திறக்க முடியல: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Overlay service-க்கான அமைதியான notification */
    fun buildRunning(ctx: Context): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setColor(BLUE)
            .setContentTitle("Buddy திரையில் இருக்கார்")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    fun cancelAlert(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_ALERT)
    }
}
