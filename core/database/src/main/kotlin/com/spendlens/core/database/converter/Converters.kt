package com.spendlens.core.database.converter

import androidx.room.TypeConverter
import com.spendlens.core.model.SyncState
import java.time.Instant
import java.time.YearMonth

/**
 * Room stores primitives; these bridge the domain types.
 *
 * [Instant] is stored as epoch **milliseconds**, not an ISO string. Integers sort and range-query
 * correctly in SQLite with no collation surprises, and `ORDER BY occurred_at DESC` is the list
 * screen's whole query.
 *
 * [SyncState] is stored by `name`, not `ordinal`. An ordinal silently changes meaning the day
 * someone reorders the enum — every PENDING row would become SYNCED with no migration and no error.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

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
