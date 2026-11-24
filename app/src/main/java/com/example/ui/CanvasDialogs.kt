package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.view.inputmethod.EditorInfo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import com.example.R
import com.example.model.CanvasPattern
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.StylusCustomBinding
import com.example.model.StylusHardwareSignal
import com.example.ui.theme.InkSwatches
import com.example.ui.theme.StickyNoteSwatches
import com.example.viewmodel.TextEditorDraft

@Composable
fun BoardConfigDialog(
    isRename: Boolean,
    initialTitle: String,
    initialPattern: CanvasPattern,
    onDismiss: () -> Unit,
    onConfirm: (String, CanvasPattern) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var selectedPattern by remember(initialPattern) { mutableStateOf(initialPattern) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isRename) R.string.dialog_rename_board_title else R.string.dialog_new_board_title
                ),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StylusFormField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.board_title_label),
                    placeholder = stringResource(R.string.board_title_placeholder),
                    singleLine = true,
                    imeAction = EditorInfo.IME_ACTION_DONE,
                    onImeAction = {
                        val finalTitle = title.trim().ifBlank { "Catatan Baru" }
                        onConfirm(finalTitle, selectedPattern)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("board_title_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.board_pattern_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PatternChoiceChip(
                            pattern = CanvasPattern.DOTS,
                            selected = selectedPattern == CanvasPattern.DOTS,
                            onClick = { selectedPattern = CanvasPattern.DOTS },
                            modifier = Modifier.weight(1f)
                        )
                        PatternChoiceChip(
                            pattern = CanvasPattern.GRID,
                            selected = selectedPattern == CanvasPattern.GRID,
                            onClick = { selectedPattern = CanvasPattern.GRID },
                            modifier = Modifier.weight(1f)
                        )
                        PatternChoiceChip(
                            pattern = CanvasPattern.BLANK,
                            selected = selectedPattern == CanvasPattern.BLANK,
                            onClick = { selectedPattern = CanvasPattern.BLANK },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { "Catatan Baru" }
                    onConfirm(finalTitle, selectedPattern)
                },
                modifier = Modifier.testTag("confirm_board_dialog_button")
            ) {
                Text(
                    stringResource(
                        if (isRename) R.string.action_save else R.string.action_create
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_board_dialog_button")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PatternChoiceChip(
    pattern: CanvasPattern,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (pattern) {
        CanvasPattern.DOTS -> stringResource(R.string.pattern_dots)
        CanvasPattern.GRID -> stringResource(R.string.pattern_grid)
        CanvasPattern.BLANK -> stringResource(R.string.pattern_blank)
    }
    val icon = when (pattern) {
        CanvasPattern.DOTS -> Icons.Default.Grain
        CanvasPattern.GRID -> Icons.Default.Grid4x4
        CanvasPattern.BLANK -> Icons.Outlined.CheckBoxOutlineBlank
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = modifier.testTag("pattern_chip_${pattern.name.lowercase()}")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextStickyEditorSheet(
    draft: TextEditorDraft,
    onDismiss: () -> Unit,
    onConfirm: (text: String, fontSizeSp: Float, textColorArgb: Long, cardColorArgb: Long) -> Unit
) {
    var text by remember(draft) { mutableStateOf(draft.initialText) }
    var fontSizeSp by remember(draft) { mutableFloatStateOf(draft.fontSizeSp) }
    var textColorArgb by remember(draft) { mutableLongStateOf(draft.textColorArgb) }
    var cardColorArgb by remember(draft) { mutableLongStateOf(draft.cardColorArgb) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val titleRes = when {
        draft.isSticky && draft.editingElementId != null -> R.string.editor_edit_sticky_title
        draft.isSticky -> R.string.editor_add_sticky_title
        draft.editingElementId != null -> R.string.editor_edit_text_title
        else -> R.string.editor_add_text_title
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge
            )

            StylusFormField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.editor_text_placeholder),
                singleLine = false,
                minLines = 4,
                maxLines = 7,
                minHeight = 132.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("canvas_text_editor_input")
            )

            if (draft.isSticky) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.editor_sticky_color_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StickyNoteSwatches.forEach { swatch ->
                            ColorCircleSwatch(
                                colorArgb = swatch,
                                selected = cardColorArgb == swatch,
                                onClick = { cardColorArgb = swatch }
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.editor_text_color_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkSwatches.forEach { swatch ->
                            ColorCircleSwatch(
                                colorArgb = swatch,
                                selected = textColorArgb == swatch,
                                onClick = { textColorArgb = swatch }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.editor_font_size_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sizes = listOf(
                        15f to R.string.font_size_small,
                        19f to R.string.font_size_medium,
                        25f to R.string.font_size_large
                    )
                    sizes.forEach { (sizeVal, labelId) ->
                        FilterChip(
                            selected = kotlin.math.abs(fontSizeSp - sizeVal) < 1.5f,
                            onClick = { fontSizeSp = sizeVal },
                            label = { Text(stringResource(labelId)) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(text, fontSizeSp, textColorArgb, cardColorArgb)
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.testTag("save_text_note_button")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
fun ColorCircleSwatch(
    colorArgb: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(colorArgb))
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (colorArgb == 0xFF1E293BL || colorArgb == 0xFF2563EBL || colorArgb == 0xFFDC2626L || colorArgb == 0xFF7C3AEDL) {
                        Color.White
                    } else {
                        Color(0xFF0F172A)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    format: ExportFormat,
    scope: ExportScope,
    previewBitmap: Bitmap?,
    isGeneratingPreview: Boolean,
    isExporting: Boolean,
    onFormatChange: (ExportFormat) -> Unit,
    onScopeChange: (ExportScope) -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.export_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Format Selector (PDF vs PNG)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_format_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExportOptionCard(
                        selected = format == ExportFormat.PDF,
                        title = stringResource(R.string.export_format_pdf),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (format == ExportFormat.PDF) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        onClick = { onFormatChange(ExportFormat.PDF) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_format_pdf_card")
                    )
                    ExportOptionCard(
                        selected = format == ExportFormat.PNG,
                        title = stringResource(R.string.export_format_png),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = if (format == ExportFormat.PNG) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        onClick = { onFormatChange(ExportFormat.PNG) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_format_png_card")
                    )
                }
            }

            // Scope Selector (Full Content vs Active Viewport)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_scope_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ScopeChoiceRow(
                    selected = scope == ExportScope.FULL_CONTENT,
                    title = stringResource(R.string.export_scope_full),
                    subtitle = stringResource(R.string.export_scope_full_desc),
                    icon = Icons.Default.Fullscreen,
                    onClick = { onScopeChange(ExportScope.FULL_CONTENT) },
                    testTag = "export_scope_full_option"
                )
                ScopeChoiceRow(
                    selected = scope == ExportScope.ACTIVE_VIEWPORT,
                    title = stringResource(R.string.export_scope_viewport),
                    subtitle = stringResource(R.string.export_scope_viewport_desc),
                    icon = Icons.Default.CropFree,
                    onClick = { onScopeChange(ExportScope.ACTIVE_VIEWPORT) },
                    testTag = "export_scope_viewport_option"
                )
            }

            // Live Preview Box
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.export_preview_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 220.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        if (isGeneratingPreview) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        } else if (previewBitmap != null && !previewBitmap.isRecycled) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.export_preview_label),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("export_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_share_now))
                }

                Button(
                    onClick = onSaveToDevice,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("export_save_device_button")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_save_device))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    selected: Boolean,
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ScopeChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun StylusCalibrationDialog(
    currentBinding: StylusCustomBinding,
    capturedSignal: StylusHardwareSignal?,
    onSaveCaptured: (StylusCustomBinding) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
            .padding(24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.stylus_calibrate_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.stylus_calibrate_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (capturedSignal != null) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (capturedSignal != null) 1.5.dp else 1.dp,
                        color = if (capturedSignal != null) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = if (capturedSignal != null) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Edit
                            },
                            contentDescription = null,
                            tint = if (capturedSignal != null) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (capturedSignal != null) {
                                stringResource(
                                    R.string.stylus_calibrate_detected,
                                    capturedSignal.label
                                )
                            } else {
                                stringResource(R.string.stylus_calibrate_waiting)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (currentBinding.isConfigured) {
                    Text(
                        text = stringResource(
                            R.string.stylus_calibrate_current,
                            currentBinding.label.ifBlank { "key=${currentBinding.keyCode}" }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = stringResource(R.string.stylus_calibrate_matepad_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    if (currentBinding.isConfigured) {
                        TextButton(
                            onClick = onResetDefault,
                            modifier = Modifier.testTag("stylus_calibrate_reset_button")
                        ) {
                            Text(stringResource(R.string.stylus_calibrate_reset))
                        }
                    }
                    if (capturedSignal != null) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { onSaveCaptured(capturedSignal.toCustomBinding()) },
                            modifier = Modifier.testTag("stylus_calibrate_save_button")
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("stylus_calibrate_close_button")
                        ) {
                            Text(stringResource(R.string.stylus_calibrate_close))
                        }
                    }
                }
            }
        }
    }
}

