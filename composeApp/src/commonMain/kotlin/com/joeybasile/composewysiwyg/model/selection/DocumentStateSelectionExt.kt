package com.joeybasile.composewysiwyg.model.selection

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
import com.joeybasile.composewysiwyg.util.sliceRange

/*
To initialize selection via shift + left | right | up | down keys,
we must consider the global caret state. The information at selection start time
is required, as it is used as a reference origin.

We must consider the intial direction chosen, as that will dictate the shiftCaret,
which will be used as the reference origin when an update to the selection via shift + arrowkey combinations
is attempted during updateShiftArrowSelection()
 */

fun DocumentState.setAnchorCaret(anchor: SelectionCaretState) {
    selectionState = selectionState
        .copy(
            anchor = anchor
        )
}

fun DocumentState.setFocusCaret(focus: SelectionCaretState) {
    selectionState = selectionState
        .copy(
            focus = focus
        )
}

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

public fun DocumentState.rebuildSelectionFromAnchorAndFocus() {
    val a = selectionState.anchor!!  // immutable origin
    val f = selectionState.focus!!   // current frontier
    // Derive startField/startOffset from 'a'
    selectionState = selectionState.copy(
        segments = computeSegmentsBetween(a, f)
    )
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
fun DocumentState.clearSelectionIfActive() {
    if (selectionState.isActive) finishSelection()
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
// -- SEGMENT REBUILD -------------------------------------------------------

/**
 * Recomputes a list of SelectionSegment between two carets
 * across single or multiple fields in document order.
 */
private fun DocumentState.computeSegmentsBetween(
    anchor: SelectionCaretState,
    focus: SelectionCaretState
): List<SelectionSegment> {
    val segments = mutableListOf<SelectionSegment>()
    val boxCoords = parentCoordinates.value.box ?: return emptyList()
    val startFieldIdx = anchor.fieldIndex
    val endFieldIdx = focus.fieldIndex
    val startOff = anchor.offset
    val endOff = focus.offset

    if (startFieldIdx == endFieldIdx) {
        // same field
        val field = documentTextFieldList[startFieldIdx]
        val start = minOf(startOff, endOff)
        val end = maxOf(startOff, endOff)
        getFieldSelectionRect(field, start, end, boxCoords)?.let { rect ->
            segments.add(SelectionSegment(startFieldIdx, start, end, rect))
        }
    } else if (startFieldIdx < endFieldIdx) {
        // downward selection
        // start field: from anchor to EOL
        val startField = documentTextFieldList[startFieldIdx]
        val len0 = startField.textFieldValue.text.length
        getFieldSelectionRect(startField, startOff, len0, boxCoords)?.let {
            segments.add(SelectionSegment(startFieldIdx, startOff, len0, it))
        }
        // full middle fields
        for (i in (startFieldIdx + 1) until endFieldIdx) {
            val field = documentTextFieldList[i]
            val len = field.textFieldValue.text.length
            getFieldSelectionRect(field, 0, len, boxCoords)?.let { rect ->
                segments.add(SelectionSegment(i, 0, len, rect))
            }
        }
        // end field: from BOF to focus
        val endField = documentTextFieldList[endFieldIdx]
        getFieldSelectionRect(endField, 0, endOff, boxCoords)?.let {
            segments.add(SelectionSegment(endFieldIdx, 0, endOff, it))
        }
    } else {
        // upward selection
        // start field: from BOF to anchor
        val startField = documentTextFieldList[startFieldIdx]
        getFieldSelectionRect(startField, 0, startOff, boxCoords)?.let {
            segments.add(SelectionSegment(startFieldIdx, 0, startOff, it))
        }
        // middle fields
        for (i in (startFieldIdx - 1) downTo (endFieldIdx + 1)) {
            val field = documentTextFieldList[i]
            val len = field.textFieldValue.text.length
            getFieldSelectionRect(field, 0, len, boxCoords)?.let { rect ->
                segments.add(SelectionSegment(i, 0, len, rect))
            }
        }
        // end field: from focus to EOL
        val endField = documentTextFieldList[endFieldIdx]
        val lenEnd = endField.textFieldValue.text.length
        getFieldSelectionRect(endField, endOff, lenEnd, boxCoords)?.let {
            segments.add(SelectionSegment(endFieldIdx, endOff, lenEnd, it))
        }
    }
    return segments
}
// 1) A tiny data class to carry our merge info:
data class MergeResult(
    val startField: Int,
    val endField: Int,
    val merged: AnnotatedString,
    val collapseOffset: Int
)

// 2) Pull out the merge logic into its own function
fun DocumentState.mergeSelection(): MergeResult? {
    val a = selectionState.anchor ?: return null
    val f = selectionState.focus  ?: return null
    // normalize so start ≤ end in document order
    val (start, end) = if (
        a.fieldIndex < f.fieldIndex ||
        (a.fieldIndex == f.fieldIndex && a.offset <= f.offset)
    ) a to f else f to a

    val startIdx = start.fieldIndex
    val endIdx   = end.fieldIndex

    // grab the two AnnotatedStrings
    val startAS = documentTextFieldList[startIdx].textFieldValue.annotatedString
    val endAS   = documentTextFieldList[endIdx].textFieldValue.annotatedString

    // build a single AnnotatedString = prefix + suffix
    val merged = buildAnnotatedString {
        // prefix = everything up to the start offset
        append(startAS.sliceRange(0, start.offset))
        // suffix = tail of the end line, even if it's the same line
        append(endAS.sliceRange(end.offset, endAS.length))
    }

    return MergeResult(
        startField     = startIdx,
        endField       = endIdx,
        merged         = merged,
        collapseOffset = start.offset
    )
}

// 3) A unified, modular handleRemoveSelection
fun DocumentState.handleRemoveSelection() {
    // if there's no real selection, bail
    val merge = mergeSelection() ?: return

    // prepare the new TextFieldValue for the start field
    val oldValue = documentTextFieldList[merge.startField].textFieldValue
    val newValue = oldValue.copy(
        annotatedString = merge.merged,
        // collapse at the start of the removed region
        selection       = TextRange(merge.collapseOffset)
    )

    // 4) Use updateTextFieldValue so Compose re-renders this one field immediately
    updateTextFieldValue(merge.startField, newValue)

    // 5) Now remove any fully-deleted fields in reverse order
    if (merge.endField > merge.startField) {
        for (i in merge.endField downTo (merge.startField + 1)) {
            documentTextFieldList.removeAt(i)
        }
    }

    // 6) Clear out our selection state and sync everything
    finishSelection()
    onCaretMoved()
}

fun DocumentState.removeSelectedTextWithoutFinishingSelection(){
    // if there's no real selection, bail
    val merge = mergeSelection() ?: return

    // prepare the new TextFieldValue for the start field
    val oldValue = documentTextFieldList[merge.startField].textFieldValue
    val newValue = oldValue.copy(
        annotatedString = merge.merged,
        // collapse at the start of the removed region
        selection       = TextRange(merge.collapseOffset)
    )

    // 4) Use updateTextFieldValue so Compose re-renders this one field immediately
    updateTextFieldValue(merge.startField, newValue)

    // 5) Now remove any fully-deleted fields in reverse order
    if (merge.endField > merge.startField) {
        for (i in merge.endField downTo (merge.startField + 1)) {
            documentTextFieldList.removeAt(i)
        }
    }
    val original = caretState.value

    setAnchorCaret(
        SelectionCaretState(
            fieldIndex = original.fieldIndex,
            offset = original.offset,
            globalPosition = original.globalPosition
        )
    )
    setFocusCaret(
        SelectionCaretState(
            fieldIndex = original.fieldIndex,
            offset = original.offset,
            globalPosition = original.globalPosition
        )
    )
    selectionState = selectionState.copy(segments = emptyList())

}

/*
fun DocumentState.handleRemoveSelection() {
    // 1) grab and normalize carets
    val a = selectionState.anchor ?: return
    val f = selectionState.focus  ?: return
    val (start, end) = if (
        a.fieldIndex < f.fieldIndex ||
        (a.fieldIndex == f.fieldIndex && a.offset <= f.offset)
    ) a to f else f to a

    val startIdx = start.fieldIndex
    val endIdx   = end.fieldIndex

    // 2) pull out the two AnnotatedStrings
    val startAS = documentTextFieldList[startIdx].textFieldValue.annotatedString
    val endAS   = documentTextFieldList[endIdx].textFieldValue.annotatedString

    // 3) build a merged AnnotatedString without the selection
    val mergedAS = buildAnnotatedString {
        // everything before the selection on the start line
        append(startAS.subSequence(0, start.offset))
        if (startIdx == endIdx) {
            // single‐line: stitch in the tail of the same line
            append(startAS.subSequence(end.offset, endAS.length))
        } else {
            // multi‐line: stitch in the tail of the end line
            append(endAS.subSequence(end.offset, endAS.length))
        }
    }

    // 4) update the start field to the merged AS (and set caret there)
    val newValue = documentTextFieldList[startIdx].textFieldValue.copy(
        annotatedString = mergedAS,
        selection       = TextRange(start.offset)  // collapse at where ‘start’ was
    )
    documentTextFieldList[startIdx] =
        documentTextFieldList[startIdx].copy(textFieldValue = newValue)

    //updateTextFieldValue(startIdx, newValue)

    // 5) remove any fully‐deleted fields
    if (endIdx > startIdx) {
        for (i in endIdx downTo (startIdx + 1)) {
            documentTextFieldList.removeAt(i)
        }
    }

    // 6) move the global caret
    caretState.value = caretState.value.copy(
        fieldIndex = startIdx,
        offset     = newValue.selection.start
    )

    // 7) finish up
    finishSelection()
    onCaretMoved()
}
 */

/*
If you really must accept an Offset, just replace step 2 with:
val caretAtOffset = toSelectionCaret(globalOffset)
setFocusCaret(caretAtOffset)
 */
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

fun DocumentState.startDragSelection(globalOffset: Offset) {
    toSelectionCaret(globalOffset)?.let { dragCaret ->
        setAnchorCaret(dragCaret)
        setFocusCaret(dragCaret)
        rebuildSelectionFromAnchorAndFocus()
    }
}

fun DocumentState.updateDragSelection(globalOffset: Offset) {
    toSelectionCaret(globalOffset)?.let { dragCaret ->
        setFocusCaret(dragCaret)
        rebuildSelectionFromAnchorAndFocus()
    }
}

private fun DocumentState.toSelectionCaret(offset: Offset): SelectionCaretState? {
    val (fieldIdx, textOff) = findTextFieldAtPosition(offset) ?: return null
    val globalRect =
        getGlobalCursorRect(documentTextFieldList[fieldIdx], textOff, parentCoordinates.value.box!!)
            ?: return null
    return SelectionCaretState(fieldIdx, textOff, globalRect.topLeft)
}

private fun getFieldSelectionRect(
    field: DocumentTextFieldState,
    startOffset: Int,
    endOffset: Int,
    boxCoords: LayoutCoordinates
): Rect? {
    val layoutResult = field.textLayoutResult ?: return null
    val fieldCoords = field.layoutCoordinates ?: return null

    val safeStart = startOffset.coerceIn(0, layoutResult.layoutInput.text.length)
    val safeEnd = endOffset.coerceIn(0, layoutResult.layoutInput.text.length)

    if (safeStart == safeEnd) {
        val cursorRect = layoutResult.getCursorRect(safeStart)
        val globalTopLeft =
            boxCoords.localPositionOf(fieldCoords, Offset(cursorRect.left, cursorRect.top))
        return Rect(globalTopLeft, cursorRect.size)
    }

    val startBounds = layoutResult.getCursorRect(safeStart)
    val endBounds = layoutResult.getCursorRect(safeEnd)

    val localLeft = minOf(startBounds.left, endBounds.left)
    val localRight = maxOf(startBounds.right, endBounds.right)
    val localTop = minOf(startBounds.top, endBounds.top)
    val localBottom = maxOf(startBounds.bottom, endBounds.bottom)

    val globalTopLeft = boxCoords.localPositionOf(fieldCoords, Offset(localLeft, localTop))
    val globalBottomRight = boxCoords.localPositionOf(fieldCoords, Offset(localRight, localBottom))

    return Rect(
        left = globalTopLeft.x,
        top = globalTopLeft.y,
        right = globalBottomRight.x,
        bottom = globalBottomRight.y
    )
}

// 1) helper that makes a Rect just for a single char at index `i..i+1`
private fun getSingleCharRect(
    field: DocumentTextFieldState,
    charIndex: Int,
    boxCoords: LayoutCoordinates
): Rect? {
    val layoutResult = field.textLayoutResult ?: return null
    val fieldCoords = field.layoutCoordinates ?: return null

    // get the two cursor‐bounds bracketing this char
    val startBounds = layoutResult.getCursorRect(charIndex)
    val endBounds = layoutResult.getCursorRect(charIndex + 1)

    // union them to cover the full glyph
    val localLeft = minOf(startBounds.left, endBounds.left)
    val localRight = maxOf(startBounds.right, endBounds.right)
    val localTop = minOf(startBounds.top, endBounds.top)
    val localBottom = maxOf(startBounds.bottom, endBounds.bottom)

    // convert to Box’s coordinate space
    val globalTopLeft = boxCoords.localPositionOf(
        fieldCoords,
        Offset(localLeft, localTop)
    )
    val globalBottomRight = boxCoords.localPositionOf(
        fieldCoords,
        Offset(localRight, localBottom)
    )

    return Rect(
        left = globalTopLeft.x,
        top = globalTopLeft.y,
        right = globalBottomRight.x,
        bottom = globalBottomRight.y
    )
}

// 2) now loop from safeStart .. safeEnd-1
private fun getFieldSelectionRects(
    field: DocumentTextFieldState,
    startOffset: Int,
    endOffset: Int,
    boxCoords: LayoutCoordinates
): List<Rect> {
    val layoutResult = field.textLayoutResult ?: return emptyList()
    val textLength = layoutResult.layoutInput.text.length
    val safeStart = startOffset.coerceIn(0, textLength)
    val safeEnd = endOffset.coerceIn(0, textLength)

    // for a zero‐length selection, just show the caret
    if (safeStart == safeEnd) {
        getSingleCharRect(field, safeStart, boxCoords)?.let { return listOf(it) }
        return emptyList()
    }

    return (safeStart until safeEnd)
        .mapNotNull { charIndex ->
            getSingleCharRect(field, charIndex, boxCoords)
        }
}

//Finalize the selection when the drag ends.
fun DocumentState.finishSelection() {
    selectionState = selectionState.copy(anchor = null, focus = null, segments = emptyList())
}

//Optionally cancel the selection.
fun DocumentState.cancelSelection() {
    selectionState = selectionState.copy(segments = emptyList())
}

// Helper function: Given a global offset (in Box coordinates) determine which text field is touched.
private fun DocumentState.findTextFieldAtPosition(globalOffset: Offset): Pair<Int, Int>? {
    val boxCoords = parentCoordinates.value.box ?: return null
    documentTextFieldList.forEachIndexed { index, field ->
        val fieldCoords = field.layoutCoordinates ?: return@forEachIndexed
        // Convert the field’s top-left into Box coordinate system.
        val topLeft = boxCoords.localPositionOf(fieldCoords, Offset.Zero)
        val fieldRect =
            Rect(topLeft, Size(fieldCoords.size.width.toFloat(), fieldCoords.size.height.toFloat()))
        if (globalOffset in fieldRect) {
            // Now convert the global offset into local coordinates within the text field.
            // (Note: The conversion function is symmetric in Compose.)
            val localPos = fieldCoords.localPositionOf(boxCoords, globalOffset)
            val layoutResult = field.textLayoutResult ?: return@forEachIndexed
            val textOffset = layoutResult.getOffsetForPosition(localPos)
            return index to textOffset
        }
    }
    return null
}

// Helper function: Compute the global cursor rectangle for a specific text field and offset.
// This uses the existing parent Box coordinates for conversion.
fun DocumentState.getGlobalCursorRect(
    field: DocumentTextFieldState,
    offset: Int,
    boxCoords: LayoutCoordinates
): Rect? {
    val layoutResult = field.textLayoutResult ?: return null
    val fieldCoords = field.layoutCoordinates ?: return null
    val localRect = layoutResult.getCursorRect(offset)
    val globalTopLeft =
        boxCoords.localPositionOf(fieldCoords, Offset(localRect.left, localRect.top))
    return Rect(globalTopLeft, localRect.size)
}

// Called on Ctrl+A or via key handling to select everything.
fun DocumentState.selectAll() {
    // Activate selection mode so that the overlay is drawn.
    selectionState = selectionState.copy()

    // If no fields or parent coordinates exist, bail out.
    if (documentTextFieldList.isEmpty() || parentCoordinates.value.box == null) return

    val boxCoords = parentCoordinates.value.box!!
    val segments = mutableListOf<SelectionSegment>()
    // For each text field, select the entire field.
    documentTextFieldList.forEachIndexed { index, field ->
        // Make sure layoutCoordinates and textLayoutResult exist.
        if (field.layoutCoordinates == null || field.textLayoutResult == null) return@forEachIndexed
        // Full text bounds: from 0 to the length of the text.
        val textLength = field.textFieldValue.text.length

        getFieldSelectionRects(field, 0, textLength, boxCoords)
            .forEachIndexed { i, charRect ->
                segments.add(
                    SelectionSegment(
                        index,
                        i, //char start
                        i + 1, //char end
                        charRect
                    )
                )
            }

    }

    // Store the computed segments in the state so the overlay can draw them.
    selectionState = selectionState.copy(segments = segments)

}