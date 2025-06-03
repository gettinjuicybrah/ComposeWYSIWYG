package com.joeybasile.composewysiwyg.model.selection.dragBased

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.DocumentTextFieldState
import com.joeybasile.composewysiwyg.model.caret.onCaretMoved
import com.joeybasile.composewysiwyg.model.selection.rebuildSelectionFromAnchorAndFocus
import com.joeybasile.composewysiwyg.model.selection.setAnchorCaret
import com.joeybasile.composewysiwyg.model.selection.setFocusCaret
import com.joeybasile.composewysiwyg.model.selection.toSelectionCaret
import com.joeybasile.composewysiwyg.util.sliceRange

fun DocumentState.startDragSelection(globalOffset: Offset) {
    toSelectionCaret(globalOffset)?.let { dragCaret ->
        setAnchorCaret(dragCaret)
        setFocusCaret(dragCaret)
        rebuildSelectionFromAnchorAndFocus()
    }
}

fun DocumentState.updateDragSelection(globalOffset: Offset) {
    toSelectionCaret(globalOffset)?.let { dragCaret ->
        setFocusCaret(dragCaret)
        rebuildSelectionFromAnchorAndFocus()
    }
}