package com.joeybasile.composewysiwyg.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString


fun AnnotatedString.sliceRange(start: Int, end: Int): AnnotatedString {
    // Clamp
    val s = start.coerceIn(0, length)
    val e = end.coerceIn(s, length)
    val newText = text.substring(s, e)
    val newSpans = spanStyles.mapNotNull { range ->
        val i0 = kotlin.math.max(range.start, s)
        val i1 = kotlin.math.min(range.end, e)
        if (i0 < i1) AnnotatedString.Range(
            item    = range.item,
            start   = i0 - s,
            end     = i1 - s
        ) else null
    }
    val newParas = paragraphStyles.mapNotNull { range ->
        val i0 = kotlin.math.max(range.start, s)
        val i1 = kotlin.math.min(range.end, e)
        if (i0 < i1) AnnotatedString.Range(
            item  = range.item,
            start = i0 - s,
            end   = i1 - s
        ) else null
    }
    val newStringAnnos = getStringAnnotations(s, e).map { it.copy(
        start      = it.start - s,
        end        = it.end   - s
    ) }
    return AnnotatedString(
        text            = newText,
        spanStyles      = newSpans,
        paragraphStyles = newParas
    )
}
/**
 * Returns a new AnnotatedString with the character at [index] removed.
 * Any SpanStyles or StringAnnotations will be shifted appropriately.
 */
public fun AnnotatedString.deleteCharAtCharIndex(index: Int): AnnotatedString {
    require(index in 0 until this.length) { "index $index out of bounds for length $length" }

    // 1) Compute the new plain text
    val newText = text.removeRange(index, index + 1)

    // 2) Rebuild an AnnotatedString with shifted ranges
    return buildAnnotatedString {
        append(newText)

        // Re‐apply all SpanStyles, adjusting start/end for the deleted char
        spanStyles.forEach { range ->
            val origStart = range.start
            val origEnd = range.end

            // If the span is entirely before the deletion, leave it
            // If it’s after, shift both by -1
            // If it crosses the deleted index, shrink its end by 1
            val newStart = if (origStart >= index + 1) origStart - 1 else origStart
            val newEnd   = when {
                origEnd <= index      -> origEnd
                origEnd > index       -> origEnd - 1
                else                   -> origEnd
            }

            if (newStart < newEnd) {
                addStyle(range.item, newStart, newEnd)
            }
        }

        // Re‐apply all StringAnnotations (e.g. tags for clickables), same logic
        getStringAnnotations(0, length).forEach { range ->
            val origStart = range.start
            val origEnd   = range.end

            val newStart = if (origStart >= index + 1) origStart - 1 else origStart
            val newEnd   = if (origEnd   > index)       origEnd   - 1 else origEnd

            if (newStart < newEnd) {
                addStringAnnotation(
                    tag   = range.tag,
                    annotation = range.item,
                    start = newStart,
                    end   = newEnd
                )
            }
        }
    }
}
/**
 * Returns a new AnnotatedString with the character immediately before
 * the given caretOffset removed. Caret offsets run 0…length, so
 * caretOffset == length deletes the last character.
 *
 * @param caretOffset the caret position (0…length)
 * @throws IllegalArgumentException if caretOffset is out of 1…length
 */
public fun AnnotatedString.deleteCharBeforeCaretOffset(caretOffset: Int): AnnotatedString {
    // caretOffset must be at least 1 (so there's a char before it) and at most length
    require(caretOffset in 0..this.length) {
        "caretOffset $caretOffset out of bounds for deletion on length $length"
    }

    // translate caretOffset into a character index, then delete
    val charIndex = caretOffset - 1
    return this.deleteCharAtCharIndex(charIndex)
}
/**
 * Returns a new AnnotatedString containing only the first character of this string,
 * and re-applies any SpanStyles or StringAnnotations that covered that character.
 */
fun AnnotatedString.keepFirstChar(): AnnotatedString = buildAnnotatedString {
    // if original is empty, just return empty
    if (this@keepFirstChar.isEmpty()) return@buildAnnotatedString

    // 1) Append the first char
    append(this@keepFirstChar.text[0])

    // 2) Re-apply SpanStyles
    this@keepFirstChar.spanStyles
        // keep only spans overlapping index 0
        .filter { it.start < 1 && it.end > 0 }
        .forEach { spanRange ->
            val start = maxOf(0, spanRange.start)
            val end   = minOf(1, spanRange.end)
            addStyle(
                style = spanRange.item,
                start = start,
                end = end
            )
        }

    // 3) Re-apply StringAnnotations
    this@keepFirstChar.getStringAnnotations(start = 0, end = this@keepFirstChar.length)
        .filter { it.start < 1 && it.end > 0 }
        .forEach { ann ->
            val start = maxOf(0, ann.start)
            val end   = minOf(1, ann.end)
            addStringAnnotation(
                tag        = ann.tag,
                annotation = ann.item,
                start      = start,
                end        = end
            )
        }
}

/**
 * Returns a new AnnotatedString that's the concatenation of this and [other],
 * preserving all SpanStyles and StringAnnotations.
 */
fun AnnotatedString.append(other: AnnotatedString): AnnotatedString = buildAnnotatedString {
    // Append all of this one's text + styling
    append(this@append)
    // Then append all of the other one's text + styling
    append(other)
}
/**
 * Returns a new AnnotatedString with the first character removed,
 * and all SpanStyles and StringAnnotations re-applied (shifted left by one).
 */
fun AnnotatedString.dropFirstChar(): AnnotatedString = buildAnnotatedString {
    // If there's 0 or 1 chars, result is empty
    if (this@dropFirstChar.text.length <= 1) return@buildAnnotatedString

    // 1) Append the text from index 1 onward
    val newText = this@dropFirstChar.text.substring(1)
    append(newText)

    // 2) Re-apply SpanStyles (shifting start/end by -1)
    this@dropFirstChar.spanStyles
        // keep any span that covered past the first character
        .filter { it.end > 1 }
        .forEach { span ->
            val newStart = (span.start - 1).coerceAtLeast(0)
            val newEnd   = (span.end   - 1).coerceAtMost(newText.length)
            if (newEnd > newStart) {
                addStyle(
                    style = span.item,
                    start = newStart,
                    end   = newEnd
                )
            }
        }

    // 3) Re-apply StringAnnotations (also shifting by -1)
    this@dropFirstChar.getStringAnnotations(start = 0, end = this@dropFirstChar.length)
        .filter { it.end > 1 }
        .forEach { ann ->
            val newStart = (ann.start - 1).coerceAtLeast(0)
            val newEnd   = (ann.end   - 1).coerceAtMost(newText.length)
            if (newEnd > newStart) {
                addStringAnnotation(
                    tag        = ann.tag,
                    annotation = ann.item,
                    start      = newStart,
                    end        = newEnd
                )
            }
        }
}