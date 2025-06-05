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

//global caret for abstract fields
data class GlobalCaret(
    val fieldId: String, //uuid for field association
    val blockId: String, //uuid for block association within the field
    val offsetInBlock: Int, // 0‥block.length
    val globalPosition: Offset,
    val height: Float, //dependent on the current caret height for the field. Determined by the currently calculated char style for the caret.
    val isVisible: Boolean = true // for blinking timer
)