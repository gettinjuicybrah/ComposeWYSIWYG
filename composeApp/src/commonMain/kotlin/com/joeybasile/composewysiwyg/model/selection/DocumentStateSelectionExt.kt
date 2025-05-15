package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState

/*
To initialize selection via shift + left | right | up | down keys,
we must consider the global caret state. The information at selection start time
is required, as it is used as a reference origin.

We must consider the intial direction chosen, as that will dictate the shiftCaret,
which will be used as the reference origin when an update to the selection via shift + arrowkey combinations
is attempted during updateShiftArrowSelection()
 */

fun DocumentState.setHeadCaret(headSelectionCaretState: HeadSelectionCaretState) {
    selectionState = selectionState
        .copy(
            headCaret = headSelectionCaretState
        )
}
fun DocumentState.setTailCaret(tailSelectionCaretState: TailSelectionCaretState) {
    selectionState = selectionState
        .copy(
            tailCaret = tailSelectionCaretState
        )
}
//nop because can't do shit homie.
fun DocumentState.processShiftArrowLeftWhenAtRoot(){
    return
}
/*
Aye bruh all we finna do is get that fuckin rect to the left and set the fuckin state you feel
 */
fun DocumentState.processShiftArrowLeftToSameFieldFromNonZeroOffset(){

}
//just render a white space in above field.
fun DocumentState.processShiftArrowLeftToAboveFieldContainingEmpty(){

}
//just add the last char's rect as selection
fun DocumentState.processShiftArrowLeftToAboveFieldLastChar(){

}
//aye can't do shit folk
fun DocumentState.processShiftArrowRightWhenAtLeaf(){
    return
}
fun DocumentState.processShiftArrowRightToSameFieldFromNonMaxOffset(){

}
fun DocumentState.processShiftArrowRightToBelowFieldContainingEmpty(){

}
fun DocumentState.processShiftArrowRightTOBelowFieldFirstChar(){

}

/*
Whether or not the global caret is at the root.
 */
fun DocumentState.determineIfAtRootOfDoc(): Boolean{
    return caretState.value.offset == 0 && caretState.value.fieldIndex == 0
}

fun DocumentState.startShiftArrowSelection(direction: DocumentEvent.Selection.Direction) {
    when (direction) {
        /*
        Must consider 'given the current global caret position, the current field content, the current field position,
        and surrounding field content and positions, determine if the move is appropriate to intialize selection.

        If not, return.
        If so, initialize as appropriate, given the above mentioned context.
        So, initialize the SelectionState, dependent upon the caret state and **Deemed relevant, depending on direction** field states.

    3 cases:
    1) "root" -> nop
    2) currentFieldIndex > 0 && currenFieldOffset == 0 -> select last offset of above line or an imaginary white space to the right of last offset
        the headCaret would be set to the getCursorRect(maxOffset) of the above field. Need to somehow generate this imaginate white space.
    3) offset > 0 -> select immediate left rect to highlight.

         */
        DocumentEvent.Selection.Direction.Left -> {

            val segments = mutableListOf<SelectionSegment>()
            /*
            Case 1.
             */
            if (determineIfAtRootOfDoc()) return
            // remember where we started
            val initialFieldIndex = caretState.value.fieldIndex
            val initialOffset = caretState.value.offset
            val currentField = documentTextFieldList[initialFieldIndex]

            /*
            Case 2.
             */
            if (initialFieldIndex > 0 && initialOffset == 0) {

                val aboveIndex = initialFieldIndex -1
                val aboveMaxOffset = documentTextFieldList[aboveIndex].textFieldValue.text.length
                val aboveField = documentTextFieldList[aboveIndex]
                //Is NOT shift+Arrow origin caret because the direction moved left.
                val tailCaret = TailSelectionCaretState(
                    fieldIndex = caretState.value.fieldIndex,
                    offset = caretState.value.offset,
                    globalPosition = caretState.value.globalPosition,
                    isShiftPlusArrowOriginCaret = false
                )
                val localOffset = aboveField.textLayoutResult?.getCursorRect(aboveMaxOffset)
                val localX = localOffset?.left
                val localY = localOffset?.top
                val globalOffset = parentCoordinates.value.box?.localPositionOf(aboveField.layoutCoordinates!!, Offset(localX!!, localY!!))

                val headCaret = TailSelectionCaretState(
                    fieldIndex = initialFieldIndex - 1,
                    offset = aboveMaxOffset,
                    globalPosition = globalOffset!!,
                    isShiftPlusArrowOriginCaret = true
                )
                //val fillerRect = documentTextFieldList[initialFieldIndex-1].textLayoutResult?.layoutInput?.let { measureCharRect(' ', it.style) }
                val fillerRect = aboveField.textLayoutResult?.getBoundingBox(aboveMaxOffset-1)
                //First, we'll need to get the rect for the last offset in the above field.
                //perhaps it's just a 'filler' white space. The other option, at the current state of the app, is it being
                // a styled char. So, we should tend to both cases.
                segments.add(
                    SelectionSegment(
                        0, 0, 0, fillerRect!!
                )
                )
            }

            /*
            Case 3.
             */
            if (initialOffset > 0) {

                //Is NOT shift+Arrow origin caret because the direction moved left.
                val tailCaret = TailSelectionCaretState(
                    fieldIndex = caretState.value.fieldIndex,
                    offset = caretState.value.offset,
                    globalPosition = caretState.value.globalPosition,
                    isShiftPlusArrowOriginCaret = false
                )
                val localOffset = currentField.textLayoutResult?.getCursorRect(initialOffset-1)
                val localX = localOffset?.left
                val localY = localOffset?.top
                val globalOffset = parentCoordinates.value.box?.localPositionOf(currentField.layoutCoordinates!!, Offset(localX!!, localY!!))
                val headCaret = HeadSelectionCaretState(
                    fieldIndex = caretState.value.fieldIndex,
                    offset = caretState.value.offset-1,
                    globalPosition = globalOffset!!,
                    isShiftPlusArrowOriginCaret = true
                )
                val segment = currentField.textLayoutResult?.getBoundingBox(caretState.value.offset-1)
                segments.add(SelectionSegment(
                    0, 0, 0, segment!!)
                )
                selectionState = selectionState.copy(
                    isActive = true,
                    startField = initialFieldIndex,
                    startOffset = initialOffset,
                    segments = segments,
                    headCaret = headCaret,
                    tailCaret = tailCaret
                )
            }

        }

        DocumentEvent.Selection.Direction.Right -> TODO()
        DocumentEvent.Selection.Direction.Up -> TODO()
        DocumentEvent.Selection.Direction.Down -> TODO()
    }

}
/*
@OptIn(ExperimentalTextApi::class)
@Composable
fun measureCharRect(
    char: Char,
    style: TextStyle
): Rect {
    // 1) remember your measurer
    val measurer = rememberTextMeasurer()

    // 2) do the synchronous measurement right in your composable—
    //    this will automatically re-execute whenever `char` or `style` change
    val layout = measurer.measure(
        text = char.toString(),
        style = style
    )

    // 3) wrap the IntSize in a Rect and return it
    return Rect(
        offset = Offset.Zero,
        size = Size(
            width  = layout.size.width.toFloat(),
            height = layout.size.height.toFloat()
        )
    )
}
*/
fun DocumentState.updateShiftArrowSelection(globalOffset: Offset) {

}


//Determine which text field and offset correspond to the drag start.
fun DocumentState.startSelection(globalOffset: Offset) {
    selectionState = selectionState.copy(isActive = true)
    val pos = findTextFieldAtPosition(globalOffset)
    if (pos != null) {

        selectionState = selectionState.copy(startField = pos.first, startOffset = pos.second)
        updateSelection(globalOffset)
    }
    // Additional logic for starting selection…
}

// Modified updateSelection: Compute a list of selection segments
fun DocumentState.updateSelection(globalOffset: Offset) {

    if (selectionState.startField == null || selectionState.startOffset == null) return

    val pos = findTextFieldAtPosition(globalOffset) ?: return
    val (currentFieldIndex, currentOffset) = pos
    val boxCoords = parentCoordinates.value.box ?: return

    val segments = mutableListOf<SelectionSegment>()
    val startFieldIdx = selectionState.startField!!
    val endFieldIdx = currentFieldIndex

    //If the starting field is the same as the current field.
    if (startFieldIdx == endFieldIdx) {
        val field = documentTextFieldList[startFieldIdx]
        val start = minOf(selectionState.startOffset!!, currentOffset)
        val end = maxOf(selectionState.startOffset!!, currentOffset)
        val rect = getFieldSelectionRect(field, start, end, boxCoords) ?: return
        segments.add(SelectionSegment(startFieldIdx, start, end, rect))
    } else {
        //Selection is going downwards.
        if (startFieldIdx < endFieldIdx) {
            // Start field: from selectionStartOffset to end of field
            val startField = documentTextFieldList[startFieldIdx]
            val fieldLength = startField.textFieldValue.text.length
            val rectStart = getFieldSelectionRect(
                startField,
                selectionState.startOffset!!,
                fieldLength,
                boxCoords
            )
            rectStart?.let {
                segments.add(
                    SelectionSegment(
                        startFieldIdx,
                        selectionState.startOffset!!,
                        fieldLength,
                        it
                    )
                )
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
        }
        //Selection is going upwards.
        else {
           // Start field: from beginning of field to selectionStartOffset
            val startField = documentTextFieldList[startFieldIdx]
            val rectStart =
                getFieldSelectionRect(startField, 0, selectionState.startOffset!!, boxCoords)
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
    selectionState = selectionState.copy(isActive = false, startField = null, startOffset = null)
}

//Optionally cancel the selection.
fun DocumentState.cancelSelection() {
    selectionState = selectionState.copy(isActive = false, segments = emptyList())
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