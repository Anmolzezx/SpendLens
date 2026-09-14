package com.spendlens.core.data.receipt

import android.content.Context
import com.spendlens.core.common.di.Dispatcher
import com.spendlens.core.common.di.SpendLensDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipt images live in app-internal storage.
 *
 * In `core:data` rather than `feature:capture` because two features touch these files: capture writes
 * them, and the expense edit screen must delete one when a scan is abandoned. Features cannot import
 * each other, so the shared piece moves down a layer.
 *
 * Not `MediaStore` and not external storage: a receipt is a financial record, and it has no business
 * appearing in the user's photo gallery or being readable by other apps. Internal storage is private
 * by construction and is removed with the app.
 */
@Singleton
class ReceiptImageStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(SpendLensDispatcher.IO)
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val directory: File
            get() = File(context.filesDir, RECEIPTS_DIR).apply { mkdirs() }

        /** A path for a not-yet-written capture. The name is a UUID for the same reason ids are. */
        fun newImageFile(): File = File(directory, "${UUID.randomUUID()}.jpg")

        fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

        /** Stored relative to `filesDir` so the absolute path can change between installs. */
        fun relativePathOf(file: File): String = "$RECEIPTS_DIR/${file.name}"

        /** Best-effort cleanup on a path we already hold; failure here is not worth surfacing. */
        fun deleteQuietly(file: File) {
            runCatching { file.delete() }
        }

        suspend fun delete(relativePath: String) =
            withContext(ioDispatcher) {
                resolve(relativePath).delete()
                Unit
            }

        private companion object {
            const val RECEIPTS_DIR = "receipts"
        }
    }
