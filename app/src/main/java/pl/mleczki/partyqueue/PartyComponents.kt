package pl.mleczki.partyqueue

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ the logo

/** Relative heights of the four equaliser bars that make up the logo. */
private val MarkBars = floatArrayOf(0.46f, 0.82f, 0.62f, 0.34f)

/** The Party Queue mark: four equaliser bars and a spark. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 32.dp, bars: Brush? = null, spark: Color = Brand.Orange) {
    Canvas(modifier.size(size)) {
        val brush = bars ?: Brand.horizontal()
        drawMark(brush, spark)
    }
}

private fun DrawScope.drawMark(bars: Brush, spark: Color) {
    val s = size.minDimension
    val barW = s * 0.14f
    val gap = s * 0.075f
    val total = MarkBars.size * barW + (MarkBars.size - 1) * gap
    val left = (s - total) / 2f
    val base = s * 0.84f
    val maxH = s * 0.66f
    MarkBars.forEachIndexed { i, h ->
        val x = left + i * (barW + gap)
        drawRoundRect(
            brush = bars,
            topLeft = Offset(x, base - maxH * h),
            size = Size(barW, maxH * h),
            cornerRadius = CornerRadius(barW / 2f),
        )
    }
    // four-point spark above the last bar
    val cx = left + total - barW * 0.2f
    val cy = s * 0.2f
    val r = s * 0.13f
    val path = Path().apply {
        moveTo(cx, cy - r); quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r); quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r); close()
    }
    drawPath(path, spark)
}

// ------------------------------------------------------------------ equaliser

/** Dancing bars while [playing], a resting pattern otherwise. The animation only exists while music plays. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp, brush: Brush = Brand.horizontal()) {
    if (!playing) {
        Canvas(modifier.size(size)) { drawBars(floatArrayOf(0.3f, 0.5f, 0.4f, 0.25f), brush) }
        return
    }
    val transition = rememberInfiniteTransition(label = "eq")
    val a by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(430, easing = FastOutSlowInEasing), RepeatMode.Reverse), "a")
    val b by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(610, easing = FastOutSlowInEasing), RepeatMode.Reverse), "b")
    val c by transition.animateFloat(0.2f, 0.95f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), "c")
    val d by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), "d")
    Canvas(modifier.size(size)) { drawBars(floatArrayOf(a, b, c, d), brush) }
}

private fun DrawScope.drawBars(heights: FloatArray, brush: Brush) {
    val barW = size.width / (heights.size * 1.6f)
    val gap = (size.width - barW * heights.size) / (heights.size - 1)
    heights.forEachIndexed { i, h ->
        val hh = size.height * h
        drawRoundRect(
            brush = brush,
            topLeft = Offset(i * (barW + gap), size.height - hh),
            size = Size(barW, hh),
            cornerRadius = CornerRadius(barW / 2f),
        )
    }
}

// ------------------------------------------------------------------ buttons

/** Pill button filled with the brand gradient; shrinks a little while pressed. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = Brand.horizontal(),
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(110), label = "press")
    Row(
        modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Round icon-like button; [content] draws the glyph. */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.22f))),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(100), label = "round")
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ------------------------------------------------------------------ glyphs (drawn, so they match everywhere)

@Composable
fun PlayGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val p = Path().apply {
        moveTo(w * 0.28f, h * 0.16f)
        lineTo(w * 0.84f, h * 0.5f)
        lineTo(w * 0.28f, h * 0.84f)
        close()
    }
    drawPath(p, color)
}

@Composable
fun PauseGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width * 0.2f
    drawRoundRect(color, Offset(this.size.width * 0.24f, this.size.height * 0.18f), Size(w, this.size.height * 0.64f), CornerRadius(w / 3))
    drawRoundRect(color, Offset(this.size.width * 0.56f, this.size.height * 0.18f), Size(w, this.size.height * 0.64f), CornerRadius(w / 3))
}

/** A skip glyph: a triangle with a bar, mirrored for "previous". */
@Composable
fun SkipGlyph(color: Color, previous: Boolean, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val draw: DrawScope.() -> Unit = {
        drawPath(
            Path().apply {
                moveTo(w * 0.2f, h * 0.2f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.2f, h * 0.8f); close()
            },
            color,
        )
        drawRoundRect(color, Offset(w * 0.74f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.04f))
    }
    if (previous) drawScale(-1f, 1f, pivot = Offset(w / 2, h / 2)) { draw() } else draw()
}

@Composable
fun SunGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val c = Offset(this.size.width / 2, this.size.height / 2)
    val r = this.size.minDimension * 0.2f
    drawCircle(color, r, c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val from = Offset(c.x + (r * 1.7f * Math.cos(a)).toFloat(), c.y + (r * 1.7f * Math.sin(a)).toFloat())
        val to = Offset(c.x + (r * 2.3f * Math.cos(a)).toFloat(), c.y + (r * 2.3f * Math.sin(a)).toFloat())
        drawLine(color, from, to, strokeWidth = this.size.minDimension * 0.07f, cap = StrokeCap.Round)
    }
}

@Composable
fun MoonGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val moon = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(s * 0.2f, s * 0.2f), Size(s * 0.6f, s * 0.6f)))
    }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(s * 0.36f, s * 0.12f), Size(s * 0.56f, s * 0.56f)))
    }
    val crescent = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite)
    drawPath(crescent, color)
}

/** "Follow the phone" glyph: a circle split in half. */
@Composable
fun AutoGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    drawCircle(color, s * 0.34f, style = Stroke(width = s * 0.08f))
    drawArc(color, 90f, 180f, true, Offset(s * 0.16f, s * 0.16f), Size(s * 0.68f, s * 0.68f))
}

/** The preview state: a crossed-out screen (hidden), an empty screen (small) or a screen with corner brackets (full). */
@Composable
fun ScreenGlyph(color: Color, state: Any, size: Dp = 24.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val stroke = w * 0.09f
    when (state.toString()) {
        "Full" -> {
            val a = w * 0.2f
            val l = w * 0.3f
            for ((x, y, dx, dy) in listOf(listOf(a, a, 1f, 1f), listOf(w - a, a, -1f, 1f), listOf(a, h - a, 1f, -1f), listOf(w - a, h - a, -1f, -1f))) {
                drawLine(color, Offset(x, y), Offset(x + dx * l, y), stroke, StrokeCap.Round)
                drawLine(color, Offset(x, y), Offset(x, y + dy * l), stroke, StrokeCap.Round)
            }
        }
        else -> {
            drawRoundRect(color, Offset(w * 0.12f, h * 0.22f), Size(w * 0.76f, h * 0.56f), CornerRadius(w * 0.1f), style = Stroke(stroke))
            if (state.toString() == "Hidden") {
                drawLine(color, Offset(w * 0.14f, h * 0.9f), Offset(w * 0.86f, h * 0.1f), stroke * 1.2f, StrokeCap.Round)
            }
        }
    }
}

/** A speaker with up to two waves for [level] 0..1; a slash when muted. */
@Composable
fun VolumeGlyph(color: Color, level: Float, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val stroke = w * 0.09f
    val body = Path().apply {
        moveTo(w * 0.08f, h * 0.38f); lineTo(w * 0.28f, h * 0.38f); lineTo(w * 0.5f, h * 0.2f)
        lineTo(w * 0.5f, h * 0.8f); lineTo(w * 0.28f, h * 0.62f); lineTo(w * 0.08f, h * 0.62f); close()
    }
    drawPath(body, color)
    if (level <= 0f) {
        drawLine(color, Offset(w * 0.62f, h * 0.38f), Offset(w * 0.92f, h * 0.62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.92f, h * 0.38f), Offset(w * 0.62f, h * 0.62f), stroke, StrokeCap.Round)
    } else {
        drawArc(color, -45f, 90f, false, Offset(w * 0.34f, h * 0.3f), Size(w * 0.4f, h * 0.4f), style = Stroke(stroke, cap = StrokeCap.Round))
        if (level > 0.45f) {
            drawArc(color, -50f, 100f, false, Offset(w * 0.22f, h * 0.14f), Size(w * 0.64f, h * 0.72f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun SearchGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    drawCircle(color, s * 0.3f, Offset(s * 0.43f, s * 0.43f), style = Stroke(s * 0.1f))
    drawLine(color, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.9f, s * 0.9f), s * 0.11f, StrokeCap.Round)
}

@Composable
fun CloseGlyph(color: Color, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    drawLine(color, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), s * 0.1f, StrokeCap.Round)
    drawLine(color, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), s * 0.1f, StrokeCap.Round)
}

/**
 * "Where in the queue": a list of three lines with an arrow pointing at its top (the song plays next)
 * or at its bottom (it is added to the end).
 */
@Composable
fun QueuePlaceGlyph(color: Color, toEnd: Boolean, size: Dp = 22.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val stroke = h * 0.1f
    // the three list lines, the target one drawn bolder
    for (i in 0..2) {
        val y = h * (0.28f + 0.22f * i)
        val target = if (toEnd) i == 2 else i == 0
        drawLine(color, Offset(w * 0.5f, y), Offset(w * 0.92f, y), if (target) stroke * 1.6f else stroke * 0.8f, StrokeCap.Round, alpha = if (target) 1f else 0.55f)
    }
    // the arrow on the left, pointing up (to the top line) or down (to the bottom line)
    val x = w * 0.22f
    val top = h * 0.2f
    val bottom = h * 0.8f
    drawLine(color, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
    val tip = if (toEnd) bottom else top
    val dir = if (toEnd) -1f else 1f
    drawLine(color, Offset(x, tip), Offset(x - w * 0.12f, tip + dir * h * 0.14f), stroke, StrokeCap.Round)
    drawLine(color, Offset(x, tip), Offset(x + w * 0.12f, tip + dir * h * 0.14f), stroke, StrokeCap.Round)
}

// ------------------------------------------------------------------ surfaces

/** A card whose gradient slowly drifts: the "now playing" panel. [t] is read in the draw phase only. */
fun Modifier.flowingGradient(colors: List<Color>, shape: Shape): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "flow")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), "t")
    this
        .clip(shape)
        .drawBehind {
            val shift = size.width * 0.9f * t
            drawRect(
                Brush.linearGradient(
                    colors,
                    start = Offset(-size.width * 0.3f + shift, 0f),
                    end = Offset(size.width * 1.1f + shift, size.height),
                )
            )
        }
}

/** Soft translucent panel used on top of gradients. */
@Composable
fun Frosted(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f))) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
