package com.joeybasile.composewysiwyg.model.caret

import androidx.compose.ui.geometry.Offset


//CaretState to track global cursor
data class CaretState(
    var fieldIndex: Int, // Index of the active (BasicText)Field
    var offset: Int, // (Character) offset within the field
    var globalPosition: Offset, // Global x, y relative to Box coords.
    var height: Float, // Default height
    var isVisible: Boolean = true // For blinking (optional)
)