package com.pointage.app.ui.employes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pointage.app.R
import com.pointage.app.data.model.Employee
import com.pointage.app.databinding.ItemEmployeeBinding

class EmployeesAdapter(
    private val onConfigurerVisage: (Employee) -> Unit,
    private val onResetBiometrie: (Employee) -> Unit
) : ListAdapter<Employee, EmployeesAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemEmployeeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(employee: Employee) {
            binding.tvNomPrenom.text = "${employee.nom} ${employee.prenom}"
            binding.tvMatricule.text = "Matricule: ${employee.matricule}"
            binding.tvPoste.text = employee.poste

            if (employee.biometrieConfiguree) {
                binding.tvBiometrieStatus.text = "Visage OK"
                binding.tvBiometrieStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.arrivee_color))
            } else {
                binding.tvBiometrieStatus.text = "Non configure"
                binding.tvBiometrieStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.depart_color))
            }

            binding.btnConfigurerVisage.setOnClickListener { onConfigurerVisage(employee) }
            binding.btnResetBiometrie.setOnClickListener { onResetBiometrie(employee) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmployeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<Employee>() {
        override fun areItemsTheSame(oldItem: Employee, newItem: Employee) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Employee, newItem: Employee) = oldItem == newItem
    }
}
