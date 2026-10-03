package me.efesser.flauncher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "launcher_spacers")
data class LauncherSpacerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val height: Int,
    val order: Int,
)
