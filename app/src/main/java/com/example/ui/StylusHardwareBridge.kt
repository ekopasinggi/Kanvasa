package com.example.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.model.StylusCustomBinding
import com.example.model.StylusHardwareSignal
import com.example.model.StylusShortcutPolicy

/**
 * Activity-scoped bridge that intercepts hardware stylus shortcuts (double-tap side of M-Pencil,
 * standard Android stylus barrel/tail buttons, and hold-to-erase button states) before child views
 * drop or ignore them.
 */
class StylusHardwareBridge {
    @Volatile
    var isCanvasActive: Boolean = false

    @Volatile
    var isModalOpen: Boolean = false

    @Volatile
    var isCalibrationMode: Boolean = false

    @Volatile
    var customBinding: StylusCustomBinding = StylusCustomBinding()

    @Volatile
    var isStylusButtonHeld: Boolean = false
        private set

    @Volatile
    private var isStylusTouchingGlass: Boolean = false

    @Volatile
    private var buttonUsedForHoldErase: Boolean = false

    @Volatile
    private var keyUsedForHoldErase: Boolean = false

    @Volatile
    private var lastToggleTimestampMs: Long = 0L

    var onTogglePenEraser: (() -> Unit)? = null
    var onCalibrationCaptured: ((StylusHardwareSignal) -> Unit)? = null

    fun notifyStylusTouchActive(touching: Boolean) {
        isStylusTouchingGlass = touching
        if (touching && isStylusButtonHeld) {
            buttonUsedForHoldErase = true
            keyUsedForHoldErase = true
        }
        if (!touching) {
            // Reset hold flag once stylus lifts if button is no longer held
            if (!isStylusButtonHeld) {
                buttonUsedForHoldErase = false
                keyUsedForHoldErase = false
            }
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val scanCode = event.scanCode
        if (StylusShortcutPolicy.isReservedSystemKey(keyCode)) return false

        val isFromStylus = StylusShortcutPolicy.isStylusInputSource(event.source) ||
            (event.device?.sources?.let { StylusShortcutPolicy.isStylusInputSource(it) } == true)

        // Calibration mode: capture any non-system key/stylus hardware event for user inspection
        if (isCalibrationMode) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val keyName = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
                val label = if (scanCode > 0) "$keyName (scan $scanCode)" else keyName
                onCalibrationCaptured?.invoke(
                    StylusHardwareSignal(
                        keyCode = keyCode,
                        scanCode = scanCode,
                        motionButton = 0,
                        source = event.source,
                        label = label
                    )
                )
            }
            return true
        }

        if (!isCanvasActive || isModalOpen) return false

        val matchesShortcut = StylusShortcutPolicy.isStylusShortcutKeyEvent(
            keyCode = keyCode,
            scanCode = scanCode,
            isFromStylusSource = isFromStylus,
            customBinding = customBinding
        )
        if (!matchesShortcut) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    isStylusButtonHeld = true
                    keyUsedForHoldErase = isStylusTouchingGlass
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                val wasHoldErasing = keyUsedForHoldErase
                isStylusButtonHeld = false
                keyUsedForHoldErase = false
                if (!wasHoldErasing) {
                    triggerToggleWithDebounce()
                }
                true
            }
            else -> true
        }
    }

    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionButton = event.actionButton
        val buttonState = event.buttonState
        val isStylusEvent = isStylusMotionEvent(event)

        if (isCalibrationMode && action == MotionEvent.ACTION_BUTTON_PRESS) {
            val mask = if (actionButton != 0) actionButton else buttonState
            if (mask != 0) {
                val label = when {
                    (mask and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 -> "Tombol Sisi Utama (Stylus)"
                    (mask and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 -> "Tombol Sisi Kedua (Stylus)"
                    else -> "Tombol Stylus ($mask)"
                }
                onCalibrationCaptured?.invoke(
                    StylusHardwareSignal(
                        keyCode = 0,
                        scanCode = 0,
                        motionButton = mask,
                        source = event.source,
                        label = label
                    )
                )
                return true
            }
        }

        if (!isCanvasActive || isModalOpen) {
            isStylusButtonHeld = false
            return false
        }

        if (action == MotionEvent.ACTION_BUTTON_PRESS) {
            val mask = if (actionButton != 0) actionButton else buttonState
            if (StylusShortcutPolicy.isStylusShortcutMotionButton(mask, customBinding)) {
                isStylusButtonHeld = true
                buttonUsedForHoldErase = isStylusTouchingGlass
                return true
            }
        } else if (action == MotionEvent.ACTION_BUTTON_RELEASE) {
            val mask = if (actionButton != 0) actionButton else buttonState
            val matches = StylusShortcutPolicy.isStylusShortcutMotionButton(mask, customBinding) ||
                (isStylusButtonHeld && !StylusShortcutPolicy.isStylusShortcutMotionButton(buttonState, customBinding))
            if (matches) {
                val wasHoldErasing = buttonUsedForHoldErase
                isStylusButtonHeld = false
                buttonUsedForHoldErase = false
                if (!wasHoldErasing) {
                    triggerToggleWithDebounce()
                }
                return true
            }
        } else if (isStylusEvent) {
            val heldNow = StylusShortcutPolicy.isStylusShortcutMotionButton(buttonState, customBinding)
            if (heldNow != isStylusButtonHeld) {
                if (heldNow) {
                    isStylusButtonHeld = true
                    buttonUsedForHoldErase = isStylusTouchingGlass
                } else {
                    val wasHoldErasing = buttonUsedForHoldErase
                    isStylusButtonHeld = false
                    buttonUsedForHoldErase = false
                    if (!wasHoldErasing && !isStylusTouchingGlass) {
                        triggerToggleWithDebounce()
                    }
                }
            }
        }

        return false
    }

    fun observeTouchEvent(event: MotionEvent) {
        if (!isCanvasActive || isModalOpen) return
        if (!isStylusMotionEvent(event)) return

        val action = event.actionMasked
        val heldNow = StylusShortcutPolicy.isStylusShortcutMotionButton(event.buttonState, customBinding)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                isStylusTouchingGlass = true
                if (heldNow) {
                    isStylusButtonHeld = true
                    buttonUsedForHoldErase = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                isStylusTouchingGlass = true
                if (heldNow) {
                    isStylusButtonHeld = true
                    buttonUsedForHoldErase = true
                } else if (isStylusButtonHeld && !keyUsedForHoldErase) {
                    isStylusButtonHeld = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isStylusTouchingGlass = false
                if (!heldNow) {
                    isStylusButtonHeld = false
                    buttonUsedForHoldErase = false
                }
            }
        }
    }

    private fun triggerToggleWithDebounce() {
        val now = SystemClock.uptimeMillis()
        if (StylusShortcutPolicy.shouldAcceptToggle(now, lastToggleTimestampMs)) {
            lastToggleTimestampMs = now
            onTogglePenEraser?.invoke()
        }
    }

    private fun isStylusMotionEvent(event: MotionEvent): Boolean {
        if (StylusShortcutPolicy.isStylusInputSource(event.source)) return true
        for (i in 0 until event.pointerCount) {
            val tt = event.getToolType(i)
            if (tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER) {
                return true
            }
        }
        return false
    }
}

val LocalStylusHardwareBridge = staticCompositionLocalOf<StylusHardwareBridge?> { null }
