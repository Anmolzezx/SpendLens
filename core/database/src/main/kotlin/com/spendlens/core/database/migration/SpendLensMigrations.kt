package com.spendlens.core.database.migration

import androidx.room.RoomDatabase
import com.spendlens.core.database.SpendLensDatabase
import java.time.ZoneId

/**
 * Every hand-written migration, registered in one place so production and tests cannot drift apart.
 * A test that opens the database without these would pass while the real app crashed on upgrade.
 */
fun RoomDatabase.Builder<SpendLensDatabase>.addSpendLensMigrations(
    zoneId: ZoneId,
): RoomDatabase.Builder<SpendLensDatabase> = addMigrations(Migration2To3(zoneId))
