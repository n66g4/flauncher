package me.efesser.flauncher.domain.model

sealed class LauncherSection {
    abstract val order: Int

    data class CategorySection(
        val category: LauncherCategory,
        override val order: Int,
    ) : LauncherSection()

    data class SpacerSection(
        val id: Int,
        val height: Int,
        override val order: Int,
    ) : LauncherSection()
}
