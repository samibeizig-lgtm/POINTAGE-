package com.pointage.app.face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.pointage.app.data.model.TypePointage
import com.pointage.app.databinding.FragmentFacePointageBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FacePointageFragment : Fragment() {

    private var _binding: FragmentFacePointageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()

    private lateinit var cameraExecutor: ExecutorService

    // true = analyse en cours ou pointage réussi (caméra ne doit plus traiter)
    private val processing = AtomicBoolean(false)
    // true = pointage enregistré, on attend la navigation retour
    private val done = AtomicBoolean(false)

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else {
            Toast.makeText(requireContext(), "Permission camera requise", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFacePointageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        viewModel.facePointageResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val typeStr = if (result.second == TypePointage.ARRIVEE) "ARRIVEE" else "DEPART"
            updateStatus("✓ ${result.first}  —  $typeStr", "#4CAF50")
            viewModel.clearFacePointageResult()
            done.set(true)
            binding.root.postDelayed({ findNavController().popBackStack() }, 2500)
        }

        viewModel.faceNoMatch.observe(viewLifecycleOwner) { score ->
            score ?: return@observe
            processing.set(false)
            val scoreStr = String.format("%.2f", score)
            if (score == 0f) {
                updateStatus("Aucun visage enregistre dans la base", "#FF8800")
            } else {
                updateStatus("Non reconnu (score: $scoreStr / seuil: 0.50)", "#FF8800")
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            updateStatus(error, "#F44336")
            // erreur technique : on reprend le scan après un court délai
            binding.root.postDelayed({ processing.set(false) }, 1500)
            viewModel.clearError()
        }

        binding.btnRetour.setOnClickListener { findNavController().popBackStack() }
    }

    private fun updateStatus(msg: String, colorHex: String) {
        activity?.runOnUiThread {
            binding.tvStatut.text = msg
            binding.tvStatut.setTextColor(android.graphics.Color.parseColor(colorHex))
            binding.tvStatut.visibility = View.VISIBLE
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (done.get() || processing.compareAndSet(false, true)) {
                            if (done.get()) {
                                imageProxy.close()
                            } else {
                                processImageForRecognition(imageProxy)
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) {
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                } catch (ex: Exception) {
                    updateStatus("Erreur camera: ${ex.message}", "#F44336")
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageForRecognition(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        if (bitmap == null) {
            imageProxy.close()
            processing.set(false)
            return
        }
        val rotated = FaceRecognitionHelper.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        imageProxy.close()

        val inputImage = InputImage.fromBitmap(rotated, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    updateStatus("Approchez votre visage de la camera...", "#FFFFFF")
                    processing.set(false)
                } else {
                    updateStatus("Visage detecte — analyse...", "#FFD5C0")
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                    val embedding = FaceRecognitionHelper.extractEmbedding(rotated, face.boundingBox, face)
                    // processing reste à true jusqu'au retour du ViewModel
                    viewModel.identifierEtPointerParVisage(embedding)
                }
            }
            .addOnFailureListener {
                updateStatus("Erreur detection: ${it.message}", "#F44336")
                binding.root.post { processing.set(false) }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        faceDetector.close()
        _binding = null
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 92, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) { null }
    }
}
