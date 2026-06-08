package com.pointage.app.ui.fiche

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pointage.app.data.model.Employee
import com.pointage.app.databinding.FragmentFichePresenceBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.util.*

class FichePresenceFragment : Fragment() {

    private var _binding: FragmentFichePresenceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()
    private var employeesList: List<Employee> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFichePresenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cal = Calendar.getInstance()
        binding.etMois.setText(String.format("%02d", cal.get(Calendar.MONTH) + 1))
        binding.etAnnee.setText(cal.get(Calendar.YEAR).toString())

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            employeesList = employees
            val noms = employees.map { "${it.nom} ${it.prenom} (${it.matricule})" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noms)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerEmployeeFiche.adapter = adapter
        }

        val lignesAdapter = LignePresenceAdapter()
        binding.recyclerFiche.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFiche.adapter = lignesAdapter

        viewModel.fichePresence.observe(viewLifecycleOwner) { fiche ->
            fiche ?: return@observe
            binding.tvTitreEmployee.text = "${fiche.employee.nom} ${fiche.employee.prenom} - ${fiche.employee.poste}"
            lignesAdapter.submitList(fiche.lignes)
            val totalMinutes = fiche.lignes.sumOf { it.dureeMinutes ?: 0L }
            val heures = totalMinutes / 60
            val minutes = totalMinutes % 60
            binding.tvTotalHeures.text = "Total: ${heures}h ${minutes}min"
        }

        binding.btnChargerFiche.setOnClickListener {
            val position = binding.spinnerEmployeeFiche.selectedItemPosition
            if (employeesList.isEmpty() || position < 0) {
                Toast.makeText(requireContext(), "Selectionnez un employe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mois = binding.etMois.text.toString().toIntOrNull()
            val annee = binding.etAnnee.text.toString().toIntOrNull()
            if (mois == null || mois !in 1..12 || annee == null) {
                Toast.makeText(requireContext(), "Mois/Annee invalide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.chargerFichePresence(employeesList[position].id, mois, annee)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
