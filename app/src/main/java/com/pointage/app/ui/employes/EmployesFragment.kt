package com.pointage.app.ui.employes

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pointage.app.R
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

        val adapter = EmployeesAdapter { employee ->
            viewModel.selectionnerEmployee(employee)
        }
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
