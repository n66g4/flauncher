package me.efesser.flauncher.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import me.efesser.flauncher.domain.model.NetworkState
import me.efesser.flauncher.domain.model.NetworkType

object NetworkUtils {
    private const val MIN_RSSI = -90
    private const val MAX_RSSI = -55

    fun fromCapabilities(context: Context, capabilities: NetworkCapabilities): NetworkState {
        val hasNetwork = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasInternet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            hasNetwork
        }

        var networkType = NetworkType.Unknown
        var signalLevel = 0

        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                networkType = NetworkType.Cellular
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                networkType = NetworkType.Wifi
                signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val wifiInfo = capabilities.transportInfo as? WifiInfo
                    wifiSignalLevel(wifiInfo)
                } else {
                    @Suppress("DEPRECATION")
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wifiSignalLevel(wifiManager.connectionInfo)
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                networkType = NetworkType.Vpn
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                networkType = NetworkType.Wired
            }
        }

        return NetworkState(
            networkType = networkType,
            wifiSignalLevel = signalLevel,
            hasInternet = hasInternet,
        )
    }

    private fun wifiSignalLevel(wifiInfo: WifiInfo?): Int {
        if (wifiInfo == null) return 0
        return calculateSignalLevel(wifiInfo.rssi, 4)
    }

    private fun calculateSignalLevel(rssi: Int, levels: Int): Int {
        return when {
            rssi <= MIN_RSSI -> 0
            rssi >= MAX_RSSI -> levels - 1
            else -> ((rssi - MIN_RSSI) * (levels - 1) / (MAX_RSSI - MIN_RSSI))
        }
    }
}
