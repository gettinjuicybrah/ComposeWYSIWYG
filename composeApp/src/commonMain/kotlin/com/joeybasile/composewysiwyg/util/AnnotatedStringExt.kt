package com.joeybasile.composewysiwyg.util

import androidx.compose.ui.text.AnnotatedString


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