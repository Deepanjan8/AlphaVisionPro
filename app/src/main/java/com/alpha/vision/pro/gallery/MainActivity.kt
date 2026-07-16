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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val prefs by settingsVm.preferences.collectAsState()
            AlphaVisionTheme(
                darkTheme    = prefs.darkMode ?: isSystemInDarkTheme(),
                dynamicColor = prefs.dynamicColor,
                colorSpace   = prefs.colorSpace
            ) {
                AppNavGraph()
            }
        }
    }
}
