package io.github.ardyln.stylo2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The presentation page's touch timeline, with the tap timing settings as handles:
 * 1. how long each touch lasted, against the zones too short (dropped by the pen), tap and hold; the
 *    boundary between tap and hold is the "longest tap" handle;
 * 2. after letting go, how long a single tap waits before it runs (the "delay single taps" handle);
 * 3. the last [WINDOW_MS] of touches, double taps and swipes, with a dot where each tap's action ran.
 * Reads [PenService.snapshotInputs] and redraws itself while it's on screen.
 */
class TapTimelineView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    /** The app's "longest tap": the boundary between the tap and hold zones. */
    var tapMaxMs = 450L
        set(v) { if (drag != Drag.TAP_MAX) field = v }

    /** How long a single tap waits after the finger lifts; 0 is instant. */
    var tapWaitMs = 0L
        set(v) { if (drag != Drag.WAIT) field = v }

    /** Called while the user drags a handle (in 50 ms steps). */
    var onTapMaxChanged: (Long) -> Unit = {}
    var onTapWaitChanged: (Long) -> Unit = {}

    private enum class Drag { NONE, TAP_MAX, WAIT }
    private var drag = Drag.NONE

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * sp }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * sp; isFakeBoldText = true }
    private val rect = RectF()
    private val path = Path()

    // Geometry from the last draw, for touch handling
    private var left = 0f
    private var right = 0f
    private val durationBar = RectF()
    private val waitBar = RectF()

    private fun color(attr: Int) = MaterialColors.getColor(this, attr)
    private val cTooShort get() = color(com.google.android.material.R.attr.colorErrorContainer)
    private val cOnTooShort get() = color(com.google.android.material.R.attr.colorOnErrorContainer)
    private val cTap get() = color(com.google.android.material.R.attr.colorPrimaryContainer)
    private val cOnTap get() = color(com.google.android.material.R.attr.colorOnPrimaryContainer)
    // Holds are neutral, so both kinds of dots stand out on every zone
    private val cHold get() = color(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    private val cOnHold get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val cWait get() = color(com.google.android.material.R.attr.colorSecondaryContainer)
    private val cOnWait get() = color(com.google.android.material.R.attr.colorOnSecondaryContainer)
    private val cTrack get() = color(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    private val cMark get() = color(androidx.appcompat.R.attr.colorPrimary)
    private val cHoldMark get() = color(com.google.android.material.R.attr.colorOnSurface)
    private val cSwipe get() = color(com.google.android.material.R.attr.colorTertiary)
    private val cOutline get() = color(com.google.android.material.R.attr.colorSurface)
    private val cText get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val cLine get() = color(com.google.android.material.R.attr.colorOutlineVariant)
    private val cHandle get() = color(com.google.android.material.R.attr.colorOnSurface)
    private val cBubble get() = color(com.google.android.material.R.attr.colorSurfaceInverse)
    private val cOnBubble get() = color(com.google.android.material.R.attr.colorOnSurfaceInverse)

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
        val h = (292 * dp).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val inputs = PenService.snapshotInputs()
        val now = SystemClock.uptimeMillis()
        left = paddingLeft + 8 * dp
        right = width - paddingRight - 8 * dp
        var y = paddingTop + 14 * sp
        y = drawDurations(canvas, inputs, y)
        y = drawWait(canvas, y + 24 * dp)
        drawTimeline(canvas, inputs, now, y + 24 * dp)
    }

    // ---- 1. How long: zones on a square-root scale, so short touches get room

    private fun durationX(ms: Long): Float = left + sqrt(ms.coerceIn(0, MAX_MS).toFloat() / MAX_MS) * (right - left)

    private fun durationAt(x: Float): Long {
        val f = ((x - left) / (right - left)).coerceIn(0f, 1f)
        return (f * f * MAX_MS).toLong()
    }

    private fun drawDurations(c: Canvas, inputs: List<TimedInput>, top: Float): Float {
        title.color = cText
        c.drawText(context.getString(R.string.timeline_durations), left, top, title)
        val barTop = top + 12 * dp
        val barBottom = barTop + 36 * dp
        durationBar.set(left, barTop, right, barBottom)
        val tapMax = tapMaxMs
        zone(c, durationX(0), durationX(MIN_MS), barTop, barBottom, cTooShort, cOnTooShort, R.string.timeline_zone_short)
        zone(c, durationX(MIN_MS), durationX(tapMax), barTop, barBottom, cTap, cOnTap, R.string.timeline_zone_tap)
        zone(c, durationX(tapMax), durationX(MAX_MS), barTop, barBottom, cHold, cOnHold, R.string.timeline_zone_hold)

        // Touches: the newest is big with a line, older ones fade. Colors follow the current boundary.
        val touches = inputs.filter { it.kind == InputKind.TOUCH }.takeLast(DOTS)
        touches.forEachIndexed { i, t ->
            val held = t.upAt - t.downAt
            val x = durationX(held)
            val newest = i == touches.lastIndex
            val r = if (newest) 6 * dp else 4 * dp
            val alpha = if (newest) 255 else 90 + 140 * (i + 1) / touches.size
            // A dark ring keeps the dot visible on every zone
            fill.color = cOutline
            fill.alpha = alpha
            c.drawCircle(x, barTop + 10 * dp, r + 2 * dp, fill)
            fill.color = if (held > tapMax) cHoldMark else cMark
            fill.alpha = alpha
            c.drawCircle(x, barTop + 10 * dp, r, fill)
            if (newest) {
                stroke.color = fill.color
                stroke.strokeWidth = 2 * dp
                c.drawLine(x, barTop + 10 * dp, x, barBottom - 4 * dp, stroke)
            }
        }
        fill.alpha = 255

        handle(c, durationX(tapMax), barTop, barBottom, drag == Drag.TAP_MAX, seconds(tapMax))

        text.color = cText
        val labelY = barBottom + 16 * sp
        for (ms in listOf(MIN_MS, 100L, 250L, 1000L, 2000L)) {
            val x = durationX(ms)
            if (abs(x - durationX(tapMax)) < 40 * dp) continue
            val label = if (ms >= 1000) "${ms / 1000} s" else "$ms ms"
            c.drawText(label, (x - text.measureText(label) / 2).coerceIn(left, right - text.measureText(label)), labelY, text)
        }
        return labelY
    }

    // ---- 2. After letting go: the single-tap delay

    private fun waitX(ms: Long) = left + ms.coerceIn(0, WAIT_MAX_MS).toFloat() / WAIT_MAX_MS * (right - left)

    private fun drawWait(c: Canvas, top: Float): Float {
        title.color = cText
        c.drawText(context.getString(R.string.timeline_delay), left, top, title)
        val barTop = top + 12 * dp
        val barBottom = barTop + 30 * dp
        waitBar.set(left, barTop, right, barBottom)
        rect.set(left, barTop, right, barBottom)
        fill.color = cTrack
        c.drawRoundRect(rect, 6 * dp, 6 * dp, fill)
        val wait = tapWaitMs
        if (wait > 0) zone(c, left, waitX(wait), barTop, barBottom, cWait, cOnWait, R.string.timeline_delay_zone)
        // Where the single tap runs
        val runX = waitX(wait)
        fill.color = cMark
        c.drawCircle(runX, (barTop + barBottom) / 2, 5 * dp, fill)
        text.color = cText
        val runLabel = context.getString(R.string.timeline_runs_here)
        val lx = if (runX + 10 * dp + text.measureText(runLabel) < right) runX + 10 * dp else runX - 10 * dp - text.measureText(runLabel)
        c.drawText(runLabel, lx, (barTop + barBottom) / 2 + 4 * sp, text)
        handle(c, runX, barTop, barBottom, drag == Drag.WAIT,
            if (wait == 0L) context.getString(R.string.tap_instant) else seconds(wait))
        val labelY = barBottom + 16 * sp
        for (ms in listOf(0L, 200L, 400L, 600L)) {
            val x = waitX(ms)
            val label = if (ms == 0L) context.getString(R.string.timeline_let_go) else seconds(ms)
            c.drawText(label, (x - text.measureText(label) / 2).coerceIn(left, right - text.measureText(label)), labelY, text)
        }
        return labelY
    }

    // ---- 3. The last seconds, newest on the right

    private fun drawTimeline(c: Canvas, inputs: List<TimedInput>, now: Long, top: Float) {
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
        for (s in 0L..WINDOW_MS / 1000 step 5) {
            val x = xAt(now - s * 1000)
            c.drawLine(x, mid - 4 * dp, x, mid + 4 * dp, stroke)
            val label = if (s == 0L) context.getString(R.string.timeline_now) else "−$s s"
            c.drawText(label, (x - text.measureText(label) / 2).coerceIn(left, right - text.measureText(label)), trackBottom + 14 * sp, text)
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
                    // Where the single tap's action ran (after the delay)
                    val ranAt = e.upAt + tapWaitMs
                    if (!isHold && e !== all.last().takeIf { held > 0 } && ranAt <= now) {
                        val rx = xAt(ranAt)
                        if (tapWaitMs > 0) {
                            stroke.color = cMark
                            stroke.strokeWidth = 1.5f * dp
                            c.drawLine(x2, mid + 12 * dp, rx, mid + 12 * dp, stroke)
                        }
                        c.drawCircle(rx, mid + 12 * dp, 3 * dp, fill)
                    }
                }
                InputKind.DOUBLE_TAP -> {
                    val x = xAt(e.downAt)
                    fill.color = cMark
                    path.reset()
                    path.moveTo(x, trackTop - 2 * dp)
                    path.lineTo(x + 7 * dp, trackTop + 5 * dp)
                    path.lineTo(x, trackTop + 12 * dp)
                    path.lineTo(x - 7 * dp, trackTop + 5 * dp)
                    path.close()
                    c.drawPath(path, fill)
                    text.color = cMark
                    c.drawText("2×", x + 9 * dp, trackTop + 9 * dp, text)
                }
                InputKind.SWIPE_UP, InputKind.SWIPE_DOWN -> {
                    val x = xAt(e.downAt)
                    val up = e.kind == InputKind.SWIPE_UP
                    fill.color = cSwipe
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

    // ---- Drawing helpers

    private fun zone(c: Canvas, x1: Float, x2: Float, top: Float, bottom: Float, bg: Int, fg: Int, label: Int) {
        rect.set(x1, top, x2, bottom)
        fill.color = bg
        c.drawRoundRect(rect, 6 * dp, 6 * dp, fill)
        val name = context.getString(label)
        if (text.measureText(name) < rect.width() - 8 * dp) {
            text.color = fg
            c.drawText(name, rect.centerX() - text.measureText(name) / 2, bottom - 8 * dp, text)
        }
    }

    /** A draggable boundary: a pill with grip lines, and the value in a bubble while dragging. */
    private fun handle(c: Canvas, x: Float, top: Float, bottom: Float, active: Boolean, value: String) {
        val w = if (active) 8 * dp else 6 * dp
        rect.set(x - w / 2, top - 6 * dp, x + w / 2, bottom + 6 * dp)
        fill.color = cHandle
        c.drawRoundRect(rect, w / 2, w / 2, fill)
        if (active) {
            val tw = text.measureText(value)
            val bx = (x - tw / 2 - 8 * dp).coerceIn(left - 8 * dp, right + 8 * dp - tw - 16 * dp)
            rect.set(bx, top - 34 * dp, bx + tw + 16 * dp, top - 10 * dp)
            fill.color = cBubble
            c.drawRoundRect(rect, 12 * dp, 12 * dp, fill)
            text.color = cOnBubble
            c.drawText(value, bx + 8 * dp, top - 18 * dp, text)
        }
    }

    private fun seconds(ms: Long) = context.getString(R.string.seconds_value_2, ms / 1000f)

    // ---- Dragging the handles

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slop = 24 * dp
                drag = when {
                    e.y in durationBar.top - slop..durationBar.bottom + slop &&
                        abs(e.x - durationX(tapMaxMs)) < slop -> Drag.TAP_MAX
                    e.y in waitBar.top - slop..waitBar.bottom + slop &&
                        abs(e.x - waitX(tapWaitMs)) < slop -> Drag.WAIT
                    // A tap elsewhere on a strip moves its handle there
                    e.y in durationBar.top..durationBar.bottom -> Drag.TAP_MAX
                    e.y in waitBar.top..waitBar.bottom -> Drag.WAIT
                    else -> Drag.NONE
                }
                if (drag == Drag.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                move(e.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (drag != Drag.NONE) {
                move(e.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (drag != Drag.NONE) {
                drag = Drag.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun move(x: Float) {
        when (drag) {
            Drag.TAP_MAX -> {
                val ms = (snap(durationAt(x))).coerceIn(TAP_MAX_MIN, TAP_MAX_MAX)
                if (ms != tapMaxMs) {
                    drag = Drag.NONE; tapMaxMs = ms; drag = Drag.TAP_MAX
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onTapMaxChanged(ms)
                }
            }
            Drag.WAIT -> {
                val f = ((x - left) / (right - left)).coerceIn(0f, 1f)
                val ms = snap((f * WAIT_MAX_MS).toLong()).coerceIn(0, WAIT_MAX_MS)
                if (ms != tapWaitMs) {
                    drag = Drag.NONE; tapWaitMs = ms; drag = Drag.WAIT
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onTapWaitChanged(ms)
                }
            }
            Drag.NONE -> {}
        }
        invalidate()
    }

    private fun snap(ms: Long) = (ms / 50.0).roundToLong() * 50

    companion object {
        /** Touches shorter than about this never reach the tablet (measured: 30–45 ms minimum). */
        const val MIN_MS = 30L
        private const val MAX_MS = 2500L
        private const val TAP_MAX_MIN = 150L
        private const val TAP_MAX_MAX = 800L
        private const val WAIT_MAX_MS = 600L
        const val WINDOW_MS = 15_000L
        private const val DOTS = 12
        private const val FRAME_MS = 33L
    }
}
