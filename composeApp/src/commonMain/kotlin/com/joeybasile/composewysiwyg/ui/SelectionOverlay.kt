package com.joeybasile.composewysiwyg.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.joeybasile.composewysiwyg.model.DocumentState

@Composable
fun SelectionOverlay(
    state: DocumentState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()

            .drawWithContent {
                drawContent()

                // Loop over each segment and draw it individually:
                state.globalSelectionState.segments.forEach { segment ->
                    drawRect(
                        color = Color.Green,
                        topLeft = segment.rect.topLeft,
                        size = segment.rect.size,
                        alpha = 0.5F
                    )
                }
            }
    )
}