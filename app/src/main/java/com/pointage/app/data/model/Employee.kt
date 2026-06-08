package com.pointage.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nom: String,
    val prenom: String,
    val matricule: String,
    val poste: String,
    val actif: Boolean = true,
    val dateCreation: Long = System.currentTimeMillis(),
    val biometrieConfiguree: Boolean = false,
    val methodeAuth: String = ""
)
