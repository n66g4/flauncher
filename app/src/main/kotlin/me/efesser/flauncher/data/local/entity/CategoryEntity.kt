package me.efesser.flauncher.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sort: Int = CategorySort.MANUAL.ordinal,
    val type: Int = CategoryType.ROW.ordinal,
    @ColumnInfo(name = "row_height") val rowHeight: Int = 110,
    @ColumnInfo(name = "columns_count") val columnsCount: Int = 5,
    val order: Int,
)

enum class CategorySort { MANUAL, ALPHABETICAL }

enum class CategoryType { ROW, GRID }
