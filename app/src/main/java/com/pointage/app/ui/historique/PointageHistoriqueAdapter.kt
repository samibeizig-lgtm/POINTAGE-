package com.pointage.app.ui.historique

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pointage.app.R
import com.pointage.app.data.model.Employee
import com.pointage.app.data.model.Pointage
import com.pointage.app.data.model.TypePointage
import com.pointage.app.databinding.ItemPointageBinding
import java.text.SimpleDateFormat
import java.util.*

class PointageHistoriqueAdapter : ListAdapter<Pointage, PointageHistoriqueAdapter.ViewHolder>(DiffCallback()) {

    private var employees: Map<Long, Employee> = emptyMap()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun setEmployees(list: List<Employee>) {
        employees = list.associateBy { it.id }
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemPointageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(pointage: Pointage) {
            val employee = employees[pointage.employeeId]
            binding.tvEmployeeNom.text = employee?.let { "${it.nom} ${it.prenom}" } ?: "Employe inconnu"
            binding.tvMatricule.text = employee?.matricule ?: ""
            binding.tvHeure.text = timeFormat.format(Date(pointage.timestamp))
            binding.tvType.text = if (pointage.type == TypePointage.ARRIVEE) "ARRIVEE" else "DEPART"
            val colorRes = if (pointage.type == TypePointage.ARRIVEE) R.color.arrivee_color else R.color.depart_color
            binding.tvType.setTextColor(ContextCompat.getColor(binding.root.context, colorRes))
            binding.tvMethode.text = pointage.methode.name
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPointageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<Pointage>() {
        override fun areItemsTheSame(oldItem: Pointage, newItem: Pointage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Pointage, newItem: Pointage) = oldItem == newItem
    }
}
