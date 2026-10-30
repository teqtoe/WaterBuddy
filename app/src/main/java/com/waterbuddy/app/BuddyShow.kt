package com.waterbuddy.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Buddy-ஓட pose படங்கள் (transparent PNG → webp). anchor = உடம்போட நடு (x, 0..1) */
enum class Pose(val res: Int, val wPx: Int, val hPx: Int, val anchor: Float) {
    RUN(R.drawable.buddy_run, 487, 465, 0.562f),
    HAPPY(R.drawable.buddy_happy, 318, 567, 0.504f),
    DRINK(R.drawable.buddy_drink, 303, 591, 0.538f),
    SAD(R.drawable.buddy_sad, 294, 564, 0.545f)
}

/** Buddy அளவு — குட்டியா (நிக்கும்போது ~140dp உயரம்) */
const val BUDDY_DP_PER_PX = 0.24f
private const val STAGE_DP = 150

private enum class Stage { RUN_IN, ASK, DRINK, CHEER, SAD_STAND, LEAVE_RUN, LEAVE_SAD, DONE }

/** ஒரு pose-ஐ அளவோட வரையும் (home screen-லயும் பயன்படுது) */
@Composable
fun BuddySprite(pose: Pose, modifier: Modifier = Modifier, scale: Float = BUDDY_DP_PER_PX) {
    Image(
        painter = painterResource(pose.res),
        contentDescription = null,
        modifier = modifier.size((pose.wPx * scale).dp, (pose.hPx * scale).dp)
    )
}

/**
 * அலர்ட் நிகழ்ச்சி (background இல்லை — transparent):
 * இடது பக்கத்துல இருந்து ஓடி வருவார் → நடுவுல நின்னு சொல்லுவார் →
 * ✓ → (தண்ணீர்னா குடிச்சு) சந்தோஷமா வலது பக்கம் ஓடுவார் / அப்புறம் → சோகமா நடந்து போவார்.
 */
@Composable
fun BuddyShow(
    alert: Reminder,
    onArrived: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onTimeout: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    speech: String? = null
) {
    var stage by remember { mutableStateOf(Stage.RUN_IN) }
    var positive by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    var stageW by remember { mutableFloatStateOf(0f) }
    val x = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val edge = with(density) { 140.dp.toPx() }

    LaunchedEffect(stage, stageW) {
        if (stageW <= 0f) return@LaunchedEffect
        val center = stageW / 2f
        when (stage) {
            Stage.RUN_IN -> {
                if (!placed) { x.snapTo(-edge); placed = true }
                x.animateTo(center, tween(1900, easing = LinearOutSlowInEasing))
                stage = Stage.ASK
                onArrived()
            }
            Stage.ASK -> { delay(45_000); onTimeout(); positive = false; stage = Stage.LEAVE_SAD }
            Stage.DRINK -> { delay(1700); stage = Stage.CHEER }
            Stage.CHEER -> { delay(1500); stage = Stage.LEAVE_RUN }
            Stage.SAD_STAND -> { delay(1800); stage = Stage.LEAVE_SAD }
            Stage.LEAVE_RUN -> {
                x.animateTo(stageW + edge, tween(1500, easing = FastOutLinearInEasing))
                stage = Stage.DONE; onFinished()
            }
            Stage.LEAVE_SAD -> {
                x.animateTo(stageW + edge, tween(3000, easing = LinearEasing))
                stage = Stage.DONE; onFinished()
            }
            Stage.DONE -> {}
        }
    }

    val pose = when (stage) {
        Stage.RUN_IN, Stage.LEAVE_RUN -> Pose.RUN
        Stage.ASK, Stage.CHEER -> Pose.HAPPY
        Stage.DRINK -> Pose.DRINK
        Stage.SAD_STAND, Stage.LEAVE_SAD, Stage.DONE -> Pose.SAD
    }

    val inf = rememberInfiniteTransition(label = "bob")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "t")
    val tau = 2f * PI.toFloat()
    val (bobDp, rotDeg) = when (stage) {
        Stage.RUN_IN, Stage.LEAVE_RUN -> -abs(sin(tau * t * 3f)) * 9f to sin(tau * t * 3f) * 3f
        Stage.ASK -> -abs(sin(tau * t * 1.2f)) * 5f to sin(tau * t) * 3f
        Stage.CHEER -> -abs(sin(tau * t * 2f)) * 16f to sin(tau * t * 2f) * 5f
        Stage.DRINK -> 0f to (-3f + sin(tau * t) * 1.5f)
        Stage.SAD_STAND -> 0f to sin(tau * t * 0.5f) * 1f
        Stage.LEAVE_SAD -> -abs(sin(tau * t * 1.6f)) * 3f to sin(tau * t * 1.6f) * 1.5f
        Stage.DONE -> 0f to 0f
    }

    val showBubble = stage in setOf(Stage.ASK, Stage.DRINK, Stage.CHEER, Stage.SAD_STAND)
    val bubbleText = when (stage) {
        Stage.ASK -> speech?.takeIf { it.isNotBlank() } ?: alert.message.ifBlank { AlertFlow.defaultMessage(alert.kind) }
        else -> remember(stage, positive) { AlertFlow.reactionBubble(alert, positive) }
    }
    val (yesLabel, laterLabel) = AlertFlow.buttonLabels(alert)

    fun answer(yes: Boolean) {
        if (stage != Stage.ASK) return
        positive = yes
        onAnswer(yes)
        stage = when {
            !yes -> Stage.SAD_STAND
            alert.kind == AlertKind.WATER -> Stage.DRINK
            else -> Stage.CHEER
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = showBubble, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            AlertBubble(
                title = "${alert.kind.emoji} ${alert.displayTitle}",
                text = bubbleText,
                yes = yesLabel, later = laterLabel,
                showButtons = stage == Stage.ASK,
                onYes = { answer(true) }, onLater = { answer(false) }
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(STAGE_DP.dp)
                .onSizeChanged { stageW = it.width.toFloat() }
        ) {
            if (placed) {
                val wPx = with(density) { (pose.wPx * BUDDY_DP_PER_PX).dp.toPx() }
                val hPx = with(density) { (pose.hPx * BUDDY_DP_PER_PX).dp.toPx() }
                val stageH = with(density) { STAGE_DP.dp.toPx() }
                val bobPx = with(density) { bobDp.dp.toPx() }
                BuddySprite(
                    pose,
                    Modifier
                        .offset { IntOffset((x.value - pose.anchor * wPx).toInt(), (stageH - hPx + bobPx).toInt()) }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(pose.anchor, 1f)
                            rotationZ = rotDeg
                        }
                )
            }
        }
    }
}

/** பேச்சு குமிழ் + அதுக்குள்ளயே சின்ன பட்டன்கள் (இடம் அடைக்காம) */
@Composable
private fun AlertBubble(
    title: String, text: String, yes: String, later: String,
    showButtons: Boolean, onYes: () -> Unit, onLater: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Primary)
                Text(text, fontSize = 15.sp, color = Brand.Ink, textAlign = TextAlign.Center, lineHeight = 20.sp)
                if (showButtons) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onYes,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand.Primary)
                        ) { Text(yes, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = onLater,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                            border = BorderStroke(1.dp, Brand.Muted.copy(alpha = 0.5f))
                        ) { Text(later, fontSize = 13.sp, color = Brand.Muted) }
                    }
                }
            }
        }
        Canvas(Modifier.size(18.dp, 10.dp)) {
            drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
            }, Color.White)
        }
    }
}
