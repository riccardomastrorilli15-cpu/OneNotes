package dev.lumenotes.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumenotes.data.LatestSaveQueue
import dev.lumenotes.data.NotesRepository
import dev.lumenotes.drawing.InkDocument
import dev.lumenotes.drawing.InkStroke
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DrawingState {
    data object Loading : DrawingState
    data class Ready(val strokes: List<InkStroke>) : DrawingState
    data object Error : DrawingState
}

@HiltViewModel
class DrawingViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: NotesRepository,
    private val queue: LatestSaveQueue<DrawingSnapshot>
) : ViewModel() {
    val id = handle.get<Long>(EXTRA_NOTE_ID) ?: -1L
    private val mutableState = MutableStateFlow<DrawingState>(DrawingState.Loading)
    val state: StateFlow<DrawingState> = mutableState
    val saveStatus = queue.status.map { it[id]?.phase }
    private var current: List<InkStroke>? = null

    init {
        viewModelScope.launch {
            try {
                val note = repository.find(id)
                if (note == null || note.trashedAt != null) {
                    mutableState.value = DrawingState.Error
                } else {
                    val decoded = withContext(Dispatchers.Default) { InkDocument.decode(note.drawing) }
                    decoded.fold(
                        onSuccess = {
                            current = it
                            mutableState.value = DrawingState.Ready(it)
                        },
                        onFailure = { mutableState.value = DrawingState.Error }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = DrawingState.Error
            }
        }
    }

    fun update(strokes: List<InkStroke>) {
        current = strokes
        mutableState.value = DrawingState.Ready(strokes)
        queue.submit(DrawingSnapshot(id, strokes))
    }

    fun retrySave() {
        current?.let { queue.submit(DrawingSnapshot(id, it)) }
    }

    suspend fun flush(): Boolean {
        val strokes = current ?: return false
        val revision = queue.submit(DrawingSnapshot(id, strokes))
        return queue.await(id, revision)
    }
}
