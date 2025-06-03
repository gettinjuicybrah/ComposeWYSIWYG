package com.joeybasile.composewysiwyg.model.selection.shiftArrowBased

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.selection.SelectionCaretState
import com.joeybasile.composewysiwyg.model.selection.finishSelection
import com.joeybasile.composewysiwyg.model.selection.getGlobalCursorRect
import com.joeybasile.composewysiwyg.model.selection.rebuildSelectionFromAnchorAndFocus
import com.joeybasile.composewysiwyg.model.selection.setAnchorCaret
import com.joeybasile.composewysiwyg.model.selection.setFocusCaret
import com.joeybasile.composewysiwyg.util.sliceRange

fun DocumentState.startShiftSelection(direction: DocumentEvent.Selection.Direction) {
    // If no anchor yet, this is “first arrow”
    if (selectionState.anchor == null) {
        // 1) Record the original caret as the anchor
        val original = caretState.value

        setAnchorCaret(
            SelectionCaretState(
                fieldIndex = original.fieldIndex,
                offset = original.offset,
                globalPosition = original.globalPosition
            )
        )

        // 2) Compute the very first focus one step over
        val firstFocus = computeAdjacentCaret(selectionState.anchor!!, direction)
        setFocusCaret(firstFocus)
    } else {
        // Subsequent arrows: just move the focus
        val prevFocus = selectionState.focus!!
        val newFocus = computeAdjacentCaret(prevFocus, direction)
        setFocusCaret(newFocus)
    }

    // 3) Rebuild ALL segments from the immutable anchor and fresh focus
    rebuildSelectionFromAnchorAndFocus()
}

fun DocumentState.updateShiftSelection(direction: DocumentEvent.Selection.Direction) {
    // 1) Ensure there’s an anchor; if not, defer to startShiftArrowSelection
    if (selectionState.anchor == null) {
        startShiftSelection(direction)
        return
    }

    // 2) Compute the new focus by moving one logical step
    val previousFocus = selectionState.focus!!
    val newFocus = computeAdjacentCaret(previousFocus, direction)
    setFocusCaret(newFocus)

    // 3) Rebuild all selection segments from the immutable anchor → new focus
    rebuildSelectionFromAnchorAndFocus()
}

private fun DocumentState.computeAdjacentCaret(
    from: SelectionCaretState,
    direction: DocumentEvent.Selection.Direction
): SelectionCaretState {
    return when (direction) {
        DocumentEvent.Selection.Direction.Left -> moveOneLeft(from)
        DocumentEvent.Selection.Direction.Right -> moveOneRight(from)
        DocumentEvent.Selection.Direction.Up -> moveOneUp(from)
        DocumentEvent.Selection.Direction.Down -> moveOneDown(from)
    }
}
/**
 * Given current selection’s anchor+focus, pick the boundary caret:
 * - Left/Up  → the “start” of the selection
 * - Right/Down → the “end” of the selection
 */
private fun DocumentState.getBoundaryCaret(
    direction: DocumentEvent.Selection.Direction
): SelectionCaretState {
    val a = selectionState.anchor ?: return selectionState.focus!!
    val f = selectionState.focus  ?: return a
    // determine document-order start & end
    val (start, end) = if (
        a.fieldIndex < f.fieldIndex ||
        (a.fieldIndex == f.fieldIndex && a.offset <= f.offset)
    ) a to f else f to a

    return when (direction) {
        DocumentEvent.Selection.Direction.Left,
        DocumentEvent.Selection.Direction.Up   -> start
        DocumentEvent.Selection.Direction.Right,
        DocumentEvent.Selection.Direction.Down -> end
    }
}
fun DocumentState.goArrowDir( direction: DocumentEvent.Selection.Direction){
    // 1) pick the “collapse” caret
    val boundary = getBoundaryCaret(direction)

    // 2) if Up/Down, nudge off that boundary into the next field/line
    val targetSelectionCaret = when (direction) {
        DocumentEvent.Selection.Direction.Up,
        DocumentEvent.Selection.Direction.Down ->
            computeAdjacentCaret(boundary, direction)
        else ->
            boundary
    }

    // 3) wipe out the selection entirely
    finishSelection()  // clears anchor, focus, & segments

    // 4) move the global caret to that spot
    caretState.value = caretState.value.copy(
        fieldIndex = targetSelectionCaret.fieldIndex,
        offset     = targetSelectionCaret.offset
    )

    // 5) sync everything (layout, local TextFieldValue, etc.)
    onCaretMoved()
}
// -- DIRECTIONAL MOVES -----------------------------------------------------

private fun DocumentState.moveOneLeft(from: SelectionCaretState): SelectionCaretState {
    val boxCoords = parentCoordinates.value.box ?: return from
    var (fieldIndex, offset) = from
    if (offset > 0) {
        offset--
    } else if (fieldIndex > 0) {
        // jump to end of previous field
        fieldIndex--
        val prevField = documentTextFieldList[fieldIndex]
        offset = prevField.textFieldValue.text.length
    }
    val rect = getGlobalCursorRect(
        documentTextFieldList[fieldIndex], offset, boxCoords
    ) ?: return from
    return SelectionCaretState(fieldIndex, offset, rect.topLeft)
}

private fun DocumentState.moveOneRight(from: SelectionCaretState): SelectionCaretState {
    val boxCoords = parentCoordinates.value.box ?: return from
    var (fieldIndex, offset) = from
    val field = documentTextFieldList[fieldIndex]
    val length = field.textFieldValue.text.length
    if (offset < length) {
        offset++
    } else if (fieldIndex < documentTextFieldList.lastIndex) {
        // jump to start of next field
        fieldIndex++
        offset = 0
    }
    val rect = getGlobalCursorRect(
        documentTextFieldList[fieldIndex], offset, boxCoords
    ) ?: return from
    return SelectionCaretState(fieldIndex, offset, rect.topLeft)
}

private fun DocumentState.moveOneUp(from: SelectionCaretState): SelectionCaretState {
    val boxCoords = parentCoordinates.value.box ?: return from
    var (fieldIndex, offset, global) = from
    val field = documentTextFieldList[fieldIndex]
    val layout = field.textLayoutResult ?: return from

    val currentLine = layout.getLineForOffset(offset)
    if (currentLine > 0) {
        // move within same field
        val x = global.x - boxCoords.localPositionOf(field.layoutCoordinates!!, Offset.Zero).x
        val targetY = layout.getLineTop(currentLine - 1)
        val newOffset = layout.getOffsetForPosition(Offset(x, targetY))
        val rect = getGlobalCursorRect(field, newOffset, boxCoords) ?: return from
        return SelectionCaretState(fieldIndex, newOffset, rect.topLeft)
    } else if (fieldIndex > 0) {
        // wrap to last line of previous field
        fieldIndex--
        val prevField = documentTextFieldList[fieldIndex]
        val prevLayout = prevField.textLayoutResult ?: return from
        val lastLine = prevLayout.lineCount - 1
        val xGlobal = global.x
        val localX =
            prevField.layoutCoordinates!!.localPositionOf(boxCoords, Offset(xGlobal, global.y)).x
        val targetY = prevLayout.getLineTop(lastLine)
        val newOffset = prevLayout.getOffsetForPosition(Offset(localX, targetY))
        val rect = getGlobalCursorRect(prevField, newOffset, boxCoords) ?: return from
        return SelectionCaretState(fieldIndex, newOffset, rect.topLeft)
    }
    return from
}

private fun DocumentState.moveOneDown(from: SelectionCaretState): SelectionCaretState {
    val boxCoords = parentCoordinates.value.box ?: return from
    var (fieldIndex, offset, global) = from
    val field = documentTextFieldList[fieldIndex]
    val layout = field.textLayoutResult ?: return from

    val currentLine = layout.getLineForOffset(offset)
    if (currentLine < layout.lineCount - 1) {
        // move within same field
        val x = global.x - boxCoords.localPositionOf(field.layoutCoordinates!!, Offset.Zero).x
        val targetY = layout.getLineBottom(currentLine)
        val newOffset = layout.getOffsetForPosition(Offset(x, targetY))
        val rect = getGlobalCursorRect(field, newOffset, boxCoords) ?: return from
        return SelectionCaretState(fieldIndex, newOffset, rect.topLeft)
    } else if (fieldIndex < documentTextFieldList.lastIndex) {
        // wrap to first line of next field
        fieldIndex++
        val nextField = documentTextFieldList[fieldIndex]
        val nextLayout = nextField.textLayoutResult ?: return from
        val xGlobal = global.x
        val localX =
            nextField.layoutCoordinates!!.localPositionOf(boxCoords, Offset(xGlobal, global.y)).x
        val targetY = nextLayout.getLineTop(0)
        val newOffset = nextLayout.getOffsetForPosition(Offset(localX, targetY))
        val rect = getGlobalCursorRect(nextField, newOffset, boxCoords) ?: return from
        return SelectionCaretState(fieldIndex, newOffset, rect.topLeft)
    }
    return from
}