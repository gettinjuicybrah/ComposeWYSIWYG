package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.document.Block
import com.joeybasile.composewysiwyg.model.document.Field
import com.joeybasile.composewysiwyg.model.document.emptyTextBlock
import com.joeybasile.composewysiwyg.model.document.getFieldIndex
import com.joeybasile.composewysiwyg.model.document.getLocGlobalFieldOffsetForABlock
import com.joeybasile.composewysiwyg.model.document.normalizeBlockList
import com.joeybasile.composewysiwyg.model.getFieldById
import com.joeybasile.composewysiwyg.model.onGlobalCaretMoved
import kotlin.math.max
import kotlin.math.min

fun DocumentState.setAnchorCaret(anchor: GlobalSelectionCaretState) {
    globalSelectionState = globalSelectionState
        .copy(
            anchor = anchor
        )
}

fun DocumentState.setFocusCaret(focus: GlobalSelectionCaretState) {
    globalSelectionState = globalSelectionState
        .copy(
            focus = focus
        )
}

fun DocumentState.rebuildSelectionFromAnchorAndFocus() {

    // Guard: If selection is not truly active or anchor/focus are somehow null,
    // ensure segments are cleared and do nothing further.
    if (!globalSelectionState.isActive || globalSelectionState.anchor == null || globalSelectionState.focus == null) {
        if (globalSelectionState.segments.isNotEmpty()) {
            // This implies an inconsistent state; clear segments.
            globalSelectionState = globalSelectionState.copy(segments = emptyList())
        }
        // If not active, there's nothing to rebuild. If anchor/focus became null
        // then isActive would be false. finishSelection() might have already run.
        return
    }

    val currentAnchor = globalSelectionState.anchor!! // Non-null due to isActive check
    val currentFocus = globalSelectionState.focus!!   // Non-null due to isActive check
    val (anchorIndices, focusIndices) = getGlobalSelectionCaretIndices(currentAnchor, currentFocus)
        ?: return
    val maxValidFieldIndex = if (fields.isEmpty()) -1 else fields.size - 1


    // --- VALIDATION START ---
    // 1. Check if field indices are within the current bounds of documentTextFieldList
    if (anchorIndices.fieldIndex < 0 || anchorIndices.fieldIndex > maxValidFieldIndex ||
        focusIndices.fieldIndex < 0 || focusIndices.fieldIndex > maxValidFieldIndex
    ) {
        // Anchor or Focus fieldIndex is out of bounds. The selection is structurally invalid.
        finishSelection() // Clears anchor, focus, segments, and sets isActive to false.
        return // Stop further processing.
    }

// 1b. **new**: blockIndex in range
    val anchorBlocks = fields[anchorIndices.fieldIndex].blocks
    val focusBlocks = fields[focusIndices.fieldIndex].blocks
    if (anchorIndices.blockIndex !in anchorBlocks.indices ||
        focusIndices.blockIndex !in focusBlocks.indices
    ) {
        finishSelection()
        return
    }
    // 2. Check if offsets are within the bounds of their respective field's current text length.
    // These fields are guaranteed to exist due to the previous check.
    val anchorBlock =
        fields[anchorIndices.fieldIndex].blocks[anchorIndices.blockIndex] as Block.TextBlock
    val focusBlock =
        fields[focusIndices.fieldIndex].blocks[focusIndices.blockIndex] as Block.TextBlock



    if (currentAnchor.offsetInBlock < 0 || currentAnchor.offsetInBlock > anchorBlock.length ||
        currentFocus.offsetInBlock < 0 || currentFocus.offsetInBlock > focusBlock.length
    ) {
        // Anchor or Focus offset is out of bounds for the current text content of their fields.
        // The selection is content-wise invalid.
        finishSelection() // Clears the invalid selection.
        return // Stop further processing.
    }
    // --- VALIDATION END ---

    // If all validations pass, it's safe to compute segments.
    // The original call to computeSegmentsBetween happens here:
    val newSegments = computeSegmentsBetween(currentAnchor, currentFocus)
    globalSelectionState = globalSelectionState.copy(segments = newSegments)

    /*
    val a = selectionState.anchor!!  // immutable origin
    val f = selectionState.focus!!   // current frontier
    // Derive startField/startOffset from 'a'
    selectionState = selectionState.copy(
        segments = computeSegmentsBetween(a, f)
    )

     */
}

/**
 * Result of collapsing a multi-field selection.
 *
 * After the merge you should:
 *  • replace the block list of fields[startFieldIndex] with `mergedBlocks`
 *  • delete every field whose index is in (startFieldIndex+1) … endFieldIndex
 *  • move the caret to (caretBlockId, caretOffsetInBlock) inside the *new*
 *    block list of the start field
 */
data class MergeResult(
    val startFieldIndex: Int,
    val endFieldIndex: Int,
    val mergedBlocks: List<Block>,
    val caretBlockId: String,
    val caretOffsetInBlock: Int
)

/* build a *prefix* of a block – everything **before** `offset`                */
private fun Block.prefix(offset: Int): Block? = when (this) {
    is Block.TextBlock -> {
        if (offset == 0) null else copy(
            textFieldValue = textFieldValue.copy(
                annotatedString = textFieldValue.annotatedString.subSequence(0, offset)
            ),
            layoutCoordinates = null,
            textLayoutResult = null
        )
    }

    is Block.ImageBlock, is Block.DelimiterBlock ->
        if (offset <= 0) null else this        // offset == 1 ⇒ keep block
}

/* build a *suffix* of a block – everything **after** `offset`                */
private fun Block.suffix(offset: Int): Block? = when (this) {
    is Block.TextBlock -> {
        if (offset >= length) null else copy(
            textFieldValue = textFieldValue.copy(
                annotatedString = textFieldValue.annotatedString
                    .subSequence(offset, length)
            ),
            layoutCoordinates = null,
            textLayoutResult = null
        )
    }

    is Block.ImageBlock, is Block.DelimiterBlock ->
        if (offset >= 1) null else this        // offset == 0 ⇒ keep block
}

/* guarantees there is at least one selectable TextBlock                      */
private fun ensureNonEmpty(blocks: MutableList<Block>) {
    if (blocks.none { it is Block.TextBlock }) {
        blocks.add(emptyTextBlock())
    }
    normalizeBlockList(blocks)                 // P-2, P-3, P-4
}

/**
 * Collapses the current GlobalSelection into a single caret and returns all
 * information the caller needs to mutate the document.
 */
fun DocumentState.mergeSelection(): MergeResult? {

    /* ─── 0.  Preconditions & ordering ──────────────────────────────────── */

    val anchor = globalSelectionState.anchor ?: return null
    val focus = globalSelectionState.focus ?: return null

    val (start, end) =
        getOrderedGlobalSelectionCaretsWithIndices(anchor, focus) ?: return null
    if (start == end) return null                    // nothing to merge

    val startFieldIdx = start.fieldIndex
    val endFieldIdx = end.fieldIndex

    /* ─── 1.  Build new block list for the *start* field ────────────────── */

    val mergedBlocks = mutableListOf<Block>()

    /* –– 1a.  prefix blocks that are completely before the caret          */
    mergedBlocks += fields[startFieldIdx].blocks.subList(0, start.blockIndex)

    /* –– 1b.  trimmed prefix of the start block (may be null)             */
    fields[startFieldIdx]
        .blocks[start.blockIndex]
        .prefix(start.caret.offsetInBlock)
        ?.let { mergedBlocks += it }

    /* ─── 2.  Same-field versus multi-field merge ──────────────────────── */

    if (startFieldIdx == endFieldIdx) {
        /* 2A.  Selection lies inside a single field --------------------- */
        val sameField = fields[startFieldIdx]

        val isSameBlock = start.blockIndex == end.blockIndex
        val endBlock = sameField.blocks[end.blockIndex]

        when {
            isSameBlock -> {
                /* prefix + suffix live in *one* TextBlock */
                val tail = endBlock.suffix(end.caret.offsetInBlock)
                if (tail is Block.TextBlock && mergedBlocks.lastOrNull() is Block.TextBlock) {
                    val mergedText = (mergedBlocks.last() as Block.TextBlock)
                        .textFieldValue
                        .annotatedString + tail.textFieldValue.annotatedString
                    mergedBlocks[mergedBlocks.lastIndex] =
                        (mergedBlocks.last() as Block.TextBlock).copy(
                            textFieldValue =
                                (mergedBlocks.last() as Block.TextBlock)
                                    .textFieldValue.copy(annotatedString = mergedText)
                        )
                } else if (tail != null) {
                    mergedBlocks += tail
                }
            }

            else -> {
                /* suffix of a *different* end-block + trailing blocks     */
                endBlock.suffix(end.caret.offsetInBlock)?.let { mergedBlocks += it }
                mergedBlocks += sameField.blocks.subList(
                    end.blockIndex + 1,
                    sameField.blocks.size
                )
            }
        }
    } else {
        /* 2B.  Multi-field merge ---------------------------------------- */

        /* suffix of the *end* field                                       */
        val endField = fields[endFieldIdx]
        val endBlock = endField.blocks[end.blockIndex]

        endBlock.suffix(end.caret.offsetInBlock)?.let { mergedBlocks += it }
        mergedBlocks += endField.blocks.subList(end.blockIndex + 1, endField.blocks.size)
    }

    /* ─── 3.  Normalise & guarantee at least one TextBlock ────────────── */

    ensureNonEmpty(mergedBlocks)

    /* ─── 4.  Determine collapse caret position ───────────────────────── */

    val caretBlockId = if (mergedBlocks.any { it.id == start.caret.blockId }) {
        start.caret.blockId
    } else {
        mergedBlocks.first { it is Block.TextBlock }.id
    }

    val caretOffset = when (val blk = mergedBlocks.first { it.id == caretBlockId }) {
        is Block.TextBlock -> minOf(start.caret.offsetInBlock, blk.length)
        is Block.ImageBlock -> 1          // caret sits *after* the image
        is Block.DelimiterBlock -> 1
    }

    /* ─── 5.  Emit result ------------------------------------------------ */

    return MergeResult(
        startFieldIndex = startFieldIdx,
        endFieldIndex = endFieldIdx,
        mergedBlocks = mergedBlocks,
        caretBlockId = caretBlockId,
        caretOffsetInBlock = caretOffset
    )
}

/**
 * Recomputes a list of SelectionSegment between two carets
 * across single or multiple fields in document order.
 */
private fun DocumentState.computeSegmentsBetween(
    anchor: GlobalSelectionCaretState,
    focus: GlobalSelectionCaretState
): List<GlobalSelectionSegment> {
    /* ---------- Early exits / helpers ----------------------------------- */

    val rootCoords: LayoutCoordinates =
        rootReferenceCoordinates.value.coordinates ?: return emptyList()

    //  Ensure we always walk “downwards” in document order
    val (start, end) =
        getOrderedGlobalSelectionCaretsWithIndices(anchor, focus) ?: return emptyList()

    // Convenience lambdas for “first” and “last” selectable positions
    fun firstCaretOf(field: Field): Pair<String, Int> {
        val firstText = field.blocks.first { it is Block.TextBlock } as Block.TextBlock
        return firstText.id to 0           // offset 0 = BOF
    }

    fun lastCaretOf(field: Field): Pair<String, Int> {
        val lastText = field.blocks.last { it is Block.TextBlock } as Block.TextBlock
        return lastText.id to lastText.length   // offset = EOL
    }

    // Produce a single GlobalSelectionSegment if we manage to build its Rect
    fun makeSegment(
        field: Field,
        startPair: Pair<String, Int>,
        endPair: Pair<String, Int>
    ): GlobalSelectionSegment? {
        val rect = getFieldSelectionRect(
            fieldId = field.id,
            startBlockId = startPair.first,
            startOffsetInBlock = startPair.second,
            endBlockId = endPair.first,
            endOffsetInBlock = endPair.second,
            rootCoords = rootCoords
        ) ?: return null

        return GlobalSelectionSegment(
            fieldId = field.id,
            height = rect.height,      // tallest thing in span
            startBlockIdAndOffsetInBlock = startPair,
            endBlockIdAndOffsetInBlock = endPair,
            rect = rect
        )
    }

    val segments = mutableListOf<GlobalSelectionSegment>()

    /* ---------- Single-field selection ---------------------------------- */

    if (start.fieldIndex == end.fieldIndex) {
        val field = fields[start.fieldIndex]

        makeSegment(
            field,
            startPair = start.caret.blockId to start.caret.offsetInBlock,
            endPair = end.caret.blockId to end.caret.offsetInBlock
        )?.let { segments += it }

        return segments
    }

    /* ---------- Multi-field selection ----------------------------------- */

    // ── 1.  Start field: from caret … to end-of-field
    run {
        val field = fields[start.fieldIndex]
        makeSegment(
            field,
            startPair = start.caret.blockId to start.caret.offsetInBlock,
            endPair = lastCaretOf(field)
        )?.let { segments += it }
    }

    // ── 2.  All fields strictly between start and end are fully selected
    for (i in (start.fieldIndex + 1) until end.fieldIndex) {
        val field = fields[i]
        makeSegment(
            field,
            startPair = firstCaretOf(field),
            endPair = lastCaretOf(field)
        )?.let { segments += it }
    }

    // ── 3.  End field: from beginning … to caret
    run {
        val field = fields[end.fieldIndex]
        makeSegment(
            field,
            startPair = firstCaretOf(field),
            endPair = end.caret.blockId to end.caret.offsetInBlock
        )?.let { segments += it }
    }

    return segments
    /*
    val segments = mutableListOf<GlobalSelectionSegment>()
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return emptyList()
    val (topMostLeftMost, bottomMostRightMost) = getOrderedGlobalSelectionCaretsWithIndices(
        anchor,
        focus
    ) ?: return emptyList()

    val startFieldIdx = topMostLeftMost.fieldIndex
    val endFieldIdx = bottomMostRightMost.fieldIndex
    val startBlockIdx = topMostLeftMost.blockIndex
    val endBlockIdx = bottomMostRightMost.blockIndex
    val startLocGlobOff = getLocGlobalFieldOffsetForABlock(
        fieldId = topMostLeftMost.caret.fieldId,
        blockId = topMostLeftMost.caret.blockId,
        offsetInBlock = topMostLeftMost.caret.offsetInBlock
    )
    val endLocGlobOff = getLocGlobalFieldOffsetForABlock(
        fieldId = bottomMostRightMost.caret.fieldId,
        blockId = bottomMostRightMost.caret.blockId,
        offsetInBlock = bottomMostRightMost.caret.offsetInBlock
    )

    if (topMostLeftMost.fieldIndex == bottomMostRightMost.fieldIndex) {
        // same field
        val field = fields[topMostLeftMost.fieldIndex]
        val start = minOf(startOff, endOff)
        val end = maxOf(startOff, endOff)
        getFieldSelectionRect(field, start, end, boxCoords)?.let { rect ->
            segments.add(SelectionSegment(startFieldIdx, start, end, rect))
        }
    } else if (startFieldIdx < endFieldIdx) {
        // downward selection
        // start field: from anchor to EOL
        val startField = documentTextFieldList[startFieldIdx]
        val len0 = startField.textFieldValue.text.length
        getFieldSelectionRect(startField, startOff, len0, boxCoords)?.let {
            segments.add(SelectionSegment(startFieldIdx, startOff, len0, it))
        }
        // full middle fields
        for (i in (startFieldIdx + 1) until endFieldIdx) {
            val field = documentTextFieldList[i]
            val len = field.textFieldValue.text.length
            getFieldSelectionRect(field, 0, len, boxCoords)?.let { rect ->
                segments.add(SelectionSegment(i, 0, len, rect))
            }
        }
        // end field: from BOF to focus
        val endField = documentTextFieldList[endFieldIdx]
        getFieldSelectionRect(endField, 0, endOff, boxCoords)?.let {
            segments.add(SelectionSegment(endFieldIdx, 0, endOff, it))
        }
    } else {
        // upward selection
        // start field: from BOF to anchor
        val startField = documentTextFieldList[startFieldIdx]
        getFieldSelectionRect(startField, 0, startOff, boxCoords)?.let {
            segments.add(SelectionSegment(startFieldIdx, 0, startOff, it))
        }
        // middle fields
        for (i in (startFieldIdx - 1) downTo (endFieldIdx + 1)) {
            val field = documentTextFieldList[i]
            val len = field.textFieldValue.text.length
            getFieldSelectionRect(field, 0, len, boxCoords)?.let { rect ->
                segments.add(SelectionSegment(i, 0, len, rect))
            }
        }
        // end field: from focus to EOL
        val endField = documentTextFieldList[endFieldIdx]
        val lenEnd = endField.textFieldValue.text.length
        getFieldSelectionRect(endField, endOff, lenEnd, boxCoords)?.let {
            segments.add(SelectionSegment(endFieldIdx, endOff, lenEnd, it))
        }
    }
    return segments
     */
}

private fun DocumentState.getFieldSelectionRect(
    fieldId: String,
    startBlockId: String,
    startOffsetInBlock: Int,
    endBlockId: String,
    endOffsetInBlock: Int,
    rootCoords: LayoutCoordinates
): Rect? {
    val field = fields.firstOrNull { it.id == fieldId } ?: return null

    // Find the highest point (minimum Y) and lowest point (maximum Y) of all blocks
    var minTop = Float.MAX_VALUE
    var maxBottom = Float.MIN_VALUE

    for (blk in field.blocks) {
        blk.layoutCoordinates?.let { coords ->
            val topLeft = rootCoords.localPositionOf(coords, Offset.Zero)
            val blockTop = topLeft.y
            val blockBottom = topLeft.y + coords.size.height

            minTop = minOf(minTop, blockTop)
            maxBottom = maxOf(maxBottom, blockBottom)
        }
    }

    // If we couldn't find any positioned blocks, fall back to individual caret heights
    val useFieldBounds = minTop != Float.MAX_VALUE && maxBottom != Float.MIN_VALUE

    /**   Convert a caret *inside* a block into a global Rect (1 dp wide). */
    fun caretRect(block: Block, offset: Int): Rect? {
        return when (block) {
            is Block.TextBlock -> {
                val result = block.textLayoutResult ?: return null
                val coords = block.layoutCoordinates ?: return null

                val safe = offset.coerceIn(0, result.layoutInput.text.length)
                val local = result.getCursorRect(safe)       // in block-local space
                val global = rootCoords.localPositionOf(
                    coords,
                    Offset(local.left, local.top)
                )

                // Use field bounds for height if available, otherwise use caret height
                val height = if (useFieldBounds) {
                    maxBottom - minTop
                } else {
                    local.size.height
                }

                val top = if (useFieldBounds) minTop else global.y

                Rect(
                    left = global.x,
                    top = top,
                    right = global.x + local.size.width,
                    bottom = top + height
                )
            }

            is Block.ImageBlock, is Block.DelimiterBlock -> {
                val coords = block.layoutCoordinates ?: return null
                val origin = rootCoords.localPositionOf(coords, Offset.Zero)

                // Use field bounds for height if available, otherwise use block height
                val height = if (useFieldBounds) {
                    maxBottom - minTop
                } else {
                    coords.size.height.toFloat()
                }

                val top = if (useFieldBounds) minTop else origin.y

                val size = Size(1f, height) // 1 dp caret-width

                // pick left or right edge depending on offset
                val dx = if (offset <= 0) 0f else coords.size.width.toFloat()
                Rect(
                    left = origin.x + dx,
                    top = top,
                    right = origin.x + dx + size.width,
                    bottom = top + size.height
                )
            }
        }
    }

    /* ---------- Build caret rectangles ----------------------------------- */

    val startBlock = field.blocks.firstOrNull { it.id == startBlockId } ?: return null
    val endBlock = field.blocks.firstOrNull { it.id == endBlockId } ?: return null

    // single-caret case
    if (startBlockId == endBlockId && startOffsetInBlock == endOffsetInBlock) {
        return caretRect(startBlock, startOffsetInBlock)
    }

    val startRect = caretRect(startBlock, startOffsetInBlock) ?: return null
    val endRect = caretRect(endBlock, endOffsetInBlock) ?: return null

    /* ---------- Bounding box --------------------------------------------- */

    val left = min(startRect.left, endRect.left)
    val right = max(startRect.right, endRect.right)
    val top = min(startRect.top, endRect.top)
    val bottom = max(startRect.bottom, endRect.bottom)

    return Rect(left, top, right, bottom)
}

fun DocumentState.finishSelection() {
    globalSelectionState =
        globalSelectionState.copy(anchor = null, focus = null, segments = emptyList())
}

fun DocumentState.startDragSelection(globalOffset: Offset) {
    toSelectionCaret(globalOffset)?.let { dragCaret ->
        setAnchorCaret(dragCaret)
        setFocusCaret(dragCaret)
        rebuildSelectionFromAnchorAndFocus()
    }
}

fun DocumentState.updateDragSelection(globalOffset: Offset) {
    toSelectionCaret(globalOffset)?.let { dragCaret ->
        setFocusCaret(dragCaret)
        rebuildSelectionFromAnchorAndFocus()
    }
}

fun DocumentState.startShiftSelection(direction: DocumentEvent.Selection.Direction) {
    // If no anchor yet, this is “first arrow”
    if (globalSelectionState.anchor == null) {
        // 1) Record the original caret as the anchor
        val original = globalCaret.value

        setAnchorCaret(
            GlobalSelectionCaretState(
                fieldId = original.fieldId,
                blockId = original.blockId,
                offsetInBlock = original.offsetInBlock,
                globalPosition = original.globalPosition
            )
        )

        // 2) Compute the very first focus one step over
        val firstFocus = computeAdjacentCaret(globalSelectionState.anchor!!, direction)
        setFocusCaret(firstFocus)
    } else {
        // Subsequent arrows: just move the focus
        val prevFocus = globalSelectionState.focus!!
        val newFocus = computeAdjacentCaret(prevFocus, direction)
        setFocusCaret(newFocus)
    }

    // 3) Rebuild ALL segments from the immutable anchor and fresh focus
    rebuildSelectionFromAnchorAndFocus()
}

fun DocumentState.updateShiftSelection(direction: DocumentEvent.Selection.Direction) {
    // 1) Ensure there’s an anchor; if not, defer to startShiftArrowSelection
    if (globalSelectionState.anchor == null) {
        startShiftSelection(direction)
        return
    }

    // 2) Compute the new focus by moving one logical step
    val previousFocus = globalSelectionState.focus!!
    val newFocus = computeAdjacentCaret(previousFocus, direction)
    setFocusCaret(newFocus)

    // 3) Rebuild all selection segments from the immutable anchor → new focus
    rebuildSelectionFromAnchorAndFocus()
}

private fun DocumentState.computeAdjacentCaret(
    from: GlobalSelectionCaretState,
    direction: DocumentEvent.Selection.Direction
): GlobalSelectionCaretState {
    return when (direction) {
        DocumentEvent.Selection.Direction.Left -> moveOneLeft(from)
        DocumentEvent.Selection.Direction.Right -> moveOneRight(from)
        DocumentEvent.Selection.Direction.Up -> moveOneUp(from)
        DocumentEvent.Selection.Direction.Down -> moveOneDown(from)
    }
}

/** This is a crucial piece of the interaction model. The goArrowDir function acts
 * as the bridge between having an active, ranged selection and collapsing it back
 * into a single, mobile caret. The logic must be precise.
 * Collapses the current selection in the direction of the arrow key press.
 * If there is no selection, this function has no effect. The caller is
 * responsible for handling simple caret movement when no selection is active.
 *
 * @param direction The arrow key direction that triggered the collapse.
 */
fun DocumentState.goArrowDir(direction: DocumentEvent.Selection.Direction) {
    // 1) Determine the boundary of the selection to collapse to.
    // This will be the "start" for Left/Up, and the "end" for Right/Down.
    // If there is no selection to get a boundary from, there's nothing to do.
    val boundary = getBoundaryCaret(direction) ?: return

    // 2) Calculate the final target position for the caret.
    // For Left/Right, the target is the boundary itself.
    // For Up/Down, we "nudge" the caret one step away from the boundary
    // to ensure it moves to the next or previous field.
    val targetCaret = when (direction) {
        DocumentEvent.Selection.Direction.Up,
        DocumentEvent.Selection.Direction.Down ->
            computeAdjacentCaret(boundary, direction)

        DocumentEvent.Selection.Direction.Left,
        DocumentEvent.Selection.Direction.Right ->
            boundary
    }

    // 3) Clear the selection state entirely. This removes the anchor, focus,
    // and all visual selection segments.
    finishSelection()

    // 4) Move the single, global document caret to the calculated target position.
    // We only need to set the logical position; onGlobalCaretMoved will calculate
    // the visual properties (globalPosition, height).
    globalCaret.value = globalCaret.value.copy(
        fieldId = targetCaret.fieldId,
        blockId = targetCaret.blockId,
        offsetInBlock = targetCaret.offsetInBlock
    )

    // 5) Synchronize the rest of the state. This updates the local TextFieldValue's
    // selection, recalculates the caret's on-screen rectangle, and ensures
    // focus is requested correctly.
    onGlobalCaretMoved()
}

/**
 * Given the current selection's anchor and focus, picks the boundary caret
 * based on the desired movement direction.
 * - Left/Up  → returns the caret that appears first in document order ("start").
 * - Right/Down → returns the caret that appears last in document order ("end").
 *
 * Returns null if the selection is not active or carets are missing.
 */
private fun DocumentState.getBoundaryCaret(
    direction: DocumentEvent.Selection.Direction
): GlobalSelectionCaretState? {
    val anchor = globalSelectionState.anchor
    val focus = globalSelectionState.focus

    // If selection isn't fully defined, there's no boundary to get.
    if (anchor == null || focus == null) {
        return focus ?: anchor // Return whichever one is not null, or null if both are.
    }

    // Use the helper to determine which caret is first ("start") and which is last ("end")
    // in the document's logical order (top-to-bottom, left-to-right).
    // This helper correctly compares field index, then block index, then offset within the block.
    val (start, end) = getOrderedGlobalSelectionCarets(anchor, focus) ?: return null

    return when (direction) {
        DocumentEvent.Selection.Direction.Left,
        DocumentEvent.Selection.Direction.Up -> start

        DocumentEvent.Selection.Direction.Right,
        DocumentEvent.Selection.Direction.Down -> end
    }
}

private fun DocumentState.moveOneLeft(from: GlobalSelectionCaretState): GlobalSelectionCaretState {
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return from

    // 1. Get current position context
    val fieldIndex = getFieldIndex(from.fieldId)
    if(fieldIndex < 0) return from
    val field = fields[fieldIndex]
    val blockIndex = field.blocks.indexOfFirst { it.id == from.blockId }
    val block = field.blocks[blockIndex]

    // 2. Try moving within the current block
    if (from.offsetInBlock > 0) {
        val newCaret = from.copy(offsetInBlock = from.offsetInBlock - 1)
        val newBlock =
            getFieldById(newCaret.fieldId)?.blocks?.firstOrNull { it.id == newCaret.blockId } as? Block.TextBlock
                ?: return from
        val newRect =
            getGlobalCursorRect(newBlock, newCaret.offsetInBlock, rootCoords) ?: return from
        return newCaret.copy(globalPosition = newRect.topLeft)
    }

    // 3. At the start of a block, try moving to the previous block in the same field
    if (blockIndex > 0) {
        val prevBlock = field.blocks[blockIndex - 1]
        val newOffset = prevBlock.length // Moves to the end of the previous block
        val newCaret = from.copy(blockId = prevBlock.id, offsetInBlock = newOffset)

        val newTextBlock =
            getFieldById(newCaret.fieldId)?.blocks?.firstOrNull { it.id == newCaret.blockId } as? Block.TextBlock
        if (newTextBlock != null) {
            val newRect =
                getGlobalCursorRect(newTextBlock, newCaret.offsetInBlock, rootCoords) ?: return from
            return newCaret.copy(globalPosition = newRect.topLeft)
        }
        // If not a text block, we can still calculate a position (e.g., for an image)
        // For now, let's assume a simple fallback or find the nearest text block.
        // A full implementation would require a getGlobalCursorRect for non-text blocks.
        return toSelectionCaret(from.globalPosition.copy(x = from.globalPosition.x - 1)) ?: from
    }

    // 4. At the start of a field, try moving to the last block of the previous field
    if (fieldIndex > 0) {
        val prevField = fields[fieldIndex - 1]
        val lastBlock = prevField.blocks.last()
        val newOffset = lastBlock.length
        val newCaret =
            from.copy(fieldId = prevField.id, blockId = lastBlock.id, offsetInBlock = newOffset)

        val newTextBlock =
            getFieldById(newCaret.fieldId)?.blocks?.firstOrNull { it.id == newCaret.blockId } as? Block.TextBlock
                ?: return from
        val newRect =
            getGlobalCursorRect(newTextBlock, newCaret.offsetInBlock, rootCoords) ?: return from
        return newCaret.copy(globalPosition = newRect.topLeft)
    }

    // 5. At the very beginning of the document
    return from
}

private fun DocumentState.moveOneRight(from: GlobalSelectionCaretState): GlobalSelectionCaretState {
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return from

    // 1. Get current position context
    val fieldIndex = getFieldIndex(from.fieldId)
    if(fieldIndex < 0) return from
    val field = fields.getOrNull(fieldIndex) ?: return from
    val blockIndex = field.blocks.indexOfFirst { it.id == from.blockId }
    val block = field.blocks.getOrNull(blockIndex) ?: return from

    // 2. Try moving within the current block
    if (from.offsetInBlock < block.length) {
        val newCaret = from.copy(offsetInBlock = from.offsetInBlock + 1)
        val newBlock =
            getFieldById(newCaret.fieldId)?.blocks?.firstOrNull { it.id == newCaret.blockId } as? Block.TextBlock
                ?: return from
        val newRect =
            getGlobalCursorRect(newBlock, newCaret.offsetInBlock, rootCoords) ?: return from
        return newCaret.copy(globalPosition = newRect.topLeft)
    }

    // 3. At the end of a block, try moving to the next block in the same field
    if (blockIndex < field.blocks.lastIndex) {
        val nextBlock = field.blocks[blockIndex + 1]
        val newCaret = from.copy(blockId = nextBlock.id, offsetInBlock = 0)

        val newTextBlock =
            getFieldById(newCaret.fieldId)?.blocks?.firstOrNull { it.id == newCaret.blockId } as? Block.TextBlock
        if (newTextBlock != null) {
            val newRect =
                getGlobalCursorRect(newTextBlock, newCaret.offsetInBlock, rootCoords) ?: return from
            return newCaret.copy(globalPosition = newRect.topLeft)
        }
        // Fallback for non-text blocks
        return toSelectionCaret(from.globalPosition.copy(x = from.globalPosition.x + block.width + 1))
            ?: from
    }

    // 4. At the end of a field, try moving to the first block of the next field
    if (fieldIndex < fields.lastIndex) {
        val nextField = fields[fieldIndex + 1]
        val firstBlock = nextField.blocks.first()
        val newCaret = from.copy(fieldId = nextField.id, blockId = firstBlock.id, offsetInBlock = 0)

        val newTextBlock =
            getFieldById(newCaret.fieldId)?.blocks?.firstOrNull { it.id == newCaret.blockId } as? Block.TextBlock
                ?: return from
        val newRect =
            getGlobalCursorRect(newTextBlock, newCaret.offsetInBlock, rootCoords) ?: return from
        return newCaret.copy(globalPosition = newRect.topLeft)
    }

    // 5. At the very end of the document
    return from
}

private fun DocumentState.moveOneUp(from: GlobalSelectionCaretState): GlobalSelectionCaretState {
    val fieldIndex = getFieldIndex(from.fieldId)
    if (fieldIndex <= 0) return from // Already at the top field

    val prevField = fields[fieldIndex - 1]

    // Find the vertical center of the previous field to use as the target Y coordinate
    val prevFieldFirstBlock = prevField.blocks.first()
    val prevFieldCoords = prevFieldFirstBlock.layoutCoordinates ?: return from // Need layout info
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return from

    val prevFieldTopLeftInRoot = rootCoords.localPositionOf(prevFieldCoords, Offset.Zero)
    val targetY = prevFieldTopLeftInRoot.y + (prevFieldCoords.size.height / 2f)

    // Use the existing X coordinate to find the horizontally-aligned position in the field above
    val targetPosition = Offset(from.globalPosition.x, targetY)

    // `toSelectionCaret` elegantly handles the hit-testing and caret creation
    return toSelectionCaret(targetPosition) ?: from
}

private fun DocumentState.moveOneDown(from: GlobalSelectionCaretState): GlobalSelectionCaretState {
    val fieldIndex = getFieldIndex(from.fieldId)
    if (fieldIndex < 0 || fieldIndex >= fields.lastIndex) return from // Already at the bottom field

    val nextField = fields[fieldIndex + 1]

    // Find the vertical center of the next field to use as the target Y coordinate
    val nextFieldFirstBlock = nextField.blocks.first()
    val nextFieldCoords = nextFieldFirstBlock.layoutCoordinates ?: return from // Need layout info
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return from

    val nextFieldTopLeftInRoot = rootCoords.localPositionOf(nextFieldCoords, Offset.Zero)
    val targetY = nextFieldTopLeftInRoot.y + (nextFieldCoords.size.height / 2f)

    // Use the existing X coordinate to find the horizontally-aligned position in the field below
    val targetPosition = Offset(from.globalPosition.x, targetY)

    // `toSelectionCaret` elegantly handles the hit-testing and caret creation
    return toSelectionCaret(targetPosition) ?: from
}

private fun DocumentState.toSelectionCaret(hitPos: Offset): GlobalSelectionCaretState? {
    val hit = findTextFieldAtPosition(hitPos) ?: return null
    val (fieldId, blockId, offset) = hit
    val block = getFieldById(fieldId)
        ?.blocks
        ?.firstOrNull { it.id == blockId } as? Block.TextBlock ?: return null
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return null

    val caretRect = getGlobalCursorRect(block, offset, rootCoords) ?: return null

    return GlobalSelectionCaretState(
        fieldId = fieldId,
        blockId = blockId,
        offsetInBlock = offset,
        globalPosition = caretRect.topLeft
    )
}

/* ──────────────────────────────────────────────────────────────────────────
 *  1. Hit-test a tap / drag position against every TextBlock in the document
 *     and return the caret it maps to.
 *
 *     return Triple<
 *         fieldId,           // String  — owning Field
 *         blockId,           // String  — hit TextBlock
 *         offsetInBlock      // Int     — caret offset inside that TextBlock
 *     >   or `null` if the tap hit no caret-hosting block.
 * ────────────────────────────────────────────────────────────────────────── */
private fun DocumentState.findTextFieldAtPosition(
    globalOffset: Offset
): Triple<String, String, Int>? {

    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return null

    fields.forEach { field ->
        field.blocks.forEach { blk ->
            val tb = blk as? Block.TextBlock ?: return@forEach   // only TextBlocks host carets
            val coords = tb.layoutCoordinates ?: return@forEach

            // Rectangle of this TextBlock in *root* coordinates
            val topLeft = rootCoords.localPositionOf(coords, Offset.Zero)
            val rect = Rect(
                topLeft,
                Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            )

            if (globalOffset !in rect) return@forEach

            // Found our host block – compute caret offset inside it
            val layout = tb.textLayoutResult ?: return@forEach
            val local = coords.localPositionOf(rootCoords, globalOffset)
            val off = layout.getOffsetForPosition(local)

            return Triple(field.id, tb.id, off)
        }
    }
    return null          // no TextBlock hit
}

/* ──────────────────────────────────────────────────────────────────────────
 *  2. Convert a caret (offsetInBlock) inside a specific TextBlock into a
 *     *global* rectangle that Compose can draw.
 * ────────────────────────────────────────────────────────────────────────── */
fun DocumentState.getGlobalCursorRect(
    block: Block.TextBlock,
    offsetInBlock: Int,
    rootCoords: LayoutCoordinates    // usually   rootReferenceCoordinates.value.coordinates !!
): Rect? {

    val layout = block.textLayoutResult ?: return null
    val blockCoords = block.layoutCoordinates ?: return null

    // Clamp offset so we never crash on stale values
    val safeOffset = offsetInBlock.coerceIn(0, layout.layoutInput.text.length)
    val localRect = layout.getCursorRect(safeOffset)

    val globalTopLeft = rootCoords.localPositionOf(
        blockCoords,
        Offset(localRect.left, localRect.top)
    )
    return Rect(globalTopLeft, localRect.size)
}