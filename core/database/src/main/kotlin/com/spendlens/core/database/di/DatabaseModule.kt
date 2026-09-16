package com.spendlens.core.database.di

import android.content.Context
import androidx.room.Room
import com.spendlens.core.database.DatabaseTransactionRunner
import com.spendlens.core.database.RoomTransactionRunner
import com.spendlens.core.database.SeedCategoriesCallback
import com.spendlens.core.database.SpendLensDatabase
import com.spendlens.core.database.dao.BudgetDao
import com.spendlens.core.database.dao.CategoryDao
import com.spendlens.core.database.dao.ExpenseConflictDao
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.dao.SyncCursorDao
import com.spendlens.core.database.migration.addSpendLensMigrations
import com.spendlens.core.database.security.DatabasePassphrase
import com.spendlens.core.database.security.KeystoreDatabasePassphrase
import com.spendlens.core.database.security.SqlCipherLibrary
import com.spendlens.core.database.security.prepareDatabaseFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * The database is encrypted with SQLCipher, keyed by a passphrase only this device's keystore can
     * decrypt. [prepareDatabaseFile] runs first, before any connection is opened: on an installation
     * that predates encryption it converts the existing plaintext file in place.
     */
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
        zoneId: ZoneId,
        passphrase: DatabasePassphrase,
    ): SpendLensDatabase {
        val key = passphrase.getOrCreate()
        // Before the factory below touches SQLCipher: its native library is not loaded automatically.
        SqlCipherLibrary.load()
        prepareDatabaseFile(context.getDatabasePath(DATABASE_NAME), key)

        return Room
            .databaseBuilder(
                context = context,
                klass = SpendLensDatabase::class.java,
                name = DATABASE_NAME,
                // SQLCipher wipes the array it is given once the database is open, so it gets a copy.
            ).openHelperFactory(SupportOpenHelperFactory(key.passphrase.copyOf()))
            .addCallback(SeedCategoriesCallback())
            .addSpendLensMigrations(zoneId)
            // Deliberately no fallbackToDestructiveMigration(). This is a finance app: silently
            // wiping a user's expenses because a migration was missing is worse than crashing, and
            // a crash in testing is what forces the migration to be written.
            .build()
    }

    @Provides
    fun providesExpenseDao(database: SpendLensDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun providesCategoryDao(database: SpendLensDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun providesBudgetDao(database: SpendLensDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun providesExpenseConflictDao(database: SpendLensDatabase): ExpenseConflictDao = database.expenseConflictDao()

    @Provides
    fun providesSyncCursorDao(database: SpendLensDatabase): SyncCursorDao = database.syncCursorDao()

    @Provides
    fun providesTransactionRunner(database: SpendLensDatabase): DatabaseTransactionRunner =
        RoomTransactionRunner(database)

    @Provides
    @Singleton
    fun providesDatabasePassphrase(
        @ApplicationContext context: Context,
    ): DatabasePassphrase = KeystoreDatabasePassphrase(context)

    private const val DATABASE_NAME = "spendlens.db"
}
