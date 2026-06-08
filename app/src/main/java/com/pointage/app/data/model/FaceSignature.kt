package com.pointage.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_signatures",
    foreignKeys = [ForeignKey(
        entity = Employee::class,
        parentColumns = ["id"],
        childColumns = ["employeeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("employeeId")]
)
data class FaceSignature(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val embedding: String, // FloatArray serialized as comma-separated string
    val capturedAt: Long = System.currentTimeMillis()
)
