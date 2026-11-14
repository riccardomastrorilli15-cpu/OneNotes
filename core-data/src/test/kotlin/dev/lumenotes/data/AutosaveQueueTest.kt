package dev.lumenotes.data

import dev.lumenotes.model.NoteDraft
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutosaveQueueTest {
    private fun draft(id: Long, body: String) = NoteDraft(id, "", body, "", "")

    @Test fun latestEditIsSavedSeparatelyForEachNote() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val saved = ConcurrentHashMap<Long, String>()
            val queue = AutosaveQueue({
                saved[it.id] = it.body
                true
            }, scope)
            repeat(50) {
                queue.submit(draft(1, "one-$it"))
                queue.submit(draft(2, "two-$it"))
            }
            val a = queue.submit(draft(1, "final one"))
            val b = queue.submit(draft(2, "final two"))
            withTimeout(5_000) {
                assertTrue(queue.await(1, a))
                assertTrue(queue.await(2, b))
            }
            assertEquals("final one", saved[1])
            assertEquals("final two", saved[2])
        } finally {
            scope.cancel()
        }
    }

    @Test fun failedWritesAreReported() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val queue = AutosaveQueue({ false }, scope)
            val revision = queue.submit(draft(1, "unsaved"))
            assertFalse(withTimeout(5_000) { queue.await(1, revision) })
        } finally {
            scope.cancel()
        }
    }

    @Test fun olderCompletionDoesNotMarkNewerRevisionSaved() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val olderStarted = CompletableDeferred<Unit>()
            val finishOlder = CompletableDeferred<Unit>()
            val newerStarted = CompletableDeferred<Unit>()
            val finishNewer = CompletableDeferred<Unit>()
            val queue = AutosaveQueue({ draft ->
                when (draft.body) {
                    "older" -> {
                        olderStarted.complete(Unit)
                        finishOlder.await()
                    }

                    "newer" -> {
                        newerStarted.complete(Unit)
                        finishNewer.await()
                    }
                }
                true
            }, scope)
            queue.submit(draft(1, "older"))
            withTimeout(5_000) { olderStarted.await() }
            val newest = queue.submit(draft(1, "newer"))
            finishOlder.complete(Unit)
            withTimeout(5_000) { newerStarted.await() }
            assertEquals(LatestSaveQueue.Status(newest, LatestSaveQueue.Phase.SAVING), queue.status.value[1])
            finishNewer.complete(Unit)
            assertTrue(withTimeout(5_000) { queue.await(1, newest) })
        } finally {
            scope.cancel()
        }
    }

    @Test fun failedWriteCanBeRetried() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val attempts = AtomicInteger()
            val queue = AutosaveQueue({
                if (attempts.incrementAndGet() == 1) error("disk failure")
                true
            }, scope)
            val failed = queue.submit(draft(1, "recoverable"))
            assertFalse(withTimeout(5_000) { queue.await(1, failed) })
            val retried = queue.submit(draft(1, "recoverable"))
            assertTrue(withTimeout(5_000) { queue.await(1, retried) })
            assertEquals(2, attempts.get())
        } finally {
            scope.cancel()
        }
    }
}
