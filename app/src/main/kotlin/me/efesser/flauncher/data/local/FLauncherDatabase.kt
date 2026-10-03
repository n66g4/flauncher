package me.efesser.flauncher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import me.efesser.flauncher.data.local.dao.AppDao
import me.efesser.flauncher.data.local.dao.CategoryDao
import me.efesser.flauncher.data.local.dao.SpacerDao
import me.efesser.flauncher.data.local.entity.AppCategoryEntity
import me.efesser.flauncher.data.local.entity.AppEntity
import me.efesser.flauncher.data.local.entity.CategoryEntity
import me.efesser.flauncher.data.local.entity.LauncherSpacerEntity

@Database(
    entities = [
        AppEntity::class,
        CategoryEntity::class,
        AppCategoryEntity::class,
        LauncherSpacerEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class FLauncherDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun categoryDao(): CategoryDao
    abstract fun spacerDao(): SpacerDao
}
