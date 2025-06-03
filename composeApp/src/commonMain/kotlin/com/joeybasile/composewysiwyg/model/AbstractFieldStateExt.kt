package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.input.TextFieldValue
import com.joeybasile.composewysiwyg.model.caret.updateGlobalCaretPosition
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun DocumentState.updateFocusedBlock(blockId: String){
    focusedBlock.value = blockId
}

/**
 * Updates the layout coordinates for a specific block within a field.
 * This function is called via state hoisting from the composables.
 */
fun DocumentState.updateBlockCoords(fieldId: String, blockId: String, newCoordinates: LayoutCoordinates) {
    // Find the field by its ID
    val field = fields.find { it.id == fieldId }
    if (field == null) {
        // Log.w("DocumentState", "Field not found: $fieldId")
        return
    }

    // Find the block by its ID within the found field
    val block = field.blocks.find { it.id == blockId }
    if (block == null) {
        // Log.w("DocumentState", "Block not found: $blockId in field $fieldId")
        return
    }

    // Update the layoutCoordinates MutableState of the block.
    // This will trigger recomposition for any composables observing this state.
    block.layoutCoordinates.value = newCoordinates
    // For debugging:
    // println("Updated coordinates for Field $fieldId, Block $blockId: ${newCoordinates.size}, ${newCoordinates.positionInRoot()}")

    if(globalCaret.value.blockId == blockId) updateGlobalCaretPosition()

}
// Helper function to get a Field by its ID
fun DocumentState.getFieldById(fieldId: String): Field? {
    return fields.find { it.id == fieldId }
}

// Helper function to get a Block by its ID within a specific Field
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
fun DocumentState.getTextBlockById(field: Field, blockId: String): TextBlock? {
    // Find the block by its ID first
    val foundBlock = field.blocks.find { it.id == blockId }

    // Then, safely cast it to TextBlock.
    // If foundBlock is null, or if it's not an instance of TextBlock,
    // 'as?' will return null. Otherwise, it returns foundBlock cast to TextBlock.
    return foundBlock as? TextBlock
}

/**
 * Converts the current UI state (mutable `Field`s and `Block`s)
 * back into an immutable [DocumentModel].
 */
fun DocumentState.toDocumentModel(): DocumentModel = DocumentModel(
    fields = fields.map { it.toFieldModel() }.toList()
)

/**
 * Initializes the document state for a new, empty document.
 * This clears existing fields and adds a default new field with a text block.
 */
@OptIn(ExperimentalUuidApi::class)
fun DocumentState.initializeNewDoc() {
    fields.clear()
    // Add a new empty Field (UI state version) with a default TextBlock
    fields.add(
        Field(
            id = Uuid.random().toString(),
            blocks = mutableStateListOf(
                TextBlock(value = TextFieldValue(), focusRequester = FocusRequester())
            )
        )
    )
}

// --- Example methods for manipulating the UI state ---
fun DocumentState.addField(index: Int, field: Field) {
    fields.add(index, field)
}

fun DocumentState.removeField(field: Field) {
    fields.remove(field)
}

fun DocumentState.updateTextBlockValue(fieldId: String, blockId: String, newValue: TextFieldValue) {
    // Find the specific TextBlock and update its `value` property
    val field = fields.find { it.id == fieldId }
    val textBlock = field?.blocks?.find { it.id == blockId } as? TextBlock
    textBlock?.value = newValue // Direct update to the `var` property is observed
}