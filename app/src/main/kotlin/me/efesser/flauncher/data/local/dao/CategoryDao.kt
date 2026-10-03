package me.efesser.flauncher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.efesser.flauncher.data.local.entity.AppCategoryEntity
import me.efesser.flauncher.data.local.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAppCategories(links: List<AppCategoryEntity>)

    @Insert
    suspend fun insertAppCategory(link: AppCategoryEntity)

    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT * FROM apps_categories WHERE category_id = :categoryId ORDER BY `order` ASC")
    suspend fun getAppLinks(categoryId: Int): List<AppCategoryEntity>

    @Query("DELETE FROM apps_categories WHERE app_package_name = :packageName")
    suspend fun deleteAppLinks(packageName: String)

    @Query(
        """
        SELECT MAX(`order`) FROM apps_categories
        WHERE category_id = :categoryId
        """,
    )
    suspend fun maxOrderInCategory(categoryId: Int): Int?

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CategoryEntity?

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun updateName(id: Int, name: String)

    @Query("UPDATE categories SET sort = :sort WHERE id = :id")
    suspend fun updateSort(id: Int, sort: Int)

    @Query("UPDATE categories SET type = :type WHERE id = :id")
    suspend fun updateType(id: Int, type: Int)

    @Query("UPDATE categories SET columns_count = :columnsCount WHERE id = :id")
    suspend fun updateColumnsCount(id: Int, columnsCount: Int)

    @Query("UPDATE categories SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: Int, order: Int)

    @Query(
        """
        DELETE FROM apps_categories
        WHERE category_id = :categoryId AND app_package_name = :packageName
        """,
    )
    suspend fun deleteAppCategory(categoryId: Int, packageName: String)

    @Query("DELETE FROM apps_categories WHERE category_id = :categoryId")
    suspend fun deleteAllAppLinks(categoryId: Int)

    @Query(
        """
        UPDATE apps_categories SET `order` = :order
        WHERE category_id = :categoryId AND app_package_name = :packageName
        """,
    )
    suspend fun updateAppOrder(categoryId: Int, packageName: String, order: Int)
}
