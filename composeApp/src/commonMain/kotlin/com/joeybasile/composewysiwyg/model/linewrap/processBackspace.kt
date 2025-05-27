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


/*
'pulling up' is just the act of attempting to append a below field's text to the text of the above field's text.
To pull up implies the existence of at least two fields.
The above field is the potential receiver of the pulled up text. It's said as the 'potential receiver' because the above field
    will only have text appended to it if the attempted-to-be-appeneded text (again, from the below field)
    a) does not cause overflow
    OR
    b)      if there is overflow,
        AND
            if the length of the original text (from the above field) is GREATER THAN OR EQUAL TO
                the shortenedAppendedString (that was measured to determine overflow, and then subsequenced at the offset that caused overflow)
    THEN
        the only implied change to the above field is
        a) if originating caret was within above field at start of backspace and offset was > 0,
            THEN No Change
        b) if originating caret was within the field directly below the above field and offset WAS == 0,
            THEN
                (if above field hasNewLine -> set to false) OR (if above field !hasNewLine -> delete last char in above field (maxoffset of above field decreased by 1))
                    THEN
                        call the attemptPullup().
                            It will attempt to pull up text from a below field to an above field at a specified offset in the above field.
                            If a 'valid pullup' is determined for the above field,
                                THEN update that field's TFV with a new AnnotatedString and Selection (set to the initial specified offset), ensuring no caret movement.
                                        ACTUALLY DON'T DO THIS, UPDATE AFTER.//AND (IF the abovefield index is equal to the inital above field index) move the global caret to said field and specified offset.
                                    THEN, update the below field's TFV with overflow annotatedString.
                                    THEN, recurse attemptPullup() on the field below the above field.
                            ELSE, no valid pullup determined for the above field,
                                So, this implies that the below fields will not be affected either, since there's no movement.
                                return.
                        THEN, after attemptPullup()
                            Update the global caret state (ensuring onMoved invoked) to the field index and offset passed in as the above field information to attemptPullup()



    if there is overflow,
        AND
            if the length of the original text (from the above field) is less than
                the shortenedAppendedString (that was measured to determine overflow, and then subsequenced at the offset that caused overflow)
 */

/*
So, the attemptPullup() function returns a Boolean.
It's purpose is to attempt a pullup, potentially recursively downwards, on a specified initial field at a specified offset within it, utilizing the


(originalDidPullUp, currentDidPullUp)
 */

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
        println("++++++++++++++++++%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% BASE CASE REACHED. CURRENT INDEX IS LAST.")
        return
    }
    //If next below index is valid, set. If not, doesn't exist.
    val belowIndex: Int = currentFieldIndex + 1
    /*if (belowIndex == null){
        println("++++++++++++++++++%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% BELOW INDEX IS NULL. currentIndex: ${currentFieldIndex} " +
                "documentTextFieldList.size: ${documentTextFieldList.size}")
        return
    }*/

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
    //extremely important to remember: caret offset is not bound to be at most field[index].TextFieldValue.text.length, but instead that length + 1, because it includes
    //the 0 offset initially, and then an offset directly AFTER the las char within the text, implying length+1 as a max caret offset possible for a field.
    val initialCaretOffset = caretState.value.offset

    if (isGlobalCaretAtRoot() || initialCaretFieldIndex < 0 || initialCaretFieldIndex > documentTextFieldList.size) {
        // No fields or invalid caret index, nothing to do.
        return
    }

    // It's safer to work with a "snapshot" of the field's state, especially its properties like hasNewLineAtEnd.
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
            /*
        Importantly, we now want to attempt to pull up any text into the current field, and recursively do such on applicable lower fields.
         */
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
    //Root was already checked for, so there's definitely an above field.
    else if (initialCaretOffset == 0) {
        println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% INITIAL OFFSET == 0")
        if (!hasAboveField(initialCaretFieldIndex)) throw IllegalStateException("Within this case, there should certainly be an above field.")

        val aboveIndex = initialCaretFieldIndex - 1
        // It's safer to work with a "snapshot" of the field's state, especially its properties like hasNewLineAtEnd.
        val aboveFieldSnapshot = documentTextFieldList[aboveIndex]
        val aboveInitialMaxCaretOffset = aboveFieldSnapshot.textFieldValue.annotatedString.length
        val originalAboveAnnotatedString = aboveFieldSnapshot.textFieldValue.annotatedString
        //If above is root and current is leaf, and leaf is empty, and the leaf was at zero caret offset when backspace was pressed, just delete it.
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
            /*
        Importantly, if the above field has a newline AND (the current field isEmpty AND has a newline),
        then just delete the current field and update the global caret accordingly
             */
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

            //to delete the newline
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

            /*
Importantly, we now want to attempt to pull up any text into the ABOVE (to be current) field, and recursively do such on applicable lower fields.
 */
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


fun DocumentState.processNewBackspace(
    // measurer and textStyle are from the current field at the time of the key event.
    // We will use field-specific measurers/styles when operating on specific fields.
    @Suppress("UNUSED_PARAMETER") measurer: TextMeasurer,
    @Suppress("UNUSED_PARAMETER") textStyle: TextStyle,
    maxWidthPx: Int
) {
    val initialCaretFieldIndex = caretState.value.fieldIndex
    val initialCaretOffset = caretState.value.offset

    if (documentTextFieldList.isEmpty() || initialCaretFieldIndex < 0 || initialCaretFieldIndex >= documentTextFieldList.size) {
        // No fields or invalid caret index, nothing to do.
        return
    }

    // It's safer to work with a "snapshot" of the field's state, especially its properties like hasNewLineAtEnd.
    val currentFieldSnapshot = documentTextFieldList[initialCaretFieldIndex]

    // Case 1: Caret offset > 0 (deletion within the current line)
    if (initialCaretOffset > 0) {
        val originalText = currentFieldSnapshot.textFieldValue.annotatedString

        // Ensure caretOffset is valid for deletion (1 to length).
        // deleteCharAfterCaretOffset handles its own bounds checking for the AnnotatedString.
        // The global caret offset rule (0 to length) means initialCaretOffset is valid.
        val textAfterCharDeletion = originalText.deleteCharBeforeCaretOffset(initialCaretOffset)
        val caretAfterCharDeletion = initialCaretOffset - 1

        // Condition for pulling text from the line below:
        // Current field does NOT have a newline AND there IS a field below.
        val shouldPullUp = !currentFieldSnapshot.hasNewLineAtEnd &&
                initialCaretFieldIndex < documentTextFieldList.lastIndex

        if (shouldPullUp) {
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& WITHIN SHOULDPULLUP")
            val belowFieldIndex = initialCaretFieldIndex + 1
            // Ensure belowField exists (double check, though lastIndex check should cover)
            if (belowFieldIndex < documentTextFieldList.size) {
                println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& BELOW FIELD INDEX LESS THAN SIZE")

                val belowFieldSnapshot = documentTextFieldList[belowFieldIndex]
                val textFromBelow = belowFieldSnapshot.textFieldValue.annotatedString
                //JUST ADDED
                val idOfBelowFieldToRemove = belowFieldSnapshot.id
                removeFieldById(idOfBelowFieldToRemove)

                val combinedText = textAfterCharDeletion.append(textFromBelow)
                val newTfvForCurrent = TextFieldValue(
                    annotatedString = combinedText,
                    selection = TextRange(caretAfterCharDeletion) // Caret remains at the point of character deletion
                )

                val currentFieldActualMeasurer = currentFieldSnapshot.textMeasurer
                    ?: throw IllegalStateException("TextMeasurer for field $initialCaretFieldIndex is null. Ensure it's set.")
                val currentFieldActualTextStyle = currentFieldSnapshot.textStyle
                    ?: throw IllegalStateException("TextStyle for field $initialCaretFieldIndex is null. Ensure it's set.")

                // processTextFieldValueUpdate will handle re-wrapping if combinedText overflows,
                // and sets the caret correctly based on the selection in newTfvForCurrent.
                processTextFieldValueUpdate(
                    index = initialCaretFieldIndex,
                    newValue = newTfvForCurrent,
                    measurer = currentFieldActualMeasurer,
                    textStyle = currentFieldActualTextStyle,
                    maxWidthPx = maxWidthPx
                )
                // Remove the field whose content was pulled up
                //removeFieldById(belowFieldSnapshot.id)
            } else {
                println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& BELOW FIELD INDEX GREATER THAN SIZE")
                // This case should ideally not be reached if shouldPullUp logic is correct.
                // Fallback to simple deletion if belowField logic fails.
                val newTextFieldValue = currentFieldSnapshot.textFieldValue.copy(
                    annotatedString = textAfterCharDeletion,
                    selection = TextRange(caretAfterCharDeletion)
                )
                updateTextFieldValue(initialCaretFieldIndex, newTextFieldValue)
            }
        } else {
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& SHOULD NOT SHOULDPULLUP")
            // Simple deletion: no pull-up from below
            val newTextFieldValue = currentFieldSnapshot.textFieldValue.copy(
                annotatedString = textAfterCharDeletion,
                selection = TextRange(caretAfterCharDeletion)
            )
            updateTextFieldValue(initialCaretFieldIndex, newTextFieldValue)
        }
        return // Finished handling offset > 0
    }
    // Case 2: Caret offset == 0 (deletion at the beginning of the line, merging with above)
    else { // initialCaretOffset == 0
        println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& caret offset is 0")
        if (initialCaretFieldIndex == 0) {
            // At the very beginning of the document (field 0, offset 0), no operation.
            return
        }

        val aboveFieldIndex = initialCaretFieldIndex - 1
        val aboveFieldSnapshot = documentTextFieldList[aboveFieldIndex]
        // currentFieldSnapshot is already available from the start of the function.

        // Scenario A.1 (User): Above field has a newline, AND (current field also has a newline AND is empty).
        // Action: Delete current field. Caret moves to the end of the above field.
        if (aboveFieldSnapshot.hasNewLineAtEnd &&
            currentFieldSnapshot.hasNewLineAtEnd &&
            currentFieldSnapshot.textFieldValue.text.isEmpty()
        ) {

            removeFieldById(currentFieldSnapshot.id)
            caretState.value = caretState.value.copy(
                fieldIndex = aboveFieldIndex,
                offset = aboveFieldSnapshot.textFieldValue.text.length // Caret at end of text in above field
            )
            onCaretMoved() // Update focus and visual caret
            return
        }

        if (currentFieldSnapshot.textFieldValue.text.isNotEmpty() &&
            (!aboveFieldSnapshot.hasNewLineAtEnd || aboveFieldSnapshot.hasNewLineAtEnd)
        ) {

        }

        // Scenario A.2 (User): Above field has a newline AND current field is NOT empty.
        // Action: Set above field’s newline to false. Merge current field's text into above field. Caret at merge point.
        if (aboveFieldSnapshot.hasNewLineAtEnd &&
            currentFieldSnapshot.textFieldValue.text.isNotEmpty()
        ) {
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& ABOVE HAS NEWLINE AND CURRENT LINE IS NOT EMPTY")
            // Create a modified version of aboveFieldSnapshot without its newline.
            val modifiedAboveField = aboveFieldSnapshot.copy(hasNewLineAtEnd = false)
            // Update the list immediately so processTextFieldValueUpdate sees the correct state if it reads from list.
            documentTextFieldList[aboveFieldIndex] = modifiedAboveField

            val textInModifiedAbove = modifiedAboveField.textFieldValue.annotatedString
            val textToAppendFromCurrent = currentFieldSnapshot.textFieldValue.annotatedString
            val combinedText = textInModifiedAbove.append(textToAppendFromCurrent)
            val mergePointCaretOffset =
                textInModifiedAbove.length // Caret will be where current text begins

            val newTfvForAbove = TextFieldValue(
                annotatedString = combinedText,
                selection = TextRange(mergePointCaretOffset)
            )

            val aboveFieldActualMeasurer =
                modifiedAboveField.textMeasurer // Use measurer from (potentially modified) above field
                    ?: throw IllegalStateException("TextMeasurer for field $aboveFieldIndex is null.")
            val aboveFieldActualTextStyle = modifiedAboveField.textStyle
                ?: throw IllegalStateException("TextStyle for field $aboveFieldIndex is null.")

            processTextFieldValueUpdate(
                index = aboveFieldIndex,
                newValue = newTfvForAbove,
                measurer = aboveFieldActualMeasurer,
                textStyle = aboveFieldActualTextStyle,
                maxWidthPx = maxWidthPx
            )
            //removeFieldById(currentFieldSnapshot.id) // Current field's content is now merged
            return
        }

        // Scenario A.3 (User): Above field DOES NOT HAVE A NEW LINE AND current field is NOT empty.
        // Action: Remove the last char in the above field. Update caret to new length of above. Merge current field's text.
        if (!aboveFieldSnapshot.hasNewLineAtEnd &&
            currentFieldSnapshot.textFieldValue.text.isNotEmpty()
        ) {
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& ABOVE FIELD DOESN'T HAVE NEWLINE AND CURRENT FIELD IS NOT EMPTY")
            var textInAboveAfterCharDelete = aboveFieldSnapshot.textFieldValue.annotatedString
            val originalAboveTextLength = textInAboveAfterCharDelete.length

            if (originalAboveTextLength > 0) {
                textInAboveAfterCharDelete =
                    textInAboveAfterCharDelete.deleteCharBeforeCaretOffset(originalAboveTextLength)
            }
            // If above field was empty, textInAboveAfterCharDelete remains empty.

            val caretInAboveAtMergePoint =
                textInAboveAfterCharDelete.length // This is the offset where current text will start

            val textToAppendFromCurrent = currentFieldSnapshot.textFieldValue.annotatedString
            val combinedText = textInAboveAfterCharDelete.append(textToAppendFromCurrent)

            val newTfvForAbove = TextFieldValue(
                annotatedString = combinedText,
                selection = TextRange(caretInAboveAtMergePoint) // Caret at the point of merge
            )

            val aboveFieldActualMeasurer =
                aboveFieldSnapshot.textMeasurer // Use original snapshot's measurer
                    ?: throw IllegalStateException("TextMeasurer for field $aboveFieldIndex is null.")
            val aboveFieldActualTextStyle = aboveFieldSnapshot.textStyle
                ?: throw IllegalStateException("TextStyle for field $aboveFieldIndex is null.")

            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& CALLING PROCESSTEXTFIELDVALUE")
            processTextFieldValueUpdate(
                index = aboveFieldIndex,
                newValue = newTfvForAbove,
                measurer = aboveFieldActualMeasurer,
                textStyle = aboveFieldActualTextStyle,
                maxWidthPx = maxWidthPx
            )
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& LEAVING PROCESSTEXTFIELDVALUE")
            removeFieldById(currentFieldSnapshot.id)
            return
        }

        // Fallback / Other specific cases for offset == 0:

        // Case: Above field DOES NOT HAVE newline, AND current field IS empty.
        // (This means current field was an empty line due to soft wrap from above).
        // Action: Delete current field. Caret to end of above field.
        if (!aboveFieldSnapshot.hasNewLineAtEnd &&
            currentFieldSnapshot.textFieldValue.text.isEmpty()
        ) {
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& ABOVE FIELD DOES NOT HAVE NEW LINE AND CURRENT LINE IS NOT EMPTY")
            removeFieldById(currentFieldSnapshot.id)
            caretState.value = caretState.value.copy(
                fieldIndex = aboveFieldIndex,
                offset = aboveFieldSnapshot.textFieldValue.text.length
            )
            onCaretMoved()
            return
        }

        // Case: Above field HAS newline, current IS empty BUT current has NO newline.
        // (Current field is an empty line immediately after a hard break, but not marked as a hard break itself).
        // Action: Set above field's newline to false. Delete current. Caret to end of (now modified) above field.
        if (aboveFieldSnapshot.hasNewLineAtEnd &&
            currentFieldSnapshot.textFieldValue.text.isEmpty() &&
            !currentFieldSnapshot.hasNewLineAtEnd
        ) {
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& Above field HAS newline, current IS empty BUT current has NO newline.")
            val modifiedAboveField = aboveFieldSnapshot.copy(hasNewLineAtEnd = false)
            documentTextFieldList[aboveFieldIndex] = modifiedAboveField // Update in list

            removeFieldById(currentFieldSnapshot.id)
            caretState.value = caretState.value.copy(
                fieldIndex = aboveFieldIndex,
                offset = modifiedAboveField.textFieldValue.text.length // Use length of the modified above field
            )
            onCaretMoved()
            return
        }

        // If execution reaches here, it implies an unhandled edge case for caret at offset 0,
        // or a logical path that wasn't explicitly covered by the scenarios.
        // Depending on editor strictness, one might log this or have a very generic fallback.
    }
}

fun DocumentState.processNewBackspaces(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    // If at the root of the document, no-op
    if (isGlobalCaretAtRoot()) return

    val fieldIndex = caretState.value.fieldIndex
    val offset = caretState.value.offset

    if (offset > 0) {
        // Case 1: Caret is within the current field
        val currentValue = documentTextFieldList[fieldIndex].textFieldValue
        val currentText = currentValue.annotatedString
        // Remove the character before the caret
        val newText = currentText.subSequence(0, offset - 1) + currentText.subSequence(
            offset,
            currentText.length
        )
        val newSelection = TextRange(offset - 1)
        val newValue = currentValue.copy(annotatedString = newText, selection = newSelection)
        // Update the field and handle wrapping
        processTextFieldValueUpdate(
            index = fieldIndex,
            newValue = newValue,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            measurer = measurer
        )
    } else if (fieldIndex > 0) {
        // Case 2: Caret is at the start of a field, and there’s a previous field
        val prevIndex = fieldIndex - 1
        val prevValue = documentTextFieldList[prevIndex].textFieldValue
        val prevText = prevValue.annotatedString

        if (prevText.isNotEmpty()) {
            // Remove the last character from the previous field
            val newPrevText = prevText.subSequence(0, prevText.length - 1)
            val newPrevSelection = TextRange(newPrevText.length)
            val newPrevValue =
                prevValue.copy(annotatedString = newPrevText, selection = newPrevSelection)

            // Move focus to the previous field
            updateFocusedLine(prevIndex)
            // Update the previous field and handle wrapping
            processTextFieldValueUpdate(
                index = prevIndex,
                newValue = newPrevValue,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                measurer = measurer
            )
        } else {
            // Optional: Handle empty previous field (e.g., remove it if it’s a line break)
            // For now, do nothing
            println("ELSE REACHED888888888888888888888888888888888888888888")
        }
    }
    /*
    //If at root of document, no op.
    val none = isGlobalCaretAtRoot()
    if (none) return

    //current only alteration:
    if (!isGlobalCaretAtZeroOffsetOfField() && (hasNewLineAtEnd(caretState.value.fieldIndex) || isLastField(caretState.value.fieldIndex))){
        println("CURRENT LINE ONLY AFFECTED.")
        return
    }
    else if (isGlobalCaretAtRoot() && hasAboveField(caretState.value.fieldIndex)){

    }

     */

}

fun DocumentState.processBackspace(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    val globalCaret = caretState.value
    //If at root of document, no op.
    val none = isGlobalCaretAtRoot()
    if (none) return

    /*
    The values currentONLY, currentANDAboveONLY, currentANDBelowONLY, currentANDAboveANDBelow
    are used to determine the effect of a backspace (when selection is not active).
    It only makes sense for ONE of the values to be true.

    The following are implications of one of them being true, and any of the following (and at least one of the following)
    will indeed be true:
    - Alter one or more documentTextFieldList elements
    - Alter global caret state
    - Alter the length of documentTextFieldList
     */
    val currentONLY: Boolean = determineIfCurrentFieldONLYEffected()
    val currentANDAboveONLY: Boolean = determineIfCurrentANDAboveONLYEffected()
    val currentANDBelowONLY: Boolean = determineIfCurrentANDBelowONLYEffected()
    val currentANDAboveANDBelow: Boolean = determineIfCurrentANDAboveANDBelowEffected()

    val currentField = getGlobalCaretField()
    val flags = listOf(
        "ONLY" to currentONLY,
        "AND_ABOVE_ONLY" to currentANDAboveONLY,
        "AND_BELOW_ONLY" to currentANDBelowONLY,
        "AND_ABOVE_AND_BELOW" to currentANDAboveANDBelow
    )
    println(
        "Backspace flags: " +
                flags.joinToString { "${it.first}=${it.second}" } +
                " | field=$currentField, offset=${globalCaret.offset}, " +
                "hasAbove=${hasAboveField(currentField)}, " +
                "hasBelow=${hasBelowField(currentField)}, " +
                "newlineAtEnd=${hasNewLineAtEnd(currentField)}"
    )

    //Allow BTF to handle.
    if (currentONLY) {
        return
    } else if (currentANDAboveONLY) {
        /*
        1.) if newline above, then set above newline to false, and move caret to last offset of field, and now delete the current field because it was a linebreak

        2.) if no newline above, then just remove last char of above line, then set caret to be the new max offset.
         */

        //if newline above
        if (aboveFieldHasNewLineAtEnd(currentField)) {
            //then set above newline to false
            setFieldNewLineToFalse(currentField - 1)
            //, and move caret to last offset of field
            setGlobalCaret(
                caretState.value.copy(
                    fieldIndex = currentField - 1,
                    offset = getFieldMaxOffset(currentField - 1)
                )
            )
            //, and now delete the current field because it was a linebreak
            removeField(currentField)
        }
        //if no newline above,
        else {
            //, then just remove last char of above line,
            removeLastCharFromField(currentField - 1)
            //, then set caret to be the new max offset.
            setGlobalCaret(
                caretState.value.copy(
                    fieldIndex = currentField - 1,
                    offset = getFieldMaxOffset(currentField - 1)
                )
            )
        }
    } else if (currentANDBelowONLY) {
        processCurrentANDBelowONLY(
            measurer, textStyle, maxWidthPx
        )
    }
    //this means that the offset is certainly zero
    else if (currentANDAboveANDBelow) {
        processCurrentANDAboveANDBelow(
            measurer, textStyle, maxWidthPx
        )

    } else {
        println("error in processBackSpace. ---------------------- Multiple are true, but only one should be.**************************************************")
        require(
            listOf(
                currentONLY,
                currentANDAboveONLY,
                currentANDBelowONLY,
                currentANDAboveANDBelow
            ).count { it }
                    == 1
        ) {
            "Backspace precondition violated: exactly one case must be true"
        }
    }

}

fun DocumentState.processCurrentANDAboveANDBelow(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    val currentField = getGlobalCaretField()
    val currentIndex = getGlobalCaretField()
    val currentFieldTFV = getTextFieldValue(currentIndex)

    val updatedCurrentAnnotatedString = currentFieldTFV.annotatedString

    val belowIndex = currentIndex + 1
    val belowFieldTFV = getTextFieldValue(belowIndex)
    val updatedBelowAnnotatedString = belowFieldTFV.annotatedString
    // remove current field if it's a line break
    if (isLineBreak(currentField)) {
        setGlobalCaret(
            caretState.value.copy(
                fieldIndex = currentField + 1,
                offset = getFieldMaxOffset(currentField + 1)
            )
        )
        removeField(currentField)
        return
    }
    /*
        else, (a.) determine if above line has newline. if so, we do that newline deletion and wrap as much as possible and update global caret
        OR b.) remove last char in above line, set global caret to new max offset)
        THEN attempt recursive merge on below field.
     */
    else {
        if (aboveFieldHasNewLineAtEnd(currentField)) {
            setFieldNewLineToFalse(currentField - 1)
            setGlobalCaret(
                caretState.value.copy(
                    fieldIndex = currentField - 1,
                    offset = getFieldMaxOffset(currentField - 1)
                )
            )
            recurseDeleteCurrentANDAboveANDBelow(
                measurer,
                textStyle,
                maxWidthPx,
                currentField,
                currentField,
                belowIndex,
                updatedCurrentAnnotatedString,
                updatedBelowAnnotatedString
            )

        } else {
            removeLastCharFromField(currentField - 1)
            setGlobalCaret(
                caretState.value.copy(
                    fieldIndex = currentField - 1,
                    offset = getFieldMaxOffset(currentField - 1)
                )
            )
            recurseDeleteCurrentANDAboveANDBelow(
                measurer,
                textStyle,
                maxWidthPx,
                currentField,
                currentField,
                belowIndex,
                updatedCurrentAnnotatedString,
                updatedBelowAnnotatedString
            )
        }
    }
}

fun DocumentState.processCurrentANDBelowONLY(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    val offset = getGlobalCaretOffset()
    val currentIndex = getGlobalCaretField()
    val currentFieldTFV = getTextFieldValue(currentIndex)

    val updatedCurrentAnnotatedString =
        currentFieldTFV.annotatedString.deleteCharBeforeCaretOffset(offset)

    val belowIndex = currentIndex + 1
    val belowFieldTFV = getTextFieldValue(belowIndex)

    recurseDeleteCurrentANDBelowONLY(
        measurer = measurer,
        textStyle = textStyle,
        maxWidthPx = maxWidthPx,
        currentIndex = currentIndex,
        initialIndex = currentIndex,
        belowIndex = belowIndex,
        updatedCurrentAnnotatedString = updatedCurrentAnnotatedString,
        updatedBelowAnnotatedString = belowFieldTFV.annotatedString,
        offset = offset
    )

    //then, let's recurse on the below field. We'll update the value with all that was taken for curretnFieldTFV, and then see if
    //any text wants to wrap from a below field.
    //so, we'll just repeat the process of.... BUT ONLY IF THERE EXISTS A FIELD BELOW THE BELOW FIELD.
    //Now, we want to attempt to append the first char (with style) from below field to above.
    //Then, we'll measure this proposedUpdatedAnnotatedString.
    //If overflow, then update current field with currentFieldTFV.
}

fun DocumentState.recurseDeleteCurrentANDBelowONLY(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    currentIndex: Int,
    initialIndex: Int,
    belowIndex: Int?,
    updatedCurrentAnnotatedString: AnnotatedString,
    updatedBelowAnnotatedString: AnnotatedString?,
    offset: Int
) {
    var currIdx = currentIndex
    var initIdx = initialIndex
    var bIdx = belowIndex
    var currAnn = updatedCurrentAnnotatedString
    var belowAnn = updatedBelowAnnotatedString
    val off = offset

    while (true) {
        // Base case: no more below fields → write out and stop
        if (!hasBelowField(currIdx)) {
            updateTextFieldValueNoCaret(
                initIdx,
                newValue = documentTextFieldList[initIdx].textFieldValue.copy(
                    annotatedString = currAnn,
                    selection = TextRange.Zero
                )
            )
            return
        }

        // Try to pull one char from below and see if it fits
        val firstChar = belowAnn!!.keepFirstChar()
        val appended = currAnn.append(firstChar)
        val result = measurer.measure(
            text = appended,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 1,
            softWrap = false
        )

        if (result.didOverflowWidth) {
            // overflow: commit currAnn and switch to merging the next field
            updateTextFieldValue(
                initIdx,
                newValue = documentTextFieldList[initIdx].textFieldValue.copy(
                    annotatedString = currAnn,
                    selection = TextRange(off - 1)
                )
            )
            // move down one field
            currIdx = bIdx!!
            initIdx = bIdx
            bIdx = bIdx - 1
            currAnn = belowAnn
            belowAnn = safeGet(documentTextFieldList, bIdx)?.textFieldValue?.annotatedString
        } else {
            // no overflow: just drop that first char from belowAnn and try again
            if (belowAnn.text.isEmpty()) {
                // once belowAnn is empty, jump to the next field
                currIdx = bIdx!!
                bIdx = bIdx - 1
                // currAnn stays the same
                belowAnn = belowAnn.dropFirstChar()
            } else {
                belowAnn = belowAnn.dropFirstChar()
            }
            // currAnn and initIdx remain unchanged
        }
    }
}

fun DocumentState.recurseDeleteCurrentANDAboveANDBelow(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    currentIndex: Int,
    initialIndex: Int,
    belowIndex: Int,
    updatedCurrentAnnotatedString: AnnotatedString,
    updatedBelowAnnotatedString: AnnotatedString?
) {
    var currIdx = currentIndex
    var initIdx = initialIndex
    var bIdx = belowIndex
    var currAnn = updatedCurrentAnnotatedString
    var belowAnn = updatedBelowAnnotatedString

    while (true) {
        // Base case: no more below fields → write out and stop
        if (!hasBelowField(currIdx)) {
            updateTextFieldValueNoCaret(
                initIdx,
                newValue = documentTextFieldList[initIdx].textFieldValue.copy(
                    annotatedString = currAnn,
                    selection = TextRange.Zero
                )
            )
            return
        }

        // Try to pull one char from below and see if it fits
        val firstChar = belowAnn!!.keepFirstChar()
        val appended = currAnn.append(firstChar)
        val result = measurer.measure(
            text = appended,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 1,
            softWrap = false
        )

        if (result.didOverflowWidth) {
            // overflow: commit currAnn and move to merging the next field (same as above-only)
            updateTextFieldValueNoCaret(
                initIdx,
                newValue = documentTextFieldList[initIdx].textFieldValue.copy(
                    annotatedString = currAnn,
                    selection = TextRange.Zero
                )
            )
            currIdx = bIdx
            initIdx = bIdx
            bIdx = bIdx - 1
            currAnn = belowAnn
            belowAnn = safeGet(documentTextFieldList, bIdx)?.textFieldValue?.annotatedString
        } else {
            // no overflow: drop that char from belowAnn and try again
            belowAnn = belowAnn.dropFirstChar()
            // currIdx, initIdx, bIdx, currAnn unchanged
        }
    }
}
/*
fun DocumentState.recurseDeleteCurrentANDAboveANDBelow(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    currentIndex: Int,
    initialIndex: Int,
    belowIndex: Int,
    updatedCurrentAnnotatedString: AnnotatedString,
    updatedBelowAnnotatedString: AnnotatedString?
) {
    //for second recursion call string
    if (!hasBelowField(currentIndex)) {
        updateTextFieldValueNoCaret(
            initialIndex,
            newValue = documentTextFieldList[initialIndex].textFieldValue.copy(
                annotatedString = updatedCurrentAnnotatedString,
                selection = TextRange.Zero
            )
        )
        return
    }

    //Now, we want to attempt to append the first char (with style) from below field to current.
    val belowAnnotatedStringFirstChar = updatedBelowAnnotatedString?.keepFirstChar()

    val appendedAnnotatedString =
        updatedCurrentAnnotatedString.append(belowAnnotatedStringFirstChar!!)

    //Then, we'll measure this proposedUpdatedAnnotatedString.

    val result = measurer.measure(
        text = appendedAnnotatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 1,
        softWrap = false
    )
    val xPos = maxWidth.toFloat() - Float.MIN_VALUE
    val maxMeasuredOffset = result.getOffsetForPosition(Offset(xPos, 0f))

    //If overflow, then update current field with currentFieldTFV.
    if (result.didOverflowWidth) {
        updateTextFieldValueNoCaret(
            initialIndex,
            newValue = documentTextFieldList[initialIndex].textFieldValue.copy(
                annotatedString = updatedCurrentAnnotatedString,
                selection = TextRange.Zero
            )
        )
        recurseDeleteCurrentANDAboveANDBelow(
            measurer = measurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            currentIndex = belowIndex,
            initialIndex = belowIndex,
            //may be out of bounds
            belowIndex = belowIndex - 1,
            updatedCurrentAnnotatedString = updatedBelowAnnotatedString!!,
            updatedBelowAnnotatedString = safeGet(
                documentTextFieldList,
                belowIndex - 1
            )?.textFieldValue?.annotatedString!!
        )
    }
    //If no overflow, repeat until OF is detected.
    else {
        recurseDeleteCurrentANDAboveANDBelow(
            measurer = measurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            currentIndex = currentIndex,
            initialIndex = initialIndex,
            belowIndex = belowIndex,
            updatedCurrentAnnotatedString = updatedCurrentAnnotatedString,
            updatedBelowAnnotatedString = updatedBelowAnnotatedString.dropFirstChar()
        )
    }

}

fun DocumentState.recurseDeleteCurrentANDBelowONLY(
    measurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int,
    currentIndex: Int,
    initialIndex: Int,
    belowIndex: Int?,
    updatedCurrentAnnotatedString: AnnotatedString,
    updatedBelowAnnotatedString: AnnotatedString?,
    offset: Int
) {

    //for second recursion call string
    if (!hasBelowField(currentIndex)) {
        updateTextFieldValueNoCaret(
            initialIndex,
            newValue = documentTextFieldList[initialIndex].textFieldValue.copy(
                annotatedString = updatedCurrentAnnotatedString,
                selection = TextRange.Zero
            )
        )
        return
    }


    //Now, we want to attempt to append the first char (with style) from below field to current.
    val belowAnnotatedStringFirstChar = updatedBelowAnnotatedString?.keepFirstChar()

    val appendedAnnotatedString =
        updatedCurrentAnnotatedString.append(belowAnnotatedStringFirstChar!!)

    //Then, we'll measure this proposedUpdatedAnnotatedString.

    val result = measurer.measure(
        text = appendedAnnotatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 1,
        softWrap = false
    )
    val xPos = maxWidth.toFloat() - Float.MIN_VALUE
    val maxMeasuredOffset = result.getOffsetForPosition(Offset(xPos, 0f))

    //If overflow, then update current field with currentFieldTFV.
    if (result.didOverflowWidth) {
        updateTextFieldValue(
            initialIndex,
            newValue = documentTextFieldList[initialIndex].textFieldValue.copy(
                annotatedString = updatedCurrentAnnotatedString,
                selection = TextRange(offset - 1)
            )
        )
        recurseDeleteCurrentANDBelowONLY(
            measurer = measurer,
            textStyle = textStyle,
            maxWidthPx = maxWidthPx,
            currentIndex = belowIndex!!,
            initialIndex = belowIndex!!,
            //may be out of bounds
            belowIndex = belowIndex - 1,
            updatedCurrentAnnotatedString = updatedBelowAnnotatedString!!,
            updatedBelowAnnotatedString = safeGet(
                documentTextFieldList,
                belowIndex - 1
            )?.textFieldValue?.annotatedString!!,
            offset = offset
        )
    }
    //If no overflow, repeat until OF is detected.
    else {
        if (updatedBelowAnnotatedString == null ||
            updatedBelowAnnotatedString.text.isEmpty()
        ) {
            recurseDeleteCurrentANDBelowONLY(
                measurer = measurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                currentIndex = belowIndex!!,
                initialIndex = initialIndex,
                belowIndex = belowIndex-1!!,
                updatedCurrentAnnotatedString = updatedCurrentAnnotatedString,
                updatedBelowAnnotatedString = updatedBelowAnnotatedString.dropFirstChar(),
                offset = offset
            )


        } else {
            recurseDeleteCurrentANDBelowONLY(
                measurer = measurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                currentIndex = currentIndex,
                initialIndex = initialIndex,
                belowIndex = belowIndex,
                updatedCurrentAnnotatedString = updatedCurrentAnnotatedString,
                updatedBelowAnnotatedString = updatedBelowAnnotatedString.dropFirstChar(),
                offset = offset
            )
        }
    }

}

 */

fun DocumentState.determineIfCurrentFieldONLYEffected(): Boolean {
    return !isGlobalCaretAtZeroOffsetOfField() &&
            (
                    isLastField(getGlobalCaretField())
                            ||
                            isSingleFieldDocument()
                            ||
                            hasNewLineAtEnd(getGlobalCaretField())
                    )
}

fun DocumentState.determineIfCurrentANDAboveONLYEffected(): Boolean {
    return isGlobalCaretAtZeroOffsetOfField() &&
            (
                    (!isEmptyField(getGlobalCaretField()) && hasNewLineAtEnd(getGlobalCaretField()))
                            ||
                            isLastField(getGlobalCaretField())
                    )
}


fun DocumentState.determineIfCurrentANDBelowONLYEffected(): Boolean {
    return !isGlobalCaretAtZeroOffsetOfField() && hasBelowField(getGlobalCaretField()) && !hasNewLineAtEnd(
        getGlobalCaretField()
    )
}

/*
Importantly, 'isGlobalCaretAtZeroOffsetOfField() && !hasNewLineAtEnd(getGlobalCaretField()' being true implies
a recursive merge, because it implies contiguous characters, since there is no line break.
 */
fun DocumentState.determineIfCurrentANDAboveANDBelowEffected(): Boolean {
    return hasAboveField(getGlobalCaretField()) &&
            hasBelowField(getGlobalCaretField()) &&
            (isLineBreak(getGlobalCaretField()) ||
                    (isGlobalCaretAtZeroOffsetOfField() && !hasNewLineAtEnd(getGlobalCaretField())))
}
