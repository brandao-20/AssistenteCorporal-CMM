package com.example.assistentecorporal

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetector
import java.util.concurrent.atomic.AtomicBoolean

class PoseAnalyzer(
    private val poseDetector: PoseDetector,
    private val feedbackEngine: ExerciseFeedbackEngine,
    private val lensFacingProvider: () -> Int,
    private val onResult: (AnalysisResult) -> Unit,
    private val onFailure: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val isFrontCamera = lensFacingProvider() == CameraSelector.LENS_FACING_FRONT

        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                onResult(feedbackEngine.evaluate(pose, isFrontCamera))
            }
            .addOnFailureListener { exception ->
                onFailure(exception.message ?: "Falha ao analisar a pose.")
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }
}
