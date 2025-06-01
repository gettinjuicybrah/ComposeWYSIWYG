package com.joeybasile.composewysiwyg.model.style

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Returns true if there is at least one SpanStyle covering [offset].
 */
fun AnnotatedString.hasSpanStyleAt(offset: Int): Boolean {
    if (offset < 0 || offset >= text.length) return false
    return spanStyles.any { offset in it.start until it.end }
}

/**
 * Returns the list of SpanStyles covering [offset], or empty if none.
 */
fun AnnotatedString.getSpanStylesAt(offset: Int): List<SpanStyle> {
    if (offset < 0 || offset >= text.length) return emptyList()
    return spanStyles
        .filter { offset in it.start until it.end }
        .map { it.item }
}

/**
 * Returns a new AnnotatedString with [styles] applied over [range].
 *
 * Note: IntRange is inclusive, so we add 1 to range.last to get the exclusive end.
 */
fun AnnotatedString.applySpanStyles(range: IntRange, styles: List<SpanStyle>): AnnotatedString {
    require(range.first >= 0 && range.last < text.length) {
        "Range $range is out of bounds for text of length ${text.length}"
    }
    return AnnotatedString.Builder(this).apply {
        styles.forEach { style ->
            addStyle(
                style = style,
                start = range.first,
                end = range.last + 1
            )
        }
    }.toAnnotatedString()
}

/**
 * Returns a new AnnotatedString with [styles] applied over the span [from, to).
 *
 * Note:
 *  - `from` must be ≥ 0.
 *  - `to` must be ≤ text.length.
 *  - `from` must be < `to` (non‐empty).
 */
fun AnnotatedString.applySpanStyles(
    from: Int,
    to: Int,
    styles: List<SpanStyle>
): AnnotatedString {
    require(from >= 0 && to <= text.length && from <= to) {
        "Invalid span: from=$from, to=$to, for text length=${text.length}"
    }

    return AnnotatedString.Builder(this).apply {
        styles.forEach { style ->
            // `addStyle` uses [start, end) semantics, so no +1 needed on `to`.
            addStyle(
                style = style,
                start = from,
                end = to
            )
        }
    }.toAnnotatedString()
}

/**
 * Adds a bold span to the specified [range] within this AnnotatedString.
 */
fun AnnotatedString.addBold(range: IntRange): AnnotatedString = buildAnnotatedString {
    // Clamp the range to valid bounds
    val start = range.first.coerceIn(0, this@addBold.length)
    val end = range.last.coerceIn(start, this@addBold.length)

    // Preserve existing text and styles
    append(this@addBold)

    // Apply bold style
    if (start < end) {
        addStyle(
            style = SpanStyle(fontWeight = FontWeight.Bold),
            start = start,
            end = end
        )
    }
}

/**
 * Returns a new AnnotatedString with all bold spans removed.
 */
fun AnnotatedString.removeBold(): AnnotatedString = buildAnnotatedString {
    // Append plain text
    append(this@removeBold.text)

    // Re-apply all span styles except bold
    this@removeBold.spanStyles.forEach { range ->
        if (range.item.fontWeight != FontWeight.Bold) {
            addStyle(
                style = range.item,
                start = range.start,
                end = range.end
            )
        }
    }


    // Re-apply string annotations (e.g., hyperlinks)
    this@removeBold.getStringAnnotations(0, this@removeBold.length).forEach { ann ->
        addStringAnnotation(
            tag = ann.tag,
            annotation = ann.item,
            start = ann.start,
            end = ann.end
        )
    }
}

/**
 * Adds an underline span to the specified [range] within this AnnotatedString.
 */
fun AnnotatedString.addUnderline(range: IntRange): AnnotatedString = buildAnnotatedString {
    val start = range.first.coerceIn(0, this@addUnderline.length)
    val end = range.last.coerceIn(start, this@addUnderline.length)

    append(this@addUnderline)

    if (start < end) {
        addStyle(
            style = SpanStyle(textDecoration = TextDecoration.Underline),
            start = start,
            end = end
        )
    }
}

/**
 * Returns a new AnnotatedString with all underline spans removed.
 */
fun AnnotatedString.removeUnderline(): AnnotatedString = buildAnnotatedString {
    append(this@removeUnderline.text)

    this@removeUnderline.spanStyles.forEach { range ->
        if (range.item.textDecoration != TextDecoration.Underline) {
            addStyle(
                style = range.item,
                start = range.start,
                end = range.end
            )
        }
    }


    this@removeUnderline.getStringAnnotations(0, this@removeUnderline.length).forEach { ann ->
        addStringAnnotation(
            tag = ann.tag,
            annotation = ann.item,
            start = ann.start,
            end = ann.end
        )
    }
}

/**
 * Adds a strikethrough span to the specified [range] within this AnnotatedString.
 */
fun AnnotatedString.addStrikethrough(range: IntRange): AnnotatedString = buildAnnotatedString {
    val start = range.first.coerceIn(0, this@addStrikethrough.length)
    val end = range.last.coerceIn(start, this@addStrikethrough.length)

    append(this@addStrikethrough)

    if (start < end) {
        addStyle(
            style = SpanStyle(textDecoration = TextDecoration.LineThrough),
            start = start,
            end = end
        )
    }
}

/**
 * Returns a new AnnotatedString with all strikethrough spans removed.
 */
fun AnnotatedString.removeStrikethrough(): AnnotatedString = buildAnnotatedString {
    append(this@removeStrikethrough.text)

    this@removeStrikethrough.spanStyles.forEach { range ->
        if (range.item.textDecoration != TextDecoration.LineThrough) {
            addStyle(
                style = range.item,
                start = range.start,
                end = range.end
            )
        }
    }



    this@removeStrikethrough.getStringAnnotations(0, this@removeStrikethrough.length)
        .forEach { ann ->
            addStringAnnotation(
                tag = ann.tag,
                annotation = ann.item,
                start = ann.start,
                end = ann.end
            )
        }
}
