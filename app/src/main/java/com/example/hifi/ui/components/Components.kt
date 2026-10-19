package com.example.hifi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/** The app mark: a thin ring with a five-bar waveform. Same geometry as the launcher icon. */
@Composable
fun LogoMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        drawCircle(color, radius = s * 0.46f, center = c, style = Stroke(width = s * 0.03f))
        val heights = listOf(0.10f, 0.22f, 0.34f, 0.22f, 0.10f)
        val step = s * 0.075f
        heights.forEachIndexed { i, h ->
            val x = c.x + (i - 2) * step
            drawLine(
                color,
                Offset(x, c.y - s * h / 2),
                Offset(x, c.y + s * h / 2),
                strokeWidth = s * 0.032f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Square artwork with a hairline border. Falls back to the app mark when there is no image. */
@Composable
fun Artwork(uri: String?, modifier: Modifier = Modifier, size: Dp? = null) {
    val colors = MaterialTheme.colorScheme
    val base = if (size != null) modifier.size(size) else modifier
    Box(
        base
            .clip(RoundedCornerShape(2.dp))
            .background(colors.surfaceVariant)
            .border(0.5.dp, colors.outline, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Fallback()
                }
            }
        } else {
            Fallback()
        }
    }
}

@Composable
private fun Fallback() {
    LogoMark(
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        Modifier.fillMaxSize(0.55f)
    )
}

/** Thin line with a small thumb. Tap or drag; [onFinished] receives the final 0..1 value. */
@Composable
fun ThinSeekBar(
    progress: Float,
    onChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val last = remember { floatArrayOf(0f) }
    val fill = if (muted) colors.onSurfaceVariant else colors.primary
    Box(
        modifier
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { off ->
                    val v = (off.x / size.width).coerceIn(0f, 1f)
                    last[0] = v
                    onChange(v)
                    onFinished(v)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        last[0] = (off.x / size.width).coerceIn(0f, 1f)
                        onChange(last[0])
                    },
                    onDragEnd = { onFinished(last[0]) },
                    onDragCancel = { onFinished(last[0]) }
                ) { change, _ ->
                    change.consume()
                    last[0] = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange(last[0])
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val stroke = 2.dp.toPx()
            drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
            val x = size.width * progress.coerceIn(0f, 1f)
            drawLine(fill, Offset(0f, y), Offset(x, y), strokeWidth = stroke)
            if (enabled) drawCircle(fill, 5.dp.toPx(), Offset(x, y))
        }
    }
}

/** One-line message bar; tap to dismiss. */
@Composable
fun StatusLine(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    val colors = MaterialTheme.colorScheme
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
