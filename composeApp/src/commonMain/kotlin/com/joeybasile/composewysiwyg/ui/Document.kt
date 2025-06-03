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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp
import com.joeybasile.composewysiwyg.model.*
import com.joeybasile.composewysiwyg.model.caret.updateGlobalCaretPosition

/**
 * Renders the whole document.  Each [Field] becomes one item in the vertical list.
 *
 */
@Composable
fun Document(
    state: DocumentState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier

            .onGloballyPositioned { coords ->
                state.setRootCoords(coords)
            }
            .drawWithContent {
                drawContent()
                val caret = state.globalCaret.value
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
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(state.fields, key = { it.id }) { field ->
                Field(
                    field = field,
                    // Hoist the call to the state's update method
                    onBlockPositioned = { fieldId, blockId, layoutCoordinates ->
                        state.updateBlockCoords(fieldId, blockId, layoutCoordinates)
                    },
                    onKeyEvent = { event, _ ->
                        handleDocKeyEvent(event, state)
                    },
                    focusedBlock = state.focusedBlock,
                    updateCaret = state.updateGlobalCaretPosition()
                    )
            }
        }
    }
}

/**
 * Lay out the blocks that make up a single [Field] left‑to‑right.
 *
 */
@Composable
private fun Field(
    field: Field,
    onBlockPositioned: (fieldId: String, blockId: String, LayoutCoordinates) -> Unit,
    onKeyEvent: (KeyEvent, String) -> Boolean,
    focusedBlock: MutableState<String>,
    updateCaret: Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        field.blocks.forEach { block ->
            key(block.id) {
                when (block) {
                    is TextBlock -> TextBlock(
                        block = block,
                        onPositioned = { blockId, layoutCoordinates ->
                            onBlockPositioned(field.id, blockId, layoutCoordinates)
                        },
                        onKeyEvent = onKeyEvent,
                        focusedBlock = focusedBlock,
                        updateCaret = updateCaret
                    )
                    is ImageBlock -> ImageBlock(
                        block,
                        onPositioned = { blockId, layoutCoordinates ->
                            onBlockPositioned(field.id, blockId, layoutCoordinates)
                        },
                        onKeyEvent = onKeyEvent,
                        focusedBlock = focusedBlock
                    )
                    is DelimiterBlock -> DelimiterBlock(block)
                }
            }
        }
    }
}

/** Inline editable text.
 * */
@Composable
private fun TextBlock(
    block: TextBlock,
    onPositioned: (blockId: String, LayoutCoordinates) -> Unit,
    onKeyEvent: (KeyEvent, String) -> Boolean,
    focusedBlock: MutableState<String>,
    updateCaret: Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = block.value,
        onValueChange = {
            block.value = it
        },
        onTextLayout = {

        },
        modifier = Modifier
            .defaultMinSize(minWidth = 1.dp) // keeps caret visible even if empty
            .onGloballyPositioned { layoutCoordinates ->
                onPositioned(block.id, layoutCoordinates)
            }
            .focusRequester(block.focusRequester)
            .onPreviewKeyEvent { event ->
                onKeyEvent(event, block.id)
            }
            .focusRequester(block.focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    isFocused = true
                    //state.onEvent(DocumentEvent.Selection.NullifyState)
                    //println("focus updated to index ${index}")
                    //state.onEvent(DocumentEvent.FocusChanged(index))
                } else {
                    isFocused = false
                }
            },
        cursorBrush = SolidColor(Color.Blue),
        singleLine = false
    )
    LaunchedEffect(focusedBlock.value) {
        if (focusedBlock.value == block.id) {
            try {
                println("FOCUS REQUESTED IN TextBlock ${block.value.text}")
                block.focusRequester.requestFocus()
                updateCaret
            } catch (e: Exception) {
            }
        }
    }
}

/** Simple bitmap render; you can swap in AsyncImage, Coil, etc. later. */
@Composable
private fun ImageBlock(
    block: ImageBlock,
    onPositioned: (blockId: String, LayoutCoordinates) -> Unit,
    onKeyEvent: (KeyEvent, String) -> Boolean,
    focusedBlock: MutableState<String>
) {
    var isFocused by remember { mutableStateOf(false) }

    Image(
        bitmap = block.bitmap,
        contentDescription = null,
        modifier = Modifier
            .widthIn(max = 300.dp) // prevent over‑size images blowing up the row
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .focusRequester(block.focusRequester)
            .onGloballyPositioned { layoutCoordinates ->
                onPositioned(block.id, layoutCoordinates)
            },
    )
}

/**
 * Placeholder implementation.
 *
 * * `NewLine` ends the current row – achieved by a zero‑size spacer with `fill` that forces line‑break.
 * * `Tab` inserts horizontal space.
 */
@Composable
private fun DelimiterBlock(block: DelimiterBlock) {
    when (block.kind) {
        is NewLine -> Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.dp)
        )
        is Tab -> Spacer(
            Modifier.width(16.dp)
        )
    }
}
