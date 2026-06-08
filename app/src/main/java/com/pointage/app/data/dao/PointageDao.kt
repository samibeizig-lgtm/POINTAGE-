package com.pointage.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.pointage.app.data.model.Pointage

@Dao
interface PointageDao {
    @Insert
    suspend fun insert(pointage: Pointage): Long

    @Query("""
        SELECT * FROM pointages
        WHERE employeeId = :employeeId
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getDernierPointage(employeeId: Long): Pointage?

    @Query("""
        SELECT * FROM pointages
        WHERE employeeId = :employeeId
        AND timestamp >= :debut
        AND timestamp <= :fin
        ORDER BY timestamp ASC
    """)
    suspend fun getPointagesPeriode(employeeId: Long, debut: Long, fin: Long): List<Pointage>

    @Query("""
        SELECT * FROM pointages
        WHERE timestamp >= :debut
        AND timestamp <= :fin
        ORDER BY employeeId, timestamp ASC
    """)
    fun getPointagesDuJour(debut: Long, fin: Long): LiveData<List<Pointage>>

    @Query("SELECT * FROM pointages WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getPointagesEmployee(employeeId: Long): LiveData<List<Pointage>>

    @Delete
    suspend fun delete(pointage: Pointage)
}
