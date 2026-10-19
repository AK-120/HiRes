package com.example.hifi.ui.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlayIcon(color: Color, size: Dp = 28.dp) = Canvas(Modifier.size(size)) {
    val p = Path().apply {
        moveTo(this@Canvas.size.width * 0.2f, 0f)
        lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
        lineTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height)
        close()
    }
    drawPath(p, color)
}

@Composable
fun PauseIcon(color: Color, size: Dp = 28.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width * 0.28f
    drawRect(color, Offset(this.size.width * 0.1f, 0f), Size(w, this.size.height))
    drawRect(color, Offset(this.size.width * 0.62f, 0f), Size(w, this.size.height))
}

/** Skip icon: triangle plus bar. [forward] false mirrors it. */
@Composable
fun SkipIcon(color: Color, forward: Boolean, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val bar = w * 0.14f
    if (forward) {
        val p = Path().apply { moveTo(0f, 0f); lineTo(w - bar - 2f, h / 2); lineTo(0f, h); close() }
        drawPath(p, color)
        drawRect(color, Offset(w - bar, 0f), Size(bar, h))
    } else {
        val p = Path().apply { moveTo(w, 0f); lineTo(bar + 2f, h / 2); lineTo(w, h); close() }
        drawPath(p, color)
        drawRect(color, Offset(0f, 0f), Size(bar, h))
    }
}
