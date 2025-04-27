package com.joeybasile.composewysiwyg.model.selection

data class SelectionState(
    val isActive: Boolean = false,
    val startField: Int? = null,
    val startOffset: Int? = null,
    val segments: List<SelectionSegment> = emptyList()
)
