package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun Block.measure(
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
): Int = when (this) {
    is Block.TextBlock -> textMeasurer.measure(
        text = textFieldValue.annotatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 1,
        softWrap = false
    ).size.width
    is Block.ImageBlock -> intrinsicWidth              // already px
    is Block.DelimiterBlock -> 0                       // NL and TAB have no own width (tab handled later)
}
/** Coordinates within a block list */
data class Pos(
    val blockIndexWithinList: Int,
    val offsetInBlock: Int        // 0‥length ; for non‑text blocks always 0/1
)
sealed class Block {
    abstract val id: String
    abstract val length: Int
    data class TextBlock(
        override val id: String,
        //override val length: Int = ,
        val textFieldValue: TextFieldValue,
        var layoutCoordinates: LayoutCoordinates? = null,
        var textLayoutResult: TextLayoutResult? = null,
        val focusRequester: FocusRequester
    ) : Block(){
        override val length get() = textFieldValue.text.length
    }

    data class ImageBlock(
        override val id: String,
        val intrinsicWidth: Int,      // px at native scale – set at insert / resize
        override val length: Int = 1,
        val focusRequester: FocusRequester
    ) : Block()

    data class DelimiterBlock(
        override val id: String,
        val kind: Kind = Kind.NewLine,
        override val length: Int = 1
    ) : Block(){
        enum class Kind { NewLine, Tab /* add more later (Space, etc.) */ }
    }
}

data class Field(
    val id: String,
    val blocks: SnapshotStateList<Block>
)
/*
Safe concatenation of two TextBlocks
 */
fun concatTextBlocks(a: Block.TextBlock, b: Block.TextBlock): Block.TextBlock =
    a.copy(
        textFieldValue = a.textFieldValue.copy(
            annotatedString = a.textFieldValue.annotatedString + b.textFieldValue.annotatedString
        )
    )

@OptIn(ExperimentalUuidApi::class)
fun Field.normalise(): Field {
    val tmp = blocks.toMutableList()

    // merge adjacent TextBlocks (P‑3)
    var i = 0
    while (i < tmp.size - 1) {
        if (tmp[i] is Block.TextBlock && tmp[i + 1] is Block.TextBlock) {
            tmp[i] = concatTextBlocks(tmp[i] as Block.TextBlock, tmp[i + 1] as Block.TextBlock)
            tmp.removeAt(i + 1)
        } else i++
    }


    // ensure ImageBlock is followed by TextBlock (P‑2)
    i = 0
    while (i < tmp.lastIndex) {
        if (tmp[i] is Block.ImageBlock && tmp[i + 1] !is Block.TextBlock) {
            tmp.add(i + 1, emptyTextBlock())
        }
        i++
    }

    return copy(blocks = mutableStateListOf(*tmp.toTypedArray()))
}
/**
 * Applies structural invariants directly to a mutable list of blocks.
 * This function MUTATES the list in place.
 *  - P-3: Merges adjacent TextBlocks.
 *  - P-2: Ensures any ImageBlock is followed by a TextBlock.
 *  - P-4: Ensures a completely empty list becomes a canonical blank line.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun normalizeBlockList(blocks: MutableList<Block>) {
    // 1. Merge adjacent TextBlocks (P-3)
    var i = 0
    while (i < blocks.size - 1) {
        val current = blocks[i]
        val next = blocks[i + 1]
        if (current is Block.TextBlock && next is Block.TextBlock) {
            blocks[i] = concatTextBlocks(current, next)
            blocks.removeAt(i + 1)
            // Do not increment i, allowing the newly merged block to be
            // checked against the next one in the following iteration.
        } else {
            i++
        }
    }

    // 2. Ensure ImageBlock is followed by TextBlock (P-2)
    i = 0
    while (i < blocks.lastIndex) {
        if (blocks[i] is Block.ImageBlock && blocks[i + 1] !is Block.TextBlock) {
            blocks.add(i + 1, emptyTextBlock())
        }
        i++
    }}
@OptIn(ExperimentalUuidApi::class)
fun emptyTextBlock() = Block.TextBlock(
    id = Uuid.random().toString(),
    textFieldValue = TextFieldValue(""),
    focusRequester = FocusRequester()
)

// --- Immutable Model (Your Source of Truth) ---
// These classes represent the clean, immutable data.
// @Immutable is suitable here because these are truly immutable data classes.
/*
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
    var layoutCoordinates: LayoutCoordinates?
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
    override var layoutCoordinates: LayoutCoordinates? = null,
    var textLayoutResult: TextLayoutResult? = null
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
    override var layoutCoordinates: LayoutCoordinates? = null,
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
    override var layoutCoordinates: LayoutCoordinates? = null,
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

 */