package com.joeybasile.composewysiwyg.model.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.selection.SelectionCaretState
import com.joeybasile.composewysiwyg.model.selection.extractSelectedAnnotatedString
import com.joeybasile.composewysiwyg.model.selection.getGlobalCursorRect
import com.joeybasile.composewysiwyg.model.selection.getOrderedSelectionCarets
import com.joeybasile.composewysiwyg.model.selection.mergeSelection
import com.joeybasile.composewysiwyg.model.selection.rebuildSelectionFromAnchorAndFocus
import com.joeybasile.composewysiwyg.model.selection.setAnchorCaret
import com.joeybasile.composewysiwyg.model.selection.setFocusCaret
import com.joeybasile.composewysiwyg.util.sliceRange

// Existing doToggle function
fun DocumentState.doToggle(charStyleType: DocumentEvent.CharStyleType, textMeasurer: TextMeasurer){ // Added textMeasurer
    when(charStyleType){
        DocumentEvent.CharStyleType.BOLD -> toggleBold(textMeasurer)
        DocumentEvent.CharStyleType.ITALIC -> toggleItalic(textMeasurer)
        DocumentEvent.CharStyleType.UNDERLINE -> toggleUnderline(textMeasurer)
        DocumentEvent.CharStyleType.STRIKETHROUGH -> toggleStrikethrough(textMeasurer)
    }
}

// Modified toggle functions
fun DocumentState.toggleBold(textMeasurer: TextMeasurer){ // Added textMeasurer
    if(selectionState.isActive){
        applyStyleToSelection(DocumentEvent.CharStyleType.BOLD, textMeasurer)
    }
    else{
        val currentCharStyleState = currentCharStyle.value
        currentCharStyle.value = currentCharStyleState.copy(isBold = !currentCharStyleState.isBold)
        toolbarState.value = toolbarState.value.copy(isBold = !toolbarState.value.isBold)
    }
}
fun DocumentState.toggleItalic(textMeasurer: TextMeasurer){ // Added textMeasurer
    if(selectionState.isActive){
        applyStyleToSelection(DocumentEvent.CharStyleType.ITALIC, textMeasurer)
    }
    else{
        val currentCharStyleState = currentCharStyle.value
        currentCharStyle.value = currentCharStyleState.copy(isItalic = !currentCharStyleState.isItalic)
        toolbarState.value = toolbarState.value.copy(isItalic = !toolbarState.value.isItalic)
    }
}
fun DocumentState.toggleUnderline(textMeasurer: TextMeasurer){ // Added textMeasurer
    if(selectionState.isActive){
        applyStyleToSelection(DocumentEvent.CharStyleType.UNDERLINE, textMeasurer)
    }
    else{
        val currentCharStyleState = currentCharStyle.value
        currentCharStyle.value = currentCharStyleState.copy(isUnderline = !currentCharStyleState.isUnderline)
        toolbarState.value = toolbarState.value.copy(isUnderline = !toolbarState.value.isUnderline)
    }
}
fun DocumentState.toggleStrikethrough(textMeasurer: TextMeasurer){ // Added textMeasurer
    if(selectionState.isActive){
        applyStyleToSelection(DocumentEvent.CharStyleType.STRIKETHROUGH, textMeasurer)
    }
    else{
        val currentCharStyleState = currentCharStyle.value
        currentCharStyle.value = currentCharStyleState.copy(isStrikethrough = !currentCharStyleState.isStrikethrough)
        toolbarState.value = toolbarState.value.copy(isStrikethrough = !toolbarState.value.isStrikethrough)
    }
}

// TODO: Implement setTextColor, setTextHighlightColor, setFont, setFontSize for selections
// These would follow a similar pattern to applyStyleToSelection, but with different SpanStyle modifications.

fun DocumentState.setTextColor(color: Color, textMeasurer: TextMeasurer){
    if(selectionState.isActive){
        // applyStyleToSelection(StyleType.TextColor(color), textMeasurer) // You'd need to adapt StyleType
    }
    else{
        currentCharStyle.value = currentCharStyle.value.copy(textColor = color)
        toolbarState.value = toolbarState.value.copy(textColor = color)
    }
}
// ... and so on for other styling functions ...

fun DocumentState.clearFormat(textMeasurer: TextMeasurer){
    if(selectionState.isActive){
        applyStyleToSelection(null, textMeasurer) // Special type or flag for clearing
        resetToolbarToDefault() // Toolbar should reflect "no style"
    }
    else{
        resetToolbarToDefault()
        resetCurrentCharStyleToDefault()
    }
}


/**
 * Core function to apply (or toggle) a character style to the current selection.
 * If charStyleType is null, it implies clearing all formatting.
 */
fun DocumentState.applyStyleToSelection(
    charStyleType: DocumentEvent.CharStyleType?,
    textMeasurer: TextMeasurer
) {
    if (!selectionState.isActive) return

    val originalGlobalAnchor = selectionState.anchor!! // Save for restoring selection direction
    val originalGlobalFocus = selectionState.focus!!

    // 1. Determine Topmost/Leftmost (start) and Bottommost/Rightmost (end) carets.
    val (selectionStartCaret, selectionEndCaret) = getOrderedSelectionCarets() ?: return

    // 2. Extract the selected AnnotatedString.
    val selectedAS = extractSelectedAnnotatedString(selectionStartCaret, selectionEndCaret)

    // 3. Apply/Toggle the style on the extracted AnnotatedString.
    val styledSelectedAS = if (charStyleType == null) {
        AnnotatedString(selectedAS.text) // Clear all styles, keep plain text
    } else {
        internalToggleStyleOnAnnotatedString(selectedAS, charStyleType)
    }

    // 4. "Delete" selected text and merge surrounding content.
    val mergeInfo = mergeSelection() ?: return
    // Provides: startField, endField, merged (prefix+suffix), collapseOffset

    // Update the start field with only the merged prefix + suffix.
    updateTextFieldValueNoCaret(mergeInfo.startField,
        documentTextFieldList[mergeInfo.startField].textFieldValue.copy(annotatedString = mergeInfo.merged)
    )

    // Remove any fully deleted intermediate fields.
    if (mergeInfo.endField > mergeInfo.startField) {
        for (i in mergeInfo.endField downTo (mergeInfo.startField + 1)) {
            documentTextFieldList.removeAt(i)
        }
    }
    // Document is now consolidated at mergeInfo.startField, caret logically at mergeInfo.collapseOffset.

    // 5. Insert the styledSelectedAS into the document.
    val insertionFieldIndex = mergeInfo.startField
    val insertionOffset = mergeInfo.collapseOffset

    println("LINE 147 IN STYLEOPERTATIONS APPLYSTYLETOSELECTION().  insertionFieldIndex: ${insertionFieldIndex}, insertionOffset: ${insertionOffset}")

    val fieldForInsertion = documentTextFieldList[insertionFieldIndex]
    val prefix = fieldForInsertion.textFieldValue.annotatedString.sliceRange(0, insertionOffset)
    val suffix = fieldForInsertion.textFieldValue.annotatedString.sliceRange(insertionOffset, fieldForInsertion.textFieldValue.annotatedString.length)

    val contentToReflow = buildAnnotatedString {
        append(prefix)
        append(styledSelectedAS)
        append(suffix)
    }

    // Place the entire combined content into the first field; reflow will handle splitting.
    documentTextFieldList[insertionFieldIndex] = fieldForInsertion.copy(
        textFieldValue = fieldForInsertion.textFieldValue.copy(annotatedString = contentToReflow)
    )

    // 6. Perform reflow logic.
    var currentProcFieldIndex = insertionFieldIndex
    var remainingContentToDistribute: AnnotatedString? = documentTextFieldList[currentProcFieldIndex].textFieldValue.annotatedString

    val constraints = Constraints(maxWidth = this.maxWidth)
    val defaultStyle = getFieldTextStyle(documentTextFieldList[currentProcFieldIndex]) // Use style of the line being processed
    var loopCount = 0
    while (remainingContentToDistribute != null && currentProcFieldIndex < documentTextFieldList.size) {
        println("--------------------------------------------------------------------------")
        println("")
        println("")
        println("LOOP COUNT WHILE LOOP LINE 174: $loopCount")
        println("")
        println("")
        println("--------------------------------------------------------------------------")
        loopCount++
        val field = documentTextFieldList[currentProcFieldIndex]
        val textToMeasure = remainingContentToDistribute ?: field.textFieldValue.annotatedString // Should always be remainingContentToDistribute

        val measureResult = textMeasurer.measure(textToMeasure, style = defaultStyle, constraints = constraints,
            maxLines = 1,
            softWrap = false)

        val explicitNewlineIndex = textToMeasure.text.indexOf('\n')
        var breakByNewline = false
        var fittingPart = textToMeasure
        var overflowPart: AnnotatedString? = null

        if (measureResult.didOverflowWidth || explicitNewlineIndex != -1) {
            println("measureResult.didOverflowWidth || explicitNewlineIndex != -1")
            val measuredEndVisible = measureResult.getLineEnd(0, visibleEnd = true)
            val cutIndex: Int

            if (explicitNewlineIndex != -1 && explicitNewlineIndex < measuredEndVisible) {
                println("   explicitNewlineIndex != -1 && explicitNewlineIndex < measuredEndVisible")
                // Explicit newline occurs before or at visual overflow point
                cutIndex = explicitNewlineIndex + 1 // Split after the newline
                breakByNewline = true
            } else if (measureResult.didOverflowWidth) {
                println("   measureResult.didOverflowWidth")
                // Visual overflow occurs, or explicit newline is after visual overflow
                cutIndex = measuredEndVisible
                breakByNewline = false // Overflow, not necessarily a newline at the break point
                // Check if the fitting part *itself* ends in a newline
                if (textToMeasure.sliceRange(0, cutIndex).text.endsWith('\n')) {
                    println("       textToMeasure.sliceRange(0, cutIndex).text.endsWith('\\n')")
                    breakByNewline = true
                }
            } else { // Only explicit newline, no overflow yet (e.g. "abc\ndef")
                println("   else instead of measureResult.didOverflowWidth || explicitNewlineIndex != -1")
                cutIndex = explicitNewlineIndex + 1
                breakByNewline = true
            }

            if (cutIndex <= textToMeasure.length) {
                println("if cutIndex < textToMeasure.length")
                fittingPart = textToMeasure.sliceRange(0, cutIndex)
                overflowPart = textToMeasure.sliceRange(cutIndex, textToMeasure.length)
            } else { // cutIndex is at or beyond length, means everything fits or ends exactly
                println("else instead of cutIndex < textToMeasure.length")
                fittingPart = textToMeasure
                overflowPart = null
                if (fittingPart.text.endsWith('\n')) breakByNewline = true
            }
        } else { // No overflow and no explicit newlines in remainingContentToDistribute
            println("else instead of measureResult.didOverflowWidth || explicitNewlineIndex != -1")
            println(overflowPart?.text)
            fittingPart = textToMeasure
            overflowPart = null
            if (fittingPart.text.endsWith('\n')) breakByNewline = true
        }
println("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ")
        updateTextFieldValueNoCaret(currentProcFieldIndex, field.textFieldValue.copy(annotatedString = fittingPart))
        documentTextFieldList[currentProcFieldIndex] = documentTextFieldList[currentProcFieldIndex].copy(hasNewLineAtEnd = breakByNewline)

        remainingContentToDistribute = overflowPart

        if (remainingContentToDistribute != null && remainingContentToDistribute.text.isNotEmpty()) {
            currentProcFieldIndex++
            if (currentProcFieldIndex >= documentTextFieldList.size) {
                documentTextFieldList.add(makeEmptyField()) // Add new field if needed
            }
            // Prepend overflow to the next field's existing content
            val nextFieldOriginalContent = documentTextFieldList[currentProcFieldIndex].textFieldValue.annotatedString
            remainingContentToDistribute = buildAnnotatedString {
                append(remainingContentToDistribute!!) // it's not null here
                append(nextFieldOriginalContent)
            }
            // Temporarily set the next field's content, it will be measured in the next loop iteration
            documentTextFieldList[currentProcFieldIndex] = documentTextFieldList[currentProcFieldIndex].copy(
                textFieldValue = documentTextFieldList[currentProcFieldIndex].textFieldValue.copy(annotatedString = remainingContentToDistribute)
            )

        } else if (breakByNewline && remainingContentToDistribute == null && currentProcFieldIndex == documentTextFieldList.lastIndex) {
            // If the last processed line ended with an explicit newline and there's no overflow,
            // ensure an empty line exists after it for typical editor behavior.
            documentTextFieldList.add(makeEmptyField())
            println("END LOOP BECAUSE breakByNewline && remainingContentToDistribute == null && currentProcFieldIndex == documentTextFieldList.lastIndex")
            remainingContentToDistribute = null // End loop
        }
        else {
            println("END LOOP BECAUSE ELSE. remainingContentToDist: ${remainingContentToDistribute} ")

            remainingContentToDistribute = null // End loop
        }
        println("currentProcField: ${currentProcFieldIndex} and docTFL size: ${documentTextFieldList.size}")
    }
    // Trim any excess empty fields that might have been added speculatively or left over from prior state
    while (documentTextFieldList.size > 1 && documentTextFieldList.last().textFieldValue.annotatedString.isEmpty() && !documentTextFieldList[documentTextFieldList.lastIndex -1].hasNewLineAtEnd) {
        if (documentTextFieldList.size > insertionFieldIndex +1 ) // Don't remove the line we just worked on if it's the only one left
            documentTextFieldList.removeLast()
        else break
    }


    // 7. Restore selection state.
    // The new anchor starts where the styled text was inserted.
    val newAnchorLogicalField = insertionFieldIndex
    val newAnchorLogicalOffset = insertionOffset

    // Calculate where the styled text ends.
    var len = styledSelectedAS.length
    var newFocusLogicalField = newAnchorLogicalField
    var newFocusLogicalOffset = newAnchorLogicalOffset

    while (len > 0 && newFocusLogicalField < documentTextFieldList.size) {
        val currentField = documentTextFieldList[newFocusLogicalField]
        val fieldText = currentField.textFieldValue.annotatedString
        val fieldTextLength = fieldText.length

        val spaceInCurrentField: Int = if (newFocusLogicalField == newAnchorLogicalField) {
            fieldTextLength - newAnchorLogicalOffset
        } else {
            fieldTextLength // If on a subsequent line, count from its start
        }

        if (len <= spaceInCurrentField) {
            newFocusLogicalOffset = (if (newFocusLogicalField == newAnchorLogicalField) newAnchorLogicalOffset else 0) + len
            len = 0
        } else {
            len -= spaceInCurrentField
            newFocusLogicalField++
            newFocusLogicalOffset = 0 // Will be start of next field
        }
    }
    // If len is still > 0, it means text flowed beyond available lines (shouldn't happen if reflow added lines correctly)
    // Coerce focus to the end of the last available line if something went wrong
    if (newFocusLogicalField >= documentTextFieldList.size && documentTextFieldList.isNotEmpty()) {
        newFocusLogicalField = documentTextFieldList.lastIndex
        newFocusLogicalOffset = documentTextFieldList.last().textFieldValue.annotatedString.length
    } else if (documentTextFieldList.isEmpty()) { // Should not happen
        newFocusLogicalField = 0
        newFocusLogicalOffset = 0
    }


    // Get global positions for the new anchor and focus
    val newAnchorGlobalPos = getGlobalCursorRectForCaretState(newAnchorLogicalField, newAnchorLogicalOffset)?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero
    println("333333333333333333333333333333333 IN LINE 300 IN STYLEOPERATIONS. APPLYSTYLETOSELECTION(). newFocusLogicalField: ${newFocusLogicalField} AND newFocusLogicalOffset: ${newFocusLogicalOffset}")
    val newFocusGlobalPos = getGlobalCursorRectForCaretState(newFocusLogicalField, newFocusLogicalOffset)?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero

    val finalAnchorCaret = SelectionCaretState(newAnchorLogicalField, newAnchorLogicalOffset, newAnchorGlobalPos)
    val finalFocusCaret = SelectionCaretState(newFocusLogicalField, newFocusLogicalOffset, newFocusGlobalPos)

    // Restore original selection direction
    if (originalGlobalAnchor.fieldIndex < originalGlobalFocus.fieldIndex ||
        (originalGlobalAnchor.fieldIndex == originalGlobalFocus.fieldIndex && originalGlobalAnchor.offset < originalGlobalFocus.offset)) {
        setAnchorCaret(finalAnchorCaret)
        setFocusCaret(finalFocusCaret)
    } else {
        setAnchorCaret(finalFocusCaret) // Swapped
        setFocusCaret(finalAnchorCaret)
    }
    rebuildSelectionFromAnchorAndFocus() // Recomputes segments

    // 8. Update global caret and toolbar.
    // Position caret at the end of the selection (focus).
    caretState.value = caretState.value.copy(
        fieldIndex = selectionState.focus!!.fieldIndex,
        offset = selectionState.focus!!.offset
    )
    onCaretMoved() // This also updates toolbar state.
}

// Helper to get TextStyle for a field (you might have this elsewhere)
private fun DocumentState.getFieldTextStyle(fieldState: DocumentTextFieldState?): TextStyle {
    // This is a placeholder. You'd fetch the actual style relevant to this field,
    // or use a document-wide default.
    // For now, using the `currentTextStyle` which might be misleading if fields can have different base styles.
    return fieldState?.textStyle ?: currentTextStyle.value
}
// Helper to get global cursor rect for a potential caret position
private fun DocumentState.getGlobalCursorRectForCaretState(fieldIndex: Int, offset: Int): androidx.compose.ui.geometry.Rect? {
    println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&**********9999999999999999999 LINE 334 IN STYLEOPERATIONS. fieldIndex: ${fieldIndex} AND offset: $offset")
    if (fieldIndex < 0 || fieldIndex >= documentTextFieldList.size) return null
    val field = documentTextFieldList[fieldIndex]
    val boxCoords = parentCoordinates.value.box ?: return null
    // Ensure textLayoutResult is available; might need a way to force layout measurement if not.
    // This is a common challenge with dynamic text manipulation.
    // For simplicity, assume it's available or getGlobalCursorRect handles nulls.
    return getGlobalCursorRect(field, offset, boxCoords)
}


/**
 * Internal helper to toggle a specific style on an AnnotatedString.
 * It attempts to preserve other styles.
 */
private fun internalToggleStyleOnAnnotatedString(input: AnnotatedString, styleType: DocumentEvent.CharStyleType): AnnotatedString {
    if (input.text.isEmpty()) return AnnotatedString("")

    val styleAttributeCheck: (SpanStyle) -> Boolean
    val styleToAdd: SpanStyle
    // Function to create a new SpanStyle with the target style aspect removed.
    // It should be smart enough not to nullify other aspects of the style.
    val createStyleWithAspectRemoved: (SpanStyle) -> SpanStyle

    when (styleType) {
        DocumentEvent.CharStyleType.BOLD -> {
            styleAttributeCheck = { it.fontWeight == FontWeight.Bold }
            styleToAdd = SpanStyle(fontWeight = FontWeight.Bold)
            createStyleWithAspectRemoved = { style ->
                if (style.fontWeight == FontWeight.Bold) style.copy(fontWeight = null) else style
            }
        }
        DocumentEvent.CharStyleType.ITALIC -> {
            styleAttributeCheck = { it.fontStyle == FontStyle.Italic }
            styleToAdd = SpanStyle(fontStyle = FontStyle.Italic)
            createStyleWithAspectRemoved = { style ->
                if (style.fontStyle == FontStyle.Italic) style.copy(fontStyle = null) else style
            }
        }
        DocumentEvent.CharStyleType.UNDERLINE -> {
            styleAttributeCheck = { it.textDecoration?.contains(TextDecoration.Underline) == true }
            styleToAdd = SpanStyle(textDecoration = TextDecoration.Underline)
            createStyleWithAspectRemoved = { style ->
                val existingDecorations = style.textDecoration?.decorations ?: emptyList()
                val newDecorations = existingDecorations.filterNot { it == TextDecoration.Underline }
                if (newDecorations.isEmpty()) {
                    style.copy(textDecoration = null)
                } else {
                    style.copy(textDecoration = TextDecoration.combine(newDecorations))
                }
            }
        }
        DocumentEvent.CharStyleType.STRIKETHROUGH -> {
            styleAttributeCheck = { it.textDecoration?.contains(TextDecoration.LineThrough) == true }
            styleToAdd = SpanStyle(textDecoration = TextDecoration.LineThrough)
            createStyleWithAspectRemoved = { style ->
                val existingDecorations = style.textDecoration?.decorations ?: emptyList()
                val newDecorations = existingDecorations.filterNot { it == TextDecoration.LineThrough }
                if (newDecorations.isEmpty()) {
                    style.copy(textDecoration = null)
                } else {
                    style.copy(textDecoration = TextDecoration.combine(newDecorations))
                }
            }
        }
    }
    // Determine if the entire input range effectively has the style.
    var entireRangeHasStyle = input.text.isNotEmpty() // Assume true if not empty, then disprove.
    if (input.text.isEmpty()) entireRangeHasStyle = false
    else {
        for (i in input.text.indices) {
            val effectiveStyle = input.spanStyles.filter { it.start <= i && i < it.end }
                .map { it.item }
                .fold(SpanStyle()) { acc, s -> acc.merge(s) }
            if (!styleAttributeCheck(effectiveStyle)) {
                entireRangeHasStyle = false
                break
            }
        }
    }


    return buildAnnotatedString {
        append(input.text) // Append the raw text

        // Re-apply existing styles, modified by the toggle logic
        input.spanStyles.forEach { existingSpan ->
            val modifiedStyle = if (entireRangeHasStyle) {
                createStyleWithAspectRemoved(existingSpan.item)
            } else {
                // When adding, ensure text decorations are combined correctly
                if (styleToAdd.textDecoration != null && existingSpan.item.textDecoration != null) {
                    val combinedDecorations = TextDecoration.combine(
                        listOfNotNull(existingSpan.item.textDecoration, styleToAdd.textDecoration)
                    )
                    existingSpan.item.merge(styleToAdd.copy(textDecoration = combinedDecorations))
                } else {
                    existingSpan.item.merge(styleToAdd)
                }
            }

            // Clean up text decoration if it becomes an empty list by reconstruction
            val finalStyle = if (modifiedStyle.textDecoration != null && modifiedStyle.textDecoration!!.decorations.isEmpty()) {
                modifiedStyle.copy(textDecoration = null)
            } else {
                modifiedStyle
            }

            if (finalStyle != SpanStyle()) { // Don't add empty/default styles explicitly
                addStyle(finalStyle, existingSpan.start, existingSpan.end)
            }
        }

        // If we are adding the style and the original string had no covering spans,
        // or to ensure full coverage if segments were unstyled.
        if (!entireRangeHasStyle && input.text.isNotEmpty()) {
            addStyle(styleToAdd, 0, input.length)
        }
    }
}

// Helper to access the internal list of decorations from a TextDecoration object
// This is needed because TextDecoration itself is opaque.
private val TextDecoration.decorations: List<TextDecoration>
    get() {
        // This is a bit of a workaround. TextDecoration internally holds a list.
        // If it's a combined one, we can infer. If it's a single one, it's just that.
        // A more robust way would be if the API exposed this, but it doesn't directly.
        // We assume that if it's not Underline or LineThrough (the simple ones),
        // it might be a combination or custom. This is imperfect.
        // A safer approach for removal is to check `contains` and then rebuild.
        val currentDecorations = mutableListOf<TextDecoration>()
        if (this.contains(TextDecoration.Underline)) currentDecorations.add(TextDecoration.Underline)
        if (this.contains(TextDecoration.LineThrough)) currentDecorations.add(TextDecoration.LineThrough)
        // Add other simple decorations if you use them
        return currentDecorations
    }