package com.alpha.vision.pro.gallery.editor.ui
import androidx.compose.animation.AnimatedVisibility

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.alpha.vision.pro.gallery.designsystem.components.AlphaTopBar
import com.alpha.vision.pro.gallery.editor.viewmodel.EditorEvent
import com.alpha.vision.pro.gallery.editor.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    mediaId  : Long,
    onBack   : () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state   by viewModel.state.collectAsState()
    val context  = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.onEvent(EditorEvent.DismissError)
        }
    }
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            snackbar.showSnackbar("Saved to gallery ✓")
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AlphaTopBar(
                title = state.media?.displayName ?: "Editor",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(EditorEvent.Reset) }) {
                        Icon(Icons.Filled.Refresh, "Reset")
                    }
                    IconButton(
                        onClick  = { viewModel.onEvent(EditorEvent.Save) },
                        enabled  = !state.isSaving
                    ) {
                        if (state.isSaving)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── Preview Pane (60% height) ──────────────────────────────────
            Box(
                modifier            = Modifier
                    .weight(0.6f)
                    .fillMaxWidth(),
                contentAlignment    = Alignment.Center
            ) {
                val preview = state.previewBitmap
                if (preview != null) {
                    Image(
                        bitmap           = preview.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale     = ContentScale.Fit,
                        modifier         = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model            = state.media?.uri,
                        contentDescription = "Original",
                        contentScale     = ContentScale.Fit,
                        modifier         = Modifier.fillMaxSize()
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible  = state.isProcessing,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CircularProgressIndicator()
                }
            }

            HorizontalDivider()

            // ── Controls Pane (40% height) ─────────────────────────────────
            EditorControls(
                params   = state.params,
                onEvent  = viewModel::onEvent,
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
            )
        }
    }
}
