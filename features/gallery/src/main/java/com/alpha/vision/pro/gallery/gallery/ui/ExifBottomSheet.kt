package com.alpha.vision.pro.gallery.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.alpha.vision.pro.gallery.designsystem.components.DragHandle
import com.alpha.vision.pro.gallery.designsystem.components.ExifChip
import com.alpha.vision.pro.gallery.domain.model.ExifData
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifBottomSheet(
    item           : MediaItem,
    exifDataLoader : suspend (Long) -> ExifData?,
    onDismiss      : () -> Unit,
    onStripExif    : () -> Unit
) {
    var exifData by remember { mutableStateOf<ExifData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoading = true
        exifData = exifDataLoader(item.id)
        isLoading = false
    }

    val exifMap = remember(exifData) {
        buildMap {
            val ex = exifData ?: return@buildMap
            ex.make?.let { put("Make", it) }
            ex.model?.let { put("Model", it) }
            ex.focalLength?.let { put("Focal Length", it) }
            ex.aperture?.let { put("Aperture", "f/$it") }
            ex.shutterSpeed?.let { put("Shutter", it) }
            ex.iso?.let { put("ISO", it) }
            ex.dateTaken?.let { put("Date", it) }
            if (ex.gpsLatitude != null && ex.gpsLongitude != null) {
                put("Lat", "%.5f".format(ex.gpsLatitude))
                put("Lon", "%.5f".format(ex.gpsLongitude))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DragHandle()
            Spacer(Modifier.height(8.dp))
            Text("EXIF Metadata", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (exifMap.isEmpty()) {
                Text("No EXIF data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(exifMap.entries.toList().size) { idx ->
                        val entry = exifMap.entries.toList()[idx]
                        ExifChip(label = entry.key, value = entry.value,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStripExif,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Strip All EXIF Data")
            }
        }
    }
}
