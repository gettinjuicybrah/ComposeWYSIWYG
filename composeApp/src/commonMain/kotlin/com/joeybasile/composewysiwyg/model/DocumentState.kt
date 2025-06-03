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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.style.CurrentCharStyle
import com.joeybasile.composewysiwyg.model.style.ToolbarState
import com.joeybasile.composewysiwyg.model.caret.CaretState
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.caret.updateCaretPosition
import com.joeybasile.composewysiwyg.model.event.onEvent
import com.joeybasile.composewysiwyg.model.selection.SelectionState
import com.joeybasile.composewysiwyg.model.style.applySpanStyles
import com.joeybasile.composewysiwyg.model.style.resetCurrentCharStyleToDefault
import com.joeybasile.composewysiwyg.model.style.resetToolbarToDefault
import com.joeybasile.composewysiwyg.util.sliceRange
import kotlinx.coroutines.CoroutineScope

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

    val state = remember {
        DocumentState(
            scope = scope
        )
    }
    // Initialize the document state for a new document. Later, this can be
    // replaced with conditional logic if you decide to support existing documents.
    LaunchedEffect(Unit) {
        state.initializeNewDocument()
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
    var currentCharStyle = mutableStateOf(defaultCharStyle)

    var toolbarState = mutableStateOf(defaultToolbarState)

    var caretState = mutableStateOf(
        CaretState(
            fieldIndex = 0,
            offset = 0,
            globalPosition = Offset.Unspecified,
            height = 16f
        )
    )

    val maxWidth: Int = 500

    var selectionState by mutableStateOf(SelectionState())

    // Internally maintain a mutable state list of text field states.
    var documentTextFieldList = mutableStateListOf<DocumentTextFieldState>()

    // State holder for the coordinates of parent containers.
    var parentCoordinates = mutableStateOf(ParentCoordinates())

    val focusedLine = mutableStateOf(0)

    // 2) The little helper that converts any mapper into a Compose callback
    inline fun <T> event(
        crossinline mapper: (T) -> DocumentEvent
    ): (T) -> Unit = { payload ->
        onEvent(mapper(payload))
    }

    fun updateFocusedLine(newIndex: Int) {
        println("updateFocusedLine: $newIndex")
        focusedLine.value = newIndex
    }

    fun initializeNewDocument() {

        val newField = DocumentTextFieldState(
            textFieldValue = TextFieldValue("new doc init test"),
            layoutCoordinates = null,
            textLayoutResult = null,
            focusRequester = FocusRequester()
        )
        documentTextFieldList.add(newField)
        caretState.value = CaretState(
            fieldIndex = 0,
            offset = 0,
            globalPosition = Offset.Unspecified,
            height = 16f
        )

    }
    fun setNewLineAtEnd(index: Int) {
        documentTextFieldList.set(
            index,
            element = documentTextFieldList[index].copy(hasNewLineAtEnd = true)
        )
    }

    fun makeField(initial: AnnotatedString): DocumentTextFieldState =
        DocumentTextFieldState(
            textFieldValue = TextFieldValue(annotatedString = initial),
            focusRequester = FocusRequester()
        )

    fun makeEmptyField(): DocumentTextFieldState =
        DocumentTextFieldState(
            textFieldValue = TextFieldValue(""),
            focusRequester = FocusRequester()
        )

    /*
    Prepends an annotatedString to the annotatedString of an existing field's TextFieldValue.
    Sets the selection's end and start to the length of the prefix via TextRange fxn.
     */
    fun prependToField(
        index: Int,
        prefix: AnnotatedString
    ) {
        val old = documentTextFieldList[index]
        val combined = buildAnnotatedString {
            append(prefix)
            append(old.textFieldValue.annotatedString)
        }
        documentTextFieldList[index] = old.copy(
            textFieldValue = old.textFieldValue.copy(
                annotatedString = combined,
                selection = TextRange(prefix.length)
            )
        )
    }

    /*
    The selection is collapsed (start == end), and set to 0 by default.
     */
    fun insertTextFieldAfter(index: Int) {
        val newField = DocumentTextFieldState(
            textFieldValue = TextFieldValue(""),
            layoutCoordinates = null,
            textLayoutResult = null,
            focusRequester = FocusRequester()
        )
        documentTextFieldList.add(index + 1, newField)
    }
    fun insertFieldAfter(index: Int, field: DocumentTextFieldState) {
        documentTextFieldList.add(index + 1, field)
    }

    /**
     * Updates the layout coordinates of the overall document container.
     *
     * @param coordinates The new [LayoutCoordinates] for the document container.
     */
    fun setDocCoords(coordinates: LayoutCoordinates) {
        parentCoordinates.value = parentCoordinates.value.copy(document = coordinates)
    }

    /**
     * Updates the layout coordinates of the Box container.
     *
     * @param coordinates The new [LayoutCoordinates] for the Box container.
     */
    fun setBoxCoords(coordinates: LayoutCoordinates) {
        parentCoordinates.value = parentCoordinates.value.copy(box = coordinates)
    }

    /**
     * Updates the layout coordinates of the LazyColumn container.
     *
     * @param coordinates The new [LayoutCoordinates] for the LazyColumn container.
     */
    fun setLazyColCoords(coordinates: LayoutCoordinates) {
        parentCoordinates.value = parentCoordinates.value.copy(lazyColumn = coordinates)
    }

    fun splitAnnotatedString(
        fullText: AnnotatedString,
        measurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int
    ): Pair<AnnotatedString, AnnotatedString> {
        val result = measurer.measure(
            text = fullText,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx)
        )

        // if there's only one line, nothing to split
        if (result.lineCount <= 1) {
            return fullText to AnnotatedString("")
        }
        // otherwise line 0 ends at:
        val cutIndex = result.getLineEnd(0, visibleEnd = true)
        val fitting = fullText.sliceRange(0, cutIndex)
        val overflow = fullText.sliceRange(cutIndex, fullText.length)
        return fitting to overflow
    }

    fun applyCurrentCharStyleToProposedTFV(
        prevValue: TextFieldValue,
        proposedNewValue: TextFieldValue
    ): TextFieldValue {
        // 1) Extract raw text from each AnnotatedString
        val oldAnnotated: AnnotatedString = prevValue.annotatedString
        val newRawText: String = proposedNewValue.annotatedString.text

        // 2) Confirm that exactly some new chunk was appended (or inserted)
        //    at “diffStart”. We’ll find that offset:
        var diffStart = 0
        val oldText = oldAnnotated.text
        while (diffStart < oldText.length && oldText[diffStart] == newRawText[diffStart]) {
            diffStart++
        }
        // diffLength = how many new chars appeared:
        val diffLength = newRawText.length - oldText.length
        val diffEnd = diffStart + diffLength  // exclusive

        // 3) Build the merged SpanStyle (bold/italic/underline as per currentCharStyle)
        val currentCS = currentCharStyle.value  // wherever you store it
        val mergedSpan = SpanStyle(
            fontFamily = currentCS.font,
            fontSize   = currentCS.fontSize!!,
            color      = currentCS.textColor!!,
            background = currentCS.textHighlightColor!!,
            fontWeight = if (currentCS.isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle  = if (currentCS.isItalic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = listOfNotNull(
                currentCS.isUnderline.takeIf { it }?.let { TextDecoration.Underline },
                currentCS.isStrikethrough.takeIf { it }?.let { TextDecoration.LineThrough }
            ).takeIf { it.isNotEmpty() }?.let { TextDecoration.combine(it) }
        )

        // 4) Now: we want a new AnnotatedString that “starts from oldAnnotated,
        //    but has the freshly typed substring [diffStart..diffEnd) styled.”
        //
        //    Easiest approach: build a NEW AnnotatedString in three chunks:
        //    (a) prefix = oldAnnotated[0 until diffStart]         (with all its old spans)
        //    (b) inserted = newRawText.substring(diffStart, diffEnd) (apply mergedSpan over it)
        //    (c) suffix = oldAnnotated[diffStart until oldLength] (with all its old spans, but must shift offsets by diffLength)
        //
        //    However, Compose’s `AnnotatedString.Builder( base = oldAnnotated )` can preserve existing spans “in place,”
        //    but only if the text content in the Builder’s base matches exactly the final text. Here, the final text is “old + inserted”.
        //    So we can’t literally feed `Builder(oldAnnotated)` because its base text is the old text (e.g. “abcd”),
        //    but our new text might be “abXcd” (one letter inserted in the middle)—the lengths don’t match.

        // 5) Instead, do a manual rebuild:
        //    5a) prefixAnnotated preserves old spans in [0..diffStart).
        val prefixAnnotated = oldAnnotated.subSequence(0, diffStart)

        //    5b) suffixAnnotated preserves old spans in [diffStart..oldText.length),
        //         but those spans need to get their start/end “shifted down” by diffStart.
        //         The easiest way: take oldAnnotated.subSequence(diffStart, oldText.length), which is an AnnotatedString
        //         that already contains the spans (local to that slice!). Because `subSequence(...)` automatically
        //         maps the old spans into the new coordinate system [0..(suffixLength)).
        val suffixAnnotated = oldAnnotated.subSequence(diffStart, oldText.length)

        //    5c) insertedText = newRawText.substring(diffStart, diffEnd) → plain String
        val insertedPlain = newRawText.substring(diffStart, diffEnd)

        // 6) Now we re-assemble:
        val rebuilt = buildAnnotatedString {
            // 6a) append the prefix (+ its spans)
            append(prefixAnnotated)

            // 6b) append the newly typed text *with* our mergedSpan over [0..insertedPlain.length)
            val insertionStartInBuilder = length  // this should be “diffStart”
            append(insertedPlain)
            addStyle(
                style = mergedSpan,
                start = insertionStartInBuilder,
                end   = insertionStartInBuilder + insertedPlain.length
            )

            // 6c) append the suffix (+ its spans). BUT! We have to shift suffix spans by “insertion length” automatically.
            //     The builder will notice that when you do `append(suffixAnnotated)`, it copies over all of `suffixAnnotated`’s
            //     spans and *adds* them at an offset of exactly `lengthBeforeAppendSuffix`. In other words,
            //     if `suffixAnnotated` had a span [0..3), after we do `append(suffixAnnotated)` at position P,
            //     the builder will place that suffix’s spans at [P..P+3). That is exactly “shift by diffStart + insertedLength.”
            append(suffixAnnotated)
        }

        // 7) Now `rebuilt` holds all the old spans (split correctly around diffStart), plus one new SpanStyle exactly around the inserted text.

        // 8) Finally, return a brand-new TextFieldValue that carries over any composition/cursor from `proposedNewValue`,
        //    except we overwrite the `annotatedString` with our freshly built one.
        return proposedNewValue.copy(annotatedString = rebuilt)
    }

    fun processTextFieldValueUpdate(
        index: Int,
        newValue: TextFieldValue,
        measurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int
    ) {
        val result = measurer.measure(
            text = newValue.annotatedString,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 1,
            softWrap = false
        )
        val xPos = maxWidth.toFloat() - Float.MIN_VALUE
        val maxMeasuredOffset = result.getOffsetForPosition(Offset(xPos, 0f))

        /*
        If the TFV's annotated string did not overflow the max width constraint,
        then update documentTextField[index].textFieldValue, which will set the global caret's offset to the local caret's, importantly.
         */
        if (!result.didOverflowWidth) {
            println("THESE ARE THE SPANSTYLES FOR THE ANNOTATEDSTRING: ${newValue.annotatedString.spanStyles}")
            updateTextFieldValue(index, newValue)
            println("NO OVERFLOW in processTextFieldValueUpdate")
            return
        }

        /*
        Else, there was OF, meaning we need to split the annotated string into two. The left is what fit, the right is what caused the overflow.
         */
        else {
            println("----------------OVERFLOW in processTextFieldValueUpdate")

            val leftAnnotatedString = newValue.annotatedString.subSequence(
                0,
                maxMeasuredOffset
            )
            val rightAnnotatedString = newValue.annotatedString.subSequence(
                maxMeasuredOffset,
                newValue.annotatedString.length
            )

            if (newValue.selection.start >= maxMeasuredOffset) {
                updateTextFieldValueNoCaret(
                    index,
                    newValue.copy(annotatedString = leftAnnotatedString)
                )
                println("BEGIN OUT OF BOUNDS CARET OF RECURSIVE CALL")
                recurseTFVUpdate(
                    index,
                    index,
                    rightAnnotatedString,
                    measurer,
                    textStyle,
                    maxWidthPx,
                    shouldUpdateCaret = true,
                    initialOverflowLength = newValue.annotatedString.length - maxMeasuredOffset
                )
            }
            //If the local caret is less than the max, then it is acceptable to eventually set the global caret to it for the current index.
            else if (newValue.selection.start < maxMeasuredOffset) {
                updateTextFieldValue(index, newValue.copy(annotatedString = leftAnnotatedString))
                println("BEGIN ***IN BOUNDS** CARET RECURSIVE CALL")
                recurseTFVUpdate(
                    index,
                    index,
                    rightAnnotatedString,
                    measurer,
                    textStyle,
                    maxWidthPx,
                    shouldUpdateCaret = false,
                    initialOverflowLength = newValue.annotatedString.length - maxMeasuredOffset
                )
            }
        }
    }

    fun recurseTFVUpdate(
        prevIndex: Int,
        initialIndex: Int,
        overflowAnnotatedString: AnnotatedString,
        measurer: TextMeasurer,
        textStyle: TextStyle,
        maxWidthPx: Int,
        shouldUpdateCaret: Boolean,
        initialOverflowLength: Int = overflowAnnotatedString.length
    ) {
        println("entered recurseTFVUpdate. ")
        val currentIndex = prevIndex + 1
        println("CURRENT INDEX: $currentIndex in recursiveTFVUpdate. PREV INDEX: $prevIndex. OVERFLOW: $overflowAnnotatedString. SHOULD UPDATE CARET: $shouldUpdateCaret.")
        //If the previous index was the last field, then we'll need to make a new one.
        if (currentIndex >= documentTextFieldList.size) {
            println("inserting new field at index: $currentIndex....")
            insertFieldAfter(prevIndex, makeEmptyField())
        }

        //Next, prepend the overflow to the currentField's annotatedstring.
        val combined = prependToAnnotatedStringAndGet(currentIndex, overflowAnnotatedString)
        //Measure the updated annotatedString.
        val result = measurer.measure(
            text = combined,
            style = textStyle,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 1,
            softWrap = false
        )
        val xPos = maxWidth.toFloat() - Float.MIN_VALUE
        val maxMeasuredOffset = result.getOffsetForPosition(Offset(xPos, 0f))
        val leftAnnotatedString = combined.subSequence(
            0,
            maxMeasuredOffset
        )
        val rightAnnotatedString = combined.subSequence(
            maxMeasuredOffset,
            combined.length
        )
        //If no OF, then we have arrived at base case.
        if (!result.didOverflowWidth) {
            println("BASE CASE REACHED IN RECURSE. SHOULD UPDATE CARETB AFTER BOUNCING RETURN.")
            updateTextFieldValueNoCaret(
                index = currentIndex,
                newValue = documentTextFieldList[currentIndex].textFieldValue.copy(
                    annotatedString = combined
                )
            )
            println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&UPDATED ANNOTATEDSTRING: ${combined}")
            if (shouldUpdateCaret) {
                caretState.value = caretState.value.copy(
                    fieldIndex = initialIndex + 1,
                    offset = initialOverflowLength
                )
                onCaretMoved()

            }
            println("NO OVERFLOW IN RECURSE")
            return
        }

        //else, we recurse through.
        else {
            println("BASE CASE NOT REACHED! OVERFLOW. RECURSING...")


            updateTextFieldValueNoCaret(
                index = currentIndex,
                newValue = documentTextFieldList[currentIndex].textFieldValue.copy(
                    annotatedString = leftAnnotatedString
                )
            )
            recurseTFVUpdate(
                prevIndex = currentIndex,
                initialIndex = initialIndex,
                overflowAnnotatedString = rightAnnotatedString,
                measurer = measurer,
                textStyle = textStyle,
                maxWidthPx = maxWidthPx,
                shouldUpdateCaret = shouldUpdateCaret,
                initialOverflowLength = initialOverflowLength
            )

        }

    }

    fun prependToAnnotatedStringAndGet(
        index: Int,
        prefix: AnnotatedString,
    ): AnnotatedString {
        println("entered prependToAnnotatedStringAndGet. index: $index. prefix: $prefix. oldValue: ${documentTextFieldList[index].textFieldValue.annotatedString}.")
        val old = documentTextFieldList[index]
        val combined = buildAnnotatedString {
            append(prefix)
            append(old.textFieldValue.annotatedString)
        }
        println("combined: $combined. returning...")
        return combined
    }

    /**
     * Prepend text into the given field, updating only the documentTextFieldList
     * and NOT syncing the global caret position.
     */
    fun prependToFieldWithoutMovingCaret(index: Int, newValue: TextFieldValue) {
        // Guard against out-of-bounds
        val field = safeGet(documentTextFieldList, index) ?: return

        // Just replace the stored TextFieldValue—in particular, don't update caretState
        documentTextFieldList[index] = field.copy(textFieldValue = newValue)
    }

    /**
     * Updates the text value for a specific text field.
     *
     * This method creates a new state object with the updated [TextFieldValue] so that
     * Compose is aware of the change and triggers recomposition.
     *
     * @param index The index of the text field to update.
     * @param newValue The new [TextFieldValue] for the text field.
     */

    //What this does: Updates the BTF’s textFieldValue and, if it’s the focused field, sets the global caret’s offset to the start of the new selection (where the local caret is after the user’s input).
    fun updateTextFieldValue(index: Int, newValue: TextFieldValue) {
        //println("entered updateTextFieldValue")
        // Check if the index is within bounds
        val field = safeGet(documentTextFieldList, index) ?: return
        //println("SAFELY GOT IN UPDATETEXTFIELDVALUE.")
        val newTFV = field.textFieldValue.copy(
            annotatedString = newValue.annotatedString, selection = newValue.selection
        )
        documentTextFieldList[index] = field.copy(
            textFieldValue = newTFV
        )
        // *** unconditionally sync the global caret to whatever the local selection now is ***
        caretState.value = caretState.value.copy(
            fieldIndex = index,
            offset = newValue.selection.start
        )
        // immediately recompute the global caret position on screen
        onCaretMoved()
    }

    fun updateTextFieldValueNoCaret(index: Int, newValue: TextFieldValue) {
        // Check if the index is within bounds
        val field = safeGet(documentTextFieldList, index) ?: return
        documentTextFieldList[index] = field.copy(textFieldValue = newValue)
    }

    /**
     * Updates the text layout result for a specific text field.
     *
     * This holds data such as text metrics and cursor positions that can be used for
     * further layout or editing operations.
     *
     * @param index The index of the text field to update.
     * @param newResult The new [TextLayoutResult] generated from the text layout.
     */
    fun updateTextFieldTextLayoutResult(index: Int, newResult: TextLayoutResult) {
        val field = safeGet(documentTextFieldList, index) ?: return
        documentTextFieldList[index] = field.copy(textLayoutResult = newResult)
        if (caretState.value.fieldIndex == index) {
            updateCaretPosition()
        }
    }

    fun <T> safeGet(list: List<T>, index: Int): T? {
        return if (index >= 0 && index < list.size) list[index] else null
    }

    /**
     * Updates the layout coordinates for a specific text field.
     *
     * These coordinates are captured during layout passes and can be used for hit testing,
     * selection, or positioning overlays relative to a text field.
     *
     * @param index The index of the text field to update.
     * @param coords The new [LayoutCoordinates] for the text field.
     */
    fun updateTextFieldCoords(index: Int, coords: LayoutCoordinates) {
        val field = safeGet(documentTextFieldList, index) ?: return
        documentTextFieldList[index] = field.copy(layoutCoordinates = coords)
        if (caretState.value.fieldIndex == index) {
            updateCaretPosition()
        }
    }

    /**
     * Maps a global offset (in the Box's coordinate system) to a text field index and character offset.
     * @param globalOffset The position in the Box's coordinate system.
     * @return A Pair of (fieldIndex, offset) or null if no text field contains the position.
     */
    fun getFieldAndOffsetForPosition(globalOffset: Offset): Pair<Int, Int>? {
        val boxCoords = parentCoordinates.value.box ?: return null
        for (i in documentTextFieldList.indices) {
            val field = documentTextFieldList[i]
            val textFieldCoords = field.layoutCoordinates ?: continue
            // Get the text field's top-left position relative to the Box
            val textFieldTopLeft = boxCoords.localPositionOf(textFieldCoords, Offset.Zero)
            val textFieldSize = textFieldCoords.size
            val textFieldRect = Rect(
                textFieldTopLeft,
                Size(textFieldSize.width.toFloat(), textFieldSize.height.toFloat())
            )
            if (textFieldRect.contains(globalOffset)) {
                // Convert globalOffset to local coordinates within the text field
                val localOffset = textFieldCoords.localPositionOf(boxCoords, globalOffset)
                val textLayoutResult = field.textLayoutResult ?: return null
                val offset = textLayoutResult.getOffsetForPosition(localOffset)
                return Pair(i, offset)
            }
        }
        return null
    }

    fun enterPressed(){
        val idx = caretState.value.fieldIndex
        val field = documentTextFieldList[idx]
        val rawOffset = caretState.value.offset.coerceIn(0, field.textFieldValue.annotatedString.length)
        val beforeAS  = field.textFieldValue.annotatedString.subSequence(0, rawOffset)
        val afterAS   = field.textFieldValue.annotatedString.subSequence(rawOffset, field.textFieldValue.annotatedString.length)

        // 1) overwrite current field with the “before” text
        documentTextFieldList[idx] = field.copy(
            textFieldValue = field.textFieldValue.copy(annotatedString = beforeAS)
        )
        setNewLineAtEnd(idx)

        // 2) unconditionally insert a brand-new, empty field **below**
        insertTextFieldAfter(idx)
        val newIndex = idx + 1

        // 3) put the “after” text into that fresh field
        prependToField(newIndex, afterAS)

        // 4) move the caret into the new line
        caretState.value = caretState.value.copy(fieldIndex = newIndex, offset = 0)
        onCaretMoved()
    }

}