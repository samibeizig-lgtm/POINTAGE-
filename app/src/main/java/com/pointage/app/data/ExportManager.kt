package com.pointage.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pointage.app.data.model.Employee
import com.pointage.app.data.model.FaceSignature
import com.pointage.app.data.model.Pointage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportData(
    val version: Int = 1,
    val exportDate: String = "",
    val employees: List<Employee> = emptyList(),
    val pointages: List<Pointage> = emptyList(),
    val faceSignatures: List<FaceSignature> = emptyList()
)

class ExportManager(private val db: AppDatabase) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exporterJson(): String = withContext(Dispatchers.IO) {
        val dao = db.exportDao()
        val data = ExportData(
            version = 1,
            exportDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date()),
            employees = dao.getAllEmployeesForExport(),
            pointages = dao.getAllPointages(),
            faceSignatures = dao.getAllFaceSignatures()
        )
        gson.toJson(data)
    }

    suspend fun importerJson(json: String, remplacer: Boolean = false): ImportResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val data = gson.fromJson(json, ExportData::class.java)
                ?: return@withContext ImportResult(false, "Fichier invalide")

            val dao = db.exportDao()

            if (remplacer) {
                dao.clearFaceSignatures()
                dao.clearPointages()
                dao.clearEmployees()
            }

            // Mapper les anciens IDs vers les nouveaux pour éviter les conflits
            val employeeIdMap = mutableMapOf<Long, Long>()

            for (emp in data.employees) {
                val newId = if (remplacer) {
                    dao.insertEmployee(emp)
                } else {
                    dao.insertEmployee(emp.copy(id = 0))
                }
                employeeIdMap[emp.id] = newId
            }

            var pointagesCount = 0
            for (p in data.pointages) {
                val newEmployeeId = employeeIdMap[p.employeeId] ?: continue
                dao.insertPointage(p.copy(id = 0, employeeId = newEmployeeId))
                pointagesCount++
            }

            for (sig in data.faceSignatures) {
                val newEmployeeId = employeeIdMap[sig.employeeId] ?: continue
                dao.insertFaceSignature(sig.copy(id = 0, employeeId = newEmployeeId))
            }

            ImportResult(
                success = true,
                message = "${data.employees.size} employes, $pointagesCount pointages importes"
            )
        } catch (e: Exception) {
            ImportResult(false, "Erreur: ${e.message}")
        }
    }
}

data class ImportResult(val success: Boolean, val message: String)
