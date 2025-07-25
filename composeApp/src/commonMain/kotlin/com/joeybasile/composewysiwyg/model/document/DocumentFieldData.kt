package com.joeybasile.composewysiwyg.model.document

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
/**
 * @return index of the last code-unit that is completely inside [maxWidthPx].
 *         If nothing fits, returns -1.
 */
fun TextLayoutResult.lastFittingIndex(maxWidthPx: Int): Int {
    var lo = 0
    var hi = layoutInput.text.length    // exclusive
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2

        // Caret AFTER the glyph at (mid-1)
        val rightEdge = getHorizontalPosition(mid, usePrimaryDirection = true)

        if (rightEdge <= maxWidthPx) {
            lo = mid            // (mid-1) still fits → search the right half
        } else {
            hi = mid - 1        // (mid-1) overflows → search the left half
        }
    }
    return lo - 1               // -1 if nothing fit at all
}
internal fun Block.TextBlock.measure(
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
): TextLayoutResult {
    val result = textMeasurer.measure(
        text = textFieldValue.annotatedString,
        style = textStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
        maxLines = 1,
        softWrap = false
    )
    println("did OF in textblock: ${result.didOverflowWidth}")
    val lastInBounds = result.lastFittingIndex(maxWidthPx)
    println("")
    println("last in bounds: $lastInBounds")
    println("")
    val firstOverflow = if(result.didOverflowWidth)lastInBounds + 1 else -1
    println("")
    println("first overflow: $firstOverflow")
    println("")
    val pixelWidth = result.size.width
    return result//Pair(maxMeasuredOffset, pixelWidth)
}

internal fun Block.ImageBlock.measure(
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
): Int {
    return width

}

internal fun Block.DelimiterBlock.measure(
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxWidthPx: Int
): Int {
    return width

}

data class LocalFieldForMutation(
    val id: String,
    val blocks: MutableList<Block>
)

data class LocalTextBlockForMutation(
    val id: String,
    val textFieldValue: TextFieldValue,
    var layoutCoordinates: LayoutCoordinates? = null,
    var textLayoutResult: TextLayoutResult? = null,
) {
    val length get() = textFieldValue.text.length
    val width get() = textFieldValue.text.length
}

/** Coordinates within a block list */
data class Pos(
    val blockIndexWithinList: Int,
    val offsetInBlock: Int        // 0‥length ; for non‑text blocks always 0/1
)
/*
length is for calculating 'locally global' position when considering a field's block list
(this is because textblock itself is also a list, really)

width is for the literal graphical width
 */
sealed class Block {
    abstract val id: String
    abstract val length: Int
    abstract val width: Int
    abstract var layoutCoordinates: LayoutCoordinates?

    data class TextBlock(
        override val id: String,
        val textFieldValue: TextFieldValue,
        override var layoutCoordinates: LayoutCoordinates? = null,
        var textLayoutResult: TextLayoutResult? = null,
        val focusRequester: FocusRequester
    ) : Block() {
        override val length get() = textFieldValue.text.length
        override val width get() = textFieldValue.text.length
    }

    data class ImageBlock(
        override val id: String,
        override var layoutCoordinates: LayoutCoordinates? = null,
        override val width: Int,      // px at native scale – set at insert / resize
        override val length: Int = 1,
        val focusRequester: FocusRequester,
        val payloadId: String
    ) : Block()

    data class DelimiterBlock(
        override val id: String,
        override var layoutCoordinates: LayoutCoordinates? = null,
        val kind: Kind = Kind.NewLine,
        override val length: Int = 1,
        override val width: Int = 0
    ) : Block() {
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
    }
}

@OptIn(ExperimentalUuidApi::class)
fun emptyTextBlock() = Block.TextBlock(
    id = Uuid.random().toString(),
    textFieldValue = TextFieldValue(annotatedString = AnnotatedString(""), selection = TextRange.Zero),
    focusRequester = FocusRequester()
)
