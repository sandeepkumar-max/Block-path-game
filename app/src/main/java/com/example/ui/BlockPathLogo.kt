package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/**
 * Official BlockPath Logo from PRD:
 * - Square grid icon (3x3 mini-grid)
 * - Two distinct colored pawns (Deep Blue & Warm Red) at opposite corners
 * - Thick Charcoal Black wall barricade dividing the path
 * - Warm wood/beige rounded background container
 */
@Composable
fun BlockPathLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    elevation: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation, RoundedCornerShape(size * 0.22f))
            .clip(RoundedCornerShape(size * 0.22f))
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // Board background
            drawRoundRect(
                color = BoardBackground,
                size = Size(w, h),
                cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
            )

            // 3x3 grid lines
            val cellSize = w / 3f
            val gridLineColor = Color(0xFFD4C3A3)
            val strokeW = (w * 0.035f).coerceAtLeast(1.5f)

            // Vertical grid lines
            for (i in 1..2) {
                drawLine(
                    color = gridLineColor,
                    start = Offset(i * cellSize, h * 0.08f),
                    end = Offset(i * cellSize, h * 0.92f),
                    strokeWidth = strokeW
                )
            }
            // Horizontal grid lines
            for (i in 1..2) {
                drawLine(
                    color = gridLineColor,
                    start = Offset(w * 0.08f, i * cellSize),
                    end = Offset(w * 0.92f, i * cellSize),
                    strokeWidth = strokeW
                )
            }

            // Outer board border
            drawRoundRect(
                color = GridLineColor.copy(alpha = 0.4f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w * 0.22f, h * 0.22f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
            )

            // Player 1 Pawn (Deep Blue at top-left cell: center at (cellSize*0.5, cellSize*0.5))
            val pawnRadius = cellSize * 0.32f
            drawCircle(
                color = Player1Color,
                radius = pawnRadius,
                center = Offset(cellSize * 0.5f, cellSize * 0.5f)
            )
            // Inner highlight
            drawCircle(
                color = Color(0xFF60A5FA),
                radius = pawnRadius * 0.45f,
                center = Offset(cellSize * 0.46f, cellSize * 0.46f)
            )

            // Player 2 Pawn (Warm Red at bottom-right cell: center at (cellSize*2.5, cellSize*2.5))
            drawCircle(
                color = Player2Color,
                radius = pawnRadius,
                center = Offset(cellSize * 2.5f, cellSize * 2.5f)
            )
            // Inner highlight
            drawCircle(
                color = Color(0xFFF87171),
                radius = pawnRadius * 0.45f,
                center = Offset(cellSize * 2.46f, cellSize * 2.46f)
            )

            // Charcoal Wall Barricade (Thick divider blocking path between them)
            val wallStroke = (cellSize * 0.30f).coerceAtLeast(3.5f)
            // Vertical wall segment between column 1 and column 2
            drawLine(
                color = WallColor,
                start = Offset(cellSize * 1.5f, cellSize * 0.4f),
                end = Offset(cellSize * 1.5f, cellSize * 1.8f),
                strokeWidth = wallStroke,
                cap = StrokeCap.Round
            )

            // Horizontal wall segment
            drawLine(
                color = WallColor,
                start = Offset(cellSize * 1.1f, cellSize * 1.5f),
                end = Offset(cellSize * 2.3f, cellSize * 1.5f),
                strokeWidth = wallStroke * 0.9f,
                cap = StrokeCap.Round
            )
        }
    }
}
