package me.efesser.flauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.efesser.flauncher.data.local.entity.LauncherSpacerEntity

@Dao
interface SpacerDao {
    @Query("SELECT * FROM launcher_spacers ORDER BY `order` ASC")
    suspend fun getAll(): List<LauncherSpacerEntity>

    @Insert
    suspend fun insert(spacer: LauncherSpacerEntity): Long

    @Query("DELETE FROM launcher_spacers WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE launcher_spacers SET height = :height WHERE id = :id")
    suspend fun updateHeight(id: Int, height: Int)

    @Query("UPDATE launcher_spacers SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: Int, order: Int)
}
