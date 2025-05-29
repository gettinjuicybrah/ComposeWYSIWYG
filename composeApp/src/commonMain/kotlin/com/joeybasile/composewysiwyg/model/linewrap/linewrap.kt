package com.joeybasile.composewysiwyg.model.linewrap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.CaretState
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.util.sliceRange

/*fun DocumentState. (fieldIndex: Int){

}*/

/*
Field-specific reference.
 */
fun DocumentState.hasNewLineAtEnd(fieldIndex: Int): Boolean {
    return documentTextFieldList[fieldIndex].hasNewLineAtEnd
}

fun DocumentState.isEmptyField(fieldIndex: Int): Boolean {
    return documentTextFieldList[fieldIndex].textFieldValue.annotatedString.length == 0
}

fun DocumentState.isFirstField(fieldIndex: Int): Boolean {
    return fieldIndex == 0
}

fun DocumentState.isLastField(fieldIndex: Int): Boolean {
    return fieldIndex == documentTextFieldList.size - 1
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


fun DocumentState.setFieldNewLineToFalse(fieldIndex: Int){
    documentTextFieldList[fieldIndex] = documentTextFieldList[fieldIndex].copy(hasNewLineAtEnd = false)
}

fun DocumentState.removeField(fieldIndex: Int){
    documentTextFieldList.remove(documentTextFieldList[fieldIndex])
}

fun DocumentState.getTextFieldValue(fieldIndex: Int): TextFieldValue{
    return documentTextFieldList[fieldIndex].textFieldValue
}



fun DocumentState.removeLastCharFromField(fieldIndex: Int){
    val currentValue = getTextFieldValue(fieldIndex)
    val newValue = removeLastCharFromTextFieldValue(currentValue)
    updateTextFieldValueNoCaret(fieldIndex, newValue)
}

fun DocumentState.removeLastCharFromTextFieldValue(value: TextFieldValue): TextFieldValue {
    // if empty, nothing to do
    if (value.annotatedString.text.isEmpty()) return value

    // drop the last char of the raw string
    val truncated = value.annotatedString.text.dropLast(1)

    // build a fresh AnnotatedString from it (this discards any styling on the chopped part)
    val newAnn = AnnotatedString(truncated)

    // put the cursor at the end of the new text
    val newCursor = TextRange(truncated.length)

    // copy over any other fields you care about (e.g. composition; usually you can ignore it)
    return value.copy(
        annotatedString = newAnn,
        selection = newCursor,
        composition = null
    )
}

fun DocumentState.setFieldTextMeasurer(fieldIndex: Int, textMeasurer: TextMeasurer){
    documentTextFieldList[fieldIndex] = documentTextFieldList[fieldIndex].copy(textMeasurer = textMeasurer)
}
fun DocumentState.setFieldTextStyle(fieldIndex: Int, textStyle: TextStyle){
    documentTextFieldList[fieldIndex] = documentTextFieldList[fieldIndex].copy(textStyle = textStyle)
}
fun DocumentState.getFieldTextMeasurer(fieldIndex: Int):TextMeasurer{
    return documentTextFieldList[fieldIndex].textMeasurer!!
}
fun DocumentState.getFieldTextStyle(fieldIndex: Int):TextStyle{
    return documentTextFieldList[fieldIndex].textStyle!!
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

/*
Caret related
 */
fun DocumentState.setGlobalCaret(newGlobalCaret: CaretState){
    caretState.value = newGlobalCaret
    onCaretMoved()
}
fun DocumentState.setGlobalCaretOffset(newOffset: Int){
    caretState.value = caretState.value.copy(offset = newOffset)
    onCaretMoved()
}
fun DocumentState.setGlobalCaretFieldIndex(newFieldIndex:Int){
    caretState.value = caretState.value.copy(fieldIndex = newFieldIndex)
    onCaretMoved()
}
fun DocumentState.setGlobalCaretHeight(newHeight: Float){
    caretState.value = caretState.value.copy(height = newHeight)
    onCaretMoved()
}
fun DocumentState.setGlobalCaretPosition(newPos: Offset){
    caretState.value = caretState.value.copy(globalPosition = newPos)
    onCaretMoved()
}
fun DocumentState.setGlobalCaretVisibility(newVisibility: Boolean){
    caretState.value = caretState.value.copy(isVisible = newVisibility)
    onCaretMoved()
}
