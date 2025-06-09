package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import com.joeybasile.composewysiwyg.model.caret.GlobalCaret
import com.joeybasile.composewysiwyg.util.deleteCharBeforeCaretOffset
import com.joeybasile.composewysiwyg.util.sliceRange
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
data class MeasureResult(
    val width: Int,
    val overflow: Boolean,
    val overflowPos: Pos? = null,
    val hardBreakPos: Pos? = null,          // first NL *inside* line, if any
    val blockWidths: List<Int> = emptyList()
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


internal fun DocumentState.isFieldLast(fieldId: String): Boolean {
    val caret = globalCaret.value
    val fieldSize = fields.size
    return fields.indexOfLast { it.id == fieldId } == (fieldSize - 1)
}
fun DocumentState.checkProtocol() {
    fields.forEachIndexed { fIdx, f ->
        for (i in 0 until f.blocks.lastIndex) {
            require(!(f.blocks[i] is Block.TextBlock &&
                    f.blocks[i + 1] is Block.TextBlock)) {
                "P-3 violated in field[$fIdx] at blocks[$i]"
            }
            if (f.blocks[i] is Block.ImageBlock) {
                require(i + 1 <= f.blocks.lastIndex &&
                        f.blocks[i + 1] is Block.TextBlock) {
                    "P-2 violated: Image not followed by Text in field[$fIdx]"
                }
            }
        }
        // P-1 – blank line canonical form
        if (f.blocks.isEmpty())
            error("P-1 violated: empty field")
    }
}

private fun DocumentState.tryPullUp(rootFieldIdx: Int){
    println("now within tryPullUp()")
    val tm = textMeasurer ?: return            // cannot measure ⇒ no pull-up
    val sty = defaultTextStyle
    var currentFieldIdx = rootFieldIdx
    var loopCount = 0
    while (currentFieldIdx < fields.lastIndex) {
        println("loop # $loopCount within the loop in attemptPullUp()")
        loopCount++
        val topField = fields[currentFieldIdx]
        val botField = fields[currentFieldIdx + 1]

        val tempBlocks = topField.blocks.toMutableList()
        println("TEMP BLOCKS BEFORER APPENDING BELOW: $tempBlocks")
        tempBlocks.addAll(botField.blocks)
        println("tempBlocks AFTER APPENDING BELOW, AND before normalizing: $tempBlocks")
        normalizeBlockList(tempBlocks)
        println("after normalizing: $tempBlocks")
        // Measure the hypothetical merged line
        val measurement = measureLine(tempBlocks, maxWidth, tm, sty)

        if(!measurement.overflow){
            if(fields[currentFieldIdx].blocks == tempBlocks){
                fields[currentFieldIdx] = fields[currentFieldIdx].normalise()
                println("Then leave, what the hell is the point anymore")
                break
            }
            //clear the current field.
            fields[currentFieldIdx].blocks.clear()
            //set it to the temp field which didn't overflow.
            fields[currentFieldIdx].blocks.addAll(tempBlocks)
            //MUST FOLLOW PROTCOL BEEP BOOP.
            fields[currentFieldIdx].normalise()
            //remove the below.
            fields.removeAt(currentFieldIdx+1)
            break
        }
        else{
            //else, we want to split at the OF.
            val (left, right) = splitBlockListAt(tempBlocks, measurement.overflowPos!!)
            if (left == tempBlocks) {
                println("NO CHANGE. LEAVING.")
                break
            }
            else {
                //Then, current field should be updated to be left.
                fields[currentFieldIdx].blocks.clear()
                fields[currentFieldIdx].blocks.addAll(left)
                fields[currentFieldIdx].normalise()
                //Below field should be updated to right.
                fields[currentFieldIdx+1].blocks.clear()
                fields[currentFieldIdx+1].blocks.addAll(right)
                fields[currentFieldIdx + 1].normalise()
                //increment to continue.
                currentFieldIdx++
            }
        }
    }

}

/*
Called within Input.kt whenever backspace is registered.

Makes determinations on isSelecting vs. Not

Makes determination on whether or not on calling attemptPullup() for iterative pullup would be necessary or not.

 */
fun DocumentState.onBackSpace(){

    //This first case would be beginning our pullup by having the root field be the above
    val pullupCaseA: Boolean = (isCaretAtOriginOfField() && thereExistsAnAboveField())
    //this other case would be beginning our pullup by have the root be the CURRENT field.
    val pullupCaseB: Boolean = (!isCaretAtOriginOfField() && !doesFieldHaveNL() && !isFieldLast(globalCaret.value.fieldId))

    val requirePullup: Boolean = pullupCaseA || pullupCaseB

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
/*
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

 */
            /* ───────────── 2) traversal-unit deletion (offset == 0) ───────────── */
            if (curIdx == 0) return                         // nothing to the left
            val prev = blocks[curIdx - 1]

// Currently we only expect ImageBlock or (in the future) other non-text units
            if (prev is Block.ImageBlock || prev is Block.DelimiterBlock) {
                // We need to know where the caret will land *before* normalizing.
                // If we are deleting a block between two TextBlocks, the caret should
                // land in the preceding TextBlock at its end.
                val isBetweenTextBlocks = curIdx > 1 && blocks[curIdx - 2] is Block.TextBlock
                val landingBlock = if (isBetweenTextBlocks) {
                    blocks[curIdx - 2] as Block.TextBlock
                } else null

                // Perform the deletion.
                blocks.removeAt(curIdx - 1)

                // Now, tell the entire field to enforce the protocol.
                // This will handle merging, empty block creation, etc.
                fields[fieldIdx] = fields[fieldIdx].normalise()

                // After normalization, the block IDs may have changed. We must find the
                // new block and place the caret correctly.
                if (landingBlock != null) {
                    // Find the potentially merged block. We search by the original left block's ID.
                    // `normalise` preserves the ID of the left-hand block in a merge.
                    val newHostBlock = fields[fieldIdx].blocks.find { it.id == landingBlock.id } as? Block.TextBlock
                    if (newHostBlock != null) {
                        globalCaret.value = caret.copy(
                            blockId       = newHostBlock.id,
                            offsetInBlock = landingBlock.length // Place caret at the original join point
                        )
                    } else {
                        // This case is unlikely, but as a fallback, we can use the current block.
                        globalCaret.value = caret
                    }
                } else {
                    // Caret was not between two TextBlocks, so its host block is still valid.
                    globalCaret.value = caret
                }

                onGlobalCaretMoved()
            }
            //NO need to continue.
            return
        }
        else {
            require(pullupCaseA.xor(pullupCaseB)) {      // or `pullupCaseA != pullupCaseB`
                "pullupCaseA and pullupCaseB cannot both be true"
            }
            println("---------------------------- REQUIRE pullUp()")
            val caret = globalCaret.value
            val currentFieldIdx = fields.indexOfFirst { it.id == caret.fieldId }

            /*
            So, at this point, we know that pullup is required. There are 2 cases that we will handle.

            Regardless of case, it implies that we will process a deletion, and then hand off initial information for attemptPullup.
            The initial information is to 'get the ball rolling'.

            Global caret should NOT be altered at all after we handed the information off for pullup. It is specifically handled before
            the hand off is made.
             */

            //We know that pullupcaseA and pullupCaseB will never be equal, especially due to the require() above.

            //This first case would be beginning our pullup by having the root field be the above
            if(pullupCaseA){
                println("PULLUP CASE A")
                val topFieldIdx = currentFieldIdx - 1
                require(topFieldIdx >= 0){"should be impossible. top field doesn't exist. this implies current field is root."}
                val topField = fields[topFieldIdx]
                val topBlocks = topField.blocks
                val lastBlock = topBlocks.lastOrNull()

                when (lastBlock) {
                    // Case A.1: The line above ends with a newline. Delete it.
                    is Block.DelimiterBlock -> {
                        topBlocks.removeLast()
                        val newLastBlock = topBlocks.last() as Block.TextBlock // Should be a TB
                        globalCaret.value = caret.copy(
                            fieldId = topField.id,
                            blockId = newLastBlock.id,
                            offsetInBlock = newLastBlock.length
                        )
                    }
                    // Case A.2: The line above ends with text. Delete the last character.
                    is Block.TextBlock -> {
                        if (lastBlock.length > 0) {
                            val newAS = lastBlock.textFieldValue.annotatedString.deleteCharBeforeCaretOffset(lastBlock.length)
                            val newTFV = lastBlock.textFieldValue.copy(annotatedString = newAS)
                            topBlocks[topBlocks.lastIndex] = lastBlock.copy(textFieldValue = newTFV)
                            globalCaret.value = caret.copy(
                                fieldId = topField.id,
                                blockId = lastBlock.id,
                                offsetInBlock = lastBlock.length - 1
                            )
                        } else {
                            // An empty TextBlock implies an Image before it (P-2). Delete both.
                            if (topBlocks.size >= 2 && topBlocks[topBlocks.lastIndex - 1] is Block.ImageBlock) {
                                topBlocks.removeLast() // remove empty text block
                                topBlocks.removeLast() // remove image block
                                val newLastBlock = topBlocks.last() as Block.TextBlock
                                globalCaret.value = caret.copy(
                                    fieldId = topField.id,
                                    blockId = newLastBlock.id,
                                    offsetInBlock = newLastBlock.length
                                )
                            }
                        }
                    }
                    else -> {

                    throw Exception("Should be unreachable.")
                    /* Should be unreachable given protocol invariants */ }
                }
                onGlobalCaretMoved()
                tryPullUp(topFieldIdx) // Kick off pull-up from the modified top field


            }
            //this other case would be beginning our pullup by have the root be the CURRENT field.
            else if(pullupCaseB) {
                println("PULLUP CASE B")
                val currentBlocks = fields[currentFieldIdx].blocks
                val currentBlockIdx = currentBlocks.indexOfFirst { it.id == caret.blockId }
                val currentTB = currentBlocks[currentBlockIdx] as Block.TextBlock

                // Perform the single character deletion in the current field.
                val oldTFV = currentTB.textFieldValue
                val newAS = oldTFV.annotatedString.deleteCharBeforeCaretOffset(caret.offsetInBlock)
                val newTFV = oldTFV.copy(
                    annotatedString = newAS,
                    selection = TextRange(caret.offsetInBlock-1))
                currentBlocks[currentBlockIdx] = currentTB.copy(textFieldValue = newTFV)
                println("in pullup case b just updated block list for the field. We need to make sure this mutated the actual state, and isn't just a copy.")
                // Update caret state ONCE.
                globalCaret.value = caret.copy(offsetInBlock = caret.offsetInBlock - 1)
                onGlobalCaretMoved()

                // Kick off pull-up from the current field, to pull the next field into it.
                tryPullUp(currentFieldIdx)
            }

            return
        }

    }

}

fun DocumentState.splitBlockListAt(blocks: MutableList<Block>, pos: Pos):Pair<MutableList<Block>, MutableList<Block>> {
    val left  = blocks.take(pos.blockIndexWithinList)
    val right = blocks.drop(pos.blockIndexWithinList)

    val newTop  = Field("",blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = Field("",blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    return Pair(newTop.blocks.toMutableList(), newBottom.blocks.toMutableList())
}

fun DocumentState.splitFieldAt(field: Field, pos: Pos):Pair<MutableList<Block>, MutableList<Block>> {
    val left  = field.blocks.take(pos.blockIndexWithinList)
    val right = field.blocks.drop(pos.blockIndexWithinList)

    val newTop  = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    return Pair(newTop.blocks.toMutableList(), newBottom.blocks.toMutableList())
}
fun DocumentState.splitFieldAt(fieldIdx: Int, pos: Pos) {
    val src = fields[fieldIdx]
    val left  = src.blocks.take(pos.blockIndexWithinList)
    val right = src.blocks.drop(pos.blockIndexWithinList)

    val newTop  = src.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = src.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    fields[fieldIdx] = newTop
    fields.add(fieldIdx + 1, newBottom)
}

fun DocumentState.measureText(
    string: AnnotatedString,
    maxWidth: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
):TextLayoutResult{
    val result = textMeasurer.measure(
        text = string,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidth),
        maxLines = 1,
        softWrap = false
    )
    return result
}

fun DocumentState.measureField(
    blocks: List<Block>,
    maxWidth: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
):MeasureResult{
    var sumWidths = 0

    var didOF: Boolean = false
    var hasHardBreak: Boolean = false
    var hardBreakPos: Pos? = null
    var overflowPos: Pos? = null
    val blockWidths = mutableListOf<Int>()

    for ((idx, blk) in blocks.withIndex()) {

        val blockWidth = blk.measure(
            textMeasurer,
            textStyle,
            maxWidth
        )
        blockWidths.add(idx, blockWidth)
        if (blk is Block.DelimiterBlock && blk.kind == Block.DelimiterBlock.Kind.NewLine && hasHardBreak == false) {
            hasHardBreak = true
            hardBreakPos = Pos(idx, 0)
            didOF = true
        }

        sumWidths += blockWidth
        if(sumWidths > maxWidth && didOF == false){

            didOF = true
            overflowPos = Pos(idx, 0)

        }
    }

    return MeasureResult(
        width = sumWidths,
        overflow = didOF,
        overflowPos = overflowPos,
        hardBreakPos = hardBreakPos,
        blockWidths = blockWidths
    )
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

    //println("in updateGlobalCaretPosition()------------------------------------------------------------------------ block.textlayoutresult: ${block.textLayoutResult} ")
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
    //println("-------------------------------------------------------------------------------")
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

       // println("onGlobalCaretMoved() current block.textlayoutresult ${textBlock.textLayoutResult}")
      //  println("tfv updated in onGlobalCaretMoved()")
    }
}

fun DocumentState.updateFocusedBlock(blockId: String) {
    focusedBlock.value = blockId
}
fun DocumentState.updateGlobalCaretFieldAndBlockId(
    fieldId:String,
    blockId:String
){
    globalCaret.value = globalCaret.value.copy(
        fieldId = fieldId,
        blockId = blockId
    )
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

fun DocumentState.getMaxWidth(): Int{
    return maxWidth
}
fun DocumentState.getDefaultTextStyle():TextStyle{
    return defaultTextStyle
}
fun DocumentState.getTextMeasurer():TextMeasurer{
    require(textMeasurer != null){"textMeasurer should not be null"}
    return textMeasurer as TextMeasurer
}

/*
This fxn is called from onValueChange callback within BasicTextField.
We use this to update the associated TextBlock to said BasicTextField.
We need to ensure that we do not update the field (identified via field id)
with a TextBlock that would cause OF (ie the updated field's width exceeds the previous, which implies
exceeding the max width constraint defined within DocumentState.
 */
fun DocumentState.processTFVUpdate(
    fieldId: String,
    blockId: String,
    newValue: TextFieldValue
) {
    val fieldIndex = fields.indexOfFirst { it.id == fieldId }
    require(fieldIndex > -1) { "field doesn't exist" }

    val field = fields[fieldIndex]

    val blockIndex = field.blocks.indexOfFirst { it.id == blockId }
    require(blockIndex > -1) { "block doesn't exist" }
    val measuredField = measureField(
        blocks = field.blocks.toList(),
        maxWidth = getMaxWidth(),
        textMeasurer = getTextMeasurer(),
        textStyle = getDefaultTextStyle()
    )

    val widthOfTextBlockBeforeUpdate = measuredField.blockWidths[blockIndex]

    /*
    Very importantly, it is understood that at this point, the field itself doesn't cause any overflow.
    The onvaluechange should not been invoked if there was OF - OF should be handled before it would be invoked,
    this idea is separate to the current function.
     */
    val widthToCauseOF = getMaxWidth() - widthOfTextBlockBeforeUpdate
    require(widthOfTextBlockBeforeUpdate <= getMaxWidth()) { "Should be impossible. width of textblock should certainly be below max width." }
    require(textMeasurer != null) { "textMeasurer should not be null." }
    val measuredString = measureText(
        newValue.annotatedString,
        maxWidth,
        textMeasurer!!,
        defaultTextStyle
    )

    val updateCausesOF: Boolean = measuredString.size.width >= widthToCauseOF

    val block = fields[fieldIndex].blocks[blockIndex] as Block.TextBlock
    val newTFV = block.textFieldValue.copy(
        annotatedString = newValue.annotatedString,
        selection = newValue.selection
    )
    if (!updateCausesOF) {


        fields[fieldIndex].blocks[blockIndex] = block.copy(textFieldValue = newTFV)
        fields[fieldIndex].normalise()
        // *** unconditionally sync the global caret to whatever the local selection now is ***
        globalCaret.value = globalCaret.value.copy(
            fieldId = fieldId,
            blockId = block.id,
            offsetInBlock = newValue.selection.start
        )
        // immediately recompute the global caret position on screen
        onGlobalCaretMoved()
        return
    } else {
        var localField = LocalFieldForMutation(
            id = "deez nuts",
            blocks = field.blocks
        )

        localField.blocks[blockIndex] = block
        val localBlock = LocalTextBlockForMutation(
            id = "my nuts",
            textFieldValue = newTFV,
        )

        val measuredLocalField = measureField(
            blocks = field.blocks.toList(),
            maxWidth = getMaxWidth(),
            textMeasurer = getTextMeasurer(),
            textStyle = getDefaultTextStyle()
        )

        //short-circuit: if blockIndex is the last index of the field, then we can literally just split that fookin block and prepend the end of it to the below field.
        if (blockIndex == localField.blocks.size - 1) {
            if(measuredField.overflowPos == null) return
            val splitBlock = splitTextBlock(block, measuredField.overflowPos!!.offsetInBlock)
            fields[fieldIndex].blocks[localField.blocks.size-1] = splitBlock.first
            fields[fieldIndex+1].blocks.add(0, splitBlock.second)
        }
        /*
        otherwise, we must
        1.) determine the type of the last block within the field.
        2.) if text: same as above mate
        3.) so, an image or a delimiter (perhaps not the case anymore, if we've updated the code since then)
            Then, let's split AT this fookin block here, and prepend to the below field.
         */
        else {
            val bid = localField.blocks.lastIndex
            val blok = localField.blocks[bid]
        }

    }
}

@OptIn(ExperimentalUuidApi::class)
fun DocumentState.splitTextBlock(block: Block.TextBlock, splitAt: Int):Pair<Block.TextBlock, Block.TextBlock>{

    val origTFV = block.textFieldValue
    val leftString = origTFV.annotatedString.subSequence(0, splitAt)
    val rightString = origTFV.annotatedString.subSequence(splitAt, origTFV.annotatedString.length)

        val leftTFV = TextFieldValue(
            annotatedString = leftString,
            selection = TextRange(leftString.length)
        )

        val rightTFV = TextFieldValue(
            annotatedString = rightString,
            selection = TextRange.Zero
    )

    val leftBlock = Block.TextBlock(
        id = block.id,
        textFieldValue = leftTFV,
        focusRequester = block.focusRequester
    )
    val rightBlock = Block.TextBlock(
        id = Uuid.random().toString(),
        textFieldValue = rightTFV,
        focusRequester = FocusRequester()
    )

    return Pair(leftBlock, rightBlock)

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
    fields[fid].normalise()
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
