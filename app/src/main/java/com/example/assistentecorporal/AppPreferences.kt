package com.example.assistentecorporal

import android.content.Context
import androidx.camera.core.CameraSelector

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isIntroSeen(): Boolean = prefs.getBoolean(KEY_INTRO_SEEN, false)

    fun setIntroSeen(seen: Boolean) {
        prefs.edit().putBoolean(KEY_INTRO_SEEN, seen).apply()
    }

    fun getDownThreshold(defaultValue: Int): Int = prefs.getInt(KEY_DOWN_THRESHOLD, defaultValue)

    fun setDownThreshold(value: Int) {
        prefs.edit().putInt(KEY_DOWN_THRESHOLD, value).apply()
    }

    fun getUpThreshold(defaultValue: Int): Int = prefs.getInt(KEY_UP_THRESHOLD, defaultValue)

    fun setUpThreshold(value: Int) {
        prefs.edit().putInt(KEY_UP_THRESHOLD, value).apply()
    }

    fun getLensFacing(defaultValue: Int = CameraSelector.LENS_FACING_BACK): Int {
        return prefs.getInt(KEY_LENS_FACING, defaultValue)
    }

    fun setLensFacing(value: Int) {
        prefs.edit().putInt(KEY_LENS_FACING, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "assistente_corporal_prefs"
        private const val KEY_INTRO_SEEN = "intro_seen"
        private const val KEY_DOWN_THRESHOLD = "down_threshold"
        private const val KEY_UP_THRESHOLD = "up_threshold"
        private const val KEY_LENS_FACING = "lens_facing"
    }
}
