package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteBoardEntity
import com.example.data.NoteRepository
import com.example.model.CanvasPattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GalleryUiState(
    val boards: List<NoteBoardEntity> = emptyList(),
    val searchQuery: String = "",
    val totalBoardCount: Int = 0,
    val isLoading: Boolean = true
)

class GalleryViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.allBoards,
        searchQueryFlow
    ) { allBoards, query ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allBoards
        } else {
            allBoards.filter { it.title.contains(trimmed, ignoreCase = true) }
        }
        GalleryUiState(
            boards = filtered,
            searchQuery = query,
            totalBoardCount = allBoards.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )

    fun onSearchQueryChange(newQuery: String) {
        searchQueryFlow.value = newQuery
    }

    fun createNewBoard(
        title: String,
        pattern: CanvasPattern,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createBoard(title, pattern)
            onCreated(id)
        }
    }

    fun renameBoard(board: NoteBoardEntity, newTitle: String, newPattern: CanvasPattern) {
        viewModelScope.launch {
            repository.renameBoard(board, newTitle, newPattern)
        }
    }

    fun deleteBoard(board: NoteBoardEntity) {
        viewModelScope.launch {
            repository.deleteBoard(board)
        }
    }

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GalleryViewModel(repository) as T
                }
            }
    }
}
