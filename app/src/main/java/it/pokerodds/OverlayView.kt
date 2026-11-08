package it.pokerodds

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Draws the recognised cards over the camera preview (which uses FIT_CENTER) and the
 * draggable line that separates the board (above) from your cards (below).
 */
class OverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    /** Line position as a fraction of the frame height. */
    var split = 0.62f
        private set
    var onSplitChanged: (() -> Unit)? = null

    private var dets: List<Detection> = emptyList()
    private var imgW = 3
    private var imgH = 4
    private val d = resources.displayMetrics.density

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f * d; isFakeBoldText = true }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE3B54A.toInt(); strokeWidth = 2f * d; pathEffect = DashPathEffect(floatArrayOf(10f * d, 6f * d), 0f)
    }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3B54A.toInt(); textSize = 12f * d; isFakeBoldText = true }
    private val hintBg = Paint().apply { color = 0x99000000.toInt() }

    fun update(detections: List<Detection>, frameW: Int, frameH: Int) {
        dets = detections; imgW = frameW; imgH = frameH
        invalidate()
    }

    private fun frameRect(): RectF {
        val s = min(width.toFloat() / imgW, height.toFloat() / imgH)
        val w = imgW * s
        val h = imgH * s
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        return RectF(l, t, l + w, t + h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fr = frameRect()
        val ly = fr.top + split * fr.height()
        for (det in dets) {
            val r = RectF(
                fr.left + det.box.left * fr.width(), fr.top + det.box.top * fr.height(),
                fr.left + det.box.right * fr.width(), fr.top + det.box.bottom * fr.height(),
            )
            val mine = r.centerY() > ly
            boxPaint.color = if (mine) 0xFF3FCF8E.toInt() else 0xFFE3B54A.toInt()
            canvas.drawRect(r, boxPaint)
            val label = Cards.display(det.card)
            val tw = labelText.measureText(label)
            labelBg.color = Cards.suitColor(det.card)
            canvas.drawRect(r.left, r.top - 18 * d, r.left + tw + 8 * d, r.top, labelBg)
            canvas.drawText(label, r.left + 4 * d, r.top - 4 * d, labelText)
        }
        canvas.drawLine(fr.left, ly, fr.right, ly, linePaint)
        val up = "▲ TAVOLO"
        val down = "▼ LE TUE CARTE"
        val x = fr.left + 8 * d
        canvas.drawRect(x - 4 * d, ly - 20 * d, x + hintText.measureText(up) + 4 * d, ly - 4 * d, hintBg)
        canvas.drawText(up, x, ly - 8 * d, hintText)
        canvas.drawRect(x - 4 * d, ly + 4 * d, x + hintText.measureText(down) + 4 * d, ly + 20 * d, hintBg)
        canvas.drawText(down, x, ly + 16 * d, hintText)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val fr = frameRect()
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                split = ((event.y - fr.top) / fr.height()).coerceIn(0.2f, 0.95f)
                invalidate()
                true
            }
            MotionEvent.ACTION_UP -> { onSplitChanged?.invoke(); true }
            else -> super.onTouchEvent(event)
        }
    }
}
