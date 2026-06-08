package com.pointage.app.ui.historique

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pointage.app.databinding.FragmentHistoriqueBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.text.SimpleDateFormat
import java.util.*

class HistoriqueFragment : Fragment() {

    private var _binding: FragmentHistoriqueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoriqueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PointageHistoriqueAdapter()
        binding.recyclerHistorique.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistorique.adapter = adapter

        val sdf = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH)
        binding.tvDateHistorique.text = sdf.format(Date())

        viewModel.getPointagesDuJour().observe(viewLifecycleOwner) { pointages ->
            adapter.submitList(pointages)
            binding.tvAucunPointage.visibility = if (pointages.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            adapter.setEmployees(employees)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
