package com.pointage.app.ui.employes

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pointage.app.R
import com.pointage.app.data.model.Employee
import com.pointage.app.databinding.FragmentEmployesBinding
import com.pointage.app.ui.viewmodel.PointageViewModel

class EmployesFragment : Fragment() {

    private var _binding: FragmentEmployesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmployesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = EmployeesAdapter(
            onConfigurerVisage = { employee -> naviguerVersEnregistrementVisage(employee) },
            onResetBiometrie = { employee -> viewModel.reinitialiserBiometrie(employee.id) }
        )
        binding.recyclerEmployes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEmployes.adapter = adapter

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            adapter.submitList(employees)
            binding.tvAucunEmploye.visibility = if (employees.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAjouterEmployee.setOnClickListener {
            afficherDialogAjout()
        }
    }

    private fun naviguerVersEnregistrementVisage(employee: Employee) {
        val action = EmployesFragmentDirections.actionNavEmployesToNavFaceEnrollment(employee.id)
        findNavController().navigate(action)
    }

    private fun lancerConfigurationBiometrie(employee: Employee, methode: String) {
        val authenticators = if (methode == "VISAGE") BIOMETRIC_WEAK else BIOMETRIC_STRONG
        val biometricManager = BiometricManager.from(requireContext())

        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(requireContext(),
                    "Aucune biometrie configuree sur cet appareil.\nAllez dans Parametres > Securite pour enregistrer votre empreinte.",
                    Toast.LENGTH_LONG).show()
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(requireContext(), "Cet appareil ne supporte pas la biometrie.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val titre = if (methode == "VISAGE") "Reconnaissance faciale" else "Empreinte digitale"
        val description = if (methode == "VISAGE")
            "Regardez la camera pour enregistrer ${employee.prenom} ${employee.nom}"
        else
            "Posez le doigt sur le capteur pour enregistrer ${employee.prenom} ${employee.nom}"

        val executor = ContextCompat.getMainExecutor(requireContext())
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.enregistrerBiometrie(employee.id, methode)
                Toast.makeText(requireContext(),
                    "Biometrie enregistree pour ${employee.prenom} ${employee.nom}",
                    Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(requireContext(), "Erreur: $errString", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                Toast.makeText(requireContext(), "Echec, reessayez", Toast.LENGTH_SHORT).show()
            }
        }

        BiometricPrompt(this, executor, callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enregistrer $titre")
                .setSubtitle("${employee.nom} ${employee.prenom}")
                .setDescription(description)
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("Annuler")
                .build()
        )
    }

    private fun afficherDialogAjout() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ajouter_employee, null)
        AlertDialog.Builder(requireContext())
            .setTitle("Ajouter un employe")
            .setView(dialogView)
            .setPositiveButton("Ajouter") { _, _ ->
                val nom = dialogView.findViewById<EditText>(R.id.et_nom).text.toString().trim()
                val prenom = dialogView.findViewById<EditText>(R.id.et_prenom).text.toString().trim()
                val matricule = dialogView.findViewById<EditText>(R.id.et_matricule).text.toString().trim()
                val poste = dialogView.findViewById<EditText>(R.id.et_poste).text.toString().trim()

                if (nom.isNotEmpty() && prenom.isNotEmpty() && matricule.isNotEmpty()) {
                    viewModel.ajouterEmployee(nom, prenom, matricule, poste)
                } else {
                    Toast.makeText(requireContext(), "Veuillez remplir tous les champs obligatoires", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
