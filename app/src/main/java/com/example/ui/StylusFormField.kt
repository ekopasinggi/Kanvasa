package com.example.ui

import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.example.R

/**
 * Material 3 styled text input backed by a genuine Android [EditText] so system-level stylus
 * handwriting engines — including Huawei MatePad FreeScript (HarmonyOS/EMUI) and Android 14+
 * Stylus Handwriting — detect the input bounds, convert handwriting directly into text, and
 * support native scratch-out / select / insert-space gestures seamlessly.
 */
@Composable
fun StylusFormField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = if (singleLine) 1 else 4,
    maxLines: Int = if (singleLine) 1 else 7,
    minHeight: Dp = if (singleLine) 52.dp else 124.dp,
    imeAction: Int = if (singleLine) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NONE,
    onImeAction: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val typeface = remember(context) {
        runCatching { ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) }.getOrNull()
    }
    var isFocused by remember { mutableStateOf(false) }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnImeAction by rememberUpdatedState(onImeAction)

    val textColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "stylus_field_border"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "stylus_field_label"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }

        Surface(
            shape = shape,
            color = containerColor,
            border = BorderStroke(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (leadingIcon != null) 8.dp else 14.dp,
                        end = if (trailingIcon != null) 6.dp else 14.dp,
                        top = if (singleLine) 4.dp else 10.dp,
                        bottom = if (singleLine) 4.dp else 10.dp
                    )
            ) {
                if (leadingIcon != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        leadingIcon()
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (singleLine) 44.dp else 104.dp),
                    factory = { ctx ->
                        EditText(ctx).apply {
                            background = null
                            isFocusable = true
                            isFocusableInTouchMode = true
                            isCursorVisible = true
                            includeFontPadding = true
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                            setTextColor(textColorArgb)
                            setHintTextColor(hintColorArgb)
                            hint = placeholder
                            if (typeface != null) {
                                this.typeface = typeface
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                isAutoHandwritingEnabled = true
                            }

                            if (singleLine) {
                                isSingleLine = true
                                this.maxLines = 1
                                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                imeOptions = imeAction
                                setPadding(4, 14, 4, 14)
                            } else {
                                isSingleLine = false
                                this.minLines = minLines
                                this.maxLines = maxLines
                                gravity = Gravity.TOP or Gravity.START
                                inputType = InputType.TYPE_CLASS_TEXT or
                                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                                isVerticalScrollBarEnabled = true
                                setPadding(4, 8, 4, 8)
                            }

                            setText(value)
                            setSelection(text?.length ?: 0)

                            setOnFocusChangeListener { _, hasFocus ->
                                isFocused = hasFocus
                            }

                            setOnEditorActionListener { _, actionId, _ ->
                                if (singleLine && (actionId == imeAction || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH)) {
                                    latestOnImeAction?.invoke()
                                    latestOnImeAction != null
                                } else {
                                    false
                                }
                            }

                            addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                override fun afterTextChanged(s: Editable?) {
                                    if (tag == TAG_INTERNAL_SYNC) return
                                    val updated = s?.toString() ?: ""
                                    latestOnValueChange(updated)
                                }
                            })
                        }
                    },
                    update = { editText ->
                        editText.setTextColor(textColorArgb)
                        editText.setHintTextColor(hintColorArgb)
                        if (editText.hint?.toString() != placeholder) {
                            editText.hint = placeholder
                        }
                        // Only mutate EditText content when external state genuinely diverges,
                        // preserving IME / Huawei FreeScript composing spans and cursor position.
                        val current = editText.text?.toString() ?: ""
                        if (current != value) {
                            editText.tag = TAG_INTERNAL_SYNC
                            editText.setText(value)
                            editText.setSelection(value.length)
                            editText.tag = null
                        }
                    }
                )

                if (trailingIcon != null) {
                    Box(contentAlignment = Alignment.Center) {
                        trailingIcon()
                    }
                }
            }
        }
    }
}

private const val TAG_INTERNAL_SYNC = "kanvasa_internal_sync"
