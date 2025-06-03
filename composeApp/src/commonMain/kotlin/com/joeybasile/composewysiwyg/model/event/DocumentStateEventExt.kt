package com.joeybasile.composewysiwyg.model.event

import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.caret.moveCaretDown
import com.joeybasile.composewysiwyg.model.caret.moveCaretLeft
import com.joeybasile.composewysiwyg.model.caret.moveCaretRight
import com.joeybasile.composewysiwyg.model.caret.moveCaretUp
import com.joeybasile.composewysiwyg.model.caret.updateCaretIndex
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.model.selection.dragBased.startDragSelection
import com.joeybasile.composewysiwyg.model.selection.dragBased.updateDragSelection
import com.joeybasile.composewysiwyg.model.selection.finishSelection
import com.joeybasile.composewysiwyg.model.selection.handleRemoveSelection
import com.joeybasile.composewysiwyg.model.selection.shiftArrowBased.goArrowDir
import com.joeybasile.composewysiwyg.model.selection.shiftArrowBased.startShiftSelection
import com.joeybasile.composewysiwyg.model.selection.shiftArrowBased.updateShiftSelection

fun DocumentState.onEvent(event: DocumentEvent) {
    when (event) {

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
            enterPressed()
        }

        is DocumentEvent.FocusChanged -> {
            updateFocusedLine(newIndex = event.index)
            // immediately recalc the caret
            updateCaretIndex(event.index)
            onEvent(DocumentEvent.UpdateCaretPosition)
        }

        is DocumentEvent.RequestFieldFocus -> {
            // drive the `focusedLine`, so the UI LaunchedEffect can pick it up
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

        else -> {}
    }
}
