package com.pointage.app.ui.pointage

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
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
    private var allEmployees: List<Employee> = emptyList()
    private var employeesAvecVisage: List<Employee> = emptyList()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPointageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            allEmployees = employees
            employeesAvecVisage = employees.filter { it.biometrieConfiguree }

            val noms = employees.map { "${it.nom} ${it.prenom} (${it.matricule})" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noms)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerEmployee.adapter = adapter

            binding.tvAucunBiometrie.visibility =
                if (employeesAvecVisage.isEmpty() && employees.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.pointageResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val typeStr = if (result.type == TypePointage.ARRIVEE) "ARRIVEE" else "DEPART"
            val heure = dateFormat.format(Date(result.timestamp))
            binding.tvResultat.text = "✓ $typeStr enregistre a $heure"
            binding.tvResultat.visibility = View.VISIBLE
            viewModel.clearPointageResult()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }

        binding.btnVisageCustom.setOnClickListener {
            findNavController().navigate(
                PointageFragmentDirections.actionNavPointageToNavFacePointage()
            )
        }

        binding.btnPointageManuel.setOnClickListener {
            val position = binding.spinnerEmployee.selectedItemPosition
            if (allEmployees.isEmpty() || position < 0 || position >= allEmployees.size) {
                Toast.makeText(requireContext(), "Selectionnez un employe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            afficherConfirmationManuelle(allEmployees[position])
        }

        val sdf = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH)
        binding.tvDate.text = sdf.format(Date())
    }

    private fun afficherConfirmationManuelle(employee: Employee) {
        val heure = dateFormat.format(Date())
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_pointage_manuel, null)
        dialogView.findViewById<TextView>(R.id.tv_dialog_employe).text =
            "${employee.nom} ${employee.prenom} — ${employee.matricule}"
        dialogView.findViewById<TextView>(R.id.tv_dialog_heure).text = "Heure : $heure"

        AlertDialog.Builder(requireContext())
            .setTitle("Pointage Manuel")
            .setView(dialogView)
            .setPositiveButton("Valider") { _, _ ->
                viewModel.effectuerPointage(employee.id, MethodeAuthentification.MANUEL)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
