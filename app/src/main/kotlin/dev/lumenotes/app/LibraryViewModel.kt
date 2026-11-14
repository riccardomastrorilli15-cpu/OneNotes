package dev.lumenotes.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumenotes.data.NotesRepository
import dev.lumenotes.model.NoteCollection
import dev.lumenotes.model.NoteSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Content(val notes: List<NoteSummary>) : LibraryState
    data object Error : LibraryState
}

sealed interface LibraryEvent {
    data class OpenEditor(val id: Long) : LibraryEvent
    data object Error : LibraryEvent
}

private data class Filters(val collection: NoteCollection, val query: String, val folder: String?, val limit: Int, val retry: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(private val repository: NotesRepository) : ViewModel() {
    val collection = MutableStateFlow(NoteCollection.NOTES)
    val query = MutableStateFlow("")
    val folder = MutableStateFlow<String?>(null)
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val retryCounter = MutableStateFlow(0)
    private val eventsChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    val folders = repository.observeFolders().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filters = combine(collection, query, folder, limit, retryCounter, ::Filters)
    val state: StateFlow<LibraryState> = filters.flatMapLatest { filter ->
        repository.observeCollection(filter.collection, filter.query, filter.folder, filter.limit)
            .map<List<NoteSummary>, LibraryState> { LibraryState.Content(it) }
            .onStart { emit(LibraryState.Loading) }
            .catch { emit(LibraryState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState.Loading)

    fun setCollection(value: NoteCollection) {
        if (collection.value != value) {
            collection.value = value
            folder.value = null
            limit.value = PAGE_SIZE
        }
    }

    fun setQuery(value: String) {
        query.value = value
        limit.value = PAGE_SIZE
    }
    fun setFolder(value: String?) {
        folder.value = value
        limit.value = PAGE_SIZE
    }
    fun retry() {
        retryCounter.value += 1
    }

    fun loadMore() {
        val count = (state.value as? LibraryState.Content)?.notes?.size ?: return
        if (count >= limit.value) limit.value += PAGE_SIZE
    }

    private var creating = false

    fun create(title: String = "", body: String = "") {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                val id = repository.create(title, body)
                eventsChannel.send(LibraryEvent.OpenEditor(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventsChannel.send(LibraryEvent.Error)
            } finally {
                creating = false
            }
        }
    }

    fun perform(action: suspend (NotesRepository) -> Unit) {
        viewModelScope.launch {
            try {
                action(repository)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                eventsChannel.send(LibraryEvent.Error)
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 40
    }
}
