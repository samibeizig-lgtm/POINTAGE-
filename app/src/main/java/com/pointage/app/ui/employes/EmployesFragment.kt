package com.pointage.app.ui.employes

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pointage.app.R
import com.pointage.app.data.model.Employee
import com.pointage.app.databinding.FragmentEmployesBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EmployesFragment : Fragment() {

    private var _binding: FragmentEmployesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()

    // Sélecteur de fichier pour l'import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return@registerForActivityResult
            afficherDialogImport(json)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erreur lecture fichier: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmployesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = EmployeesAdapter(
            onConfigurerVisage = { employee -> naviguerVersEnregistrementVisage(employee) },
            onModifier = { employee -> afficherDialogModification(employee) },
            onSupprimer = { employee -> viewModel.supprimerEmployee(employee.id) }
        )
        binding.recyclerEmployes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEmployes.adapter = adapter

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            adapter.submitList(employees)
            binding.tvAucunEmploye.visibility = if (employees.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.exportJson.observe(viewLifecycleOwner) { json ->
            json ?: return@observe
            viewModel.clearExportJson()
            partagerFichierJson(json)
        }

        viewModel.importResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            viewModel.clearImportResult()
            val msg = if (result.success) "✓ ${result.message}" else "Erreur: ${result.message}"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }

        binding.fabAjouterEmployee.setOnClickListener { afficherDialogAjout() }

        binding.btnExporter.setOnClickListener {
            viewModel.exporterDonnees()
        }

        binding.btnImporter.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun partagerFichierJson(json: String) {
        try {
            val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileName = "pointage_nexstay_$date.json"
            val file = File(requireContext().cacheDir, fileName)
            file.writeText(json)

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Pointage NEXSTAY — $date")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Exporter vers..."))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erreur export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun afficherDialogImport(json: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Importer les donnees")
            .setMessage("Comment voulez-vous importer ?\n\n• Fusionner : ajoute les donnees sans effacer les existantes\n• Remplacer : efface toutes les donnees actuelles")
            .setPositiveButton("Fusionner") { _, _ ->
                viewModel.importerDonnees(json, remplacer = false)
            }
            .setNeutralButton("Remplacer") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Confirmation")
                    .setMessage("Toutes les donnees actuelles seront effacees. Continuer ?")
                    .setPositiveButton("Oui, remplacer") { _, _ ->
                        viewModel.importerDonnees(json, remplacer = true)
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun naviguerVersEnregistrementVisage(employee: Employee) {
        val action = EmployesFragmentDirections.actionNavEmployesToNavFaceEnrollment(employee.id)
        findNavController().navigate(action)
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
                    Toast.makeText(requireContext(), "Veuillez remplir les champs obligatoires", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun afficherDialogModification(employee: Employee) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ajouter_employee, null)
        dialogView.findViewById<EditText>(R.id.et_nom).setText(employee.nom)
        dialogView.findViewById<EditText>(R.id.et_prenom).setText(employee.prenom)
        dialogView.findViewById<EditText>(R.id.et_matricule).setText(employee.matricule)
        dialogView.findViewById<EditText>(R.id.et_poste).setText(employee.poste)
        AlertDialog.Builder(requireContext())
            .setTitle("Modifier l'employe")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val nom = dialogView.findViewById<EditText>(R.id.et_nom).text.toString().trim()
                val prenom = dialogView.findViewById<EditText>(R.id.et_prenom).text.toString().trim()
                val matricule = dialogView.findViewById<EditText>(R.id.et_matricule).text.toString().trim()
                val poste = dialogView.findViewById<EditText>(R.id.et_poste).text.toString().trim()
                if (nom.isNotEmpty() && prenom.isNotEmpty() && matricule.isNotEmpty()) {
                    viewModel.modifierEmployee(employee.copy(nom = nom, prenom = prenom, matricule = matricule, poste = poste))
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
