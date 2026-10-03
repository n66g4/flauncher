package me.efesser.flauncher.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    val name: String,
    val version: String,
    val hidden: Boolean = false,
)
