package com.spendlens.feature.capture

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Guava [ListenableFuture] as a coroutine.
 *
 * `kotlinx-coroutines-guava` provides this, but pulling in Guava's coroutine bridge for one call
 * site is a poor trade — CameraX is the only thing in this project that returns a
 * `ListenableFuture`. Cancelling the coroutine cancels the future.
 */
internal suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: java.util.concurrent.ExecutionException) {
                    continuation.resumeWithException(e.cause ?: e)
                } catch (e: InterruptedException) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
