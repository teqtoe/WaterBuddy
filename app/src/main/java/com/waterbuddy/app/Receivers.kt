package com.waterbuddy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** அலர்ட் நேரம் வந்ததும் Android இதை எழுப்பும். */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ReminderStore.init(ctx)

        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)) {
            Notifier.showReminder(ctx)
            return
        }

        val r = ReminderStore.find(intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)) ?: return
        if (!ReminderStore.masterOn.value || !r.enabled) return

        // நாளைக்கு இதே நேரத்துக்கு மறுபடி செட் (1 நிமிஷம் buffer — double alert வராம இருக்க)
        AlarmScheduler.schedule(ctx, r, after = System.currentTimeMillis() + 60_000L)
        Notifier.showReminder(ctx)
    }
}

/** Notification-லயே இருக்குற "குடிச்சேன்" / "இப்போ வேணாம்" பட்டன்கள். */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ReminderStore.init(ctx)
        Notifier.cancelReminder(ctx)
        when (intent.action) {
            ACTION_DRANK -> {
                ReminderStore.recordDrink(ctx)
                Notifier.showFeedback(ctx, drank = true)
            }
            ACTION_SKIP -> {
                ReminderStore.recordSkip(ctx)
                Notifier.showFeedback(ctx, drank = false)
            }
        }
    }

    companion object {
        const val ACTION_DRANK = "com.waterbuddy.app.DRANK"
        const val ACTION_SKIP = "com.waterbuddy.app.SKIP"
    }
}

/** Phone restart / நேரம் மாற்றம் / permission மாற்றம் — அலர்ட்ஸ் மறுபடி செட். */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(ctx)
    }
}
