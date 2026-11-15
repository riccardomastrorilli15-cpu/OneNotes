package dev.lumenotes.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

/** Read only a bounded, valid UTF-8 document; never silently replace invalid bytes. */
internal suspend fun readTextDocument(input: InputStream, maxBytes: Int = MAX_IMPORT_BYTES): String {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) throw IOException("Input stream made no progress")
        total += read
        require(total <= maxBytes.toLong()) { "Text document is too large" }
        output.write(buffer, 0, read)
    }
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
}
