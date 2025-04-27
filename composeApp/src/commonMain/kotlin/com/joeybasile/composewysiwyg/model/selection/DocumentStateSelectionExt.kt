package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState

//Determine which text field and offset correspond to the drag start.
fun DocumentState.startSelection(globalOffset: Offset){
    selectionState = selectionState.copy(isActive = true )
    val pos = findTextFieldAtPosition(globalOffset)
    if (pos != null) {

        selectionState = selectionState.copy(startField = pos.first, startOffset = pos.second)
        updateSelection(globalOffset)
    }
    // Additional logic for starting selection…
}
// Modified updateSelection: Compute a list of selection segments
fun DocumentState.updateSelection(globalOffset: Offset) {

    if(selectionState.startField == null || selectionState.startOffset == null) return

    val pos = findTextFieldAtPosition(globalOffset) ?: return
    val (currentFieldIndex, currentOffset) = pos
    val boxCoords = parentCoordinates.value.box ?: return

    val segments = mutableListOf<SelectionSegment>()
    val startFieldIdx = selectionState.startField!!
    val endFieldIdx = currentFieldIndex

    if (startFieldIdx == endFieldIdx) {
        val field = documentTextFieldList[startFieldIdx]
        val start = minOf(selectionState.startOffset!!, currentOffset)
        val end = maxOf(selectionState.startOffset!!, currentOffset)
        val rect = getFieldSelectionRect(field, start, end, boxCoords) ?: return
        segments.add(SelectionSegment(startFieldIdx, start, end, rect))
    } else {
        if (startFieldIdx < endFieldIdx) {
            // Selection going downwards:
            // Start field: from selectionStartOffset to end of field
            val startField = documentTextFieldList[startFieldIdx]
            val fieldLength = startField.textFieldValue.text.length
            val rectStart = getFieldSelectionRect(startField, selectionState.startOffset!!, fieldLength, boxCoords)
            rectStart?.let {
                segments.add(SelectionSegment(startFieldIdx, selectionState.startOffset!!, fieldLength, it))
            }

            // Middle fields (if any): entire field selected
            for (idx in (startFieldIdx + 1) until endFieldIdx) {
                val field = documentTextFieldList[idx]
                val textLen = field.textFieldValue.text.length
                val rectMid = getFieldSelectionRect(field, 0, textLen, boxCoords)
                rectMid?.let {
                    segments.add(SelectionSegment(idx, 0, textLen, it))
                }
            }

            // End field: from beginning of field to currentOffset
            val endField = documentTextFieldList[endFieldIdx]
            val rectEnd = getFieldSelectionRect(endField, 0, currentOffset, boxCoords)
            rectEnd?.let {
                segments.add(SelectionSegment(endFieldIdx, 0, currentOffset, it))
            }
        } else {
            // Selection going upwards:
            // Start field: from beginning of field to selectionStartOffset
            val startField = documentTextFieldList[startFieldIdx]
            val rectStart = getFieldSelectionRect(startField, 0, selectionState.startOffset!!, boxCoords)
            rectStart?.let {
                segments.add(SelectionSegment(startFieldIdx, 0, selectionState.startOffset!!, it))
            }

            // Middle fields (if any)
            for (idx in (startFieldIdx - 1) downTo (endFieldIdx + 1)) {
                val field = documentTextFieldList[idx]
                val textLen = field.textFieldValue.text.length
                val rectMid = getFieldSelectionRect(field, 0, textLen, boxCoords)
                rectMid?.let {
                    segments.add(SelectionSegment(idx, 0, textLen, it))
                }
            }

            // End field: from currentOffset to end of field
            val endField = documentTextFieldList[endFieldIdx]
            val fieldLength = endField.textFieldValue.text.length
            val rectEnd = getFieldSelectionRect(endField, currentOffset, fieldLength, boxCoords)
            rectEnd?.let {
                segments.add(SelectionSegment(endFieldIdx, currentOffset, fieldLength, it))
            }
        }
    }

    selectionState = selectionState.copy(segments = segments)
}

// Helper function remains mostly the same
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
        val globalTopLeft = boxCoords.localPositionOf(fieldCoords, Offset(cursorRect.left, cursorRect.top))
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

//Finalize the selection when the drag ends.
fun DocumentState.finishSelection(){
    selectionState = selectionState.copy(isActive = false, startField = null, startOffset = null)
}
//Optionally cancel the selection.
fun DocumentState.cancelSelection(){
    selectionState = selectionState.copy(isActive = false, segments = emptyList())
}

// Helper function: Given a global offset (in Box coordinates) determine which text field is touched.
private fun DocumentState.findTextFieldAtPosition(globalOffset: Offset): Pair<Int, Int>? {
    val boxCoords = parentCoordinates.value.box ?: return null
    documentTextFieldList.forEachIndexed { index, field ->
        val fieldCoords = field.layoutCoordinates ?: return@forEachIndexed
        // Convert the field’s top-left into Box coordinate system.
        val topLeft = boxCoords.localPositionOf(fieldCoords, Offset.Zero)
        val fieldRect = Rect(topLeft, Size(fieldCoords.size.width.toFloat(), fieldCoords.size.height.toFloat()))
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
private fun DocumentState.getGlobalCursorRect(field: DocumentTextFieldState, offset: Int, boxCoords: LayoutCoordinates): Rect? {
    val layoutResult = field.textLayoutResult ?: return null
    val fieldCoords = field.layoutCoordinates ?: return null
    val localRect = layoutResult.getCursorRect(offset)
    val globalTopLeft = boxCoords.localPositionOf(fieldCoords, Offset(localRect.left, localRect.top))
    return Rect(globalTopLeft, localRect.size)
}
// Called on Ctrl+A or via key handling to select everything.
fun DocumentState.selectAll() {
    // Activate selection mode so that the overlay is drawn.
    selectionState = selectionState.copy(isActive = true)

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
        val rect = getFieldSelectionRect(field, 0, textLength, boxCoords)
        rect?.let {
            segments.add(SelectionSegment(index, 0, textLength, it))
        }
    }

    // Store the computed segments in the state so the overlay can draw them.
    selectionState = selectionState.copy(segments = segments)

}