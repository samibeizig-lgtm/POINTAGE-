package com.pointage.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.pointage.app.data.model.Employee

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE actif = 1 ORDER BY nom, prenom")
    fun getAllEmployees(): LiveData<List<Employee>>

    @Query("SELECT * FROM employees WHERE actif = 1 ORDER BY nom, prenom")
    suspend fun getAllEmployeesList(): List<Employee>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Long): Employee?

    @Query("SELECT * FROM employees WHERE matricule = :matricule LIMIT 1")
    suspend fun getEmployeeByMatricule(matricule: String): Employee?

    @Insert
    suspend fun insert(employee: Employee): Long

    @Update
    suspend fun update(employee: Employee)

    @Query("UPDATE employees SET actif = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
