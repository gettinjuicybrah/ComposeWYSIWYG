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

public fun DocumentState.rebuildSelectionFromAnchorAndFocus() {
    val a = selectionState.anchor!!  // immutable origin
    val f = selectionState.focus!!   // current frontier
    // Derive startField/startOffset from 'a'
    selectionState = selectionState.copy(
        segments = computeSegmentsBetween(a, f)
    )
}
fun DocumentState.finishSelection() {
    selectionState = selectionState.copy(anchor = null, focus = null, segments = emptyList())
}

fun DocumentState.cancelSelection() {
    selectionState = selectionState.copy(segments = emptyList())
}
fun DocumentState.clearSelectionIfActive() {
    if (selectionState.isActive) finishSelection()
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

data class MergeResult(
    val startField: Int,
    val endField: Int,
    val merged: AnnotatedString,
    val collapseOffset: Int
)

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

fun DocumentState.toSelectionCaret(offset: Offset): SelectionCaretState? {
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

fun DocumentState.getGlobalCursorRect(
    field: DocumentTextFieldState,
    offset: Int,
    boxCoords: LayoutCoordinates
): Rect? {
    println("************************************************&&&&&&&&&&&&******************* WITHIN GETGLOBAL CURSOR RECT LINE 662. OFFSET: $offset FIELD TEXT LENGTH: ${field.textFieldValue.text.length} AND &&&&& FIELD TEXTLAYOUTRESULT LENGTH: ${field.textLayoutResult?.size!!}")

    val layoutResult = field.textLayoutResult ?: return null
    val fieldCoords = field.layoutCoordinates ?: return null
    if(offset > field.textLayoutResult!!.layoutInput.text.length)return null
    println("999999999999999999999999999999999999999999999999999 current field: ${field.textFieldValue.text} AND offset: ${offset } AND layoutResult.getCursorRect(offset): ${layoutResult.getCursorRect(offset)}")
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