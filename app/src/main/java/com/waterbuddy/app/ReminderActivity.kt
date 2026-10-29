package com.waterbuddy.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Lock screen-ல வரும் அலர்ட் — background இல்லாத transparent திரை.
 * கேரக்டர் இடது பக்கம் ஓடி வந்து, நடுவுல நின்னு, வலது பக்கம் போயிடுவாங்க.
 */
class ReminderActivity : ComponentActivity() {
    private var speaker: Speaker? = null
    private var beat: BeatPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ReminderStore.init(this)
        OverlayService.stop(this)   // ரெண்டு இடத்துலயும் ஒரே நேரத்துல வர வேண்டாம்
        if (ReminderStore.voiceOn.value && Speaker.soundAllowed(this)) {
            beat = BeatPlayer().also { it.start() }
            speaker = Speaker(this)
        }

        setContent {
            WaterBuddyTheme {
                var spoken by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { speaker?.onSpeak = { spoken = it } }
                Box(Modifier.fillMaxSize()) {
                    BuddyShow(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        speech = spoken,
                        onArrived = { speaker?.sayRepeated(ReminderStore.playlist()) },
                        onAnswer = ::answer,
                        onTimeout = { beat?.stop(); speaker?.stop() },
                        onFinished = { finish() }
                    )
                }
            }
        }
    }

    private fun answer(drank: Boolean) {
        beat?.stop()
        Notifier.cancelReminder(this)
        if (drank) {
            ReminderStore.recordDrink(this)
            speaker?.say(Lines.HAPPY_TA, Lines.HAPPY_EN)
        } else {
            ReminderStore.recordSkip(this)
            speaker?.say(Lines.SAD_TA, Lines.SAD_EN)
        }
    }

    override fun onStop() {
        beat?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        beat?.stop()
        speaker?.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
