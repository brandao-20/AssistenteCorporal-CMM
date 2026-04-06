package com.example.assistentecorporal

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.assistentecorporal.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appPreferences: AppPreferences
    private var currentSlide = 0
    private val totalSlides = 3
    private var forceShowIntro = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences(this)

        forceShowIntro = intent.getBooleanExtra(EXTRA_FORCE_SHOW_INTRO, false)
        if (appPreferences.isIntroSeen() && !forceShowIntro) {
            binding.btnSkip.text = getString(R.string.open_analysis_direct)
        }

        updateSlideUi()

        binding.btnPrevious.setOnClickListener {
            if (currentSlide > 0) {
                currentSlide--
                binding.viewFlipperIntro.showPrevious()
                updateSlideUi()
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentSlide < totalSlides - 1) {
                currentSlide++
                binding.viewFlipperIntro.showNext()
                updateSlideUi()
            } else {
                openAnalysis(markIntroSeen = true)
            }
        }

        binding.btnSkip.setOnClickListener {
            openAnalysis(markIntroSeen = true)
        }
    }

    private fun openAnalysis(markIntroSeen: Boolean) {
        if (markIntroSeen) {
            appPreferences.setIntroSeen(true)
        }
        startActivity(Intent(this, AnalysisActivity::class.java))
    }

    private fun updateSlideUi() {
        binding.tvSlideIndicator.text = getString(
            R.string.slide_indicator,
            currentSlide + 1,
            totalSlides
        )

        binding.btnPrevious.isEnabled = currentSlide > 0
        binding.btnPrevious.alpha = if (currentSlide > 0) 1f else 0.55f

        binding.btnNext.text = if (currentSlide == totalSlides - 1) {
            getString(R.string.start_analysis)
        } else {
            getString(R.string.next)
        }

        binding.btnSkip.visibility = if (currentSlide == totalSlides - 1) {
            View.GONE
        } else {
            View.VISIBLE
        }

        if (binding.btnSkip.visibility == View.VISIBLE) {
            binding.btnSkip.text = if (appPreferences.isIntroSeen() && !forceShowIntro) {
                getString(R.string.open_analysis_direct)
            } else {
                getString(R.string.skip_intro)
            }
        }
    }

    companion object {
        const val EXTRA_FORCE_SHOW_INTRO = "force_show_intro"
    }
}
