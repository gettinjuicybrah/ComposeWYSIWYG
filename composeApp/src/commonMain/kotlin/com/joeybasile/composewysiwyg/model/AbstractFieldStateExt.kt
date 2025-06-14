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
import com.joeybasile.composewysiwyg.model.linewrap.genericPullDown
import com.joeybasile.composewysiwyg.model.linewrap.getLocGlobalFieldOffsetForABlock
import com.joeybasile.composewysiwyg.model.linewrap.splitTextBlock
import com.joeybasile.composewysiwyg.model.linewrap.totalLength
import com.joeybasile.composewysiwyg.util.deleteCharBeforeCaretOffset
import com.joeybasile.composewysiwyg.util.sliceRange
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class MeasureResult(
    val width: Int,
    val overflow: Boolean,
    val overflowPos: Pos? = null,
    val hardBreakPos: Pos? = null,          // first NL *inside* line, if any
    val blockWidths: List<Int> = emptyList(),
    val blockMeasureResults: List<BlockMeasureResult> = emptyList()
)

data class BlockMeasureResult(
    val isTextBlock: Boolean,
    val textBlockResult: TextLayoutResult?,
    val elseResult: Int?
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
            require(
                !(f.blocks[i] is Block.TextBlock &&
                        f.blocks[i + 1] is Block.TextBlock)
            ) {
                "P-3 violated in field[$fIdx] at blocks[$i]"
            }
            if (f.blocks[i] is Block.ImageBlock) {
                require(
                    i + 1 <= f.blocks.lastIndex &&
                            f.blocks[i + 1] is Block.TextBlock
                ) {
                    "P-2 violated: Image not followed by Text in field[$fIdx]"
                }
            }
        }
        // P-1 – blank line canonical form
        if (f.blocks.isEmpty())
            error("P-1 violated: empty field")
    }
}

private fun DocumentState.tryPullUp(rootFieldIdx: Int) {
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
        // val measurement = measureLine(tempBlocks, maxWidth, tm, sty)
        val measurement = measureField(tempBlocks, maxWidth, tm, sty)
        if (!measurement.overflow) {
            if (fields[currentFieldIdx].blocks == tempBlocks) {
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
            fields.removeAt(currentFieldIdx + 1)
            break
        } else {
            //else, we want to split at the OF.
            val (left, right) = splitBlockListAt(tempBlocks, measurement.overflowPos!!)
            if (left == tempBlocks) {
                println("NO CHANGE. LEAVING.")
                break
            } else {
                //Then, current field should be updated to be left.
                fields[currentFieldIdx].blocks.clear()
                fields[currentFieldIdx].blocks.addAll(left)
                fields[currentFieldIdx].normalise()
                //Below field should be updated to right.
                fields[currentFieldIdx + 1].blocks.clear()
                fields[currentFieldIdx + 1].blocks.addAll(right)
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
fun DocumentState.onBackSpace() {

    //This first case would be beginning our pullup by having the root field be the above
    val pullupCaseA: Boolean = (isCaretAtOriginOfField() && thereExistsAnAboveField())
    //this other case would be beginning our pullup by have the root be the CURRENT field.
    val pullupCaseB: Boolean =
        (!isCaretAtOriginOfField() && !doesFieldHaveNL() && !isFieldLast(globalCaret.value.fieldId))

    val requirePullup: Boolean = pullupCaseA || pullupCaseB
    println(" selection state is active: ${selectionState.isActive}")
    //Right now, only case is for inactive selection.
    if (!selectionState.isActive) {
        println("require pullup: ${requirePullup}")
        //This implies that the offset is > 0 (and of course witin a textblock) and the field has a newline block delimiter at the end, implying that
        //any potential fields below would not be pulled up, because the newline delimiter block is 'preventing' such.
        if (!requirePullup) {
            println("----------------------------NOT requirePullup()")
            val caret = globalCaret.value
            val fieldIdx = fields.indexOfFirst { it.id == caret.fieldId }
            if (fieldIdx < 0) return            // nothing sane to do
            val blocks = fields[fieldIdx].blocks

            val curIdx = blocks.indexOfFirst { it.id == caret.blockId }
            if (curIdx < 0){
                println("CUR INDX <0, RETURNING FROM BACKSPACE")
                return }

            if(!(blocks[curIdx] is Block.TextBlock)) {
                println("${blocks[curIdx]}")
                println("current block is NOT a text block. RETURNING.")
                return
            }   // P-5 guarantees this cast
            else{
                val curTB = blocks[curIdx] as Block.TextBlock
            /* ───────────── 1) ordinary character deletion ───────────── */
            if (caret.offsetInBlock > 0) {
                val oldAS = curTB.textFieldValue.annotatedString
                val newAS = buildAnnotatedString {
                    append(oldAS.subSequence(0, caret.offsetInBlock))
                    append(oldAS.subSequence(caret.offsetInBlock, oldAS.length))
                }
                val newTFV = curTB.textFieldValue.copy(
                    annotatedString = newAS,
                    selection = TextRange(caret.offsetInBlock - 1)
                )
                blocks[curIdx] = curTB.copy(textFieldValue = newTFV)

                globalCaret.value = caret.copy(offsetInBlock = caret.offsetInBlock - 1)
                onGlobalCaretMoved()
                return
            }}
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
            println(" BEFORE THE CHECK OF CURRENT BLK INDEX 0, PROGRAMMED TO RETURN IF SO.")
            println(" BEFORE THE CHECK OF CURRENT BLK INDEX 0, PROGRAMMED TO RETURN IF SO.")
            println(" BEFORE THE CHECK OF CURRENT BLK INDEX 0, PROGRAMMED TO RETURN IF SO.")
            if (curIdx == 0) return                         // nothing to the left
            println("DID NOT RETURN")
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
                    val newHostBlock =
                        fields[fieldIdx].blocks.find { it.id == landingBlock.id } as? Block.TextBlock
                    if (newHostBlock != null) {
                        globalCaret.value = caret.copy(
                            blockId = newHostBlock.id,
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
        } else {
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
            if (pullupCaseA) {
                println("PULLUP CASE A")
                val topFieldIdx = currentFieldIdx - 1
                require(topFieldIdx >= 0) { "should be impossible. top field doesn't exist. this implies current field is root." }
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
                            val newAS =
                                lastBlock.textFieldValue.annotatedString.deleteCharBeforeCaretOffset(
                                    lastBlock.length
                                )
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
                        /* Should be unreachable given protocol invariants */
                    }
                }
                onGlobalCaretMoved()
                tryPullUp(topFieldIdx) // Kick off pull-up from the modified top field


            }
            //this other case would be beginning our pullup by have the root be the CURRENT field.
            else if (pullupCaseB) {
                println("PULLUP CASE B")
                val currentBlocks = fields[currentFieldIdx].blocks
                val currentBlockIdx = currentBlocks.indexOfFirst { it.id == caret.blockId }
                val currentTB = currentBlocks[currentBlockIdx] as Block.TextBlock

                // Perform the single character deletion in the current field.
                val oldTFV = currentTB.textFieldValue
                val newAS = oldTFV.annotatedString.deleteCharBeforeCaretOffset(caret.offsetInBlock)
                val newTFV = oldTFV.copy(
                    annotatedString = newAS,
                    selection = TextRange(caret.offsetInBlock - 1)
                )
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

fun DocumentState.splitBlockListAt(
    blocks: MutableList<Block>,
    pos: Pos
): Pair<MutableList<Block>, MutableList<Block>> {
    println("entered splitBlockListAt()")
    val left = blocks.take(pos.blockIndexWithinList)
    val right = blocks.drop(pos.blockIndexWithinList)

    val newTop = Field("", blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = Field("", blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    return Pair(newTop.blocks.toMutableList(), newBottom.blocks.toMutableList())
}


@OptIn(ExperimentalUuidApi::class)
fun DocumentState.splitFieldAt(
    field: Field,
    pos: Pos
): Pair<MutableList<Block>, MutableList<Block>>? {
    println("entered splitFieldtAt()")
    val index = pos.blockIndexWithinList
    val offset = pos.offsetInBlock
    val blockListSize = field.blocks.size
    val posBlock = field.blocks[index]
    val posBlockTypeIsText: Boolean = posBlock is Block.TextBlock
    val posBlockTypeIsImage: Boolean = posBlock is Block.ImageBlock
    val posBlockTypeIsDelimiter: Boolean = posBlock is Block.DelimiterBlock

    var left: List<Block> = emptyList()
    var right: List<Block> = emptyList()

    if ((posBlockTypeIsText || posBlockTypeIsImage || posBlockTypeIsDelimiter) && offset == 0) {
println("IF 1 IN SPLITFIELDAT")
        left = field.blocks.take(index)
        right = field.blocks.drop(index)
        val newLeft = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
        val newRight = field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()
        return Pair(newLeft.blocks.toMutableList(), newRight.blocks.toMutableList())

    }

    //implies splitting the textblock. the left side will remain on the left, and the right on the right.
    if (posBlockTypeIsText) {
        println("TEXT BLOCK IN SPLIT FIELD")
        posBlock as Block.TextBlock

        val caretStart = posBlock.textFieldValue.selection.start

        if (blockListSize == 1) {
            println("BLOCK SIZE 1 IN SPLIT FIELD")
            /*
            left += Block.TextBlock(
                id = Uuid.random().toString(), textFieldValue = TextFieldValue(
                    annotatedString = AnnotatedString(""), selection = TextRange(0)
                ), focusRequester = FocusRequester()
            )
            right += field.blocks[0]
            val newLeft = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
            val newRight =
                field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()
            return Pair(newLeft.blocks.toMutableList(), newRight.blocks.toMutableList())

             */
        } else {
            left = field.blocks.take(index - 1)
            right = field.blocks.drop(index)
        }
        val caretLocGlobOffset = getLocGlobalFieldOffsetForABlock(field, posBlock.id, caretStart)
        val posOffsetLocGlobOffset = getLocGlobalFieldOffsetForABlock(field, posBlock.id, offset)
        //if()

        val (leftText, rightText) = splitTextBlock(posBlock, offset)
        println("left: $left")
        println("left text: $leftText")
        println("right text: $rightText")
        println("right: $right")
        left += leftText
        right += rightText

        println("")
        println("updated right: $right")
        println("updated left: $left")
        val newLeft = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
        val newRight = field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()
        return Pair(newLeft.blocks.toMutableList(), newRight.blocks.toMutableList())

    } else if (posBlockTypeIsImage) {
        posBlock as Block.ImageBlock
        left = field.blocks.take(index)
        right = field.blocks.drop(index)
        val newLeft = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
        val newRight = field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()
        return Pair(newLeft.blocks.toMutableList(), newRight.blocks.toMutableList())
    } else {//else if (posBlockTypeIsDelimiter) {
        posBlock as Block.DelimiterBlock
        println("block is newline delimiter")
        if (posBlock.kind == Block.DelimiterBlock.Kind.NewLine) {
            left = field.blocks.take(index)
            right = field.blocks.drop(index)
            val newLeft = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
            val newRight =
                field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()
            return Pair(newLeft.blocks.toMutableList(), newRight.blocks.toMutableList())
        }
    }
    return null

    /*
        var before: List<Block>
        var after: List<Block>
        // Blocks before and after the split point
        if (index == 0) {
            before = field.blocks.take(index)
            after = field.blocks.drop(index)
        } else {
            before = field.blocks.take(index)
            after = field.blocks.drop(index)
        }
        println("index: $index")
        println("before: $before")
        println("after: $after")
        val leftBlocks = mutableListOf<Block>()
        val rightBlocks = mutableListOf<Block>()

        // Handle the block at the split index
        val splitBlock = field.blocks[index]
        if (splitBlock is Block.TextBlock) {
            val tfv = splitBlock.textFieldValue
            // Split the text block at the given offset
            println("~~~~~~~~~~~~~ calling splitTextBlock(). splitBlock: ${splitBlock}, tfv.selection.start: ${tfv.selection.start}")
            println("txt length: ${splitBlock.textFieldValue.text.length}")
            println("global caret offset in block: ${globalCaret.value.offsetInBlock}")
            val (leftText, rightText) = splitTextBlock(splitBlock, tfv.selection.start)
            println("~~~~~~~~~~~~~~~ result from splitTextBlocks:")
            println("leftText: ${leftText}")
            println("rightText: ${rightText}")

            println()
            println("leftblocks: $leftBlocks")
            leftBlocks += before
            println("leftblocks after adding before: $leftBlocks")
            leftBlocks += leftText
            println("leftblocks after adding left text: $leftBlocks")

            println("rightblocks: $rightBlocks")
            rightBlocks += rightText
            println("rightblocks after adding rightText: $rightBlocks")
            rightBlocks += after
            println("rightblocks after adding after: $rightBlocks")
        } else {
            println("else option. splitblock is not textblock.")
            leftBlocks += field.blocks.take(index)
            rightBlocks += after
        }
        println("after if statement")
        println("")
        println("leftBlocks: $leftBlocks")
        println("rightBlocks: $rightBlocks")
        println("before printing new bottom and top")
        println("")
        // Create new Fields and normalise them
        val newTop = field.copy(blocks = mutableStateListOf(*leftBlocks.toTypedArray())).normalise()
        val newBottom = field.copy(blocks = mutableStateListOf(*rightBlocks.toTypedArray())).normalise()
        println("newTop.blocks: ${newTop.blocks}")
        println("newBottom.blocks: ${newBottom.blocks}")
        println("leaving splitFieldtAt()")
        println("leaving splitFieldtAt()")
        println("leaving splitFieldtAt()")
        println("leaving splitFieldtAt()")
        return Pair(
            newTop.blocks.toMutableList(),
            newBottom.blocks.toMutableList()
        )

     */
    /*
    val left = field.blocks.take(pos.blockIndexWithinList)
    val right = field.blocks.drop(pos.blockIndexWithinList)

    val newTop = field.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = field.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    return Pair(newTop.blocks.toMutableList(), newBottom.blocks.toMutableList())
     */
}

fun DocumentState.splitFieldAt(fieldIdx: Int, pos: Pos) {
    val src = fields[fieldIdx]
    val left = src.blocks.take(pos.blockIndexWithinList)
    val right = src.blocks.drop(pos.blockIndexWithinList)

    val newTop = src.copy(blocks = mutableStateListOf(*left.toTypedArray())).normalise()
    val newBottom = src.copy(blocks = mutableStateListOf(*right.toTypedArray())).normalise()

    fields[fieldIdx] = newTop
    fields.add(fieldIdx + 1, newBottom)
}

fun DocumentState.measureText(
    string: AnnotatedString,
    maxWidth: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle
): TextLayoutResult {
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
): MeasureResult {
    println("---")
    println("           entering measuredField()")
    println("---")
    var sumWidths = 0

    var didOF: Boolean = false
    var hasHardBreak: Boolean = false
    var hardBreakPos: Pos? = null
    var overflowPos: Pos? = null
    val blockWidths = mutableListOf<Int>()
    var blockMeasureResults = mutableListOf<BlockMeasureResult>()

    var NLCount = 0

    for ((idx, blk) in blocks.withIndex()) {
        println("block $idx")
        var offsetInBlock: Int = 0

        if (blk is Block.TextBlock) {
            val result = blk.measure(
                textMeasurer,
                textStyle,
                maxWidth
            )
            val xPos = maxWidth.toFloat() - Float.MIN_VALUE
            blockMeasureResults.add(
                idx,
                BlockMeasureResult(isTextBlock = true, textBlockResult = result, null)
            )
            println("is TextBlock. textBlockResult: $result")
            println("blockMeasurerResults for $idx: ${blockMeasureResults}")
            var blockWidth = result.size.width
            sumWidths += blockWidth
            offsetInBlock = result.getOffsetForPosition(Offset(xPos, 0f))
            blockWidths.add(idx, blockWidth)
            println("blk.length: ${blk.length}")
            println("block pixel width: ${blockWidth}")
        } else if (blk is Block.ImageBlock) {
            val result = blk.measure(
                textMeasurer,
                textStyle,
                maxWidth
            )
            blockMeasureResults.add(idx, BlockMeasureResult(isTextBlock = false, null, 0))
            var blockWidth = result
            blockWidths.add(idx, blockWidth)
            offsetInBlock = 0
            sumWidths += blockWidth
        } else if (blk is Block.DelimiterBlock) {
            val result = blk.measure(
                textMeasurer,
                textStyle,
                maxWidth
            )
            if (blk.kind == Block.DelimiterBlock.Kind.NewLine) {
                NLCount++
            }
            blockMeasureResults.add(idx, BlockMeasureResult(isTextBlock = false, null, 0))
            var blockWidth = result
            blockWidths.add(idx, blockWidth)
            if (blk is Block.DelimiterBlock && blk.kind == Block.DelimiterBlock.Kind.NewLine && hasHardBreak == false && NLCount > 1) {
                println("in measureField didOF determined in else clause for DelimiterBlock. idx: $idx")
                hasHardBreak = true
                hardBreakPos = Pos(idx, 0)
                didOF = true
            }
            if (blk is Block.DelimiterBlock && blk.kind == Block.DelimiterBlock.Kind.NewLine) {
                offsetInBlock = 0
                sumWidths += blockWidth
            }
        }


        if (sumWidths >= maxWidth) {
            println("in measureField if sumwidths >= maxwidth.  didOF determined. idx: $idx")
            didOF = true
            overflowPos = Pos(idx, offsetInBlock)
        }
        println("SUM WIDTHS: ${sumWidths} AND MAXWIDTH: $maxWidth")

    }
    println("---")
    println("           leaving measuredField()")
    println("---")
    return MeasureResult(
        width = sumWidths,
        overflow = didOF,
        overflowPos = overflowPos,
        hardBreakPos = hardBreakPos,
        blockWidths = blockWidths,
        blockMeasureResults = blockMeasureResults
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
        val w = 0//blk.measure(textMeasurer, textStyle, maxWidth)
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
    println("global caret local offset: ${localOffset}")
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
    fieldId: String,
    blockId: String
) {
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


fun DocumentState.handleOnTextLayout(fieldId: String, blockId: String, result: TextLayoutResult) {

    val fid = fields.indexOfFirst {
        it.id == fieldId
    }
    val blocksList = fields[fid].blocks
    val bid = fields[fid].blocks.indexOfFirst {
        it is Block.TextBlock && it.id == blockId

    }
    println("current field index: ${fields.indexOfFirst { it.id == fieldId }}")
    println("current block uuid: ${blockId}")
    println("size of block list in field: ${fields[fid].blocks.size}")
    for(blk in fields[fid].blocks){
        println("^^^^^^^^^^^^^^^^^^^^^^^^ ${blk.id} AND THE BLK ITSELF: ${blk}")
    }
    require(blocksList[bid] is Block.TextBlock)
    require(fid > -1) { "field doesn't exit" }

    require(bid > -1) { "block doesn't exit" }
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
    if (globalCaret.value.blockId == block.id) {
        updateGlobalCaretPosition()
        println("just called update global caret pos from line 146")
    }
    println("block set to result: ${updatedTextBlock.textLayoutResult}")
    println("123456789 LEAVING handleOnTextLayoutResult  in abstractfieldstateext line 147")
}

fun DocumentState.getMaxWidth(): Int {
    return maxWidth
}

fun DocumentState.getDefaultTextStyle(): TextStyle {
    return defaultTextStyle
}

fun DocumentState.getTextMeasurer(): TextMeasurer {
    require(textMeasurer != null) { "textMeasurer should not be null" }
    return textMeasurer as TextMeasurer
}

@OptIn(ExperimentalUuidApi::class)
fun DocumentState.bprocessTFVUpdate(
    fieldId: String,
    blockId: String,
    newValue: TextFieldValue
) {
    val fieldIndex = fields.indexOfFirst { it.id == fieldId }
    require(fieldIndex > -1) { "field doesn't exist" }

    val field = fields[fieldIndex]
    val blockIndex = field.blocks.indexOfFirst { it.id == blockId }
    require(blockIndex > -1) { "block doesn't exist" }

    val block = fields[fieldIndex].blocks[blockIndex] as Block.TextBlock
    val newTFV = block.textFieldValue.copy(
        annotatedString = newValue.annotatedString,
        selection = newValue.selection
    )

    var blockListCopy = fields[fieldIndex].blocks.toList()
    blockListCopy = blockListCopy.toMutableStateList()
    blockListCopy.set(blockIndex, block.copy(textFieldValue = newTFV))
    val proposedField = field.copy(
        id = "my balls",
        blocks = blockListCopy
    )

    val result = getFieldReport(proposedField)

    if (!result.measureResult.overflow) {
        println("in bprocessTFVUpdate(). result.measureResult.overflow is false")
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
        println("leaving bprocessTFVUpdate()")
        return
    } else {
        println("in bprocessTFVUpdate(). result.measureResult.overflow is true")
        //Get the coordinates of the newValue.selection.start.
        //a) < than 500: update global caret to be such.
        //b >= than 500: implies that the global caret will reside in a later field than the current.

        val (fittingBlocks, overflowBlocks) = splitFieldAt(
            field = proposedField,
            pos = result.measureResult.overflowPos!!
        )!!

        val total = proposedField.totalLength()
        println("This field spans $total logical characters.")

        println("in bprocessTFVUpdate.")
        println("fittingBlocks: ${fittingBlocks}")
        println("overflowBlocks: ${overflowBlocks}")

        val xPos = maxWidth.toFloat() - Float.MIN_VALUE

        //Need to have fxn calculate the number of caret positions per field - tfv.length + 1 for every textblock in the field.
        //Then, need to be able to get a locally global caret position - so, instead of the local 0:length of it's textblock,
        //it needs to be 0:number of carets in field we calculated.
        val OFresult = result.measureResult.overflowPos
        val caretDist =
            globalCaret.value.globalPosition.x //caretDist = result.measureResult.blockMeasureResults[blockIndex].textBlockResult!!.getCursorRect(newValue.selection.start).right.coerceIn(0F, maxWidth.toFloat())
        val locGlobCaretOffset = getLocGlobalFieldOffsetForABlock(
            proposedField,
            globalCaret.value.blockId,
            globalCaret.value.offsetInBlock
        )
        val locGlobalOverflowPos = getLocGlobalFieldOffsetForABlock(
            proposedField,
            proposedField.blocks[OFresult.blockIndexWithinList].id,
            OFresult.offsetInBlock
        )
        println()
        println()
        println("caretDist: $caretDist, xPos: $xPos")
        println("global caret pos: ${globalCaret.value.globalPosition.x}")

        //The caret will be wrapped directly below, onto a new or existing line.
        //Then we will have to update the global caret within genericPullDown(). Boo.
        //if(caretDist >= xPos){

        //require(locGlobalOverflowPos != null && locGlobCaretOffset != null) { "should not be null" }
        if(locGlobalOverflowPos != null && locGlobCaretOffset != null) {
            if (locGlobCaretOffset >= locGlobalOverflowPos) {
                println("^^^^^^^^^^^^^^^^^^^^^^^^ caretDist GREATER OR EQUAL TO xPos")
                val splitPos = result.measureResult.overflowPos
                println("splitPos: $splitPos")
                val caretSnapshot = globalCaret.value

                val proposedCurrentFieldBlocks = fittingBlocks
                val proposedOverflowFieldBlocks = overflowBlocks
                val selection = locGlobCaretOffset - locGlobalOverflowPos

                fields.set(
                    fieldIndex, fields[fieldIndex].copy(
                        id = fieldId,
                        blocks = fittingBlocks.toMutableStateList()
                    )
                )

                //must insert field below, then prepend proposedOFBlocks to it, then update caret.
                if (fields.lastIndex == fieldIndex) {
                    val oid = overflowBlocks.indexOfFirst { it is Block.TextBlock }
                    val tb = overflowBlocks[oid] as Block.TextBlock
                    overflowBlocks.set(
                        oid,
                        tb.copy(
                            textFieldValue = tb.textFieldValue.copy(
                                selection = TextRange(
                                    selection,
                                    selection
                                )
                            )
                        )
                    )
                    val newFieldId = Uuid.random().toString()
                    fields.add(
                        fieldIndex + 1,
                        Field(
                            id = newFieldId,
                            blocks = overflowBlocks.toMutableStateList()
                        )
                    )
                    globalCaret.value = globalCaret.value.copy(
                        fieldId = newFieldId,
                        blockId = tb.id,
                        offsetInBlock = selection
                    )
                    onGlobalCaretMoved()
                } else {

                    if (overflowBlocks.size == 1 && overflowBlocks[0] is Block.DelimiterBlock) {
                        val block = overflowBlocks[0] as Block.DelimiterBlock
                        if (block.kind == Block.DelimiterBlock.Kind.NewLine) {
                            println("OI WE'VE GOT A NEW LINE MATE")
                            val uuid = Uuid.random().toString()
                            val newFieldId = Uuid.random().toString()
                            //added because have to have text block in order to render caret.
                            overflowBlocks.add(
                                0, Block.TextBlock(
                                    id = uuid,
                                    textFieldValue = TextFieldValue(
                                        annotatedString = AnnotatedString(""),
                                        selection = TextRange(0, 0)
                                    ),
                                    focusRequester = FocusRequester()
                                )
                            )
                            fields.add(
                                fieldIndex + 1,
                                Field(
                                    id = newFieldId,
                                    blocks = overflowBlocks.toMutableStateList()
                                )
                            )
                            globalCaret.value = globalCaret.value.copy(
                                fieldId = newFieldId,
                                blockId = uuid,
                                offsetInBlock = 0
                            )
                            onGlobalCaretMoved()

                        }
                    } else {

                        val oid = overflowBlocks.indexOfFirst { it is Block.TextBlock }
                        val tb = overflowBlocks[oid] as Block.TextBlock
                        overflowBlocks.set(
                            oid,
                            tb.copy(
                                textFieldValue = tb.textFieldValue.copy(
                                    selection = TextRange(
                                        selection,
                                        selection
                                    )
                                )
                            )
                        )
                        fields[fieldIndex + 1] = fields[fieldIndex + 1].copy(
                            blocks = (overflowBlocks + fields[fieldIndex + 1].blocks).toMutableStateList()
                        )
                        globalCaret.value = globalCaret.value.copy(
                            fieldId = fields[fieldIndex + 1].id,
                            blockId = tb.id,
                            offsetInBlock = selection
                        )
                        onGlobalCaretMoved()
                    }
                }



                genericPullDown(
                    initialProposedField = fields[fieldIndex + 1],
                    initialFieldIdInListToReplaceWith = fields[fieldIndex + 1].id,
                    purposeTFVUpdate = false
                )


            }
            //Means that the caret will not be wrapped below, but stay on the same line.
            else {
                println("^^^^^^^^^^^^^^^^^^^^^^^^ caretDist LESS THAN xPos")
                globalCaret.value = globalCaret.value.copy(
                    fieldId = fieldId,
                    blockId = block.id,
                    offsetInBlock = newValue.selection.start
                )
                // immediately recompute the global caret position on screen
                onGlobalCaretMoved()
                /*
                        genericPullDown(
                            initialProposedField = proposedField,
                            initialFieldIdInListToReplaceWith = fieldId,
                            //purposeTFVUpdate = false
                        )

             */


            }
        }

    }
    /*
        genericPullDown(
            initialProposedField = proposedField,
            initialFieldIdInListToReplaceWith = fieldId
        )

     */
}


fun DocumentState.aprocessTFVUpdate(
    fieldId: String,
    blockId: String,
    newValue: TextFieldValue
) {
    val fieldIndex = fields.indexOfFirst { it.id == fieldId }
    require(fieldIndex > -1) { "field doesn't exist" }

    val field = fields[fieldIndex]
    val blockIndex = field.blocks.indexOfFirst { it.id == blockId }
    require(blockIndex > -1) { "block doesn't exist" }

    val block = fields[fieldIndex].blocks[blockIndex] as Block.TextBlock
    val newTFV = block.textFieldValue.copy(
        annotatedString = newValue.annotatedString,
        selection = newValue.selection
    )

    var blockListCopy = fields[fieldIndex].blocks.toList()
    blockListCopy = blockListCopy.toMutableStateList()
    blockListCopy.set(blockIndex, block.copy(textFieldValue = newTFV))
    val proposedField = field.copy(
        id = "my balls",
        blocks = blockListCopy
    )
    genericPullDown(
        initialProposedField = proposedField,
        initialFieldIdInListToReplaceWith = fieldId
    )
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

    val updateCausesOF: Boolean = measuredString.size.width > widthToCauseOF

    val block = fields[fieldIndex].blocks[blockIndex] as Block.TextBlock
    val newTFV = block.textFieldValue.copy(
        annotatedString = newValue.annotatedString,
        selection = newValue.selection
    )
    if (!updateCausesOF) {
        println("IF 1. !updateCausesOF")

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
        println("ELSE IF 1. updateCausesOF")
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
            println("   -IF 1 blockIndex == localField.blocks.size - 1")
            if (measuredField.overflowPos == null) return
            println("CALLING SPLITTEXTBLOCK()")
            val splitBlock = splitTextBlock(block, measuredField.overflowPos!!.offsetInBlock)
            println("GOT BACK FROM SPLITTEXTBLACK()")
            fields[fieldIndex].blocks[localField.blocks.size - 1] = splitBlock.first
            fields[fieldIndex + 1].blocks.add(0, splitBlock.second)
        }
        /*
        otherwise, we must
        1.) determine the type of the last block within the field.
        2.) if text: same as above mate
        3.) so, an image or a delimiter (perhaps not the case anymore, if we've updated the code since then)
            Then, let's split AT this fookin block here, and prepend to the below field.
         */
        else {
            println("   -ELSE IF 1. !(blockIndex == localField.blocks.size - 1)")
            val bid = localField.blocks.lastIndex
            val blok = localField.blocks[bid]

        }


    }
}

data class FieldReport(
    val measureResult: MeasureResult,
    val numBlocks: Int,
    val isEmpty: Boolean,
    val newLineCount: Int,
    val doesFieldHaveNL: Boolean
)

fun DocumentState.getFieldReport(fieldId: String): FieldReport {
    val field = fields.find { it.id == fieldId }
    require(field != null) { "field doesn't exist" }
    val blocksList = field.blocks
    val numBlocks = blocksList.size
    val isEmpty = numBlocks == 0
    val newLineCount =
        blocksList.count { it is Block.DelimiterBlock && it.kind == Block.DelimiterBlock.Kind.NewLine }
    val doesFieldHaveNL = newLineCount > 0
    val measureResult = measureField(
        blocks = blocksList,
        maxWidth = getMaxWidth(),
        textMeasurer = getTextMeasurer(),
        textStyle = getDefaultTextStyle()
    )
    val fieldReport = FieldReport(
        measureResult = measureResult,
        numBlocks = numBlocks,
        isEmpty = isEmpty,
        newLineCount = newLineCount,
        doesFieldHaveNL = doesFieldHaveNL
    )
    return fieldReport
}

fun DocumentState.getFieldReport(field: Field): FieldReport {

    println("-----")
    println("-----")
    println("Entering getFieldReport()")
    println("-----")
    println("-----")

    val blocksList = field.blocks
    println("Block list: ${blocksList}")
    val numBlocks = blocksList.size
    val isEmpty = numBlocks == 0
    val newLineCount =
        blocksList.count { it is Block.DelimiterBlock && it.kind == Block.DelimiterBlock.Kind.NewLine }
    val doesFieldHaveNL = newLineCount > 0
    println("now calling measureField()")
    val measureResult = measureField(
        blocks = blocksList,
        maxWidth = getMaxWidth(),
        textMeasurer = getTextMeasurer(),
        textStyle = getDefaultTextStyle()
    )
    println("returned freom measureField()")

    val fieldReport = FieldReport(
        measureResult = measureResult,
        numBlocks = numBlocks,
        isEmpty = isEmpty,
        newLineCount = newLineCount,
        doesFieldHaveNL = doesFieldHaveNL
    )
    println("-----")
    println("-----")
    println("LEAVING getFieldReport()")
    println("-----")
    println("-----")
    return fieldReport
}


fun DocumentState.getNewLineCount(fieldId: String): Int {
    val findex = fields.indexOfFirst { it.id == fieldId }
    val field = fields[findex]
    return field.blocks.count { it is Block.DelimiterBlock && it.kind == Block.DelimiterBlock.Kind.NewLine }
}

fun DocumentState.getNewLineCount(field: Field): Int {
    return field.blocks.count { it is Block.DelimiterBlock && it.kind == Block.DelimiterBlock.Kind.NewLine }
}


fun DocumentState.handleOnValueChange(
    fieldId: String,
    blockId: String,
    newValue: TextFieldValue
) {
    val fid = fields.indexOfFirst {
        it.id == fieldId
    }
    val blocksList = fields[fid].blocks
    val bid = fields[fid].blocks.indexOfFirst {
        it is Block.TextBlock && it.id == blockId
    }

    require(fid > -1) { "field doesn't exist" }

    require(bid > -1) { "block doesn't exist" }
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
    val bid = fields[fid].blocks.indexOfFirst {
        it is Block.TextBlock && it.id == block.id
    }

    require(fid > -1) { "field doesn't exist" }

    require(bid > -1) { "block doesn't exist" }

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
