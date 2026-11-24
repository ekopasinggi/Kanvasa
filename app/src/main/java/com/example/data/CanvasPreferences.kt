package com.example.data

import android.content.Context
import com.example.model.PalmRejectionMode
import com.example.model.StylusCustomBinding

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

    fun getStylusCustomBinding(): StylusCustomBinding {
        return StylusCustomBinding(
            keyCode = prefs.getInt(KEY_STYLUS_CUSTOM_KEYCODE, 0),
            scanCode = prefs.getInt(KEY_STYLUS_CUSTOM_SCANCODE, 0),
            motionButton = prefs.getInt(KEY_STYLUS_CUSTOM_BUTTON, 0),
            label = prefs.getString(KEY_STYLUS_CUSTOM_LABEL, "") ?: ""
        )
    }

    fun setStylusCustomBinding(binding: StylusCustomBinding) {
        prefs.edit()
            .putInt(KEY_STYLUS_CUSTOM_KEYCODE, binding.keyCode)
            .putInt(KEY_STYLUS_CUSTOM_SCANCODE, binding.scanCode)
            .putInt(KEY_STYLUS_CUSTOM_BUTTON, binding.motionButton)
            .putString(KEY_STYLUS_CUSTOM_LABEL, binding.label)
            .apply()
    }

    fun clearStylusCustomBinding() {
        prefs.edit()
            .remove(KEY_STYLUS_CUSTOM_KEYCODE)
            .remove(KEY_STYLUS_CUSTOM_SCANCODE)
            .remove(KEY_STYLUS_CUSTOM_BUTTON)
            .remove(KEY_STYLUS_CUSTOM_LABEL)
            .apply()
    }

    companion object {
        private const val KEY_PALM_REJECTION_MODE = "palm_rejection_mode"
        private const val KEY_STYLUS_CUSTOM_KEYCODE = "stylus_custom_keycode"
        private const val KEY_STYLUS_CUSTOM_SCANCODE = "stylus_custom_scancode"
        private const val KEY_STYLUS_CUSTOM_BUTTON = "stylus_custom_button"
        private const val KEY_STYLUS_CUSTOM_LABEL = "stylus_custom_label"
    }
}

