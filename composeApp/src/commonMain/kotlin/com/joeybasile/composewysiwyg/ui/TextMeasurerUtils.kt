package com.joeybasile.composewysiwyg.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * A small helper to centralize line-wrapping TextMeasurer.
 */
@Composable
fun rememberLineMeasurer(): TextMeasurer = rememberTextMeasurer()