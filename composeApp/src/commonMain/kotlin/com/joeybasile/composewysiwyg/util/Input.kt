package com.joeybasile.composewysiwyg.util


import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
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
import com.joeybasile.composewysiwyg.model.selection.selectAll

fun handleDocKeyEvent(
    event: KeyEvent,
    state: DocumentState,
): Boolean {
    if (event.isCtrlPressed && event.key == Key.A)  {
        state.selectAll()
        return true  // Consume the event.
    }
    when(event.type){
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