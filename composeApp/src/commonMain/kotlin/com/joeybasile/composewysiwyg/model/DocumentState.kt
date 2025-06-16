package com.joeybasile.composewysiwyg.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.joeybasile.composewysiwyg.model.style.CurrentCharStyle
import com.joeybasile.composewysiwyg.model.style.ToolbarState
import com.joeybasile.composewysiwyg.model.caret.GlobalCaret
import com.joeybasile.composewysiwyg.model.document.Block
import com.joeybasile.composewysiwyg.model.document.Field
import com.joeybasile.composewysiwyg.model.image.ImagePayload
import com.joeybasile.composewysiwyg.model.selection.GlobalSelectionState
import com.joeybasile.composewysiwyg.ui.rememberLineMeasurer
import kotlinx.coroutines.CoroutineScope
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Creates and remembers the [DocumentState] instance.
 *
 * This composable ensures that the state is retained across recompositions and initializes
 * a [CoroutineScope] for any asynchronous operations that may be necessary.
 *
 * @return A remembered instance of [DocumentState].
 */
@Composable
fun rememberDocumentState(): DocumentState {
    // Use remember to persist the state instance across recompositions.
    val scope = rememberCoroutineScope()
    val measurer = rememberLineMeasurer()
    val state = remember {
        DocumentState(
            scope = scope
        )
    }
    // Initialize the document state for a new document. Later, this can be
    // replaced with conditional logic if you decide to support existing documents.
    LaunchedEffect(Unit) {
        // state.initializeNewDocument()
        state.textMeasurer = measurer
        state.initializeNewDoc()
    }
    return state
}

/**
 * The main state holder for the document in the WYSIWYG editor.
 *
 * [DocumentState] holds a list of text field states as well as coordinate data for various
 * parent components. It provides methods to update individual text fields (text, layout metrics,
 * and coordinates) so that these changes are handled in a single source of truth.
 *
 * @property scope A [CoroutineScope] that can be used for asynchronous operations if needed.
 */
class DocumentState(val scope: CoroutineScope) {
    val imageStore = mutableMapOf<String, ImagePayload>()
    /** Snapshot lists Compose will observe directly */
    // The `fields` property now holds the UI-mutable `Field` data class instances.
    // Changes to this list (add, remove, reorder) and changes *within* the `Field` objects
    // (due to Field.blocks being SnapshotStateList) will be observed by Compose.
    val fields = mutableStateListOf<Field>()
    val rootReferenceCoordinates = mutableStateOf(RootReferenceCoordinates())
    val globalCaret = mutableStateOf(
        GlobalCaret(
            fieldId = "",
            blockId = "",
            offsetInBlock = 0,
            globalPosition = Offset.Unspecified,
            height = 16f
        )
    )
    val focusedBlock = mutableStateOf("")

    val defaultTextStyle = TextStyle.Default

    //initialized with default TextStyle value.
    var currentTextStyle = mutableStateOf(defaultTextStyle)

    val defaultCharStyle = CurrentCharStyle(
        font = FontFamily.Default,
        fontSize = 16.sp,
        textColor = Color.Black,
        textHighlightColor = Color.Unspecified,
        isBold = false,
        isItalic = false,
        isUnderline = false,
        isStrikethrough = false
    )

    val defaultToolbarState = ToolbarState(
        font = FontFamily.Default,
        fontSize = 16.sp,
        textColor = Color.Black,
        textHighlightColor = Color.Unspecified,
        isBold = false,
        isItalic = false,
        isUnderline = false,
        isStrikethrough = false
    )
    var textMeasurer: TextMeasurer? = null

    var currentCharStyle = mutableStateOf(defaultCharStyle)

    var toolbarState = mutableStateOf(defaultToolbarState)


    val maxWidth: Int = 500

    var globalSelectionState by mutableStateOf(GlobalSelectionState())

    fun setRootCoords(coords: LayoutCoordinates){
        rootReferenceCoordinates.value = rootReferenceCoordinates.value.copy(coords)
    }
    /**
     * Handle the **Enter** key.
     *
     * Workflow:
     * 1.  Insert a `DelimiterBlock.Kind.NewLine` at the caret position **inside the current field**.
     * 2.  Scoop up everything **after** that delimiter (any split‑off text plus trailing sibling
     *     blocks) and move it to the *beginning* of the *next* field – creating that field if it
     *     doesn’t already exist.
     * 3.  Teleport the caret/focus to the first editable `TextBlock` in the next field, seeding an
     *     empty one when required.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun DocumentState.enterPressed() {
        val caret = globalCaret.value
        require(caret.fieldId != null && caret.blockId != null) {
            "Global caret must reference an existing field/block"
        }

        /* ─────────── 1. Locate active field + block ─────────────────────────────── */
        val fid = fields.indexOfFirst { it.id == caret.fieldId }
        require(fid >= 0) { "Field (id=${caret.fieldId}) not found" }

        val currentBlocks = fields[fid].blocks
        val bid = currentBlocks.indexOfFirst { it.id == caret.blockId }
        require(bid >= 0) { "Block (id=${caret.blockId}) not found" }

        /* ─────────── 2. Split active TextBlock + gather blocks to move ──────────── */
        val blocksToMove = mutableListOf<Block>()

        val active = currentBlocks[bid]
        if (active is Block.TextBlock) {
            val tfv = active.textFieldValue
            val off = caret.offsetInBlock.coerceIn(0, tfv.annotatedString.length)

            val before = tfv.annotatedString.subSequence(0, off)
            val after = tfv.annotatedString.subSequence(off, tfv.annotatedString.length)

            // Rewrite the current block with text BEFORE the caret
            currentBlocks[bid] = active.copy(
                textFieldValue = tfv.copy(
                    annotatedString = before,
                    selection = TextRange(before.length)
                )
            )

            // If there is text AFTER the caret, move it to its own block
            if (after.isNotEmpty()) {
                blocksToMove.add(
                    Block.TextBlock(
                        id = Uuid.random().toString(),
                        textFieldValue = tfv.copy(
                            annotatedString = after,
                            selection = TextRange(0)
                        ),
                        focusRequester = FocusRequester()
                    )
                )
            }
        }

        /* ─────────── 3. Collect trailing sibling blocks ─────────────────────────── */
        val trailing = currentBlocks.subList(bid + 1, currentBlocks.size).toList()
        blocksToMove.addAll(trailing)

        // Remove those trailing blocks from current field
        while (currentBlocks.size > bid + 1) currentBlocks.removeAt(bid + 1)

        /* ─────────── 4. Insert the newline delimiter right after the active block ─ */
        val delimiter = Block.DelimiterBlock(
            id = Uuid.random().toString(),
            kind = Block.DelimiterBlock.Kind.NewLine
        )
        currentBlocks.add(bid + 1, delimiter)

        /* ─────────── 5. Prepare / create the NEXT field ─────────────────────────── */
        val nextFieldIndex = fid + 1
        val nextField: Field = if (nextFieldIndex < fields.size) {
            fields[nextFieldIndex]
        } else {
            Field(id = Uuid.random().toString(), blocks = mutableStateListOf()).also {
                fields.add(it)
            }
        }

        val nextBlocks = nextField.blocks

        // Prepend moved blocks to the next field
        if (blocksToMove.isNotEmpty()) nextBlocks.addAll(0, blocksToMove)

        // Ensure next field starts with a TextBlock so the user can type
        if (nextBlocks.isEmpty() || nextBlocks.first() !is Block.TextBlock) {
            val emptyText = Block.TextBlock(
                id = Uuid.random().toString(),
                textFieldValue = TextFieldValue("").copy(selection = TextRange(0)),
                focusRequester = FocusRequester()
            )
            nextBlocks.add(0, emptyText)
        }

        val caretBlock = nextBlocks.first { it is Block.TextBlock } as Block.TextBlock

        /* ─────────── 6. Update caret + focus ────────────────────────────────────── */
        globalCaret.value = caret.copy(
            fieldId = nextField.id,
            blockId = caretBlock.id,
            offsetInBlock = 0
        )
        updateFocusedBlock(caretBlock.id)
        onGlobalCaretMoved()
    }
}
