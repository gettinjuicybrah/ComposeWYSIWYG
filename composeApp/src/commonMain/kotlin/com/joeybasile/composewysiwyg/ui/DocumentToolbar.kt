package com.joeybasile.composewysiwyg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.joeybasile.composewysiwyg.events.DocumentEvent
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.style.*
import com.joeybasile.composewysiwyg.model.style.ToolbarState

@Composable
fun DocumentToolbar(
    state: DocumentState,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier
) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        val toolbarState = state.toolbarState
        //FontListDropdown(state, state.toolbarState.value)
        //FontSizeListDropdown(state, state.toolbarState.value)
        BoldButton(state, toolbarState, textMeasurer)
        ItalicButton(state, toolbarState, textMeasurer)
        UnderlineButton(state, toolbarState, textMeasurer)
        StrikethroughButton(state, toolbarState, textMeasurer)
        ClearFormatButton(state, textMeasurer)
    }
}

@Composable
fun FontListDropdown(state: DocumentState, toolbarState: ToolbarState, textMeasurer: TextMeasurer) {
Text("to be font list")
}

@Composable
fun FontSizeListDropdown(state: DocumentState, toolbarState: ToolbarState, textMeasurer: TextMeasurer) {
Text("to be font size list")
}

@Composable
fun BoldButton(state:DocumentState, toolbarState: MutableState<ToolbarState>, textMeasurer: TextMeasurer) {

    Button(
        onClick = {
            println("RIGHT BEFORE BOLD CLICKED. isBold: ${toolbarState.value.isBold}")
            //state.toggleBold()
            state.doToggle(DocumentEvent.CharStyleType.BOLD, textMeasurer)
            println("AFTER BOLD CLICKED. isBold: ${toolbarState.value.isBold}")
                  },

        modifier = Modifier
            // <-- Prevent this Button from ever taking focus:
            .focusProperties { canFocus = false },
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (toolbarState.value.isBold) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            contentColor = if (toolbarState.value.isBold) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        val text = if (toolbarState.value.isBold) "isBold" else "notBold"
        Text(text)
    }
}
@Composable
fun ItalicButton(state: DocumentState, toolbarState: MutableState<ToolbarState>, textMeasurer: TextMeasurer) {
    Button(
        onClick = {
            //state.toggleItalic()
            state.doToggle(DocumentEvent.CharStyleType.ITALIC, textMeasurer)},
        modifier = Modifier
            // <-- Prevent this Button from ever taking focus:
            .focusProperties { canFocus = false },
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (toolbarState.value.isItalic) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            contentColor = if (toolbarState.value.isItalic) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        val text = if (toolbarState.value.isItalic) "isItalic" else "notItalic"
        Text(text)
    }
}

@Composable
fun UnderlineButton(state: DocumentState, toolbarState: MutableState<ToolbarState>, textMeasurer: TextMeasurer) {
    Button(
        onClick = { state.doToggle(DocumentEvent.CharStyleType.UNDERLINE, textMeasurer) },
        modifier = Modifier
            // <-- Prevent this Button from ever taking focus:
            .focusProperties { canFocus = false },
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (toolbarState.value.isUnderline) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            contentColor = if (toolbarState.value.isUnderline) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        val text = if (toolbarState.value.isUnderline) "isUnderline" else "notUnderline"
        Text(text)
    }
}

@Composable
fun StrikethroughButton(state: DocumentState, toolbarState: MutableState<ToolbarState>, textMeasurer: TextMeasurer) {
    Button(
        onClick = { state.doToggle(DocumentEvent.CharStyleType.STRIKETHROUGH, textMeasurer) },
        modifier = Modifier
            // <-- Prevent this Button from ever taking focus:
            .focusProperties { canFocus = false },
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (toolbarState.value.isStrikethrough) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
            contentColor = if (toolbarState.value.isStrikethrough) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        val text = if (toolbarState.value.isStrikethrough) "isStrikethrough" else "notStrikethrough"
        Text(text)
    }
}
@Composable
fun ClearFormatButton(state: DocumentState, textMeasurer: TextMeasurer) {
    Button(
        onClick = { state.clearFormat(textMeasurer) },
        modifier = Modifier
            // <-- Prevent this Button from ever taking focus:
            .focusProperties { canFocus = false }
    ) {
        Text("Clear Formatting")
    }
}
