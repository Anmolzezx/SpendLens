package com.spendlens.core.common.di

import javax.inject.Qualifier

enum class SpendLensDispatcher {
    Default,
    IO,
}

/**
 * Distinguishes the two `CoroutineDispatcher` bindings — without a qualifier Hilt cannot tell them
 * apart, since they are the same type.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(
    val dispatcher: SpendLensDispatcher,
)
