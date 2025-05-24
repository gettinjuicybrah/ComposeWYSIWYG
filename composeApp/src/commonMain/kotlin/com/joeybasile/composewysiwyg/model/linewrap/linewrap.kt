package com.joeybasile.composewysiwyg.model.linewrap

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState

/*fun DocumentState. (fieldIndex: Int){

}*/

/*
Field-specific reference.
 */
fun DocumentState.hasNewLineAtEnd(fieldIndex: Int): Boolean {
    return documentTextFieldList[fieldIndex].hasNewLineAtEnd
}

fun DocumentState.isEmptyField(fieldIndex: Int): Boolean {
    return documentTextFieldList[fieldIndex].isEmpty
}

fun DocumentState.isFirstField(fieldIndex: Int): Boolean {
    return fieldIndex == 0
}

fun DocumentState.isLastField(fieldIndex: Int): Boolean {
    return fieldIndex == documentTextFieldList.size
}

fun DocumentState.hasAboveField(fieldIndex: Int): Boolean {
    return fieldIndex - 1 > -1
}

fun DocumentState.aboveFieldHasNewLineAtEnd(fieldIndex: Int): Boolean {
    if (isValidFieldIndex(fieldIndex - 1)) {
        return hasNewLineAtEnd(fieldIndex - 1)
    } else return false
}

fun DocumentState.hasBelowField(fieldIndex: Int): Boolean {
    return fieldIndex < documentTextFieldList.size - 1
}

fun DocumentState.getLastFieldIndex(): Int {
    return documentTextFieldList.lastIndex
}

//Get the length of annotatedString for a particicular document
fun DocumentState.getFieldMaxOffset(fieldIndex: Int): Int {
    return documentTextFieldList[fieldIndex].textFieldValue.annotatedString.length
}

fun DocumentState.isValidFieldIndex(fieldIndex: Int): Boolean {
    return documentTextFieldList.size > 0 && fieldIndex >= 0 && fieldIndex <= documentTextFieldList.size
}

fun DocumentState.isLineBreak(fieldIndex: Int): Boolean {
    return hasAboveField(fieldIndex) &&
            aboveFieldHasNewLineAtEnd(fieldIndex) &&
            isEmptyField(fieldIndex) &&
            hasNewLineAtEnd(fieldIndex)
}

/*
Non field-specific reference
 */
fun DocumentState.isSingleFieldDocument(): Boolean {
    return documentTextFieldList.size == 0
}

fun DocumentState.isGlobalCaretAtZeroOffsetOfField(): Boolean {
    return caretState.value.offset == 0
}

fun DocumentState.isGlobalCaretAtMaxOffsetOfField(): Boolean {
    return caretState.value.offset == documentTextFieldList[caretState.value.fieldIndex].textFieldValue.text.length - 1
}

fun DocumentState.isGlobalCaretAtFirstFieldIndex(): Boolean {
    return caretState.value.fieldIndex == 0
}

fun DocumentState.isGlobalCaretAtLastFieldIndex(): Boolean {
    return caretState.value.fieldIndex == getLastFieldIndex()
}

fun DocumentState.isGlobalCaretAtRoot(): Boolean {
    return caretState.value.fieldIndex == 0 && caretState.value.offset == 0
}

fun DocumentState.getGlobalCaretOffset(): Int {
    return caretState.value.offset
}

fun DocumentState.getGlobalCaretField(): Int {
    return caretState.value.fieldIndex
}