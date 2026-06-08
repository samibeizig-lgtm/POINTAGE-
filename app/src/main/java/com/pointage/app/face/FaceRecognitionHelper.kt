package com.pointage.app.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object FaceRecognitionHelper {

    private const val EMBEDDING_SIZE = 64

    fun extractEmbedding(bitmap: Bitmap, boundingBox: Rect, face: com.google.mlkit.vision.face.Face? = null): FloatArray {
        val aligned = if (face != null) alignFace(bitmap, boundingBox, face) else null
        val source = aligned ?: run {
            val left = max(0, boundingBox.left - boundingBox.width() / 6)
            val top = max(0, boundingBox.top - boundingBox.height() / 6)
            val right = min(bitmap.width, boundingBox.right + boundingBox.width() / 6)
            val bottom = min(bitmap.height, boundingBox.bottom + boundingBox.height() / 6)
            if (right <= left || bottom <= top) return FloatArray(EMBEDDING_SIZE * EMBEDDING_SIZE)
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }

        val resized = Bitmap.createScaledBitmap(source, EMBEDDING_SIZE, EMBEDDING_SIZE, true)
        source.recycle()

        val embedding = FloatArray(EMBEDDING_SIZE * EMBEDDING_SIZE)
        var mean = 0f
        for (y in 0 until EMBEDDING_SIZE) {
            for (x in 0 until EMBEDDING_SIZE) {
                val pixel = resized.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f) / 255f
                embedding[y * EMBEDDING_SIZE + x] = gray
                mean += gray
            }
        }
        resized.recycle()

        mean /= embedding.size
        var std = 0f
        for (v in embedding) std += (v - mean) * (v - mean)
        std = sqrt(std / embedding.size)
        if (std > 0f) for (i in embedding.indices) embedding[i] = (embedding[i] - mean) / std

        return embedding
    }

    private fun alignFace(bitmap: Bitmap, boundingBox: Rect, face: com.google.mlkit.vision.face.Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val matrix = Matrix().apply { postRotate(-angle, cx, cy) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val padding = (boundingBox.width() * 0.25f).toInt()
        val left = max(0, boundingBox.left - padding)
        val top = max(0, boundingBox.top - padding)
        val right = min(rotated.width, boundingBox.right + padding)
        val bottom = min(rotated.height, boundingBox.bottom + padding)
        if (right <= left || bottom <= top) { rotated.recycle(); return null }

        val cropped = Bitmap.createBitmap(rotated, left, top, right - left, bottom - top)
        rotated.recycle()
        return cropped
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }

    fun embeddingToString(embedding: FloatArray): String = embedding.joinToString(",")

    fun stringToEmbedding(s: String): FloatArray =
        if (s.isEmpty()) FloatArray(0)
        else s.split(",").map { it.toFloat() }.toFloatArray()

    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
