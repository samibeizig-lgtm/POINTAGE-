package com.pointage.app.data.dao

import androidx.room.*
import com.pointage.app.data.model.Employee
import com.pointage.app.data.model.FaceSignature
import com.pointage.app.data.model.Pointage

@Dao
interface ExportDao {
    @Query("SELECT * FROM employees")
    suspend fun getAllEmployeesForExport(): List<Employee>

    @Query("SELECT * FROM pointages ORDER BY timestamp ASC")
    suspend fun getAllPointages(): List<Pointage>

    @Query("SELECT * FROM face_signatures")
    suspend fun getAllFaceSignatures(): List<FaceSignature>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPointage(pointage: Pointage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaceSignature(sig: FaceSignature)

    @Query("DELETE FROM employees")
    suspend fun clearEmployees()

    @Query("DELETE FROM pointages")
    suspend fun clearPointages()

    @Query("DELETE FROM face_signatures")
    suspend fun clearFaceSignatures()
}
