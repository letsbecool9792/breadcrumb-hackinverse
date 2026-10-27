package com.lbc.breadcrumb.embed

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The model's [ModelStatus], live: the download's progress, and the network it
 * would download on. Nothing here starts a download ([ModelDownloadWorker] does).
 */
class ModelWatch(private val context: Context, private val store: ModelStore) {

    private data class Net(val online: Boolean, val unmetered: Boolean)

    val status: Flow<ModelStatus> = combine(store.progress, network()) { progress, net ->
        ModelStatus.of(
            ready = store.ready() != null,
            progress = progress,
            downloaded = store.downloaded(),
            online = net.online,
            unmetered = net.unmetered,
        )
    }.distinctUntilChanged()

    private fun network(): Flow<Net> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        fun of(capabilities: NetworkCapabilities?) = Net(
            online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
        )
        trySend(of(connectivity.getNetworkCapabilities(connectivity.activeNetwork)))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(of(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(Net(online = false, unmetered = false))
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
