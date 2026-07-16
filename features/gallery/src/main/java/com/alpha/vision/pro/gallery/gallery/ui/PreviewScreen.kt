package com.alpha.vision.pro.gallery.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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

    var isZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
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
                    pageSpacing = 16.dp,
                    userScrollEnabled = !isZoomed
                ) { pageIndex ->
                    if (pageIndex < state.mediaItems.size) {
                        val item = state.mediaItems[pageIndex]
                        ZoomableImage(
                            uri = item.uri,
                            contentDescription = item.displayName,
                            isCurrentPage = (pageIndex == pagerState.currentPage),
                            onZoomChanged = { zoomed ->
                                isZoomed = zoomed
                            },
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
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    var size by remember { mutableStateOf(IntSize.Zero) }

    // Automatically snap-reset state when page swipe occurs
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            onZoomChanged(false)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scale.value > 1f) {
                                launch { scale.animateTo(1f, animationSpec = tween(300)) }
                                launch { offsetX.animateTo(0f, animationSpec = tween(300)) }
                                launch { offsetY.animateTo(0f, animationSpec = tween(300)) }
                                onZoomChanged(false)
                            } else {
                                val targetScale = 3f
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val maxOffsetX = (size.width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (targetScale - 1f)) / 2f
                                val targetOffsetX = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX)
                                val targetOffsetY = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)
                                launch { scale.animateTo(targetScale, animationSpec = tween(300)) }
                                launch { offsetX.animateTo(targetOffsetX, animationSpec = tween(300)) }
                                launch { offsetY.animateTo(targetOffsetY, animationSpec = tween(300)) }
                                onZoomChanged(true)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                    coroutineScope.launch {
                        val currentScale = scale.value
                        val newScale = (currentScale * zoom).coerceIn(1f, 5f)

                        val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (size.height * (newScale - 1f)) / 2f

                        val newOffsetX = if (newScale > 1f) {
                            (offsetX.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        } else {
                            0f
                        }

                        val newOffsetY = if (newScale > 1f) {
                            (offsetY.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            0f
                        }

                        scale.snapTo(newScale)
                        offsetX.snapTo(newOffsetX)
                        offsetY.snapTo(newOffsetY)

                        onZoomChanged(newScale > 1f)
                    }
                }
            }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offsetX.value,
                    translationY = offsetY.value
                )
        )
    }
}
