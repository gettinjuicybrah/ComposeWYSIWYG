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
        //contentAlignment = Alignment.Center
    ) {
        // Your existing document renders the text fields inside a LazyColumn.
        Document(state = state, modifier = Modifier.matchParentSize())

        // The selection overlay sits on top.
        // Only include the overlay if a selection is actively in progress.
        if (state.selectionState.isActive) {

            //println("---------------------------------------------------------ISSELECTING TRUE")
            SelectionOverlay(state = state, modifier = Modifier.matchParentSize())
        } else {
            //println("ISSELECTING FALSE----------------------------------------------------")
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

                // Loop over each segment and draw it individually:
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

        // Capture the coordinates for the overall document.
        .onGloballyPositioned { coords ->
            state.onEvent(DocumentEvent.CoordinatesUpdated(DocumentEvent.CoordType.DOCUMENT, coords))
            //println("document $coords")
        }

) {

    // Update caret position when layout or text fields change
    /*
    By including state.documentTextFieldList as a key,
    you’re telling Compose “whenever the identity or contents of this list change, cancel & relaunched this effect.”

SnapshotStateList implements equals as a structural (element-by-element) check, so if you add/remove items,
the key is considered “different” and your coroutine will restart.
     */
    LaunchedEffect(state.parentCoordinates.value, state.documentTextFieldList) {
        //state.updateCaretPosition()
        state.onEvent(DocumentEvent.DocumentLayoutChanged)
    }
    //variables of non‐primitive types are references to objects, not the objects themselves.
    /*
    var fieldList = … would let you do fieldList = anotherList later in that function body.
But composables are re-invoked from scratch on each recomposition,
so any var you declare is going to be reset anyway. In practice you almost always use val for local aliases.

SnapshotStateList and recomposition

A SnapshotStateList<T> is a special list whose mutations (e.g. add(), remove(), element updates) are part of Compose’s snapshot system.

Anytime you read that list during composition, Compose will record that “this composable depends on the contents of this list.”

For example, if you do:

Column {
  fieldList.forEach { textField ->
    Text(textField.text)
  }
}
the act of iterating (.forEach → .size, indexing, etc.) registers a read on the list.

Whenever the list is mutated, Compose will see that as “the snapshot this composable read has changed” and will schedule a recompose of any composables that read it.
     */
    val fieldList = state.documentTextFieldList

    Box(
        modifier = Modifier

            // Capture the coordinates for the Box container.
            .onGloballyPositioned { coords ->
                state.onEvent(DocumentEvent.CoordinatesUpdated(DocumentEvent.CoordType.BOX, coords))
               // println("box $coords")
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
        // Use a LazyColumn to lay out each text field.
        LazyColumn(
            modifier = Modifier
                // Capture coordinates for the LazyColumn container.
                .onGloballyPositioned { coords ->
                    state.onEvent(DocumentEvent.CoordinatesUpdated(DocumentEvent.CoordType.LAZY_COLUMN, coords))
                   // println("lazyCol $coords")
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

    // val isTooWide = layoutResult.size.width > state.maxWidthPx
    // Holds (fieldIndex, characterOffset)

    BasicTextField(
        // The current text and styling information for this field.
        value = entry.textFieldValue,
        // Delegate text change handling to the DocumentState.
        onValueChange = { newValue ->

            //state.onEvent(DocumentEvent.Text.Changed(index, newValue))
/*
            val result = measurer.measure(newValue.annotatedString, textStyle, constraints = Constraints(maxWidth = state.maxWidth), maxLines = 1, softWrap = false)
            val xPos   = state.maxWidth.toFloat() - Float.MIN_VALUE
            val offset = result.getOffsetForPosition(Offset(xPos, 0f))
            println("measured width: ${result.size.width} for text: ${newValue.text} linecount: ${result.lineCount} and didOverflowWidth: ${result.didOverflowWidth}, and offset: $offset")
*/
            state.processTextFieldValueUpdate(
                index = index,
                newValue = newValue,
                textStyle = textStyle,
                maxWidthPx = state.maxWidth,
                measurer = measurer
            )


        },
        // Delegate capturing of text layout results (metrics like cursor position).
        onTextLayout = { layoutResult ->
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
                        println("DOWN event at global position: $startPosGlobal")

                        val fieldAndOffsetForClick = state.getFieldAndOffsetForPosition(startPosGlobal)


                        var selectionTriggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.all { it.changedToUp() }) {
                                println("Pointer released, breaking out of movement loop")
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
                                println("**********************************&&&&&&&&&&&dRAG STARTED: threshold met")
                                println("START POS GLOBAL: $startPosGlobal")
                                state.onEvent(DocumentEvent.Selection.StartDrag(startPosGlobal))
                                selectionTriggered = true
                            }
                            if (selectionTriggered) {
                                state.onEvent(DocumentEvent.Selection.UpdateDrag(currentPosGlobal))
                            }
                            event.changes.forEach { it.consume() }
                        }
                        /*
                        if (selectionTriggered) {
                            println("Selection finished")
                            state.onEvent(DocumentEvent.Selection.Finish)
                        }

                         */
                    }
                }
            }

            // Update and store the layout coordinates for this field.
            .onGloballyPositioned { coords ->
                state.onEvent(DocumentEvent.Text.CoordChanged(index, coords))
                //println("${coords.size.width} WIDTH")

                //state.updateTextFieldCoords(index, coords)
            }
            .onPreviewKeyEvent { event ->
                onKeyEvent(event, index)
            }
            .focusRequester(entry.focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    isFocused = true
                    //maybe problematic
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
                    } else if(!isFocused && entry.hasNewLineAtEnd){
                        Color.Magenta
                    } else if(isFocused && !entry.hasNewLineAtEnd){
                        Color.LightGray
                    } else {
                        Color.Transparent
                    }) as Color
                /*
                    when {
                    entry.hasNewLineAtEnd -> Color(0x33FF0000)  // semi-transparent red
                    isFocused           -> Color.LightGray
                    else                -> Color.Transparent
                }*/,
                //color = if (isFocused) Color.LightGray else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            ),
            //.widthIn(max = state.maxWidth.dp),
        //This is to make the 'local' cursors transparent.
        cursorBrush = SolidColor(Color.Blue),
        singleLine = true

    )
    // Side-effect fires on *any* change to textFieldValue
    /*
    LaunchedEffect(entry.textFieldValue) {
        // react to the new value, no matter how it was changed
        println("Text is now: ${entry.textFieldValue.text}")
        state.processNewTextFieldValue(index, entry.textFieldValue, measurer, textStyle, state.maxWidth)
    }*/

    /*
    That little LaunchedEffect is there so that, any time your shared focusedLine.value changes, each DocTextField gets a chance to notice “hey—my index just became the focused one” and then imperatively call focusRequester.requestFocus(). In other words:

When it runs

Compose walks every DocTextField and sees your LaunchedEffect(focusedLine.value) call.

If focusedLine.value has not changed since last composition, nothing happens.

If it has changed, Compose cancels the old effect coroutines and launches new ones—one in each DocTextField.

Why it can be useful

Programmatic focus: lets you drive focus from your DocumentState (for example, if you hit “Enter” in line 2 and you want line 3 to grab focus).

Keeps your effect in sync: because it’s keyed on the snapshot state, it will only re-run when the line truly changes, not on every recomposition.

When it’s redundant

If you only ever set focusedLine inside the very same onFocusChanged { … state.updateFocusedLine(index) }, you already have a round-trip:

user taps → onFocusChanged fires → you update focusedLine → that triggers your LaunchedEffect → you call requestFocus() (but it was already focused!).

In that narrow case you could drop the LaunchedEffect and let the normal focus-system do its thing.

Bottom line:
Yes, it does “something useful” whenever you change focusedLine in your state from outside that exact text field (for example, via keyboard navigation or a parent-level action). It won’t spin on
every recomposition (the key guards you) and it won’t fire on the wrong field (you check index == focusedLine.value). If you never drive focusedLine externally, it’s harmless but effectively no-op.
     */
    LaunchedEffect(focusedLine.value) {
        if (focusedLine.value == index && index < state.documentTextFieldList.size) {
            try {
                println("FOCUS REQUESTED IN BASICTEXTENTRY$index")
                entry.focusRequester.requestFocus()
                state.onEvent(DocumentEvent.UpdateCaretPosition)
            } catch (e: Exception) {
                // Handle exception gracefully
            }
        }
    }

}