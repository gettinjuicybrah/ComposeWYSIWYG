package com.joeybasile.composewysiwyg.model.event

import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretDown
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretLeft
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretRight
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretUp
import com.joeybasile.composewysiwyg.model.selection.goArrowDir
import com.joeybasile.composewysiwyg.model.selection.startShiftSelection
import com.joeybasile.composewysiwyg.model.selection.updateShiftSelection

fun DocumentState.onEvent(event: DocumentEvent) {
    when (event) {

        is DocumentEvent.Selection.NullifyState -> {
            // wipe out anchor, focus, and any drawn segments

        }

        is DocumentEvent.Selection.RemoveSelection -> {

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

            onEvent(DocumentEvent.UpdateCaretPosition)
        }

        is DocumentEvent.RequestFieldFocus -> {

        }

        is DocumentEvent.UpdateCaretPosition -> {

        }

        is DocumentEvent.DocumentLayoutChanged -> {

        }

        is DocumentEvent.Text.CoordChanged -> {

        }

        is DocumentEvent.Caret.Move -> when (event.direction) {
            DocumentEvent.Caret.Direction.Right -> moveGlobalCaretRight()
            DocumentEvent.Caret.Direction.Left -> moveGlobalCaretLeft()
            DocumentEvent.Caret.Direction.Up -> moveGlobalCaretUp()
            DocumentEvent.Caret.Direction.Down -> moveGlobalCaretDown()
        }

        is DocumentEvent.Selection.StartDrag -> {
            //startDragSelection(event.at)
        }

        is DocumentEvent.Selection.UpdateDrag -> {
            //updateDragSelection(event.to)
        }

        DocumentEvent.Selection.Finish -> {
            //finishSelection()
        }

        is DocumentEvent.Text.Changed ->{}


        is DocumentEvent.Text.Layout ->{}
            //updateTextFieldTextLayoutResult(event.index, event.layout)

        is DocumentEvent.CoordinatesUpdated ->
        {}

        else -> {}
    }
}
