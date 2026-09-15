package com.spendlens.core.database.converter

import androidx.room.TypeConverter
import com.spendlens.core.model.SyncState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Room stores primitives; these bridge the domain types.
 *
 * [Instant] (`updated_at`) is stored as epoch **milliseconds**, and [LocalDate] (`occurred_at`) as an
 * epoch **day** — integers, not ISO strings, so both sort and range-query correctly in SQLite with no
 * collation surprises.
 *
 * [SyncState] is stored by `name`, not `ordinal`. An ordinal silently changes meaning the day
 * someone reorders the enum — every PENDING row would become SYNCED with no migration and no error.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /**
     * `LocalDate` as an epoch day. An integer, like the millis this column held before migration 3,
     * so the column keeps its type and ordering and range queries stay plain numeric comparisons.
     */
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun syncStateToName(value: SyncState?): String? = value?.name

    @TypeConverter
    fun nameToSyncState(value: String?): SyncState? = value?.let(SyncState::valueOf)

    /** `YearMonth` as "2026-08" — sorts lexicographically in the same order it does chronologically. */
    @TypeConverter
    fun yearMonthToString(value: YearMonth?): String? = value?.toString()

    @TypeConverter
    fun stringToYearMonth(value: String?): YearMonth? = value?.let(YearMonth::parse)
}
