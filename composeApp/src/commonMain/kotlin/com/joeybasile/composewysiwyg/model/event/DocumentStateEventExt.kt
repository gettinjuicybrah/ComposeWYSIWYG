package com.joeybasile.composewysiwyg.model.event

import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.caret.moveCaretDown
import com.joeybasile.composewysiwyg.model.caret.moveCaretLeft
import com.joeybasile.composewysiwyg.model.caret.moveCaretRight
import com.joeybasile.composewysiwyg.model.caret.moveCaretUp
import com.joeybasile.composewysiwyg.model.caret.updateCaretIndex
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.model.selection.finishSelection
import com.joeybasile.composewysiwyg.model.selection.startSelection
import com.joeybasile.composewysiwyg.model.selection.updateSelection

fun DocumentState.onEvent(event: DocumentEvent) {
    when(event) {
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

        is DocumentEvent.Selection.Start -> {
            startSelection(event.at)
        }

        is DocumentEvent.Selection.Update -> {
            updateSelection(event.to)
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
