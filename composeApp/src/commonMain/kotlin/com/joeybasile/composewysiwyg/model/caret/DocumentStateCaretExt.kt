package com.joeybasile.composewysiwyg.model.caret

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.joeybasile.composewysiwyg.model.Block
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.Field
import com.joeybasile.composewysiwyg.model.getBlockById
import com.joeybasile.composewysiwyg.model.getFieldById
import com.joeybasile.composewysiwyg.model.getTextBlockById
import com.joeybasile.composewysiwyg.model.linewrap.isEmptyField
import com.joeybasile.composewysiwyg.model.style.CurrentCharStyle
import com.joeybasile.composewysiwyg.model.style.DefaultToolbarState
import com.joeybasile.composewysiwyg.model.style.ToolbarState
import com.joeybasile.composewysiwyg.model.style.getSpanStylesAt
import com.joeybasile.composewysiwyg.model.style.hasSpanStyleAt
import com.joeybasile.composewysiwyg.model.style.resetCurrentCharStyleToDefault
import com.joeybasile.composewysiwyg.model.style.resetToolbarToDefault
import com.joeybasile.composewysiwyg.model.updateFocusedBlock


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
    else {
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

IMPORTANT: focus can be updated.
 */
fun DocumentState.onCaretMoved() {
    //println("```````````````````````````ENTERED onCaretMoved.")
    updateCaretPosition()// Updates the global caret position based on movement
    //println("^^^^^^^^^^^^updateCaretPosition JUST RETURNED.")
    val focusedIndex = caretState.value.fieldIndex
    println("focusedIndex: $focusedIndex")
    println("about to call updateFocusedLine() from onCaretMoved()")
    updateFocusedLine(focusedIndex) // Sets focusedLine.value to the new fieldIndex
    val focusedField = documentTextFieldList[focusedIndex]
    val newTextFieldValue = focusedField.textFieldValue.copy(
        selection = TextRange(caretState.value.offset, caretState.value.offset)
    )
    documentTextFieldList[focusedIndex] = focusedField.copy(textFieldValue = newTextFieldValue)
    //println("```````````````````````````LEAVING onCaretMoved()")
    updateStylesOnCaretMoved()
}

fun DocumentState.updateCaretIndex(index: Int) {
    val caret = caretState.value
    caretState.value = caret.copy(fieldIndex = index)
}

fun DocumentState.updateGlobalCaretFieldId(fieldId: String) {
    globalCaret.value = globalCaret.value.copy(fieldId)
}

fun DocumentState.updateGlobalCaretBlockId(blockId: String) {
    globalCaret.value = globalCaret.value.copy(blockId)
}

fun DocumentState.updateGlobalCaretFieldAndBlockId(fieldId: String, blockId: String) {
    globalCaret.value = globalCaret.value.copy(fieldId = fieldId, blockId = blockId)
}


fun DocumentState.updateCaretPosition() {
    //println("entered updateCaretPosition().")
    val caret = caretState.value
    //println("```````````````````````````IN updateCaretPosition(). line 448 ran. caret:${caret}")
    val field = documentTextFieldList.getOrNull(caret.fieldIndex) ?: return
    val layoutResult = field.textLayoutResult ?: return
    val fieldCoords = field.layoutCoordinates ?: return
    val boxCoords = parentCoordinates.value.box ?: return

    // Calculate local cursor position within the text field
    val localOffset =
        layoutResult.getCursorRect(caret.offset.coerceIn(0, layoutResult.layoutInput.text.length))
    val localX = localOffset.left
    val localY = localOffset.top

    val globalOffset = boxCoords.localPositionOf(fieldCoords, Offset(localX, localY))
    val height = layoutResult.getLineBottom(0) - layoutResult.getLineTop(0)

    // Update caret state with calculated values
    caretState.value = caret.copy(globalPosition = globalOffset, height = height.coerceAtLeast(16f))
    //println("```````````````````````````LEAVING updateCaretPosition()")
    //updateStylesOnCaretMoved()
}

fun DocumentState.updateStylesOnCaretMoved() {
    println("updateStylesOnCaretMoved()")

    if (caretState.value.offset == 0 || isEmptyField(caretState.value.fieldIndex)) {
        println("caret offset 0 or is empty field.")
        //resetToolbarToDefault()
        //resetCurrentCharStyleToDefault()
        return
    }


    val currentFieldAS =
        documentTextFieldList[caretState.value.fieldIndex].textFieldValue.annotatedString
    val currentCharOffset = caretState.value.offset - 1
    if (!currentFieldAS.hasSpanStyleAt(currentCharOffset)) {
        //resetToolbarToDefault()
        //resetCurrentCharStyleToDefault()
        println("has default styling.")
        return
    }
    println("CUSTOM STYLING INBOUNDDDD")

    val spanStyles = currentFieldAS.getSpanStylesAt(currentCharOffset)
    //Now, here, we need a fxn where given a list of spanstyles, we mutate the toolbarstate and the currentcharstylestate to match the styles.

    // start from your default toolbar state as a SpanStyle
    val defaultTB = DefaultToolbarState()
    var merged = SpanStyle(
        fontFamily = defaultTB.font,
        fontSize = defaultTB.fontSize,
        color = defaultTB.textColor,
        background = defaultTB.textHighlightColor,
        fontWeight = if (defaultTB.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (defaultTB.isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = listOfNotNull(
            defaultTB.isUnderline.let { if (it) TextDecoration.Underline else null },
            defaultTB.isStrikethrough.let { if (it) TextDecoration.LineThrough else null }
        ).takeIf { it.isNotEmpty() }?.let { TextDecoration.combine(it) }
    )

    // merge in each SpanStyle override
    spanStyles.forEach { merged = merged.merge(it) }

    // 3) peel back into your two states
    val newToolbar = ToolbarState(
        font = merged.fontFamily,
        fontSize = merged.fontSize,
        textColor = merged.color,
        textHighlightColor = merged.background,
        isBold = merged.fontWeight == FontWeight.Bold,
        isItalic = merged.fontStyle == FontStyle.Italic,
        isUnderline = merged.textDecoration
            ?.contains(TextDecoration.Underline) == true,
        isStrikethrough = merged.textDecoration
            ?.contains(TextDecoration.LineThrough) == true
    )

    val newCharStyle = CurrentCharStyle(
        font = merged.fontFamily,
        fontSize = merged.fontSize,
        textColor = merged.color,
        textHighlightColor = merged.background,
        isBold = merged.fontWeight == FontWeight.Bold,
        isItalic = merged.fontStyle == FontStyle.Italic,
        isUnderline = merged.textDecoration
            ?.contains(TextDecoration.Underline) == true,
        isStrikethrough = merged.textDecoration
            ?.contains(TextDecoration.LineThrough) == true
    )

    toolbarState.value = newToolbar
    currentCharStyle.value = newCharStyle
}


fun DocumentState.updateToolbarStateOnCaretMoved() {

}

fun DocumentState.updateCurrentCharStyleStateOnCaretMoved() {

}