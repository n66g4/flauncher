package me.efesser.flauncher.domain.model

import android.graphics.drawable.Drawable

data class LauncherApp(
    val packageName: String,
    val name: String,
    val version: String,
    val sideloaded: Boolean,
    val hidden: Boolean = false,
    val action: String? = null,
)

enum class CategoryLayoutType {
    Row,
    Grid,
}

data class LauncherCategory(
    val id: Int,
    val name: String,
    val manualSort: Boolean = true,
    val layoutType: CategoryLayoutType = CategoryLayoutType.Grid,
    val columnsCount: Int = 5,
    val apps: List<LauncherAppItem>,
)

data class LauncherAppItem(
    val app: LauncherApp,
    val icon: Drawable?,
)
