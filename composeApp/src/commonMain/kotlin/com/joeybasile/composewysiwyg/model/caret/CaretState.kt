package com.joeybasile.composewysiwyg.model.caret

import androidx.compose.ui.geometry.Offset


//CaretState to track global cursor
data class CaretState(
    val fieldIndex: Int, // Index of the active (BasicText)Field
    val offset: Int, // (Character) offset within the field
    val globalPosition: Offset, // Global x, y relative to Box coords.
    val height: Float, // Default height
    val isVisible: Boolean = true // For blinking (optional)
)