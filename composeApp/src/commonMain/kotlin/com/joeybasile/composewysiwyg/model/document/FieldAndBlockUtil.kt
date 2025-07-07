package com.joeybasile.composewysiwyg.model.document
import com.joeybasile.composewysiwyg.model.DocumentState

fun DocumentState.getFieldIndex(fieldId: String):Int{
    return fields.indexOfFirst{it.id == fieldId}
}
fun DocumentState.getBlockIndex(fieldId: String, blockId: String):Int{
    val fieldIndex = getFieldIndex(fieldId)
    return fields[fieldIndex].blocks.indexOfFirst{it.id == blockId}
}
/**
 * Returns the zero-based “global” character offset of the given `blockId`
 * inside the supplied `field`, or `null` when
 *  • the block is not present in this field, or
 *  • the supplied `offsetInBlock` is outside the block’s bounds.
 *
 * Complexity: O(n) where n = number of blocks in the field.
 */
fun DocumentState.getLocGlobalFieldOffsetForABlock(
    field: Field,
    blockId: String,
    offsetInBlock: Int
): Int? {
    var runningTotal = 0

    for (block in field.blocks) {
        if (block.id == blockId) {
            // Offset must be inside the block’s own range
            return if (offsetInBlock in 0..block.length) {
                runningTotal + offsetInBlock
            } else {
                null        // caller asked for an invalid in-block offset
            }
        }

        runningTotal += block.length
    }

    // No block with that ID found in this field
    return null
}
fun DocumentState.getLocGlobalFieldOffsetForABlock(
    fieldId: String,
    blockId: String,
    offsetInBlock: Int
): Int? {
    var runningTotal = 0
    val fInd = getFieldIndex(fieldId)
    for (block in fields[fInd].blocks) {
        if (block.id == blockId) {
            // Offset must be inside the block’s own range
            return if (offsetInBlock in 0..block.length) {
                runningTotal + offsetInBlock
            } else {
                null        // caller asked for an invalid in-block offset
            }
        }

        runningTotal += block.length
    }

    // No block with that ID found in this field
    return null
}

