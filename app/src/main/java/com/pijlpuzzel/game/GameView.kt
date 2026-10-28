package com.pijlpuzzel.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onArrowRemoved(remaining: Int)
        fun onMistake()
        fun onCleared()
    }

    var listener: Listener? = null
    var board: Board? = null
        private set

    /** Pijlen die net weggetikt zijn en nog uit beeld schuiven. */
    private class Flying(val arrow: Arrow, val cells: Int, var progress: Float = 0f)

    private val flying = mutableListOf<Flying>()
    private var bumped: Arrow? = null
    private var blockedBy: Arrow? = null
    private var bumpProgress = 0f
    private var hintArrow: Arrow? = null
    private var hintPulse = 0f
    private var hintAnimator: ValueAnimator? = null
    var inputEnabled = true

    private var cell = 0f
    private var originX = 0f
    private var originY = 0f

    private val dirColors = mapOf(
        Dir.UP to Color.parseColor("#4F8EF7"),
        Dir.RIGHT to Color.parseColor("#F7A64F"),
        Dir.DOWN to Color.parseColor("#4FC98E"),
        Dir.LEFT to Color.parseColor("#C46BF2"),
    )
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A3350") }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFE066")
    }
    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF5A5A")
    }
    private val rect = RectF()
    private val arrowPath = Path()

    fun loadBoard(newBoard: Board) {
        board = newBoard
        flying.clear()
        bumped = null
        blockedBy = null
        clearHint()
        inputEnabled = true
        requestLayout()
        computeGeometry()
        invalidate()
    }

    fun showHint() {
        val target = board?.hint() ?: return
        hintArrow = target
        hintAnimator?.cancel()
        hintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { hintPulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun clearHint() {
        hintAnimator?.cancel()
        hintAnimator = null
        hintArrow = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeGeometry()
    }

    private fun computeGeometry() {
        val b = board ?: return
        if (width == 0 || height == 0) return
        val pad = 16f * resources.displayMetrics.density
        cell = min((width - 2 * pad) / b.cols, (height - 2 * pad) / b.rows)
        originX = (width - cell * b.cols) / 2f
        originY = (height - cell * b.rows) / 2f
        hintPaint.strokeWidth = cell * 0.08f
        errorPaint.strokeWidth = cell * 0.08f
        buildArrowPath()
    }

    /** Pijl die naar boven wijst, gecentreerd rond (0,0). Draaien doen we bij het tekenen. */
    private fun buildArrowPath() {
        val s = cell * 0.5f
        arrowPath.reset()
        arrowPath.moveTo(0f, -s * 0.62f)
        arrowPath.lineTo(s * 0.48f, -s * 0.08f)
        arrowPath.lineTo(s * 0.17f, -s * 0.08f)
        arrowPath.lineTo(s * 0.17f, s * 0.58f)
        arrowPath.lineTo(-s * 0.17f, s * 0.58f)
        arrowPath.lineTo(-s * 0.17f, -s * 0.08f)
        arrowPath.lineTo(-s * 0.48f, -s * 0.08f)
        arrowPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        val b = board ?: return
        for (y in 0 until b.rows) for (x in 0 until b.cols) {
            canvas.drawCircle(cx(x), cy(y), cell * 0.06f, dotPaint)
        }
        for (a in b.arrows()) {
            var ox = 0f
            var oy = 0f
            if (a == bumped) {
                val shake = sin(bumpProgress * Math.PI * 3).toFloat() * (1 - bumpProgress) * cell * 0.22f
                ox = a.dir.dx * shake
                oy = a.dir.dy * shake
            }
            drawArrow(canvas, a, ox, oy, 255)
            if (a == hintArrow) {
                hintPaint.alpha = (120 + 135 * hintPulse).toInt()
                tileRect(a, 0f, 0f, cell * 0.02f)
                canvas.drawRoundRect(rect, cell * 0.2f, cell * 0.2f, hintPaint)
            }
            if (a == blockedBy && bumped != null) {
                errorPaint.alpha = (255 * (1 - bumpProgress)).toInt()
                tileRect(a, 0f, 0f, cell * 0.02f)
                canvas.drawRoundRect(rect, cell * 0.2f, cell * 0.2f, errorPaint)
            }
        }
        for (f in flying) {
            val dist = f.progress * f.cells * cell
            val alpha = (255 * (1 - f.progress * 0.8f)).toInt()
            drawArrow(canvas, f.arrow, f.arrow.dir.dx * dist, f.arrow.dir.dy * dist, alpha)
        }
    }

    private fun drawArrow(canvas: Canvas, a: Arrow, ox: Float, oy: Float, alpha: Int) {
        tilePaint.color = dirColors.getValue(a.dir)
        tilePaint.alpha = alpha
        arrowPaint.alpha = alpha
        tileRect(a, ox, oy, cell * 0.07f)
        canvas.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, tilePaint)
        canvas.save()
        canvas.translate(cx(a.x) + ox, cy(a.y) + oy)
        canvas.rotate(a.dir.degrees)
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
    }

    private fun tileRect(a: Arrow, ox: Float, oy: Float, inset: Float) {
        val left = originX + a.x * cell + ox
        val top = originY + a.y * cell + oy
        rect.set(left + inset, top + inset, left + cell - inset, top + cell - inset)
    }

    private fun cx(x: Int) = originX + (x + 0.5f) * cell
    private fun cy(y: Int) = originY + (y + 0.5f) * cell

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val b = board ?: return true
        if (!inputEnabled || cell == 0f) return true
        val x = ((event.x - originX) / cell).toInt()
        val y = ((event.y - originY) / cell).toInt()
        if (event.x < originX || event.y < originY) return true
        val arrow = b.arrowAt(x, y) ?: return true
        tap(b, arrow)
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun tap(b: Board, arrow: Arrow) {
        val blocker = b.blocker(arrow)
        if (blocker == null) {
            if (arrow == hintArrow) clearHint()
            // Tot net buiten het scherm schuiven, dus verder dan alleen de rand van het bord.
            val screenCells = (maxOf(width, height) / cell).toInt() + 2
            val f = Flying(arrow, b.distanceToEdge(arrow) + screenCells)
            b.remove(arrow)
            flying += f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 420
                interpolator = AccelerateInterpolator()
                addUpdateListener { f.progress = it.animatedValue as Float; invalidate() }
                doOnEnd { flying.remove(f); invalidate() }
                start()
            }
            listener?.onArrowRemoved(b.remaining)
            if (b.isCleared) {
                inputEnabled = false
                postDelayed({ listener?.onCleared() }, 450)
            }
        } else {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            bumped = arrow
            blockedBy = blocker
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 450
                addUpdateListener { bumpProgress = it.animatedValue as Float; invalidate() }
                doOnEnd { if (bumped == arrow) { bumped = null; blockedBy = null }; invalidate() }
                start()
            }
            listener?.onMistake()
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearHint()
    }
}
