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
import kotlin.math.sin

/** Buddy-ஓட மனநிலை */
enum class Mood { IDLE, ASKING, HAPPY, SAD, SLEEPY }

private val Skin = Color(0xFFF2C29B)
private val SkinShade = Color(0xFFE0A97F)
private val Hair = Color(0xFF3B2A25)
private val Shirt = Color(0xFF1ABC9C)
private val ShirtDark = Color(0xFF16A085)
private val Pants = Color(0xFF34495E)
private val Shoe = Color(0xFF22313F)
private val MouthRed = Color(0xFF8E2B2B)
private val WaterBlue = Color(0xFF4FC3F7)
private val TearBlue = Color(0xFF5DADE2)
private val Ink = Color(0xFF2B2B2B)
private val Gold = Color(0xFFFFC107)

/** கை நிலைகள் (200 x 300 unit canvas-ல). */
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

private fun poseFor(m: Mood, ph: Float): Pose = when (m) {
    // கையில கிளாஸ் வச்சுக்கிட்டு சும்மா நிக்கிறார்
    Mood.IDLE -> Pose(
        Offset(60f, 172f), Offset(62f, 205f),
        Offset(146f, 172f), Offset(141f, 150f + sin(ph) * 2f), 0f
    )
    // கிளாஸை நீட்டி "குடிங்க!" ன்னு ஆட்டுறார், இன்னொரு கை இடுப்புல
    Mood.ASKING -> Pose(
        Offset(50f, 168f), Offset(68f, 192f),
        Offset(154f, 150f), Offset(167f, 116f + sin(ph * 2f) * 5f), sin(ph * 2f) * 8f
    )
    // ரெண்டு கையும் மேல — குதிச்சு கொண்டாட்டம்!
    Mood.HAPPY -> Pose(
        Offset(52f, 112f), Offset(42f, 76f + sin(ph * 2f) * 6f),
        Offset(148f, 112f), Offset(158f, 72f + cos(ph * 2f) * 6f), sin(ph * 2f) * 14f
    )
    // கை தொங்கிப் போச்சு, கிளாஸ் சாஞ்சு போச்சு
    Mood.SAD -> Pose(
        Offset(62f, 176f), Offset(66f, 212f),
        Offset(138f, 176f), Offset(134f, 212f), 28f
    )
    Mood.SLEEPY -> Pose(
        Offset(62f, 176f), Offset(64f, 208f),
        Offset(138f, 176f), Offset(136f, 208f), 0f
    )
}

/**
 * Water Buddy கேரக்டர் — முழுக்க code-ல வரையப்பட்டது (image file எதுவும் இல்லை, app size சின்னது).
 */
@Composable
fun WaterBuddy(mood: Mood, modifier: Modifier = Modifier, glassFill: Float = 0.75f) {
    val inf = rememberInfiniteTransition(label = "buddy")
    val phase by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase"
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
        when (mood) { Mood.HAPPY -> 1f; Mood.SAD -> -1f; else -> 0f },
        tween(500), label = "happiness"
    )
    val baseTilt by animateFloatAsState(
        when (mood) { Mood.SAD -> -7f; Mood.SLEEPY -> 12f; else -> 0f },
        tween(600), label = "tilt"
    )
    val fill by animateFloatAsState(glassFill, tween(900), label = "fill")

    // Mood மாறும்போது கை நிலை bouncy-யா மாறும்
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

        val dy = when (mood) {
            Mood.HAPPY -> -abs(sin(phase * 2f)) * 20f          // குதிக்கிறார்
            Mood.SAD -> 5f + sin(phase) * 1.2f                  // தோள் தொங்கிப் போச்சு
            Mood.SLEEPY -> 3f + sin(phase * 0.5f) * 2f          // மெதுவா மூச்சு
            else -> sin(phase) * 2.5f                           // சாதாரண மூச்சு
        }

        fun X(v: Float) = v * u
        fun Y(v: Float) = (v + dy) * u
        fun P(x: Float, y: Float) = Offset(X(x), Y(y))
        fun PO(o: Offset) = P(o.x, o.y)

        // ── நிழல் (குதிக்கும்போது சின்னதாகும்)
        val shW = (92f + dy * 1.6f) * u
        drawOval(
            Color.Black.copy(alpha = 0.13f),
            topLeft = Offset(X(100f) - shW / 2f, X(283f)),
            size = Size(shW, X(10f))
        )

        // ── கால்கள் & ஷூ
        drawRoundRect(Pants, P(80f, 200f), Size(X(17f), X(74f)), CornerRadius(X(6f)))
        drawRoundRect(Pants, P(103f, 200f), Size(X(17f), X(74f)), CornerRadius(X(6f)))
        drawOval(Shoe, P(74f, 268f), Size(X(28f), X(12f)))
        drawOval(Shoe, P(98f, 268f), Size(X(28f), X(12f)))

        // ── கழுத்து & உடம்பு
        drawRect(SkinShade, P(93f, 112f), Size(X(14f), X(20f)))
        drawRoundRect(Shirt, P(70f, 126f), Size(X(60f), X(88f)), CornerRadius(X(22f)))
        drawPath(
            Path().apply { moveTo(X(90f), Y(127f)); lineTo(X(100f), Y(140f)); lineTo(X(110f), Y(127f)) },
            Color.White.copy(alpha = 0.7f), style = Stroke(X(3f), cap = StrokeCap.Round)
        )
        // சட்டையில தண்ணி துளி logo
        drawPath(Path().apply {
            moveTo(X(100f), Y(158f))
            cubicTo(X(104f), Y(165f), X(108f), Y(168f), X(108f), Y(176f))
            cubicTo(X(108f), Y(181f), X(104f), Y(184f), X(100f), Y(184f))
            cubicTo(X(96f), Y(184f), X(92f), Y(181f), X(92f), Y(176f))
            cubicTo(X(92f), Y(168f), X(96f), Y(165f), X(100f), Y(158f))
            close()
        }, Color.White.copy(alpha = 0.85f))

        // ── தலை (சாய்வு + சோகமா இருந்தா கீழே தொங்கும்)
        val wobble = when (mood) {
            Mood.ASKING -> sin(phase * 2f) * 4f
            Mood.HAPPY -> sin(phase * 2f) * 7f
            Mood.IDLE -> sin(phase) * 2f
            else -> sin(phase) * 1f
        }
        withTransform({
            translate(0f, X(sadness * 6f))
            rotate(baseTilt + wobble, pivot = P(100f, 120f))
        }) {
            // காது
            drawCircle(Skin, X(8f), P(61f, 86f))
            drawCircle(Skin, X(8f), P(139f, 86f))
            drawCircle(SkinShade, X(4f), P(61f, 86f))
            drawCircle(SkinShade, X(4f), P(139f, 86f))
            // முகம்
            drawCircle(Skin, X(40f), P(100f, 82f))
            // முடி
            drawPath(Path().apply {
                moveTo(X(60f), Y(86f))
                cubicTo(X(52f), Y(30f), X(148f), Y(30f), X(140f), Y(86f))
                lineTo(X(137f), Y(72f))
                quadraticTo(X(124f), Y(52f), X(104f), Y(58f))
                quadraticTo(X(86f), Y(50f), X(72f), Y(62f))
                quadraticTo(X(63f), Y(70f), X(60f), Y(86f))
                close()
            }, Hair)
            drawPath(
                Path().apply { moveTo(X(96f), Y(44f)); quadraticTo(X(100f), Y(30f), X(112f), Y(35f)) },
                Hair, style = Stroke(X(4f), cap = StrokeCap.Round)
            )

            // கண்கள்
            val eyeY = 84f + sadness * 2f
            for (ex in listOf(85f, 115f)) {
                when {
                    mood == Mood.SLEEPY -> drawPath(
                        Path().apply { moveTo(X(ex - 6f), Y(eyeY)); quadraticTo(X(ex), Y(eyeY + 5f), X(ex + 6f), Y(eyeY)) },
                        Ink, style = Stroke(X(3f), cap = StrokeCap.Round)
                    )
                    joy > 0.5f -> drawPath(  // ^ ^ சந்தோஷ கண்
                        Path().apply { moveTo(X(ex - 6f), Y(eyeY + 3f)); quadraticTo(X(ex), Y(eyeY - 7f), X(ex + 6f), Y(eyeY + 3f)) },
                        Ink, style = Stroke(X(3.2f), cap = StrokeCap.Round)
                    )
                    else -> {
                        val h = 12f * blink
                        drawOval(Ink, Offset(X(ex - 4.5f), Y(eyeY - h / 2f)), Size(X(9f), X(h)))
                        if (blink > 0.5f) drawCircle(Color.White, X(1.8f), P(ex + 1.5f, eyeY - 2.5f))
                    }
                }
            }

            // புருவம் (சோகத்துல நடுவுல மேல தூக்கும்)
            if (mood != Mood.SLEEPY) {
                val lift = sadness * 6f
                val up = joy * 3f
                drawLine(Hair, P(76f, 70f - up + lift * 0.3f), P(92f, 68f - lift - up), X(3.5f), StrokeCap.Round)
                drawLine(Hair, P(108f, 68f - lift - up), P(124f, 70f - up + lift * 0.3f), X(3.5f), StrokeCap.Round)
            }

            // மூக்கு & கன்னம்
            drawPath(
                Path().apply { moveTo(X(100f), Y(88f)); quadraticTo(X(104f), Y(95f), X(98f), Y(96f)) },
                SkinShade, style = Stroke(X(2.5f), cap = StrokeCap.Round)
            )
            val cheek = Color(0xFFFF7F7F).copy(alpha = 0.25f + 0.35f * joy)
            drawCircle(cheek, X(7f), P(76f, 98f))
            drawCircle(cheek, X(7f), P(124f, 98f))

            // வாய்
            when {
                mood == Mood.ASKING -> {   // பேசுறார்
                    val open = 5f + abs(sin(phase * 3f)) * 7f
                    drawOval(MouthRed, Offset(X(93f), Y(104f - open / 2f)), Size(X(14f), X(open)))
                }
                mood == Mood.SLEEPY -> drawCircle(MouthRed, X(3f), P(100f, 106f))
                happiness > 0.25f -> {     // பெரிய சிரிப்பு
                    val depth = 6f + 10f * joy
                    val smile = Path().apply {
                        moveTo(X(87f), Y(102f))
                        quadraticTo(X(100f), Y(102f + depth * 2f), X(113f), Y(102f))
                        close()
                    }
                    drawPath(smile, MouthRed)
                    clipPath(smile) { drawRect(Color.White, P(86f, 102f), Size(X(28f), X(3.5f))) }
                }
                else -> {                  // சாதாரண புன்னகை → சோக முகம்
                    val endY = 104f + 3f * sadness
                    val ctrlY = 108f - 16f * sadness
                    drawPath(
                        Path().apply { moveTo(X(89f), Y(endY)); quadraticTo(X(100f), Y(ctrlY), X(111f), Y(endY)) },
                        MouthRed, style = Stroke(X(3f), cap = StrokeCap.Round)
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
                    drawCircle(c, X(3.2f), P(ex, ty))
                }
            }
        }

        // ── கைகள் & கிளாஸ்
        val pose = lerp(poseFor(fromMood, phase), poseFor(toMood, phase), poseProgress.value)
        fun limb(shoulder: Offset, elbow: Offset, hand: Offset) {
            drawLine(ShirtDark, shoulder, elbow, X(14f), StrokeCap.Round)
            drawLine(Skin, elbow, hand, X(11f), StrokeCap.Round)
        }
        limb(P(74f, 140f), PO(pose.lElbow), PO(pose.lHand))
        drawCircle(Skin, X(7.5f), PO(pose.lHand))

        limb(P(126f, 140f), PO(pose.rElbow), PO(pose.rHand))
        val gh = PO(pose.rHand)
        rotate(pose.glassTilt, pivot = gh) {
            val top = gh.y - X(18f)
            val bot = gh.y + X(12f)
            val glass = Path().apply {
                moveTo(gh.x - X(11f), top); lineTo(gh.x + X(11f), top)
                lineTo(gh.x + X(8f), bot); lineTo(gh.x - X(8f), bot); close()
            }
            drawPath(glass, Color(0xFFE3F6FD).copy(alpha = 0.6f))
            clipPath(glass) {
                val level = bot - (bot - top) * fill
                val wave = sin(phase * 2f) * X(1.5f)
                drawPath(Path().apply {
                    moveTo(gh.x - X(14f), level + wave)
                    quadraticTo(gh.x, level - wave * 2f, gh.x + X(14f), level + wave)
                    lineTo(gh.x + X(14f), bot + X(2f)); lineTo(gh.x - X(14f), bot + X(2f)); close()
                }, WaterBlue)
            }
            drawPath(glass, Color(0xFF0288D1).copy(alpha = 0.7f), style = Stroke(X(2f)))
            drawLine(Color.White.copy(alpha = 0.8f), Offset(gh.x - X(7f), top + X(4f)), Offset(gh.x - X(5.5f), bot - X(4f)), X(2f))
        }
        drawCircle(Skin, X(7.5f), gh)

        // ── சந்தோஷ நட்சத்திரங்கள் ✨
        if (joy > 0.05f) {
            for (i in 0 until 5) {
                val ang = phase + i * (2f * PI.toFloat() / 5f)
                val r = 64f + sin(phase * 3f + i) * 6f
                val c = Offset(X(100f + cos(ang) * r), Y(82f + sin(ang) * r * 0.8f))
                val s = X(5f + 2f * sin(phase * 4f + i))
                val col = (if (i % 2 == 0) Gold else WaterBlue).copy(alpha = joy)
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
