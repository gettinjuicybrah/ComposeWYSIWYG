package com.joeybasile.composewysiwyg.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.util.handleDocKeyEvent

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import com.joeybasile.composewysiwyg.model.document.Block
import com.joeybasile.composewysiwyg.model.document.Field
import com.joeybasile.composewysiwyg.model.bprocessTFVUpdate
import com.joeybasile.composewysiwyg.model.handleFocusChange
import com.joeybasile.composewysiwyg.model.handleOnTextLayout
import com.joeybasile.composewysiwyg.model.document.normalise
import com.joeybasile.composewysiwyg.model.updateBlockCoords
import com.joeybasile.composewysiwyg.model.updateGlobalCaretPosition
import androidx.compose.ui.input.pointer.changedToUp
import coil3.compose.*
import com.joeybasile.composewysiwyg.model.onGlobalCaretMoved
import com.joeybasile.composewysiwyg.model.selection.finishSelection
import com.joeybasile.composewysiwyg.model.selection.startDragSelection
import com.joeybasile.composewysiwyg.model.selection.updateDragSelection
import kotlin.math.sqrt

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
                state.onGlobalCaretMoved()
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
        LazyColumn() {
            items(state.fields, key = { it.id }) { field ->
                println("FIELDS:")
                println()
                println("------------------------")
                println()
                val curIndx = state.fields.indexOfFirst { it.id == field.id }
                println("Field Index $curIndx and Field ID: ${field.id}")
                println("Content of Field index: $curIndx:${field.blocks}")
                for(blk in field.blocks){
                    println("   {{{{")
                    println("   Block Index: ${field.blocks.indexOfFirst { it.id == blk.id }}")
                    println("Block info: ${blk}")
                    println("}}}}")
                }
                println()
                println("------------------------")
                println()
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
    Row(verticalAlignment = Alignment.Bottom) {
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
                    is Block.ImageBlock -> ImageBlockView(
                        block,
                        fieldId = field.id,
                        state,
                        modifier
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
                    /* ────────────────────────────────────────────
             *  TAP   → clear selection, move caret
             *  DRAG  → live-update green selection rects
             * ──────────────────────────────────────────── */
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {

                                /* ── 0. Wait for first finger/mouse down ––––––– */
                                val down = awaitFirstDown(requireUnconsumed = false)

                                /* Clear any old selection immediately on tap     */
                                state.finishSelection()

                                /* Convert the DOWN position to *root* coordinates */
                                val blockCoords = block.layoutCoordinates ?: continue
                                val rootCoords  = state.rootReferenceCoordinates
                                    .value.coordinates ?: continue
                                val startGlobal = rootCoords.localPositionOf(
                                    blockCoords, down.position
                                )

                                var selectionTriggered = false

                                /* ── 1. Track movement until all pointers up ––– */
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    if (ev.changes.all { it.changedToUp() }) {
                                        println("Pointer released, breaking out of movement loop")
                                        break
                                    }

                                    val curGlobal = rootCoords.localPositionOf(
                                        blockCoords, ev.changes.first().position
                                    )

                                    /* threshold ≈ 1 px to avoid spurious drags   */
                                    val dx = curGlobal.x - startGlobal.x
                                    val dy = curGlobal.y - startGlobal.y
                                    val dist2 = sqrt(dx*dx + dy*dy)

                                    if (!selectionTriggered && dist2 > 1f) {
                                        state.startDragSelection(startGlobal)
                                        selectionTriggered = true
                                    }
                                    if (selectionTriggered) {
                                        state.updateDragSelection(curGlobal)
                                    }

                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
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
                            state.finishSelection()
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
                state.onGlobalCaretMoved()
                //state.updateGlobalCaretPosition()
            } catch (e: Exception) {
                println("CATACHY ")
            }
        }
    }
}
@Composable
fun ImageBlockView(block: Block.ImageBlock,
                   fieldId: String,
                   state: DocumentState,
                   modifier: Modifier = Modifier) {

    val payload = remember(block.payloadId) {
        state.imageStore[block.payloadId]   // could be null if gc'ed
    }

    // Move Composable calls outside of remember block
    val asyncPainter = payload?.let { rememberAsyncImagePainter(it.bytes) }
    val fallbackPainter = rememberVectorPainter(Icons.Default.Star)

    // Now use regular conditional logic to choose the painter
    val painter = asyncPainter ?: fallbackPainter

    Image(
        painter = painter,
        contentDescription = "inline image",
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                state.updateBlockCoords(fieldId, block, layoutCoordinates)
            }
            .pointerInput(Unit) {
                // enable selection-start or drag-to-resize later
            }
            .focusRequester(block.focusRequester)
            .onPreviewKeyEvent { ev -> handleDocKeyEvent(ev, state) }
    )
}
/** Simple bitmap render; you can swap in AsyncImage, Coil, etc. later. */
/*
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
*/
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
        }
        Block.DelimiterBlock.Kind.Tab -> TODO()
    }
}