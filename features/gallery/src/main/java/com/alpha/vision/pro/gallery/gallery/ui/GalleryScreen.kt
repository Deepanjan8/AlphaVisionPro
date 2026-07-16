package com.alpha.vision.pro.gallery.gallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alpha.vision.pro.gallery.designsystem.components.AlphaTopBar
import com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryEvent
import com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryViewModel
import com.alpha.vision.pro.gallery.gallery.viewmodel.SortType
import com.alpha.vision.pro.gallery.gallery.viewmodel.FilterType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onMediaClick   : (Long) -> Unit,
    onVaultClick   : () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel      : GalleryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior    = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(GalleryEvent.DismissSnack)
        }
    }

    Scaffold(
        modifier     = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = state.isSelectionMode,
                label       = "topbar"
            ) { selMode ->
                if (selMode) {
                    SelectionTopBar(
                        count          = state.selectedIds.size,
                        onClear        = { viewModel.onEvent(GalleryEvent.ClearSelection) },
                        onDelete       = { viewModel.onEvent(GalleryEvent.DeleteSelected) },
                        onVault        = { viewModel.onEvent(GalleryEvent.MoveSelectedToVault) }
                    )
                } else {
                    AlphaTopBar(
                        title          = "Gallery",
                        scrollBehavior = scrollBehavior,
                        actions        = {
                            // Filter Button & Dropdown
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false }
                                ) {
                                    FilterType.values().forEach { filterType ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = when (filterType) {
                                                        FilterType.ALL -> "All Media"
                                                        FilterType.IMAGES -> "Images"
                                                        FilterType.VIDEOS -> "Videos"
                                                        FilterType.GIFS -> "GIFs"
                                                        FilterType.SCREENSHOTS -> "Screenshots"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                viewModel.onEvent(GalleryEvent.ChangeFilterType(filterType))
                                                showFilterMenu = false
                                            },
                                            leadingIcon = {
                                                if (state.filterType == filterType) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Sort Button & Dropdown
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Filled.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortType.values().forEach { sortType ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = when (sortType) {
                                                        SortType.DATE -> "Date"
                                                        SortType.NAME -> "Name"
                                                        SortType.SIZE -> "Size"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                viewModel.onEvent(GalleryEvent.ChangeSortType(sortType))
                                                showSortMenu = false
                                            },
                                            leadingIcon = {
                                                if (state.sortType == sortType) {
                                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = onVaultClick) {
                                Icon(Icons.Filled.Lock, contentDescription = "Vault")
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text     = state.error ?: "",
                    modifier = Modifier.align(Alignment.Center),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.error
                )
                else -> {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val screenWidthDp = configuration.screenWidthDp
                    val adaptiveColumns = remember(screenWidthDp, state.gridColumns) {
                        val ratio = screenWidthDp.toFloat() / 360f
                        (state.gridColumns * ratio).toInt().coerceIn(2, 8)
                    }
                    GalleryGrid(
                        items       = state.mediaItems,
                        selectedIds = state.selectedIds,
                        columns     = adaptiveColumns,
                        onItemClick = { id ->
                            if (state.isSelectionMode) viewModel.onEvent(GalleryEvent.TapItem(id))
                            else onMediaClick(id)
                        },
                        onItemLongPress = { id -> viewModel.onEvent(GalleryEvent.LongPressItem(id)) },
                        onPinchZoom     = { scale -> viewModel.onEvent(GalleryEvent.PinchZoom(scale)) },
                        onStripExif     = { id -> viewModel.onEvent(GalleryEvent.StripExif(id)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count   : Int,
    onClear : () -> Unit,
    onDelete: () -> Unit,
    onVault : () -> Unit
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            IconButton(onClick = onVault) {
                Icon(Icons.Filled.Lock, contentDescription = "Move to vault")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}
