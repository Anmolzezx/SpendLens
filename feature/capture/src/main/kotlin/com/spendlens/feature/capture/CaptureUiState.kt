package com.spendlens.feature.capture

import com.spendlens.core.ocr.ParsedReceipt

sealed interface CaptureUiState {
    /** Viewfinder is live, waiting for the shutter. */
    data object Ready : CaptureUiState

    /** Shutter pressed; the photo is being written and read. Blocks a second press. */
    data object Processing : CaptureUiState

    /**
     * Recognition finished. [receipt] may be entirely empty — a blurred or blank photo is a normal
     * outcome, not an error, and the review form opens either way.
     */
    data class Recognized(
        val receipt: ParsedReceipt,
        val imagePath: String,
    ) : CaptureUiState

    /** The capture itself failed — no camera, no storage, hardware error. */
    data class Failed(
        val reason: CaptureFailure,
    ) : CaptureUiState
}

enum class CaptureFailure {
    CaptureFailed,
    RecognitionFailed,
}
