package com.pointage.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pointage.app.data.AppDatabase
import com.pointage.app.data.model.*
import com.pointage.app.repository.PointageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class PointageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository by lazy { PointageRepository(AppDatabase.getDatabase(application)) }

    val employees = repository.employees

    private val _pointageResult = MutableLiveData<PointageResultat?>()
    val pointageResult: LiveData<PointageResultat?> = _pointageResult

    private val _fichePresence = MutableLiveData<FichePresence?>()
    val fichePresence: LiveData<FichePresence?> = _fichePresence

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _facePointageResult = MutableLiveData<Pair<String, TypePointage>?>()
    val facePointageResult: LiveData<Pair<String, TypePointage>?> = _facePointageResult

    private val _employeeSelectionne = MutableLiveData<Employee?>()
    val employeeSelectionne: LiveData<Employee?> = _employeeSelectionne

    fun selectionnerEmployee(employee: Employee) {
        _employeeSelectionne.value = employee
    }

    fun effectuerPointage(employeeId: Long, methode: MethodeAuthentification) {
        viewModelScope.launch {
            try {
                val type = repository.inscrirePointage(employeeId, methode)
                _pointageResult.value = PointageResultat(employeeId, type, System.currentTimeMillis())
            } catch (e: Exception) {
                _error.value = "Erreur lors du pointage: ${e.message}"
            }
        }
    }

    fun chargerFichePresence(employeeId: Long, mois: Int, annee: Int) {
        viewModelScope.launch {
            try {
                val fiche = withContext(Dispatchers.IO) { repository.getFichePresence(employeeId, mois, annee) }
                _fichePresence.value = fiche
            } catch (e: Exception) {
                _error.value = "Erreur chargement fiche: ${e.message}"
            }
        }
    }

    fun ajouterEmployee(nom: String, prenom: String, matricule: String, poste: String) {
        viewModelScope.launch {
            try {
                repository.ajouterEmployee(nom, prenom, matricule, poste)
            } catch (e: Exception) {
                _error.value = "Erreur ajout employé: ${e.message}"
            }
        }
    }

    fun clearPointageResult() {
        _pointageResult.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun enregistrerVisage(employeeId: Long, embeddingStr: String) {
        viewModelScope.launch {
            try {
                repository.enregistrerVisage(employeeId, embeddingStr)
                repository.enregistrerBiometrie(employeeId, "VISAGE")
            } catch (e: Exception) {
                _error.value = "Erreur enregistrement visage: ${e.message}"
            }
        }
    }

    fun identifierEtPointerParVisage(embedding: FloatArray) {
        viewModelScope.launch {
            try {
                val result = repository.identifierEtPointer(embedding)
                if (result != null) {
                    _facePointageResult.value = result
                } else {
                    _error.value = "Visage non reconnu"
                }
            } catch (e: Exception) {
                _error.value = "Erreur reconnaissance: ${e.message}"
            }
        }
    }

    fun clearFacePointageResult() {
        _facePointageResult.value = null
    }

    fun enregistrerBiometrie(employeeId: Long, methode: String) {
        viewModelScope.launch {
            try {
                repository.enregistrerBiometrie(employeeId, methode)
            } catch (e: Exception) {
                _error.value = "Erreur enregistrement biométrie: ${e.message}"
            }
        }
    }

    fun reinitialiserBiometrie(employeeId: Long) {
        viewModelScope.launch {
            try {
                repository.reinitialiserBiometrie(employeeId)
            } catch (e: Exception) {
                _error.value = "Erreur réinitialisation: ${e.message}"
            }
        }
    }

    fun getPointagesDuJour(): LiveData<List<Pointage>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val debut = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val fin = cal.timeInMillis
        return repository.getPointagesDuJour(debut, fin)
    }
}

data class PointageResultat(
    val employeeId: Long,
    val type: TypePointage,
    val timestamp: Long
)
