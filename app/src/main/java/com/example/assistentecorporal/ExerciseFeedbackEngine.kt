package com.example.assistentecorporal

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
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

    private var trackingStage: TrackingStage = TrackingStage.READY
    private var stableDownFrames: Int = 0
    private var stableValidFrames: Int = 0
    private var stableInvalidFrames: Int = 0
    private var stablePoseAccepted: Boolean = false

    private var fixedSide: BodySide? = null
    private var sideCandidate: BodySide? = null
    private var sideCandidateFrames: Int = 0

    private var filteredAngle: Float? = null
    private var filteredDepth: Float? = null
    private var lastStableResult: AnalysisResult? = null
    private var lastRepTimestampMs: Long = 0L
    private var standingHipBaselineY: Float? = null

    fun updateDownThreshold(value: Int) {
        downThreshold = value.coerceIn(80, upThreshold - 10)
    }

    fun updateUpThreshold(value: Int) {
        upThreshold = value.coerceIn(downThreshold + 10, 180)
    }

    fun resetCounter() {
        repCount = 0
        trackingStage = TrackingStage.READY
        stableDownFrames = 0
        filteredAngle = null
        filteredDepth = null
        lastStableResult = null
        lastRepTimestampMs = 0L
        standingHipBaselineY = null
        stableValidFrames = 0
        stableInvalidFrames = 0
        stablePoseAccepted = false
        fixedSide = null
        sideCandidate = null
        sideCandidateFrames = 0
    }

    fun evaluate(pose: Pose, isFrontCamera: Boolean, frameWidth: Int, frameHeight: Int): AnalysisResult {
        val left = SideLandmarks.fromPose(pose, BodySide.LEFT)
        val right = SideLandmarks.fromPose(pose, BodySide.RIGHT)

        val rawCandidate = selectBestValidatedSide(pose, left, right, frameWidth, frameHeight)

        if (rawCandidate == null) {
            stableInvalidFrames += 1
            stableValidFrames = 0

            if (stablePoseAccepted && stableInvalidFrames < INVALID_FRAMES_TO_STOP) {
                return lastStableResult?.copy(justCompletedRep = false) ?: invalidResult(
                    appStatus = STATUS_WAITING_HUMAN,
                    feedback = FEEDBACK_WAITING_HUMAN,
                    visibleSideLabel = fixedSide?.toLabel(isFrontCamera) ?: "Indefinido"
                )
            }

            stablePoseAccepted = false
            stableDownFrames = 0
            trackingStage = TrackingStage.READY
            filteredAngle = null
            filteredDepth = null
            fixedSide = null
            sideCandidate = null
            sideCandidateFrames = 0
            lastStableResult = null
            standingHipBaselineY = null

            return invalidResult(
                appStatus = STATUS_WAITING_HUMAN,
                feedback = FEEDBACK_WAITING_HUMAN,
                visibleSideLabel = "Indefinido"
            )
        }

        stableValidFrames += 1
        stableInvalidFrames = 0

        if (!stablePoseAccepted) {
            if (stableValidFrames < VALID_FRAMES_TO_START) {
                return invalidResult(
                    appStatus = STATUS_STABILIZING,
                    feedback = FEEDBACK_STABILIZING,
                    visibleSideLabel = rawCandidate.side.toLabel(isFrontCamera)
                )
            }
            stablePoseAccepted = true
            fixedSide = rawCandidate.side
        }

        val activeCandidate = resolveStableSide(rawCandidate, left, right) ?: rawCandidate
        val visibleSideLabel = activeCandidate.side.toLabel(isFrontCamera)

        val bodyHeight = distance(activeCandidate.shoulder, activeCandidate.ankle)
        updateStandingBaseline(activeCandidate.hip.y, bodyHeight)
        val hipDropPx = computeHipDrop(activeCandidate.hip.y)
        val downHipDropRequired = bodyHeight * MIN_HIP_DROP_DOWN_RATIO
        val descendHipDropRequired = bodyHeight * MIN_HIP_DROP_DESCENDING_RATIO

        val rawKneeAngle = calculateAngle(activeCandidate.hip, activeCandidate.knee, activeCandidate.ankle)
        val smoothedAngle = smoothAngle(rawKneeAngle)
        val kneeAngle = smoothedAngle.roundToInt().coerceIn(0, 180)

        val rawDepth = if (hipDropPx < descendHipDropRequired * 0.5f) {
            0f
        } else {
            calculateDepthPercent(kneeAngle).toFloat()
        }
        val smoothedDepth = smoothDepth(rawDepth)
        val depthPercent = smoothedDepth.roundToInt().coerceIn(0, 100)

        val now = System.currentTimeMillis()
        var justCompletedRep = false
        val uiStage: UiStage
        val feedback: String

        when {
            kneeAngle <= downThreshold && hipDropPx >= downHipDropRequired -> {
                stableDownFrames += 1
                if (stableDownFrames >= REQUIRED_STABLE_DOWN_FRAMES) {
                    trackingStage = TrackingStage.DOWN
                    uiStage = UiStage.BOTTOM
                    feedback = FEEDBACK_BOTTOM
                } else {
                    trackingStage = TrackingStage.DESCENDING
                    uiStage = UiStage.DESCENDING
                    feedback = FEEDBACK_DESCENDING
                }
            }

            trackingStage == TrackingStage.DOWN && kneeAngle >= upThreshold -> {
                if (now - lastRepTimestampMs >= MIN_REP_INTERVAL_MS) {
                    repCount += 1
                    justCompletedRep = true
                    lastRepTimestampMs = now
                }
                trackingStage = TrackingStage.READY
                stableDownFrames = 0
                uiStage = UiStage.RISING
                feedback = FEEDBACK_REP_COMPLETED
            }

            trackingStage == TrackingStage.DOWN -> {
                uiStage = UiStage.RISING
                feedback = FEEDBACK_RISING
            }

            kneeAngle < upThreshold && hipDropPx >= descendHipDropRequired -> {
                trackingStage = TrackingStage.DESCENDING
                stableDownFrames = 0
                uiStage = UiStage.DESCENDING
                feedback = FEEDBACK_DESCENDING
            }

            else -> {
                trackingStage = TrackingStage.READY
                stableDownFrames = 0
                uiStage = UiStage.READY
                feedback = FEEDBACK_READY
            }
        }

        val result = AnalysisResult(
            poseDetected = true,
            repCount = repCount,
            stageLabel = uiStage.label,
            feedback = feedback,
            appStatus = STATUS_HUMAN_DETECTED,
            kneeAngle = kneeAngle,
            visibleSideLabel = visibleSideLabel,
            depthPercent = depthPercent,
            justCompletedRep = justCompletedRep
        )

        lastStableResult = result.copy(justCompletedRep = false)
        return result
    }

    private fun invalidResult(
        appStatus: String,
        feedback: String,
        visibleSideLabel: String
    ): AnalysisResult {
        return AnalysisResult(
            poseDetected = false,
            repCount = repCount,
            stageLabel = UiStage.READY.label,
            feedback = feedback,
            appStatus = appStatus,
            kneeAngle = null,
            visibleSideLabel = visibleSideLabel,
            depthPercent = 0,
            justCompletedRep = false
        )
    }

    private fun resolveStableSide(
        rawCandidate: SideLandmarks,
        left: SideLandmarks?,
        right: SideLandmarks?
    ): SideLandmarks? {
        val currentStableSide = fixedSide
        if (currentStableSide == null) {
            fixedSide = rawCandidate.side
            sideCandidate = null
            sideCandidateFrames = 0
            return rawCandidate
        }

        if (rawCandidate.side == currentStableSide) {
            sideCandidate = null
            sideCandidateFrames = 0
            return getSideLandmarks(currentStableSide, left, right) ?: rawCandidate
        }

        if (sideCandidate == rawCandidate.side) {
            sideCandidateFrames += 1
        } else {
            sideCandidate = rawCandidate.side
            sideCandidateFrames = 1
        }

        if (sideCandidateFrames >= SIDE_SWITCH_FRAMES) {
            fixedSide = rawCandidate.side
            sideCandidate = null
            sideCandidateFrames = 0
            return rawCandidate
        }

        return getSideLandmarks(currentStableSide, left, right) ?: rawCandidate
    }

    private fun getSideLandmarks(
        side: BodySide,
        left: SideLandmarks?,
        right: SideLandmarks?
    ): SideLandmarks? {
        return when (side) {
            BodySide.LEFT -> left
            BodySide.RIGHT -> right
        }
    }

    private fun selectBestValidatedSide(
        pose: Pose,
        left: SideLandmarks?,
        right: SideLandmarks?,
        frameWidth: Int,
        frameHeight: Int
    ): SideLandmarks? {
        val candidates = listOfNotNull(left, right)
            .filter { isValidHumanSide(pose, it, frameWidth, frameHeight) }

        if (candidates.isEmpty()) return null

        val stable = fixedSide?.let { side -> candidates.firstOrNull { it.side == side } }
        if (stable != null) return stable

        return candidates.maxByOrNull { candidate -> candidateScore(pose, candidate) }
    }

    private fun candidateScore(pose: Pose, candidate: SideLandmarks): Float {
        val headScore = countHeadLandmarks(pose) * 0.1f
        val bodyScore = countGlobalConfidentLandmarks(pose) * 0.02f
        return candidate.averageConfidence + headScore + bodyScore
    }

    private fun isValidHumanSide(
        pose: Pose,
        side: SideLandmarks,
        frameWidth: Int,
        frameHeight: Int
    ): Boolean {
        if (side.averageConfidence < MIN_SIDE_CONFIDENCE) return false

        val headCount = countHeadLandmarks(pose)
        if (headCount < MIN_HEAD_LANDMARKS) return false

        val globalCount = countGlobalConfidentLandmarks(pose)
        if (globalCount < MIN_GLOBAL_CONFIDENT_LANDMARKS) return false

        val upperCount = countUpperBodyLandmarks(pose, side.side)
        if (upperCount < MIN_UPPER_BODY_LANDMARKS) return false

        val shoulderHip = distance(side.shoulder, side.hip)
        val hipKnee = distance(side.hip, side.knee)
        val kneeAnkle = distance(side.knee, side.ankle)
        val bodyHeight = distance(side.shoulder, side.ankle)
        val minSegmentPx = maxOf(frameHeight * MIN_SEGMENT_RATIO, MIN_SEGMENT_PX)

        if (shoulderHip < minSegmentPx) return false
        if (hipKnee < minSegmentPx) return false
        if (kneeAnkle < minSegmentPx) return false
        if (bodyHeight < maxOf(frameHeight * MIN_BODY_HEIGHT_RATIO, MIN_BODY_HEIGHT_PX)) return false

        if (!(side.shoulder.y < side.hip.y && side.hip.y < side.ankle.y)) return false

        val trunkToThighRatio = shoulderHip / hipKnee
        val thighToCalfRatio = hipKnee / kneeAnkle
        if (trunkToThighRatio !in MIN_SEGMENT_RATIO_HUMAN..MAX_SEGMENT_RATIO_HUMAN) return false
        if (thighToCalfRatio !in MIN_SEGMENT_RATIO_HUMAN..MAX_SEGMENT_RATIO_HUMAN) return false

        val xValues = listOf(side.shoulder.x, side.hip.x, side.knee.x, side.ankle.x)
        val yValues = listOf(side.shoulder.y, side.hip.y, side.knee.y, side.ankle.y)
        val bboxWidth = xValues.maxOrNull()!! - xValues.minOrNull()!!
        val bboxHeight = yValues.maxOrNull()!! - yValues.minOrNull()!!

        if (bboxHeight < maxOf(frameHeight * MIN_BBOX_HEIGHT_RATIO, MIN_BODY_HEIGHT_PX)) return false
        if (bboxWidth <= 0f) return false
        if ((bboxHeight / bboxWidth) < MIN_BODY_ASPECT_RATIO) return false

        return true
    }

    private fun countHeadLandmarks(pose: Pose): Int {
        val ids = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE,
            PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_EAR,
            PoseLandmark.RIGHT_EAR
        )
        return ids.count { id ->
            (pose.getPoseLandmark(id)?.inFrameLikelihood ?: 0f) >= MIN_HEAD_CONFIDENCE
        }
    }

    private fun countGlobalConfidentLandmarks(pose: Pose): Int {
        val ids = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )
        return ids.count { id ->
            (pose.getPoseLandmark(id)?.inFrameLikelihood ?: 0f) >= MIN_GLOBAL_CONFIDENCE
        }
    }

    private fun countUpperBodyLandmarks(pose: Pose, side: BodySide): Int {
        val ids = when (side) {
            BodySide.LEFT -> listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
            BodySide.RIGHT -> listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        }
        return ids.count { id ->
            (pose.getPoseLandmark(id)?.inFrameLikelihood ?: 0f) >= MIN_UPPER_BODY_CONFIDENCE
        }
    }


    private fun updateStandingBaseline(currentHipY: Float, bodyHeight: Float) {
        val currentBaseline = standingHipBaselineY
        val shouldRefreshBaseline = trackingStage == TrackingStage.READY &&
            (filteredAngle == null || filteredAngle!! >= upThreshold - BASELINE_REFRESH_MARGIN_DEGREES) &&
            bodyHeight > MIN_BODY_HEIGHT_PX

        if (!shouldRefreshBaseline) return

        standingHipBaselineY = if (currentBaseline == null) {
            currentHipY
        } else {
            BASELINE_ALPHA * currentHipY + (1f - BASELINE_ALPHA) * currentBaseline
        }
    }

    private fun computeHipDrop(currentHipY: Float): Float {
        val baseline = standingHipBaselineY ?: return 0f
        return (currentHipY - baseline).coerceAtLeast(0f)
    }

    private fun smoothAngle(rawAngle: Float): Float {
        val previous = filteredAngle
        val next = if (previous == null) rawAngle else {
            val blended = ANGLE_ALPHA * rawAngle + (1f - ANGLE_ALPHA) * previous
            if (abs(blended - previous) < ANGLE_DEAD_ZONE) previous else blended
        }
        filteredAngle = next
        return next
    }

    private fun smoothDepth(rawDepth: Float): Float {
        val previous = filteredDepth
        val next = if (previous == null) rawDepth else {
            val blended = DEPTH_ALPHA * rawDepth + (1f - DEPTH_ALPHA) * previous
            if (abs(blended - previous) < DEPTH_DEAD_ZONE) previous else blended
        }
        filteredDepth = next
        return next
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

    private fun calculateAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val radians = atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)
        var angle = abs(Math.toDegrees(radians.toDouble())).toFloat()
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float = hypot(a.x - b.x, a.y - b.y)

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
        DESCENDING,
        DOWN
    }

    private enum class UiStage(val label: String) {
        READY("Pronto"),
        DESCENDING("A descer"),
        BOTTOM("Em baixo"),
        RISING("A subir")
    }

    companion object {
        private const val MIN_SIDE_CONFIDENCE = 0.68f
        private const val MIN_HEAD_CONFIDENCE = 0.45f
        private const val MIN_GLOBAL_CONFIDENCE = 0.55f
        private const val MIN_UPPER_BODY_CONFIDENCE = 0.55f
        private const val MIN_HEAD_LANDMARKS = 1
        private const val MIN_GLOBAL_CONFIDENT_LANDMARKS = 8
        private const val MIN_UPPER_BODY_LANDMARKS = 2
        private const val MIN_SEGMENT_RATIO = 0.08f
        private const val MIN_SEGMENT_PX = 35f
        private const val MIN_BODY_HEIGHT_RATIO = 0.24f
        private const val MIN_BODY_HEIGHT_PX = 150f
        private const val MIN_BBOX_HEIGHT_RATIO = 0.22f
        private const val MIN_BODY_ASPECT_RATIO = 1.15f
        private const val MIN_SEGMENT_RATIO_HUMAN = 0.45f
        private const val MAX_SEGMENT_RATIO_HUMAN = 2.2f

        private const val VALID_FRAMES_TO_START = 3
        private const val INVALID_FRAMES_TO_STOP = 4
        private const val SIDE_SWITCH_FRAMES = 3
        private const val REQUIRED_STABLE_DOWN_FRAMES = 2
        private const val MIN_REP_INTERVAL_MS = 900L

        private const val ANGLE_ALPHA = 0.20f
        private const val DEPTH_ALPHA = 0.18f
        private const val ANGLE_DEAD_ZONE = 1.5f
        private const val DEPTH_DEAD_ZONE = 2f
        private const val BASELINE_ALPHA = 0.12f
        private const val BASELINE_REFRESH_MARGIN_DEGREES = 3
        private const val MIN_HIP_DROP_DESCENDING_RATIO = 0.04f
        private const val MIN_HIP_DROP_DOWN_RATIO = 0.08f

        private const val STATUS_WAITING_HUMAN = "Corpo humano não detetado."
        private const val STATUS_STABILIZING = "A estabilizar deteção humana."
        private const val STATUS_HUMAN_DETECTED = "Corpo humano válido detetado."

        private const val FEEDBACK_WAITING_HUMAN = "Mostra uma pessoa real com o corpo inteiro de perfil para iniciar a análise."
        private const val FEEDBACK_STABILIZING = "Mantém-te imóvel por um momento para estabilizar a análise."
        private const val FEEDBACK_READY = "Posição pronta. Inicia o agachamento de perfil."
        private const val FEEDBACK_DESCENDING = "Desce mais para atingir a profundidade alvo."
        private const val FEEDBACK_BOTTOM = "Boa profundidade. Agora sobe para completar."
        private const val FEEDBACK_RISING = "Sobe para completar o agachamento."
        private const val FEEDBACK_REP_COMPLETED = "Repetição válida. Excelente controlo."
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
    );

    fun toLabel(isFrontCamera: Boolean): String {
        return when (this) {
            LEFT -> if (isFrontCamera) "Esquerdo" else "Esquerdo"
            RIGHT -> if (isFrontCamera) "Direito" else "Direito"
        }
    }
}

data class AnalysisResult(
    val poseDetected: Boolean,
    val repCount: Int,
    val stageLabel: String,
    val feedback: String,
    val appStatus: String,
    val kneeAngle: Int? = null,
    val visibleSideLabel: String = "Indefinido",
    val depthPercent: Int = 0,
    val justCompletedRep: Boolean = false
)
