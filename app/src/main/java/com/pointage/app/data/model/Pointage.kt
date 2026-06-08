package com.pointage.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pointages",
    foreignKeys = [ForeignKey(
        entity = Employee::class,
        parentColumns = ["id"],
        childColumns = ["employeeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("employeeId")]
)
data class Pointage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val employeeId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val type: TypePointage,
    val methode: MethodeAuthentification = MethodeAuthentification.EMPREINTE
)

enum class TypePointage {
    ARRIVEE, DEPART
}

enum class MethodeAuthentification {
    EMPREINTE, VISAGE, MANUEL
}
