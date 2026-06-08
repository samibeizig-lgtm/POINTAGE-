package com.pointage.app.ui.pointage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pointage.app.R
import com.pointage.app.data.model.Employee
import com.pointage.app.data.model.MethodeAuthentification
import com.pointage.app.data.model.TypePointage
import com.pointage.app.databinding.FragmentPointageBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.text.SimpleDateFormat
import java.util.*

class PointageFragment : Fragment() {

    private var _binding: FragmentPointageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()
    private var employeesList: List<Employee> = emptyList()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPointageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            employeesList = employees
            val noms = employees.map { "${it.nom} ${it.prenom} (${it.matricule})" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noms)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerEmployee.adapter = adapter
        }

        viewModel.pointageResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val typeStr = if (result.type == TypePointage.ARRIVEE) "ARRIVEE" else "DEPART"
            val heure = dateFormat.format(Date(result.timestamp))
            binding.tvResultat.text = "OK $typeStr enregistre a $heure"
            binding.tvResultat.visibility = View.VISIBLE
            viewModel.clearPointageResult()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }

        binding.btnEmpreinte.setOnClickListener {
            lancerBiometrie(MethodeAuthentification.EMPREINTE)
        }

        binding.btnVisage.setOnClickListener {
            lancerBiometrie(MethodeAuthentification.VISAGE)
        }

        afficherHeureActuelle()
    }

    private fun afficherHeureActuelle() {
        val sdf = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH)
        binding.tvDate.text = sdf.format(Date())
    }

    private fun lancerBiometrie(methode: MethodeAuthentification) {
        val position = binding.spinnerEmployee.selectedItemPosition
        if (employeesList.isEmpty() || position < 0) {
            Toast.makeText(requireContext(), "Veuillez selectionner un employe", Toast.LENGTH_SHORT).show()
            return
        }
        val employee = employeesList[position]

        val biometricManager = BiometricManager.from(requireContext())
        val authenticators = if (methode == MethodeAuthentification.VISAGE) {
            BIOMETRIC_STRONG or BIOMETRIC_WEAK
        } else {
            BIOMETRIC_STRONG
        }

        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                afficherPromptBiometrique(employee, methode, authenticators)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(requireContext(), "Pas de capteur biometrique disponible", Toast.LENGTH_LONG).show()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(requireContext(), "Aucune biometrie enregistree. Veuillez configurer dans les parametres.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(requireContext(), "Authentification biometrique non disponible", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun afficherPromptBiometrique(employee: Employee, methode: MethodeAuthentification, authenticators: Int) {
        val titre = if (methode == MethodeAuthentification.EMPREINTE) "Empreinte digitale" else "Reconnaissance faciale"
        val executor = ContextCompat.getMainExecutor(requireContext())

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.effectuerPointage(employee.id, methode)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(requireContext(), "Erreur: $errString", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(requireContext(), "Authentification echouee, reessayez", Toast.LENGTH_SHORT).show()
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titre)
            .setSubtitle("${employee.nom} ${employee.prenom}")
            .setDescription("Placez votre doigt sur le capteur pour pointer")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Annuler")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
