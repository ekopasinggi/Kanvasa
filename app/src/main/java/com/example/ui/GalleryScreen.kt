package com.example.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.NoteBoardEntity
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.CanvasSerializer
import com.example.viewmodel.GalleryViewModel

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onOpenBoard: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var boardToRename by remember { mutableStateOf<NoteBoardEntity?>(null) }
    var boardToDelete by remember { mutableStateOf<NoteBoardEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.totalBoardCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.new_canvas_button)) },
                    modifier = Modifier.testTag("fab_new_canvas")
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = when {
                maxWidth >= 840.dp -> 3
                maxWidth >= 560.dp -> 2
                else -> 1
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("boards_grid")
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GalleryHeaderSection(
                        searchQuery = state.searchQuery,
                        showSearch = state.totalBoardCount > 0,
                        onSearchChange = viewModel::onSearchQueryChange
                    )
                }

                if (!state.isLoading && state.totalBoardCount == 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyStudioOnboardingCard(
                            onCreateFirstClick = { showCreateDialog = true }
                        )
                    }
                } else if (!state.isLoading && state.boards.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NoSearchResultsBox()
                    }
                } else {
                    items(
                        items = state.boards,
                        key = { it.id }
                    ) { board ->
                        NoteBoardCard(
                            board = board,
                            onClick = { onOpenBoard(board.id) },
                            onRenameClick = { boardToRename = board },
                            onDeleteClick = { boardToDelete = board }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        BoardConfigDialog(
            isRename = false,
            initialTitle = "",
            initialPattern = CanvasPattern.DOTS,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, pattern ->
                showCreateDialog = false
                viewModel.createNewBoard(title, pattern) { createdId ->
                    onOpenBoard(createdId)
                }
            }
        )
    }

    boardToRename?.let { target ->
        BoardConfigDialog(
            isRename = true,
            initialTitle = target.title,
            initialPattern = CanvasPattern.fromName(target.patternName),
            onDismiss = { boardToRename = null },
            onConfirm = { newTitle, newPattern ->
                viewModel.renameBoard(target, newTitle, newPattern)
                boardToRename = null
            }
        )
    }

    boardToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { boardToDelete = null },
            title = { Text(stringResource(R.string.delete_board_confirm_title)) },
            text = {
                Text(stringResource(R.string.delete_board_confirm_desc, target.title))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBoard(target)
                        boardToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_board_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { boardToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun GalleryHeaderSection(
    searchQuery: String,
    showSearch: Boolean,
    onSearchChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.gallery_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(stringResource(R.string.search_boards_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_cancel)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_boards_input")
            )
        }
    }
}

@Composable
private fun EmptyStudioOnboardingCard(
    onCreateFirstClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_studio_1794840019390),
                    contentDescription = stringResource(R.string.empty_gallery_title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.empty_gallery_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_gallery_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onCreateFirstClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier.testTag("empty_state_create_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.create_first_canvas))
                }
            }
        }
    }
}

@Composable
private fun NoSearchResultsBox() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.no_search_results_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.no_search_results_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoteBoardCard(
    board: NoteBoardEntity,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pattern = remember(board.patternName) { CanvasPattern.fromName(board.patternName) }
    val previewElements = remember(board.elementsJson) {
        CanvasSerializer.decode(board.elementsJson).take(25)
    }
    val relativeTime = remember(board.updatedAt) {
        DateUtils.getRelativeTimeSpanString(
            board.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("board_card_${board.id}")
    ) {
        Column {
            // Mini Canvas Pattern & Sketch Preview Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(Color(0xFFFAF9F6))
            ) {
                MiniBoardCanvasPreview(
                    pattern = pattern,
                    elements = previewElements,
                    modifier = Modifier.fillMaxSize()
                )

                // Pattern Badge Top-Left
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        val icon = when (pattern) {
                            CanvasPattern.DOTS -> Icons.Default.Grain
                            CanvasPattern.GRID -> Icons.Default.Grid4x4
                            CanvasPattern.BLANK -> Icons.Outlined.CheckBoxOutlineBlank
                        }
                        val label = when (pattern) {
                            CanvasPattern.DOTS -> stringResource(R.string.pattern_dots)
                            CanvasPattern.GRID -> stringResource(R.string.pattern_grid)
                            CanvasPattern.BLANK -> stringResource(R.string.pattern_blank)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card Footer Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = board.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val countLabel = if (board.elementCount == 0) {
                        stringResource(R.string.board_empty_label)
                    } else {
                        stringResource(R.string.board_elements_count, board.elementCount)
                    }
                    Text(
                        text = "$countLabel • $relativeTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("board_menu_${board.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_rename)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBoardCanvasPreview(
    pattern: CanvasPattern,
    elements: List<CanvasElement>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val step = 20.dp.toPx()
        when (pattern) {
            CanvasPattern.DOTS -> {
                var x = step / 2f
                while (x < size.width) {
                    var y = step / 2f
                    while (y < size.height) {
                        drawCircle(
                            color = Color(0xFFD6D2C6),
                            radius = 1.4.dp.toPx(),
                            center = Offset(x, y)
                        )
                        y += step
                    }
                    x += step
                }
            }
            CanvasPattern.GRID -> {
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = Color(0xFFE6E3DA),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = Color(0xFFE6E3DA),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
            }
            CanvasPattern.BLANK -> Unit
        }

        if (elements.isNotEmpty()) {
            var minL = Float.POSITIVE_INFINITY
            var minT = Float.POSITIVE_INFINITY
            var maxR = Float.NEGATIVE_INFINITY
            var maxB = Float.NEGATIVE_INFINITY
            for (el in elements) {
                val b = el.bounds()
                if (b.left < minL) minL = b.left
                if (b.top < minT) minT = b.top
                if (b.right > maxR) maxR = b.right
                if (b.bottom > maxB) maxB = b.bottom
            }
            val spanW = (maxR - minL).coerceAtLeast(100f)
            val spanH = (maxB - minT).coerceAtLeast(80f)
            val scale = kotlin.math.min(
                (size.width * 0.78f) / spanW,
                (size.height * 0.72f) / spanH
            )
            val offX = (size.width - spanW * scale) / 2f - minL * scale
            val offY = (size.height - spanH * scale) / 2f - minT * scale

            for (el in elements) {
                when (el) {
                    is CanvasElement.Stroke -> {
                        val pts = el.points
                        for (i in 0 until pts.size - 1) {
                            drawLine(
                                color = Color(el.colorArgb).copy(alpha = if (el.isHighlighter) 0.4f else 0.85f),
                                start = Offset(pts[i].x * scale + offX, pts[i].y * scale + offY),
                                end = Offset(pts[i + 1].x * scale + offX, pts[i + 1].y * scale + offY),
                                strokeWidth = (el.strokeWidth * scale).coerceIn(1.5f, 6f)
                            )
                        }
                    }
                    is CanvasElement.TextNote -> {
                        val b = el.bounds()
                        drawRoundRect(
                            color = if (el.isSticky) Color(el.cardColorArgb) else Color(0xFFE2E8F0),
                            topLeft = Offset(b.left * scale + offX, b.top * scale + offY),
                            size = androidx.compose.ui.geometry.Size(
                                (b.width * scale).coerceAtLeast(12f),
                                (b.height * scale).coerceAtLeast(10f)
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }
                    is CanvasElement.Shape -> {
                        val b = el.bounds()
                        drawRoundRect(
                            color = Color(el.colorArgb).copy(alpha = 0.7f),
                            topLeft = Offset(b.left * scale + offX, b.top * scale + offY),
                            size = androidx.compose.ui.geometry.Size(
                                (b.width * scale).coerceAtLeast(8f),
                                (b.height * scale).coerceAtLeast(8f)
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                    is CanvasElement.ImageItem -> {
                        val b = el.bounds()
                        drawRoundRect(
                            color = Color(0xFFCBD5E1),
                            topLeft = Offset(b.left * scale + offX, b.top * scale + offY),
                            size = androidx.compose.ui.geometry.Size(
                                (b.width * scale).coerceAtLeast(14f),
                                (b.height * scale).coerceAtLeast(12f)
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }
                }
            }
        }
    }
}
