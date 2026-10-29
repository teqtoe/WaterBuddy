package com.waterbuddy.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Phone பயன்பாட்டுல இருக்கும்போது (unlock) — எந்த app திறந்திருந்தாலும் அதுக்கு மேலயே
 * கேரக்டர் இடது பக்கத்துல இருந்து ஓடி வந்து, நடுவுல நின்னு, வலது பக்கம் ஓடிப்போவாங்க.
 * Background இல்லை — முழுக்க transparent overlay.
 * அலர்ட் முடிஞ்சதும் service தானா நின்னுடும் (பேட்டரி).
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private var view: ComposeView? = null
    private var speaker: Speaker? = null
    private var beat: BeatPlayer? = null
    private var timedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (view != null) return START_NOT_STICKY
        ReminderStore.init(this)
        ServiceCompat.startForeground(
            this, Notifier.ID_REMINDER, Notifier.buildReminder(this, fullScreen = false),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        try {
            show()
            DebugLog.add(this, "🏃 Overlay-ல ஓடி வர்றாங்க")
        } catch (e: Exception) {
            DebugLog.add(this, "Overlay பிழை: ${e.javaClass.simpleName}: ${e.message}")
            finishWith(notify = true)
        }
        return START_NOT_STICKY
    }

    private fun show() {
        if (ReminderStore.voiceOn.value && Speaker.soundAllowed(this)) {
            beat = BeatPlayer().also { it.start() }
            speaker = Speaker(this)
        }
        val wm = getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                WaterBuddyTheme {
                    var spoken by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) { speaker?.onSpeak = { spoken = it } }
                    BuddyShow(
                        modifier = Modifier.navigationBarsPadding(),
                        speech = spoken,
                        onArrived = { speaker?.sayRepeated(ReminderStore.playlist()) },
                        onAnswer = { drank -> answer(drank) },
                        onTimeout = {
                            timedOut = true
                            beat?.stop(); speaker?.stop()
                        },
                        onFinished = { finishWith(notify = timedOut) }
                    )
                }
            }
        }
        wm.addView(v, params)
        view = v
        registry.currentState = Lifecycle.State.RESUMED
    }

    private fun answer(drank: Boolean) {
        beat?.stop()
        if (drank) {
            ReminderStore.recordDrink(this)
            speaker?.say(Lines.HAPPY_TA, Lines.HAPPY_EN)
        } else {
            ReminderStore.recordSkip(this)
            speaker?.say(Lines.SAD_TA, Lines.SAD_EN)
        }
    }

    /** பதில் சொல்லாம போனா notification-ஐ விட்டுட்டு போகும் (அப்புறம் பதில் சொல்லலாம்). */
    private fun finishWith(notify: Boolean) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (notify) Notifier.showReminder(this, fullScreen = false)
        stopSelf()
    }

    override fun onDestroy() {
        view?.let {
            try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: Exception) {}
        }
        view = null
        beat?.stop()
        speaker?.shutdown()
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }
}
