package me.efesser.flauncher.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "apps_categories",
    primaryKeys = ["category_id", "app_package_name"],
)
data class AppCategoryEntity(
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "app_package_name") val appPackageName: String,
    val order: Int,
)
