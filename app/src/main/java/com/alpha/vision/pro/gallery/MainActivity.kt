package com.alpha.vision.pro.gallery

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.alpha.vision.pro.gallery.designsystem.theme.AlphaVisionTheme
import com.alpha.vision.pro.gallery.navigation.AppNavGraph
import com.alpha.vision.pro.gallery.settings.viewmodel.SettingsViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var mediaRepository: com.alpha.vision.pro.gallery.domain.repository.MediaRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            lifecycleScope.launch {
                mediaRepository.sync()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        checkAndRequestStoragePermission()

        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val prefs by settingsVm.preferences.collectAsState()
            val themeColorSpace = when (prefs.colorSpace) {
                com.alpha.vision.pro.gallery.domain.model.ColorSpace.SRGB -> com.alpha.vision.pro.gallery.designsystem.theme.ColorSpace.SRGB
                com.alpha.vision.pro.gallery.domain.model.ColorSpace.DISPLAY_P3 -> com.alpha.vision.pro.gallery.designsystem.theme.ColorSpace.DISPLAY_P3
            }
            AlphaVisionTheme(
                darkTheme    = prefs.darkMode ?: isSystemInDarkTheme(),
                dynamicColor = prefs.dynamicColor,
                colorSpace   = themeColorSpace
            ) {
                AppNavGraph()
            }
        }
    }

    private fun checkAndRequestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }
}
