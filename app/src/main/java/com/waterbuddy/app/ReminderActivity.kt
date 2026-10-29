package com.waterbuddy.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lock screen மேலயே வரும் அலர்ட்: கேரக்டர் dance ஆடிக்கிட்டே உள்ள வருவாங்க! */
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
        if (ReminderStore.voiceOn.value && Speaker.soundAllowed(this)) {
            beat = BeatPlayer().also { it.start() }
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
        beat?.stop()   // திரை off ஆனா பாட்டு நிக்கும் (பேட்டரி)
        super.onStop()
    }

    override fun onDestroy() {
        beat?.stop()
        speaker?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun ReminderScreen(onAnswer: (Boolean) -> Unit, onClose: () -> Unit) {
    var mood by remember { mutableStateOf(Mood.DANCING) }
    val character by ReminderStore.character.collectAsState()
    val stats by ReminderStore.today.collectAsState()
    val goal by ReminderStore.goalMl.collectAsState()
    val glass by ReminderStore.glassMl.collectAsState()
    val clock = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val buddy = character ?: BuddyCharacter.KUTTY

    val bubble = remember(mood) {
        when (mood) {
            Mood.HAPPY -> Lines.happyBubbles.random()
            Mood.SAD -> Lines.sadBubbles.random()
            else -> Lines.askBubbles.random()
        }
    }

    // Dance ஆடிக்கிட்டே பக்கவாட்டுல இருந்து உள்ள வருவாங்க
    val enter = remember { Animatable(1.4f) }
    LaunchedEffect(Unit) { enter.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 60f)) }

    // பதில் இல்லைனா 1 நிமிஷத்துல மூடிடும் (பேட்டரி). Notification அப்படியே இருக்கும்.
    LaunchedEffect(mood) {
        when (mood) {
            Mood.DANCING -> { delay(60_000); onClose() }
            Mood.HAPPY, Mood.SAD -> { delay(4_500); onClose() }
            else -> {}
        }
    }

    val top by animateColorAsState(
        when (mood) {
            Mood.HAPPY -> Color(0xFFFFE082)
            Mood.SAD -> Color(0xFF90A4AE)
            else -> Brand.Primary
        }, tween(700), label = "top"
    )
    val bottom by animateColorAsState(
        when (mood) {
            Mood.HAPPY -> Brand.Secondary
            Mood.SAD -> Color(0xFF455A64)
            else -> Brand.Secondary
        }, tween(700), label = "bottom"
    )
    val onBg = if (mood == Mood.HAPPY) Brand.Ink else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(clock, fontSize = 54.sp, fontWeight = FontWeight.Light, color = onBg)
            Text("💧 Water Buddy", style = MaterialTheme.typography.titleMedium, color = onBg)
            Spacer(Modifier.height(14.dp))
            SpeechBubble(bubble)
            WaterBuddy(
                mood = mood,
                character = buddy,
                glassFill = if (mood == Mood.HAPPY) 0.15f else 0.8f,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
                    .graphicsLayer { translationX = enter.value * size.width }
            )

            if (mood == Mood.DANCING) {
                Button(
                    onClick = { mood = Mood.HAPPY; onAnswer(true) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Primary)
                ) { Text("இப்போ குடிக்கிறேன் 💧", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { mood = Mood.SAD; onAnswer(false) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.5.dp, Color.White)
                ) { Text("அப்புறம் குடிக்கிறேன் 😕", fontSize = 17.sp, color = Color.White) }
            } else {
                WaterProgress(drankMl = stats.ml, goalMl = goal, size = 130.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (mood == Mood.HAPPY) "+$glass ml சேர்த்தாச்சு!" else "பரவால்ல, அடுத்த முறை!",
                    fontWeight = FontWeight.SemiBold, color = onBg
                )
            }
            TeqtoeFooter(light = mood != Mood.HAPPY)
        }
    }
}
