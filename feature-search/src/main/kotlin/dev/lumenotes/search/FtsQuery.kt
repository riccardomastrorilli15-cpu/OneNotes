package dev.lumenotes.search

/** Builds literal prefix terms for SQLite FTS5, never accepts FTS operators from input. */
object FtsQuery {
    private val words = Regex("[\\p{L}\\p{M}\\p{N}]+")
    private const val MAX_TERMS = 12
    private const val MAX_TERM_LENGTH = 64

    fun fromUserInput(input: String): String? {
        val terms = words.findAll(input)
            .take(MAX_TERMS)
            .map { it.value.take(MAX_TERM_LENGTH) }
            .filter { it.isNotBlank() }
            .toList()
        return terms.takeIf { it.isNotEmpty() }
            ?.joinToString(" AND ") { "\"$it\"*" }
    }
}
