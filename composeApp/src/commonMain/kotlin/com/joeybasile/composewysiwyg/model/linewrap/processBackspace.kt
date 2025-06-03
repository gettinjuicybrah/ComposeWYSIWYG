package com.joeybasile.composewysiwyg.model.linewrap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.caret.CaretState
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.util.append
import com.joeybasile.composewysiwyg.util.deleteCharBeforeCaretOffset
import com.joeybasile.composewysiwyg.util.dropFirstChar
import com.joeybasile.composewysiwyg.util.keepFirstChar

fun DocumentState.processBackspaceWithSelection() {

}

// Helper to remove a field by its ID to avoid issues with index changes
// This can be a private extension within the same file or a utility in DocumentState
private fun DocumentState.removeFieldById(id: String) {
    val indexToRemove = documentTextFieldList.indexOfFirst { it.id == id }
    if (indexToRemove != -1) {
        documentTextFieldList.removeAt(indexToRemove)
    }
}

fun DocumentState.attemptPullup(
    initialFieldIndex: Int,
    currentFieldAnnotatedString: AnnotatedString,
    currentFieldIndex: Int,
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    if (isLastField(currentFieldIndex)){
        val newValue = TextFieldValue(
            annotatedString = currentFieldAnnotatedString,
            selection = TextRange.Zero
        )
        updateTextFieldValueNoCaret(
            index = currentFieldIndex,
            newValue = newValue
        )
        println("++++++++++++++++++%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% BASE CASE REACHED. CURRENT INDEX IS LAST.")
        return
    }
    val belowIndex: Int = currentFieldIndex + 1

    val belowField = documentTextFieldList[belowIndex]
    val belowFieldAnnotatedString = belowField.textFieldValue.annotatedString

    val appendedBelowToAboveAnnotatedString =
        currentFieldAnnotatedString.append(belowFieldAnnotatedString)

    val measureResult = measurer.measure(
        text = appendedBelowToAboveAnnotatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 1,
        softWrap = false
    )

    val xPos = maxWidth.toFloat() - Float.MIN_VALUE
    //The character offset of the final fitting char from the measureResult, whether overflow or not.
    val maxMeasuredOffset = measureResult.getOffsetForPosition(Offset(xPos, 0f))

    //fitting
    val leftAnnotatedString = appendedBelowToAboveAnnotatedString.subSequence(
        0,
        maxMeasuredOffset
    )
    //(potentially) overflowed
    val rightAnnotatedString = appendedBelowToAboveAnnotatedString.subSequence(
        maxMeasuredOffset,
        appendedBelowToAboveAnnotatedString.length
    )

    /*
    Now, we must determine if a valid pullup is possible.
     */

    //Implies no change.
    if (currentFieldAnnotatedString.length == leftAnnotatedString.length) {
        println("++++++++++++++++++%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% NO CHANGE IMPLIED. RETURNING FROM attemptPullup()")
        return
    }
    //implies change - there was text able to be appended. There was enough width.
    else if (currentFieldAnnotatedString.length < leftAnnotatedString.length) {
        println("++++++++++++++++++%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% CHANGE IMPLIED")
        val newValue = TextFieldValue(
            annotatedString = leftAnnotatedString,
            /*
            If the initial index is the current field index, then we want the selection to be the initial caret offset,
            otherwise, it can be 0, as it doesn't matter then.

            This actually isn't necessary, as the local caret will be determined by the global. TextRange.Zero it is, then.
             */
            selection = TextRange.Zero
        )
        updateTextFieldValueNoCaret(
            index = currentFieldIndex,
            newValue = newValue
        )
        println("++++++++++++++++++%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% RECURSIVE CALL IN attemptPullup()")
        attemptPullup(
            initialFieldIndex = initialFieldIndex,
            currentFieldAnnotatedString = rightAnnotatedString,
            currentFieldIndex = belowIndex,
            measurer = measurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx
        )

    }
    //This should never be reached.
    else {
        throw IllegalStateException("Within attemptPullup(), initalFieldAnnotatedString.length is somehow GREATER than the the fitting AS length.")
    }

}

fun DocumentState.procBackspace(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    val initialCaretFieldIndex = caretState.value.fieldIndex
      val initialCaretOffset = caretState.value.offset

    if (isGlobalCaretAtRoot() || initialCaretFieldIndex < 0 || initialCaretFieldIndex > documentTextFieldList.size) {
        // No fields or invalid caret index, nothing to do.
        return
    }
    val currentFieldSnapshot = documentTextFieldList[initialCaretFieldIndex]

    if (initialCaretOffset > 0) {
        println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% INITIAL OFFSET > 0")
        //The new offset the global caret will have once the delete is processed, given the intial was > 0.
        val newCaretOffset = initialCaretOffset - 1

        val originalAnnotatedString = currentFieldSnapshot.textFieldValue.annotatedString
        val updatedOriginalAnnotatedString =
            originalAnnotatedString.deleteCharBeforeCaretOffset(initialCaretOffset)

        println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% doc textfield size: ${documentTextFieldList.size} initial caret field index: $initialCaretFieldIndex")

        if(isSingleFieldDocument() || currentFieldSnapshot.hasNewLineAtEnd|| isLastField(initialCaretFieldIndex)){
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% So, IS a single line document OR has a new line at end.")
            updateTextFieldValueNoCaret(initialCaretFieldIndex, currentFieldSnapshot.textFieldValue.copy(annotatedString = updatedOriginalAnnotatedString, selection = TextRange(newCaretOffset)))

        }
        //So, IS a single line document OR has a new line at end.
        else{
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% NOT A SINGLE FIELD DOC AND DOESNT HAVE A NEWLINE")
            attemptPullup(
                initialFieldIndex = initialCaretFieldIndex,
                currentFieldAnnotatedString = updatedOriginalAnnotatedString,
                currentFieldIndex = initialCaretFieldIndex,
                measurer = measurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx
            )
        }
        setGlobalCaret(
            newGlobalCaret = caretState.value.copy(
                fieldIndex = initialCaretFieldIndex,
                offset = newCaretOffset
            )
        )
        return
    }
     else if (initialCaretOffset == 0) {
        println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% INITIAL OFFSET == 0")
        if (!hasAboveField(initialCaretFieldIndex)) throw IllegalStateException("Within this case, there should certainly be an above field.")

        val aboveIndex = initialCaretFieldIndex - 1
        val aboveFieldSnapshot = documentTextFieldList[aboveIndex]
        val aboveInitialMaxCaretOffset = aboveFieldSnapshot.textFieldValue.annotatedString.length
        val originalAboveAnnotatedString = aboveFieldSnapshot.textFieldValue.annotatedString
        if (isFirstField(aboveIndex) && isLastField(initialCaretFieldIndex) && isEmptyField(
                initialCaretFieldIndex
            )
        ) {
            removeField(initialCaretFieldIndex)
            setGlobalCaret(
                newGlobalCaret = caretState.value.copy(
                    fieldIndex = aboveIndex,
                    offset = aboveInitialMaxCaretOffset
                )
            )
            return
        }

        if (aboveFieldHasNewLineAtEnd(initialCaretFieldIndex)) {
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% ABOVE FIELD HAS NEW LINE")

            if (isEmptyField(initialCaretFieldIndex)) {
                removeField(initialCaretFieldIndex)
                setGlobalCaret(
                    newGlobalCaret = caretState.value.copy(
                        fieldIndex = aboveIndex,
                        offset = aboveInitialMaxCaretOffset
                    )
                )
                return
            }

            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% GOING TO SET NEW LINE TO FALSE.")
            setFieldNewLineToFalse(aboveIndex)
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% BEGIN PULLUP ATTEMPT.")
            attemptPullup(
                initialFieldIndex = aboveIndex,
                currentFieldAnnotatedString = aboveFieldSnapshot.textFieldValue.annotatedString,
                currentFieldIndex = aboveIndex,
                measurer = measurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx
            )
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% GLOBAL CARET WILL BE SET NOW")
            setGlobalCaret(
                newGlobalCaret = caretState.value.copy(
                    fieldIndex = aboveIndex,
                    offset = aboveInitialMaxCaretOffset
                )
            )
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% RETURNING FROM procBackspace()")
            return
        }
        //Else, the above field doesn't have a new line at end
        else {
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% ABOVE FIELD DOES NOT HAVE NEW LINE")
            val updatedOriginalAnnotatedString =
                originalAboveAnnotatedString.deleteCharBeforeCaretOffset(aboveInitialMaxCaretOffset)

            val updatedAboveMaxCaretOffset = aboveInitialMaxCaretOffset - 1

            if (isEmptyField(initialCaretFieldIndex)) {
                println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% CURRENT FIELD EMPTY")
                removeField(initialCaretFieldIndex)
                updateTextFieldValueNoCaret(
                    aboveIndex,
                    aboveFieldSnapshot.textFieldValue.copy(
                        annotatedString = updatedOriginalAnnotatedString,
                        selection = TextRange(updatedAboveMaxCaretOffset)
                    )
                )
                setGlobalCaret(
                    newGlobalCaret = caretState.value.copy(
                        fieldIndex = aboveIndex,
                        offset = updatedAboveMaxCaretOffset
                    )
                )
                return
            }
            println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% CURRENT FIELD NOT EMPTY")
            attemptPullup(
                initialFieldIndex = aboveIndex,
                currentFieldAnnotatedString = updatedOriginalAnnotatedString,
                currentFieldIndex = aboveIndex,
                measurer = measurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx
            )
            setGlobalCaret(
                newGlobalCaret = caretState.value.copy(
                    fieldIndex = aboveIndex,
                    offset = updatedAboveMaxCaretOffset
                )
            )

            return
        }


    }

}