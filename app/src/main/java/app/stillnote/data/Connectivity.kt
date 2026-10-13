package app.stillnote.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the phone currently has a network to reach Notion through. */
class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val networks = mutableSetOf<Network>()

    private val _online = MutableStateFlow(manager.activeNetwork != null)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        val internet = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(internet, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update { networks += network }
            override fun onLost(network: Network) = update { networks -= network }
        })
    }

    @Synchronized
    private fun update(change: () -> Unit) {
        change()
        _online.value = networks.isNotEmpty()
    }
}
