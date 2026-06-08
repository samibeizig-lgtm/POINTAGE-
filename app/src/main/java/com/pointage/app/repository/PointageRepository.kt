package com.pointage.app.repository

import com.pointage.app.data.AppDatabase
import com.pointage.app.data.model.*
import com.pointage.app.face.FaceRecognitionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class PointageRepository(private val db: AppDatabase) {

    val employees = db.employeeDao().getAllEmployees()

    suspend fun inscrirePointage(employeeId: Long, methode: MethodeAuthentification): TypePointage = withContext(Dispatchers.IO) {
        val dernier = db.pointageDao().getDernierPointage(employeeId)
        val type = if (dernier == null || dernier.type == TypePointage.DEPART) {
            TypePointage.ARRIVEE
        } else {
            TypePointage.DEPART
        }
        db.pointageDao().insert(Pointage(employeeId = employeeId, type = type, methode = methode))
        type
    }

    suspend fun getFichePresence(employeeId: Long, mois: Int, annee: Int): FichePresence? = withContext(Dispatchers.IO) {
        val employee = db.employeeDao().getEmployeeById(employeeId) ?: return@withContext null
        val cal = Calendar.getInstance()
        cal.set(annee, mois - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val debut = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val fin = cal.timeInMillis

        val pointages = db.pointageDao().getPointagesPeriode(employeeId, debut, fin)
        val lignes = buildLignesPresence(pointages, mois, annee)
        FichePresence(employee, mois, annee, lignes)
    }

    private fun buildLignesPresence(pointages: List<Pointage>, mois: Int, annee: Int): List<LignePresence> {
        val cal = Calendar.getInstance()
        val grouped = pointages.groupBy { p ->
            cal.timeInMillis = p.timestamp
            Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }

        val nbJours = Calendar.getInstance().apply { set(annee, mois - 1, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

        return (1..nbJours).map { jour ->
            val key = Triple(annee, mois, jour)
            cal.set(annee, mois - 1, jour, 0, 0, 0)
            val dateMs = cal.timeInMillis
            val pointagesJour = grouped[key] ?: emptyList()
            val arrivee = pointagesJour.firstOrNull { it.type == TypePointage.ARRIVEE }?.timestamp
            val depart = pointagesJour.lastOrNull { it.type == TypePointage.DEPART }?.timestamp
            val duree = if (arrivee != null && depart != null) (depart - arrivee) / 60000 else null
            LignePresence(dateMs, arrivee, depart, duree)
        }
    }

    suspend fun ajouterEmployee(nom: String, prenom: String, matricule: String, poste: String): Long = withContext(Dispatchers.IO) {
        db.employeeDao().insert(Employee(nom = nom, prenom = prenom, matricule = matricule, poste = poste))
    }

    suspend fun getAllEmployees() = withContext(Dispatchers.IO) { db.employeeDao().getAllEmployeesList() }

    suspend fun enregistrerBiometrie(employeeId: Long, methode: String) = withContext(Dispatchers.IO) {
        db.employeeDao().updateBiometrie(employeeId, true, methode)
    }

    suspend fun reinitialiserBiometrie(employeeId: Long) = withContext(Dispatchers.IO) {
        db.employeeDao().updateBiometrie(employeeId, false, "")
    }

    fun getPointagesDuJour(debut: Long, fin: Long) = db.pointageDao().getPointagesDuJour(debut, fin)

    fun getPointagesEmployee(employeeId: Long) = db.pointageDao().getPointagesEmployee(employeeId)

    suspend fun enregistrerVisage(employeeId: Long, embeddingStr: String) = withContext(Dispatchers.IO) {
        db.faceSignatureDao().deleteByEmployee(employeeId)
        db.faceSignatureDao().insert(FaceSignature(employeeId = employeeId, embedding = embeddingStr))
    }

    suspend fun identifierEtPointer(embedding: FloatArray): Triple<String?, TypePointage?, Float> = withContext(Dispatchers.IO) {
        val signatures = db.faceSignatureDao().getAllSignatures()
        if (signatures.isEmpty()) return@withContext Triple(null, null, 0f)
        var bestMatch: Long? = null
        var bestScore = 0f
        for (sig in signatures) {
            val storedEmbedding = FaceRecognitionHelper.stringToEmbedding(sig.embedding)
            val score = FaceRecognitionHelper.cosineSimilarity(embedding, storedEmbedding)
            if (score > bestScore) {
                bestScore = score
                bestMatch = sig.employeeId
            }
        }
        if (bestScore < 0.50f || bestMatch == null) return@withContext Triple(null, null, bestScore)
        val employee = db.employeeDao().getEmployeeById(bestMatch) ?: return@withContext Triple(null, null, bestScore)
        val type = inscrirePointageInternal(bestMatch, MethodeAuthentification.VISAGE)
        Triple("${employee.prenom} ${employee.nom}", type, bestScore)
    }

    private suspend fun inscrirePointageInternal(employeeId: Long, methode: MethodeAuthentification): TypePointage {
        val dernier = db.pointageDao().getDernierPointage(employeeId)
        val type = if (dernier == null || dernier.type == TypePointage.DEPART) TypePointage.ARRIVEE else TypePointage.DEPART
        db.pointageDao().insert(Pointage(employeeId = employeeId, type = type, methode = methode))
        return type
    }
}
