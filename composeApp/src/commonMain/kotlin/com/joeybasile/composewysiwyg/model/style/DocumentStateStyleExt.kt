package com.joeybasile.composewysiwyg.model.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState

/*
These are events sent from Toolbar composable to the DocumentState.
 */

fun DocumentState.doToggle(charStyleType: DocumentEvent.CharStyleType){
    when(charStyleType){
        DocumentEvent.CharStyleType.BOLD -> toggleBold()
        DocumentEvent.CharStyleType.ITALIC -> toggleItalic()
        DocumentEvent.CharStyleType.UNDERLINE -> toggleUnderline()
        DocumentEvent.CharStyleType.STRIKETHROUGH -> toggleStrikethrough()
    }
}

/*
If NOT Selecting:
Style updates will mutate the state.currentCharStyleState, which will in turn mutate the state.toolbarState, because it's source of truth is
the currentCharStyleState when we are not selecting.

If isSelecting:
 */
fun DocumentState.toggleBold(){
    if(selectionState.isActive){
        println("selection is active with BOLD")
    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val charBool = currentCharStyleState.isBold
        val currentToolbarState = toolbarState.value
        val toolBool = currentToolbarState.isBold
        currentCharStyle.value = currentCharStyleState.copy(isBold = !charBool)
        toolbarState.value = currentToolbarState.copy(isBold = !toolBool)
        println("selection NOT ACTIVE with BOLD")
        println("char bool: $charBool")
        println("tool bool: $toolBool")
    }
}
fun DocumentState.toggleItalic(){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(isItalic = !currentCharStyleState.isItalic)
        toolbarState.value = currentToolbarState.copy(isItalic = !currentToolbarState.isItalic)
    }
}
fun DocumentState.toggleUnderline(){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(isUnderline = !currentCharStyleState.isUnderline)
        toolbarState.value = currentToolbarState.copy(isUnderline = !currentToolbarState.isUnderline)
    }
}
fun DocumentState.toggleStrikethrough(){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(isStrikethrough = !currentCharStyleState.isStrikethrough)
        toolbarState.value = currentToolbarState.copy(isStrikethrough = !currentToolbarState.isStrikethrough)
    }
}
fun DocumentState.setTextColor(color: Color){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(textColor = color)
        toolbarState.value = currentToolbarState.copy(textColor = color)
    }
}
fun DocumentState.setTextHighlightColor(color: Color){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(textHighlightColor = color)
        toolbarState.value = currentToolbarState.copy(textHighlightColor = color)
    }
}
//Not entirely sure on how fonts work, yet. Don't even know if these are the proper types.
fun DocumentState.setFont(font: FontFamily){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(font = font)
        toolbarState.value = currentToolbarState.copy(font = font)
    }
}

fun DocumentState.setFontSize(fontSize: TextUnit){
    if(selectionState.isActive){

    }
    else{
        val currentCharStyleState = currentCharStyle.value
        val currentToolbarState = toolbarState.value
        currentCharStyle.value = currentCharStyleState.copy(fontSize = fontSize)
        toolbarState.value = currentToolbarState.copy(fontSize = fontSize)
    }
}

fun DocumentState.pasteIntoFieldAtCaretOffsetNoNewlinesConsidered(
    initialFieldIndex: Int,
    initialCaretOffset: Int,
    annotatedStringToBePasted: AnnotatedString,
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
){
    //1 First, split the existing annotatedString into L and R.
    val initialString = documentTextFieldList[initialFieldIndex].textFieldValue.annotatedString
    val initialLength = initialString.length
    val leftInitialString = initialString.subSequence(0, initialCaretOffset)
    val rightInitialString = initialString.subSequence(0, initialLength)

    //2 Now, let's build a new string with the 'annotatedStringToBePasted' in the center.
    val updatedString = buildAnnotatedString {
        append(leftInitialString)
        append(annotatedStringToBePasted)
        append(rightInitialString)
    }

    //3 Now that we have this updated string with our to-be-pasted content inn the center, let's measure it and see if any OF occured.
    val result = measurer.measure(
        text = updatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 1,
        softWrap = false
    )

    //4 Now, we need to see if there's any OF. If there's none, we can just update the textfield. If this fxn was invoked via selection, we can now recalculate the segments and redraw the selection highlight.
    if(!result.didOverflowWidth){
        updateTextFieldValueNoCaret(
            index = initialFieldIndex,
            newValue = documentTextFieldList[initialFieldIndex].textFieldValue.copy(annotatedString = updatedString)
        )
    } else {

        val xPos = maxWidth.toFloat() - Float.MIN_VALUE
        val maxMeasuredOffset = result.getOffsetForPosition(Offset(xPos, 0f))

        val leftAnnotatedString = updatedString.subSequence(
            0,
            maxMeasuredOffset
        )
        val rightAnnotatedString = updatedString.subSequence(
            maxMeasuredOffset,
            initialLength
        )

    }

}