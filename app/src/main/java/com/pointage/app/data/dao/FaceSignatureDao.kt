package com.pointage.app.data.dao

import androidx.room.*
import com.pointage.app.data.model.FaceSignature

@Dao
interface FaceSignatureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signature: FaceSignature): Long

    @Query("SELECT * FROM face_signatures")
    suspend fun getAllSignatures(): List<FaceSignature>

    @Query("SELECT * FROM face_signatures WHERE employeeId = :employeeId LIMIT 1")
    suspend fun getSignatureByEmployee(employeeId: Long): FaceSignature?

    @Query("DELETE FROM face_signatures WHERE employeeId = :employeeId")
    suspend fun deleteByEmployee(employeeId: Long)

    @Query("SELECT COUNT(*) FROM face_signatures WHERE employeeId = :employeeId")
    suspend fun countByEmployee(employeeId: Long): Int
}
