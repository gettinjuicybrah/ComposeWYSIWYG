package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.ui.geometry.Offset

data class SelectionState(
    val segments: List<SelectionSegment> = emptyList(),
    val anchor: SelectionCaretState? = null,
    val focus: SelectionCaretState? = null,
){
    val isActive: Boolean // Derived state. ensures any mutation to anchor or focus can be reflected in the getter.
        get() = anchor != null && focus != null && anchor != focus
}
data class SelectionCaretState(
    val fieldIndex: Int, // Index of the active (BasicText)Field
    val offset: Int, // (Character) offset within the field
    val globalPosition: Offset // Global x, y relative to Box coords.
)
