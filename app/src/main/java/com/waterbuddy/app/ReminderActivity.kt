package com.waterbuddy.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lock screen மேலயே வரும் முழு திரை: Buddy கேள்வி கேப்பார், பதிலுக்கு ஏத்த மாதிரி react பண்ணுவார். */
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
        if (ReminderStore.voiceOn.value && Speaker.soundAllowed(this)) {
            speaker = Speaker(this).also { it.say(Lines.ASK_TA, Lines.ASK_EN) }
        }

        enableEdgeToEdge()
        setContent {
            WaterBuddyTheme {
                ReminderScreen(onAnswer = ::answer, onClose = { finish() })
            }
        }
    }

    private fun answer(drank: Boolean) {
        Notifier.cancelReminder(this)
        if (drank) {
            ReminderStore.recordDrink(this)
            speaker?.say(Lines.HAPPY_TA, Lines.HAPPY_EN)
        } else {
            ReminderStore.recordSkip(this)
            speaker?.say(Lines.SAD_TA, Lines.SAD_EN)
        }
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun ReminderScreen(onAnswer: (Boolean) -> Unit, onClose: () -> Unit) {
    var mood by remember { mutableStateOf(Mood.ASKING) }
    val stats by ReminderStore.today.collectAsState()
    val clock = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }

    val bubble = remember(mood) {
        when (mood) {
            Mood.HAPPY -> Lines.happyBubbles.random()
            Mood.SAD -> Lines.sadBubbles.random()
            else -> Lines.askBubbles.random()
        }
    }

    // பதில் இல்லைனா 1 நிமிஷத்துல திரை மூடிடும் (பேட்டரி சேமிக்க). Notification அப்படியே இருக்கும்.
    LaunchedEffect(mood) {
        when (mood) {
            Mood.ASKING -> { delay(60_000); onClose() }
            Mood.HAPPY, Mood.SAD -> { delay(4_000); onClose() }
            else -> {}
        }
    }

    val top by animateColorAsState(
        when (mood) {
            Mood.HAPPY -> Color(0xFFFFE082)
            Mood.SAD -> Color(0xFF90A4AE)
            else -> Color(0xFF81D4FA)
        }, tween(600), label = "top"
    )
    val bottom by animateColorAsState(
        when (mood) {
            Mood.HAPPY -> Color(0xFF80DEEA)
            Mood.SAD -> Color(0xFF546E7A)
            else -> Color(0xFFE1F5FE)
        }, tween(600), label = "bottom"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(clock, fontSize = 56.sp, fontWeight = FontWeight.Light, color = Color(0xFF0D2B3E))
            Text("Water Buddy 💧", style = MaterialTheme.typography.titleMedium, color = Color(0xFF0D2B3E))
            Spacer(Modifier.height(16.dp))
            SpeechBubble(bubble)
            WaterBuddy(
                mood = mood,
                glassFill = if (mood == Mood.HAPPY) 0.15f else 0.8f,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )

            if (mood == Mood.ASKING) {
                Button(
                    onClick = { mood = Mood.HAPPY; onAnswer(true) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                ) { Text("குடிச்சேன் 💧", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { mood = Mood.SAD; onAnswer(false) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) { Text("இப்போ வேணாம் 😕", fontSize = 17.sp, color = Color(0xFF0D2B3E)) }
            } else {
                Text(
                    "இன்னைக்கு ${stats.drank} / ${ReminderStore.DAILY_GOAL} கிளாஸ்",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF0D2B3E)
                )
                Spacer(Modifier.height(8.dp))
                GlassRow(stats.drank, ReminderStore.DAILY_GOAL)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
