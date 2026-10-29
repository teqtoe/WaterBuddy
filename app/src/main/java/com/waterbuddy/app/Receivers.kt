package com.waterbuddy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** அலர்ட் நேரம் வந்ததும் Android இதை எழுப்பும். */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            ReminderStore.init(ctx)
            val test = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)
            val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
            DebugLog.add(ctx, "⏰ Android எழுப்புச்சு (${if (test) "டெஸ்ட்" else "id=$id"})")

            if (test) {
                Notifier.showReminder(ctx)
                return
            }

            val r = ReminderStore.find(id)
            if (r == null) { DebugLog.add(ctx, "அந்த அலர்ட் இப்போ இல்லை (delete ஆயிருக்கு)"); return }
            if (!ReminderStore.masterOn.value || !r.enabled) { DebugLog.add(ctx, "அலர்ட் OFF-ல இருக்கு, skip"); return }

            // நாளைக்கு இதே நேரத்துக்கு மறுபடி செட் (1 நிமிஷம் buffer — double alert வராம இருக்க)
            AlarmScheduler.schedule(ctx, r, after = System.currentTimeMillis() + 60_000L)
            Notifier.showReminder(ctx)
        } catch (e: Exception) {
            DebugLog.add(ctx, "AlarmReceiver பிழை: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/** Notification-லயே இருக்குற "குடிச்சேன்" / "இப்போ வேணாம்" பட்டன்கள். */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
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
        } catch (e: Exception) {
            DebugLog.add(ctx, "ActionReceiver பிழை: ${e.message}")
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
        try {
            AlarmScheduler.rescheduleAll(ctx, reason = intent.action?.substringAfterLast('.') ?: "boot")
        } catch (e: Exception) {
            DebugLog.add(ctx, "BootReceiver பிழை: ${e.message}")
        }
    }
}
