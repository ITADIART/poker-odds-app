package it.pokerodds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/** One detected card; box coordinates are 0..1 fractions of the analysed (upright) frame. */
data class Detection(val card: Int, val conf: Float, val box: RectF)

/**
 * Runs the YOLOv8 card model (assets/cards.tflite + assets/labels.txt) fully on the phone.
 * Not thread-safe: call [detect] from one thread.
 */
class CardDetector(context: Context) {
    private val labels: List<String> =
        context.assets.open("labels.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
    private val cardOfClass = IntArray(labels.size) { Cards.parseLabel(labels[it]) }
    private val interpreter: Interpreter
    val inW: Int
    val inH: Int
    private val channelsFirst: Boolean
    private val numAnchors: Int
    private val output: Array<Array<FloatArray>>
    private val input: ByteBuffer
    private val letterbox: Bitmap
    private val pixels: IntArray
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    val unknownLabels: List<String> get() = labels.filterIndexed { i, _ -> cardOfClass[i] < 0 }

    init {
        val fd = context.assets.openFd("cards.tflite")
        val model = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        val ish = interpreter.getInputTensor(0).shape() // [1, H, W, 3]
        inH = ish[1]; inW = ish[2]
        val osh = interpreter.getOutputTensor(0).shape() // [1, 4+nc, N] or [1, N, 4+nc]
        channelsFirst = osh[1] == 4 + labels.size || osh[1] < osh[2]
        numAnchors = if (channelsFirst) osh[2] else osh[1]
        output = Array(1) { Array(osh[1]) { FloatArray(osh[2]) } }
        input = ByteBuffer.allocateDirect(4 * inW * inH * 3).order(ByteOrder.nativeOrder())
        letterbox = Bitmap.createBitmap(inW, inH, Bitmap.Config.ARGB_8888)
        pixels = IntArray(inW * inH)
    }

    fun detect(src: Bitmap, minConf: Float = 0.45f): List<Detection> {
        // Letterbox: keep aspect ratio, pad with grey like YOLO training does.
        val scale = min(inW.toFloat() / src.width, inH.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val padX = (inW - dw) / 2f
        val padY = (inH - dh) / 2f
        val canvas = Canvas(letterbox)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(src, null, RectF(padX, padY, padX + dw, padY + dh), paint)
        letterbox.getPixels(pixels, 0, inW, 0, 0, inW, inH)
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        input.rewind()
        interpreter.run(input, output)

        val out = output[0]
        val nc = labels.size
        val cands = ArrayList<Detection>()
        for (a in 0 until numAnchors) {
            var best = -1
            var bestScore = minConf
            for (c in 0 until nc) {
                val s = if (channelsFirst) out[4 + c][a] else out[a][4 + c]
                if (s > bestScore) { bestScore = s; best = c }
            }
            if (best < 0 || cardOfClass[best] < 0) continue
            var cx = if (channelsFirst) out[0][a] else out[a][0]
            var cy = if (channelsFirst) out[1][a] else out[a][1]
            var w = if (channelsFirst) out[2][a] else out[a][2]
            var h = if (channelsFirst) out[3][a] else out[a][3]
            if (cx <= 1.5f && cy <= 1.5f && w <= 1.5f && h <= 1.5f) { // normalised output
                cx *= inW; cy *= inH; w *= inW; h *= inH
            }
            val x1 = ((cx - w / 2 - padX) / scale) / src.width
            val y1 = ((cy - h / 2 - padY) / scale) / src.height
            val x2 = ((cx + w / 2 - padX) / scale) / src.width
            val y2 = ((cy + h / 2 - padY) / scale) / src.height
            cands.add(Detection(cardOfClass[best], bestScore, RectF(x1, y1, x2, y2)))
        }
        return nms(cands, 0.5f)
    }

    private fun nms(dets: List<Detection>, iouThr: Float): List<Detection> {
        val sorted = dets.sortedByDescending { it.conf }
        val keep = ArrayList<Detection>()
        for (d in sorted) {
            if (keep.none { it.card == d.card && iou(it.box, d.box) > iouThr }) keep.add(d)
            if (keep.size >= 40) break
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() = interpreter.close()
}
