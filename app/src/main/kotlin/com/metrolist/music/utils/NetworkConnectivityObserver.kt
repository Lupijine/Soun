/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus = _networkStatus.asStateFlow()

    private val _defaultNetwork = MutableStateFlow(connectivityManager.activeNetwork)
    val defaultNetwork = _defaultNetwork.asStateFlow()
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_defaultNetwork.value != network) _networkStatus.value = false
            _defaultNetwork.value = network
        }

        override fun onLost(network: Network) {
            // The old route can disappear after the replacement is already available.
            if (_defaultNetwork.value == network) {
                _networkStatus.value = false
                _defaultNetwork.value = null
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (_defaultNetwork.value == network) {
                _networkStatus.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            registered = true
        } catch (e: Exception) {
            // Fallback: assume connected if registration fails
            _networkStatus.value = true
        }
    }

    fun unregister() {
        if (registered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            registered = false
        }
    }
    
    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            // Check if we have internet capability
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }
}
