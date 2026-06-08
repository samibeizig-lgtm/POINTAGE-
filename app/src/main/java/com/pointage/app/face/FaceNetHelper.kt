package com.pointage.app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.atan2

object FaceNetHelper {

    private const val INPUT_SIZE = 112
    private const val EMBEDDING_SIZE = 128
    private const val MODEL_FILE = "mobile_face_net.tflite"

    private var interpreter: Interpreter? = null
    private var initialized = false

    fun initialize(context: Context): Boolean {
        if (initialized) return true
        return try {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(model, options)
            initialized = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isAvailable() = initialized && interpreter != null

    fun getEmbedding(bitmap: Bitmap, boundingBox: Rect, face: Face? = null): FloatArray {
        val aligned = if (face != null) alignFace(bitmap, boundingBox, face) else null
        val source = aligned ?: cropWithPadding(bitmap, boundingBox)
        val resized = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
        source.recycle()

        val input = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)
                input.putFloat((Color.red(pixel) - 127.5f) / 128f)
                input.putFloat((Color.green(pixel) - 127.5f) / 128f)
                input.putFloat((Color.blue(pixel) - 127.5f) / 128f)
            }
        }
        resized.recycle()

        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        interpreter!!.run(input, output)
        return l2normalize(output[0])
    }

    private fun cropWithPadding(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val pad = (boundingBox.width() * 0.3f).toInt()
        val left = max(0, boundingBox.left - pad)
        val top = max(0, boundingBox.top - pad)
        val right = min(bitmap.width, boundingBox.right + pad)
        val bottom = min(bitmap.height, boundingBox.bottom + pad)
        return if (right > left && bottom > top)
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        else bitmap
    }

    private fun alignFace(bitmap: Bitmap, boundingBox: Rect, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val matrix = Matrix().apply { postRotate(-angle, cx, cy) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val pad = (boundingBox.width() * 0.3f).toInt()
        val left = max(0, boundingBox.left - pad)
        val top = max(0, boundingBox.top - pad)
        val right = min(rotated.width, boundingBox.right + pad)
        val bottom = min(rotated.height, boundingBox.bottom + pad)
        if (right <= left || bottom <= top) { rotated.recycle(); return null }
        val cropped = Bitmap.createBitmap(rotated, left, top, right - left, bottom - top)
        rotated.recycle()
        return cropped
    }

    private fun l2normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        return if (norm > 0f) FloatArray(v.size) { v[it] / norm } else v
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // already L2-normalized, so cosine = dot product
    }

    fun embeddingToString(e: FloatArray): String = e.joinToString(",")

    fun stringToEmbedding(s: String): FloatArray =
        if (s.isEmpty()) FloatArray(0)
        else s.split(",").map { it.toFloat() }.toFloatArray()

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val afd = context.assets.openFd(MODEL_FILE)
        val fis = FileInputStream(afd.fileDescriptor)
        val channel = fis.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }
}
