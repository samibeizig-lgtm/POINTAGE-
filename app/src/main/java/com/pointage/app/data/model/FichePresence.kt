package com.pointage.app.data.model

data class FichePresence(
    val employee: Employee,
    val mois: Int,
    val annee: Int,
    val lignes: List<LignePresence>
)

data class LignePresence(
    val date: Long,
    val arriveeId: Long?,
    val arrivee: Long?,
    val departId: Long?,
    val depart: Long?,
    val dureeMinutes: Long?
) {
    val heuresTravaillees: Double
        get() = (dureeMinutes ?: 0L) / 60.0
}
