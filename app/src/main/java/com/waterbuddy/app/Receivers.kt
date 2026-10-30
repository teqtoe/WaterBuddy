package com.waterbuddy.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/** அலர்ட் நேரம் வந்ததும் Android இதை எழுப்பும். */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            ReminderStore.init(ctx)
            val test = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)
            val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)
            val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, AlarmScheduler.TEST_WATER)
            DebugLog.add(ctx, "⏰ Android எழுப்புச்சு (id=$id${if (test) " டெஸ்ட்" else ""}${if (snooze) " snooze" else ""})")

            if (test) { fire(ctx, id); return }

            val r = ReminderStore.find(id)
            if (r == null) { DebugLog.add(ctx, "அந்த அலர்ட் இப்போ இல்லை (delete ஆயிருக்கு)"); return }
            if (!ReminderStore.masterOn.value) { DebugLog.add(ctx, "Master OFF, skip"); return }

            if (!snooze) {
                if (!r.enabled) { DebugLog.add(ctx, "அலர்ட் OFF-ல இருக்கு, skip"); return }
                if (r.isOnce) {
                    ReminderStore.setEnabled(ctx, r.id, false)          // ஒரு தடவை — முடிஞ்சது
                } else {
                    // அடுத்த முறைக்கு செட் (1 நிமிஷம் buffer — double alert வராம இருக்க)
                    AlarmScheduler.schedule(ctx, r, after = System.currentTimeMillis() + 60_000L)
                }
            }
            if (ReminderStore.isPaused() && r.kind == AlertKind.WATER) {
                DebugLog.add(ctx, "⏸ Pause-ல இருக்கு, தண்ணீர் அலர்ட் skip"); return
            }
            fire(ctx, id)
        } catch (e: Exception) {
            DebugLog.add(ctx, "AlarmReceiver பிழை: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/**
 * Phone பயன்பாட்டுல இருந்தா → எந்த app மேலயும் overlay-ல Buddy ஓடி வருவார் (மேல notification வராது).
 * Lock / screen off-ல இருந்தா → lock screen மேல transparent திரையில வருவார்.
 */
fun fire(ctx: Context, id: Int) {
    val pm = ctx.getSystemService(PowerManager::class.java)
    val km = ctx.getSystemService(KeyguardManager::class.java)
    val inUse = pm.isInteractive && !km.isKeyguardLocked
    if (inUse && Settings.canDrawOverlays(ctx)) {
        try {
            OverlayService.start(ctx, id)
            return
        } catch (e: Exception) {
            DebugLog.add(ctx, "Overlay start ஆகல (${e.javaClass.simpleName})")
        }
    }
    // Lock screen: full-screen அனுமதி மூலமா திறக்கும்
    Notifier.showFullScreen(ctx, id)
    // கூடவே நேரடியாவும் திறக்க முயற்சி ("Display over other apps" அனுமதி இருந்தா Android அனுமதிக்கும்)
    if (Settings.canDrawOverlays(ctx)) {
        try {
            ctx.startActivity(
                Intent(ctx, ReminderActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            DebugLog.add(ctx, "நேரடியா திறக்க முடியல: ${e.javaClass.simpleName}")
        }
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
