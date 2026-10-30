package com.waterbuddy.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WaterBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand.Primary,
            onPrimary = Color.White,
            secondary = Brand.Secondary,
            tertiary = Brand.Accent,
            background = Brand.Background,
            surface = Brand.Card,
            primaryContainer = Color(0xFFE8E7FD),
            onPrimaryContainer = Brand.Ink,
            secondaryContainer = Color(0xFFF0E9FE),
            onSecondaryContainer = Brand.Ink
        ),
        content = content
    )
}

/** Buddy பேசுற பேச்சு குமிழ் 💬 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp) {
            AnimatedContent(targetState = text, label = "bubble") { t ->
                Text(
                    t,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = Brand.Ink
                )
            }
        }
        Canvas(Modifier.size(22.dp, 12.dp)) {
            drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
            }, Color.White)
        }
    }
}

/** வட்டமான தண்ணீர் progress — உள்ளே அலை அசையும் 🌊 */
@Composable
fun WaterProgress(drankMl: Int, goalMl: Int, size: Dp, modifier: Modifier = Modifier) {
    val target = if (goalMl <= 0) 0f else (drankMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val level by animateFloatAsState(target, tween(1200), label = "level")
    val inf = rememberInfiniteTransition(label = "wave")
    val shift by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shift")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(r, r)
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - 6.dp.toPx())) }
            drawCircle(Color(0xFFEAF3FF), r - 6.dp.toPx(), c)
            clipPath(circle) {
                val h = this.size.height
                val w = this.size.width
                val baseY = h * (1f - level)
                val amp = if (level in 0.01f..0.99f) 7.dp.toPx() else 0f
                for ((k, col) in listOf(0 to Brand.Water.copy(alpha = 0.55f), 1 to Brand.WaterDeep)) {
                    val p = Path().apply {
                        moveTo(0f, h)
                        var x = 0f
                        lineTo(0f, baseY)
                        while (x <= w) {
                            val y = baseY + sin(x / w * 2f * PI.toFloat() * 1.3f + shift + k * 1.7f) * amp
                            lineTo(x, y + k * 4.dp.toPx())
                            x += 6f
                        }
                        lineTo(w, h); close()
                    }
                    drawPath(p, col)
                }
            }
            drawCircle(Brand.WaterDeep, r - 3.dp.toPx(), c, style = Stroke(5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val onWater = level > 0.55f
            Text(
                "${(target * 100).toInt()}%",
                fontSize = (size.value / 5.2f).sp, fontWeight = FontWeight.ExtraBold,
                color = if (onWater) Color.White else Brand.Ink
            )
            Text(
                "$drankMl / $goalMl ml",
                fontSize = (size.value / 13f).sp, fontWeight = FontWeight.SemiBold,
                color = if (onWater) Color.White else Brand.Muted
            )
        }
    }
}

/** கீழே வரும் "Developed by teqtoe.com" */
@Composable
fun TeqtoeFooter(modifier: Modifier = Modifier, light: Boolean = false) {
    val ctx = LocalContext.current
    Text(
        buildAnnotatedString {
            append("Developed by ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (light) Color.White else Brand.Primary)) {
                append(Brand.WEBSITE)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Brand.WEBSITE_URL)))
                } catch (_: Exception) {
                }
            }
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        color = if (light) Color.White.copy(alpha = 0.85f) else Brand.Muted
    )
}
