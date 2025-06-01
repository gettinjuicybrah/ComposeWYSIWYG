package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.ui.text.AnnotatedString
import com.joeybasile.composewysiwyg.model.DocumentState

/**
 * Returns a pair of (startCaret, endCaret) in document order.
 * Returns null if selection is not active or carets are missing.
 */
fun DocumentState.getOrderedSelectionCarets(): Pair<SelectionCaretState, SelectionCaretState>? {
    val anchor = selectionState.anchor ?: return null
    val focus = selectionState.focus ?: return null

    return if (anchor.fieldIndex < focus.fieldIndex ||
        (anchor.fieldIndex == focus.fieldIndex && anchor.offset <= focus.offset)
    ) {
        anchor to focus
    } else {
        focus to anchor
    }
}

/**
 * Extracts the combined AnnotatedString from the current selection.
 * Preserves existing styles.
 */
fun DocumentState.extractSelectedAnnotatedString(
    startCaret: SelectionCaretState,
    endCaret: SelectionCaretState
): AnnotatedString {
    val builder = androidx.compose.ui.text.buildAnnotatedString {
        for (fieldIdx in startCaret.fieldIndex..endCaret.fieldIndex) {
            val field = documentTextFieldList[fieldIdx]
            val textToAppend = when (fieldIdx) {
                startCaret.fieldIndex -> {
                    if (startCaret.fieldIndex == endCaret.fieldIndex) { // Single field selection
                        field.textFieldValue.annotatedString.subSequence(startCaret.offset, endCaret.offset)
                    } else { // Start of multi-field selection
                        field.textFieldValue.annotatedString.subSequence(startCaret.offset, field.textFieldValue.annotatedString.length)
                    }
                }
                endCaret.fieldIndex -> { // End of multi-field selection
                    field.textFieldValue.annotatedString.subSequence(0, endCaret.offset)
                }
                else -> { // Full field in middle of selection
                    field.textFieldValue.annotatedString
                }
            }
            append(textToAppend)
            // If not the last field in selection and the field itself has a newline, or if we are taking the full field, append a newline.
            // This ensures multi-line pasted text maintains its structure.
            // This logic might need refinement based on how explicit newlines are handled globally.
            // For now, we assume newlines are part of the AnnotatedString's text.
            if (fieldIdx < endCaret.fieldIndex && field.hasNewLineAtEnd && textToAppend == field.textFieldValue.annotatedString) {
                // If we took the whole line and it's marked with newline, and it's not the very last field of selection
                // append('\n') // This adds a LITERAL newline character.
                // If hasNewLineAtEnd is purely for visual breaking, this might be wrong.
                // Let's assume selectedAS should reflect content including its inherent newlines for now.
            }
        }
    }
    return builder
}