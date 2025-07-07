package com.joeybasile.composewysiwyg.model.caret

import androidx.compose.ui.geometry.Offset


//global caret for abstract fields
data class GlobalCaret(
    val fieldId: String, //uuid for field association
    val blockId: String, //uuid for block association within the field
    val offsetInBlock: Int, // 0‥block.length
    val globalPosition: Offset,
    val height: Float, //dependent on the current caret height for the field. Determined by the currently calculated char style for the caret.
    val isVisible: Boolean = true // for blinking timer
)