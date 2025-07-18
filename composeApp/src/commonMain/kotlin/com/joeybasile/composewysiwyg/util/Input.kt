package com.joeybasile.composewysiwyg.util

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretDown
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretLeft
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretRight
import com.joeybasile.composewysiwyg.model.caret.moveGlobalCaretUp
import com.joeybasile.composewysiwyg.model.event.onEvent
import com.joeybasile.composewysiwyg.model.*
import com.joeybasile.composewysiwyg.model.image.insertImageAtCaret

fun handleDocKeyEvent(
    event: KeyEvent,
    state: DocumentState
): Boolean {
    if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.I) {
        // ----- where to load the test picture ---------------------------------
        // Desktop / JVM – read from working directory:
        println("Working dir = " + System.getProperty("user.dir"))
        val bytes = java.io.File("test.png").readBytes()
        // Android – use assets/res/raw instead:
        // val ctx = LocalContext.current
        // val bytes = ctx.assets.open("test.png").readBytes()

        // ----------------------------------------------------------------------

        state.insertImageAtCaret(bytes, "image/png")
        return true
    }

    if (event.type == KeyEventType.KeyDown && !event.isShiftPressed && event.key == Key.Backspace) {
        println("BACKSPACE PRESSED")
        println("BACKSPACE PRESSED")
        println("BACKSPACE PRESSED")
        println("BACKSPACE PRESSED")
        state.onBackSpace()
        /*
        state.pullUp(
            state.maxWidth,
            state.textMeasurer!!,
            state.defaultTextStyle
        )

         */
        return true
    }

    /*
    if (event.type == KeyEventType.KeyDown && !state.selectionState.isActive && !event.isShiftPressed && event.key == Key.Backspace) {
        state.procBackspace(
            state.getFieldTextMeasurer(state.getGlobalCaretField()),
            state.getFieldTextStyle(state.getGlobalCaretField()), state.maxWidth
        )
        return true
    }*/
    if (event.type == KeyEventType.KeyDown && state.globalSelectionState.isActive && !event.isShiftPressed) {

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
        //state.onEvent(DocumentEvent.Selection.RemoveSelection)
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
            if (!state.globalSelectionState.isActive) {
                state.onEvent(DocumentEvent.Selection.StartShift(dir))
            } else {
                state.onEvent(DocumentEvent.Selection.UpdateShift(dir))
            }
            return true
        } else return false
    }
/*
    if (event.isCtrlPressed && event.key == Key.A) {
        state.selectAll()
    }
    */
    when (event.type) {
        KeyEventType.KeyDown -> when (event.key) {

            Key.DirectionRight -> {
                state.moveGlobalCaretRight()
                state.onGlobalCaretMoved()
                return true
            }

            Key.DirectionLeft -> {
               // state.moveCaretLeft()
               // state.onCaretMoved()
                state.moveGlobalCaretLeft()
                state.onGlobalCaretMoved()
                return true
            }

            Key.DirectionDown -> {
                state.moveGlobalCaretDown()
                state.onGlobalCaretMoved()
                return true
            }

            Key.DirectionUp -> {

                state.moveGlobalCaretUp()
                state.onGlobalCaretMoved()
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

        else -> {
            return false
        }
    }
}