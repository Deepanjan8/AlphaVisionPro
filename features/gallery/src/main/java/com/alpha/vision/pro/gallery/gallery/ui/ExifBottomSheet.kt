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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.alpha.vision.pro.gallery.designsystem.components.DragHandle
import com.alpha.vision.pro.gallery.designsystem.components.ExifChip
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifBottomSheet(
    item       : MediaItem,
    onDismiss  : () -> Unit,
    onStripExif: () -> Unit
) {
    val context = LocalContext.current
    // Read EXIF from URI
    val exifMap = remember(item.uri) {
        buildMap {
            try {
                val uri = Uri.parse(item.uri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val ex = ExifInterface(inputStream)
                    ex.getAttribute(ExifInterface.TAG_MAKE)?.let { put("Make", it) }
                    ex.getAttribute(ExifInterface.TAG_MODEL)?.let { put("Model", it) }
                    ex.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { put("Focal Length", it) }
                    ex.getAttribute(ExifInterface.TAG_APERTURE_VALUE)?.let { put("Aperture", it) }
                    ex.getAttribute(ExifInterface.TAG_SHUTTER_SPEED_VALUE)?.let { put("Shutter", it) }
                    ex.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { put("ISO", it) }
                    ex.getAttribute(ExifInterface.TAG_DATETIME)?.let { put("Date", it) }
                    
                    val latLong = FloatArray(2)
                    if (ex.getLatLong(latLong)) {
                        put("Lat", "%.5f".format(latLong[0]))
                        put("Lon", "%.5f".format(latLong[1]))
                    }
                }
            } catch (_: Exception) {}
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

            if (exifMap.isEmpty()) {
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
