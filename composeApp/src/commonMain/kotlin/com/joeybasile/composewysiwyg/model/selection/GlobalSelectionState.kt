package com.joeybasile.composewysiwyg.model.selection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/*
TODO:
Add selection segment heights which are just based on the tallest segment within a current line - all segments in a line will have the
same height.
 */
data class GlobalSelectionState(
    val segments: List<GlobalSelectionSegment> = emptyList(),
    val anchor: GlobalSelectionCaretState? = null,
    val focus: GlobalSelectionCaretState? = null,
){
    val isActive: Boolean // Derived state. ensures any mutation to anchor or focus can be reflected in the getter.
        get() = anchor != null && focus != null && anchor != focus

}

data class GlobalSelectionCaretState(
    val fieldId: String, // field id associated with block that currently has focus (and really only as it related to text. An image block in the future could be focused, but it wouldn't have to do with the caret, but instead altering the image somehow, like applying an entity link, resizing it, or deleting it.)
    val blockId: String,
    val offsetInBlock: Int, // (Character) offset within the text block
    val globalPosition: Offset // Global x, y relative to "root coords" defined in RootReferenceCoordinates.kt
)

data class GlobalSelectionSegment(
    val fieldId: String,
    val height: Float, //just the tallest 'thing' in the field - an image height or tallest char (based on size and font)
    val startBlockIdAndOffsetInBlock: Pair<String, Int>,
    val endBlockIdAndOffsetInBlock: Pair<String, Int>,
    val rect: Rect
)
