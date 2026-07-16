package com.alpha.vision.pro.gallery.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.vision.pro.gallery.domain.model.ColorSpace
import com.alpha.vision.pro.gallery.domain.model.AppPreferences
import com.alpha.vision.pro.gallery.settings.datastore.DataStorePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStorePrefs
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = dataStore.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    fun setDarkMode(enabled: Boolean?)      = viewModelScope.launch { dataStore.setDarkMode(enabled) }
    fun setDynamicColor(enabled: Boolean)   = viewModelScope.launch { dataStore.setDynamicColor(enabled) }
    fun setColorSpace(cs: ColorSpace)       = viewModelScope.launch { dataStore.setColorSpace(cs) }
    fun setGpuAcceleration(enabled: Boolean)= viewModelScope.launch { dataStore.setGpuAcceleration(enabled) }
    fun setBufferSizeMb(mb: Int)            = viewModelScope.launch { dataStore.setBufferSizeMb(mb) }
    fun setAutoClearDays(days: Int)         = viewModelScope.launch { dataStore.setAutoClearDays(days) }
}
