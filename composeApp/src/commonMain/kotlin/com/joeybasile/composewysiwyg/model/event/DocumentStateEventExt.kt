package com.joeybasile.composewysiwyg.model.event

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.moveCaretDown
import com.joeybasile.composewysiwyg.model.caret.moveCaretLeft
import com.joeybasile.composewysiwyg.model.caret.moveCaretRight
import com.joeybasile.composewysiwyg.model.caret.moveCaretUp
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.caret.updateCaretIndex
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.model.selection.finishSelection
import com.joeybasile.composewysiwyg.model.selection.goArrowDir
import com.joeybasile.composewysiwyg.model.selection.handleRemoveSelection
import com.joeybasile.composewysiwyg.model.selection.startDragSelection
import com.joeybasile.composewysiwyg.model.selection.startShiftSelection
import com.joeybasile.composewysiwyg.model.selection.updateDragSelection
import com.joeybasile.composewysiwyg.model.selection.updateShiftSelection

fun DocumentState.onEvent(event: DocumentEvent) {
    when(event) {

        is DocumentEvent.Selection.NullifyState -> {
            // wipe out anchor, focus, and any drawn segments
            finishSelection()
        }

        is DocumentEvent.Selection.RemoveSelection -> {
            handleRemoveSelection()
        }

        is DocumentEvent.Selection.GoArrowDir -> {
            goArrowDir(event.direction)
        }
        is DocumentEvent.Selection.StartShift -> {
            startShiftSelection(event.direction)
        }
        is DocumentEvent.Selection.UpdateShift -> {
            updateShiftSelection(event.direction)
        }

        is DocumentEvent.EnterPressed -> {
            val currentFieldIndex = caretState.value.fieldIndex
            val currentField = documentTextFieldList[currentFieldIndex]
            val rawOffset = caretState.value.offset
            val annotatedString = currentField.textFieldValue.annotatedString
            // clamp the offset into [0..length]
            val safeOffset = rawOffset.coerceIn(0, annotatedString.length)
            // 1) Split the text at the caret
            val beforeAS = annotatedString.subSequence(0, safeOffset)
            val afterAS  = annotatedString.subSequence(safeOffset, annotatedString.length)


            val text              = currentField.textFieldValue.annotatedString

            // If the field is completely empty, just insert a new one and bail out
            if (text.isEmpty()) {
                insertTextFieldAfter(currentFieldIndex)
                val newIndex = currentFieldIndex + 1
                caretState.value = caretState.value.copy(
                    fieldIndex = newIndex,
                    offset     = 0
                )
                onCaretMoved()
                return
            }

            // 2) Update the current field to only the "before" text
            documentTextFieldList[currentFieldIndex] = currentField.copy(
                textFieldValue = currentField.textFieldValue.copy(
                    annotatedString = beforeAS
                )
            )
            setNewLineAtEnd(currentFieldIndex)

            // 3) Make sure there's a field to receive the "after" text
            val nextIndex = currentFieldIndex + 1
            if (nextIndex >= documentTextFieldList.size) {
                // no field yet → insert one
                insertTextFieldAfter(currentFieldIndex)
            }
            // now it's safe to prepend
            prependToField(nextIndex, afterAS)

            // 4) Move the caret into the new/next field
            caretState.value = caretState.value.copy(
                fieldIndex = nextIndex,
                offset     = afterAS.length  // or 0 if you want the caret at the start
            )
            onCaretMoved()
        }
        is DocumentEvent.FocusChanged -> {
            updateFocusedLine(newIndex = event.index)
            // immediately recalc the caret
            updateCaretIndex(event.index)
            onEvent(DocumentEvent.UpdateCaretPosition)
        }
        is DocumentEvent.RequestFieldFocus -> {
            // drive your `focusedLine`, so the UI LaunchedEffect can pick it up
            updateFocusedLine(event.index)
        }

        is DocumentEvent.UpdateCaretPosition -> {
            updateCaretPosition()
        }
        is DocumentEvent.DocumentLayoutChanged -> {
            updateCaretPosition()
        }
        is DocumentEvent.Text.CoordChanged -> {
            updateTextFieldCoords(event.index, event.coords)
        }
        is DocumentEvent.Caret.Move -> when (event.direction) {
            DocumentEvent.Caret.Direction.Right -> moveCaretRight()
            DocumentEvent.Caret.Direction.Left -> moveCaretLeft()
            DocumentEvent.Caret.Direction.Up -> moveCaretUp()
            DocumentEvent.Caret.Direction.Down -> moveCaretDown()
        }

        is DocumentEvent.Selection.StartDrag -> {
            startDragSelection(event.at)
        }

        is DocumentEvent.Selection.UpdateDrag -> {
            updateDragSelection(event.to)
        }

        DocumentEvent.Selection.Finish -> {
            finishSelection()
        }

        is DocumentEvent.Text.Changed ->
            updateTextFieldValue(event.index, event.value)

        is DocumentEvent.Text.Layout ->
            updateTextFieldTextLayoutResult(event.index, event.layout)

        is DocumentEvent.CoordinatesUpdated ->
            when (event.type) {
                DocumentEvent.CoordType.DOCUMENT -> setDocCoords(event.coords)
                DocumentEvent.CoordType.BOX -> setBoxCoords(event.coords)
                DocumentEvent.CoordType.LAZY_COLUMN -> setLazyColCoords(event.coords)
            }

        // …and so on for every event
        else -> {}
    }
}
