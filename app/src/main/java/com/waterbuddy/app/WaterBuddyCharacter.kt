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

/** தேர்ந்தெடுக்கக்கூடிய 2 கேரக்டர்கள் (நம்ம சொந்த design). */
enum class BuddyCharacter(val key: String, val displayName: String, val tagline: String) {
    KUTTY("kutty", "குட்டி", "Mass dancer 😎"),
    MALAR("malar", "மலர்", "Classy dancer 💃");

    companion object {
        fun fromKey(k: String?): BuddyCharacter? = entries.firstOrNull { it.key == k }
    }
}

/** கேரக்டரோட மனநிலை */
enum class Mood { IDLE, ASKING, DANCING, HAPPY, SAD, SLEEPY }

private class Look(
    val skin: Color, val skinShade: Color, val hair: Color,
    val top: Color, val topDark: Color, val bottom: Color, val shoe: Color,
    val girl: Boolean
)

private fun lookOf(c: BuddyCharacter) = when (c) {
    BuddyCharacter.KUTTY -> Look(
        skin = Color(0xFFE2AE85), skinShade = Color(0xFFC98F66), hair = Color(0xFF1C1716),
        top = Brand.Primary, topDark = Brand.PrimaryDark, bottom = Color(0xFF2B3A55),
        shoe = Color(0xFFF4F6FA), girl = false
    )
    BuddyCharacter.MALAR -> Look(
        skin = Color(0xFFF0C49C), skinShade = Color(0xFFD9A57B), hair = Color(0xFF2A1A18),
        top = Brand.Accent, topDark = Color(0xFFD24E25), bottom = Color(0xFFF6E7C8),
        shoe = Color(0xFFB23A2C), girl = true
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
    Pose(o(52f, 165f), o(70f, 190f), o(150f, 100f), o(172f, 58f), -10f),   // 1: வலது கை மேல point ☝
    Pose(o(50f, 100f), o(28f, 58f), o(150f, 165f), o(132f, 190f), 10f),    // 2: இடது கை மேல point
    Pose(o(44f, 132f), o(18f, 118f), o(156f, 132f), o(182f, 118f), -6f),   // 3: கைகள் விரிச்சு
    Pose(o(52f, 112f), o(44f, 70f), o(148f, 112f), o(156f, 70f), 12f),     // 4: ரெண்டு கையும் மேல 🙌
    Pose(o(48f, 166f), o(70f, 188f), o(152f, 166f), o(130f, 188f), 0f),    // 5: இடுப்புல கை
    Pose(o(48f, 162f), o(70f, 186f), o(152f, 162f), o(130f, 186f), 0f),    // 6: தோள் குலுக்கல்
    Pose(o(42f, 135f), o(14f, 135f), o(158f, 135f), o(186f, 135f), 0f),    // 7: சுத்தல் 🌀
    Pose(o(42f, 135f), o(14f, 138f), o(158f, 135f), o(186f, 132f), 0f)     // 8: சுத்தல்
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
    // 120 BPM: 8 beat = 4 வினாடி (BeatPlayer-ஓட பாட்டும் இதே வேகம்)
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
        when (mood) { Mood.HAPPY -> 1f; Mood.DANCING -> 0.7f; Mood.SAD -> -1f; else -> 0f },
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
        val dancing = mood == Mood.DANCING
        val bi = floor(beat).toInt().coerceIn(0, 7)
        val bf = beat - bi

        val dy = when (mood) {
            Mood.HAPPY -> -abs(sin(phase * 2f)) * 20f
            Mood.DANCING -> -abs(sin(PI.toFloat() * beat)) * 9f
            Mood.SAD -> 5f + sin(phase) * 1.2f
            Mood.SLEEPY -> 3f + sin(phase * 0.5f) * 2f
            else -> sin(phase) * 2.5f
        }
        val dx = if (dancing) sin(PI.toFloat() * beat / 2f) * 9f else 0f
        // சுத்தல்: beat 6–8-ல உடம்பு திரும்பும் (scaleX 1 → -1 → 1)
        val spin = if (dancing && beat >= 6f) cos(PI.toFloat() * (beat - 6f)) else 1f
        // கால் தூக்குதல் (மாத்தி மாத்தி)
        val stepLift = if (dancing && bi < 6) sin(PI.toFloat() * bf) * 14f else 0f
        val liftL = if (bi % 2 == 0) stepLift else 0f
        val liftR = if (bi % 2 == 1) stepLift else 0f

        fun S(v: Float) = v * u                    // அளவு
        fun X(v: Float) = (v + dx) * u             // x நிலை
        fun Y(v: Float) = (v + dy) * u             // y நிலை
        fun P(x: Float, y: Float) = Offset(X(x), Y(y))
        fun PO(p: Offset) = P(p.x, p.y)

        // ── நிழல்
        val shW = S(92f + dy * 1.6f)
        drawOval(
            Color.Black.copy(alpha = 0.13f),
            topLeft = Offset(X(100f) - shW / 2f, S(283f)),
            size = Size(shW, S(10f))
        )

        withTransform({ scale(spin.coerceIn(-1f, 1f).let { if (abs(it) < 0.06f) 0.06f else it }, 1f, pivot = P(100f, 160f)) }) {

            // ── பின்னல் / நீள முடி (மலர்) — உடம்புக்கு பின்னால
            if (look.girl) {
                drawRoundRect(look.hair, P(56f, 62f), Size(S(88f), S(78f)), CornerRadius(S(30f)))
                for (k in 0 until 6) {
                    val sw = sin(phase + k * 0.6f) * 1.5f
                    drawOval(look.hair, P(127f + sw, 118f + k * 13f), Size(S(15f), S(16f)))
                }
                drawCircle(Color(0xFFE53935), S(4f), P(134f, 198f))
            }

            // ── கால்கள் & காலணி
            drawRoundRect(look.bottom, P(80f, 200f), Size(S(17f), S(74f - liftL)), CornerRadius(S(6f)))
            drawRoundRect(look.bottom, P(103f, 200f), Size(S(17f), S(74f - liftR)), CornerRadius(S(6f)))
            drawOval(look.shoe, P(74f, 268f - liftL), Size(S(28f), S(12f)))
            drawOval(look.shoe, P(98f, 268f - liftR), Size(S(28f), S(12f)))
            if (!look.girl) {
                drawOval(Brand.Primary, P(78f, 272f - liftL), Size(S(20f), S(4f)))
                drawOval(Brand.Primary, P(102f, 272f - liftR), Size(S(20f), S(4f)))
            }

            // ── கழுத்து & உடம்பு
            drawRect(look.skinShade, P(93f, 112f), Size(S(14f), S(20f)))
            if (look.girl) {
                // குர்தி (கீழே விரியும்)
                drawPath(Path().apply {
                    moveTo(X(76f), Y(128f)); lineTo(X(124f), Y(128f))
                    quadraticTo(X(132f), Y(130f), X(132f), Y(142f))
                    lineTo(X(140f), Y(222f)); lineTo(X(60f), Y(222f)); lineTo(X(68f), Y(142f))
                    quadraticTo(X(68f), Y(130f), X(76f), Y(128f)); close()
                }, look.top)
                // துப்பட்டா
                drawLine(Brand.Secondary, P(74f, 132f), P(130f, 205f), S(10f), StrokeCap.Round)
                // கழுத்து design
                drawPath(
                    Path().apply { moveTo(X(90f), Y(128f)); quadraticTo(X(100f), Y(142f), X(110f), Y(128f)) },
                    Gold, style = Stroke(S(2.5f))
                )
            } else {
                drawRoundRect(look.top, P(70f, 126f), Size(S(60f), S(88f)), CornerRadius(S(22f)))
                drawPath(
                    Path().apply { moveTo(X(90f), Y(127f)); lineTo(X(100f), Y(140f)); lineTo(X(110f), Y(127f)) },
                    Color.White.copy(alpha = 0.75f), style = Stroke(S(3f), cap = StrokeCap.Round)
                )
                // சட்டையில தண்ணி துளி
                drawPath(Path().apply {
                    moveTo(X(100f), Y(158f))
                    cubicTo(X(104f), Y(165f), X(108f), Y(168f), X(108f), Y(176f))
                    cubicTo(X(108f), Y(181f), X(104f), Y(184f), X(100f), Y(184f))
                    cubicTo(X(96f), Y(184f), X(92f), Y(181f), X(92f), Y(176f))
                    cubicTo(X(92f), Y(168f), X(96f), Y(165f), X(100f), Y(158f))
                    close()
                }, Color.White.copy(alpha = 0.85f))
            }

            // ── தலை
            val wobble = when (mood) {
                Mood.ASKING -> sin(phase * 2f) * 4f
                Mood.HAPPY -> sin(phase * 2f) * 7f
                Mood.DANCING -> sin(PI.toFloat() * beat) * 8f
                Mood.IDLE -> sin(phase) * 2f
                else -> sin(phase) * 1f
            }
            withTransform({
                translate(0f, S(sadness * 6f))
                rotate(baseTilt + wobble, pivot = P(100f, 120f))
            }) {
                drawCircle(look.skin, S(8f), P(61f, 86f))
                drawCircle(look.skin, S(8f), P(139f, 86f))
                drawCircle(look.skinShade, S(4f), P(61f, 86f))
                drawCircle(look.skinShade, S(4f), P(139f, 86f))
                if (look.girl) {   // தோடு
                    drawCircle(Gold, S(3.5f), P(61f, 96f))
                    drawCircle(Gold, S(3.5f), P(139f, 96f))
                }
                drawCircle(look.skin, S(40f), P(100f, 82f))

                // முடி
                if (look.girl) {
                    drawPath(Path().apply {
                        moveTo(X(60f), Y(84f))
                        cubicTo(X(54f), Y(32f), X(146f), Y(32f), X(140f), Y(84f))
                        quadraticTo(X(132f), Y(58f), X(100f), Y(54f))   // நடு வகிடு
                        quadraticTo(X(68f), Y(58f), X(60f), Y(84f))
                        close()
                    }, look.hair)
                    // மல்லிகை பூ 🌼
                    for (k in 0 until 5) drawCircle(Color.White, S(3.6f), P(128f + k * 3.2f, 50f + k * 5.5f))
                } else {
                    // spiky style
                    drawPath(Path().apply {
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
                }

                // கண்கள்
                val eyeY = 84f + sadness * 2f
                for (ex in listOf(85f, 115f)) {
                    when {
                        mood == Mood.SLEEPY -> drawPath(
                            Path().apply { moveTo(X(ex - 6f), Y(eyeY)); quadraticTo(X(ex), Y(eyeY + 5f), X(ex + 6f), Y(eyeY)) },
                            Ink, style = Stroke(S(3f), cap = StrokeCap.Round)
                        )
                        joy > 0.5f -> drawPath(
                            Path().apply { moveTo(X(ex - 6f), Y(eyeY + 3f)); quadraticTo(X(ex), Y(eyeY - 7f), X(ex + 6f), Y(eyeY + 3f)) },
                            Ink, style = Stroke(S(3.2f), cap = StrokeCap.Round)
                        )
                        else -> {
                            val h = 12f * blink
                            drawOval(Ink, Offset(X(ex - 4.5f), Y(eyeY - h / 2f)), Size(S(9f), S(h)))
                            if (blink > 0.5f) drawCircle(Color.White, S(1.8f), P(ex + 1.5f, eyeY - 2.5f))
                            if (look.girl && blink > 0.5f) {   // இமை
                                drawLine(Ink, P(ex + 3f, eyeY - 5f), P(ex + 7f, eyeY - 8f), S(1.6f), StrokeCap.Round)
                            }
                        }
                    }
                }

                // 😎 Mass sunglasses — குட்டி dance / சந்தோஷம்
                if (!look.girl && (mood == Mood.DANCING || mood == Mood.HAPPY)) {
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
                    val bw = if (look.girl) 2.6f else 3.8f
                    drawLine(look.hair, P(76f, 70f - up + lift * 0.3f), P(92f, 68f - lift - up), S(bw), StrokeCap.Round)
                    drawLine(look.hair, P(108f, 68f - lift - up), P(124f, 70f - up + lift * 0.3f), S(bw), StrokeCap.Round)
                }
                if (look.girl) drawCircle(Color(0xFFD32F2F), S(2.6f), P(100f, 70f))   // பொட்டு

                // மூக்கு, மீசை, கன்னம்
                drawPath(
                    Path().apply { moveTo(X(100f), Y(88f)); quadraticTo(X(104f), Y(95f), X(98f), Y(96f)) },
                    look.skinShade, style = Stroke(S(2.5f), cap = StrokeCap.Round)
                )
                if (!look.girl) {
                    drawPath(Path().apply {
                        moveTo(X(88f), Y(101f))
                        quadraticTo(X(94f), Y(95f), X(100f), Y(99f))
                        quadraticTo(X(106f), Y(95f), X(112f), Y(101f))
                        quadraticTo(X(100f), Y(99f), X(88f), Y(101f))
                        close()
                    }, look.hair)
                }
                val cheek = Color(0xFFFF7F7F).copy(alpha = (if (look.girl) 0.35f else 0.2f) + 0.35f * joy)
                drawCircle(cheek, S(7f), P(76f, 98f))
                drawCircle(cheek, S(7f), P(124f, 98f))

                // வாய்
                val mouthY = if (look.girl) 102f else 104f
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
            fun limb(shoulder: Offset, elbow: Offset, hand: Offset) {
                drawLine(look.topDark, shoulder, elbow, S(14f), StrokeCap.Round)
                drawLine(look.skin, elbow, hand, S(11f), StrokeCap.Round)
            }
            limb(P(74f, 140f), PO(pose.lElbow), PO(pose.lHand))
            drawCircle(look.skin, S(7.5f), PO(pose.lHand))
            if (look.girl) drawCircle(Gold, S(8.5f), PO(lerp(pose.lElbow, pose.lHand, 0.8f)), style = Stroke(S(2.2f)))  // வளையல்

            limb(P(126f, 140f), PO(pose.rElbow), PO(pose.rHand))
            val gh = PO(pose.rHand)
            rotate(pose.glassTilt, pivot = gh) {
                val top = gh.y - S(18f)
                val bot = gh.y + S(12f)
                val glass = Path().apply {
                    moveTo(gh.x - S(11f), top); lineTo(gh.x + S(11f), top)
                    lineTo(gh.x + S(8f), bot); lineTo(gh.x - S(8f), bot); close()
                }
                drawPath(glass, Color(0xFFE3F6FD).copy(alpha = 0.6f))
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
        if (joy > 0.05f && !dancing) {
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
