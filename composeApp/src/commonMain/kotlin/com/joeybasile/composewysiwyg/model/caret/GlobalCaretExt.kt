package com.joeybasile.composewysiwyg.model.caret
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.joeybasile.composewysiwyg.model.Block
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.Field
import com.joeybasile.composewysiwyg.model.*
import com.joeybasile.composewysiwyg.model.getBlockById
import com.joeybasile.composewysiwyg.model.getFieldById
import com.joeybasile.composewysiwyg.model.getTextBlockById
import com.joeybasile.composewysiwyg.model.linewrap.isEmptyField
import com.joeybasile.composewysiwyg.model.style.CurrentCharStyle
import com.joeybasile.composewysiwyg.model.style.DefaultToolbarState
import com.joeybasile.composewysiwyg.model.style.ToolbarState
import com.joeybasile.composewysiwyg.model.style.getSpanStylesAt
import com.joeybasile.composewysiwyg.model.style.hasSpanStyleAt
import com.joeybasile.composewysiwyg.model.style.resetCurrentCharStyleToDefault
import com.joeybasile.composewysiwyg.model.style.resetToolbarToDefault
import com.joeybasile.composewysiwyg.model.updateFocusedBlock

import kotlin.math.abs

/**
 * Move one “character” (or block) to the right.
 *
 *  • If we’re inside a TextBlock and not at the end of the text → bump offset.
 *  • Otherwise walk to the next *navigable* block in the current field,
 *    then to the first navigable block of following fields as needed.
 */
fun DocumentState.moveGlobalCaretRight() {
    val caret  = globalCaret.value
    var field  = caret.fieldId?.let(::getFieldById) ?: return
    var blocks = field.blocks
    var idx    = blocks.indexOfFirst { it.id == caret.blockId }.takeIf { it >= 0 } ?: return

    // 1) Advance inside current TextBlock if possible
    val cur = blocks[idx] as? Block.TextBlock
    if (cur != null && caret.offsetInBlock < cur.length) {
        globalCaret.value = caret.copy(offsetInBlock = caret.offsetInBlock + 1)
        onGlobalCaretMoved(); return
    }

    // 2) Walk forward until we hit the NEXT caret-host
    while (true) {
        idx++
        // Cross field boundary
        if (idx >= blocks.size) {
            val fieldIdx = fields.indexOf(field)
            if (fieldIdx == fields.lastIndex) return            // bottom of document
            val nextField = fields[fieldIdx + 1]
            idx    = -1                      // will become 0 on next loop turn
            blocks = nextField.blocks
            field  = nextField
            continue
        }

        val blk = blocks[idx]
        if (blk.isCaretHost()) {
            globalCaret.value = caret.copy(
                fieldId       = field.id,
                blockId       = blk.id,
                offsetInBlock = 0
            )
            onGlobalCaretMoved()
            return
        }
        /*  blk is traversal unit → simply continue looping  */
    }
}

/**
 * Move one “character” (or block) to the left.
 */
fun DocumentState.moveGlobalCaretLeft() {
    var field  = globalCaret.value.fieldId?.let(::getFieldById) ?: return
    var blocks = field.blocks
    var idx    = blocks.indexOfFirst { it.id == globalCaret.value.blockId }
    if (idx < 0) return

    // 1) Try to retreat inside current TextBlock
    (blocks[idx] as? Block.TextBlock)?.let { tb ->
        val off = globalCaret.value.offsetInBlock
        if (off > 0) {
            globalCaret.value = globalCaret.value.copy(offsetInBlock = off - 1)
            onGlobalCaretMoved(); return
        }
    }

    // 2) Walk backward until previous caret-host
    while (true) {
        idx--
        if (idx < 0) {                              // cross field boundary
            val prevFieldIdx = fields.indexOf(field) - 1
            if (prevFieldIdx < 0) return            // top of doc
            field  = fields[prevFieldIdx]
            blocks = field.blocks
            idx    = blocks.lastIndex
        }
        val blk = blocks[idx]
        if (blk.isCaretHost()) {
            val newOffset = (blk as Block.TextBlock).length
            globalCaret.value = globalCaret.value.copy(
                fieldId       = field.id,
                blockId       = blk.id,
                offsetInBlock = newOffset
            )
            onGlobalCaretMoved(); return
        }
        /* traversal unit → keep looping */
    }
}

/**
 * Move vertically *down* one line (i.e. into the next Field) trying to keep the
 * same X-coordinate when possible.
 */
fun DocumentState.moveGlobalCaretDown() {
    val caret = globalCaret.value
    val curFieldIdx = fields.indexOfFirst { it.id == caret.fieldId }.takeIf { it >= 0 } ?: return
    if (curFieldIdx >= fields.lastIndex) return                         // already at bottom

    val nextField = fields[curFieldIdx + 1]

    placeCaretAtX(nextField, caret.globalPosition.x) ?: return
}

/**
 * Move vertically *up* one line (i.e. to the previous Field).
 */
fun DocumentState.moveGlobalCaretUp() {
    val caret = globalCaret.value
    val curFieldIdx = fields.indexOfFirst { it.id == caret.fieldId }.takeIf { it >= 0 } ?: return
    if (curFieldIdx == 0) return                                         // already at top

    val prevField = fields[curFieldIdx - 1]

    placeCaretAtX(prevField, caret.globalPosition.x) ?: return
}

/* ───────────────────────── helper utilities ───────────────────────── */

/** True caret-hosts; traversal units don’t qualify. */
private fun Block.isCaretHost() = this is Block.TextBlock

/** Traversal units that the caret can *pass* but never *sit on*. */
private fun Block.isTraversalUnit() =
    this is Block.ImageBlock || this is Block.DelimiterBlock

/**
 * Put caret inside the field on the block whose horizontal span best matches [targetX].
 * Returns null if the field has no navigable blocks.
 */
private fun DocumentState.placeCaretAtX(field: Field, targetX: Float) {
    val textBlocks = field.blocks.filterIsInstance<Block.TextBlock>()
    if (textBlocks.isEmpty()) return    //SHOULD be impossible

    // Choose the block whose left edge is closest but not greater than targetX
    val best = textBlocks.minByOrNull { blk ->
        val coords = blk.layoutCoordinates ?: return@minByOrNull Float.MAX_VALUE
        val left   = coords.positionInRoot().x
        if (targetX >= left) abs(targetX - left) else Float.MAX_VALUE
    } ?: return

    val coords  = best.layoutCoordinates ?: return
    val layout  = best.textLayoutResult   ?: return
    val localX  = (targetX - coords.positionInRoot().x).coerceAtLeast(0f)
    val offset  = layout.getOffsetForPosition(Offset(localX, 0f))

    globalCaret.value = globalCaret.value.copy(
        fieldId       = field.id,
        blockId       = best.id,
        offsetInBlock = offset
    )
    onGlobalCaretMoved()
}