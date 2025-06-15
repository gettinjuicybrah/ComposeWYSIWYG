package com.joeybasile.composewysiwyg.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joeybasile.composewysiwyg.model.DocumentState

@Composable
fun DocumentWithSelectionOverlay(
    state: DocumentState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        /* the actual document */
        Document(
            state    = state,
            modifier = Modifier.matchParentSize()
        )

        /* translucent green rects go on top */
        if (state.globalSelectionState.isActive) {
            SelectionOverlay(
                state    = state,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

