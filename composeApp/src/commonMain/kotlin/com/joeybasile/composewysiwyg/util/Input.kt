package com.joeybasile.composewysiwyg.util


import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.moveCaretDown
import com.joeybasile.composewysiwyg.model.caret.moveCaretLeft
import com.joeybasile.composewysiwyg.model.caret.moveCaretRight
import com.joeybasile.composewysiwyg.model.caret.moveCaretUp
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.event.onEvent
import com.joeybasile.composewysiwyg.model.linewrap.getFieldTextMeasurer
import com.joeybasile.composewysiwyg.model.linewrap.getFieldTextStyle
import com.joeybasile.composewysiwyg.model.linewrap.getGlobalCaretField
import com.joeybasile.composewysiwyg.model.linewrap.procBackspace
import com.joeybasile.composewysiwyg.model.linewrap.processNewBackspace
import com.joeybasile.composewysiwyg.model.selection.selectAll

fun handleDocKeyEvent(
    event: KeyEvent,
    state: DocumentState
): Boolean {
    if (event.type == KeyEventType.KeyDown && !state.selectionState.isActive && !event.isShiftPressed && event.key == Key.Backspace){
        state.procBackspace(state.getFieldTextMeasurer(state.getGlobalCaretField()),
            state.getFieldTextStyle(state.getGlobalCaretField()), state.maxWidth)
        return true
    }
    if (event.type == KeyEventType.KeyDown && state.selectionState.isActive && !event.isShiftPressed) {

        val dir = when (event.key) {
            Key.DirectionLeft -> DocumentEvent.Selection.Direction.Left
            Key.DirectionRight -> DocumentEvent.Selection.Direction.Right
            Key.DirectionUp -> DocumentEvent.Selection.Direction.Up
            Key.DirectionDown -> DocumentEvent.Selection.Direction.Down
            else -> null
        }
        if (dir != null) {
            state.onEvent(DocumentEvent.Selection.GoArrowDir(dir))
            return true
        }

        // 2) Any other key (letters, numbers, Backspace, Delete, etc.) → remove the selection
        state.onEvent(DocumentEvent.Selection.RemoveSelection)
        // return `false` so that the key event is _not_ fully consumed:
        //   • for printable keys Compose will insert the character at the collapsed caret
        //   • for Backspace/Delete the default deletion will now act on the collapsed caret
        return true



    }

    if (event.type == KeyEventType.KeyDown && event.isShiftPressed) {
        val dir = when (event.key) {
            Key.DirectionLeft -> DocumentEvent.Selection.Direction.Left
            Key.DirectionRight -> DocumentEvent.Selection.Direction.Right
            Key.DirectionUp -> DocumentEvent.Selection.Direction.Up
            Key.DirectionDown -> DocumentEvent.Selection.Direction.Down
            else -> null
        }
        if (dir != null) {
            if (!state.selectionState.isActive) {
                state.onEvent(DocumentEvent.Selection.StartShift(dir))
            } else {
                state.onEvent(DocumentEvent.Selection.UpdateShift(dir))
            }
            return true
        } else return false
    }

    if (event.isCtrlPressed && event.key == Key.A) {
        state.selectAll()
        return true  // Consume the event.
    }
    when (event.type) {
        KeyEventType.KeyDown -> when (event.key) {

            Key.DirectionRight -> {
                state.moveCaretRight()
                state.onCaretMoved()
                return true
            }

            Key.DirectionLeft -> {
                state.moveCaretLeft()
                state.onCaretMoved()
                return true
            }

            Key.DirectionDown -> {
                state.moveCaretDown()
                state.onCaretMoved()
                return true
            }

            Key.DirectionUp -> {

                state.moveCaretUp()
                state.onCaretMoved()
                return true
            }

            Key.Enter -> {
                state.onEvent(DocumentEvent.EnterPressed)
                return true

            }

            Key.NumPadEnter -> {
                state.onEvent(DocumentEvent.EnterPressed)
                return true
            }

            else -> return false
        }
        // Optionally, you can add handling for KeyDown or other types
        else -> {
            return false
        }
    }
    return false
}