/*
 * PSST (D80): the search query handed to the Rust SDK.
 *
 * The psst build of matrix-sdk-search (scripts/android/rust-sdk/psst-search.patch) indexes message bodies with
 * tantivy's default tokenizer plus ASCII folding, and matches every single query word as a word PREFIX. So the query
 * only has to be split into the same words the index uses: runs of letters and digits (tantivy's SimpleTokenizer
 * splits on everything that is not alphanumeric). Each word becomes a required clause (`+word`), so all words must
 * occur; lowercasing keeps `AND`/`OR`/`NOT` from being read as operators. Nothing else of tantivy's query syntax can
 * remain, because only letters and digits are left — no escaping needed, a pasted URL just becomes its words.
 *
 * Replaces upstream's escapeForTantivy (TantivyQueryEscaper.kt, kept unchanged for upstream merges): that one passed
 * whole tokens to the parser, so `e-mail` or `1/2` became phrases and a word typed without diacritics never matched.
 * PII: the query is never logged.
 */
package io.element.android.libraries.matrix.impl.search

import java.text.Normalizer

/** tantivy's RemoveLongFilter drops longer tokens from the index, so such a word could never match. */
private const val MAX_TOKEN_BYTES = 40

internal fun String.toPsstSearchQuery(): String =
    psstSearchWords(this).joinToString(separator = " ") { "+$it" }

/** The words of [query] as the index sees them (before folding, which the SDK's analyzer does). */
fun psstSearchWords(query: String): List<String> =
    Normalizer.normalize(query, Normalizer.Form.NFC)
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() && it.toByteArray().size <= MAX_TOKEN_BYTES }
