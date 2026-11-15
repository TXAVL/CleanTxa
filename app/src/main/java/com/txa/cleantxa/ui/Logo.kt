package com.txa.cleantxa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A TV with a check mark on the screen: "a clean TV". */
@Composable
fun CleanTxaLogo(size: Dp, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val line = w * 0.07f
        // screen
        drawRoundRect(
            color = ink,
            topLeft = Offset(w * 0.08f, w * 0.14f),
            size = Size(w * 0.84f, w * 0.56f),
            cornerRadius = CornerRadius(w * 0.09f),
            style = Stroke(width = line),
        )
        // stand
        drawLine(ink, Offset(w * 0.5f, w * 0.70f), Offset(w * 0.5f, w * 0.82f), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(ink, Offset(w * 0.32f, w * 0.86f), Offset(w * 0.68f, w * 0.86f), strokeWidth = line, cap = StrokeCap.Round)
        // check mark
        val check = Path().apply {
            moveTo(w * 0.32f, w * 0.43f)
            lineTo(w * 0.45f, w * 0.55f)
            lineTo(w * 0.69f, w * 0.30f)
        }
        drawPath(check, accent, style = Stroke(width = line * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
