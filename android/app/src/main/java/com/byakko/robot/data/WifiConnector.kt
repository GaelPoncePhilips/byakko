package com.byakko.robot.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier

/**
 * Se une a la red del robot con WifiNetworkSpecifier (Android 10+). El sistema
 * muestra un diálogo para confirmar; la red queda sólo para esta app y el
 * celular conserva sus datos móviles para el resto.
 */
class WifiConnector(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun request(
        ssid: String,
        passphrase: String,
        onAvailable: (Network) -> Unit,
        onLost: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        release()
        val spec = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onAvailable(network)
            override fun onLost(network: Network) = onLost()
            override fun onUnavailable() = onUnavailable()
        }
        callback = cb
        cm.requestNetwork(req, cb, 60_000)
    }

    fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }
}
