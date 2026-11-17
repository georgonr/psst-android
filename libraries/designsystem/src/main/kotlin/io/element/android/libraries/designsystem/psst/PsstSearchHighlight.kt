/*
 * PSST (D80): message search result snippets with the searched words highlighted.
 *
 * Matching mirrors the search index (psst build of matrix-sdk-search): case and diacritics are ignored and a query
 * word matches the beginning of a word, so `cisl` highlights the whole word `Čísla`. When the first match is far
 * into a long message, the snippet starts a little before it ("…").
 */
package io.element.android.libraries.designsystem.psst

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.text.Normalizer

object PsstSearchHighlight {
    /** Characters kept before the first match when the snippet has to be shortened. */
    private const val CONTEXT_BEFORE = 28

    /** The words of a query, as the index splits them (letters and digits), folded. */
    fun queryWords(query: String): List<String> =
        fold(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.distinct()

    /** Lowercase, without diacritics, same length as [text] (one folded char per char, for index mapping). */
    fun fold(text: String): String = buildString(text.length) {
        for (c in text) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            append((base ?: c).lowercaseChar())
        }
    }

    /** Ranges (start until end) of the words of [text] that start with one of [words]. */
    fun matches(text: String, words: List<String>): List<IntRange> {
        if (words.isEmpty()) return emptyList()
        val folded = fold(text)
        val result = mutableListOf<IntRange>()
        var i = 0
        while (i < folded.length) {
            if (!folded[i].isLetterOrDigit()) {
                i++
                continue
            }
            var end = i
            while (end < folded.length && folded[end].isLetterOrDigit()) end++
            if (words.any { folded.startsWith(it, i) && it.length <= end - i }) result += i until end
            i = end
        }
        return result
    }

    /**
     * [text] (optionally after a [prefix] such as "Sender: " that is never cut) with the matches of [query] in
     * [highlight] style, shortened at the start when the first match is far in.
     */
    fun snippet(text: String, query: String, highlight: SpanStyle, prefix: String = ""): AnnotatedString {
        val body = if (prefix.isNotEmpty() && text.startsWith(prefix)) text.substring(prefix.length) else text
        val shownPrefix = if (prefix.isNotEmpty() && text.startsWith(prefix)) prefix else ""
        val ranges = matches(body, queryWords(query))
        var start = 0
        val first = ranges.firstOrNull()?.first ?: 0
        if (first > CONTEXT_BEFORE + 10) {
            start = first - CONTEXT_BEFORE
            // do not start in the middle of a word
            while (start < first && body[start].isLetterOrDigit() && body[start - 1].isLetterOrDigit()) start++
        }
        return buildAnnotatedString {
            append(shownPrefix)
            if (start > 0) append("…")
            var pos = start
            for (r in ranges) {
                if (r.first < start) continue
                append(body.substring(pos, r.first))
                pushStyle(highlight)
                append(body.substring(r.first, r.last + 1))
                pop()
                pos = r.last + 1
            }
            append(body.substring(pos))
        }
    }
}
