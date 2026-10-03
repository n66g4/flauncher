package me.efesser.flauncher.data.repository

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import me.efesser.flauncher.R
import me.efesser.flauncher.data.local.FLauncherDatabase
import me.efesser.flauncher.data.local.entity.AppCategoryEntity
import me.efesser.flauncher.data.local.entity.AppEntity
import me.efesser.flauncher.data.local.entity.CategoryEntity
import me.efesser.flauncher.data.local.entity.CategorySort
import me.efesser.flauncher.data.local.entity.CategoryType
import me.efesser.flauncher.data.local.entity.LauncherSpacerEntity
import me.efesser.flauncher.domain.model.AppChangeEvent
import me.efesser.flauncher.domain.model.CategoryLayoutType
import me.efesser.flauncher.domain.model.LauncherApp
import me.efesser.flauncher.domain.model.LauncherAppItem
import me.efesser.flauncher.domain.model.LauncherCategory
import me.efesser.flauncher.domain.model.LauncherSection
import me.efesser.flauncher.domain.model.NetworkState
import me.efesser.flauncher.platform.AppPlatformService
import me.efesser.flauncher.platform.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FLauncherDatabase,
    private val appPlatformService: AppPlatformService,
    networkMonitor: NetworkMonitor,
) {
    private val appDao = database.appDao()
    private val categoryDao = database.categoryDao()
    private val spacerDao = database.spacerDao()

    val networkState: StateFlow<NetworkState> = networkMonitor.state

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        val installed = appPlatformService.loadInstalledApps()
        database.withTransaction {
            upsertAllPreservingHidden(installed)
            if (categoryDao.count() == 0) {
                seedDefaultCategories(installed)
            } else {
                ensureFavoritesCategory()
                normalizeBuiltinCategoryLayouts()
            }
        }
    }

    suspend fun loadLauncherSections(): List<LauncherSection> = withContext(Dispatchers.IO) {
        val appMap = appDao.getAll().associateBy { it.packageName }
        val categorySections = categoryDao.getAll().map { entity ->
            LauncherSection.CategorySection(
                category = buildCategory(entity, appMap),
                order = entity.order,
            )
        }
        val spacerSections = spacerDao.getAll().map { entity ->
            LauncherSection.SpacerSection(
                id = entity.id,
                height = entity.height,
                order = entity.order,
            )
        }
        (categorySections + spacerSections).sortedBy { it.order }
    }

    suspend fun loadLauncherCategories(): List<LauncherCategory> = withContext(Dispatchers.IO) {
        val appMap = appDao.getAll().associateBy { it.packageName }
        categoryDao.getAll().map { buildCategory(it, appMap) }
    }

    suspend fun getCategoriesWithoutApp(packageName: String): List<LauncherCategory> =
        withContext(Dispatchers.IO) {
            val appMap = appDao.getAll().associateBy { it.packageName }
            categoryDao.getAll().mapNotNull { entity ->
                val links = categoryDao.getAppLinks(entity.id)
                if (links.any { it.appPackageName == packageName }) {
                    null
                } else {
                    buildCategory(entity, appMap)
                }
            }
        }

    suspend fun addCategory(name: String): Int = withContext(Dispatchers.IO) {
        database.withTransaction {
            shiftAllSectionsOrder(by = 1)
            categoryDao.insert(
                CategoryEntity(
                    name = name,
                    order = 0,
                    sort = CategorySort.MANUAL.ordinal,
                    type = CategoryType.GRID.ordinal,
                    columnsCount = 5,
                ),
            ).toInt()
        }
    }

    suspend fun renameCategory(categoryId: Int, name: String) = withContext(Dispatchers.IO) {
        categoryDao.updateName(categoryId, name)
    }

    suspend fun setCategorySort(categoryId: Int, manual: Boolean) = withContext(Dispatchers.IO) {
        val sort = if (manual) CategorySort.MANUAL.ordinal else CategorySort.ALPHABETICAL.ordinal
        categoryDao.updateSort(categoryId, sort)
    }

    suspend fun setCategoryLayoutType(categoryId: Int, layoutType: CategoryLayoutType) =
        withContext(Dispatchers.IO) {
            val type = when (layoutType) {
                CategoryLayoutType.Row -> CategoryType.ROW
                CategoryLayoutType.Grid -> CategoryType.GRID
            }
            categoryDao.updateType(categoryId, type.ordinal)
        }

    suspend fun setCategoryColumnsCount(categoryId: Int, columnsCount: Int) =
        withContext(Dispatchers.IO) {
            categoryDao.updateColumnsCount(categoryId, columnsCount.coerceIn(5, 7))
        }

    suspend fun addSpacer(height: Int = DEFAULT_SPACER_HEIGHT) = withContext(Dispatchers.IO) {
        val order = loadSectionRefs().size
        spacerDao.insert(LauncherSpacerEntity(height = height, order = order))
    }

    suspend fun deleteCategory(categoryId: Int) = withContext(Dispatchers.IO) {
        database.withTransaction {
            categoryDao.deleteAllAppLinks(categoryId)
            categoryDao.delete(categoryId)
            renormalizeSectionOrders(loadSectionRefs().filter { it.type != SectionType.CATEGORY || it.id != categoryId })
        }
    }

    suspend fun deleteSpacer(spacerId: Int) = withContext(Dispatchers.IO) {
        database.withTransaction {
            spacerDao.delete(spacerId)
            renormalizeSectionOrders(loadSectionRefs().filter { it.type != SectionType.SPACER || it.id != spacerId })
        }
    }

    suspend fun moveSection(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        if (fromIndex == toIndex) return@withContext
        val refs = loadSectionRefs().toMutableList()
        if (fromIndex !in refs.indices || toIndex !in refs.indices) return@withContext
        val moved = refs.removeAt(fromIndex)
        refs.add(toIndex, moved)
        database.withTransaction {
            renormalizeSectionOrders(refs)
        }
    }

    suspend fun removeAppFromCategory(categoryId: Int, packageName: String) = withContext(Dispatchers.IO) {
        categoryDao.deleteAppCategory(categoryId, packageName)
    }

    suspend fun addAppToCategory(categoryId: Int, packageName: String) = withContext(Dispatchers.IO) {
        val links = categoryDao.getAppLinks(categoryId)
        if (links.any { it.appPackageName == packageName }) return@withContext
        val nextOrder = (categoryDao.maxOrderInCategory(categoryId) ?: -1) + 1
        categoryDao.insertAppCategory(
            AppCategoryEntity(
                categoryId = categoryId,
                appPackageName = packageName,
                order = nextOrder,
            ),
        )
    }

    suspend fun reorderAppInCategory(categoryId: Int, fromIndex: Int, toIndex: Int) =
        withContext(Dispatchers.IO) {
            if (fromIndex == toIndex) return@withContext
            database.withTransaction {
                val links = categoryDao.getAppLinks(categoryId).toMutableList()
                if (fromIndex !in links.indices || toIndex !in links.indices) return@withTransaction
                val moved = links.removeAt(fromIndex)
                links.add(toIndex, moved)
                links.forEachIndexed { index, link ->
                    categoryDao.updateAppOrder(categoryId, link.appPackageName, index)
                }
            }
        }

    suspend fun handleAppChange(event: AppChangeEvent) = withContext(Dispatchers.IO) {
        when (event) {
            is AppChangeEvent.Removed -> removeApp(event.packageName)
            is AppChangeEvent.Added -> upsertAndLink(event.app)
            is AppChangeEvent.Changed -> upsertPreservingHidden(event.app)
            is AppChangeEvent.Available -> event.apps.forEach { upsertAndLink(it) }
        }
    }

    fun launchApp(app: LauncherApp): Boolean = appPlatformService.launchApp(app)

    fun openAppInfo(packageName: String): Boolean = appPlatformService.openAppInfo(packageName)

    fun uninstallApp(packageName: String): Boolean = appPlatformService.uninstallApp(packageName)

    suspend fun setAppHidden(packageName: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        appDao.setHidden(packageName, hidden)
    }

    private suspend fun buildCategory(
        entity: CategoryEntity,
        appMap: Map<String, AppEntity>,
    ): LauncherCategory {
        val links = categoryDao.getAppLinks(entity.id)
        var items = links.mapNotNull { link ->
            appMap[link.appPackageName]?.takeUnless { it.hidden }?.let { appEntity ->
                LauncherAppItem(
                    app = appEntity.toDomain(),
                    icon = appPlatformService.loadIcon(appEntity.packageName),
                )
            }
        }
        if (entity.sort == CategorySort.ALPHABETICAL.ordinal) {
            items = items.sortedBy { it.app.name }
        }
        val layoutType = if (entity.type == CategoryType.ROW.ordinal) {
            CategoryLayoutType.Row
        } else {
            CategoryLayoutType.Grid
        }
        return LauncherCategory(
            id = entity.id,
            name = entity.name,
            manualSort = entity.sort == CategorySort.MANUAL.ordinal,
            layoutType = layoutType,
            columnsCount = entity.columnsCount.coerceIn(5, 7),
            apps = items,
        )
    }

    private enum class SectionType { CATEGORY, SPACER }

    private data class SectionRef(val type: SectionType, val id: Int, val order: Int)

    private suspend fun loadSectionRefs(): List<SectionRef> {
        val categories = categoryDao.getAll().map { SectionRef(SectionType.CATEGORY, it.id, it.order) }
        val spacers = spacerDao.getAll().map { SectionRef(SectionType.SPACER, it.id, it.order) }
        return (categories + spacers).sortedBy { it.order }
    }

    private suspend fun renormalizeSectionOrders(refs: List<SectionRef>) {
        refs.forEachIndexed { index, ref ->
            when (ref.type) {
                SectionType.CATEGORY -> categoryDao.updateOrder(ref.id, index)
                SectionType.SPACER -> spacerDao.updateOrder(ref.id, index)
            }
        }
    }

    private suspend fun shiftAllSectionsOrder(by: Int) {
        loadSectionRefs().forEach { ref ->
            when (ref.type) {
                SectionType.CATEGORY -> categoryDao.updateOrder(ref.id, ref.order + by)
                SectionType.SPACER -> spacerDao.updateOrder(ref.id, ref.order + by)
            }
        }
    }

    private suspend fun upsertAllPreservingHidden(apps: List<LauncherApp>) {
        val hiddenByPackage = appDao.getAll().associate { it.packageName to it.hidden }
        appDao.upsertAll(
            apps.map { app ->
                app.toEntity().copy(hidden = hiddenByPackage[app.packageName] ?: false)
            },
        )
    }

    private suspend fun upsertPreservingHidden(app: LauncherApp) {
        val hidden = appDao.getByPackageName(app.packageName)?.hidden ?: false
        appDao.upsert(app.toEntity().copy(hidden = hidden))
    }

    private suspend fun removeApp(packageName: String) {
        database.withTransaction {
            categoryDao.deleteAppLinks(packageName)
            appDao.delete(packageName)
        }
    }

    private suspend fun upsertAndLink(app: LauncherApp) {
        database.withTransaction {
            upsertPreservingHidden(app)
            val categories = categoryDao.getAll()
            val alreadyLinked = categories.any { category ->
                categoryDao.getAppLinks(category.id).any { it.appPackageName == app.packageName }
            }
            if (!alreadyLinked) {
                linkToDefaultCategory(app)
            }
        }
    }

    private suspend fun linkToDefaultCategory(app: LauncherApp) {
        val categoryName = if (app.sideloaded) {
            context.getString(R.string.category_non_tv_apps)
        } else {
            context.getString(R.string.category_tv_apps)
        }
        var categoryId = categoryDao.findByName(categoryName)?.id
        if (categoryId == null) {
            categoryId = categoryDao.insert(
                CategoryEntity(
                    name = categoryName,
                    type = CategoryType.GRID.ordinal,
                    columnsCount = 5,
                    order = loadSectionRefs().size,
                ),
            ).toInt()
        }
        val nextOrder = (categoryDao.maxOrderInCategory(categoryId) ?: -1) + 1
        categoryDao.insertAppCategory(
            AppCategoryEntity(
                categoryId = categoryId,
                appPackageName = app.packageName,
                order = nextOrder,
            ),
        )
    }

    private suspend fun seedDefaultCategories(installed: List<LauncherApp>) {
        val tvApps = installed.filter { !it.sideloaded }
        val nonTvApps = installed.filter { it.sideloaded }

        insertCategoryWithApps(
            name = context.getString(R.string.category_favorites),
            type = CategoryType.ROW,
            columnsCount = 5,
            apps = emptyList(),
        )
        if (tvApps.isNotEmpty()) {
            insertCategoryWithApps(
                name = context.getString(R.string.category_tv_apps),
                type = CategoryType.GRID,
                columnsCount = 5,
                apps = tvApps,
            )
        }
        if (nonTvApps.isNotEmpty()) {
            insertCategoryWithApps(
                name = context.getString(R.string.category_non_tv_apps),
                type = CategoryType.GRID,
                columnsCount = 5,
                apps = nonTvApps,
            )
        }
    }

    private suspend fun normalizeBuiltinCategoryLayouts() {
        listOf(
            R.string.category_tv_apps,
            R.string.category_non_tv_apps,
        ).forEach { nameRes ->
            val entity = categoryDao.findByName(context.getString(nameRes)) ?: return@forEach
            if (entity.type != CategoryType.GRID.ordinal) {
                categoryDao.updateType(entity.id, CategoryType.GRID.ordinal)
            }
            if (entity.columnsCount != BUILTIN_GRID_COLUMNS) {
                categoryDao.updateColumnsCount(entity.id, BUILTIN_GRID_COLUMNS)
            }
        }
    }

    private suspend fun ensureFavoritesCategory() {
        val favoritesName = context.getString(R.string.category_favorites)
        if (categoryDao.findByName(favoritesName) != null) return
        database.withTransaction {
            shiftAllSectionsOrder(by = 1)
            categoryDao.insert(
                CategoryEntity(
                    name = favoritesName,
                    sort = CategorySort.MANUAL.ordinal,
                    type = CategoryType.ROW.ordinal,
                    columnsCount = 5,
                    order = 0,
                ),
            )
        }
    }

    private suspend fun insertCategoryWithApps(
        name: String,
        type: CategoryType,
        apps: List<LauncherApp>,
        columnsCount: Int = 5,
    ) {
        val categoryId = categoryDao.insert(
            CategoryEntity(
                name = name,
                type = type.ordinal,
                columnsCount = columnsCount,
                order = loadSectionRefs().size,
            ),
        ).toInt()

        categoryDao.insertAppCategories(
            apps.mapIndexed { index, app ->
                AppCategoryEntity(
                    categoryId = categoryId,
                    appPackageName = app.packageName,
                    order = index,
                )
            },
        )
    }

    private fun LauncherApp.toEntity() = AppEntity(
        packageName = packageName,
        name = name,
        version = version,
    )

    private fun AppEntity.toDomain() = LauncherApp(
        packageName = packageName,
        name = name,
        version = version,
        sideloaded = appPlatformService.isSideloaded(packageName),
        hidden = hidden,
    )

    companion object {
        const val DEFAULT_SPACER_HEIGHT = 48
        private const val BUILTIN_GRID_COLUMNS = 5
    }
}
