package com.waterbuddy.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** தேர்ந்தெடுக்கக்கூடிய 5 கேரக்டர்கள் (நம்ம சொந்த design). */
enum class BuddyCharacter(val key: String, val displayName: String, val tagline: String) {
    KUTTY("kutty", "குட்டி", "Mass dancer 😎"),
    MALAR("malar", "மலர்", "Classy dancer 💃"),
    PAATI("paati", "பாட்டி", "Cute paati 👵"),
    CHELLAM("chellam", "செல்லம்", "Little star ⭐"),
    RAJA("raja", "ராஜா", "Gym boy 💪");

    companion object {
        fun fromKey(k: String?): BuddyCharacter? = entries.firstOrNull { it.key == k }
    }
}

/** கேரக்டரோட மனநிலை / செயல் */
enum class Mood { IDLE, ASKING, DANCING, RUNNING, HAPPY, SAD, SLEEPY }

private enum class HairStyle { SPIKY, BRAID, BUN, PONYTAILS, BUZZ }
private enum class Outfit { SHIRT, KURTI, SAREE, FROCK, TANK }

private class Look(
    val skin: Color, val skinShade: Color, val hair: Color,
    val top: Color, val topDark: Color, val bottom: Color, val shoe: Color,
    val hairStyle: HairStyle, val outfit: Outfit,
    val mustache: Boolean = false, val beard: Boolean = false, val specs: Boolean = false,
    val bindi: Boolean = false, val earrings: Boolean = false, val bangles: Boolean = false,
    val flowers: Boolean = false, val shades: Boolean = false, val sneaker: Boolean = false,
    val lashes: Boolean = false, val kid: Boolean = false, val muscle: Boolean = false,
    val accent: Color = Color(0xFFE53935)
)

private fun lookOf(c: BuddyCharacter) = when (c) {
    BuddyCharacter.KUTTY -> Look(
        skin = Color(0xFFE2AE85), skinShade = Color(0xFFC98F66), hair = Color(0xFF1C1716),
        top = Brand.Primary, topDark = Brand.PrimaryDark, bottom = Color(0xFF2B3A55), shoe = Color(0xFFF4F6FA),
        hairStyle = HairStyle.SPIKY, outfit = Outfit.SHIRT, mustache = true, shades = true, sneaker = true
    )
    BuddyCharacter.MALAR -> Look(
        skin = Color(0xFFF0C49C), skinShade = Color(0xFFD9A57B), hair = Color(0xFF2A1A18),
        top = Brand.Accent, topDark = Color(0xFFD24E25), bottom = Color(0xFFF6E7C8), shoe = Color(0xFFB23A2C),
        hairStyle = HairStyle.BRAID, outfit = Outfit.KURTI,
        bindi = true, earrings = true, bangles = true, flowers = true, lashes = true
    )
    BuddyCharacter.PAATI -> Look(
        skin = Color(0xFFDDA67C), skinShade = Color(0xFFC28A61), hair = Color(0xFFD9D9D9),
        top = Color(0xFF2E7D32), topDark = Color(0xFF8E1B3A), bottom = Color(0xFF8E1B3A), shoe = Color(0xFF6D4C41),
        hairStyle = HairStyle.BUN, outfit = Outfit.SAREE,
        specs = true, bindi = true, earrings = true, bangles = true, lashes = true
    )
    BuddyCharacter.CHELLAM -> Look(
        skin = Color(0xFFF2C8A2), skinShade = Color(0xFFDDAA80), hair = Color(0xFF2B1B17),
        top = Color(0xFFE91E63), topDark = Color(0xFFC2185B), bottom = Color(0xFFF2C8A2), shoe = Color(0xFFFFC107),
        hairStyle = HairStyle.PONYTAILS, outfit = Outfit.FROCK,
        lashes = true, kid = true, accent = Color(0xFFFFEB3B)
    )
    BuddyCharacter.RAJA -> Look(
        skin = Color(0xFFC98D62), skinShade = Color(0xFFAD7349), hair = Color(0xFF151112),
        top = Color(0xFF263238), topDark = Color(0xFF263238), bottom = Color(0xFF37474F), shoe = Color(0xFFF4F6FA),
        hairStyle = HairStyle.BUZZ, outfit = Outfit.TANK,
        beard = true, shades = true, sneaker = true, muscle = true, accent = Color(0xFFE53935)
    )
}

private val MouthRed = Color(0xFF8E2B2B)
private val TearBlue = Color(0xFF5DADE2)
private val Ink = Color(0xFF231F20)
private val Gold = Color(0xFFFFC107)

private data class Pose(
    val lElbow: Offset, val lHand: Offset,
    val rElbow: Offset, val rHand: Offset,
    val glassTilt: Float
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerp(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))
private fun lerp(a: Pose, b: Pose, t: Float) = Pose(
    lerp(a.lElbow, b.lElbow, t), lerp(a.lHand, b.lHand, t),
    lerp(a.rElbow, b.rElbow, t), lerp(a.rHand, b.rHand, t),
    lerp(a.glassTilt, b.glassTilt, t)
)

private fun o(x: Float, y: Float) = Offset(x, y)

/** Dance step-கள் — ஒவ்வொரு beat-க்கும் ஒரு pose (8 beat = ஒரு சுற்று). */
private val DanceKeys = listOf(
    Pose(o(52f, 165f), o(70f, 190f), o(150f, 100f), o(172f, 58f), -10f),
    Pose(o(50f, 100f), o(28f, 58f), o(150f, 165f), o(132f, 190f), 10f),
    Pose(o(44f, 132f), o(18f, 118f), o(156f, 132f), o(182f, 118f), -6f),
    Pose(o(52f, 112f), o(44f, 70f), o(148f, 112f), o(156f, 70f), 12f),
    Pose(o(48f, 166f), o(70f, 188f), o(152f, 166f), o(130f, 188f), 0f),
    Pose(o(48f, 162f), o(70f, 186f), o(152f, 162f), o(130f, 186f), 0f),
    Pose(o(42f, 135f), o(14f, 135f), o(158f, 135f), o(186f, 135f), 0f),
    Pose(o(42f, 135f), o(14f, 138f), o(158f, 135f), o(186f, 132f), 0f)
)

private fun ease(t: Float) = t * t * (3f - 2f * t)

private fun poseFor(m: Mood, ph: Float, beat: Float): Pose = when (m) {
    Mood.IDLE -> Pose(o(60f, 172f), o(62f, 205f), o(146f, 172f), o(141f, 150f + sin(ph) * 2f), 0f)
    Mood.ASKING -> Pose(
        o(50f, 168f), o(68f, 192f),
        o(154f, 150f), o(167f, 116f + sin(ph * 2f) * 5f), sin(ph * 2f) * 8f
    )
    Mood.DANCING -> {
        val i = floor(beat).toInt().coerceIn(0, 7)
        lerp(DanceKeys[i], DanceKeys[(i + 1) % 8], ease(beat - i))
    }
    Mood.RUNNING -> {   // கைகள் மாத்தி மாத்தி வீசும்
        val s = sin(ph * 3f)
        Pose(o(56f, 164f), o(66f, 180f - 24f * s), o(144f, 164f), o(134f, 180f + 24f * s), s * 12f)
    }
    Mood.HAPPY -> Pose(
        o(52f, 112f), o(42f, 76f + sin(ph * 2f) * 6f),
        o(148f, 112f), o(158f, 72f + cos(ph * 2f) * 6f), sin(ph * 2f) * 14f
    )
    Mood.SAD -> Pose(o(62f, 176f), o(66f, 212f), o(138f, 176f), o(134f, 212f), 28f)
    Mood.SLEEPY -> Pose(o(62f, 176f), o(64f, 208f), o(138f, 176f), o(136f, 208f), 0f)
}

/**
 * Water Buddy கேரக்டர் — முழுக்க code-ல வரையப்பட்டது (image file இல்லை, app size சின்னது).
 */
@Composable
fun WaterBuddy(
    mood: Mood,
    character: BuddyCharacter,
    modifier: Modifier = Modifier,
    glassFill: Float = 0.75f
) {
    val look = lookOf(character)
    val inf = rememberInfiniteTransition(label = "buddy")
    val phase by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase"
    )
    val beat by inf.animateFloat(
        0f, 8f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "beat"
    )
    val blink by inf.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 3800
            1f at 3400
            0.08f at 3480
            1f at 3560
        }), label = "blink"
    )
    val happiness by animateFloatAsState(
        when (mood) {
            Mood.HAPPY -> 1f; Mood.DANCING -> 0.7f; Mood.RUNNING -> 0.5f; Mood.SAD -> -1f; else -> 0f
        },
        tween(500), label = "happiness"
    )
    val baseTilt by animateFloatAsState(
        when (mood) { Mood.SAD -> -7f; Mood.SLEEPY -> 12f; else -> 0f },
        tween(600), label = "tilt"
    )
    val lean by animateFloatAsState(if (mood == Mood.RUNNING) 8f else 0f, tween(300), label = "lean")
    val fill by animateFloatAsState(glassFill, tween(900), label = "fill")

    val poseProgress = remember { Animatable(1f) }
    var fromMood by remember { mutableStateOf(mood) }
    var toMood by remember { mutableStateOf(mood) }
    LaunchedEffect(mood) {
        if (mood != toMood) {
            fromMood = toMood
            toMood = mood
            poseProgress.snapTo(0f)
            poseProgress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 170f))
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier.aspectRatio(200f / 300f)) {
        val u = size.width / 200f
        val joy = happiness.coerceAtLeast(0f)
        val sadness = (-happiness).coerceAtLeast(0f)
        val dancing = mood == Mood.DANCING
        val running = mood == Mood.RUNNING
        val bi = floor(beat).toInt().coerceIn(0, 7)
        val bf = beat - bi
        val runS = sin(phase * 3f)

        val dy = when (mood) {
            Mood.HAPPY -> -abs(sin(phase * 2f)) * 20f
            Mood.DANCING -> -abs(sin(PI.toFloat() * beat)) * 9f
            Mood.RUNNING -> -abs(runS) * 7f
            Mood.SAD -> 5f + sin(phase) * 1.2f
            Mood.SLEEPY -> 3f + sin(phase * 0.5f) * 2f
            else -> sin(phase) * 2.5f
        }
        val dx = if (dancing) sin(PI.toFloat() * beat / 2f) * 9f else 0f
        val spin = if (dancing && beat >= 6f) cos(PI.toFloat() * (beat - 6f)) else 1f
        val stepLift = if (dancing && bi < 6) sin(PI.toFloat() * bf) * 14f else 0f
        val liftL = when {
            running -> runS.coerceAtLeast(0f) * 18f
            bi % 2 == 0 -> stepLift
            else -> 0f
        }
        val liftR = when {
            running -> (-runS).coerceAtLeast(0f) * 18f
            bi % 2 == 1 -> stepLift
            else -> 0f
        }

        fun S(v: Float) = v * u
        fun X(v: Float) = (v + dx) * u
        fun Y(v: Float) = (v + dy) * u
        fun P(x: Float, y: Float) = Offset(X(x), Y(y))
        fun PO(p: Offset) = P(p.x, p.y)

        // ── நிழல்
        val kidK = if (look.kid) 0.82f else 1f
        val shW = S((92f + dy * 1.6f) * kidK)
        drawOval(
            Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(X(100f) - shW / 2f, S(283f)),
            size = Size(shW, S(10f))
        )
        // ஓடும்போது புழுதி 💨
        if (running) {
            val t = (phase / (2f * PI.toFloat()) * 3f) % 1f
            for (k in 0 until 3) {
                val kk = (t + k / 3f) % 1f
                drawCircle(Color(0xFFB0BEC5).copy(alpha = 0.5f * (1f - kk)), S(5f + kk * 7f), P(70f - kk * 40f, 278f - kk * 10f))
            }
        }

        val spinS = spin.coerceIn(-1f, 1f).let { if (abs(it) < 0.06f) 0.06f else it }
        withTransform({
            rotate(lean, pivot = P(100f, 280f))
            scale(spinS, 1f, pivot = P(100f, 160f))
            scale(kidK, kidK, pivot = P(100f, 284f))
        }) {

            // ── பின்னல் (மலர்) — உடம்புக்கு பின்னால
            if (look.hairStyle == HairStyle.BRAID) {
                drawRoundRect(look.hair, P(56f, 62f), Size(S(88f), S(78f)), CornerRadius(S(30f)))
                for (k in 0 until 6) {
                    val sw = sin(phase + k * 0.6f) * 1.5f
                    drawOval(look.hair, P(127f + sw, 118f + k * 13f), Size(S(15f), S(16f)))
                }
                drawCircle(look.accent, S(4f), P(134f, 198f))
            }

            // ── கால்கள்
            val legColor = when (look.outfit) { Outfit.TANK, Outfit.FROCK -> look.skin; else -> look.bottom }
            drawRoundRect(legColor, P(80f, 200f), Size(S(17f), S(74f - liftL)), CornerRadius(S(6f)))
            drawRoundRect(legColor, P(103f, 200f), Size(S(17f), S(74f - liftR)), CornerRadius(S(6f)))
            if (look.outfit == Outfit.TANK) {   // shorts
                drawRoundRect(look.bottom, P(77f, 200f), Size(S(23f), S(38f)), CornerRadius(S(5f)))
                drawRoundRect(look.bottom, P(100f, 200f), Size(S(23f), S(38f)), CornerRadius(S(5f)))
                drawLine(look.accent, P(79f, 236f), P(98f, 236f), S(2.5f))
                drawLine(look.accent, P(102f, 236f), P(121f, 236f), S(2.5f))
            }
            if (look.outfit == Outfit.FROCK) {  // socks
                drawRect(Color.White, P(80f, 256f - liftL), Size(S(17f), S(12f)))
                drawRect(Color.White, P(103f, 256f - liftR), Size(S(17f), S(12f)))
            }
            drawOval(look.shoe, P(74f, 268f - liftL), Size(S(28f), S(12f)))
            drawOval(look.shoe, P(98f, 268f - liftR), Size(S(28f), S(12f)))
            if (look.sneaker) {
                drawOval(Brand.Primary, P(78f, 272f - liftL), Size(S(20f), S(4f)))
                drawOval(Brand.Primary, P(102f, 272f - liftR), Size(S(20f), S(4f)))
            }

            // ── கழுத்து & உடை
            drawRect(look.skinShade, P(93f, 112f), Size(S(14f), S(20f)))
            when (look.outfit) {
                Outfit.SHIRT -> {
                    drawRoundRect(look.top, P(70f, 126f), Size(S(60f), S(88f)), CornerRadius(S(22f)))
                    drawPath(
                        Path().apply { moveTo(X(90f), Y(127f)); lineTo(X(100f), Y(140f)); lineTo(X(110f), Y(127f)) },
                        Color.White.copy(alpha = 0.75f), style = Stroke(S(3f), cap = StrokeCap.Round)
                    )
                    drawDrop(this, P(100f, 158f), u, Color.White.copy(alpha = 0.85f))
                }
                Outfit.KURTI -> {
                    drawPath(Path().apply {
                        moveTo(X(76f), Y(128f)); lineTo(X(124f), Y(128f))
                        quadraticTo(X(132f), Y(130f), X(132f), Y(142f))
                        lineTo(X(140f), Y(222f)); lineTo(X(60f), Y(222f)); lineTo(X(68f), Y(142f))
                        quadraticTo(X(68f), Y(130f), X(76f), Y(128f)); close()
                    }, look.top)
                    drawLine(Brand.Secondary, P(74f, 132f), P(130f, 205f), S(10f), StrokeCap.Round)
                    drawPath(
                        Path().apply { moveTo(X(90f), Y(128f)); quadraticTo(X(100f), Y(142f), X(110f), Y(128f)) },
                        Gold, style = Stroke(S(2.5f))
                    )
                }
                Outfit.SAREE -> {
                    drawPath(Path().apply {
                        moveTo(X(76f), Y(128f)); lineTo(X(124f), Y(128f))
                        quadraticTo(X(132f), Y(130f), X(132f), Y(142f))
                        lineTo(X(138f), Y(270f)); lineTo(X(62f), Y(270f)); lineTo(X(68f), Y(142f))
                        quadraticTo(X(68f), Y(130f), X(76f), Y(128f)); close()
                    }, look.top)
                    drawLine(Gold, P(63f, 264f), P(137f, 264f), S(3f))
                    // முந்தானை
                    drawLine(look.topDark, P(78f, 130f), P(134f, 220f), S(18f), StrokeCap.Round)
                    drawLine(Gold, P(84f, 126f), P(140f, 214f), S(2.5f), StrokeCap.Round)
                }
                Outfit.FROCK -> {
                    drawPath(Path().apply {
                        moveTo(X(78f), Y(128f)); lineTo(X(122f), Y(128f))
                        quadraticTo(X(130f), Y(130f), X(130f), Y(140f))
                        lineTo(X(148f), Y(214f))
                        quadraticTo(X(100f), Y(226f), X(52f), Y(214f))
                        lineTo(X(70f), Y(140f))
                        quadraticTo(X(70f), Y(130f), X(78f), Y(128f)); close()
                    }, look.top)
                    drawLine(look.topDark, P(70f, 164f), P(130f, 164f), S(5f))
                    for ((px, py) in listOf(80f to 190f, 100f to 200f, 120f to 188f, 92f to 178f, 110f to 182f)) {
                        drawCircle(Color.White.copy(alpha = 0.8f), S(3f), P(px, py))
                    }
                    drawOval(Color.White, P(86f, 124f), Size(S(14f), S(9f)))
                    drawOval(Color.White, P(100f, 124f), Size(S(14f), S(9f)))
                }
                Outfit.TANK -> {
                    drawRoundRect(look.top, P(74f, 126f), Size(S(52f), S(84f)), CornerRadius(S(16f)))
                    drawOval(look.skinShade, P(90f, 120f), Size(S(20f), S(14f)))
                    drawDrop(this, P(100f, 156f), u, Brand.Secondary)
                }
            }

            // ── தலை
            val wobble = when (mood) {
                Mood.ASKING -> sin(phase * 2f) * 4f
                Mood.HAPPY -> sin(phase * 2f) * 7f
                Mood.DANCING -> sin(PI.toFloat() * beat) * 8f
                Mood.RUNNING -> runS * 3f
                Mood.IDLE -> sin(phase) * 2f
                else -> sin(phase) * 1f
            }
            withTransform({
                translate(0f, S(sadness * 6f))
                rotate(baseTilt + wobble, pivot = P(100f, 120f))
            }) {
                // தலைக்கு பின்னால வரும் முடி
                when (look.hairStyle) {
                    HairStyle.BUN -> drawCircle(look.hair, S(15f), P(100f, 40f))
                    HairStyle.PONYTAILS -> {
                        val sw = sin(phase * 2f) * 3f
                        drawOval(look.hair, P(36f, 58f + sw), Size(S(24f), S(38f)))
                        drawOval(look.hair, P(140f, 58f - sw), Size(S(24f), S(38f)))
                        drawCircle(look.accent, S(6f), P(58f, 64f))
                        drawCircle(look.accent, S(6f), P(142f, 64f))
                    }
                    else -> {}
                }

                drawCircle(look.skin, S(8f), P(61f, 86f))
                drawCircle(look.skin, S(8f), P(139f, 86f))
                drawCircle(look.skinShade, S(4f), P(61f, 86f))
                drawCircle(look.skinShade, S(4f), P(139f, 86f))
                if (look.earrings) {
                    drawCircle(Gold, S(3.5f), P(61f, 96f))
                    drawCircle(Gold, S(3.5f), P(139f, 96f))
                }
                drawCircle(look.skin, S(40f), P(100f, 82f))

                // முன் முடி
                when (look.hairStyle) {
                    HairStyle.BRAID, HairStyle.BUN -> {
                        drawPath(Path().apply {
                            moveTo(X(60f), Y(84f))
                            cubicTo(X(54f), Y(32f), X(146f), Y(32f), X(140f), Y(84f))
                            quadraticTo(X(132f), Y(58f), X(100f), Y(54f))
                            quadraticTo(X(68f), Y(58f), X(60f), Y(84f))
                            close()
                        }, look.hair)
                        if (look.flowers) {
                            for (k in 0 until 5) drawCircle(Color.White, S(3.6f), P(128f + k * 3.2f, 50f + k * 5.5f))
                        }
                    }
                    HairStyle.SPIKY -> drawPath(Path().apply {
                        moveTo(X(60f), Y(84f))
                        cubicTo(X(54f), Y(52f), X(64f), Y(40f), X(74f), Y(38f))
                        lineTo(X(80f), Y(26f)); lineTo(X(90f), Y(36f))
                        lineTo(X(100f), Y(22f)); lineTo(X(110f), Y(35f))
                        lineTo(X(122f), Y(26f)); lineTo(X(126f), Y(40f))
                        cubicTo(X(140f), Y(46f), X(146f), Y(60f), X(140f), Y(84f))
                        lineTo(X(136f), Y(68f))
                        quadraticTo(X(118f), Y(54f), X(100f), Y(60f))
                        quadraticTo(X(80f), Y(52f), X(66f), Y(66f))
                        lineTo(X(60f), Y(84f))
                        close()
                    }, look.hair)
                    HairStyle.PONYTAILS -> drawPath(Path().apply {
                        moveTo(X(60f), Y(84f))
                        cubicTo(X(54f), Y(32f), X(146f), Y(32f), X(140f), Y(84f))
                        lineTo(X(136f), Y(70f))
                        quadraticTo(X(128f), Y(58f), X(118f), Y(64f))
                        quadraticTo(X(110f), Y(56f), X(100f), Y(64f))
                        quadraticTo(X(90f), Y(56f), X(82f), Y(64f))
                        quadraticTo(X(72f), Y(58f), X(64f), Y(70f))
                        lineTo(X(60f), Y(84f))
                        close()
                    }, look.hair)
                    HairStyle.BUZZ -> {
                        drawPath(Path().apply {
                            moveTo(X(62f), Y(78f))
                            cubicTo(X(58f), Y(36f), X(142f), Y(36f), X(138f), Y(78f))
                            quadraticTo(X(120f), Y(60f), X(100f), Y(62f))
                            quadraticTo(X(80f), Y(60f), X(62f), Y(78f))
                            close()
                        }, look.hair)
                        drawRoundRect(look.accent, P(62f, 58f), Size(S(76f), S(9f)), CornerRadius(S(4f)))
                    }
                }

                // கண்கள்
                val eyeY = 84f + sadness * 2f
                val eyeK = if (look.kid) 1.25f else 1f
                for (ex in listOf(85f, 115f)) {
                    when {
                        mood == Mood.SLEEPY -> drawPath(
                            Path().apply { moveTo(X(ex - 6f), Y(eyeY)); quadraticTo(X(ex), Y(eyeY + 5f), X(ex + 6f), Y(eyeY)) },
                            Ink, style = Stroke(S(3f), cap = StrokeCap.Round)
                        )
                        joy > 0.55f -> drawPath(
                            Path().apply { moveTo(X(ex - 6f), Y(eyeY + 3f)); quadraticTo(X(ex), Y(eyeY - 7f), X(ex + 6f), Y(eyeY + 3f)) },
                            Ink, style = Stroke(S(3.2f), cap = StrokeCap.Round)
                        )
                        else -> {
                            val h = 12f * blink * eyeK
                            val w = 9f * eyeK
                            drawOval(Ink, Offset(X(ex - w / 2f), Y(eyeY - h / 2f)), Size(S(w), S(h)))
                            if (blink > 0.5f) drawCircle(Color.White, S(1.8f * eyeK), P(ex + 1.5f, eyeY - 2.5f))
                            if (look.lashes && blink > 0.5f) {
                                drawLine(Ink, P(ex + 3f, eyeY - 5f), P(ex + 7f, eyeY - 8f), S(1.6f), StrokeCap.Round)
                            }
                        }
                    }
                }

                // கண்ணாடி 👓 (பாட்டி)
                if (look.specs) {
                    val fc = Color(0xFF6D4C41)
                    drawCircle(fc, S(10f), P(85f, eyeY), style = Stroke(S(2.2f)))
                    drawCircle(fc, S(10f), P(115f, eyeY), style = Stroke(S(2.2f)))
                    drawLine(fc, P(95f, eyeY - 1f), P(105f, eyeY - 1f), S(2f))
                }

                // 😎 Mass sunglasses
                if (look.shades && (mood == Mood.DANCING || mood == Mood.HAPPY || mood == Mood.RUNNING)) {
                    drawRoundRect(Color(0xFF111111), P(73f, 77f), Size(S(23f), S(14f)), CornerRadius(S(5f)))
                    drawRoundRect(Color(0xFF111111), P(104f, 77f), Size(S(23f), S(14f)), CornerRadius(S(5f)))
                    drawLine(Color(0xFF111111), P(95f, 81f), P(105f, 81f), S(3f))
                    drawLine(Color.White.copy(alpha = 0.6f), P(77f, 88f), P(83f, 80f), S(2f), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.6f), P(108f, 88f), P(114f, 80f), S(2f), StrokeCap.Round)
                }

                // புருவம்
                if (mood != Mood.SLEEPY) {
                    val lift = sadness * 6f
                    val up = joy * 3f
                    val bw = if (look.lashes) 2.6f else 3.8f
                    val bc = if (look.hairStyle == HairStyle.BUN) Color(0xFF9E9E9E) else look.hair
                    drawLine(bc, P(76f, 70f - up + lift * 0.3f), P(92f, 68f - lift - up), S(bw), StrokeCap.Round)
                    drawLine(bc, P(108f, 68f - lift - up), P(124f, 70f - up + lift * 0.3f), S(bw), StrokeCap.Round)
                }
                if (look.bindi) drawCircle(Color(0xFFD32F2F), S(2.6f), P(100f, 70f))

                // மூக்கு
                drawPath(
                    Path().apply { moveTo(X(100f), Y(88f)); quadraticTo(X(104f), Y(95f), X(98f), Y(96f)) },
                    look.skinShade, style = Stroke(S(2.5f), cap = StrokeCap.Round)
                )
                // தாடி
                if (look.beard) {
                    drawPath(Path().apply {
                        moveTo(X(63f), Y(88f))
                        quadraticTo(X(66f), Y(124f), X(100f), Y(124f))
                        quadraticTo(X(134f), Y(124f), X(137f), Y(88f))
                        quadraticTo(X(126f), Y(104f), X(100f), Y(110f))
                        quadraticTo(X(74f), Y(104f), X(63f), Y(88f))
                        close()
                    }, look.hair.copy(alpha = 0.85f))
                }
                if (look.mustache || look.beard) {
                    drawPath(Path().apply {
                        moveTo(X(88f), Y(101f))
                        quadraticTo(X(94f), Y(95f), X(100f), Y(99f))
                        quadraticTo(X(106f), Y(95f), X(112f), Y(101f))
                        quadraticTo(X(100f), Y(99f), X(88f), Y(101f))
                        close()
                    }, look.hair)
                }
                val cheek = Color(0xFFFF7F7F).copy(alpha = (if (look.lashes) 0.35f else 0.2f) + 0.35f * joy)
                drawCircle(cheek, S(7f), P(76f, 98f))
                drawCircle(cheek, S(7f), P(124f, 98f))

                // வாய்
                val mouthY = if (look.mustache || look.beard) 104f else 102f
                when {
                    mood == Mood.ASKING -> {
                        val open = 5f + abs(sin(phase * 3f)) * 7f
                        drawOval(MouthRed, Offset(X(93f), Y(mouthY + 2f - open / 2f)), Size(S(14f), S(open)))
                    }
                    mood == Mood.SLEEPY -> drawCircle(MouthRed, S(3f), P(100f, mouthY + 2f))
                    happiness > 0.25f -> {
                        val depth = 6f + 10f * joy
                        val smile = Path().apply {
                            moveTo(X(87f), Y(mouthY))
                            quadraticTo(X(100f), Y(mouthY + depth * 2f), X(113f), Y(mouthY))
                            close()
                        }
                        drawPath(smile, MouthRed)
                        clipPath(smile) { drawRect(Color.White, P(86f, mouthY), Size(S(28f), S(3.5f))) }
                    }
                    else -> {
                        val endY = mouthY + 3f * sadness
                        val ctrlY = mouthY + 4f - 16f * sadness
                        drawPath(
                            Path().apply { moveTo(X(89f), Y(endY)); quadraticTo(X(100f), Y(ctrlY), X(111f), Y(endY)) },
                            MouthRed, style = Stroke(S(3f), cap = StrokeCap.Round)
                        )
                    }
                }

                // கண்ணீர் 😢
                if (sadness > 0.4f) {
                    val a = ((sadness - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    listOf(83f, 117f).forEachIndexed { i, ex ->
                        val t = ((phase / (2f * PI.toFloat())) + i * 0.5f) % 1f
                        val ty = 92f + t * 34f
                        val c = TearBlue.copy(alpha = a * (1f - t * 0.6f))
                        drawPath(Path().apply {
                            moveTo(X(ex), Y(ty - 6f)); lineTo(X(ex - 3f), Y(ty - 1f)); lineTo(X(ex + 3f), Y(ty - 1f)); close()
                        }, c)
                        drawCircle(c, S(3.2f), P(ex, ty))
                    }
                }
            }

            // ── கைகள் & கிளாஸ்
            val pose = lerp(poseFor(fromMood, phase, beat), poseFor(toMood, phase, beat), poseProgress.value)
            val upperW = if (look.muscle) 17f else 14f
            val foreW = if (look.muscle) 13f else 11f
            val sleeve = if (look.outfit == Outfit.TANK) look.skin else look.topDark
            fun limb(shoulder: Offset, elbow: Offset, hand: Offset) {
                drawLine(sleeve, shoulder, elbow, S(upperW), StrokeCap.Round)
                drawLine(look.skin, elbow, hand, S(foreW), StrokeCap.Round)
            }
            limb(P(74f, 140f), PO(pose.lElbow), PO(pose.lHand))
            drawCircle(look.skin, S(7.5f), PO(pose.lHand))
            if (look.bangles) drawCircle(Gold, S(8.5f), PO(lerp(pose.lElbow, pose.lHand, 0.8f)), style = Stroke(S(2.2f)))

            limb(P(126f, 140f), PO(pose.rElbow), PO(pose.rHand))
            val gh = PO(pose.rHand)
            rotate(pose.glassTilt, pivot = gh) {
                val top = gh.y - S(18f)
                val bot = gh.y + S(12f)
                val glass = Path().apply {
                    moveTo(gh.x - S(11f), top); lineTo(gh.x + S(11f), top)
                    lineTo(gh.x + S(8f), bot); lineTo(gh.x - S(8f), bot); close()
                }
                drawPath(glass, Color(0xFFE3F6FD).copy(alpha = 0.7f))
                clipPath(glass) {
                    val level = bot - (bot - top) * fill
                    val wave = sin(phase * 2f) * S(1.5f)
                    drawPath(Path().apply {
                        moveTo(gh.x - S(14f), level + wave)
                        quadraticTo(gh.x, level - wave * 2f, gh.x + S(14f), level + wave)
                        lineTo(gh.x + S(14f), bot + S(2f)); lineTo(gh.x - S(14f), bot + S(2f)); close()
                    }, Brand.Water)
                }
                drawPath(glass, Brand.WaterDeep.copy(alpha = 0.75f), style = Stroke(S(2f)))
                drawLine(Color.White.copy(alpha = 0.8f), Offset(gh.x - S(7f), top + S(4f)), Offset(gh.x - S(5.5f), bot - S(4f)), S(2f))
            }
            drawCircle(look.skin, S(7.5f), gh)
        }

        // ── சந்தோஷ நட்சத்திரங்கள் ✨
        if (joy > 0.05f && !dancing && !running) {
            for (i in 0 until 5) {
                val ang = phase + i * (2f * PI.toFloat() / 5f)
                val r = 64f + sin(phase * 3f + i) * 6f
                val c = Offset(X(100f + cos(ang) * r), Y(82f + sin(ang) * r * 0.8f))
                val s = S(5f + 2f * sin(phase * 4f + i))
                val col = (if (i % 2 == 0) Gold else Brand.Water).copy(alpha = joy)
                drawPath(Path().apply {
                    moveTo(c.x, c.y - s)
                    quadraticTo(c.x, c.y, c.x + s, c.y)
                    quadraticTo(c.x, c.y, c.x, c.y + s)
                    quadraticTo(c.x, c.y, c.x - s, c.y)
                    quadraticTo(c.x, c.y, c.x, c.y - s)
                    close()
                }, col)
            }
        }

        // ── Dance-ல மிதக்கும் இசை குறிகள் ♪
        if (dancing) {
            val t = beat / 8f
            val notes = listOf("♪", "♫", "♪", "♬")
            for (i in 0 until 4) {
                val k = (t * 2f + i / 4f) % 1f
                val side = if (i % 2 == 0) -1f else 1f
                drawText(
                    textMeasurer, notes[i],
                    topLeft = Offset(S(100f + side * (70f + sin(k * 6f) * 8f)) - S(8f), S(150f - k * 130f)),
                    style = TextStyle(
                        color = (if (i % 2 == 0) Brand.Primary else Brand.Accent).copy(alpha = 1f - k),
                        fontSize = (18f + k * 8f).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // ── தூக்கம் Zzz
        if (mood == Mood.SLEEPY) {
            val t = phase / (2f * PI.toFloat())
            for (i in 0..2) {
                val k = (t + i / 3f) % 1f
                drawText(
                    textMeasurer, "z",
                    topLeft = Offset(X(140f + k * 30f), Y(48f - k * 40f)),
                    style = TextStyle(
                        color = Color(0xFF5D6D7E).copy(alpha = 1f - k),
                        fontSize = (14f + k * 10f).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/** சட்டையில வரும் தண்ணி துளி logo. top = துளியோட மேல் முனை. */
private fun drawDrop(scope: androidx.compose.ui.graphics.drawscope.DrawScope, top: Offset, u: Float, color: Color) {
    val x = top.x
    val y = top.y
    scope.drawPath(Path().apply {
        moveTo(x, y)
        cubicTo(x + 4f * u, y + 7f * u, x + 8f * u, y + 10f * u, x + 8f * u, y + 18f * u)
        cubicTo(x + 8f * u, y + 23f * u, x + 4f * u, y + 26f * u, x, y + 26f * u)
        cubicTo(x - 4f * u, y + 26f * u, x - 8f * u, y + 23f * u, x - 8f * u, y + 18f * u)
        cubicTo(x - 8f * u, y + 10f * u, x - 4f * u, y + 7f * u, x, y)
        close()
    }, color)
}
