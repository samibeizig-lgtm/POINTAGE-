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
    private val isProcessing = AtomicBoolean(false)
    private val pointageFait = AtomicBoolean(false)
    private var lastRecognitionTime = 0L
    private var framesWithFace = 0
    private val RECOGNITION_COOLDOWN_MS = 2000L

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
            binding.root.postDelayed({ findNavController().popBackStack() }, 2500)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error ?: return@observe
            updateStatus(error, "#F44336")
            viewModel.clearError()
            binding.root.postDelayed({ pointageFait.set(false) }, 2000)
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
                        val now = System.currentTimeMillis()
                        if (!isProcessing.get() && now - lastRecognitionTime > RECOGNITION_COOLDOWN_MS) {
                            processImageForRecognition(imageProxy)
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
        isProcessing.set(true)
        val bitmap = imageProxy.toBitmap() ?: run { imageProxy.close(); isProcessing.set(false); return }
        val rotated = FaceRecognitionHelper.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        imageProxy.close()

        val inputImage = InputImage.fromBitmap(rotated, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    framesWithFace = 0
                    updateStatus("Approchez votre visage de la camera...", "#FFFFFF")
                } else {
                    framesWithFace++
                    updateStatus("Visage detecte — analyse...", "#FFD5C0")
                    if (!pointageFait.get()) {
                        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                        val embedding = FaceRecognitionHelper.extractEmbedding(rotated, face.boundingBox, face)
                        pointageFait.set(true)
                        viewModel.identifierEtPointerParVisage(embedding)
                    }
                    lastRecognitionTime = System.currentTimeMillis()
                }
                isProcessing.set(false)
            }
            .addOnFailureListener {
                updateStatus("Erreur detection: ${it.message}", "#F44336")
                isProcessing.set(false)
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
