package com.spendlens.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendlens.core.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "color_index")
    val colorIndex: Int,
    @ColumnInfo(name = "icon_key")
    val iconKey: String,
)

fun CategoryEntity.asDomainModel() =
    Category(
        id = id,
        name = name,
        colorIndex = colorIndex,
        iconKey = iconKey,
    )

fun Category.asEntity() =
    CategoryEntity(
        id = id,
        name = name,
        colorIndex = colorIndex,
        iconKey = iconKey,
    )
