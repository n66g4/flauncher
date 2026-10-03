package me.efesser.flauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.efesser.flauncher.data.local.entity.AppEntity

@Dao
interface AppDao {
    @Query("SELECT * FROM apps WHERE hidden = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getVisible(): List<AppEntity>

    @Query("SELECT * FROM apps")
    suspend fun getAll(): List<AppEntity>

    @Query("SELECT * FROM apps WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: AppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<AppEntity>)

    @Query("DELETE FROM apps WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE apps SET hidden = :hidden WHERE package_name = :packageName")
    suspend fun setHidden(packageName: String, hidden: Boolean)
}
