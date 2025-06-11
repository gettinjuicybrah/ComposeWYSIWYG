package com.joeybasile.composewysiwyg.model.image

import androidx.compose.ui.focus.FocusRequester
import com.joeybasile.composewysiwyg.model.Block
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.emptyTextBlock
import com.joeybasile.composewysiwyg.model.linewrap.splitTextBlock
import com.joeybasile.composewysiwyg.model.normalise
import com.joeybasile.composewysiwyg.model.onGlobalCaretMoved
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun DocumentState.insertImageAtCaret(bytes: ByteArray, mime: String) {
    val caret = globalCaret.value
    val fieldIdx = fields.indexOfFirst { it.id == caret.fieldId }
    val blocks = fields[fieldIdx].blocks
    val blockIdx = blocks.indexOfFirst { it.id == caret.blockId }
    val hostTB = blocks[blockIdx] as Block.TextBlock

    /* 1 ─ split host text at caret */
    val (leftTB, rightTB) = splitTextBlock(hostTB, caret.offsetInBlock)

    /* 2 ─ build payload + blocks */
    val payload = ImagePayload(
        bytes = bytes,
        mime = mime,
        naturalWidth = 0,   // fill in from decoder
        naturalHeight = 0
    )
    imageStore[payload.id] = payload

    val imgBlock = Block.ImageBlock(
        id = Uuid.random().toString(),
        payloadId = payload.id,
        width = payload.naturalWidth,            // for now 1:1 pixels
        focusRequester = FocusRequester()
    )
    val emptyTB = emptyTextBlock()

    /* 3 ─ splice into list and normalise */
    blocks.apply {
        removeAt(blockIdx)
        addAll(blockIdx, listOf(leftTB, imgBlock, emptyTB, rightTB))
    }
    fields[fieldIdx] = fields[fieldIdx].normalise()

    /* 4 ─ move caret to emptyTB@offset0 */
    globalCaret.value = caret.copy(
        blockId = emptyTB.id,
        offsetInBlock = 0
    )
    onGlobalCaretMoved()
}

data class ImagePayload @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val bytes: ByteArray,
    val mime: String, // “image/png”, “image/jpeg”…
     val naturalWidth: Int, // px
     val naturalHeight: Int // px )
)