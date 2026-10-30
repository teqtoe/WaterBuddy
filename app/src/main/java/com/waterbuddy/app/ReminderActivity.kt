package com.waterbuddy.app

import android.content.Intent
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
 * Buddy இடது பக்கம் ஓடி வந்து, நடுவுல நின்னு சொல்லிட்டு, வலது பக்கம் போயிடுவார்.
 */
class ReminderActivity : ComponentActivity() {
    private var speaker: Speaker? = null

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
        Notifier.cancelAlert(this)        // மேல notification தெரியக்கூடாது
        OverlayService.stop(this)          // ரெண்டு இடத்துலயும் ஒரே நேரத்துல வர வேண்டாம்
        val r = AlertFlow.resolve(intent.getIntExtra(AlarmScheduler.EXTRA_ID, AlarmScheduler.TEST_WATER))
        if (ReminderStore.voiceOn.value && Speaker.soundAllowed(this)) speaker = Speaker(this)

        setContent {
            WaterBuddyTheme {
                var spoken by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { speaker?.onSpeak = { spoken = it } }
                Box(Modifier.fillMaxSize()) {
                    BuddyShow(
                        alert = r,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 32.dp),
                        speech = spoken,
                        onArrived = { speaker?.sayRepeated(AlertFlow.spokenLines(r)) },
                        onAnswer = { yes ->
                            AlertFlow.onAnswer(this@ReminderActivity, r, yes)
                            val (ta, en) = AlertFlow.reactionSpoken(r, yes)
                            speaker?.say(ta, en)
                        },
                        onTimeout = { speaker?.stop() },
                        onFinished = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Notifier.cancelAlert(this)
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
