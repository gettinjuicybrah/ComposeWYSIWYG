package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.ui.geometry.Offset

data class SelectionState(
    val isActive: Boolean = false, //isDragging
    val startField: Int? = null,
    val startOffset: Int? = null,
    val segments: List<SelectionSegment> = emptyList(),
    val headCaret: HeadSelectionCaretState? = null,
    val tailCaret: TailSelectionCaretState? = null,
)
data class HeadSelectionCaretState(
    val fieldIndex: Int, // Index of the active (BasicText)Field
    val offset: Int, // (Character) offset within the field
    val globalPosition: Offset, // Global x, y relative to Box coords.
    val isShiftPlusArrowOriginCaret: Boolean //Whether or not shift + arrow_dir is relative to this or not.
)
data class TailSelectionCaretState(
    val fieldIndex: Int, // Index of the active (BasicText)Field
    val offset: Int, // (Character) offset within the field
    val globalPosition: Offset, // Global x, y relative to Box coords.
    val isShiftPlusArrowOriginCaret: Boolean //Whether or not shift + arrow_dir is relative to this or not.
)