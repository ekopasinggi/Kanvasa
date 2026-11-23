package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CanvasPreferences
import com.example.data.KanvasaDatabase
import com.example.data.MediaStorageManager
import com.example.data.NoteRepository
import com.example.ui.CanvasWorkspaceScreen
import com.example.ui.GalleryScreen
import com.example.ui.theme.KanvasaTheme
import com.example.viewmodel.CanvasViewModel
import com.example.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KanvasaTheme {
                KanvasaApp()
            }
        }
    }
}

@Composable
fun KanvasaApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        val db = KanvasaDatabase.getInstance(context)
        val mediaStorage = MediaStorageManager(context)
        NoteRepository(db.noteBoardDao(), mediaStorage)
    }
    val canvasPreferences = remember(context) { CanvasPreferences(context) }

    var activeBoardId by rememberSaveable { mutableLongStateOf(-1L) }

    val galleryViewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModel.provideFactory(repository)
    )
    val canvasViewModel: CanvasViewModel = viewModel(
        factory = CanvasViewModel.provideFactory(repository, canvasPreferences)
    )

    Crossfade(
        targetState = activeBoardId,
        label = "screen_transition"
    ) { boardId ->
        if (boardId <= 0L) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onOpenBoard = { selectedId -> activeBoardId = selectedId }
            )
        } else {
            CanvasWorkspaceScreen(
                boardId = boardId,
                viewModel = canvasViewModel,
                onNavigateBack = { activeBoardId = -1L }
            )
        }
    }
}
