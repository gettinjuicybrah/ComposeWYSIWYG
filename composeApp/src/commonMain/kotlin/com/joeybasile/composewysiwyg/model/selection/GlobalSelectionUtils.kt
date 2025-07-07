package com.joeybasile.composewysiwyg.model.selection

import com.joeybasile.composewysiwyg.model.document.*
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.document.getBlockIndex
import com.joeybasile.composewysiwyg.model.document.getFieldIndex

/**
 * Returns a pair of (startCaret, endCaret) in document order.
 * Returns null if selection is not active or carets are missing.
 */
fun DocumentState.getOrderedGlobalSelectionCarets(): Pair<GlobalSelectionCaretState, GlobalSelectionCaretState>? {
    val (anchorIndices, focusIndices) = getGlobalSelectionCaretIndices() ?: return null
    val anchor = globalSelectionState.anchor ?: return null
    val focus = globalSelectionState.focus ?: return null

    return if (anchorIndices.fieldIndex < focusIndices.fieldIndex ||
        (anchorIndices.fieldIndex == focusIndices.fieldIndex && anchor.offsetInBlock <= focus.offsetInBlock)
    ) {
        anchor to focus
    } else {
        focus to anchor
    }
}

fun DocumentState.getOrderedGlobalSelectionCarets(
    anchor: GlobalSelectionCaretState,
    focus: GlobalSelectionCaretState
): Pair<GlobalSelectionCaretState, GlobalSelectionCaretState>? {
    val (anchorIndices, focusIndices) = getGlobalSelectionCaretIndices() ?: return null


    return if (anchorIndices.fieldIndex < focusIndices.fieldIndex ||
        (anchorIndices.fieldIndex == focusIndices.fieldIndex && anchor.offsetInBlock <= focus.offsetInBlock)
    ) {
        anchor to focus
    } else {
        focus to anchor
    }
}

fun DocumentState.getOrderedGlobalSelectionCaretsWithIndices(
    anchor: GlobalSelectionCaretState,
    focus: GlobalSelectionCaretState
): Pair<GlobalCaretIndicesWithOffsetModel, GlobalCaretIndicesWithOffsetModel>? {
    val (anchorIndices, focusIndices) = getGlobalSelectionCaretIndices() ?: return null


    return if (anchorIndices.fieldIndex < focusIndices.fieldIndex ||
        (anchorIndices.fieldIndex == focusIndices.fieldIndex && anchor.offsetInBlock <= focus.offsetInBlock)
    ) {
        anchorIndices to focusIndices
    } else {
        focusIndices to anchorIndices
    }
}

data class GlobalCaretIndicesWithOffsetModel(
    val fieldIndex: Int,
    val blockIndex: Int,
    val caret: GlobalSelectionCaretState
)

fun DocumentState.getGlobalSelectionCaretIndices(): Pair<GlobalCaretIndicesWithOffsetModel, GlobalCaretIndicesWithOffsetModel>? {
    val anchor = globalSelectionState.anchor ?: return null
    val focus = globalSelectionState.focus ?: return null
    val anchorFieldIndx = getFieldIndex(anchor.fieldId)
    val anchorBlockIndx = getBlockIndex(anchor.fieldId, anchor.blockId)
    val anchorIndices = GlobalCaretIndicesWithOffsetModel(
        fieldIndex = anchorFieldIndx,
        blockIndex = anchorBlockIndx,
        anchor
    )
    val focusFieldIndx = getFieldIndex(focus.fieldId)
    val focusBlockIndx = getBlockIndex(focus.fieldId, focus.blockId)
    val focusIndices = GlobalCaretIndicesWithOffsetModel(
        fieldIndex = focusFieldIndx,
        blockIndex = focusBlockIndx,
        focus
    )
    return anchorIndices to focusIndices
}

fun DocumentState.getGlobalSelectionCaretIndices(
    anchor: GlobalSelectionCaretState,
    focus: GlobalSelectionCaretState
): Pair<GlobalCaretIndicesWithOffsetModel, GlobalCaretIndicesWithOffsetModel>? {

    val anchorFieldIndx = getFieldIndex(anchor.fieldId)
    val anchorBlockIndx = getBlockIndex(anchor.fieldId, anchor.blockId)
    val anchorIndices = GlobalCaretIndicesWithOffsetModel(
        fieldIndex = anchorFieldIndx,
        blockIndex = anchorBlockIndx,
        anchor
    )
    val focusFieldIndx = getFieldIndex(focus.fieldId)
    val focusBlockIndx = getBlockIndex(focus.fieldId, focus.blockId)
    val focusIndices = GlobalCaretIndicesWithOffsetModel(
        fieldIndex = focusFieldIndx,
        blockIndex = focusBlockIndx,
        focus
    )
    return anchorIndices to focusIndices
}