package com.waterbuddy.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class Stage { RUN_IN, ALERT, REACT, RUN_OUT, DONE }

/**
 * அலர்ட் நிகழ்ச்சி (background இல்லை — transparent):
 * 1) இடது பக்கத்துல இருந்து ஓடி வருவாங்க → 2) நடுவுல நின்னு dance + கேள்வி
 * → 3) பதிலுக்கு ஏத்த மாதிரி சந்தோஷம் / சோகம் → 4) வலது பக்கம் ஓடிப் போயிடுவாங்க.
 */
@Composable
fun BuddyShow(
    character: BuddyCharacter,
    onArrived: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onTimeout: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by remember { mutableStateOf(Stage.RUN_IN) }
    var answer by remember { mutableStateOf<Boolean?>(null) }
    val x = remember { Animatable(-1.1f) }   // அகலத்தோட பங்கு: -1 = இடது வெளியே, 0 = நடு, 1 = வலது வெளியே

    LaunchedEffect(stage) {
        when (stage) {
            Stage.RUN_IN -> {
                x.animateTo(0f, tween(2000, easing = LinearOutSlowInEasing))
                stage = Stage.ALERT
                onArrived()
            }
            Stage.ALERT -> {
                delay(45_000)               // பதில் இல்லைனா கிளம்பிடுவாங்க
                onTimeout()
                stage = Stage.RUN_OUT
            }
            Stage.REACT -> {
                delay(3_000)
                stage = Stage.RUN_OUT
            }
            Stage.RUN_OUT -> {
                x.animateTo(1.15f, tween(1700, easing = FastOutLinearInEasing))
                stage = Stage.DONE
                onFinished()
            }
            Stage.DONE -> {}
        }
    }

    val mood = when (stage) {
        Stage.RUN_IN, Stage.RUN_OUT -> Mood.RUNNING
        Stage.ALERT -> Mood.DANCING
        Stage.REACT -> if (answer == true) Mood.HAPPY else Mood.SAD
        Stage.DONE -> Mood.IDLE
    }
    val bubble = remember(stage, answer) {
        when {
            stage == Stage.REACT && answer == true -> Lines.happyBubbles.random()
            stage == Stage.REACT -> Lines.sadBubbles.random()
            else -> Lines.askBubbles.random()
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = stage == Stage.ALERT || stage == Stage.REACT,
            enter = scaleIn() + fadeIn(),
            exit = fadeOut()
        ) {
            SpeechBubble(bubble, Modifier.padding(horizontal = 24.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
            val widthPx = constraints.maxWidth.toFloat()
            WaterBuddy(
                mood = mood,
                character = character,
                glassFill = if (stage == Stage.REACT && answer == true) 0.15f else 0.8f,
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(250.dp)
                    .graphicsLayer { translationX = x.value * widthPx }
            )
        }
        AnimatedVisibility(
            visible = stage == Stage.ALERT,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { answer = true; stage = Stage.REACT; onAnswer(true) },
                    modifier = Modifier.weight(1.3f).height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Primary, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) { Text("குடிக்கிறேன் 💧", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { answer = false; stage = Stage.REACT; onAnswer(false) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    border = BorderStroke(1.5.dp, Brand.Primary),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) { Text("அப்புறம் 😕", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
