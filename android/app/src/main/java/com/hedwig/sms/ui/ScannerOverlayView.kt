package com.hedwig.sms.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 优雅的扫码遮罩 / elegant scanner overlay.
 *
 * 暗化四周、镂空一个圆角取景窗、绿色四角括号、循环扫描线动画。
 * Dims the surroundings, cuts a rounded viewfinder window, draws green corner brackets
 * and an animated scan line.
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val green = Color.parseColor("#34D399")
    private val scrim = Color.parseColor("#B3000000")

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scrim }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = green
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val window = RectF()
    private var scanFrac = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { scanFrac = it.animatedValue as Float; invalidate() }
    }

    init {
        // CLEAR xfermode 需要软件层 / software layer required for PorterDuff.CLEAR
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun computeWindow() {
        val side = minOf(width, height) * 0.70f
        val cx = width / 2f
        val cy = height / 2f - dp(20f) // 稍微上移，给底部提示留空间
        window.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        computeWindow()
        val r = dp(22f)

        // 1) 暗化四周 + 镂空圆角窗 / dim + cut rounded window
        val sl = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRoundRect(window, r, r, clearPaint)
        canvas.restoreToCount(sl)

        // 2) 四角括号 / corner brackets
        val len = dp(28f)
        drawCorner(canvas, window.left, window.top, len, len, r)        // 左上
        drawCorner(canvas, window.right, window.top, -len, len, r)      // 右上
        drawCorner(canvas, window.left, window.bottom, len, -len, r)    // 左下
        drawCorner(canvas, window.right, window.bottom, -len, -len, r)  // 右下

        // 3) 扫描线 / scan line
        val pad = dp(14f)
        val y = window.top + pad + scanFrac * (window.height() - pad * 2)
        linePaint.shader = LinearGradient(
            window.left, y, window.right, y,
            intArrayOf(0x0034D399, 0xCC34D399.toInt(), 0x0034D399),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(window.left + pad, y - dp(1.5f), window.right - pad, y + dp(1.5f), linePaint)
    }

    /** 画一个 L 形圆角括号 / draw one rounded L-shaped corner. */
    private fun drawCorner(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, r: Float) {
        val path = Path()
        path.moveTo(x, y + dy)
        path.lineTo(x, y + Math.copySign(r, dy))
        path.quadTo(x, y, x + Math.copySign(r, dx), y)
        path.lineTo(x + dx, y)
        canvas.drawPath(path, cornerPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
