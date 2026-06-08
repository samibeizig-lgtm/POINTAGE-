package com.pointage.app.face

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object FaceRecognitionHelper {

    private const val EMBEDDING_SIZE = 64

    fun extractEmbedding(bitmap: Bitmap, boundingBox: Rect): FloatArray {
        val left = max(0, boundingBox.left)
        val top = max(0, boundingBox.top)
        val right = min(bitmap.width, boundingBox.right)
        val bottom = min(bitmap.height, boundingBox.bottom)

        if (right <= left || bottom <= top) return FloatArray(EMBEDDING_SIZE * EMBEDDING_SIZE)

        val faceBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val resized = Bitmap.createScaledBitmap(faceBitmap, EMBEDDING_SIZE, EMBEDDING_SIZE, true)
        faceBitmap.recycle()

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

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
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
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
