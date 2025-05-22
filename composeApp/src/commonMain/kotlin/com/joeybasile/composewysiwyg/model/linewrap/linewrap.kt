package com.joeybasile.composewysiwyg.model.linewrap

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.DocumentState

/**
 * Wraps text within a DocumentTextFieldState, splitting on newlines and width.
 * Each '\n' spawns a new field, and width overflow moves to the next line.
 */
/*
fun DocumentState.wrapTextField(
    index: Int,
    originalValue: TextFieldValue,
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    isFirstCall: Boolean = true
) {
    // Split into fitting and overflow (handles newlines and width)
    val (fitting, overflow) = splitAnnotatedString(
        fullText = originalValue.annotatedString,
        measurer = measurer,
        textStyle = textStyle,
        maxWidthPx = maxWidthPx
    )

    // Determine new selection for this field
    val newSelection = when {
        isFirstCall && originalValue.selection.start <= fitting.length -> TextRange(originalValue.selection.start)
        else -> null
    }

    // Update this field's text and optional caret
    val updated = if (newSelection != null) {
        originalValue.copy(annotatedString = fitting, selection = newSelection)
    } else {
        originalValue.copy(annotatedString = fitting)
    }
    updateTextFieldValue(index, updated)

    // No overflow => done
    if (overflow.isEmpty()) return
        // Ensure a next field exists
    val nextIndex = index + 1
    if (nextIndex >= documentTextFieldList.size) {
        documentTextFieldList.add(nextIndex, makeField(AnnotatedString("")))
    }

    // Prepare next field's TextFieldValue
    val base = documentTextFieldList[nextIndex].textFieldValue
    val caretOffset = if (isFirstCall) originalValue.selection.start - fitting.length else null
    val nextValue = base.copy(
        annotatedString = overflow + base.annotatedString,
        selection = caretOffset?.let { TextRange(it) } ?: base.selection
    )
    prependToFieldWithoutMovingCaret(nextIndex, nextValue)

    // Recurse for further wrapping (only width if no more newlines)
    wrapTextField(
        index = nextIndex,
        originalValue = nextValue,
        measurer = measurer,
        textStyle = textStyle,
        maxWidthPx = maxWidthPx,
        isFirstCall = false
    )
}
*/