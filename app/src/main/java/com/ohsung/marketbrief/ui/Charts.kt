package com.ohsung.marketbrief.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohsung.marketbrief.data.ChartPoint
import kotlin.math.abs
import kotlin.math.max

/** Compact price line with a soft gradient fill; used for live tickers. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val padY = size.height * 0.08f
        val h = size.height - padY * 2
        val stepX = size.width / (values.size - 1)
        fun y(v: Double) = padY + (h - ((v - minV) / span * h)).toFloat()

        val line = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * stepX, y(v))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width, y(values.last())))
    }
}

/** Labelled line or bar chart for the series published in the daily feed. */
@Composable
fun SeriesChart(
    points: List<ChartPoint>,
    type: String,
    unit: String,
    upColor: Color,
    downColor: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val valueStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val bottomPad = 18.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val values = points.map { it.y }

        // X-axis labels: show up to ~6 evenly spaced.
        val every = if (type == "bar" && points.size <= 8) 1 else max(1, (points.size + 5) / 6)

        if (type == "bar") {
            val maxAbs = values.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
            val hasNeg = values.any { it < 0 }
            val baseY = if (hasNeg) topPad + chartH / 2 else topPad + chartH
            val scale = if (hasNeg) chartH / 2 / maxAbs else chartH / maxAbs
            val slot = size.width / points.size
            val barW = slot * 0.56f
            drawLine(gridColor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1.dp.toPx())
            points.forEachIndexed { i, p ->
                val cx = slot * i + slot / 2
                val hBar = (abs(p.y) * scale).toFloat().coerceAtLeast(1.5f)
                val top = if (p.y >= 0) baseY - hBar else baseY
                val col = if (p.y >= 0) upColor else downColor
                drawRoundRect(
                    col,
                    topLeft = Offset(cx - barW / 2, top),
                    size = Size(barW, hBar),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                val label = formatAxis(p.y, unit)
                val tl = measurer.measure(label, valueStyle)
                val ly = if (p.y >= 0) top - tl.size.height - 2.dp.toPx() else top + hBar + 2.dp.toPx()
                drawText(tl, topLeft = Offset(cx - tl.size.width / 2, ly.coerceAtLeast(0f)))
                if (i % every == 0 || i == points.lastIndex) {
                    val xl = measurer.measure(p.x, axisStyle)
                    drawText(xl, topLeft = Offset(cx - xl.size.width / 2, size.height - xl.size.height))
                }
            }
        } else {
            val minV = values.min()
            val maxV = values.max()
            val span = (maxV - minV).takeIf { it > 0 } ?: (abs(maxV) * 0.1 + 1)
            val lo = minV - span * 0.12
            val hi = maxV + span * 0.12
            val stepX = if (points.size > 1) (size.width - 24.dp.toPx()) / (points.size - 1) else 0f
            val x0 = 12.dp.toPx()
            fun y(v: Double) = topPad + (chartH - ((v - lo) / (hi - lo) * chartH)).toFloat()
            val color = if (values.last() >= values.first()) upColor else downColor

            gridLines(gridColor, topPad, chartH)
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = x0 + i * stepX
                if (i == 0) path.moveTo(x, y(p.y)) else path.lineTo(x, y(p.y))
            }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p ->
                val x = x0 + i * stepX
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y(p.y)))
                val showValue = points.size <= 8 || i == points.lastIndex || i == 0
                if (showValue) {
                    val tl = measurer.measure(formatAxis(p.y, unit), valueStyle)
                    val tx = (x - tl.size.width / 2).coerceIn(0f, size.width - tl.size.width)
                    // Put the label on the side away from the line: above local highs, below local lows.
                    val neighbors = listOfNotNull(points.getOrNull(i - 1)?.y, points.getOrNull(i + 1)?.y)
                    val above = neighbors.isEmpty() || p.y >= neighbors.average()
                    val gap = 6.dp.toPx()
                    val ty = if (above) y(p.y) - tl.size.height - gap else y(p.y) + gap
                    val top = ty.coerceIn(0f, topPad + chartH - tl.size.height)
                    val padH = 3.dp.toPx()
                    drawRoundRect(
                        labelBg,
                        topLeft = Offset(tx - padH, top),
                        size = Size(tl.size.width + padH * 2, tl.size.height.toFloat()),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawText(tl, topLeft = Offset(tx, top))
                }
                if (i % every == 0 || i == points.lastIndex) {
                    val xl = measurer.measure(p.x, axisStyle)
                    val tx = (x - xl.size.width / 2).coerceIn(0f, size.width - xl.size.width)
                    drawText(xl, topLeft = Offset(tx, size.height - xl.size.height))
                }
            }
        }
    }
}

private fun DrawScope.gridLines(color: Color, top: Float, h: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    for (k in 0..2) {
        val yy = top + h * k / 2
        drawLine(color, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f, pathEffect = dash)
    }
}

private fun formatAxis(v: Double, unit: String): String {
    val n = when {
        abs(v) >= 1000 -> "%,.0f".format(v)
        abs(v) >= 100 -> "%.0f".format(v)
        abs(v) >= 10 && unit != "$" -> "%.1f".format(v)
        else -> "%.2f".format(v)
    }
    if (unit == "억") return "%.2f억".format(v)
    if (unit == "건") return "%,.0f".format(v)
    return when (unit) {
        "%" -> "$n%"
        "$" -> "$$n"
        else -> n
    }
}

/** One line of a multi-line chart. Points are matched to the shared x labels by their x text. */
data class ChartLine(
    val name: String,
    val points: List<ChartPoint>,
    val color: Color,
    val dashed: Boolean = false,
    /** Scaled on its own (e.g. a stock that moves far more than house prices); its end label shows the real value. */
    val secondary: Boolean = false,
)

/** Several lines over the same months (e.g. house prices of a few regions as an index). */
@Composable
fun MultiLineChart(lines: List<ChartLine>, xLabels: List<String>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(modifier) {
        val all = lines.filterNot { it.secondary }.flatMap { l -> l.points.map { it.y } }
        if (all.isEmpty() || xLabels.size < 2) return@Canvas
        val sec = lines.filter { it.secondary }.flatMap { l -> l.points.map { it.y } }
        val bottomPad = 18.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 34.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val minV = all.min()
        val maxV = all.max()
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val lo = minV - span * 0.08
        val hi = maxV + span * 0.08
        val x0 = 6.dp.toPx()
        val stepX = (size.width - x0 - rightPad) / (xLabels.size - 1)
        fun y(v: Double) = topPad + (chartH - ((v - lo) / (hi - lo) * chartH)).toFloat()
        val sLo = sec.minOrNull() ?: 0.0
        val sHi = sec.maxOrNull() ?: 1.0
        val sSpan = (sHi - sLo).takeIf { it > 0 } ?: 1.0
        fun ys(v: Double) = topPad + (chartH - ((v - (sLo - sSpan * 0.08)) / (sSpan * 1.16) * chartH)).toFloat()

        gridLines(gridColor, topPad, chartH)
        // Reference line at 100 when the chart is an index.
        if (lo < 100 && hi > 100) {
            drawLine(labelColor.copy(alpha = 0.5f), Offset(0f, y(100.0)), Offset(size.width - rightPad, y(100.0)), strokeWidth = 1f)
        }
        val endLabels = ArrayList<Triple<Float, Float, androidx.compose.ui.text.TextLayoutResult>>()
        lines.forEach { l ->
            val pts = l.points.mapNotNull { p -> xLabels.indexOf(p.x).takeIf { it >= 0 }?.let { it to p.y } }.sortedBy { it.first }
            if (pts.size < 2) return@forEach
            val yy: (Double) -> Float = if (l.secondary) ::ys else ::y
            val path = Path()
            pts.forEachIndexed { i, (xi, v) ->
                val x = x0 + xi * stepX
                if (i == 0) path.moveTo(x, yy(v)) else path.lineTo(x, yy(v))
            }
            drawPath(
                path, l.color,
                style = Stroke(
                    2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                ),
            )
            val (lx, lv) = pts.last()
            drawCircle(l.color, radius = 3.dp.toPx(), center = Offset(x0 + lx * stepX, yy(lv)))
            val tl = measurer.measure("%.0f".format(lv), TextStyle(fontSize = 10.sp, color = l.color))
            endLabels += Triple(x0 + lx * stepX + 5.dp.toPx(), yy(lv) - tl.size.height / 2, tl)
        }
        // End-of-line values, nudged apart so lines ending at the same height stay readable.
        var lastBottom = -1f
        endLabels.sortedBy { it.second }.forEach { (x, y, tl) ->
            val top = maxOf(y, lastBottom).coerceIn(0f, size.height - bottomPad - tl.size.height)
            drawText(tl, topLeft = Offset(x, top))
            lastBottom = top + tl.size.height
        }
        val every = max(1, (xLabels.size + 4) / 5)
        xLabels.forEachIndexed { i, s ->
            if (i % every == 0 || i == xLabels.lastIndex) {
                val xl = measurer.measure(s, axisStyle)
                val tx = (x0 + i * stepX - xl.size.width / 2).coerceIn(0f, size.width - xl.size.width)
                drawText(xl, topLeft = Offset(tx, size.height - xl.size.height))
            }
        }
    }
}

val SeriesPalette = listOf(Color(0xFF2E6BE6), Color(0xFFE8590C), Color(0xFF2F9E44), Color(0xFF8E44D9))
