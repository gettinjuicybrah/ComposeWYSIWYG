package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.caret.CaretState
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.model.event.onEvent
import com.joeybasile.composewysiwyg.model.selection.SelectionSegment
import com.joeybasile.composewysiwyg.model.selection.SelectionState
import com.joeybasile.composewysiwyg.util.sliceRange
import kotlinx.coroutines.CoroutineScope


/**
 * Creates and remembers the [DocumentState] instance.
 *
 * This composable ensures that the state is retained across recompositions and initializes
 * a [CoroutineScope] for any asynchronous operations that may be necessary.
 *
 * @return A remembered instance of [DocumentState].
 */
@Composable
fun rememberDocumentState(): DocumentState {
    // Use remember to persist the state instance across recompositions.
    val scope = rememberCoroutineScope()

    val state = remember {
        DocumentState(
            scope = scope
        )
    }
    // Initialize the document state for a new document. Later, this can be
    // replaced with conditional logic if you decide to support existing documents.
    LaunchedEffect(Unit) {
        state.initializeNewDocument()
    }
    return state
}

/**
 * The main state holder for the document in the WYSIWYG editor.
 *
 * [DocumentState] holds a list of text field states as well as coordinate data for various
 * parent components. It provides methods to update individual text fields (text, layout metrics,
 * and coordinates) so that these changes are handled in a single source of truth.
 *
 * @property scope A [CoroutineScope] that can be used for asynchronous operations if needed.
 */
class DocumentState(val scope: CoroutineScope) {
    var caretState = mutableStateOf(
        CaretState(
            fieldIndex = 0,
            offset = 0,
            globalPosition = Offset.Unspecified,
            height = 16f
        )
    )
    var selectionState by mutableStateOf(SelectionState())

    // Internally maintain a mutable state list of text field states.
    var documentTextFieldList = mutableStateListOf<DocumentTextFieldState>()

    // State holder for the coordinates of parent containers.
    var parentCoordinates = mutableStateOf(ParentCoordinates())

    val focusedLine = mutableStateOf(0)

    // 2) The little helper that converts any mapper into a Compose callback
    inline fun <T> event(
        crossinline mapper: (T) -> DocumentEvent
    ): (T) -> Unit = { payload ->
        onEvent(mapper(payload))
    }

    fun updateFocusedLine(newIndex: Int) {
        focusedLine.value = newIndex
    }

    /**
     * Initializes a new, blank document by adding a single empty text field.
     * This method can later be expanded to check a flag or
     * accept a document model for loading existing content.
     */
    fun initializeNewDocument() {
        // Clear any existing fields just to be sure we start fresh
        //_documentTextFieldList.clear()

        // Create a single blank DocumentTextFieldState.
        val newField = DocumentTextFieldState(
            textFieldValue = TextFieldValue("new doc init test"),
            layoutCoordinates = null,
            textLayoutResult = null,
            focusRequester = FocusRequester()
        )
        documentTextFieldList.add(newField)

        // Optionally, you can set the caret position here using your custom logic.
        // For example, record initial caret coordinates in state or notify a caret manager.

        // Initialize caret at start of first field; global position calculated later
        caretState.value = CaretState(
            fieldIndex = 0,
            offset = 0,
            globalPosition = Offset.Unspecified,
            height = 16f
        )

    }

    fun insertTextFieldAfter(index: Int) {
        // Create new DocumentTextFieldState.
        val newField = DocumentTextFieldState(
            textFieldValue = TextFieldValue("pressed enter"),
            layoutCoordinates = null,
            textLayoutResult = null,
            focusRequester = FocusRequester()
        )
        documentTextFieldList.add(index + 1, newField)

        // Optional: Adjust indices or any other state updates if necessary.
        // You might also want to notify any focus manager or refresh the list.
    }

    /**
     * Updates the layout coordinates of the overall document container.
     *
     * @param coordinates The new [LayoutCoordinates] for the document container.
     */
    fun setDocCoords(coordinates: LayoutCoordinates) {
        parentCoordinates.value = parentCoordinates.value.copy(document = coordinates)
    }

    /**
     * Updates the layout coordinates of the Box container.
     *
     * @param coordinates The new [LayoutCoordinates] for the Box container.
     */
    fun setBoxCoords(coordinates: LayoutCoordinates) {
        parentCoordinates.value = parentCoordinates.value.copy(box = coordinates)
    }

    /**
     * Updates the layout coordinates of the LazyColumn container.
     *
     * @param coordinates The new [LayoutCoordinates] for the LazyColumn container.
     */
    fun setLazyColCoords(coordinates: LayoutCoordinates) {
        parentCoordinates.value = parentCoordinates.value.copy(lazyColumn = coordinates)
    }

    fun splitAnnotatedString(
        fullText: AnnotatedString,
        measurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int
    ): Pair<AnnotatedString, AnnotatedString> {
        val result = measurer.measure(
            text = fullText,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx)
        )
        if (!result.didOverflowWidth) {
            // all fits in one line
            return fullText to AnnotatedString("")
        }
        val cutIndex = result.getLineEnd(0, visibleEnd = true)
        val fitting = fullText.sliceRange(0, cutIndex)
        val overflow = fullText.sliceRange(cutIndex, fullText.length)
        return fitting to overflow
    }

    fun addFieldAtIndex(index: Int) {
        // Create new DocumentTextFieldState.
        val newField = DocumentTextFieldState(
            textFieldValue = TextFieldValue("pressed enter"),
            layoutCoordinates = null,
            textLayoutResult = null,
            focusRequester = FocusRequester()
        )
        documentTextFieldList.add(index, newField)
    }

    /**
     * Updates the text value for a specific text field.
     *
     * This method creates a new state object with the updated [TextFieldValue] so that
     * Compose is aware of the change and triggers recomposition.
     *
     * @param index The index of the text field to update.
     * @param newValue The new [TextFieldValue] for the text field.
     */
    //What this does: Updates the BTF’s textFieldValue and, if it’s the focused field, sets the global caret’s offset to the start of the new selection (where the local caret is after the user’s input).
    fun updateTextFieldValue(index: Int, newValue: TextFieldValue) {
        documentTextFieldList[index] = documentTextFieldList[index].copy(textFieldValue = newValue)
        // *** unconditionally sync the global caret to whatever the local selection now is ***
        caretState.value = caretState.value.copy(
            fieldIndex = index,
            offset = newValue.selection.start
        )

        // immediately recompute the global caret position on screen
        updateCaretPosition()

        /*
        if (index == focusedLine.value) {
            caretState.value = caretState.value.copy(offset = newValue.selection.start)
        }
         */
    }

    /**
     * Updates the text layout result for a specific text field.
     *
     * This holds data such as text metrics and cursor positions that can be used for
     * further layout or editing operations.
     *
     * @param index The index of the text field to update.
     * @param newResult The new [TextLayoutResult] generated from the text layout.
     */
    fun updateTextFieldTextLayoutResult(index: Int, newResult: TextLayoutResult) {
        documentTextFieldList[index] =
            documentTextFieldList[index].copy(textLayoutResult = newResult)
        if (caretState.value.fieldIndex == index) {
            updateCaretPosition()
        }
    }

    /**
     * Updates the layout coordinates for a specific text field.
     *
     * These coordinates are captured during layout passes and can be used for hit testing,
     * selection, or positioning overlays relative to a text field.
     *
     * @param index The index of the text field to update.
     * @param coords The new [LayoutCoordinates] for the text field.
     */
    fun updateTextFieldCoords(index: Int, coords: LayoutCoordinates) {
        documentTextFieldList[index] = documentTextFieldList[index].copy(layoutCoordinates = coords)
        if (caretState.value.fieldIndex == index) {
            updateCaretPosition()
        }
    }

    /**
     * Maps a global offset (in the Box's coordinate system) to a text field index and character offset.
     * @param globalOffset The position in the Box's coordinate system.
     * @return A Pair of (fieldIndex, offset) or null if no text field contains the position.
     */
    fun getFieldAndOffsetForPosition(globalOffset: Offset): Pair<Int, Int>? {
        val boxCoords = parentCoordinates.value.box ?: return null
        for (i in documentTextFieldList.indices) {
            val field = documentTextFieldList[i]
            val textFieldCoords = field.layoutCoordinates ?: continue
            // Get the text field's top-left position relative to the Box
            val textFieldTopLeft = boxCoords.localPositionOf(textFieldCoords, Offset.Zero)
            val textFieldSize = textFieldCoords.size
            val textFieldRect = Rect(
                textFieldTopLeft,
                Size(textFieldSize.width.toFloat(), textFieldSize.height.toFloat())
            )
            if (textFieldRect.contains(globalOffset)) {
                // Convert globalOffset to local coordinates within the text field
                val localOffset = textFieldCoords.localPositionOf(boxCoords, globalOffset)
                val textLayoutResult = field.textLayoutResult ?: return null
                val offset = textLayoutResult.getOffsetForPosition(localOffset)
                return Pair(i, offset)
            }
        }
        return null
    }
}