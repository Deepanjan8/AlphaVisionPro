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
import com.alpha.vision.pro.gallery.data.nativelib.NativeImageProcessor
import org.json.JSONObject
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
    val exifMap = remember(item.uri) {
        buildMap {
            try {
                val uri = Uri.parse(item.uri)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val processor = NativeImageProcessor()
                    val jsonStr = processor.getExifMetadata(item.uri, bytes)
                    if (jsonStr != null) {
                        val json = JSONObject(jsonStr)
                        if (!json.isNull("make")) put("Make", json.getString("make"))
                        if (!json.isNull("model")) put("Model", json.getString("model"))
                        if (!json.isNull("focalLength")) put("Focal Length", json.getString("focalLength"))
                        if (!json.isNull("aperture")) put("Aperture", "f/${json.getString("aperture")}")
                        if (!json.isNull("shutterSpeed")) put("Shutter", json.getString("shutterSpeed"))
                        if (!json.isNull("iso")) put("ISO", json.getString("iso"))
                        if (!json.isNull("dateTaken")) put("Date", json.getString("dateTaken"))
                        
                        if (!json.isNull("gpsLatitude") && !json.isNull("gpsLongitude")) {
                            put("Lat", "%.5f".format(json.getDouble("gpsLatitude")))
                            put("Lon", "%.5f".format(json.getDouble("gpsLongitude")))
                        }
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
