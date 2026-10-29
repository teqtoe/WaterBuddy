package com.waterbuddy.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WaterBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0288D1),
            onPrimary = Color.White,
            secondary = Color(0xFF1ABC9C),
            background = Color(0xFFF2F9FF),
            surface = Color.White,
            primaryContainer = Color(0xFFD7EFFC),
            onPrimaryContainer = Color(0xFF0D2B3E)
        ),
        content = content
    )
}

/** Buddy பேசுற பேச்சு குமிழ் 💬 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp) {
            AnimatedContent(targetState = text, label = "bubble") { t ->
                Text(
                    t,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF1B3A4B)
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

/** சின்ன கிளாஸ் ஐகான் — குடிச்சது நிரம்பியிருக்கும். */
@Composable
fun GlassIcon(filled: Boolean, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(if (filled) 0.8f else 0f, tween(700), label = "glass")
    Canvas(modifier.size(24.dp, 30.dp)) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            moveTo(w * 0.08f, 0f); lineTo(w * 0.92f, 0f)
            lineTo(w * 0.78f, h); lineTo(w * 0.22f, h); close()
        }
        drawPath(glass, Color(0xFFE3F6FD))
        clipPath(glass) {
            drawRect(
                Color(0xFF4FC3F7),
                topLeft = androidx.compose.ui.geometry.Offset(0f, h * (1f - level)),
                size = androidx.compose.ui.geometry.Size(w, h * level)
            )
        }
        drawPath(glass, Color(0xFF0288D1), style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun GlassRow(drank: Int, goal: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until goal) GlassIcon(filled = i < drank)
    }
}
