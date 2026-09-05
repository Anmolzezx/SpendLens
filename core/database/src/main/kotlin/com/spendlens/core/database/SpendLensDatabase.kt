package com.spendlens.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendlens.core.database.converter.Converters
import com.spendlens.core.database.dao.CategoryDao
import com.spendlens.core.database.dao.ExpenseDao
import com.spendlens.core.database.entity.CategoryEntity
import com.spendlens.core.database.entity.ExpenseEntity

/**
 * `exportSchema = true` (the default) writes the schema JSON to `schemas/`, which is committed.
 * That file is the input to Room's migration tests, and its diff is what makes a schema change
 * visible in review instead of arriving as a crash on someone's device.
 */
@Database(
    entities = [ExpenseEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SpendLensDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao
}
