package com.joeybasile.composewysiwyg.model.linewrap

import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.document.Block
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.caret.syncCaret
import com.joeybasile.composewysiwyg.model.document.Field
import com.joeybasile.composewysiwyg.model.document.Pos
import com.joeybasile.composewysiwyg.model.getFieldReport
import com.joeybasile.composewysiwyg.model.document.normalise
import com.joeybasile.composewysiwyg.model.document.normalizeBlockList
import com.joeybasile.composewysiwyg.model.splitFieldAt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
Idea:
We will consider all blocks within a fields.
We will determine if the field
    1.) OF
    2.) Contains more than one NL

IF OF: certainly must split, potentially iteratively.

ELSE: return.

IF more than one NL: requirement to split at every NL except for the last. There's definitely something that can be done for parallelization for efficiency.
ELSE: NOP itself.

IF OF && m1NL: determine if OF position came before the NL.

 */

/* With the naming of the params, I'm thinking that
    @param proposedField is a field we must process. It will eventually be inserted, perhaps after being mutated.
        proposedField.id is a dummy id and doesn't matter.
    @param fieldIdInListToReplaceWith is the fieldId of the field that will be replace with the proposedField ONCE we do our due dilligence on it.

 */
@OptIn(ExperimentalUuidApi::class)
fun DocumentState.genericPullDown(
    initialProposedField: Field,
    initialFieldIdInListToReplaceWith: String,
    //initialGlobalCaretState: GlobalCaret,
    purposeTFVUpdate: Boolean = true
) {

    var loopCount = 0

    var proposedField = initialProposedField
    var fieldIdInListToReplaceWith = initialFieldIdInListToReplaceWith

    var hasMoreToPullDown: Boolean = true
    while (hasMoreToPullDown) {
        println("LOOP ENTERED. hasMoreToPullDown == TRUE")
        println("********************************************************")
        println("LOOP COUNT: $loopCount")
        println("********************************************************")
        val fIndex = fields.indexOfFirst { it.id == fieldIdInListToReplaceWith }

        //Firstly, we must investigate the proposedField. Does it cause OF, does it have more than one NL, etc.

        val fieldReport = getFieldReport(proposedField)
        println("RECEIVED FIELD REPORT: $fieldReport")
        //The field requires pulldown if so.
        val requiresPullDown =
            fieldReport.newLineCount > 1 || fieldReport.measureResult.overflow
        println("-------------------------------------- fieldReport.newLineCount: ${fieldReport.newLineCount}")
        println("-------------------------------------- fieldReport.measureResult.overflow: ${fieldReport.measureResult.overflow}")
        if (requiresPullDown) {
            println("IF 1 requiredpulldown")
            println("IF 1 requiredpulldown")
            println("IF 1 requiredpulldown")
            println("   LINE 1003------------------ overflowPos: ${fieldReport.measureResult.overflowPos}")
            println("   LINE 1003------------------ hardbreakPos: ${fieldReport.measureResult.hardBreakPos}")
            println("   LINE 1003------------------ measureResult.did OF:: ${fieldReport.measureResult.overflow}")

            var doesNLComeBeforeOF = false
            if (fieldReport.measureResult.hardBreakPos != null) {
                doesNLComeBeforeOF = fieldReport.measureResult.overflowPos!!.blockIndexWithinList > fieldReport.measureResult.hardBreakPos!!.blockIndexWithinList
            }
            //fieldReport.measureResult.overflowPos!!.offsetInBlock > fieldReport.measureResult.hardBreakPos!!.offsetInBlock
            val splitPos: Pos
            if (doesNLComeBeforeOF) {
                println("       IF 1A does nl come before of. split at hardbreak pos.")
                //smartcasted to Pos because certainly not null at this point.
                splitPos = fieldReport.measureResult.hardBreakPos!!
            } else {
                println("       ELSE IF 1A. split at overflowpos")
                //smartcasted to Pos because certainly not null at this point.
                splitPos = fieldReport.measureResult.overflowPos!!
            }

            println("splitPos: $splitPos")
            val (fittingBlocks, overflowBlocks) = splitFieldAt(
                field = proposedField,
                pos = splitPos
            )!!

            println("blocks before clear: ${fields[fIndex].blocks}")
            println("the fitting blocks: ${fittingBlocks}")
            println("the OVERFLOWING blocks: ${overflowBlocks}")
            println("clearing all blocks at specified index.")
            fields[fIndex].blocks.clear()
            //normalizeBlockList(fields[fIndex].blocks)
            //syncCaret()
            println("blocks AFTER clear: ${fields[fIndex].blocks}")
            fields[fIndex].blocks.addAll(fittingBlocks)
            //normalizeBlockList(fields[fIndex].blocks)
            //syncCaret()
            println("blocks AFTER addAll: ${fields[fIndex].blocks}")

            //if we are the last line, then this means we just create a below line and prepend to it. If not, we'll just prepend to the existing below line.
            val mustCreateBlankFieldBelow = fields.size - 1 == fIndex

            if (mustCreateBlankFieldBelow) {
                println("   IF 1B")
                fields.add(
                    fIndex + 1,
                    Field(
                        id = Uuid.random().toString(),
                        blocks = overflowBlocks.toMutableStateList()
                    )//.normalise()
                )
                //syncCaret()
            } else {
                println("   ELSE IF 1B")
                fields[fIndex + 1].blocks.addAll(0, overflowBlocks)
                fields[fIndex + 1] = fields[fIndex + 1]//.normalise()
                //syncCaret()
            }
            //Because there was certainly a field below, we will always iterate on it.
            proposedField = fields[fIndex + 1]
            fieldIdInListToReplaceWith = proposedField.id


            loopCount++
            println("STILL REQUIRES PULLDOWN. CONTINUING LOOP. $loopCount")
            println("STILL REQUIRES PULLDOWN. CONTINUING LOOP. $loopCount")
            println("STILL REQUIRES PULLDOWN. CONTINUING LOOP. $loopCount")
            println("STILL REQUIRES PULLDOWN. CONTINUING LOOP. $loopCount")



        } else {
            println("DOESN'T REQUIRE PULLDOWN. BREAKING OUT OF LOOP.")
            println("DOESN'T REQUIRE PULLDOWN. BREAKING OUT OF LOOP.")
            println("DOESN'T REQUIRE PULLDOWN. BREAKING OUT OF LOOP.")
            println("DOESN'T REQUIRE PULLDOWN. BREAKING OUT OF LOOP.")
            println("DOESN'T REQUIRE PULLDOWN. BREAKING OUT OF LOOP.")
            fields[fIndex] = fields[fIndex].copy(
                blocks = proposedField.blocks
            )
            //syncCaret()
            /*
            //FOR PURPOSE OF TFV UPDATE.
            if(loopCount == 0 && purposeTFVUpdate){
                val bid = proposedField.blocks.indexOfFirst { it.id == globalCaret.value.blockId }
                if(bid > -1 && proposedField.blocks[bid] is Block.TextBlock) {
                    val textBlock = proposedField.blocks[bid] as Block.TextBlock
                    globalCaret.value = globalCaret.value.copy(
                        offsetInBlock = textBlock.textFieldValue.selection.start
                    )
                    // immediately recompute the global caret position on screen
                    onGlobalCaretMoved()
                }
            }*/
            println("BREAKING OUT OF LOOP.")
            break
            hasMoreToPullDown = false
        }
    }

}


/**
 * Returns the total number of logical “characters” (length units)
 * contained in all blocks of this Field.
 *
 * Complexity: O(n) where n = number of blocks.
 */
fun Field.totalLength(): Int = blocks.sumOf { it.length }
@OptIn(ExperimentalUuidApi::class)
fun DocumentState.splitTextBlock(
    block: Block.TextBlock,
    splitAt: Int
): Pair<Block.TextBlock, Block.TextBlock> {
    println("((((((( entered splitTextBlock()")
    val origTFV = block.textFieldValue
    val leftString = origTFV.annotatedString.subSequence(0, splitAt)
    val rightString =
        origTFV.annotatedString.subSequence(splitAt, origTFV.annotatedString.length)

    val leftTFV = TextFieldValue(
        annotatedString = leftString,
        selection = TextRange(leftString.length)
    )

    val rightTFV = TextFieldValue(
        annotatedString = rightString,
        selection = TextRange(rightString.length)
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
    println("leftString: $leftString")
    println("leftString LENGTH: ${leftString.length}")
    println("rightString: $rightString")
    println("rightString LENGTH: ${rightString.length}")
    println(")))))) leaving splitTextBlock(). leftBlock: $leftBlock, rightBlock: $rightBlock")
    return Pair(leftBlock, rightBlock)

}