package com.pointage.app.ui.fiche

import android.app.TimePickerDialog
import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pointage.app.data.model.Employee
import com.pointage.app.data.model.FichePresence
import com.pointage.app.data.model.LignePresence
import com.pointage.app.databinding.FragmentFichePresenceBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FichePresenceFragment : Fragment() {

    private var _binding: FragmentFichePresenceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()
    private var employeesList: List<Employee> = emptyList()
    private var ficheCourante: FichePresence? = null
    private var spinnersReady = false

    private val moisNoms = listOf(
        "Janvier", "Fevrier", "Mars", "Avril", "Mai", "Juin",
        "Juillet", "Aout", "Septembre", "Octobre", "Novembre", "Decembre"
    )
    private val annees = listOf("2026", "2027", "2028")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFichePresenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val moisAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, moisNoms)
        moisAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMois.adapter = moisAdapter
        val calMois = Calendar.getInstance().get(Calendar.MONTH)
        binding.spinnerMois.setSelection(calMois)

        val anneeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, annees)
        anneeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAnnee.adapter = anneeAdapter
        val calAnnee = Calendar.getInstance().get(Calendar.YEAR)
        val anneeIndex = annees.indexOf(calAnnee.toString()).coerceAtLeast(0)
        binding.spinnerAnnee.setSelection(anneeIndex)

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (spinnersReady) chargerFiche()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerMois.onItemSelectedListener = spinnerListener
        binding.spinnerAnnee.onItemSelectedListener = spinnerListener
        binding.spinnerEmployeFiche.onItemSelectedListener = spinnerListener

        viewModel.employees.observe(viewLifecycleOwner) { employees ->
            employeesList = employees
            val noms = employees.map { "${it.nom} ${it.prenom} (${it.matricule})" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noms)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerEmployeFiche.adapter = adapter
            spinnersReady = true
            chargerFiche()
        }

        val lignesAdapter = LignePresenceAdapter(
            onModifierArrivee = { ligne -> afficherTimePickerModification(ligne, isArrivee = true) },
            onModifierDepart = { ligne -> afficherTimePickerModification(ligne, isArrivee = false) }
        )
        binding.recyclerFiche.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFiche.adapter = lignesAdapter

        viewModel.fichePresence.observe(viewLifecycleOwner) { fiche ->
            fiche ?: return@observe
            ficheCourante = fiche
            binding.layoutFicheContent.visibility = View.VISIBLE
            binding.tvTitreEmployee.text = "${fiche.employee.nom} ${fiche.employee.prenom} — ${fiche.employee.matricule}"
            lignesAdapter.submitList(fiche.lignes)
            val totalMinutes = fiche.lignes.sumOf { it.dureeMinutes ?: 0L }
            val heures = totalMinutes / 60
            val minutes = totalMinutes % 60
            binding.tvTotalHeures.text = "Total : ${heures}h ${String.format("%02d", minutes)}min"
        }

        viewModel.pointageModifie.observe(viewLifecycleOwner) { modified ->
            modified ?: return@observe
            viewModel.clearPointageModifie()
            chargerFiche()
        }

        binding.btnTelechargerPdf.setOnClickListener {
            ficheCourante?.let { genererPdf(it) }
                ?: Toast.makeText(requireContext(), "Chargez d'abord la fiche", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chargerFiche() {
        val position = binding.spinnerEmployeFiche.selectedItemPosition
        if (employeesList.isEmpty() || position < 0 || position >= employeesList.size) return
        val mois = binding.spinnerMois.selectedItemPosition + 1
        val annee = annees[binding.spinnerAnnee.selectedItemPosition].toInt()
        viewModel.clearFichePresence()
        viewModel.chargerFichePresence(employeesList[position].id, mois, annee)
    }

    private fun afficherTimePickerModification(ligne: LignePresence, isArrivee: Boolean) {
        val pointageId = if (isArrivee) ligne.arriveeId else ligne.departId
        val currentTs = if (isArrivee) ligne.arrivee else ligne.depart
        if (pointageId == null || currentTs == null) return

        val cal = Calendar.getInstance().apply { timeInMillis = currentTs }
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val newCal = Calendar.getInstance().apply {
                timeInMillis = ligne.date
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            viewModel.modifierPointage(pointageId, newCal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun genererPdf(fiche: FichePresence) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val blue = Color.parseColor("#1976D2")
        val blueDark = Color.parseColor("#1565C0")
        val blueLight = Color.parseColor("#E3F2FD")

        val paintTitre = Paint().apply { color = blue; textSize = 22f; isFakeBoldText = true }
        val paintSousTitre = Paint().apply { color = blueDark; textSize = 11f }
        val paintHeader = Paint().apply { color = Color.WHITE; textSize = 10f; isFakeBoldText = true }
        val paintCell = Paint().apply { color = Color.parseColor("#333333"); textSize = 9f }
        val paintTotal = Paint().apply { color = blueDark; textSize = 11f; isFakeBoldText = true }
        val paintLine = Paint().apply { color = Color.parseColor("#BBDEFB"); strokeWidth = 1f }
        val paintBgHeader = Paint().apply { color = blue }
        val paintBgRow = Paint().apply { color = blueLight }
        val paintBgRowAlt = Paint().apply { color = Color.WHITE }

        var y = 38f

        canvas.drawText("NEXSTAY", 40f, y, paintTitre)
        y += 16f
        canvas.drawText("Systeme de Pointage", 40f, y, paintSousTitre)
        y += 12f

        canvas.drawLine(40f, y, 555f, y, Paint().apply { color = blue; strokeWidth = 1.5f })
        y += 14f

        val moisNom = moisNoms[fiche.mois - 1]
        val cal = Calendar.getInstance()
        cal.set(fiche.annee, fiche.mois - 1, 1)
        val nbJours = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
        cal.set(fiche.annee, fiche.mois - 1, 1)
        val dateDebut = sdfDate.format(cal.time)
        cal.set(fiche.annee, fiche.mois - 1, nbJours)
        val dateFin = sdfDate.format(cal.time)

        val paintInfo = Paint().apply { color = Color.parseColor("#555555"); textSize = 10f }
        canvas.drawText("Periode : du $dateDebut au $dateFin  ($moisNom ${fiche.annee})", 40f, y, paintInfo)
        y += 14f
        canvas.drawText("Employe : ${fiche.employee.nom} ${fiche.employee.prenom}     Matricule : ${fiche.employee.matricule}     Poste : ${fiche.employee.poste}", 40f, y, paintInfo.apply { isFakeBoldText = true })
        y += 16f

        val col0 = 40f; val col1 = 175f; val col2 = 305f; val col3 = 425f
        // Calculate row height to fit all days on one page
        // Available space: 842 - y (current) - 30 (footer) = ~742 - y
        val available = 842f - y - 30f
        val headerH = 20f
        val rowH = ((available - headerH) / nbJours).coerceIn(14f, 22f)

        canvas.drawRect(col0, y, 555f, y + headerH, paintBgHeader)
        val textY = y + headerH - 6f
        canvas.drawText("Jour", col0 + 4f, textY, paintHeader)
        canvas.drawText("Arrivee", col1 + 4f, textY, paintHeader)
        canvas.drawText("Depart", col2 + 4f, textY, paintHeader)
        canvas.drawText("Duree", col3 + 4f, textY, paintHeader)
        y += headerH

        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfJour = SimpleDateFormat("EEE dd", Locale.FRENCH)

        fiche.lignes.forEachIndexed { index, ligne ->
            val bg = if (index % 2 == 0) paintBgRow else paintBgRowAlt
            canvas.drawRect(col0, y, 555f, y + rowH, bg)
            canvas.drawLine(col0, y + rowH, 555f, y + rowH, paintLine.apply { strokeWidth = 0.4f })

            val cy = y + rowH - 4f
            canvas.drawText(sdfJour.format(Date(ligne.date)), col0 + 4f, cy, paintCell.apply { color = Color.parseColor("#333333") })
            canvas.drawText(ligne.arrivee?.let { sdfTime.format(Date(it)) } ?: "--:--", col1 + 4f, cy, paintCell.apply { color = Color.parseColor("#2E7D32") })
            canvas.drawText(ligne.depart?.let { sdfTime.format(Date(it)) } ?: "--:--", col2 + 4f, cy, paintCell.apply { color = Color.parseColor("#C62828") })
            if (ligne.dureeMinutes != null) {
                val h = ligne.dureeMinutes / 60; val m = ligne.dureeMinutes % 60
                canvas.drawText("${h}h${String.format("%02d", m)}", col3 + 4f, cy, paintCell.apply { color = Color.parseColor("#333333") })
            } else {
                canvas.drawText("--", col3 + 4f, cy, paintCell.apply { color = Color.parseColor("#999999") })
            }
            y += rowH
        }

        y += 8f
        canvas.drawLine(40f, y, 555f, y, Paint().apply { color = blue; strokeWidth = 1f })
        y += 14f
        val totalMinutes = fiche.lignes.sumOf { it.dureeMinutes ?: 0L }
        val h = totalMinutes / 60; val m = totalMinutes % 60
        canvas.drawText("Total heures travaillees : ${h}h ${String.format("%02d", m)}min", col0, y, paintTotal)

        document.finishPage(page)

        val nomFichier = "Fiche_${fiche.employee.matricule}_${moisNoms[fiche.mois - 1]}_${fiche.annee}.pdf"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, nomFichier)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { os ->
                        document.writeTo(os)
                    }
                    Toast.makeText(requireContext(), "PDF sauvegarde dans Telechargements:\n$nomFichier", Toast.LENGTH_LONG).show()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, nomFichier)
                FileOutputStream(file).use { document.writeTo(it) }
                Toast.makeText(requireContext(), "PDF sauvegarde:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erreur PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            document.close()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
