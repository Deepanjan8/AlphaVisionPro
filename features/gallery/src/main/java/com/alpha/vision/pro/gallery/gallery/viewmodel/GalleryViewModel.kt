package com.alpha.vision.pro.gallery.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import com.alpha.vision.pro.gallery.domain.usecase.DeleteMediaUseCase
import com.alpha.vision.pro.gallery.domain.usecase.MoveToVaultUseCase
import com.alpha.vision.pro.gallery.domain.usecase.ObserveAllMediaUseCase
import com.alpha.vision.pro.gallery.domain.usecase.StripExifUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortType {
    DATE, NAME, SIZE
}

enum class FilterType {
    ALL, IMAGES, VIDEOS, GIFS, SCREENSHOTS
}

data class GalleryUiState(
    val mediaItems    : List<MediaItem> = emptyList(),
    val selectedIds   : Set<Long>       = emptySet(),
    val isLoading     : Boolean         = true,
    val error         : String?         = null,
    val gridColumns   : Int             = 3,        // pinch-to-zoom target: 2-5
    val isSelectionMode: Boolean        = false,
    val snackMessage  : String?         = null,
    val sortType      : SortType        = SortType.DATE,
    val filterType    : FilterType      = FilterType.ALL
)

sealed interface GalleryEvent {
    data class LongPressItem(val id: Long) : GalleryEvent
    data class TapItem(val id: Long)       : GalleryEvent
    data class PinchZoom(val scale: Float) : GalleryEvent
    data object DeleteSelected             : GalleryEvent
    data object MoveSelectedToVault        : GalleryEvent
    data class  StripExif(val id: Long)   : GalleryEvent
    data object ClearSelection             : GalleryEvent
    data object DismissSnack               : GalleryEvent
    data class ChangeSortType(val sortType: SortType) : GalleryEvent
    data class ChangeFilterType(val filterType: FilterType) : GalleryEvent
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val observeAllMedia : ObserveAllMediaUseCase,
    private val deleteMedia     : DeleteMediaUseCase,
    private val moveToVault     : MoveToVaultUseCase,
    private val stripExif       : StripExifUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    private val sortType = MutableStateFlow(SortType.DATE)
    private val filterType = MutableStateFlow(FilterType.ALL)

    init {
        combine(
            observeAllMedia().catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } },
            sortType,
            filterType
        ) { items, sort, filter ->
            val processed = filterAndSort(items, filter, sort)
            _state.update {
                it.copy(
                    mediaItems = processed,
                    sortType = sort,
                    filterType = filter,
                    isLoading = false
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun filterAndSort(
        items: List<MediaItem>,
        filter: FilterType,
        sort: SortType
    ): List<MediaItem> {
        val filtered = when (filter) {
            FilterType.ALL -> items
            FilterType.IMAGES -> items.filter { it.mimeType.startsWith("image/") }
            FilterType.VIDEOS -> items.filter { it.mimeType.startsWith("video/") }
            FilterType.GIFS -> items.filter { it.mimeType.contains("gif") || it.displayName.endsWith(".gif", ignoreCase = true) }
            FilterType.SCREENSHOTS -> items.filter {
                it.displayName.contains("screenshot", ignoreCase = true) ||
                it.bucketName.contains("screenshot", ignoreCase = true)
            }
        }

        return when (sort) {
            SortType.DATE -> filtered.sortedByDescending { it.dateModified.takeIf { t -> t > 0 } ?: it.dateAdded }
            SortType.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
            SortType.SIZE -> filtered.sortedByDescending { it.size }
        }
    }

    fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.LongPressItem -> handleLongPress(event.id)
            is GalleryEvent.TapItem       -> handleTap(event.id)
            is GalleryEvent.PinchZoom     -> handlePinch(event.scale)
            is GalleryEvent.DeleteSelected-> handleDelete()
            is GalleryEvent.MoveSelectedToVault -> handleVault()
            is GalleryEvent.StripExif     -> handleStripExif(event.id)
            is GalleryEvent.ClearSelection-> _state.update {
                it.copy(selectedIds = emptySet(), isSelectionMode = false)
            }
            is GalleryEvent.DismissSnack  -> _state.update { it.copy(snackMessage = null) }
            is GalleryEvent.ChangeSortType -> {
                sortType.value = event.sortType
            }
            is GalleryEvent.ChangeFilterType -> {
                filterType.value = event.filterType
            }
        }
    }

    private fun handleLongPress(id: Long) {
        _state.update { s ->
            val newSel = s.selectedIds + id
            s.copy(selectedIds = newSel, isSelectionMode = true)
        }
    }

    private fun handleTap(id: Long) {
        val s = _state.value
        if (!s.isSelectionMode) return // handled by NavGraph click
        val newSel = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
        _state.update { it.copy(
            selectedIds     = newSel,
            isSelectionMode = newSel.isNotEmpty()
        )}
    }

    private fun handlePinch(scale: Float) {
        val current = _state.value.gridColumns
        val newCols = when {
            scale < 0.85f -> (current + 1).coerceAtMost(5)
            scale > 1.15f -> (current - 1).coerceAtLeast(2)
            else          -> current
        }
        if (newCols != current) _state.update { it.copy(gridColumns = newCols) }
    }

    private fun handleDelete() = viewModelScope.launch {
        val ids = _state.value.selectedIds.toList()
        deleteMedia(ids)
            .onSuccess {
                _state.update { it.copy(
                    selectedIds    = emptySet(),
                    isSelectionMode= false,
                    snackMessage   = "${ids.size} item(s) moved to trash"
                )}
            }
            .onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
    }

    private fun handleVault() = viewModelScope.launch {
        _state.value.selectedIds.forEach { id ->
            moveToVault(id)
        }
        _state.update { it.copy(
            selectedIds    = emptySet(),
            isSelectionMode= false,
            snackMessage   = "Items moved to Vault"
        )}
    }

    private fun handleStripExif(id: Long) = viewModelScope.launch {
        stripExif(id)
            .onSuccess { _state.update { it.copy(snackMessage = "EXIF data removed") } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }
}
