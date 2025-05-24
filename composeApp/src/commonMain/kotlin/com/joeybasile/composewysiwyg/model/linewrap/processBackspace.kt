package com.joeybasile.composewysiwyg.model.linewrap

import com.joeybasile.composewysiwyg.model.DocumentState

fun DocumentState.processBackspaceWithSelection() {

}

fun DocumentState.processBackspace() {
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
    //Allow BTF to handle.
    if (currentONLY){
        return
    }
    else if(currentANDAboveONLY){
        /*
        1.) if newline above, then set above newline to false, and move caret to last offset of field (we just deleted a newline.)
        2.) if no newline above, then just remove last char of above line, then set caret to be the new max offset.
         */
    }
    else if(currentANDBelowONLY){
        /*
        potential for recursive merge now
         */
    }
    //this means that the offset is certainly zero
    else if(currentANDAboveANDBelow){
        /*
        1.) remove current field if it's a line break
        2.) (a.) determine if above line has newline. if so, we do that newline deletion and wrap as much as possible and update global caret
            OR b.) remove last char in above line, set global caret to new max offset)
            THEN attempt recursive merge on below field.
         */
    }
    else{
        println("error in processBackSpace. ---------------------- Multiple are true, but only one should be.**************************************************")
    }

}

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
    return !isGlobalCaretAtZeroOffsetOfField() && hasBelowField(getGlobalCaretField()) && hasNewLineAtEnd(getGlobalCaretField())
}

/*
Importantly, 'isGlobalCaretAtZeroOffsetOfField() && !hasNewLineAtEnd(getGlobalCaretField()' being true implies
a recursive merge, because it implies contiguous characters, since there is no line break.
 */
fun DocumentState.determineIfCurrentANDAboveANDBelowEffected(): Boolean{
    return hasAboveField(getGlobalCaretField()) &&
            hasBelowField(getGlobalCaretField()) &&
            (isLineBreak(getGlobalCaretField()) ||
                    (isGlobalCaretAtZeroOffsetOfField() && !hasNewLineAtEnd(getGlobalCaretField())))
}
