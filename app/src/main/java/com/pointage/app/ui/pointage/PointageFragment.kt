package com.pointage.app.ui.pointage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.pointage.app.data.model.Employee
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
            employeesList = employees.filter { it.biometrieConfiguree }
            val noms = employeesList.map { "${it.nom} ${it.prenom} (${it.matricule})" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noms)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerEmployee.adapter = adapter
            binding.tvAucunBiometrie.visibility =
                if (employeesList.isEmpty() && employees.isNotEmpty()) View.VISIBLE else View.GONE
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

        val sdf = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH)
        binding.tvDate.text = sdf.format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
