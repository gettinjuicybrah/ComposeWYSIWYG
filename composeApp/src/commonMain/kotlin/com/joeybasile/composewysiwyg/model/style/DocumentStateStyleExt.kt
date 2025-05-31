package com.joeybasile.composewysiwyg.model.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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


fun DocumentState.clearFormat(){
    if(selectionState.isActive){
        setSelectionToDefaultStyling()
        setToolbarStateToDefault()
    }
    else{

        setCharStyleToDefault()
        setToolbarStateToDefault()
    }
}

fun DocumentState.setCharStyleToDefault(){

}
fun DocumentState.setToolbarStateToDefault(){

}

fun DocumentState.setSelectionToDefaultStyling(){

}