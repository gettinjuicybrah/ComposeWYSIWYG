package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.caret.CaretState
import com.joeybasile.composewysiwyg.model.caret.GlobalCaret
import com.joeybasile.composewysiwyg.model.caret.updateGlobalCaretFieldAndBlockId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
data class MeasureResult(
    val width: Int,
    val overflow: Boolean,
    val overflowPos: Pos? = null,
    val hardBreakPos: Pos? = null          // first NL *inside* line, if any
)

/**
 * Returns `true` when the caret is positioned at the *very start* of the current
 * field – i.e. its offset inside the current TextBlock is 0 **and** there is no
 * earlier caret-host (TextBlock) in that field.
 */
internal fun DocumentState.isCaretAtOriginOfField(): Boolean {
    val caret = globalCaret.value

    // 1) We need an offset of 0 in the current block
    if (caret.offsetInBlock != 0) return false

    // 2) Find the field; bail out if something’s inconsistent
    val fid = fields.indexOfFirst { it.id == caret.fieldId }
    if (fid == -1) return false
    val blocks = fields[fid].blocks
    val idx = blocks.indexOfFirst { it.id == caret.blockId }
    if (idx < 0) return false                                    // caret points nowhere

    // 3) Make sure there is **no** earlier caret-host in this field
    for (i in 0 until idx) {
        if (blocks[i] is Block.TextBlock) return false           // another host before us
    }

    // We are at offset 0 and this is the first caret-host in the field
    return true
}

/**
 * `true` ⇢ the caret is **not** in the first field of the document
 * `false` ⇢ the caret is already on the topmost field or the caret is invalid
 */
internal fun DocumentState.thereExistsAnAboveField(): Boolean {
    // Find the index of the field that currently owns the caret
    val idx = globalCaret.value.fieldId.let { id -> fields.indexOfFirst { it.id == id } }
        ?: return false                      // no valid caret → no “above” field

    // We have an “above” field if the current one is *not* the first (index 0)
    return idx > 0
}

/**
 * Returns true when the field that currently owns the global caret
 * ends with a New-Line delimiter block.
 */
internal fun DocumentState.doesFieldHaveNL(): Boolean {
    // Resolve the caret’s field; return false if the caret is orphaned
    val field = globalCaret.value.fieldId.let(::getFieldById) ?: return false

    // The last block must be a DelimiterBlock of kind NewLine
    val tail = field.blocks.lastOrNull() as? Block.DelimiterBlock ?: return false
    return tail.kind == Block.DelimiterBlock.Kind.NewLine
}
/**
 * `true` when the global caret sits **inside** its TextBlock
 * (i.e. its offset is > 0).
 * Returns `false` if the caret is at offset 0 *or* if, for whatever
 * reason, it does not currently reference a `TextBlock` (which would
 * violate protocol P-5).
 */
internal fun DocumentState.isCaretOffsetPositive(): Boolean {
    val caret = globalCaret.value

    // 1) Early-out if the offset isn’t positive
    if (caret.offsetInBlock <= 0) return false

    // 2) Make sure the caret actually resides in a TextBlock
    val fid = fields.indexOfFirst { it.id == caret.fieldId }
    if (fid == -1) return false
    val blocks = fields[fid].blocks
    val idx = blocks.indexOfFirst { it.id == caret.blockId }

    val blk = blocks[idx] as? Block.TextBlock ?: return false

    // The offset is > 0 and we’re definitely in a TextBlock
    return true
}

internal fun DocumentState.isFieldLast(fieldId: String): Boolean {
    val caret = globalCaret.value
    val fieldSize = fields.size
    return fields.indexOfLast { it.id == fieldId } == (fieldSize - 1)
}

private fun DocumentState.isPullUpStillRequired(): Boolean =
    (isCaretAtOriginOfField() && thereExistsAnAboveField()) ||
            (isCaretOffsetPositive() && !doesFieldHaveNL() &&
                    !isFieldLast(globalCaret.value.fieldId))

private fun DocumentState.attemptPullUp() {
    val tm  = textMeasurer ?: return            // cannot measure ⇒ no pull-up
    val sty = defaultTextStyle
    var guard = 0                               // cheap safety valve
    while (isPullUpStillRequired()) {
        pullUp(maxWidth, tm, sty)
    }
}

/*
Called within Input.kt whenever backspace is registered.

Makes determinations on isSelecting vs. Not

Makes determination on whether or not on calling attemptPullup() for iterative pullup would be necessary or not.

 */
fun DocumentState.onBackSpace(){
    /*
    First, we make a determination. We need to know whether or not pulling up would be required.
    1.) need to know if current textblock is the first block within field AND if we are at offset 0 within it.
    2.) Need to know if there exists an above field. We
     */


    val requirePullup: Boolean =
        (isCaretAtOriginOfField() && thereExistsAnAboveField()) ||
                (isCaretOffsetPositive() && !doesFieldHaveNL() && !isFieldLast(globalCaret.value.fieldId))

    //Right now, only case is for inactive selection.
    if(!selectionState.isActive){

        //This implies that the offset is > 0 (and of course witin a textblock) and the field has a newline block delimiter at the end, implying that
        //any potential fields below would not be pulled up, because the newline delimiter block is 'preventing' such.
        if (!requirePullup) {
            println("----------------------------NOT requirePullup()")
            val caret  = globalCaret.value
            val fieldIdx = fields.indexOfFirst { it.id == caret.fieldId }
            if (fieldIdx < 0) return            // nothing sane to do
            val blocks = fields[fieldIdx].blocks

            val curIdx = blocks.indexOfFirst { it.id == caret.blockId }
            if (curIdx < 0) return
            val curTB  = blocks[curIdx] as? Block.TextBlock ?: return   // P-5 guarantees this cast

            /* ───────────── 1) ordinary character deletion ───────────── */
            if (caret.offsetInBlock > 0) {
                val oldAS  = curTB.textFieldValue.annotatedString
                val newAS  = buildAnnotatedString {
                    append(oldAS.subSequence(0, caret.offsetInBlock - 1))
                    append(oldAS.subSequence(caret.offsetInBlock, oldAS.length))
                }
                val newTFV = curTB.textFieldValue.copy(
                    annotatedString = newAS,
                    selection       = TextRange(caret.offsetInBlock - 1)
                )
                blocks[curIdx] = curTB.copy(textFieldValue = newTFV)

                globalCaret.value = caret.copy(offsetInBlock = caret.offsetInBlock - 1)
                onGlobalCaretMoved()
                return
            }

            /* ───────────── 2) traversal-unit deletion (offset == 0) ───────────── */
            if (curIdx == 0) return                         // nothing to the left
            val prev = blocks[curIdx - 1]

            // Currently we only expect ImageBlock or (in the future) other non-text units
            if (prev is Block.ImageBlock || prev is Block.DelimiterBlock) {
                blocks.removeAt(curIdx - 1)

                // After removal we may have TB | TB   →  merge to honour P-3.
                var newCurIdx = curIdx - 1                       // caret-host may have shifted left
                if (newCurIdx > 0 &&
                    blocks[newCurIdx - 1] is Block.TextBlock &&
                    blocks[newCurIdx]     is Block.TextBlock) {

                    val left  = blocks[newCurIdx - 1] as Block.TextBlock
                    val right = blocks[newCurIdx]     as Block.TextBlock
                    val merged = concatTextBlocks(left, right)

                    blocks[newCurIdx - 1] = merged
                    blocks.removeAt(newCurIdx)                   // drop the old “right”

                    globalCaret.value = caret.copy(
                        blockId       = merged.id,
                        offsetInBlock = left.length              // caret now sits at join-point
                    )
                } else {
                    // caret stayed in the same (still valid) TextBlock
                    globalCaret.value = caret
                }
                onGlobalCaretMoved()
            }
        }else {
            println("---------------------------- REQUIRE pullUp()")
            attemptPullUp()
            return
        }

    }

}

/**
 * Invoked **only** when Backspace is pressed at offset 0 of the caret‑host.
 */
@OptIn(ExperimentalUuidApi::class)
fun DocumentState.pullUp(
    maxWidth: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
) {
    // ───── locate involved fields ───────────────────────────────────────
    val caret       = globalCaret.value
    val curFieldIdx = fields.indexOfFirst { it.id == caret.fieldId }
    if (curFieldIdx <= 0) return                                    // already at top

    val topFieldIdx = curFieldIdx - 1
    val topField    = fields[topFieldIdx]
    val botField    = fields[curFieldIdx]

    // ───── work on a *copy* to avoid chatty recompositions ──────────────
    val newTopBlocks = topField.blocks.toMutableList()

    /* 1 ─ drop the mandatory NL from the top field */
    val lastIdx = newTopBlocks.lastIndex
    if (lastIdx >= 0 && newTopBlocks[lastIdx] is Block.DelimiterBlock)
        newTopBlocks.removeAt(lastIdx)

    /* 2 ─ merge TB|TB if the two fields’ border blocks are both TextBlocks */
    var newCaretBlockId = caret.blockId
    var newCaretOffset  = caret.offsetInBlock

    val firstBot = botField.blocks.first()
    if (newTopBlocks.lastOrNull() is Block.TextBlock && firstBot is Block.TextBlock) {
        val left     = newTopBlocks.last() as Block.TextBlock
        val right    = firstBot
        val leftLen  = left.length
        val merged   = concatTextBlocks(left, right)

        newTopBlocks[newTopBlocks.lastIndex] = merged
        newTopBlocks.addAll(botField.blocks.drop(1))

        when (caret.blockId) {
            left.id  -> newCaretBlockId = merged.id            // offset unchanged
            right.id -> {
                newCaretBlockId = merged.id
                newCaretOffset  = leftLen + newCaretOffset
            }
        }
    } else {
        newTopBlocks.addAll(botField.blocks)
    }

    /* 3 ─ overflow / hard-break check on the *local* list */
    when (val m = measureLine(newTopBlocks, maxWidth, textMeasurer, textStyle)) {
        is MeasureResult -> when {
            m.hardBreakPos != null -> splitFieldAt(topFieldIdx, m.hardBreakPos)
            m.overflow            -> splitFieldAt(topFieldIdx, m.overflowPos!!)
        }
    }

    /* 4 ─ publish the entire new block list in one go */
    fields[topFieldIdx] = topField.copy(
        blocks = mutableStateListOf(*newTopBlocks.toTypedArray())
    ).normalise()

    /* 5 ─ drop the now-empty lower field with a single mutation */
    fields.removeAt(curFieldIdx)

    /* 6 ─ move the caret */
    globalCaret.value = caret.copy(
        fieldId       = fields[topFieldIdx].id,
        blockId       = newCaretBlockId,
        offsetInBlock = newCaretOffset
    )
    onGlobalCaretMoved()
}

fun DocumentState.splitFieldAt(fieldIdx: Int, pos: Pos) {
    val src = fields[fieldIdx]
    val left  = src.blocks.take(pos.blockIdx)
    val right = src.blocks.drop(pos.blockIdx)

    val newTop  = src.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = src.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    fields[fieldIdx] = newTop
    fields.add(fieldIdx + 1, newBottom)
}
fun DocumentState.measureLine(
    blocks: List<Block>,
    maxWidth: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
): MeasureResult {
    var sum = 0
    for ((idx, blk) in blocks.withIndex()) {
        // Hard break BEFORE adding current block
        if (blk is Block.DelimiterBlock && blk.kind == Block.DelimiterBlock.Kind.NewLine) {
            return MeasureResult(sum, false, hardBreakPos = Pos(idx, 0))
        }
        val w = blk.measure(textMeasurer, textStyle, maxWidth)
        if (sum + w > maxWidth) {
            return MeasureResult(sum, true, overflowPos = Pos(idx, 0))
        }
        sum += w
    }
    return MeasureResult(sum, false)
}

fun DocumentState.updateGlobalCaretPosition() {
    val caret = globalCaret.value
    require(caret.fieldId != null)
    require(caret.blockId != null)
    val fid = fields.indexOfFirst { it.id == caret.fieldId }
    require(fid >= 0) { "Field doesn't exist (id = ${caret.fieldId})" }

    val blocksList = fields[fid].blocks

    // Find the TextBlock index
    val bid = blocksList.indexOfFirst { it is Block.TextBlock && it.id == caret.blockId }
    require(bid >= 0) { "Block doesn't exist (id = ${caret.blockId})" }

    val field = fields[fid]

    val block = blocksList[bid] as Block.TextBlock
    val blockCoords = block.layoutCoordinates ?: return
    val rootCoords = rootReferenceCoordinates.value.coordinates ?: return

    println("in updateGlobalCaretPosition()------------------------------------------------------------------------ block.textlayoutresult: ${block.textLayoutResult} ")
    val layoutResult = block.textLayoutResult ?: return
    val localOffset =
        layoutResult.getCursorRect(
            caret.offsetInBlock.coerceIn(
                0,
                layoutResult.layoutInput.text.length
            )
        )
    val localX = localOffset.left
    val localY = localOffset.top
    println("-------------------------------------------------------------------------------")
    val globalOffset = rootCoords.localPositionOf(blockCoords, Offset(localX, localY))
    val height = layoutResult.getLineBottom(0) - layoutResult.getLineTop(0)

    // Update caret state with calculated values
    globalCaret.value = caret.copy(
        globalPosition = globalOffset,
        height = height.coerceAtLeast(16f)
    )

}
fun DocumentState.onGlobalCaretMoved() {
    updateGlobalCaretPosition()
    val caret = globalCaret.value

    // Find the field index
    val fid = fields.indexOfFirst { it.id == caret.fieldId }
    require(fid >= 0) { "Field doesn't exist (id = ${caret.fieldId})" }

    val blocksList = fields[fid].blocks

    // Find the TextBlock index
    val bid = blocksList.indexOfFirst { it is Block.TextBlock && it.id == caret.blockId }
    require(bid >= 0) { "Block doesn't exist (id = ${caret.blockId})" }

    // Update focus to this block
    updateFocusedBlock(caret.blockId)

    // Safely cast to TextBlock
    val textBlock = blocksList[bid] as? Block.TextBlock
    val currentTFV = textBlock?.textFieldValue
    if (currentTFV != null) {
        // Create a new TextFieldValue with the caret position as both selection start and end
        val newTFV = currentTFV.copy(
            selection = TextRange(caret.offsetInBlock, caret.offsetInBlock)
        )

        // Replace the old TextBlock with an updated one
        val updatedTextBlock = textBlock.copy(textFieldValue = newTFV)
        blocksList[bid] = updatedTextBlock

        println("onGlobalCaretMoved() current block.textlayoutresult ${textBlock.textLayoutResult}")
        println("tfv updated in onGlobalCaretMoved()")
    }
}

fun DocumentState.updateFocusedBlock(blockId: String) {
    focusedBlock.value = blockId
}

fun DocumentState.handleFocusChange(fieldId: String, blockId: String) {
    updateFocusedBlock(blockId)
    updateGlobalCaretFieldAndBlockId(
        fieldId = fieldId,
        blockId = blockId
    )
    println("in handleFocusChange(). About to call updateGlobalCaretPosition()")
    updateGlobalCaretPosition()
        println(";';;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;")
}


fun DocumentState.getFieldIndexById(id: String): Int{
    val index = fields.indexOfFirst {it.id == id}
    require(index != -1){"field is null"}
    return index
}

fun DocumentState.getBlockIndexById(fieldId: String, blockId: String): Int {
    // 1) Find the Field
    val field = fields.find { it.id == fieldId }
    require(field != null) { "field with id='$fieldId' was not found" }

    // 2) Find the index of any Block whose .id matches blockId
    val index = field.blocks.indexOfFirst { it.id == blockId }
    require(index != -1) { "block with id='$blockId' was not found in field '$fieldId'" }

    return index
}

fun DocumentState.handleOnTextLayout(fieldId: String,blockId: String, result: TextLayoutResult) {

    val fid = fields.indexOfFirst {
        it.id == fieldId
    }
    val blocksList = fields[fid].blocks
    val bid = fields[fid].blocks.indexOfFirst{
        it is Block.TextBlock && it.id == blockId

    }
    require(blocksList[bid] is Block.TextBlock)
    require(fid > -1) {"field doesn't exit"}

    require(bid > -1) {"block doesn't exit"}
    val block = fields[fid].blocks[bid] as Block.TextBlock
    println("")
    println("")
    println("handleOnTextLayout(): $result")
    println("")
    println("")
    println("block before set to result: ${block.textLayoutResult}")
    val updatedTextBlock = block.copy(textLayoutResult = result)
    //    This will trigger Compose to recompose anything reading `fields[fid].blocks`
    //blocksList[bid] = updatedTextBlock
    fields[fid].blocks[bid] = updatedTextBlock
    if(globalCaret.value.blockId == block.id){
        updateGlobalCaretPosition()
        println("just called update global caret pos from line 146")
    }
    println("block set to result: ${updatedTextBlock.textLayoutResult}")
    println("123456789 LEAVING handleOnTextLayoutResult  in abstractfieldstateext line 147")
}

fun DocumentState.handleOnValueChange(fieldId: String, blockId: String, newValue: TextFieldValue){
    val fid = fields.indexOfFirst {
        it.id == fieldId
    }
    val blocksList = fields[fid].blocks
    val bid = fields[fid].blocks.indexOfFirst{
        it is Block.TextBlock && it.id == blockId
    }

    require(fid > -1) {"field doesn't exist"}

    require(bid > -1) {"block doesn't exist"}
    val block = fields[fid].blocks[bid] as Block.TextBlock
    val newTFV = block.textFieldValue.copy(
        annotatedString = newValue.annotatedString,
        selection = newValue.selection
    )

    //    This will trigger Compose to recompose anything reading `fields[fid].blocks`
    //blocksList[bid] = updatedTextBlock
    fields[fid].blocks[bid] = block.copy(textFieldValue = newTFV)
    // *** unconditionally sync the global caret to whatever the local selection now is ***
    globalCaret.value = globalCaret.value.copy(
        fieldId = fieldId,
        blockId = block.id,
        offsetInBlock = newValue.selection.start
    )
    // immediately recompute the global caret position on screen
    onGlobalCaretMoved()
}


/**
 * Updates the layout coordinates for a specific block within a field.
 * This function is called via state hoisting from the composables.
 */
fun DocumentState.updateBlockCoords(
    fieldId: String,
    block: Block.TextBlock,
    newCoordinates: LayoutCoordinates
) {
    val fid = fields.indexOfFirst {
        it.id == fieldId
    }
    val blocksList = fields[fid].blocks
    val bid = fields[fid].blocks.indexOfFirst{
        it is Block.TextBlock && it.id == block.id
    }

    require(fid > -1) {"field doesn't exist"}

    require(bid > -1) {"block doesn't exist"}

    //val updatedTextBlock = block.copy(layoutCoordinates = newCoordinates)
    val updatedTextBlock = blocksList[bid] as Block.TextBlock
    //    This will trigger Compose to recompose anything reading `fields[fid].blocks`
    blocksList[bid] = updatedTextBlock.copy(layoutCoordinates = newCoordinates)
    // For debugging:
    // println("Updated coordinates for Field $fieldId, Block $blockId: ${newCoordinates.size}, ${newCoordinates.positionInRoot()}")

   if (globalCaret.value.blockId == block.id) updateGlobalCaretPosition()

}

// Helper function to get a Field by its ID
fun DocumentState.getFieldById(fieldId: String): Field? {
    return fields.find { it.id == fieldId }
}

/**
 * Helper function to get a Field by a Block's ID.
 * It searches through all fields and their blocks to find the field
 * that contains the block with the specified ID.
 *
 * @param blockId The ID of the block to search for.
 * @return The [Field] containing the block with the given [blockId], or null if not found.
 */
fun DocumentState.getFieldByBlockId(blockId: String): Field? {
    // Iterate over each field in the document
    return this.fields.find { field ->
        // For each field, check if any of its blocks has the matching blockId
        field.blocks.any { block ->
            block.id == blockId
        }
    }
}

fun DocumentState.getBlockById(field: Field, blockId: String): Block? {
    return field.blocks.find { it.id == blockId }
}

/**
 * Retrieves a specific TextBlock from a given Field by its ID.
 *
 * @param field The Field object to search within.
 * @param blockId The unique ID (UUID string) of the TextBlock to find.
 * @return The [TextBlock] if found and it is indeed a TextBlock, otherwise `null`.
 */

fun DocumentState.getTextBlockById(field: Field, blockId: String): Block.TextBlock? {
    // Find the block by its ID first
    val foundBlock = field.blocks.find { it.id == blockId }

    // Then, safely cast it to TextBlock.
    // If foundBlock is null, or if it's not an instance of TextBlock,
    // 'as?' will return null. Otherwise, it returns foundBlock cast to TextBlock.
    return foundBlock as? Block.TextBlock
}


/**
 * Initializes the document state for a new, empty document.
 * This clears existing fields and adds a default new field with a text block.
 */
@OptIn(ExperimentalUuidApi::class)
fun DocumentState.initializeNewDoc() {
    //fields.clear()
    // Add a new empty Field (UI state version) with a default TextBlock
    val fieldId = Uuid.random().toString()
    val blockId = Uuid.random().toString()
    focusedBlock.value = blockId
    val blocks: SnapshotStateList<Block> = mutableStateListOf()
    val initialBlock = Block.TextBlock(
        id = blockId,
        textFieldValue = TextFieldValue("new doc"),
        focusRequester = FocusRequester()
    )
    blocks.add(initialBlock)
    fields.add(
        Field(
            id = fieldId,
            blocks = blocks
        )
    )
    globalCaret.value = GlobalCaret(
        fieldId = fieldId,
        blockId = blockId,
        offsetInBlock = 0,
        globalPosition = Offset.Unspecified,
        height = 16f

    )
}

// --- Example methods for manipulating the UI state ---
fun DocumentState.addField(index: Int, field: Field) {
    fields.add(index, field)
}

fun DocumentState.removeField(field: Field) {
    fields.remove(field)
}
