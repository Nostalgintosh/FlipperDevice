package com.a11.hackerboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Draws a [Layout] and turns touches into key events. Pure Canvas drawing — no
 * deprecated KeyboardView, no XML keyboard files — so every key (sizing,
 * long-press hints, modifier highlight, repeat) behaves exactly as defined.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onKey(key: Key, longPress: Boolean)

        /** A swiped corner symbol (or other direct text insert). */
        fun onText(text: String)
    }

    var listener: Listener? = null
    var haptics: Boolean = true
    var sound: Boolean = false

    private var rows: Layout = emptyList()
    private var theme: KbTheme = buildTheme(0)

    private val density = resources.displayMetrics.density
    private var keyHeightPx = 52f * density
    private val gap = 3f * density
    private val vPad = 6f * density
    private val radius = 7f * density

    private var shift = ModState.OFF
    private var ctrl = ModState.OFF
    private var alt = ModState.OFF

    private class Placed(val key: Key, val rect: RectF)
    private val placed = ArrayList<Placed>()
    private var pressed: Placed? = null
    private var longFired = false
    private var downX = 0f
    private var downY = 0f
    private val swipeThreshold = 18f * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longRunnable: Runnable? = null

    fun configure(theme: KbTheme, keyHeightDp: Int, haptics: Boolean, sound: Boolean) {
        this.theme = theme
        this.keyHeightPx = keyHeightDp * density
        this.haptics = haptics
        this.sound = sound
        setBackgroundColor(theme.bg)
        requestLayout()
        invalidate()
    }

    fun setRows(rows: Layout) {
        this.rows = rows
        cancelTimers()
        pressed = null
        // Re-place keys now: switching to a layer with the same row count leaves
        // the measured height unchanged, so onSizeChanged() wouldn't fire and the
        // cached rectangles would still point at the previous layout.
        if (width > 0) layoutKeys(width.toFloat())
        requestLayout()
        invalidate()
    }

    fun setModifiers(shift: ModState, ctrl: ModState, alt: ModState) {
        this.shift = shift
        this.ctrl = ctrl
        this.alt = alt
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (rows.size * keyHeightPx + vPad * 2f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys(w.toFloat())
    }

    private fun layoutKeys(width: Float) {
        placed.clear()
        for ((r, row) in rows.withIndex()) {
            val total = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
            var x = 0f
            val top = vPad + r * keyHeightPx
            for (key in row) {
                val kw = width * (key.weight / total)
                placed.add(Placed(key, RectF(x, top, x + kw, top + keyHeightPx)))
                x += kw
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.bg)
        if (placed.isEmpty() && rows.isNotEmpty()) layoutKeys(width.toFloat())
        for (p in placed) drawKey(canvas, p)
    }

    private fun modStateFor(code: KeyCode): ModState? = when (code) {
        KeyCode.SHIFT -> shift
        KeyCode.CTRL -> ctrl
        KeyCode.ALT -> alt
        else -> null
    }

    private fun drawKey(canvas: Canvas, p: Placed) {
        val k = p.key
        val inset = gap / 2f
        val rect = RectF(
            p.rect.left + inset, p.rect.top + inset,
            p.rect.right - inset, p.rect.bottom - inset,
        )
        val isPressed = p === pressed
        val mod = modStateFor(k.code)

        var bg: Int
        var fg: Int
        when {
            isPressed -> { bg = theme.keyDown; fg = theme.onAccent }
            mod == ModState.LOCK -> { bg = theme.accent; fg = theme.onAccent }
            k.style == KeyStyle.ACCENT -> { bg = theme.accent; fg = theme.onAccent }
            k.style == KeyStyle.MODIFIER || k.style == KeyStyle.ACTION -> { bg = theme.keyMod; fg = theme.text }
            else -> { bg = theme.key; fg = theme.text }
        }

        fill.color = bg
        canvas.drawRoundRect(rect, radius, radius, fill)

        if (mod == ModState.ON && !isPressed) {
            stroke.color = theme.accent
            stroke.strokeWidth = 2f * density
            fg = theme.accent
        } else {
            stroke.color = theme.border
            stroke.strokeWidth = 1f * density
        }
        canvas.drawRoundRect(rect, radius, radius, stroke)

        val label = displayLabel(k)
        labelPaint.color = fg
        labelPaint.textSize = fitTextSize(label, rect.width() - 6f * density)
        val cy = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), cy, labelPaint)

        if (k.hasCorners) {
            drawCorners(canvas, k, rect, fg)
        } else {
            k.hintLabel?.let { hint ->
                hintPaint.textAlign = Paint.Align.RIGHT
                hintPaint.color = if (fg == theme.onAccent) theme.onAccent else theme.textDim
                hintPaint.textSize = keyHeightPx * 0.22f
                canvas.drawText(
                    hint,
                    rect.right - keyHeightPx * 0.14f,
                    rect.top + hintPaint.textSize * 1.25f,
                    hintPaint,
                )
            }
        }
    }

    /** Small symbols in the four corners — the targets for diagonal swipes. */
    private fun drawCorners(canvas: Canvas, k: Key, rect: RectF, fg: Int) {
        hintPaint.textSize = keyHeightPx * 0.20f
        hintPaint.color = if (fg == theme.onAccent) theme.onAccent else theme.textDim
        val padX = keyHeightPx * 0.12f
        val topY = rect.top + hintPaint.textSize + keyHeightPx * 0.04f
        val botY = rect.bottom - keyHeightPx * 0.10f
        k.nw?.let { hintPaint.textAlign = Paint.Align.LEFT; canvas.drawText(it, rect.left + padX, topY, hintPaint) }
        k.ne?.let { hintPaint.textAlign = Paint.Align.RIGHT; canvas.drawText(it, rect.right - padX, topY, hintPaint) }
        k.sw?.let { hintPaint.textAlign = Paint.Align.LEFT; canvas.drawText(it, rect.left + padX, botY, hintPaint) }
        k.se?.let { hintPaint.textAlign = Paint.Align.RIGHT; canvas.drawText(it, rect.right - padX, botY, hintPaint) }
    }

    private fun displayLabel(k: Key): String {
        return if (shift != ModState.OFF && k.code == KeyCode.NONE &&
            k.label.length == 1 && k.label[0].isLetter()
        ) {
            k.label.uppercase()
        } else {
            k.label
        }
    }

    /** Pick a size by label length, then shrink until it fits the key width. */
    private fun fitTextSize(label: String, maxWidth: Float): Float {
        val base = keyHeightPx * 0.42f
        var size = when (label.length) {
            0, 1 -> base
            2 -> base * 0.92f
            3 -> base * 0.74f
            else -> base * 0.60f
        }
        labelPaint.textSize = size
        while (size > 8f && labelPaint.measureText(label) > maxWidth) {
            size *= 0.9f
            labelPaint.textSize = size
        }
        return size
    }

    // --- touch ------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = findKey(e.x, e.y) ?: return true
                pressed = p
                longFired = false
                downX = e.x
                downY = e.y
                invalidate()
                feedback()
                val k = p.key
                when {
                    k.repeatable -> { fire(k, false); scheduleRepeat(k) }
                    k.hasHint -> scheduleLong(k)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val p = pressed ?: return true
                // Corner keys resolve by swipe direction on UP, so the finger is
                // expected to leave the key — only cancel non-swipe keys on slide-off.
                if (!p.key.hasCorners && !p.rect.contains(e.x, e.y)) {
                    cancelTimers()
                    pressed = null
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val p = pressed
                cancelTimers()
                if (p != null && !p.key.repeatable && !longFired) {
                    val swiped = if (p.key.hasCorners) cornerFor(p.key, e.x - downX, e.y - downY) else null
                    if (swiped != null) {
                        listener?.onText(swiped)
                    } else {
                        performClick()
                        fire(p.key, false)
                    }
                }
                pressed = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTimers()
                pressed = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findKey(x: Float, y: Float): Placed? = placed.firstOrNull { it.rect.contains(x, y) }

    /** Map a drag vector to the key's corner symbol (null = treat as a tap). */
    private fun cornerFor(k: Key, dx: Float, dy: Float): String? {
        if (dx * dx + dy * dy < swipeThreshold * swipeThreshold) return null
        return when {
            dx >= 0 && dy < 0 -> k.ne
            dx >= 0 && dy >= 0 -> k.se
            dx < 0 && dy < 0 -> k.nw
            else -> k.sw
        }
    }

    private fun fire(key: Key, longPress: Boolean) = listener?.onKey(key, longPress)

    private fun scheduleRepeat(k: Key) {
        val r = object : Runnable {
            override fun run() {
                if (pressed?.key === k) {
                    fire(k, false)
                    handler.postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, REPEAT_DELAY_MS)
    }

    private fun scheduleLong(k: Key) {
        val r = Runnable {
            if (pressed?.key === k) {
                longFired = true
                feedback()
                fire(k, true)
            }
        }
        longRunnable = r
        handler.postDelayed(r, LONG_PRESS_MS)
    }

    private fun cancelTimers() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        longRunnable = null
    }

    private fun feedback() {
        if (haptics) {
            performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        }
        if (sound) {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelTimers()
    }

    companion object {
        private const val REPEAT_DELAY_MS = 320L
        private const val REPEAT_INTERVAL_MS = 55L
        private const val LONG_PRESS_MS = 300L
    }
}
