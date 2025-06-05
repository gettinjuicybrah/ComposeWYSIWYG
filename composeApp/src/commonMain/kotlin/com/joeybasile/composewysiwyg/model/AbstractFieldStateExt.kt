package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.caret.CaretState
import com.joeybasile.composewysiwyg.model.caret.GlobalCaret
import com.joeybasile.composewysiwyg.model.caret.updateGlobalCaretFieldAndBlockId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
