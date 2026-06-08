package com.pointage.app.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import kotlin.math.sqrt

object GeometricFaceHelper {

    // Points utilisés : contour du visage + nez + bouche (stables même avec lunettes/chapeau)
    private val CONTOUR_TYPES = listOf(
        FaceContour.FACE,              // 36 points — ovale du visage
        FaceContour.NOSE_BRIDGE,       //  4 points
        FaceContour.NOSE_BOTTOM,       // 11 points
        FaceContour.UPPER_LIP_TOP,     //  7 points
        FaceContour.UPPER_LIP_BOTTOM,  //  7 points
        FaceContour.LOWER_LIP_TOP,     //  7 points
        FaceContour.LOWER_LIP_BOTTOM   //  7 points
    )
    // Total : 79 points → vecteur de 158 valeurs

    fun extractEmbedding(face: Face): FloatArray? {
        val points = mutableListOf<Pair<Float, Float>>()
        for (type in CONTOUR_TYPES) {
            val contour = face.getContour(type) ?: return null
            if (contour.points.isEmpty()) return null
            for (p in contour.points) points.add(Pair(p.x, p.y))
        }
        if (points.isEmpty()) return null

        // Normalisation par rapport au centre et à la largeur du visage
        val box = face.boundingBox
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val scale = box.width().toFloat().coerceAtLeast(1f)

        val features = FloatArray(points.size * 2)
        for (i in points.indices) {
            features[i * 2]     = (points[i].first  - cx) / scale
            features[i * 2 + 1] = (points[i].second - cy) / scale
        }
        return l2normalize(features)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // déjà L2-normalisé → cosine = produit scalaire
    }

    fun embeddingToString(e: FloatArray): String = e.joinToString(",")

    fun stringToEmbedding(s: String): FloatArray =
        if (s.isEmpty()) FloatArray(0)
        else s.split(",").map { it.toFloat() }.toFloatArray()

    private fun l2normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        return if (norm > 0f) FloatArray(v.size) { v[it] / norm } else v
    }
}
