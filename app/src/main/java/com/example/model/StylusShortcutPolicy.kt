package com.example.model

/**
 * Persisted custom hardware shortcut binding captured via the Stylus Calibration dialog
 * for OEM tablets/styluses (e.g. Huawei MatePad M-Pencil) that emit non-standard key or scan codes.
 */
data class StylusCustomBinding(
    val keyCode: Int = 0,
    val scanCode: Int = 0,
    val motionButton: Int = 0,
    val label: String = ""
) {
    val isConfigured: Boolean
        get() = keyCode > 0 || scanCode > 0 || motionButton > 0
}

/**
 * Snapshot of a hardware stylus key or motion button event intercepted by the Activity.
 */
data class StylusHardwareSignal(
    val keyCode: Int = 0,
    val scanCode: Int = 0,
    val motionButton: Int = 0,
    val source: Int = 0,
    val label: String = ""
) {
    fun toCustomBinding(): StylusCustomBinding = StylusCustomBinding(
        keyCode = keyCode,
        scanCode = scanCode,
        motionButton = motionButton,
        label = label
    )
}

/**
 * Pure domain logic for stylus hardware shortcuts (double-tap toggle & hold-to-erase).
 * Kept free of Android framework runtime dependencies so all rules are deterministically unit-tested.
 */
object StylusShortcutPolicy {
    // Standard Android API 34+ Stylus KeyCodes
    const val KEYCODE_STYLUS_BUTTON_PRIMARY = 308
    const val KEYCODE_STYLUS_BUTTON_SECONDARY = 309
    const val KEYCODE_STYLUS_BUTTON_TERTIARY = 310
    const val KEYCODE_STYLUS_BUTTON_TAIL = 311

    // Standard Android Function KeyCodes sometimes emitted by Bluetooth/NearLink styluses
    const val KEYCODE_F1 = 131
    const val KEYCODE_F12 = 142
    const val KEYCODE_F13 = 326
    const val KEYCODE_F24 = 337

    // Standard Android MotionEvent stylus button bitmasks
    const val BUTTON_STYLUS_PRIMARY = 32
    const val BUTTON_STYLUS_SECONDARY = 64

    // Linux HID evdev scan codes for stylus buttons & eraser switch
    const val SCAN_BTN_TOOL_RUBBER = 321 // 0x141
    const val SCAN_BTN_STYLUS3 = 329     // 0x149
    const val SCAN_BTN_STYLUS = 331      // 0x14b
    const val SCAN_BTN_STYLUS2 = 332     // 0x14c

    // Standard Android InputDevice stylus source flags
    const val SOURCE_STYLUS = 0x4002
    const val SOURCE_BLUETOOTH_STYLUS = 0xC002

    const val TOGGLE_DEBOUNCE_MS = 220L

    private val STANDARD_STYLUS_SCAN_CODES = setOf(
        SCAN_BTN_TOOL_RUBBER,
        SCAN_BTN_STYLUS3,
        SCAN_BTN_STYLUS,
        SCAN_BTN_STYLUS2
    )

    // Never hijack core Android navigation/volume/power keys even if reported unexpectedly
    private val RESERVED_SYSTEM_KEYCODES = setOf(
        3,   // KEYCODE_HOME
        4,   // KEYCODE_BACK
        24,  // KEYCODE_VOLUME_UP
        25,  // KEYCODE_VOLUME_DOWN
        26,  // KEYCODE_POWER
        82,  // KEYCODE_MENU
        164, // KEYCODE_VOLUME_MUTE
        187  // KEYCODE_APP_SWITCH
    )

    fun isReservedSystemKey(keyCode: Int): Boolean = keyCode in RESERVED_SYSTEM_KEYCODES

    fun isStylusInputSource(source: Int): Boolean {
        return (source and SOURCE_STYLUS) == SOURCE_STYLUS ||
            (source and SOURCE_BLUETOOTH_STYLUS) == SOURCE_BLUETOOTH_STYLUS
    }

    fun isStylusShortcutKeyEvent(
        keyCode: Int,
        scanCode: Int,
        isFromStylusSource: Boolean,
        customBinding: StylusCustomBinding?
    ): Boolean {
        if (isReservedSystemKey(keyCode)) return false

        if (customBinding != null && customBinding.isConfigured) {
            if (customBinding.keyCode > 0 && keyCode == customBinding.keyCode) return true
            if (customBinding.scanCode > 0 && scanCode == customBinding.scanCode) return true
        }

        if (keyCode in KEYCODE_STYLUS_BUTTON_PRIMARY..KEYCODE_STYLUS_BUTTON_TAIL) {
            return true
        }

        if (scanCode in STANDARD_STYLUS_SCAN_CODES) {
            return true
        }

        // OEM Bluetooth/NearLink stylus profiles sometimes map barrel double-tap to F-keys
        if (isFromStylusSource && (keyCode in KEYCODE_F1..KEYCODE_F12 || keyCode in KEYCODE_F13..KEYCODE_F24)) {
            return true
        }

        return false
    }

    fun isStylusShortcutMotionButton(
        buttonMask: Int,
        customBinding: StylusCustomBinding?
    ): Boolean {
        if (buttonMask == 0) return false
        if ((buttonMask and (BUTTON_STYLUS_PRIMARY or BUTTON_STYLUS_SECONDARY)) != 0) {
            return true
        }
        if (customBinding != null && customBinding.motionButton > 0) {
            if ((buttonMask and customBinding.motionButton) != 0) return true
        }
        return false
    }

    fun shouldAcceptToggle(nowMs: Long, lastToggleMs: Long): Boolean {
        if (lastToggleMs <= 0L) return true
        return (nowMs - lastToggleMs) >= TOGGLE_DEBOUNCE_MS
    }

    /**
     * Toggles between the last active drawing tool (PEN or HIGHLIGHTER) and ERASER.
     * Returns Pair(newActiveTool, updatedLastDrawingTool).
     */
    fun resolveToggleTool(
        activeTool: CanvasTool,
        lastDrawingTool: CanvasTool
    ): Pair<CanvasTool, CanvasTool> {
        val safeLastDrawing = if (lastDrawingTool == CanvasTool.PEN || lastDrawingTool == CanvasTool.HIGHLIGHTER) {
            lastDrawingTool
        } else {
            CanvasTool.PEN
        }
        return if (activeTool == CanvasTool.ERASER) {
            safeLastDrawing to safeLastDrawing
        } else {
            val remembered = if (activeTool == CanvasTool.PEN || activeTool == CanvasTool.HIGHLIGHTER) {
                activeTool
            } else {
                safeLastDrawing
            }
            CanvasTool.ERASER to remembered
        }
    }
}
