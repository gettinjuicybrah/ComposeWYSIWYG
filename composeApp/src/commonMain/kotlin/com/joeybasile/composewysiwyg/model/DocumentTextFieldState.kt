package com.joeybasile.composewysiwyg.model

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


/**
 * Represents the state of a single text field within the document.
 *
 * @property index The index of this text field in the document.
 * @property layoutCoordinates The layout coordinates for the text field (assigned via onGloballyPositioned).
 * @property textLayoutResult The layout result capturing text metrics (e.g., cursor offsets).
 * @property textFieldValue The current text and associated style state for the text field.
 */
@OptIn(ExperimentalUuidApi::class)

data class DocumentTextFieldState (
    val id: String = Uuid.random().toString(),
    var layoutCoordinates: LayoutCoordinates? = null, // updated via onGloballyPositioned
    var textLayoutResult: TextLayoutResult? = null, // holds metrics like cursor offsets
    var textFieldValue: TextFieldValue, // current text state of the BTF,
    val focusRequester: FocusRequester
)