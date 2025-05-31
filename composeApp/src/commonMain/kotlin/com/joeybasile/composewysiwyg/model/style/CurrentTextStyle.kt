package com.joeybasile.composewysiwyg.model.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.joeybasile.composewysiwyg.model.DocumentState

data class CurrentTextStyle(
    val style: TextStyle?
){
    fun isBold(): Boolean {
        // If style is null or fontWeight is not Bold, this will be false
        return style?.fontWeight == FontWeight.Bold
    }
}

data class DefaultCharStyle(
    val font: FontFamily = FontFamily.Default,
    val fontSize: TextUnit = 16.sp,
    val textColor: Color = Color.Black,
    val textHighlightColor: Color = Color.Unspecified,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false
)

data class CurrentCharStyle(
    val font: FontFamily? = null,
    val fontSize: TextUnit? = null,
    val textColor: Color? = null,
    val textHighlightColor: Color? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false
)

fun DefaultCharStyle.toCurrentCharStyle(): CurrentCharStyle =
    CurrentCharStyle(
        font               = font,
        fontSize           = fontSize,
        textColor          = textColor,
        textHighlightColor = textHighlightColor,
        isBold             = isBold,
        isItalic           = isItalic,
        isUnderline        = isUnderline,
        isStrikethrough    = isStrikethrough
    )
fun DocumentState.resetCurrentCharStyleToDefault(defaultState: DefaultCharStyle = DefaultCharStyle()) {
    currentCharStyle.value = defaultState.toCurrentCharStyle()
}
