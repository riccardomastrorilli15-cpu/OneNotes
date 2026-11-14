package dev.lumenotes.data

import dev.lumenotes.model.NoteDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App-scoped, ordered writer; coalescing is per note, not across notes. */
open class LatestSaveQueue<T>(private val keyOf: (T) -> Long, private val save: suspend (T) -> Boolean, scope: CoroutineScope) {
    enum class Phase { SAVING, SAVED, FAILED }
    data class Status(val revision: Long, val phase: Phase)
    private data class Pending<T>(val key: Long, val revision: Long, val value: T)

    private val lock = Any()
    private val pending = LinkedHashMap<Long, Pending<T>>()
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private var nextRevision = 0L
    private val mutableStatus = MutableStateFlow<Map<Long, Status>>(emptyMap())
    val status: StateFlow<Map<Long, Status>> = mutableStatus

    init {
        scope.launch(Dispatchers.IO) {
            for (signal in wakeup) {
                while (true) {
                    val item = synchronized(lock) {
                        val entry = pending.entries.firstOrNull() ?: return@synchronized null
                        pending.remove(entry.key)
                    } ?: break
                    val saved = try {
                        save(item.value)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    synchronized(lock) {
                        val current = mutableStatus.value[item.key]
                        if (current?.revision == item.revision) {
                            mutableStatus.value = mutableStatus.value +
                                (item.key to Status(item.revision, if (saved) Phase.SAVED else Phase.FAILED))
                        }
                    }
                }
            }
        }
    }

    fun submit(value: T): Long {
        val key = keyOf(value)
        val revision = synchronized(lock) {
            val revision = ++nextRevision
            pending[key] = Pending(key, revision, value)
            mutableStatus.value = mutableStatus.value + (key to Status(revision, Phase.SAVING))
            revision
        }
        wakeup.trySend(Unit)
        return revision
    }

    suspend fun await(id: Long, revision: Long): Boolean {
        val final = status.first { statuses ->
            val current = statuses[id]
            current != null && current.revision >= revision && current.phase != Phase.SAVING
        }[id]
        return final?.phase == Phase.SAVED
    }
}

class AutosaveQueue(save: suspend (NoteDraft) -> Boolean, scope: CoroutineScope) : LatestSaveQueue<NoteDraft>({ it.id }, save, scope)
