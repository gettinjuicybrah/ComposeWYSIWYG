package com.joeybasile.composewysiwyg.model.selection

import androidx.compose.ui.geometry.Rect


data class SelectionSegment(
    val fieldIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val rect: Rect
)