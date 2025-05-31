package com.joeybasile.composewysiwyg.model.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.joeybasile.composewysiwyg.model.DocumentState

data class ToolbarState(
    val font: FontFamily? = null,
    val fontSize: TextUnit? = null,
    val textColor: Color? = null,
    val textHighlightColor: Color? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false
)
data class DefaultToolbarState(
    val font: FontFamily = FontFamily.Default,
    val fontSize: TextUnit = 16.sp,
    val textColor: Color = Color.Black,
    val textHighlightColor: Color = Color.Unspecified,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false
)
fun DefaultToolbarState.toToolbarState(): ToolbarState =
    ToolbarState(
        font               = font,
        fontSize           = fontSize,
        textColor          = textColor,
        textHighlightColor = textHighlightColor,
        isBold             = isBold,
        isItalic           = isItalic,
        isUnderline        = isUnderline,
        isStrikethrough    = isStrikethrough
    )
fun DocumentState.resetToolbarToDefault(defaultState: DefaultToolbarState = DefaultToolbarState()) {
    toolbarState.value = defaultState.toToolbarState()
}