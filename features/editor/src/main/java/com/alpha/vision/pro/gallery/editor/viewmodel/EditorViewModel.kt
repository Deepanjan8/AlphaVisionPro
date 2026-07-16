package com.alpha.vision.pro.gallery.editor.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.vision.pro.gallery.domain.model.EditParams
import com.alpha.vision.pro.gallery.domain.model.MediaItem
import com.alpha.vision.pro.gallery.domain.usecase.ApplyEditParamsUseCase
import com.alpha.vision.pro.gallery.domain.usecase.GetMediaByIdUseCase
import com.alpha.vision.pro.gallery.domain.usecase.SaveEditedMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val media          : MediaItem?  = null,
    val params         : EditParams  = EditParams(),
    val previewBitmap  : Bitmap?     = null,
    val isProcessing   : Boolean     = false,
    val isSaving       : Boolean     = false,
    val isLoading      : Boolean     = true,
    val error          : String?     = null,
    val savedSuccessfully: Boolean   = false
)

sealed interface EditorEvent {
    data class SetExposure  (val v: Float) : EditorEvent
    data class SetContrast  (val v: Float) : EditorEvent
    data class SetSaturation(val v: Float) : EditorEvent
    data class SetWarmth    (val v: Float) : EditorEvent
    data class SetHighlights(val v: Float) : EditorEvent
    data class SetShadows   (val v: Float) : EditorEvent
    data class SetSharpness (val v: Float) : EditorEvent
    data class SourceLoaded (val bmp: Bitmap) : EditorEvent
    data object Reset       : EditorEvent
    data object Save        : EditorEvent
    data object DismissError: EditorEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle  : SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val getMedia      : GetMediaByIdUseCase,
    private val applyEdit     : ApplyEditParamsUseCase,
    private val saveEdited    : SaveEditedMediaUseCase
) : ViewModel() {

    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    // Source bitmap held separately — not in UI state to avoid recomposition cost
    private var sourceBitmap: Bitmap? = null

    // Debounced params flow to avoid hammering C++ on every slider tick
    private val paramsFlow = MutableSharedFlow<EditParams>(replay = 1)

    init {
        loadMedia()
        observeParamsForProcessing()
    }

    private fun loadMedia() = viewModelScope.launch {
        getMedia(mediaId)
            .onSuccess { media ->
                _state.update { it.copy(media = media, isLoading = false) }
                loadSourceBitmap(media.uri)
            }
            .onFailure { e   -> _state.update { it.copy(error = e.message, isLoading = false) } }
    }

    private fun loadSourceBitmap(uriString: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(uriString)
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        android.graphics.BitmapFactory.decodeStream(inputStream)?.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    } ?: error("Failed to open input stream")
                }
                bitmap
            }.onSuccess { bmp ->
                onEvent(EditorEvent.SourceLoaded(bmp))
            }.onFailure { e ->
                _state.update { it.copy(error = "Failed to load image: ${e.message}") }
            }
        }
    }

    private fun observeParamsForProcessing() = viewModelScope.launch {
        paramsFlow
            .debounce(120L)   // 120 ms debounce — smooth slider, not spammy
            .collect { params ->
                val src = sourceBitmap ?: return@collect
                _state.update { it.copy(isProcessing = true) }
                applyEdit(mediaId.toString(), src, params)
                    .onSuccess { bmp ->
                        _state.update { it.copy(previewBitmap = bmp, isProcessing = false) }
                    }
                    .onFailure { e ->
                        _state.update { it.copy(error = e.message, isProcessing = false) }
                    }
            }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.SetExposure   -> updateParam { it.copy(exposure   = event.v) }
            is EditorEvent.SetContrast   -> updateParam { it.copy(contrast   = event.v) }
            is EditorEvent.SetSaturation -> updateParam { it.copy(saturation = event.v) }
            is EditorEvent.SetWarmth     -> updateParam { it.copy(warmth     = event.v) }
            is EditorEvent.SetHighlights -> updateParam { it.copy(highlights = event.v) }
            is EditorEvent.SetShadows    -> updateParam { it.copy(shadows    = event.v) }
            is EditorEvent.SetSharpness  -> updateParam { it.copy(sharpness  = event.v) }
            is EditorEvent.SourceLoaded  -> {
                sourceBitmap = event.bmp
                viewModelScope.launch { paramsFlow.emit(_state.value.params) }
            }
            is EditorEvent.Reset         -> {
                _state.update { it.copy(params = EditParams()) }
                viewModelScope.launch { paramsFlow.emit(EditParams()) }
            }
            is EditorEvent.Save          -> handleSave()
            is EditorEvent.DismissError  -> _state.update { it.copy(error = null) }
        }
    }

    private fun updateParam(transform: (EditParams) -> EditParams) {
        val newParams = transform(_state.value.params)
        _state.update { it.copy(params = newParams) }
        viewModelScope.launch { paramsFlow.emit(newParams) }
    }

    private fun handleSave() = viewModelScope.launch {
        val bitmap = _state.value.previewBitmap ?: sourceBitmap ?: return@launch
        _state.update { it.copy(isSaving = true) }
        saveEdited(mediaId, bitmap)
            .onSuccess { _state.update { it.copy(isSaving = false, savedSuccessfully = true) } }
            .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
    }
}
