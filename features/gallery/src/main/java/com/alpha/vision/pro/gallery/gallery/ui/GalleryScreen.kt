package com.alpha.vision.pro.gallery.gallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.alpha.vision.pro.gallery.designsystem.components.AlphaTopBar
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import com.alpha.vision.pro.gallery.gallery.viewmodel.FilterType
import com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryEvent
import com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryViewModel
import com.alpha.vision.pro.gallery.gallery.viewmodel.SortType
import kotlinx.coroutines.delay

enum class GalleryTab {
    PHOTOS, SEARCH, LIBRARY
}

data class StoryItem(
    val title: String,
    val mediaItem: MediaItem
)

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
    
    var currentTab by remember { mutableStateOf(GalleryTab.PHOTOS) }
    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }

    val stories = remember(state.mediaItems) {
        if (state.mediaItems.isEmpty()) emptyList()
        else {
            val titles = listOf("Recent Highlights", "Flashback", "One Year Ago", "Best of Summer", "Favorites")
            state.mediaItems.take(5).mapIndexed { index, mediaItem ->
                StoryItem(titles.getOrElse(index) { "Memory" }, mediaItem)
            }
        }
    }

    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(GalleryEvent.DismissSnack)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    count    = state.selectedIds.size,
                    onClear  = { viewModel.onEvent(GalleryEvent.ClearSelection) },
                    onDelete = { viewModel.onEvent(GalleryEvent.DeleteSelected) },
                    onVault  = { viewModel.onEvent(GalleryEvent.MoveSelectedToVault) }
                )
            } else {
                when (currentTab) {
                    GalleryTab.PHOTOS -> {
                        PhotosSearchBar(
                            searchQuery = state.searchQuery,
                            onQueryChange = { viewModel.onEvent(GalleryEvent.SearchQueryChanged(it)) },
                            onVaultClick = onVaultClick,
                            onSettingsClick = onSettingsClick
                        )
                    }
                    GalleryTab.SEARCH -> {
                        // Embedded in content to keep Google Photos styling clean
                    }
                    GalleryTab.LIBRARY -> {
                        AlphaTopBar(
                            title = "Library",
                            actions = {
                                IconButton(onClick = onSettingsClick) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == GalleryTab.PHOTOS,
                    onClick = { currentTab = GalleryTab.PHOTOS },
                    icon = { Icon(Icons.Filled.Image, contentDescription = "Photos") },
                    label = { Text("Photos") }
                )
                NavigationBarItem(
                    selected = currentTab == GalleryTab.SEARCH,
                    onClick = { currentTab = GalleryTab.SEARCH },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = currentTab == GalleryTab.LIBRARY,
                    onClick = { currentTab = GalleryTab.LIBRARY },
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = "Library") },
                    label = { Text("Library") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                GalleryTab.PHOTOS -> {
                    PhotosTabContent(
                        state = state,
                        stories = stories,
                        onStoryClick = { activeStoryIndex = it },
                        onMediaClick = { id ->
                            if (state.isSelectionMode) viewModel.onEvent(GalleryEvent.TapItem(id))
                            else onMediaClick(id)
                        },
                        onItemLongPress = { id -> viewModel.onEvent(GalleryEvent.LongPressItem(id)) },
                        onPinchZoom = { scale -> viewModel.onEvent(GalleryEvent.PinchZoom(scale)) },
                        onStripExif = { id -> viewModel.onEvent(GalleryEvent.StripExif(id)) },
                        onFilterChange = { viewModel.onEvent(GalleryEvent.ChangeFilterType(it)) },
                        onSortChange = { viewModel.onEvent(GalleryEvent.ChangeSortType(it)) }
                    )
                }
                GalleryTab.SEARCH -> {
                    SearchTabContent(
                        state = state,
                        onQueryChange = { viewModel.onEvent(GalleryEvent.SearchQueryChanged(it)) },
                        onMediaClick = { id -> onMediaClick(id) },
                        onFilterChange = { viewModel.onEvent(GalleryEvent.ChangeFilterType(it)) }
                    )
                }
                GalleryTab.LIBRARY -> {
                    LibraryTabContent(
                        state = state,
                        onVaultClick = onVaultClick,
                        onSettingsClick = onSettingsClick,
                        onMediaClick = onMediaClick
                    )
                }
            }

            // Interactive Story Player Overlay
            activeStoryIndex?.let { initialIdx ->
                StoryPlayer(
                    stories = stories,
                    initialIndex = initialIdx,
                    onDismiss = { activeStoryIndex = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PhotosSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search your gallery",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = onVaultClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Vault",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PhotosTabContent(
    state: com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryUiState,
    stories: List<StoryItem>,
    onStoryClick: (Int) -> Unit,
    onMediaClick: (Long) -> Unit,
    onItemLongPress: (Long) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onStripExif: (Long) -> Unit,
    onFilterChange: (FilterType) -> Unit,
    onSortChange: (SortType) -> Unit
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (state.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = state.error, color = MaterialTheme.colorScheme.error)
        }
    } else {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val adaptiveColumns = remember(screenWidthDp, state.gridColumns) {
            if (state.gridColumns == 1) {
                1
            } else {
                val ratio = screenWidthDp.toFloat() / 360f
                (state.gridColumns * ratio).toInt().coerceIn(2, 8)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            FilterAndSortChipsRow(
                activeFilter = state.filterType,
                activeSort = state.sortType,
                onFilterSelect = onFilterChange,
                onSortSelect = onSortChange
            )
            
            GalleryGrid(
                items = state.mediaItems,
                selectedIds = state.selectedIds,
                columns = adaptiveColumns,
                onItemClick = onMediaClick,
                onItemLongPress = onItemLongPress,
                onPinchZoom = onPinchZoom,
                onStripExif = onStripExif,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onHeaderContent = {
                    if (stories.isNotEmpty() && state.searchQuery.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StoryPreviewRow(stories = stories, onStoryClick = onStoryClick)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun StoryPreviewRow(
    stories: List<StoryItem>,
    onStoryClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Memories",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(stories) { index, story ->
                Card(
                    modifier = Modifier
                        .width(105.dp)
                        .height(155.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStoryClick(index) },
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = story.mediaItem.uri,
                            contentDescription = story.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                        startY = 150f
                                    )
                                )
                        )
                        Text(
                            text = story.title,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterAndSortChipsRow(
    activeFilter: FilterType,
    activeSort: SortType,
    onFilterSelect: (FilterType) -> Unit,
    onSortSelect: (SortType) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showSortMenu = true },
                    label = {
                        Text(
                            text = when (activeSort) {
                                SortType.DATE -> "Sort: Date"
                                SortType.NAME -> "Sort: Name"
                                SortType.SIZE -> "Sort: Size"
                            }
                        )
                    },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortType.entries.forEach { sortType ->
                        DropdownMenuItem(
                            text = { Text(sortType.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onSortSelect(sortType)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (activeSort == sortType) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected")
                                }
                            }
                        )
                    }
                }
            }
        }

        items(FilterType.entries.toList().size) { index ->
            val filterType = FilterType.entries[index]
            val label = when (filterType) {
                FilterType.ALL -> "All Media"
                FilterType.IMAGES -> "Images"
                FilterType.VIDEOS -> "Videos"
                FilterType.GIFS -> "GIFs"
                FilterType.SCREENSHOTS -> "Screenshots"
            }
            FilterChip(
                selected = activeFilter == filterType,
                onClick = { onFilterSelect(filterType) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SearchTabContent(
    state: com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryUiState,
    onQueryChange: (String) -> Unit,
    onMediaClick: (Long) -> Unit,
    onFilterChange: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQueryLocal by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQueryLocal,
            onValueChange = {
                searchQueryLocal = it
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search photos, folders...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQueryLocal.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQueryLocal = ""
                        onQueryChange("")
                     }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            shape = MaterialTheme.shapes.medium
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Text("Quick Categories", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchCategoryChip(title = "Screenshots", icon = Icons.Filled.Smartphone, modifier = Modifier.weight(1f)) {
                onFilterChange(FilterType.SCREENSHOTS)
            }
            SearchCategoryChip(title = "GIFs", icon = Icons.Filled.Gif, modifier = Modifier.weight(1f)) {
                onFilterChange(FilterType.GIFS)
            }
            SearchCategoryChip(title = "Videos", icon = Icons.Filled.Videocam, modifier = Modifier.weight(1f)) {
                onFilterChange(FilterType.VIDEOS)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Results", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        if (state.mediaItems.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.mediaItems, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onMediaClick(item.id) }
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun LibraryTabContent(
    state: com.alpha.vision.pro.gallery.gallery.viewmodel.GalleryUiState,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMediaClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val albums = remember(state.mediaItems) {
        state.mediaItems.groupBy { it.bucketName }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryActionCard(title = "Favorites", icon = Icons.Filled.Favorite, count = 0, modifier = Modifier.weight(1f)) {}
            LibraryActionCard(title = "Vault", icon = Icons.Filled.Lock, count = null, modifier = Modifier.weight(1f), onClick = onVaultClick)
            LibraryActionCard(title = "Settings", icon = Icons.Filled.Settings, count = null, modifier = Modifier.weight(1f), onClick = onSettingsClick)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Device Albums", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No albums found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums.entries.toList(), key = { it.key }) { entry ->
                    val bucketName = entry.key
                    val itemsInBucket = entry.value
                    val coverItem = itemsInBucket.firstOrNull()
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable {
                                coverItem?.let { onMediaClick(it.id) }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                if (coverItem != null) {
                                    AsyncImage(
                                        model = coverItem.uri,
                                        contentDescription = bucketName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.secondary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Folder, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = bucketName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${itemsInBucket.size} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                if (count != null) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryPlayer(
    stories: List<StoryItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeStoryIndex by remember { mutableIntStateOf(initialIndex) }
    var storyProgress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(activeStoryIndex) {
        storyProgress = 0f
    }

    LaunchedEffect(activeStoryIndex, isPaused) {
        if (!isPaused) {
            val duration = 4000f // 4 seconds
            val step = 16f
            while (storyProgress < 1f) {
                delay(step.toLong())
                storyProgress += step / duration
            }
            if (activeStoryIndex < stories.lastIndex) {
                activeStoryIndex++
            } else {
                onDismiss()
            }
        }
    }

    val activeStory = stories.getOrNull(activeStoryIndex) ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = activeStory.mediaItem.uri,
            contentDescription = activeStory.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeStoryIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            try {
                                awaitRelease()
                            } finally {
                                isPaused = false
                            }
                        },
                        onTap = { offset ->
                            val width = size.width
                            if (offset.x < width / 3f) {
                                if (activeStoryIndex > 0) activeStoryIndex--
                                else onDismiss()
                            } else {
                                if (activeStoryIndex < stories.lastIndex) activeStoryIndex++
                                else onDismiss()
                            }
                        }
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    val barProgress = when {
                        idx < activeStoryIndex -> 1f
                        idx == activeStoryIndex -> storyProgress
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(barProgress)
                                .background(Color.White, RoundedCornerShape(1.5.dp))
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeStory.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
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
