package me.efesser.flauncher.domain.model

enum class NetworkType {
    Cellular,
    Wifi,
    Vpn,
    Wired,
    Unknown,
}

data class NetworkState(
    val networkType: NetworkType = NetworkType.Unknown,
    val wifiSignalLevel: Int = 0,
    val hasInternet: Boolean = false,
)
