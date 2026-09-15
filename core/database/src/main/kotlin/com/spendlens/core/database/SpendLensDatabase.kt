package com.spendlens.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendlens.core.database.converter.Converters
import com.spendlens.core.database.dao.BudgetDao
import com.spendlens.core.database.dao.CategoryDao
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.entity.BudgetEntity
import com.spendlens.core.database.entity.CategoryEntity
import com.spendlens.core.database.entity.ExpenseEntity

/**
 * `exportSchema = true` (the default) writes the schema JSON to `schemas/`, which is committed.
 * That file is the input to Room's migration tests, and its diff is what makes a schema change
 * visible in review instead of arriving as a crash on someone's device.
 */
@Database(
    entities = [ExpenseEntity::class, CategoryEntity::class, BudgetEntity::class],
    version = 3,
    exportSchema = true,
    // Adding a table is purely additive, so Room can derive the migration from the exported
    // schemas. A change that renames or retypes a column could not be auto-migrated and would need
    // a hand-written Migration plus a MigrationTestHelper test — knowing which case you are in is
    // the point. There is deliberately no destructive fallback, so a missing migration crashes.
    // 1 → 2 is automatic (a new table). 2 → 3 changes what a column's values mean, which no
    // schema diff can express, so it is hand-written — see migration/Migrations.kt.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class SpendLensDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    abstract fun budgetDao(): BudgetDao
}
