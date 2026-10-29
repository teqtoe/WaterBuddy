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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class Stage { RUN_IN, ALERT, REACT, RUN_OUT, DONE }

/**
 * அலர்ட் நிகழ்ச்சி (background இல்லை — transparent):
 * 1) பக்கவாட்டா திரும்பி இடது பக்கத்துல இருந்து ஓடி வருவாங்க
 * 2) நடுவுல நின்னு உங்களை பாத்து dance ஆடி, தண்ணி குடிக்க சொல்லுவாங்க
 * 3) "குடிக்கிறேன்" → தலையாட்டி சந்தோஷம் / "அப்புறம்" → சோகமா தலை குனிஞ்சு
 * 4) வலது பக்கம் ஓடிப் போயிடுவாங்க
 */
@Composable
fun BuddyShow(
    onArrived: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onTimeout: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    speech: String? = null
) {
    val ctx = LocalContext.current
    val model by ReminderStore.model.collectAsState()
    val faceOn by ReminderStore.faceOn.collectAsState()
    val faceVersion by ReminderStore.faceVersion.collectAsState()
    val face = remember(faceOn, faceVersion) { if (faceOn) FaceMaker.load(ctx) else null }

    var stage by remember { mutableStateOf(Stage.RUN_IN) }
    var answer by remember { mutableStateOf<Boolean?>(null) }
    val x = remember { Animatable(-1.1f) }

    LaunchedEffect(stage) {
        when (stage) {
            Stage.RUN_IN -> {
                x.animateTo(0f, tween(2300, easing = LinearOutSlowInEasing))
                stage = Stage.ALERT
                onArrived()
            }
            Stage.ALERT -> {
                delay(45_000)
                onTimeout()
                stage = Stage.RUN_OUT
            }
            Stage.REACT -> {
                delay(3_200)
                stage = Stage.RUN_OUT
            }
            Stage.RUN_OUT -> {
                delay(350)   // திரும்புறதுக்கு நேரம்
                x.animateTo(1.15f, tween(1900, easing = FastOutLinearInEasing))
                stage = Stage.DONE
                onFinished()
            }
            Stage.DONE -> {}
        }
    }

    val action = when (stage) {
        Stage.RUN_IN, Stage.RUN_OUT -> Action3D.RUN
        Stage.ALERT -> Action3D.DANCE
        Stage.REACT -> if (answer == true) Action3D.YES else Action3D.SAD
        Stage.DONE -> Action3D.IDLE
    }
    val yaw = when (stage) {
        Stage.RUN_IN, Stage.RUN_OUT -> 90f   // வலது பக்கம் பாத்து ஓடுறாங்க
        else -> 0f                           // உங்களை பாக்குறாங்க
    }
    val fallbackMood = when (stage) {
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
    val shown = if (stage == Stage.ALERT && !speech.isNullOrBlank()) "🗣 $speech" else bubble

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = stage == Stage.ALERT || stage == Stage.REACT,
            enter = scaleIn() + fadeIn(),
            exit = fadeOut()
        ) {
            SpeechBubble(shown, Modifier.padding(horizontal = 24.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp)) {
            val widthPx = constraints.maxWidth.toFloat()
            Buddy3D(
                model = model,
                action = action,
                yaw = yaw,
                face = face,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 260.dp, height = 300.dp)
                    .graphicsLayer { translationX = x.value * widthPx },
                fallback = { WaterBuddy(fallbackMood, BuddyCharacter.KUTTY, Modifier.height(260.dp)) }
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
