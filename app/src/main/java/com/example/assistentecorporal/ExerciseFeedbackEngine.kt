package com.example.assistentecorporal

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

class ExerciseFeedbackEngine(
    downThreshold: Int,
    upThreshold: Int
) {

    var downThreshold: Int = downThreshold
        private set

    var upThreshold: Int = upThreshold
        private set

    var repCount: Int = 0
        private set

    private var squatStage: TrackingStage = TrackingStage.READY

    fun updateDownThreshold(value: Int) {
        downThreshold = value.coerceIn(80, upThreshold - 10)
    }

    fun updateUpThreshold(value: Int) {
        upThreshold = value.coerceIn(downThreshold + 10, 180)
    }

    fun resetCounter() {
        repCount = 0
        squatStage = TrackingStage.READY
    }

    fun evaluate(pose: Pose, isFrontCamera: Boolean): AnalysisResult {
        val leftLandmarks = SideLandmarks.fromPose(pose, BodySide.LEFT)
        val rightLandmarks = SideLandmarks.fromPose(pose, BodySide.RIGHT)

        val selected = selectBestSide(leftLandmarks, rightLandmarks)
            ?: return AnalysisResult(
                poseDetected = false,
                repCount = repCount,
                stageLabel = UiStage.READY.label,
                feedback = "Mostra o corpo inteiro de lado à câmara.",
                visibleSideLabel = "Indefinido",
                depthPercent = 0,
                justCompletedRep = false
            )

        val kneeAngle = calculateAngle(selected.hip, selected.knee, selected.ankle).roundToInt()
        val visibleSideLabel = when (selected.side) {
            BodySide.LEFT -> if (isFrontCamera) "Esquerdo" else "Esquerdo"
            BodySide.RIGHT -> if (isFrontCamera) "Direito" else "Direito"
        }

        val depthPercent = calculateDepthPercent(kneeAngle)
        var justCompletedRep = false

        val uiStage: UiStage
        val feedback: String

        when {
            kneeAngle <= downThreshold -> {
                squatStage = TrackingStage.DOWN
                uiStage = UiStage.BOTTOM
                feedback = "Boa profundidade. Agora sobe para completar."
            }

            squatStage == TrackingStage.DOWN && kneeAngle >= upThreshold -> {
                squatStage = TrackingStage.READY
                repCount += 1
                justCompletedRep = true
                uiStage = UiStage.RISING
                feedback = "Repetição válida. Excelente controlo."
            }

            squatStage == TrackingStage.DOWN -> {
                uiStage = UiStage.RISING
                feedback = "Sobe para completar o agachamento."
            }

            kneeAngle < upThreshold -> {
                uiStage = UiStage.DESCENDING
                feedback = "Desce mais para atingir a profundidade alvo."
            }

            else -> {
                squatStage = TrackingStage.READY
                uiStage = UiStage.READY
                feedback = "Posição pronta. Inicia o agachamento de perfil."
            }
        }

        return AnalysisResult(
            poseDetected = true,
            repCount = repCount,
            stageLabel = uiStage.label,
            feedback = feedback,
            kneeAngle = kneeAngle,
            visibleSideLabel = visibleSideLabel,
            depthPercent = depthPercent,
            justCompletedRep = justCompletedRep
        )
    }

    private fun calculateDepthPercent(kneeAngle: Int): Int {
        return when {
            kneeAngle >= upThreshold -> 0
            kneeAngle <= downThreshold -> 100
            else -> {
                val range = (upThreshold - downThreshold).coerceAtLeast(1)
                (((upThreshold - kneeAngle).toFloat() / range.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
        }
    }

    private fun selectBestSide(left: SideLandmarks?, right: SideLandmarks?): SideLandmarks? {
        val candidates = listOfNotNull(left, right)
            .filter { it.averageConfidence >= MIN_CONFIDENCE }

        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { it.averageConfidence }
    }

    private fun calculateAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val radians = atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)
        var angle = abs(Math.toDegrees(radians.toDouble())).toFloat()
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    private data class SideLandmarks(
        val side: BodySide,
        val shoulder: LandmarkPoint,
        val hip: LandmarkPoint,
        val knee: LandmarkPoint,
        val ankle: LandmarkPoint,
        val averageConfidence: Float
    ) {
        companion object {
            fun fromPose(pose: Pose, side: BodySide): SideLandmarks? {
                val shoulder = pose.getPoseLandmark(side.shoulder) ?: return null
                val hip = pose.getPoseLandmark(side.hip) ?: return null
                val knee = pose.getPoseLandmark(side.knee) ?: return null
                val ankle = pose.getPoseLandmark(side.ankle) ?: return null

                val points = listOf(shoulder, hip, knee, ankle)
                val averageConfidence = points.map { it.inFrameLikelihood }.average().toFloat()

                return SideLandmarks(
                    side = side,
                    shoulder = LandmarkPoint(shoulder.position.x, shoulder.position.y),
                    hip = LandmarkPoint(hip.position.x, hip.position.y),
                    knee = LandmarkPoint(knee.position.x, knee.position.y),
                    ankle = LandmarkPoint(ankle.position.x, ankle.position.y),
                    averageConfidence = averageConfidence
                )
            }
        }
    }

    private data class LandmarkPoint(val x: Float, val y: Float)

    private enum class TrackingStage {
        READY,
        DOWN
    }

    private enum class UiStage(val label: String) {
        READY("Pronto"),
        DESCENDING("A descer"),
        BOTTOM("Em baixo"),
        RISING("A subir")
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.55f
    }
}

private enum class BodySide(
    val shoulder: Int,
    val hip: Int,
    val knee: Int,
    val ankle: Int
) {
    LEFT(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_ANKLE
    ),
    RIGHT(
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.RIGHT_HIP,
        PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_ANKLE
    )
}

data class AnalysisResult(
    val poseDetected: Boolean,
    val repCount: Int,
    val stageLabel: String,
    val feedback: String,
    val kneeAngle: Int? = null,
    val visibleSideLabel: String = "Indefinido",
    val depthPercent: Int = 0,
    val justCompletedRep: Boolean = false
)
