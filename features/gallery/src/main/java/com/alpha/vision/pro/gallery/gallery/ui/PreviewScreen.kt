package com.alpha.vision.pro.gallery.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryEvent
import com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    mediaId: Long,
    onBack: () -> Unit,
    onEditClick: (Long) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    val initialPage = remember(state.mediaItems) {
        val index = state.mediaItems.indexOfFirst { it.id == mediaId }
        if (index != -1) index else 0
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { state.mediaItems.size }
    )

    var hasScrolledToInitial by remember { mutableStateOf(false) }
    LaunchedEffect(state.mediaItems, initialPage) {
        if (state.mediaItems.isNotEmpty() && !hasScrolledToInitial) {
            pagerState.scrollToPage(initialPage)
            hasScrolledToInitial = true
        }
    }

    var exifTarget by remember { mutableStateOf<com.alpha.vision.pro.gallery.domain.model.MediaItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.mediaItems.isNotEmpty() && pagerState.currentPage < state.mediaItems.size) {
                        Text(state.mediaItems[pagerState.currentPage].displayName)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.mediaItems.isNotEmpty() && pagerState.currentPage < state.mediaItems.size) {
                        val currentItem = state.mediaItems[pagerState.currentPage]
                        IconButton(onClick = { onEditClick(currentItem.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { exifTarget = currentItem }) {
                            Icon(Icons.Filled.Info, contentDescription = "Info")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.onEvent(GalleryEvent.LongPressItem(currentItem.id))
                                    viewModel.onEvent(GalleryEvent.DeleteSelected)
                                    onBack()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            if (state.mediaItems.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 16.dp
                ) { pageIndex ->
                    if (pageIndex < state.mediaItems.size) {
                        val item = state.mediaItems[pageIndex]
                        ZoomableImage(
                            uri = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    exifTarget?.let { item ->
        ExifBottomSheet(
            item = item,
            onDismiss = { exifTarget = null },
            onStripExif = {
                viewModel.onEvent(GalleryEvent.StripExif(item.id))
                exifTarget = null
            }
        )
    }
}

@Composable
private fun ZoomableImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 3f
                        offset = Offset.Zero
                    }
                )
            }
            .transformable(state = transformState)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
