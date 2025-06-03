package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.external.kotlinx.collections.immutable.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
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
    val textFieldValue: TextFieldValue, // current text state of the BTF,
    val focusRequester: FocusRequester,
    val hasNewLineAtEnd: Boolean = false, // true if \n at the end of a line.
    val textMeasurer: TextMeasurer? = null,
    val textStyle: TextStyle? = null
)
// --- Immutable Model (Your Source of Truth) ---
// These classes represent the clean, immutable data.
// @Immutable is suitable here because these are truly immutable data classes.

@Immutable
data class DocumentModel(
    val fields: List<FieldModel> = emptyList()
)

data class FieldModel(
    val id: String,
    val blocks: List<BlockModel>
)

sealed interface BlockModel {
    val id: String
    val length: Int
}

@OptIn(ExperimentalUuidApi::class)
data class TextBlockModel(
    override val id: String = Uuid.random().toString(),
    val value: TextFieldValue,
    val focusRequester: FocusRequester
) : BlockModel {
    override val length get() = value.text.length
}

@OptIn(ExperimentalUuidApi::class)
data class ImageBlockModel(
    override val id: String = Uuid.random().toString(),
    val bitmap: ImageBitmap,
    val focusRequester: FocusRequester
) : BlockModel {
    override val length get() = 1
}

@OptIn(ExperimentalUuidApi::class)
data class DelimiterBlockModel(
    override val id: String = Uuid.random().toString(),
    val kind: Delimiter
) : BlockModel {
    override val length get() = 1
}

// --------------- Delimiter “enum” ----------------
sealed interface Delimiter
object NewLine : Delimiter
object Tab : Delimiter


// --- Mutable UI State (For Compose Observation) ---
// These data classes hold the state that Compose will directly observe and react to.
// Note: @Immutable is generally NOT used on these if they contain mutable collections (like SnapshotStateList)
// or mutable properties (like `var value`).

data class Field @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    // Blocks within a field are mutable for UI observation.
    val blocks: SnapshotStateList<Block> = mutableStateListOf()
) {
    // Constructor to convert from an immutable FieldModel to a mutable UI Field
    constructor(fieldModel: FieldModel) : this(
        id = fieldModel.id,
        blocks = fieldModel.blocks.map { Block.fromModel(it) }.toMutableStateList()
    )

    // Converts the mutable UI Field back to an immutable FieldModel
    fun toFieldModel(): FieldModel = FieldModel(
        id = id,
        blocks = blocks.map { it.toModel() }.toList() // Use .toList() for standard Kotlin List
        // or .toPersistentList() if your model uses PersistentList
    )
}

// Block as a sealed interface for UI state, with concrete data classes
sealed interface Block {
    val id: String
    val length: Int
    val layoutCoordinates: MutableState<LayoutCoordinates?>
    fun toModel(): BlockModel // Function to convert back to the immutable model

    companion object {
        // Helper to convert any BlockModel to its corresponding mutable UI Block
        fun fromModel(model: BlockModel): Block = when (model) {
            is TextBlockModel -> TextBlock(model)
            is ImageBlockModel -> ImageBlock(model)
            is DelimiterBlockModel -> DelimiterBlock(model)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
data class TextBlock(
    override val id: String = Uuid.random().toString(),
    // Make `value` a `var` so changes to TextFieldValue are observed.
    var value: TextFieldValue,
    val focusRequester: FocusRequester,
    override val layoutCoordinates: MutableState<LayoutCoordinates?> = mutableStateOf(null),
    val textLayoutResult: TextLayoutResult? = null
) : Block {
    override val length get() = value.text.length

    // Constructor to convert from immutable TextBlockModel to mutable UI TextBlock
    constructor(model: TextBlockModel) : this(
        id = model.id,
        value = model.value,
        focusRequester = model.focusRequester
    )

    override fun toModel(): BlockModel = TextBlockModel(
        id = id,
        value = value,
        focusRequester = focusRequester
        // layoutCoordinates is a UI concern, typically not part of the persistent model
    )
}

@OptIn(ExperimentalUuidApi::class)
data class ImageBlock(
    override val id: String = Uuid.random().toString(),
    // Make `bitmap` a `var` if it can change and you want to observe it.
    var bitmap: ImageBitmap,
    val focusRequester: FocusRequester,
    override val layoutCoordinates: MutableState<LayoutCoordinates?> = mutableStateOf(null)
) : Block {
    override val length get() = 1

    // Constructor to convert from immutable ImageBlockModel to mutable UI ImageBlock
    constructor(model: ImageBlockModel) : this(
        id = model.id,
        bitmap = model.bitmap,
        focusRequester = model.focusRequester
    )

    override fun toModel(): BlockModel = ImageBlockModel(
        id = id,
        bitmap = bitmap,
        focusRequester = focusRequester
        // layoutCoordinates is a UI concern, typically not part of the persistent model
    )
}

@OptIn(ExperimentalUuidApi::class)
data class DelimiterBlock(
    override val id: String = Uuid.random().toString(),
    // Make `kind` a `var` if it can change and you want to observe it.
    var kind: Delimiter,
    override val layoutCoordinates: MutableState<LayoutCoordinates?> = mutableStateOf(null)
) : Block {
    override val length get() = 1

    // Constructor to convert from immutable DelimiterBlockModel to mutable UI DelimiterBlock
    constructor(model: DelimiterBlockModel) : this(
        id = model.id,
        kind = model.kind
    )

    override fun toModel(): BlockModel = DelimiterBlockModel(
        id = id,
        kind = kind
    )
}