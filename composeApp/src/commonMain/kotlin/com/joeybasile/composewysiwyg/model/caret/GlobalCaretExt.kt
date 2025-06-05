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
    val caret = globalCaret.value
    val field  = caret.fieldId ?.let(::getFieldById)        ?: return
    val blocks = field.blocks
    val idx    = blocks.indexOfFirst { it.id == caret.blockId }.takeIf { it >= 0 } ?: return

    // ── 1. Try to advance inside the current TextBlock ─────────────────────────
    val cur = blocks[idx]
    if (cur is Block.TextBlock && caret.offsetInBlock < cur.length) {
        globalCaret.value = caret.copy(offsetInBlock = caret.offsetInBlock + 1)
        onGlobalCaretMoved()
        return
    }

    // ── 2. Find the next navigable block in document order ─────────────────────
    fun Sequence<Field>.firstNavPair(): Pair<Field, Block>? =
        flatMap { f -> f.blocks.asSequence().map { f to it } }
            .firstOrNull { (_, b) -> b.isNavigable() }

    val remainderOfThisField = blocks.drop(idx + 1)
    val remainderPairs = remainderOfThisField
        .asSequence()
        .map { field to it }

    val laterPairs = fields
        .drop(fields.indexOf(field) + 1)              // fields after the current one
        .asSequence()
        .flatMap { f -> f.blocks.asSequence().map { blk -> f to blk } }

    val result = sequenceOf(remainderPairs, laterPairs)
        .flatten()
        .firstOrNull { (_, blk) -> blk.isNavigable() }   // blk == pair.second

    result?.let { (newField, newBlock) ->
        globalCaret.value = caret.copy(
            fieldId       = newField.id,
            blockId       = newBlock.id,
            offsetInBlock = 0
        )
        onGlobalCaretMoved()
    }
}

/**
 * Move one “character” (or block) to the left.
 */
fun DocumentState.moveGlobalCaretLeft() {
    val caret = globalCaret.value
    val field  = caret.fieldId ?.let(::getFieldById)        ?: return
    val blocks = field.blocks
    val idx    = blocks.indexOfFirst { it.id == caret.blockId }.takeIf { it >= 0 } ?: return

    // ── 1. Try to retreat inside current TextBlock ─────────────────────────────
    val cur = blocks[idx]
    if (cur is Block.TextBlock && caret.offsetInBlock > 0) {
        globalCaret.value = caret.copy(offsetInBlock = caret.offsetInBlock - 1)
        onGlobalCaretMoved()
        return
    }

    // ── 2. Walk backwards to previous navigable block in document order ────────
    fun Sequence<Field>.lastNavPair(): Pair<Field, Block>? =
        flatMap { f -> f.blocks.asReversed().asSequence().map { f to it } }
            .firstOrNull { (_, b) -> b.isNavigable() }

    val beforeThisField = blocks.take(idx).asReversed()
        .firstOrNull { it.isNavigable() }?.let { field to it }
        ?: fields.take(fields.indexOf(field))               // earlier fields
            .asReversed().asSequence()
            .flatMap { f -> f.blocks.asReversed().asSequence().map { f to it } }
            .firstOrNull { (_, b) -> b.isNavigable() }

    beforeThisField?.let { (newField, newBlock) ->
        val newOffset = when (newBlock) {
            is Block.TextBlock -> newBlock.length
            else         -> 0
        }
        globalCaret.value = caret.copy(
            fieldId       = newField.id,
            blockId       = newBlock.id,
            offsetInBlock = newOffset
        )
        onGlobalCaretMoved()
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

private fun Block.isNavigable() = this is Block.TextBlock || this is Block.ImageBlock || this is Block.DelimiterBlock

/**
 * Put caret inside the field on the block whose horizontal span best matches [targetX].
 * Returns null if the field has no navigable blocks.
 */
private fun DocumentState.placeCaretAtX(field: Field, targetX: Float) {
    val textBlocks = field.blocks.filterIsInstance<Block.TextBlock>()
    if (textBlocks.isEmpty()) return

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