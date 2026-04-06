package com.example.assistentecorporal

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.assistentecorporal.databinding.ActivityAnalysisBinding
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var orientationHelper: DeviceOrientationHelper
    private lateinit var feedbackEngine: ExerciseFeedbackEngine
    private lateinit var poseDetector: PoseDetector
    private lateinit var appPreferences: AppPreferences

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var latestOrientationResult: OrientationResult? = null
    private var latestAnalysisResult: AnalysisResult? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            binding.tvAppStatus.text = getString(R.string.camera_permission_required)
            updateStateChip(
                stateText = getString(R.string.state_permission_needed),
                backgroundRes = R.drawable.bg_state_error,
                textColorRes = R.color.error
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences(this)
        lensFacing = appPreferences.getLensFacing(DEFAULT_LENS_FACING)

        feedbackEngine = ExerciseFeedbackEngine(
            downThreshold = appPreferences.getDownThreshold(DEFAULT_DOWN_THRESHOLD),
            upThreshold = appPreferences.getUpThreshold(DEFAULT_UP_THRESHOLD)
        )

        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        cameraExecutor = Executors.newSingleThreadExecutor()

        orientationHelper = DeviceOrientationHelper(this) { result ->
            latestOrientationResult = result
            renderOrientation(result)
            refreshUiState()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.btnInfo.setOnClickListener { showInfoDialog() }

        binding.btnPresetPermissive.setOnClickListener { applyPreset(ThresholdPreset.PERMISSIVE) }
        binding.btnPresetNormal.setOnClickListener { applyPreset(ThresholdPreset.NORMAL) }
        binding.btnPresetStrict.setOnClickListener { applyPreset(ThresholdPreset.STRICT) }

        binding.btnDownMinus.setOnClickListener {
            feedbackEngine.updateDownThreshold(feedbackEngine.downThreshold - 5)
            persistThresholds()
            syncThresholdViews()
        }
        binding.btnDownPlus.setOnClickListener {
            feedbackEngine.updateDownThreshold(feedbackEngine.downThreshold + 5)
            persistThresholds()
            syncThresholdViews()
        }
        binding.btnUpMinus.setOnClickListener {
            feedbackEngine.updateUpThreshold(feedbackEngine.upThreshold - 5)
            persistThresholds()
            syncThresholdViews()
        }
        binding.btnUpPlus.setOnClickListener {
            feedbackEngine.updateUpThreshold(feedbackEngine.upThreshold + 5)
            persistThresholds()
            syncThresholdViews()
        }
        binding.btnResetCounter.setOnClickListener {
            feedbackEngine.resetCounter()
            latestAnalysisResult = null
            renderBaselineValues()
            refreshUiState()
        }

        updateCameraButtonLabel()
        syncThresholdViews()
        renderBaselineValues()
        refreshUiState()
    }

    override fun onResume() {
        super.onResume()
        orientationHelper.register()
        ensureCameraPermissionAndStart()
    }

    override fun onPause() {
        super.onPause()
        orientationHelper.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }

    private fun renderBaselineValues() {
        binding.tvAppStatus.text = getString(R.string.analysis_idle)
        binding.tvFeedback.text = getString(R.string.feedback_ready)
        binding.tvOrientation.text = getString(R.string.orientation_initial)
        binding.tvVisibleSide.text = getString(R.string.visible_side_initial)
        binding.tvStageChip.text = getString(R.string.stage_ready)
        binding.tvAngleValue.text = getString(R.string.value_unknown)
        binding.tvCounter.text = getString(R.string.counter_value, feedbackEngine.repCount)
        binding.tvDepthValue.text = getString(R.string.depth_value, 0)
        binding.progressDepth.progress = 0
        applyStageChipStyle(label = getString(R.string.stage_ready), highlight = false)
    }

    private fun ensureCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor,
                        PoseAnalyzer(
                            poseDetector = poseDetector,
                            feedbackEngine = feedbackEngine,
                            lensFacingProvider = { lensFacing },
                            onResult = { result -> runOnUiThread { renderAnalysis(result) } },
                            onFailure = { message -> runOnUiThread { binding.tvAppStatus.text = message } }
                        )
                    )
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                binding.tvAppStatus.text = getString(R.string.analysis_running)
                refreshUiState()
            } catch (_: Exception) {
                binding.tvAppStatus.text = getString(R.string.camera_start_error)
                Toast.makeText(this, getString(R.string.camera_start_error), Toast.LENGTH_SHORT).show()
                updateStateChip(
                    stateText = getString(R.string.state_camera_error),
                    backgroundRes = R.drawable.bg_state_error,
                    textColorRes = R.color.error
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        appPreferences.setLensFacing(lensFacing)
        updateCameraButtonLabel()
        startCamera()
    }

    private fun updateCameraButtonLabel() {
        binding.btnSwitchCamera.text = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            getString(R.string.switch_to_front)
        } else {
            getString(R.string.switch_to_back)
        }
    }

    private fun syncThresholdViews() {
        binding.tvDownThreshold.text = getString(R.string.threshold_value, feedbackEngine.downThreshold)
        binding.tvUpThreshold.text = getString(R.string.threshold_value, feedbackEngine.upThreshold)
        updatePresetButtons()
    }

    private fun persistThresholds() {
        appPreferences.setDownThreshold(feedbackEngine.downThreshold)
        appPreferences.setUpThreshold(feedbackEngine.upThreshold)
    }

    private fun applyPreset(preset: ThresholdPreset) {
        feedbackEngine.updateDownThreshold(preset.down)
        feedbackEngine.updateUpThreshold(preset.up)
        persistThresholds()
        syncThresholdViews()
    }

    private fun updatePresetButtons() {
        val activePreset = ThresholdPreset.values().firstOrNull {
            it.down == feedbackEngine.downThreshold && it.up == feedbackEngine.upThreshold
        }

        stylePresetButton(binding.btnPresetPermissive, activePreset == ThresholdPreset.PERMISSIVE)
        stylePresetButton(binding.btnPresetNormal, activePreset == ThresholdPreset.NORMAL)
        stylePresetButton(binding.btnPresetStrict, activePreset == ThresholdPreset.STRICT)

        binding.tvPresetStatus.text = activePreset?.let { getString(it.labelRes) }
            ?: getString(R.string.manual_adjustment)
    }

    private fun stylePresetButton(button: Button, selected: Boolean) {
        if (selected) {
            button.setBackgroundResource(R.drawable.bg_primary_button)
            button.setTextColor(ContextCompat.getColor(this, R.color.surface))
        } else {
            button.setBackgroundResource(R.drawable.bg_secondary_button)
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun renderOrientation(result: OrientationResult) {
        binding.tvOrientation.text = if (!result.available) {
            getString(R.string.orientation_unavailable)
        } else {
            getString(R.string.orientation_value, result.rollDegrees, result.message)
        }

        val colorRes = when {
            !result.available -> R.color.text_secondary
            result.isAcceptable -> R.color.success
            else -> R.color.warning
        }
        binding.tvOrientation.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun renderAnalysis(result: AnalysisResult) {
        latestAnalysisResult = result

        binding.tvCounter.text = getString(R.string.counter_value, result.repCount)
        binding.tvVisibleSide.text = getString(R.string.visible_side_value, result.visibleSideLabel)
        binding.tvFeedback.text = result.feedback
        binding.tvAngleValue.text = result.kneeAngle?.let { getString(R.string.angle_value_only, it) }
            ?: getString(R.string.value_unknown)
        binding.tvDepthValue.text = getString(R.string.depth_value, result.depthPercent)
        binding.progressDepth.progress = result.depthPercent
        applyStageChipStyle(result.stageLabel, result.justCompletedRep)

        binding.tvAppStatus.text = result.appStatus

        if (result.justCompletedRep) {
            celebrateCompletedRep()
        }

        refreshUiState()
    }

    private fun refreshUiState() {
        val orientation = latestOrientationResult
        val result = latestAnalysisResult

        when {
            result?.justCompletedRep == true -> {
                updateStateChip(
                    stateText = getString(R.string.state_rep_completed),
                    backgroundRes = R.drawable.bg_state_success,
                    textColorRes = R.color.success
                )
            }

            orientation?.available == true && !orientation.isAcceptable -> {
                updateStateChip(
                    stateText = getString(R.string.state_phone_tilted),
                    backgroundRes = R.drawable.bg_state_warning,
                    textColorRes = R.color.warning
                )
            }

            result?.poseDetected == true -> {
                val stateText = when (result.stageLabel) {
                    getString(R.string.stage_ready) -> getString(R.string.state_ready)
                    getString(R.string.stage_descending) -> getString(R.string.state_descending)
                    getString(R.string.stage_bottom) -> getString(R.string.state_bottom)
                    getString(R.string.stage_rising) -> getString(R.string.state_rising)
                    else -> getString(R.string.state_ready)
                }
                updateStateChip(
                    stateText = stateText,
                    backgroundRes = R.drawable.bg_state_success,
                    textColorRes = R.color.success
                )
            }

            result?.poseDetected == false -> {
                val stateText = when {
                    result.appStatus.contains("estabilizar", ignoreCase = true) -> getString(R.string.state_stabilizing)
                    result.appStatus.contains("humano", ignoreCase = true) -> getString(R.string.state_waiting_human)
                    else -> getString(R.string.state_body_incomplete)
                }
                updateStateChip(
                    stateText = stateText,
                    backgroundRes = R.drawable.bg_state_error,
                    textColorRes = R.color.error
                )
            }

            else -> {
                updateStateChip(
                    stateText = getString(R.string.state_waiting),
                    backgroundRes = R.drawable.bg_state_neutral,
                    textColorRes = R.color.primary_dark
                )
            }
        }
    }

    private fun updateStateChip(stateText: String, backgroundRes: Int, textColorRes: Int) {
        binding.tvStatusChip.text = stateText
        binding.tvStatusChip.setBackgroundResource(backgroundRes)
        binding.tvStatusChip.setTextColor(ContextCompat.getColor(this, textColorRes))
    }

    private fun applyStageChipStyle(label: String, highlight: Boolean) {
        binding.tvStageChip.text = label

        val (backgroundRes, textColorRes) = when {
            highlight -> R.drawable.bg_state_success to R.color.success
            label == getString(R.string.stage_bottom) -> R.drawable.bg_state_success to R.color.success
            label == getString(R.string.stage_descending) || label == getString(R.string.stage_rising) -> {
                R.drawable.bg_state_warning to R.color.warning
            }
            else -> R.drawable.bg_state_neutral to R.color.primary_dark
        }

        binding.tvStageChip.setBackgroundResource(backgroundRes)
        binding.tvStageChip.setTextColor(ContextCompat.getColor(this, textColorRes))
    }

    private fun celebrateCompletedRep() {
        vibrateOnce()

        binding.tvCounter.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(110)
            .withEndAction {
                binding.tvCounter.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140)
                    .start()
            }
            .start()

        val startColor = ContextCompat.getColor(this, R.color.text_primary)
        val endColor = ContextCompat.getColor(this, R.color.success)
        ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor, startColor).apply {
            duration = 550
            addUpdateListener { animator ->
                binding.tvCounter.setTextColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun vibrateOnce() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(60)
        }
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.info_dialog_title)
            .setMessage(getString(R.string.info_dialog_body))
            .setPositiveButton(R.string.close_label, null)
            .setNeutralButton(R.string.review_intro) { _, _ ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_FORCE_SHOW_INTRO, true)
                )
            }
            .show()
    }

    companion object {
        private const val DEFAULT_DOWN_THRESHOLD = 110
        private const val DEFAULT_UP_THRESHOLD = 160
        private const val DEFAULT_LENS_FACING = CameraSelector.LENS_FACING_BACK
    }
}

private enum class ThresholdPreset(
    val down: Int,
    val up: Int,
    val labelRes: Int
) {
    PERMISSIVE(120, 155, R.string.preset_status_permissive),
    NORMAL(110, 160, R.string.preset_status_normal),
    STRICT(100, 165, R.string.preset_status_strict);

}
