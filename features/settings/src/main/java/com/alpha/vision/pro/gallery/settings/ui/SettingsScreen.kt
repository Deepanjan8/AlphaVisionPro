package com.alpha.vision.pro.gallery.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alpha.vision.pro.gallery.designsystem.components.AlphaTopBar
import com.alpha.vision.pro.gallery.domain.model.ColorSpace
import com.alpha.vision.pro.gallery.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack   : () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            AlphaTopBar(
                title = "Settings",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .fillMaxSize()
                .padding(bottom = 24.dp)
        ) {
            // ── Appearance ───────────────────────────────────────────────
            SettingsSectionHeader("Appearance")

            SettingsToggle(
                icon    = Icons.Filled.DarkMode,
                label   = "Dark Mode",
                checked = prefs.darkMode ?: false,
                onToggle= { viewModel.setDarkMode(if (it) true else null) }
            )
            SettingsToggle(
                icon    = Icons.Filled.Palette,
                label   = "Dynamic Color (Material You)",
                checked = prefs.dynamicColor,
                onToggle= { viewModel.setDynamicColor(it) }
            )

            // Color Space picker
            SettingsSectionHeader("Color Science")
            Text(
                text     = "Color Space",
                style    = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSpace.entries.forEach { cs ->
                    FilterChip(
                        selected = prefs.colorSpace == cs,
                        onClick  = { viewModel.setColorSpace(cs) },
                        label    = { Text(cs.name) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Performance ───────────────────────────────────────────────
            SettingsSectionHeader("Performance")

            SettingsToggle(
                icon    = Icons.Filled.Speed,
                label   = "GPU Acceleration",
                checked = prefs.gpuAcceleration,
                onToggle= { viewModel.setGpuAcceleration(it) }
            )

            // Buffer size
            Text(
                text     = "Native Buffer Size: ${prefs.nativeBufferSizeMb} MB",
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Slider(
                value         = prefs.nativeBufferSizeMb.toFloat(),
                onValueChange = { viewModel.setBufferSizeMb(it.toInt()) },
                valueRange    = 32f..256f,
                steps         = 6,  // 32, 64, 96, 128, 160, 192, 224, 256
                modifier      = Modifier.padding(horizontal = 20.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Storage ───────────────────────────────────────────────────
            SettingsSectionHeader("Storage")

            Text(
                text     = "Auto-clear Trash: ${prefs.autoClearTrashDays} days",
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Slider(
                value         = prefs.autoClearTrashDays.toFloat(),
                onValueChange = { viewModel.setAutoClearDays(it.toInt()) },
                valueRange    = 7f..90f,
                steps         = 11, // 7, 14, 21, 30, 37, 44, 51, 58, 65, 72, 79, 86, 90
                modifier      = Modifier.padding(horizontal = 20.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── About ─────────────────────────────────────────────────────
            SettingsSectionHeader("About")
            ListItem(
                headlineContent  = { Text("Alpha Vision Pro") },
                supportingContent= { Text("Version 1.0.0 • com.alpha.vision.pro.gallery") },
                leadingContent   = {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggle(
    icon    : ImageVector,
    label   : String,
    checked : Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent  = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked         = checked,
                onCheckedChange = onToggle
            )
        },
        modifier = Modifier.clickable { onToggle(!checked) }
    )
}
