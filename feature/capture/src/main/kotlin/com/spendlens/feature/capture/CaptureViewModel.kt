package com.spendlens.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.core.data.receipt.ReceiptImageStore
import com.spendlens.core.ocr.ParsedReceipt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel
    @Inject
    constructor(
        private val recognizer: MlKitReceiptRecognizer,
        private val imageStore: ReceiptImageStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Ready)
        val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

        fun newImageFile(): File = imageStore.newImageFile()

        fun onCaptureStarted() {
            _uiState.value = CaptureUiState.Processing
        }

        fun onCaptureFailed() {
            _uiState.value = CaptureUiState.Failed(CaptureFailure.CaptureFailed)
        }

        /**
         * Recognition failing is different from recognising nothing.
         *
         * A blank result still opens the review form — the user photographed something and can type
         * what the parser missed. Only a thrown error is a failure state.
         */
        fun onImageCaptured(file: File) {
            viewModelScope.launch {
                val result = runCatching { recognizer.recognize(file) }
                _uiState.value = result.fold(
                    onSuccess = { receipt ->
                        CaptureUiState.Recognized(
                            receipt = receipt,
                            imagePath = imageStore.relativePathOf(file),
                        )
                    },
                    onFailure = {
                        // The photo is on disk but unreadable to us; do not leave it orphaned.
                        imageStore.deleteQuietly(file)
                        CaptureUiState.Failed(CaptureFailure.RecognitionFailed)
                    },
                )
            }
        }

        /** Discard a capture the user backed out of, so failed attempts do not accumulate. */
        fun discard(imagePath: String?) {
            imagePath ?: return
            viewModelScope.launch { imageStore.delete(imagePath) }
        }

        fun retry() {
            _uiState.value = CaptureUiState.Ready
        }

        fun parsedOrEmpty(): ParsedReceipt =
            (_uiState.value as? CaptureUiState.Recognized)?.receipt ?: ParsedReceipt.EMPTY
    }
