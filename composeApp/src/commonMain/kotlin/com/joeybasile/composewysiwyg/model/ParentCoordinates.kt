package com.joeybasile.composewysiwyg.model

import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Represents the coordinates for various parent containers of the document.
 *
 * This data class holds layout coordinate information for the overall document, the Box container,
 * and the LazyColumn. These coordinates might be used for aligning overlays or managing complex
 * positioning logic in the WYSIWYG editor.
 *
 * @property document Coordinates of the document container.
 * @property box Coordinates of the Box container.
 * @property lazyColumn Coordinates of the LazyColumn container.
 */
data class ParentCoordinates(
    val document: LayoutCoordinates? = null,
    val box: LayoutCoordinates? = null, //Currently, literally only this is used for computing global coordinates. This is for caret global relative offset,
    val lazyColumn: LayoutCoordinates? = null
)