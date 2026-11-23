package com.example.data

import android.content.Context
import com.example.model.PalmRejectionMode

class CanvasPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "kanvasa_studio_prefs",
        Context.MODE_PRIVATE
    )

    fun getPalmRejectionMode(): PalmRejectionMode {
        val raw = prefs.getString(KEY_PALM_REJECTION_MODE, PalmRejectionMode.AUTO.name)
        return PalmRejectionMode.fromName(raw)
    }

    fun setPalmRejectionMode(mode: PalmRejectionMode) {
        prefs.edit().putString(KEY_PALM_REJECTION_MODE, mode.name).apply()
    }

    companion object {
        private const val KEY_PALM_REJECTION_MODE = "palm_rejection_mode"
    }
}
