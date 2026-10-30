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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.floor
import kotlin.math.sin

/** Buddy-ஓட pose படங்கள் (transparent webp). anchor = உடம்போட நடு (x, 0..1) */
enum class Pose(val res: Int, val wPx: Int, val hPx: Int, val anchor: Float) {
    RUN(R.drawable.buddy_run, 487, 465, 0.562f),
    HAPPY(R.drawable.buddy_happy, 318, 567, 0.504f),       // கிளாஸ் தூக்கி 👍 (தண்ணீர்)
    CHEER(R.drawable.buddy_cheer, 318, 567, 0.504f),       // கை உயர்த்தி 👍 (கிளாஸ் இல்லை)
    DRINK(R.drawable.buddy_drink, 303, 591, 0.538f),
    SAD(R.drawable.buddy_sad, 294, 564, 0.545f),           // சோகம், கையில கிளாஸ்
    SADWALK(R.drawable.buddy_sadwalk, 294, 564, 0.545f)    // சோகம், கிளாஸ் இல்லை
}

/** Buddy அளவு (நிக்கும்போது ~250dp உயரம்) */
const val BUDDY_DP_PER_PX = 0.42f
private const val STAGE_DP = 280
private const val BEAT_MS = 500   // 120 BPM — இசையோட அதே வேகம்

private enum class Stage { ENTER, DANCE, DRINK, CHEER, SAD_STAND, LEAVE_HAPPY, LEAVE_SAD, DONE }

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
 * இடது பக்கத்துல இருந்து துள்ளி நடந்து வருவார் → நடுவுல இசைக்கு ஏத்த மாதிரி ஆடிக்கிட்டே சொல்லுவார் →
 * ✓ → (தண்ணீர்னா குடிச்சு) சந்தோஷமா போவார் / அப்புறம் → சோகமா நடந்து போவார்.
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
    val water = alert.kind == AlertKind.WATER
    var stage by remember { mutableStateOf(Stage.ENTER) }
    var positive by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    var stageW by remember { mutableFloatStateOf(0f) }
    val x = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val edge = with(density) { 230.dp.toPx() }

    LaunchedEffect(stage, stageW) {
        if (stageW <= 0f) return@LaunchedEffect
        val center = stageW / 2f
        when (stage) {
            Stage.ENTER -> {
                if (!placed) { x.snapTo(-edge); placed = true }
                x.animateTo(center, tween(2800, easing = LinearOutSlowInEasing))
                stage = Stage.DANCE
                onArrived()
            }
            Stage.DANCE -> { delay(45_000); onTimeout(); positive = false; stage = Stage.LEAVE_SAD }
            Stage.DRINK -> { delay(1800); stage = Stage.CHEER }
            Stage.CHEER -> { delay(1500); stage = Stage.LEAVE_HAPPY }
            Stage.SAD_STAND -> { delay(1500); stage = Stage.LEAVE_SAD }
            Stage.LEAVE_HAPPY -> {
                x.animateTo(stageW + edge, tween(2200, easing = FastOutLinearInEasing))
                stage = Stage.DONE; onFinished()
            }
            Stage.LEAVE_SAD -> {
                x.animateTo(stageW + edge, tween(3600, easing = LinearEasing))
                stage = Stage.DONE; onFinished()
            }
            Stage.DONE -> {}
        }
    }

    val pose = when (stage) {
        Stage.ENTER, Stage.LEAVE_HAPPY -> Pose.RUN
        Stage.DANCE, Stage.CHEER -> if (water) Pose.HAPPY else Pose.CHEER
        Stage.DRINK -> Pose.DRINK
        Stage.SAD_STAND, Stage.LEAVE_SAD, Stage.DONE -> if (water) Pose.SAD else Pose.SADWALK
    }

    // நேரம் (ms) — எல்லா அசைவும் இதை வச்சு
    val inf = rememberInfiniteTransition(label = "clock")
    val ms by inf.animateFloat(0f, 8000f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "ms")
    val beat = ms / BEAT_MS                        // beat எண்ணிக்கை (தசமத்தோட)
    val bi = floor(beat).toInt()
    val bf = beat - bi                             // ஒரு beat-க்குள்ள 0..1
    val pi = PI.toFloat()

    // hop = மேல எழும்பும் உயரம் (dp), squash = தரையில இறங்கும்போது அமுங்குறது
    var hop = 0f; var rot = 0f; var flip = false; var sideDp = 0f; var squash = 0f
    when (stage) {
        Stage.ENTER, Stage.LEAVE_HAPPY -> {        // துள்ளி நடை — ஒவ்வொரு அடிக்கும் ஒரு துள்ளல்
            val step = (beat * 1.6f) % 1f
            hop = sin(step * pi) * 10f
            squash = if (step < 0.15f) (1f - step / 0.15f) * 0.06f else 0f
            rot = 4f + sin(step * 2f * pi) * 2f
        }
        Stage.DANCE -> {                           // இசைக்கு ஆட்டம்: beat-க்கு ஒரு முறை திரும்பி குதிப்பார்
            flip = bi % 2 == 1
            hop = sin(bf * pi) * 12f
            squash = if (bf < 0.18f) (1f - bf / 0.18f) * 0.08f else 0f
            sideDp = (if (bi % 4 < 2) 1f else -1f) * 8f * sin(bf * pi)
            rot = (if (flip) -1f else 1f) * (3f + 4f * sin(bf * pi))
        }
        Stage.CHEER -> {
            hop = abs(sin(beat * pi)) * 18f
            squash = if (bf < 0.15f) 0.07f else 0f
            rot = sin(beat * pi) * 5f
        }
        Stage.DRINK -> { rot = -3f + sin(beat * pi * 0.5f) * 1.5f }
        Stage.SAD_STAND -> { rot = sin(beat * pi * 0.25f) * 1f }
        Stage.LEAVE_SAD -> {                       // மெதுவான சோக நடை
            val step = (beat * 0.9f) % 1f
            hop = sin(step * pi) * 3f
            rot = sin(step * 2f * pi) * 1.5f
        }
        Stage.DONE -> {}
    }

    val showBubble = stage in setOf(Stage.DANCE, Stage.DRINK, Stage.CHEER, Stage.SAD_STAND)
    val bubbleText = when (stage) {
        Stage.DANCE -> speech?.takeIf { it.isNotBlank() } ?: alert.message.ifBlank { AlertFlow.defaultMessage(alert.kind) }
        else -> remember(stage, positive) { AlertFlow.reactionBubble(alert, positive) }
    }
    val (yesLabel, laterLabel) = AlertFlow.buttonLabels(alert)

    fun answer(yes: Boolean) {
        if (stage != Stage.DANCE) return
        positive = yes
        onAnswer(yes)
        stage = when {
            !yes -> Stage.SAD_STAND
            water -> Stage.DRINK
            else -> Stage.CHEER
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = showBubble, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            AlertBubble(
                title = "${alert.kind.emoji} ${alert.displayTitle}",
                text = bubbleText,
                yes = yesLabel, later = laterLabel,
                showButtons = stage == Stage.DANCE,
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
                val groundY = stageH - with(density) { 6.dp.toPx() }
                val hopPx = with(density) { hop.dp.toPx() }
                val sidePx = with(density) { sideDp.dp.toPx() }
                val cx = x.value + sidePx

                // தரையில நிழல் — குதிக்கும்போது சின்னதாகும் (3D உணர்வு)
                Canvas(Modifier.fillMaxSize()) {
                    val k = 1f - (hop / 30f).coerceIn(0f, 0.5f)
                    val sw = with(density) { 118.dp.toPx() } * k
                    val sh = with(density) { 18.dp.toPx() } * k
                    drawOval(
                        Color.Black.copy(alpha = 0.22f * k),
                        topLeft = Offset(cx - sw / 2f, groundY - sh / 2f),
                        size = Size(sw, sh)
                    )
                }

                val anchor = if (flip) 1f - pose.anchor else pose.anchor
                BuddySprite(
                    pose,
                    Modifier
                        .offset { IntOffset((cx - anchor * wPx).toInt(), (groundY - hPx - hopPx).toInt()) }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(anchor, 1f)
                            rotationZ = rot
                            scaleX = (if (flip) -1f else 1f) * (1f + squash * 0.6f)
                            scaleY = 1f - squash
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
