package com.joeybasile.composewysiwyg.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.joeybasile.composewysiwyg.model.DocumentState
import com.joeybasile.composewysiwyg.model.rememberDocumentState

@Composable
fun DocumentRoot(){
    val state: DocumentState = rememberDocumentState()
    Column {
        DocumentToolbar(state, rememberLineMeasurer())
        DocumentWithSelectionOverlay(state)
    }
}