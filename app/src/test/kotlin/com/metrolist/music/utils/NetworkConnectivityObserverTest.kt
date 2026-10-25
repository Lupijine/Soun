package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Config(sdk = [33], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class NetworkConnectivityObserverTest {
    @Test
    fun defaultRouteChangesAndStaleCallbacksDoNotLoseTheReplacementNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val observer = NetworkConnectivityObserver(context)
        try {
            val callback = shadowOf(manager).networkCallbacks.single()
            val wifi = ShadowNetwork.newInstance(101)
            val vpn = ShadowNetwork.newInstance(102)
            val internet = NetworkCapabilities()
            shadowOf(internet).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            callback.onAvailable(wifi)
            callback.onCapabilitiesChanged(wifi, internet)
            assertTrue(observer.networkStatus.value)

            callback.onAvailable(vpn)
            callback.onCapabilitiesChanged(vpn, internet)
            assertEquals(vpn, observer.defaultNetwork.value)
            callback.onLost(wifi)
            callback.onCapabilitiesChanged(wifi, NetworkCapabilities())
            assertEquals(vpn, observer.defaultNetwork.value)
            assertTrue(observer.networkStatus.value)

            callback.onLost(vpn)
            assertNull(observer.defaultNetwork.value)
            assertFalse(observer.networkStatus.value)

            callback.onAvailable(wifi)
            callback.onCapabilitiesChanged(wifi, internet)
            assertEquals(wifi, observer.defaultNetwork.value)
            assertTrue(observer.networkStatus.value)
        } finally {
            observer.unregister()
            observer.unregister()
        }
    }

    @Test
    fun networkStatusIsSharedAcrossCollectors() = runBlocking {
        val observer = NetworkConnectivityObserver(ApplicationProvider.getApplicationContext<Context>())

        withTimeout(1_000) {
            val firstCollector = async { observer.networkStatus.first() }
            val secondCollector = async { observer.networkStatus.first() }

            assertEquals(firstCollector.await(), secondCollector.await())
        }
    }
}
