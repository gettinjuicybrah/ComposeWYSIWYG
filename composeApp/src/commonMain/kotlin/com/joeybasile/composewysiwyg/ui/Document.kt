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
import com.joeybasile.composewysiwyg.util.handleDocKeyEvent
import kotlin.math.sqrt

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.joeybasile.composewysiwyg.model.Block
import com.joeybasile.composewysiwyg.model.Field
import com.joeybasile.composewysiwyg.model.aprocessTFVUpdate
import com.joeybasile.composewysiwyg.model.bprocessTFVUpdate
import com.joeybasile.composewysiwyg.model.handleFocusChange
import com.joeybasile.composewysiwyg.model.handleOnTextLayout
import com.joeybasile.composewysiwyg.model.handleOnValueChange
import com.joeybasile.composewysiwyg.model.normalise
import com.joeybasile.composewysiwyg.model.processTFVUpdate
import com.joeybasile.composewysiwyg.model.updateBlockCoords
import com.joeybasile.composewysiwyg.model.updateGlobalCaretPosition

/**
 * Renders the whole document.  Each [Field] becomes one item in the vertical list.
 *
 */
@Composable
fun Document(
    state: DocumentState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.rootReferenceCoordinates.value, state.fields){
        println("root reference coords changed. LaunchedEffect in document. About to call updateGlobalCaretPosition()")
        state.updateGlobalCaretPosition()
    }
    Box(
        modifier = Modifier

            .onGloballyPositioned { coords ->
                state.setRootCoords(coords)
            }
            .drawWithContent {
                drawContent()
                val caret = state.globalCaret.value
                if (caret.globalPosition != Offset.Unspecified && caret.isVisible) {
                    //println("-------------------------------------------------------------------------------")

                    //println("-------------------------------------------------------------------------------")

                    //println("-------------------------------------------------------------------------------")
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
                    onKeyEvent = { event, _ ->
                        handleDocKeyEvent(event, state)
                    },
                    focusedBlock = state.focusedBlock,
                    state = state,
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
    state: DocumentState,
    onKeyEvent: (KeyEvent, String) -> Boolean,
    focusedBlock: MutableState<String>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        field.normalise()
        field.blocks.forEach { block ->
            key(block.id) {
                when (block) {
                    is Block.TextBlock -> TextBlock(
                        block = block,
                        fieldId = field.id,
                        state,
                        onKeyEvent = onKeyEvent,
                        focusedBlock = focusedBlock,
                        )
                    is Block.ImageBlock -> ImageBlock(
                        block,
                        onKeyEvent = onKeyEvent,
                        focusedBlock = focusedBlock
                    )
                    is Block.DelimiterBlock -> DelimiterBlock(block)
                }
            }
        }
    }
}

/** Inline editable text.
 * */
@Composable
private fun TextBlock(
    block: Block.TextBlock,
    fieldId: String,
    state: DocumentState,
    onKeyEvent: (KeyEvent, String) -> Boolean,
    focusedBlock: MutableState<String>
) {
    var isFocused by remember { mutableStateOf(false) }

            BasicTextField(
                value = block.textFieldValue,
                //value = block.value,
                onValueChange = { newValue ->
                    state.bprocessTFVUpdate(fieldId, block.id, newValue)
                    //state.handleOnValueChange(fieldId, block.id, newValue)
                    //println("HANDLED TEXTFIELDVALUE. text: ${newValue.text}")
                    println("")
                    println("")
                },
                onTextLayout = {
                    (state.fields[state.fields.indexOfFirst { it.id == fieldId }].normalise())
                    state.handleOnTextLayout(fieldId, block.id, it)
                    //normalize the field whenever a layout result occurs.

                },
                modifier = Modifier
                    .background(color = Color.LightGray)
                    .onGloballyPositioned { layoutCoordinates ->
                        state.updateBlockCoords(fieldId, block, layoutCoordinates)
                    }
                    .onPreviewKeyEvent { event ->
                        onKeyEvent(event, block.id)
                    }

                    .focusRequester(block.focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            isFocused = true
                            state.handleFocusChange(fieldId, block.id)
                        } else {
                            isFocused = false
                        }
                    },
                cursorBrush = SolidColor(Color.Blue),
                singleLine = true
            )

    LaunchedEffect(focusedBlock.value) {
        if (focusedBlock.value == block.id) {
            try {
                println("FOCUS REQUESTED IN TextBlock ${block.textFieldValue.text}")
                block.focusRequester.requestFocus()
            //state.handleFocusChange(fieldId, block.id)
                state.updateGlobalCaretPosition()
            } catch (e: Exception) {
                println("CATACHY ")
            }
        }
    }
}

/** Simple bitmap render; you can swap in AsyncImage, Coil, etc. later. */
@Composable
private fun ImageBlock(
    block: Block.ImageBlock,
    onKeyEvent: (KeyEvent, String) -> Boolean,
    focusedBlock: MutableState<String>,
    modifier: Modifier = Modifier
        .background(color = Color.Red)
) {
    var isFocused by remember { mutableStateOf(false) }
    Text("image")
/*
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

 */
}

/**
 * Placeholder implementation.
 *
 * * `NewLine` ends the current row – achieved by a zero‑size spacer with `fill` that forces line‑break.
 * * `Tab` inserts horizontal space.
 */
@Composable
private fun DelimiterBlock(
    block: Block.DelimiterBlock,
    modifier : Modifier = Modifier
        .background(color = Color.Yellow)) {

    when (block.kind) {
        Block.DelimiterBlock.Kind.NewLine -> {
         Text(modifier = modifier, text = "new line")
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                   // .background(color = Color.Blue)
            )
        }
        Block.DelimiterBlock.Kind.Tab -> TODO()
    }
}