package com.alpha.vision.pro.gallery.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alpha.vision.pro.gallery.designsystem.components.SelectionOverlay
import com.alpha.vision.pro.gallery.domain.model.ExifData
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGrid(
    items          : List<MediaItem>,
    selectedIds    : Set<Long>,
    columns        : Int,
    onItemClick    : (Long) -> Unit,
    onItemLongPress: (Long) -> Unit,
    onPinchZoom    : (Float) -> Unit,
    onStripExif    : (Long) -> Unit,
    exifDataLoader : suspend (Long) -> ExifData?,
    modifier       : Modifier = Modifier,
    onHeaderContent: (LazyGridScope.() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var exifTarget by remember { mutableStateOf<MediaItem?>(null) }

    val contentPadding = if (columns == 1) PaddingValues(8.dp) else PaddingValues(2.dp)
    val verticalArrangement = if (columns == 1) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(2.dp)
    val horizontalArrangement = if (columns == 1) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(2.dp)

    LazyVerticalGrid(
        columns       = GridCells.Fixed(columns),
        modifier      = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var scaleAccumulator = 1f
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom == 1f) {
                        scaleAccumulator = 1f
                    } else {
                        scaleAccumulator *= zoom
                        if (scaleAccumulator < 0.75f) {
                            onPinchZoom(0.7f) // increase columns
                            scaleAccumulator = 1f
                        } else if (scaleAccumulator > 1.35f) {
                            onPinchZoom(1.3f) // decrease columns
                            scaleAccumulator = 1f
                        }
                    }
                }
            },
        contentPadding        = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement   = verticalArrangement
    ) {
        if (onHeaderContent != null) {
            onHeaderContent()
        }

        items(
            items = items,
            key   = { it.id }
        ) { item ->
            val isSelected = item.id in selectedIds
            if (columns == 1) {
                // Comfortable single-image detail/view card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick      = { onItemClick(item.id) },
                            onLongClick  = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onItemLongPress(item.id)
                            }
                        )
                        .animateItem(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                        ) {
                            AsyncImage(
                                model            = item.uri,
                                contentDescription = item.displayName,
                                contentScale     = ContentScale.Crop,
                                modifier         = Modifier.fillMaxSize()
                            )
                            SelectionOverlay(
                                selected = isSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick  = { exifTarget = item },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Filled.Info,
                                    contentDescription = "View EXIF",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val formattedDate = remember(item.dateModified, item.dateAdded) {
                                    val time = if (item.dateModified > 0) item.dateModified else item.dateAdded
                                    SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                                        .format(Date(time * 1000))
                                }
                                val formattedSize = remember(item.size) {
                                    val kb = item.size / 1024.0
                                    val mb = kb / 1024.0
                                    if (mb >= 1.0) "%.1f MB".format(mb) else "%.1f KB".format(kb)
                                }
                                Text(
                                    text = "$formattedDate  |  $formattedSize",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.bucketName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Normal grid square crop
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .combinedClickable(
                            onClick      = { onItemClick(item.id) },
                            onLongClick  = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onItemLongPress(item.id)
                            }
                        )
                        .animateItem()
                ) {
                    AsyncImage(
                        model            = item.uri,
                        contentDescription = item.displayName,
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize()
                    )
                    SelectionOverlay(
                        selected = isSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick  = { exifTarget = item },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Info,
                            contentDescription = "View EXIF",
                            tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    exifTarget?.let { item ->
        ExifBottomSheet(
            item           = item,
            exifDataLoader = exifDataLoader,
            onDismiss      = { exifTarget = null },
            onStripExif    = {
                onStripExif(item.id)
                exifTarget = null
            }
        )
    }
}
