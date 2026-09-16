package com.spendlens.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendlens.core.database.entity.ExpenseEntity
import com.spendlens.core.database.migration.addSpendLensMigrations
import com.spendlens.core.database.security.DatabaseKey
import com.spendlens.core.database.security.KeystoreDatabasePassphrase
import com.spendlens.core.database.security.SqlCipherLibrary
import com.spendlens.core.database.security.prepareDatabaseFile
import com.spendlens.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Instrumented, because neither half of this works on the JVM: SQLCipher is a native library, and the
 * passphrase lives in the device's keystore.
 *
 * The case that matters most is the middle one — an installation that already has a plaintext database
 * full of expenses. Getting that wrong loses a user's data, and no unit test can tell you it works.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseFile: File = context.getDatabasePath("encryption-test.db")
    private val key = DatabaseKey(passphrase = "a".repeat(64).toByteArray(Charsets.US_ASCII), isNew = false)
    private val openDatabases = mutableListOf<SpendLensDatabase>()

    @Before
    fun loadSqlCipher() {
        // Production loads this in DatabaseModule; the tests that open SQLCipher directly need it too.
        SqlCipherLibrary.load()
    }

    @After
    fun tearDown() {
        openDatabases.forEach { it.close() }
        listOf("", "-wal", "-shm", "-journal", ".encrypting").forEach { File(databaseFile.path + it).delete() }
    }

    @Test
    fun aPlaintextDatabaseIsEncryptedAndKeepsItsRows() =
        runTest {
            plaintextDatabase().use { database ->
                database.expenseDao().upsert(expense())
            }
            assertTrue("test set-up should produce a plaintext file", databaseFile.startsWithSqliteHeader())

            prepareDatabaseFile(databaseFile, key)

            assertFalse("the file should no longer be readable as plaintext", databaseFile.startsWithSqliteHeader())
            val expense = encryptedDatabase(key)
                .expenseDao()
                .observeExpenses()
                .first()
                .single()
            assertEquals("Blue Bottle", expense.merchant)
            assertEquals(650L, expense.amountMinor)
        }

    /** Room reads `user_version` to decide what to migrate, and `sqlcipher_export` does not copy it. */
    @Test
    fun theSchemaVersionSurvivesEncryption() =
        runTest {
            plaintextDatabase().use { it.expenseDao().upsert(expense()) }
            val versionBefore = readUserVersion(passphrase = "")

            prepareDatabaseFile(databaseFile, key)

            assertEquals(versionBefore, readUserVersion(String(key.passphrase, Charsets.US_ASCII)))
        }

    @Test
    fun theEncryptedFileCannotBeOpenedWithAnotherPassphrase() =
        runTest {
            plaintextDatabase().use { it.expenseDao().upsert(expense()) }
            prepareDatabaseFile(databaseFile, key)

            assertThrows(Exception::class.java) {
                SQLiteDatabase.openOrCreateDatabase(databaseFile, "b".repeat(64), null, null).close()
            }
        }

    @Test
    fun anAlreadyEncryptedDatabaseIsLeftAlone() =
        runTest {
            plaintextDatabase().use { it.expenseDao().upsert(expense()) }
            prepareDatabaseFile(databaseFile, key)

            prepareDatabaseFile(databaseFile, key)

            assertEquals(
                1,
                encryptedDatabase(key)
                    .expenseDao()
                    .observeExpenses()
                    .first()
                    .size,
            )
        }

    /** The key is unrecoverable, so the file is unreadable by anyone. Starting over beats crashing. */
    @Test
    fun anEncryptedDatabaseWhoseKeyIsGoneIsReplaced() =
        runTest {
            plaintextDatabase().use { it.expenseDao().upsert(expense()) }
            prepareDatabaseFile(databaseFile, key)

            val newKey = DatabaseKey(passphrase = "c".repeat(64).toByteArray(Charsets.US_ASCII), isNew = true)
            prepareDatabaseFile(databaseFile, newKey)

            assertFalse(databaseFile.exists())
            assertTrue(
                encryptedDatabase(newKey)
                    .expenseDao()
                    .observeExpenses()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun theKeystorePassphraseIsTheSameEveryTime() {
        val first = KeystoreDatabasePassphrase(context).getOrCreate()
        val second = KeystoreDatabasePassphrase(context).getOrCreate()

        assertNotNull(first.passphrase)
        assertEquals(String(first.passphrase), String(second.passphrase))
        assertFalse("the second read should find the stored passphrase", second.isNew)
    }

    // -- helpers ------------------------------------------------------------------------------------

    private fun plaintextDatabase(): SpendLensDatabase =
        Room
            .databaseBuilder(context, SpendLensDatabase::class.java, databaseFile.name)
            .addSpendLensMigrations(ZoneOffset.UTC)
            .build()

    private fun encryptedDatabase(key: DatabaseKey): SpendLensDatabase =
        Room
            .databaseBuilder(context, SpendLensDatabase::class.java, databaseFile.name)
            .openHelperFactory(
                net.zetetic.database.sqlcipher
                    .SupportOpenHelperFactory(key.passphrase.copyOf()),
            ).addSpendLensMigrations(ZoneOffset.UTC)
            .build()
            .also { openDatabases += it }

    private fun readUserVersion(passphrase: String): Int {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, passphrase, null, null)
        return try {
            database.version
        } finally {
            database.close()
        }
    }

    private fun File.startsWithSqliteHeader(): Boolean =
        inputStream().use { stream ->
            val header = ByteArray(16)
            stream.read(header)
            String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
        }

    private fun expense() =
        ExpenseEntity(
            id = "a",
            merchant = "Blue Bottle",
            amountMinor = 650,
            currency = "USD",
            occurredOn = LocalDate.parse("2026-09-16"),
            categoryId = "cat-dining",
            note = null,
            receiptImagePath = null,
            syncState = SyncState.PENDING,
            updatedAt = Instant.EPOCH,
            isDeleted = false,
        )

    private inline fun <T> SpendLensDatabase.use(block: (SpendLensDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
