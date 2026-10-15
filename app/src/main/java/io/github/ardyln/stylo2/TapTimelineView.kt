package io.github.ardyln.stylo2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.sqrt

/**
 * The presentation page's touch timeline. Top: how long each touch lasted, against the zones (too
 * short for the pen, tap, hold). Bottom: the last [WINDOW_MS] of touches, double taps and swipes.
 * Reads [PenService.recentInputs] and redraws itself while it's on screen.
 */
class TapTimelineView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    /** The app's "longest tap": the boundary between the tap and hold zones. */
    var tapMaxMs = 450L

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * sp }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * sp; isFakeBoldText = true }
    private val rect = RectF()
    private val path = Path()

    private fun color(attr: Int) = MaterialColors.getColor(this, attr)
    private val cTooShort get() = color(com.google.android.material.R.attr.colorErrorContainer)
    private val cOnTooShort get() = color(com.google.android.material.R.attr.colorOnErrorContainer)
    private val cTap get() = color(com.google.android.material.R.attr.colorPrimaryContainer)
    private val cOnTap get() = color(com.google.android.material.R.attr.colorOnPrimaryContainer)
    private val cHold get() = color(com.google.android.material.R.attr.colorTertiaryContainer)
    private val cOnHold get() = color(com.google.android.material.R.attr.colorOnTertiaryContainer)
    private val cMark get() = color(androidx.appcompat.R.attr.colorPrimary)
    private val cHoldMark get() = color(com.google.android.material.R.attr.colorTertiary)
    private val cText get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val cLine get() = color(com.google.android.material.R.attr.colorOutlineVariant)

    private val redraw = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, FRAME_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(redraw)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(redraw)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (196 * dp).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val inputs = PenService.snapshotInputs()
        val now = SystemClock.uptimeMillis()
        val left = paddingLeft + 4 * dp
        val right = width - paddingRight - 4 * dp
        var y = paddingTop + 14 * sp
        y = drawDurations(canvas, inputs, left, right, y)
        drawTimeline(canvas, inputs, now, left, right, y + 22 * dp)
    }

    // ---- How long: zones on a square-root scale, so short touches get room
    private fun xFor(ms: Long, left: Float, right: Float): Float {
        val f = sqrt(ms.coerceIn(0, MAX_MS).toFloat() / MAX_MS)
        return left + f * (right - left)
    }

    private fun drawDurations(c: Canvas, inputs: List<TimedInput>, left: Float, right: Float, top: Float): Float {
        title.color = cText
        c.drawText(context.getString(R.string.timeline_durations), left, top, title)
        val barTop = top + 10 * dp
        val barBottom = barTop + 34 * dp
        val tapMax = tapMaxMs.coerceIn(MIN_MS + 50, MAX_MS - 100)
        val zones = listOf(
            Triple(0L, MIN_MS, cTooShort to cOnTooShort) to R.string.timeline_zone_short,
            Triple(MIN_MS, tapMax, cTap to cOnTap) to R.string.timeline_zone_tap,
            Triple(tapMax, MAX_MS, cHold to cOnHold) to R.string.timeline_zone_hold,
        )
        for ((zone, label) in zones) {
            val (from, to, colors) = zone
            rect.set(xFor(from, left, right), barTop, xFor(to, left, right), barBottom)
            fill.color = colors.first
            c.drawRoundRect(rect, 6 * dp, 6 * dp, fill)
            val name = context.getString(label)
            if (text.measureText(name) < rect.width() - 8 * dp) {
                text.color = colors.second
                c.drawText(name, rect.centerX() - text.measureText(name) / 2, barBottom - 8 * dp, text)
            }
        }
        // Touches: the newest is a filled ring, older ones fade out
        val touches = inputs.filter { it.kind == InputKind.TOUCH }.takeLast(DOTS)
        touches.forEachIndexed { i, t ->
            val held = t.upAt - t.downAt
            val x = xFor(held, left, right)
            val newest = i == touches.lastIndex
            fill.color = if (held > tapMaxMs) cHoldMark else cMark
            fill.alpha = if (newest) 255 else 70 + 150 * (i + 1) / touches.size
            c.drawCircle(x, barTop + 9 * dp, if (newest) 6 * dp else 4 * dp, fill)
            if (newest) {
                stroke.color = fill.color
                stroke.strokeWidth = 2 * dp
                c.drawLine(x, barTop + 9 * dp, x, barBottom, stroke)
            }
        }
        fill.alpha = 255
        // Scale: a few round durations, plus the longest tap
        text.color = cText
        val labelY = barBottom + 14 * sp
        for (ms in listOf(MIN_MS, 100L, 250L, tapMaxMs, 1000L, 2000L)) {
            if (ms != tapMaxMs && kotlin.math.abs(xFor(ms, left, right) - xFor(tapMaxMs, left, right)) < 36 * dp) continue
            val x = xFor(ms, left, right)
            val label = if (ms >= 1000) "${ms / 1000} s" else "$ms ms"
            c.drawText(label, (x - text.measureText(label) / 2).coerceIn(left, right - text.measureText(label)), labelY, text)
        }
        return labelY
    }

    // ---- When: the last seconds, newest on the right
    private fun drawTimeline(c: Canvas, inputs: List<TimedInput>, now: Long, left: Float, right: Float, top: Float) {
        title.color = cText
        c.drawText(context.getString(R.string.timeline_recent, WINDOW_MS / 1000), left, top, title)
        val trackTop = top + 12 * dp
        val trackBottom = trackTop + 40 * dp
        val mid = (trackTop + trackBottom) / 2
        fun xAt(t: Long) = right - (now - t).toFloat() / WINDOW_MS * (right - left)
        stroke.color = cLine
        stroke.strokeWidth = 1 * dp
        c.drawLine(left, mid, right, mid, stroke)
        text.color = cText
        for (s in 0..WINDOW_MS / 1000 step 5) {
            val x = xAt(now - s * 1000)
            c.drawLine(x, mid - 4 * dp, x, mid + 4 * dp, stroke)
            val label = if (s == 0L) context.getString(R.string.timeline_now) else "−$s s"
            c.drawText(label, (x - text.measureText(label) / 2).coerceIn(left, right - text.measureText(label)), trackBottom + 12 * sp, text)
        }
        val held = PenService.touchDownSince
        val all = if (held > 0) inputs + TimedInput(InputKind.TOUCH, held, now) else inputs
        for (e in all) {
            if (now - e.downAt > WINDOW_MS) continue
            when (e.kind) {
                InputKind.TOUCH -> {
                    val isHold = e.upAt - e.downAt > tapMaxMs
                    fill.color = if (isHold) cHoldMark else cMark
                    val x1 = xAt(e.downAt).coerceAtLeast(left)
                    val x2 = maxOf(xAt(e.upAt), x1 + 3 * dp)
                    rect.set(x1, mid - 9 * dp, x2, mid + 9 * dp)
                    c.drawRoundRect(rect, 3 * dp, 3 * dp, fill)
                }
                InputKind.DOUBLE_TAP -> {
                    val x = xAt(e.downAt)
                    fill.color = cMark
                    path.reset()
                    path.moveTo(x, trackTop)
                    path.lineTo(x + 7 * dp, trackTop + 7 * dp)
                    path.lineTo(x, trackTop + 14 * dp)
                    path.lineTo(x - 7 * dp, trackTop + 7 * dp)
                    path.close()
                    c.drawPath(path, fill)
                    text.color = cMark
                    c.drawText("2×", x + 9 * dp, trackTop + 11 * dp, text)
                }
                InputKind.SWIPE_UP, InputKind.SWIPE_DOWN -> {
                    val x = xAt(e.downAt)
                    val up = e.kind == InputKind.SWIPE_UP
                    fill.color = cHoldMark
                    path.reset()
                    val tip = if (up) trackBottom - 14 * dp else trackBottom
                    val base = if (up) trackBottom else trackBottom - 14 * dp
                    path.moveTo(x, tip)
                    path.lineTo(x + 6 * dp, base)
                    path.lineTo(x - 6 * dp, base)
                    path.close()
                    c.drawPath(path, fill)
                }
            }
        }
    }

    companion object {
        /** Touches shorter than about this never reach the tablet (measured: 30–45 ms minimum). */
        const val MIN_MS = 30L
        private const val MAX_MS = 2500L
        const val WINDOW_MS = 15_000L
        private const val DOTS = 12
        private const val FRAME_MS = 33L
    }
}
