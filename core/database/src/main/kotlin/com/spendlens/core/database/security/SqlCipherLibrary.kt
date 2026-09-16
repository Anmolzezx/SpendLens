package com.spendlens.core.database.security

/**
 * Loads SQLCipher's native library.
 *
 * The `sqlcipher-android` artifact does not load it for you — every entry point has to ask first, or the
 * first query fails with an `UnsatisfiedLinkError` deep inside SQLite. `by lazy` makes that happen once,
 * whichever entry point gets there first.
 */
internal object SqlCipherLibrary {
    private val loaded: Boolean by lazy {
        System.loadLibrary("sqlcipher")
        true
    }

    fun load() {
        check(loaded) { "SQLCipher's native library could not be loaded" }
    }
}
