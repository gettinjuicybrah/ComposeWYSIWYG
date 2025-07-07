package com.joeybasile.composewysiwyg.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.joeybasile.composewysiwyg.model.DocumentState

@Composable
fun DocumentWithSelectionOverlay(
    state: DocumentState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            //Added below so the selection segments are not static when the window resizes
            .onGloballyPositioned { coords ->
            })
    {

        /* the actual document */
        Document(
            state = state,
            modifier = Modifier.matchParentSize()
        )

        /* translucent green rects go on top */
        if (state.globalSelectionState.isActive) {
            SelectionOverlay(
                state = state,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

