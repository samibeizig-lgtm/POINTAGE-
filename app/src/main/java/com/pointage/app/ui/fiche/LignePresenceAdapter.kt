package com.pointage.app.ui.fiche

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pointage.app.data.model.LignePresence
import com.pointage.app.databinding.ItemLignePresenceBinding
import java.text.SimpleDateFormat
import java.util.*

class LignePresenceAdapter(
    private val onModifierArrivee: ((ligne: LignePresence) -> Unit)? = null,
    private val onModifierDepart: ((ligne: LignePresence) -> Unit)? = null
) : ListAdapter<LignePresence, LignePresenceAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("EEE dd", Locale.FRENCH)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemLignePresenceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ligne: LignePresence) {
            binding.tvJour.text = dateFormat.format(Date(ligne.date))
            binding.tvArrivee.text = ligne.arrivee?.let { timeFormat.format(Date(it)) } ?: "--:--"
            binding.tvDepart.text = ligne.depart?.let { timeFormat.format(Date(it)) } ?: "--:--"
            if (ligne.dureeMinutes != null) {
                val h = ligne.dureeMinutes / 60
                val m = ligne.dureeMinutes % 60
                binding.tvDuree.text = "${h}h${String.format("%02d", m)}"
            } else {
                binding.tvDuree.text = "--"
            }

            if (ligne.arriveeId != null && onModifierArrivee != null) {
                binding.tvArrivee.setOnClickListener { onModifierArrivee.invoke(ligne) }
            } else {
                binding.tvArrivee.setOnClickListener(null)
            }

            if (ligne.departId != null && onModifierDepart != null) {
                binding.tvDepart.setOnClickListener { onModifierDepart.invoke(ligne) }
            } else {
                binding.tvDepart.setOnClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLignePresenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<LignePresence>() {
        override fun areItemsTheSame(oldItem: LignePresence, newItem: LignePresence) = oldItem.date == newItem.date
        override fun areContentsTheSame(oldItem: LignePresence, newItem: LignePresence) = oldItem == newItem
    }
}
