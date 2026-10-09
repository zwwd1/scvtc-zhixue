package com.tyust.course.ui.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import com.tyust.course.R
import kotlin.math.PI
import kotlin.math.sin

/** Native splash and application overlay keep the original card choreography. */
class StartupLogoRenderer(context: Context) {
    private val cap = BitmapDrawable(context.resources, requireNotNull(BitmapFactory.decodeResource(
        context.resources, R.drawable.campus_icon_portrait,
        BitmapFactory.Options().apply { inSampleSize = 4; inScaled = false }
    )))
    private val bolt = requireNotNull(ContextCompat.getDrawable(context, R.drawable.campus_startup_accent)).mutate()
    private val portraitMask = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPath = requireNotNull(PathParser.createPathFromPathData(
        "M764,431 L855,399 M781,496 L868,463"
    ))
    private val boltMeasure = PathMeasure(boltPath, false)
    private val trace = Path()
    private val colors = intArrayOf(0xFF219EF0.toInt(), 0xFF29ACDE.toInt(), 0xFF29BBC5.toInt(),
        0xFF258DDE.toInt(), 0xFF29A7D3.toInt(), 0xFF32C9A7.toInt())

    fun draw(canvas: Canvas, bounds: Rect, milliseconds: Float, opacity: Float = 1f) {
        val exit = StartupChoreography.content(milliseconds)
        val alpha = ((1f - exit) * opacity * 255).toInt().coerceIn(0, 255)
        if (alpha == 0) return
        canvas.save()
        val scale = StartupChoreography.settleScale(milliseconds)
        canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())
        cap.bounds = bounds
        bolt.bounds = bounds
        portraitMask.reset()
        portraitMask.addRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(),
            bounds.bottom.toFloat(), bounds.width() * .24f, bounds.height() * .24f, Path.Direction.CW)
        val unit = bounds.width() / 1024f
        if (milliseconds <= 0f || milliseconds >= 650f) {
            cap.alpha = alpha
            drawPortrait(canvas)
        } else repeat(6) { index ->
            val frame = StartupChoreography.card(milliseconds, index)
            val left = bounds.left + (218f + (index % 3) * 196f) * unit
            val top = bounds.top + (270f + (index / 3) * 240f) * unit
            val right = left + 196f * unit
            val bottom = top + 240f * unit
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            canvas.save()
            canvas.translate(frame.x * unit, frame.y * unit)
            canvas.rotate(frame.rotation, cx, cy)
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            cap.alpha = (alpha * (1f - frame.cardAmount)).toInt()
            drawPortrait(canvas)
            canvas.restore()
            paint.style = Paint.Style.FILL
            paint.color = colors[index]
            paint.alpha = (alpha * frame.cardAmount).toInt()
            canvas.drawRoundRect(cx - 84f * unit, cy - 70f * unit, cx + 84f * unit, cy + 70f * unit, 18f * unit, 18f * unit, paint)
            paint.color = Color.WHITE
            paint.alpha = (alpha * frame.cardAmount * 0.9f).toInt()
            paint.strokeWidth = 8f * unit
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx - 54f * unit, cy - 36f * unit, cx - 20f * unit, cy - 36f * unit, paint)
            paint.alpha = (alpha * frame.cardAmount * 0.72f).toInt()
            canvas.drawLine(cx - 54f * unit, cy - 4f * unit, cx + 48f * unit, cy - 4f * unit, paint)
            paint.alpha = (alpha * frame.cardAmount * 0.42f).toInt()
            canvas.drawLine(cx - 54f * unit, cy + 27f * unit, cx + 14f * unit, cy + 27f * unit, paint)
            canvas.restore()
        }
        val reveal = StartupChoreography.bolt(milliseconds)
        if (milliseconds >= 650f && milliseconds < 850f) {
            canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(unit, unit)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF33D29C.toInt()
            paint.alpha = (sin(reveal * PI.toFloat()) * 28f).toInt().coerceIn(0, 28)
            canvas.drawCircle(812f, 445f, 62f + 18f * reveal, paint)
            trace.reset()
            boltMeasure.getSegment(0f, boltMeasure.length * reveal, trace, true)
            paint.alpha = (alpha * sin(reveal * PI.toFloat())).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 9f
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(trace, paint)
            canvas.restore()
        }
        bolt.alpha = (alpha * reveal * reveal).toInt()
        bolt.draw(canvas)
        canvas.restore()
    }

    private fun drawPortrait(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(portraitMask)
        cap.draw(canvas)
        canvas.restore()
    }
}
