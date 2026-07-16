package com.alpha.vision.pro.gallery.settings.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alpha.vision.pro.gallery.domain.model.ColorSpace
import com.alpha.vision.pro.gallery.domain.model.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alpha_vision_prefs")

@Singleton
class DataStorePrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DARK_MODE          = booleanPreferencesKey("dark_mode")
        val DARK_MODE_SET      = booleanPreferencesKey("dark_mode_set")
        val DYNAMIC_COLOR      = booleanPreferencesKey("dynamic_color")
        val COLOR_SPACE        = stringPreferencesKey("color_space")
        val GPU_ACCELERATION   = booleanPreferencesKey("gpu_acceleration")
        val BUFFER_SIZE_MB     = intPreferencesKey("buffer_size_mb")
        val AUTO_CLEAR_DAYS    = intPreferencesKey("auto_clear_days")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppPreferences(
                darkMode           = if (prefs[Keys.DARK_MODE_SET] == true) prefs[Keys.DARK_MODE] else null,
                dynamicColor       = prefs[Keys.DYNAMIC_COLOR]    ?: true,
                colorSpace         = prefs[Keys.COLOR_SPACE]?.let {
                    runCatching { ColorSpace.valueOf(it) }.getOrDefault(ColorSpace.SRGB)
                } ?: ColorSpace.SRGB,
                gpuAcceleration    = prefs[Keys.GPU_ACCELERATION]  ?: true,
                nativeBufferSizeMb = prefs[Keys.BUFFER_SIZE_MB]    ?: 64,
                autoClearTrashDays = prefs[Keys.AUTO_CLEAR_DAYS]   ?: 30
            )
        }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            if (enabled == null) {
                prefs.remove(Keys.DARK_MODE)
                prefs[Keys.DARK_MODE_SET] = false
            } else {
                prefs[Keys.DARK_MODE]     = enabled
                prefs[Keys.DARK_MODE_SET] = true
            }
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setColorSpace(cs: ColorSpace) {
        context.dataStore.edit { it[Keys.COLOR_SPACE] = cs.name }
    }

    suspend fun setGpuAcceleration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GPU_ACCELERATION] = enabled }
    }

    suspend fun setBufferSizeMb(mb: Int) {
        context.dataStore.edit { it[Keys.BUFFER_SIZE_MB] = mb }
    }

    suspend fun setAutoClearDays(days: Int) {
        context.dataStore.edit { it[Keys.AUTO_CLEAR_DAYS] = days }
    }
}
