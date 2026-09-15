package com.spendlens.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

/**
 * Version 2 → 3: `occurred_at` becomes a calendar date, and `remote_version` is added.
 *
 * Hand-written, not an `@AutoMigration`. Room can generate a migration from schema diffs, but the
 * schema barely changes here — the column keeps its name and INTEGER type. What changes is what the
 * numbers *mean*, milliseconds to epoch days, and no schema diff can express that.
 *
 * Existing values are converted in [zoneId], the device's zone, because that is the zone the user saw
 * each date in. Converting in UTC would move every evening expense east of Greenwich to the next day.
 */
internal class Migration2To3(
    private val zoneId: ZoneId,
) : Migration(startVersion = 2, endVersion = 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN remote_version INTEGER")

        // Read everything first, then write. Updating rows while a cursor is still open over the same
        // table is legal in SQLite but easy to get subtly wrong; two passes is plainly correct.
        val converted = buildList {
            db.query("SELECT id, occurred_at FROM expenses").use { cursor ->
                while (cursor.moveToNext()) {
                    val epochDay = Instant
                        .ofEpochMilli(cursor.getLong(1))
                        .atZone(zoneId)
                        .toLocalDate()
                        .toEpochDay()
                    add(cursor.getString(0) to epochDay)
                }
            }
        }
        converted.forEach { (id, epochDay) ->
            db.execSQL("UPDATE expenses SET occurred_at = ? WHERE id = ?", arrayOf<Any>(epochDay, id))
        }
    }
}
