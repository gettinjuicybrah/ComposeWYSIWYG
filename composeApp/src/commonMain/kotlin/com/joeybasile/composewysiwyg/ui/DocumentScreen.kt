package com.joeybasile.composewysiwyg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.model.event.onEvent
import com.joeybasile.composewysiwyg.model.linewrap.setFieldTextMeasurer
import com.joeybasile.composewysiwyg.model.linewrap.setFieldTextStyle
import com.joeybasile.composewysiwyg.model.rememberDocumentState
import com.joeybasile.composewysiwyg.util.handleDocKeyEvent
import kotlin.math.sqrt

@Composable
fun DocumentWithSelectionOverlay(
    state: DocumentState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Document(state = state, modifier = Modifier.matchParentSize())
        if (state.selectionState.isActive) {
            SelectionOverlay(state = state, modifier = Modifier.matchParentSize())
        } else {
        }
    }
}

@Composable
fun SelectionOverlay(
    state: DocumentState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()

            .drawWithContent {
                drawContent()
                state.selectionState.segments.forEach { segment ->
                    drawRect(
                        color = Color.Green,
                        topLeft = segment.rect.topLeft,
                        size = segment.rect.size,
                        alpha = 0.5F
                    )
                }
            }
    )
}

/**
 * Composable function that represents a document view in the WYSIWYG editor.
 *
 * This composable is responsible for rendering the entire document using a [LazyColumn] where each
 * item represents a text field. It also captures layout coordinates for different parent containers,
 * which could later be used for tasks like positioning overlays or handling complex layout behavior.
 *
 * @param state The current [DocumentState] holding the list of text fields and layout data.
 *              If not provided, a state is created using [rememberDocumentState].
 * @param modifier A [Modifier] applied to the top-level container.
 */
@Composable
fun Document(
    state: DocumentState,
    modifier: Modifier = Modifier
        .onGloballyPositioned { coords ->
            state.onEvent(
                DocumentEvent.CoordinatesUpdated(
                    DocumentEvent.CoordType.DOCUMENT,
                    coords
                )
            )
        }

) {
    LaunchedEffect(state.parentCoordinates.value, state.documentTextFieldList) {
        state.onEvent(DocumentEvent.DocumentLayoutChanged)
    }
    val fieldList = state.documentTextFieldList

    Box(
        modifier = Modifier

            .onGloballyPositioned { coords ->
                state.onEvent(DocumentEvent.CoordinatesUpdated(DocumentEvent.CoordType.BOX, coords))
            }
            .drawWithContent {
                drawContent()
                val caret = state.caretState.value
                if (caret.globalPosition != Offset.Unspecified && caret.isVisible) {
                    drawLine(
                        color = Color.Green,
                        start = caret.globalPosition,
                        end = Offset(caret.globalPosition.x, caret.globalPosition.y + caret.height),
                        strokeWidth = 2f
                    )
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    state.onEvent(
                        DocumentEvent.CoordinatesUpdated(
                            DocumentEvent.CoordType.LAZY_COLUMN,
                            coords
                        )
                    )
                }
        ) {
            // Iterate over text fields using their index.
            itemsIndexed(fieldList, key = { _, item -> item.id }) { index, docFieldEntry ->
                // Use a key so that Compose can manage state for list items efficiently.
                key(index) {
                    // Render an individual document text field.
                    DocTextField(
                        state,
                        index,
                        docFieldEntry,
                        onKeyEvent = { event, _ ->
                            handleDocKeyEvent(event, state)
                        },
                        state.focusedLine
                    )
                }
            }
        }

    }
}

/**
 * Composable function that renders a single text field inside the document.
 *
 * [DocTextField] wraps a [BasicTextField] to display and update each text field in the document.
 * It delegates state updates (text, layout coordinates, and text metrics) to the [DocumentState]
 * so that the single source of truth remains in the document state.
 *
 * @param state The [DocumentState] providing functions to update text field state.
 * @param entry The [DocumentTextFieldState] representing the current state of this text field.
 */
@Composable
fun DocTextField(
    state: DocumentState,
    index: Int,
    entry: DocumentTextFieldState,
    onKeyEvent: (KeyEvent, Int) -> Boolean,
    focusedLine: MutableState<Int>
) {
    val measurer = rememberLineMeasurer()
    state.setFieldTextMeasurer(index, measurer)
    val textStyle = state.currentTextStyle.value
    state.setFieldTextStyle(index, textStyle)
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = entry.textFieldValue,
        onValueChange = { newValue ->

            val oldValue = entry.textFieldValue
            var updatedNewValue = newValue
            if (newValue.text.length > oldValue.text.length) {
                println("UPDATED NEW VALUE****************************************************************************")
                println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& updatedNewValue: $updatedNewValue")
                updatedNewValue = state.applyCurrentCharStyleToProposedTFV(
                    prevValue = oldValue,
                    proposedNewValue = newValue
                )
            }
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& prevValue ${oldValue}")
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& newValue: $newValue")

            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&")
            state.processTextFieldValueUpdate(
                index = index,
                newValue = updatedNewValue,
                //newValue = newValue,
                textStyle = textStyle,
                maxWidthPx = state.maxWidth,
                measurer = measurer
            )
        },
        onTextLayout = { layoutResult ->
            println("&&&&&&&&&&&&&&&&&&&&&*****************&&&&&&&&&&&&&**************************************ONTEXTLAYOUT RESULT FIRED")
            state.onEvent(DocumentEvent.Text.Layout(index, layoutResult))
        },
        modifier = Modifier

            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {

                        val down = awaitFirstDown(requireUnconsumed = false)

                        //Always clear any existing selection on tap.
                        state.onEvent(DocumentEvent.Selection.NullifyState)

                        // Get layout coordinates
                        val fieldCoords = entry.layoutCoordinates ?: continue
                        val boxCoords = state.parentCoordinates.value.box ?: continue
                        val startPosLocal = down.position
                        // Convert local startPos to global coordinates
                        val startPosGlobal = boxCoords.localPositionOf(fieldCoords, startPosLocal)
                        println("-----")
                        println("")
                        println("DOWN event at global position: $startPosGlobal")
                        println("")
                        println("-----")

                        val fieldAndOffsetForClick =
                            state.getFieldAndOffsetForPosition(startPosGlobal)


                        var selectionTriggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.all { it.changedToUp() }) {
                                println("-----")
                                println("")
                                println("Pointer released, breaking out of movement loop")
                                println("")
                                println("-----")
                                break
                            }
                            val currentPosLocal = event.changes.first().position
                            // Convert local currentPos to global coordinates
                            val currentPosGlobal =
                                boxCoords.localPositionOf(fieldCoords, currentPosLocal)
                            val dx = currentPosGlobal.x - startPosGlobal.x
                            val dy = currentPosGlobal.y - startPosGlobal.y
                            val distance = sqrt(dx * dx + dy * dy)
                            println("Movement delta: dx = $dx, dy = $dy, distance = $distance")
                            if (!selectionTriggered && distance > 1f) {
                                println("-----")
                                println("")
                                println("DRAG STARTED: threshold met")
                                println("")
                                println("-----")
                                println("START POS GLOBAL: $startPosGlobal")
                                state.onEvent(DocumentEvent.Selection.StartDrag(startPosGlobal))
                                selectionTriggered = true
                            }
                            if (selectionTriggered) {
                                state.onEvent(DocumentEvent.Selection.UpdateDrag(currentPosGlobal))
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .onGloballyPositioned { coords ->
                state.onEvent(DocumentEvent.Text.CoordChanged(index, coords))
            }
            .onPreviewKeyEvent { event ->
                onKeyEvent(event, index)
            }
            .focusRequester(entry.focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    isFocused = true
                    state.onEvent(DocumentEvent.Selection.NullifyState)
                    println("focus updated to index ${index}")
                    state.onEvent(DocumentEvent.FocusChanged(index))
                } else {
                    isFocused = false
                }
            }
            .background(
                color =
                    (if (entry.hasNewLineAtEnd && isFocused) {
                        Color(0x33FF0000)
                    } else if (!isFocused && entry.hasNewLineAtEnd) {
                        Color.Magenta
                    } else if (isFocused && !entry.hasNewLineAtEnd) {
                        Color.LightGray
                    } else {
                        Color.Transparent
                    }),
                shape = RoundedCornerShape(4.dp)
            ),
        cursorBrush = SolidColor(Color.Blue),
        singleLine = true

    )
    LaunchedEffect(focusedLine.value) {
        if (focusedLine.value == index && index < state.documentTextFieldList.size) {
            try {
                println("FOCUS REQUESTED IN BASICTEXTENTRY$index")
                entry.focusRequester.requestFocus()
                state.onEvent(DocumentEvent.UpdateCaretPosition)
            } catch (e: Exception) {
            }
        }
    }

}