package me.efesser.flauncher.domain.model

import androidx.annotation.ColorInt

enum class GradientStyle {
    LinearVertical,
    LinearHorizontal,
    Radial,
}

data class LauncherGradient(
    val uuid: String,
    val name: String,
    @ColorInt val startColor: Int,
    @ColorInt val endColor: Int,
    val style: GradientStyle = GradientStyle.LinearVertical,
)

object LauncherGradients {
    val greatWhale = LauncherGradient(
        uuid = "8bbdc190-ff6c-496e-8033-3c217e78da36",
        name = "Great Whale",
        startColor = 0xFF6991C7.toInt(),
        endColor = 0xFFA3BDED.toInt(),
        style = GradientStyle.LinearHorizontal,
    )

    val viciousStance = LauncherGradient(
        uuid = "e89f29f3-a0a3-4ee6-a363-5e9df2a124fd",
        name = "Vicious Stance",
        startColor = 0xFF29323C.toInt(),
        endColor = 0xFF485563.toInt(),
    )

    val teenNotebook = LauncherGradient(
        uuid = "027e7848-104c-42eb-94ce-d25762d426c1",
        name = "Teen Notebook",
        startColor = 0xFF9795F0.toInt(),
        endColor = 0xFFFBC8D4.toInt(),
    )

    val oldHat = LauncherGradient(
        uuid = "8458ae14-7a5a-461d-bb14-154a04a9f6d2",
        name = "Old Hat",
        startColor = 0xFFFCB69F.toInt(),
        endColor = 0xFFFFECD2.toInt(),
        style = GradientStyle.Radial,
    )

    val burningSprings = LauncherGradient(
        uuid = "57801094-a300-4626-8512-ec366d7d9c59",
        name = "Burning Spring",
        startColor = 0xFF71DDA6.toInt(),
        endColor = 0xFF70B2BC.toInt(),
        style = GradientStyle.Radial,
    )

    val all = listOf(
        greatWhale,
        viciousStance,
        teenNotebook,
        oldHat,
        burningSprings,
    )

    fun findByUuid(uuid: String?): LauncherGradient =
        all.firstOrNull { it.uuid == uuid } ?: greatWhale
}
