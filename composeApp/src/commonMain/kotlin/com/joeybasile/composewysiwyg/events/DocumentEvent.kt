package com.joeybasile.composewysiwyg.events

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.events.DocumentEvent.Caret.Direction

/**
 * A sealed class representing all possible user or UI-driven events
 * in the document editor. Handled by the ViewModel to update state.
 */
sealed class DocumentEvent {
    data class FocusChanged(val index: Int) : DocumentEvent()

   object EnterPressed: DocumentEvent()

    object DocumentLayoutChanged : DocumentEvent()

    sealed class Text : DocumentEvent() {
        data class Changed(val index: Int, val value: TextFieldValue) : Text()
        data class Layout(val index: Int, val layout: TextLayoutResult) : Text()
        data class CoordChanged(val index: Int, val coords: LayoutCoordinates): Text()
    }
    sealed class Selection : DocumentEvent() {
        data class StartDrag(val at: Offset)            : Selection()
        data class UpdateDrag(val to: Offset)           : Selection()
        object Finish                              : Selection()
        data class StartShift(val direction: Direction) : Selection()
        data class UpdateShift(val direction: Direction): Selection()
        data class GoArrowDir(val direction: Direction) : Selection()
        enum class Direction { Left, Right, Up, Down }
        object NullifyState : Selection()
        object RemoveSelection : Selection()
        data class Paste(val text: String) : Selection()
    }
    data class CoordinatesUpdated(val type: CoordType, val coords: LayoutCoordinates) : DocumentEvent()

    enum class CoordType { DOCUMENT, BOX, LAZY_COLUMN }

    sealed class Caret : DocumentEvent() {
        data class Move(val direction: Direction) : Caret()
        enum class Direction { Left, Right, Up, Down }

        data class SetPosition(val fieldIndex: Int, val offset: Int) : Caret()
        object Show : Caret()
        object Hide : Caret()
    }
    /** UI is requesting that we recalculate the caret position
     *  (and by extension, update selection+focus binds). */
    object UpdateCaretPosition : DocumentEvent()

    /** UI is requesting that field `index` be programmatically focused. */
    data class RequestFieldFocus(val index: Int) : DocumentEvent()

    enum class CharStyleType {BOLD, ITALIC, UNDERLINE, STRIKETHROUGH}
}