package com.joeybasile.composewysiwyg.model.caret

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import com.joeybasile.composewysiwyg.model.DocumentState


fun DocumentState.moveCaretRight() {
    //println("```````````````````````````entered moveCaretRight()")
    val currentField = documentTextFieldList.getOrNull(caretState.value.fieldIndex) ?: return
    val textLength = currentField.textFieldValue.text.length
    /*
    If the current caret position is not at the end of the text field, then move right.
     */
    if (caretState.value.offset < textLength) {
        caretState.value = caretState.value.copy(offset = caretState.value.offset + 1)
    }
    /*
    If the current caret position IS at the end of the field AND there exists a text field below,
    then set the caret position to the beginning of said text field.
     */
    else if (caretState.value.fieldIndex + 1 < documentTextFieldList.size) {
        caretState.value = caretState.value.copy(
            fieldIndex = caretState.value.fieldIndex + 1,
            offset = 0
        )
    }
    onCaretMoved() // Call to synchronize focus and selection
    //println("```````````````````````````leaving moveCaretRight()")
}

fun DocumentState.moveCaretDown() {

    if (caretState.value.fieldIndex < documentTextFieldList.size - 1) {
        val nextField = documentTextFieldList[caretState.value.fieldIndex + 1]
        val nextLayoutResult = nextField.textLayoutResult ?: return
        val currentField = documentTextFieldList[caretState.value.fieldIndex]
        val layoutResult = currentField.textLayoutResult ?: return
        val offset = caretState.value.offset.coerceIn(0, layoutResult.layoutInput.text.length)
        val cursorRect = layoutResult.getCursorRect(offset)
        val x = cursorRect.left // Get the current caret's x-position


        // Find the offset in the next field at the same x-position
        val newOffset = nextLayoutResult.getOffsetForPosition(Offset(x, 0f))
        caretState.value = caretState.value.copy(
            fieldIndex = caretState.value.fieldIndex + 1,
            offset = newOffset
        )
    }
    //just move to the end of the line.
    else{
        val currentField = documentTextFieldList[caretState.value.fieldIndex]
        val currentLayoutResult = currentField.textLayoutResult ?: return
        caretState.value = caretState.value.copy(
            offset = currentLayoutResult.getLineEnd(0)
        )
    }
    onCaretMoved()
}


fun DocumentState.moveCaretLeft() {
    //println("``````````````````````````` entered moveCaretLeft()")
    /*
    inverse behavior of right
     */
    if (caretState.value.offset > 0) {
        caretState.value = caretState.value.copy(offset = caretState.value.offset - 1)
    } else if (caretState.value.fieldIndex > 0) {
        val previousField = documentTextFieldList[caretState.value.fieldIndex - 1]
        caretState.value = caretState.value.copy(
            fieldIndex = caretState.value.fieldIndex - 1,
            offset = previousField.textFieldValue.text.length
        )
    }
    onCaretMoved() // Call to synchronize focus and selection
    //println("```````````````````````````leaving moveCaretLeft()")
}

fun DocumentState.moveCaretUp() {
    //println("``````````````````````````` entering moveCaretUp()")
    if (caretState.value.fieldIndex > 0) {
        val currentField = documentTextFieldList[caretState.value.fieldIndex]
        val layoutResult = currentField.textLayoutResult ?: return
        val offset = caretState.value.offset.coerceIn(0, layoutResult.layoutInput.text.length)
        val cursorRect = layoutResult.getCursorRect(offset)
        val x = cursorRect.left

        // Use the current caret's vertical center (or another meaningful y value)
        val y = cursorRect.center.y

        val previousField = documentTextFieldList[caretState.value.fieldIndex - 1]
        val previousLayoutResult = previousField.textLayoutResult ?: return
        val newOffset = previousLayoutResult.getOffsetForPosition(Offset(x, y))
        caretState.value = caretState.value.copy(
            fieldIndex = caretState.value.fieldIndex - 1,
            offset = newOffset
        )
    }
    //just move to the beginning of the line.
    else {
        caretState.value = caretState.value.copy(
            offset = 0
        )
    }
    onCaretMoved() // Call to synchronize focus and selection
    //println("```````````````````````````leaving moveCaretUp()")
}

/*
What this does: After updating the global caret’s position and the focused field,
it sets the focused BTF’s textFieldValue.selection to match the global caret’s offset,
ensuring the local caret moves to the same position.
 */
fun DocumentState.onCaretMoved() {
    //println("```````````````````````````ENTERED onCaretMoved.")
    updateCaretPosition()// Updates the global caret position based on movement
    //println("^^^^^^^^^^^^updateCaretPosition JUST RETURNED.")
    val focusedIndex = caretState.value.fieldIndex
    updateFocusedLine(focusedIndex) // Sets focusedLine.value to the new fieldIndex
    val focusedField = documentTextFieldList[focusedIndex]
    val newTextFieldValue = focusedField.textFieldValue.copy(
        selection = TextRange(caretState.value.offset, caretState.value.offset)
    )
    documentTextFieldList[focusedIndex] = focusedField.copy(textFieldValue = newTextFieldValue)
    //println("```````````````````````````LEAVING onCaretMoved()")
}
fun DocumentState.updateCaretIndex(index: Int){
    val caret = caretState.value
    caretState.value = caret.copy(fieldIndex = index)
}
fun DocumentState.updateCaretPosition() {
    val caret = caretState.value
    //println("```````````````````````````IN updateCaretPosition(). line 448 ran. caret:${caret}")
    val field = documentTextFieldList.getOrNull(caret.fieldIndex) ?: return
    val layoutResult = field.textLayoutResult ?: return
    val fieldCoords = field.layoutCoordinates ?: return
    val boxCoords = parentCoordinates.value.box ?: return

    // Calculate local cursor position within the text field
    val localOffset = layoutResult.getCursorRect(caret.offset.coerceIn(0, layoutResult.layoutInput.text.length))
    val localX = localOffset.left
    val localY = localOffset.top
    //println("-------------updateCARETPOS X:$localX")
    //println("-------------updateCARETPOS y:$localY")

    //val offset = field.textLayoutResult?.getOffsetForPosition(Offset(localX, localY))
    //println("offset: $offset")


    // Convert to global coordinates relative to the Box
    val globalOffset = boxCoords.localPositionOf(fieldCoords, Offset(localX, localY))
    val height = layoutResult.getLineBottom(0) - layoutResult.getLineTop(0)

    // Update caret state with calculated values
    caretState.value = caret.copy(globalPosition = globalOffset, height = height.coerceAtLeast(16f))
    //println("```````````````````````````LEAVING updateCaretPosition()")
}