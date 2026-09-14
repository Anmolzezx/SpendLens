package com.spendlens.feature.capture

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import com.spendlens.core.ocr.ParsedReceipt
import com.spendlens.core.ocr.ReceiptParser
import com.spendlens.core.ocr.RecognizedLine
import com.spendlens.core.ocr.RecognizedText
import com.spendlens.core.ocr.verticalPositionOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The only class in the project that knows ML Kit exists.
 *
 * Everything interesting — the merchant, total and date heuristics — lives in `core:ocr` behind a
 * plain [RecognizedText], so it is unit-tested on the JVM. This adapter is deliberately thin enough
 * to verify by reading, because ML Kit's [Text] is effectively impossible to construct in a test.
 */
class MlKitReceiptRecognizer
    @Inject
    constructor(
        private val parser: ReceiptParser,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        suspend fun recognize(imageFile: File): ParsedReceipt =
            withContext(ioDispatcher) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    ?: return@withContext ParsedReceipt.EMPTY

                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                parser.parse(text.toRecognizedText(imageHeight = bitmap.height))
            }
    }

/**
 * Flattens ML Kit's block → line hierarchy into a flat, top-to-bottom list.
 *
 * Sorting matters: ML Kit returns blocks in reading order per column, so a two-column receipt can
 * interleave. The parser's "merchant is near the top, total is near the bottom" rules only hold if
 * the lines really are ordered down the page.
 */
internal fun Text.toRecognizedText(imageHeight: Int): RecognizedText =
    RecognizedText(
        lines = textBlocks
            .flatMap { it.lines }
            .map { line ->
                val box = line.boundingBox
                RecognizedLine(
                    text = line.text,
                    verticalPosition = verticalPositionOf(
                        boxTop = box?.top ?: 0,
                        boxBottom = box?.bottom ?: 0,
                        imageHeight = imageHeight,
                    ),
                )
            }.sortedBy { it.verticalPosition },
    )

/** Bridges a Play Services [com.google.android.gms.tasks.Task] into a cancellable coroutine. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
