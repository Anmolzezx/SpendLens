package com.spendlens.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendlens.core.model.DefaultCategories

/**
 * Seeds the default categories the first time the database is created.
 *
 * `onCreate` rather than an app-startup check: it runs exactly once, inside Room's creation
 * transaction, before any query can observe an empty table. A "seed if empty" check on startup races
 * the first read and can flash an uncategorised list.
 *
 * Raw inserts rather than the DAO because the DAO does not exist yet at this point in Room's
 * lifecycle — `onCreate` is called while the database is still being built.
 */
internal class SeedCategoriesCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        DefaultCategories.all.forEach { category ->
            val values = ContentValues().apply {
                put("id", category.id)
                put("name", category.name)
                put("color_index", category.colorIndex)
                put("icon_key", category.iconKey)
            }
            // CONFLICT_IGNORE so a re-run can never fail the whole creation transaction.
            db.insert("categories", SQLiteDatabase.CONFLICT_IGNORE, values)
        }
    }
}
