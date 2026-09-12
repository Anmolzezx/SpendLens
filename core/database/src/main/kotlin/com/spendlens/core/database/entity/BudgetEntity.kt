package com.spendlens.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.spendlens.core.model.Budget
import java.time.YearMonth

/**
 * One spending limit per category per month.
 *
 * The primary key is composite — `(category_id, month)` — rather than a synthetic id. That makes
 * "one budget per category per month" a database constraint instead of a rule the code has to
 * remember, so an `@Upsert` naturally overwrites last month's figure rather than duplicating it.
 */
@Entity(tableName = "budgets", primaryKeys = ["category_id", "month"])
data class BudgetEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    /** Stored as "2026-08" — sorts lexicographically in the same order it does chronologically. */
    val month: YearMonth,
    @ColumnInfo(name = "limit_minor")
    val limitMinor: Long,
    val currency: String,
)

fun BudgetEntity.asDomainModel() =
    Budget(
        categoryId = categoryId,
        limitMinor = limitMinor,
        currency = currency,
        month = month,
    )

fun Budget.asEntity() =
    BudgetEntity(
        categoryId = categoryId,
        month = month,
        limitMinor = limitMinor,
        currency = currency,
    )
