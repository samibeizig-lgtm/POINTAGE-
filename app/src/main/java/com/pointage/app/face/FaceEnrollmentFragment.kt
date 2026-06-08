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
import androidx.navigation.fragment.navArgs
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.pointage.app.databinding.FragmentFaceEnrollmentBinding
import com.pointage.app.ui.viewmodel.PointageViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceEnrollmentFragment : Fragment() {

    private var _binding: FragmentFaceEnrollmentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PointageViewModel by activityViewModels()
    private val args: FaceEnrollmentFragmentArgs by navArgs()

    private lateinit var cameraExecutor: ExecutorService
    private var isCapturing = false

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.20f)
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
        _binding = FragmentFaceEnrollmentBinding.inflate(inflater, container, false)
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

        binding.btnCapture.setOnClickListener {
            if (!isCapturing) {
                isCapturing = true
                binding.tvInstruction.text = "Capture en cours..."
                binding.btnCapture.isEnabled = false
            }
        }

        binding.btnAnnuler.setOnClickListener {
            findNavController().popBackStack()
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
                        if (isCapturing) processImageForEnrollment(imageProxy)
                        else imageProxy.close()
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
                    Toast.makeText(requireContext(), "Erreur camera: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageForEnrollment(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap() ?: run { imageProxy.close(); return }
        val rotated = FaceRecognitionHelper.rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        imageProxy.close()

        val inputImage = InputImage.fromBitmap(rotated, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    isCapturing = false
                    activity?.runOnUiThread {
                        binding.tvInstruction.text = "Aucun visage detecte. Appuyez sur Capturer."
                        binding.btnCapture.isEnabled = true
                    }
                    return@addOnSuccessListener
                }
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                val embeddingStr = if (FaceNetHelper.isAvailable()) {
                    FaceNetHelper.embeddingToString(FaceNetHelper.getEmbedding(rotated, face.boundingBox, face))
                } else {
                    val geom = GeometricFaceHelper.extractEmbedding(face)
                    if (geom == null) {
                        isCapturing = false
                        activity?.runOnUiThread {
                            binding.tvInstruction.text = "Visage pas assez visible. Rapprochez-vous."
                            binding.btnCapture.isEnabled = true
                        }
                        return@addOnSuccessListener
                    }
                    GeometricFaceHelper.embeddingToString(geom)
                }
                viewModel.enregistrerVisage(args.employeeId, embeddingStr)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Visage enregistre avec succes!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener {
                isCapturing = false
                activity?.runOnUiThread {
                    binding.tvInstruction.text = "Erreur, reessayez."
                    binding.btnCapture.isEnabled = true
                }
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
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) { null }
    }
}
