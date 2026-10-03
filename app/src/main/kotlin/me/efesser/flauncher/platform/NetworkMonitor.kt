package me.efesser.flauncher.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.efesser.flauncher.domain.model.NetworkState
import me.efesser.flauncher.domain.model.NetworkType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(readCurrentState(context))
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _state.value = NetworkUtils.fromCapabilities(context, capabilities)
        }

        override fun onLost(network: Network) {
            _state.value = NetworkState(networkType = NetworkType.Unknown)
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(callback)
            }
        } else {
            val request = NetworkRequest.Builder().build()
            connectivityManager.registerNetworkCallback(request, callback)
        }
    }

    private fun readCurrentState(context: Context): NetworkState {
        val network = connectivityManager.activeNetwork ?: return NetworkState()
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkState()
        return NetworkUtils.fromCapabilities(context, capabilities)
    }
}
