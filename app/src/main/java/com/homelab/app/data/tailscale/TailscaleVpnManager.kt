package com.homelab.app.data.tailscale

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class TailscaleState {
    NOT_INSTALLED,
    DISCONNECTED,
    CONNECTED
}

@Singleton
class TailscaleVpnManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
        private const val TAILSCALE_RECEIVER = "com.tailscale.ipn.IPNReceiver"
        private const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
        private const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"
        private const val PLAY_STORE_URI = "market://details?id=$TAILSCALE_PACKAGE"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(checkCurrentState())
    val state: StateFlow<TailscaleState> = _state.asStateFlow()

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isTailscaleNetwork(network)) {
                _state.value = TailscaleState.CONNECTED
            }
        }

        override fun onLost(network: Network) {
            // Re-check — another VPN might still be active
            _state.value = if (isTailscaleInstalled()) TailscaleState.DISCONNECTED
                           else TailscaleState.NOT_INSTALLED
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        connectivityManager.registerNetworkCallback(request, vpnCallback)
    }

    fun connect() {
        if (!isTailscaleInstalled()) return

        // Workaround for tailscale/tailscale#14148: launch the app first,
        // then send CONNECT_VPN after a short delay
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(TAILSCALE_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            sendBroadcast(ACTION_CONNECT)
        }, 1500)
    }

    fun disconnect() {
        sendBroadcast(ACTION_DISCONNECT)
    }

    fun openApp() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(TAILSCALE_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) {
            context.startActivity(intent)
        }
    }

    fun openPlayStore() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(PLAY_STORE_URI)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sendBroadcast(action: String) {
        val intent = Intent(action).apply {
            setClassName(TAILSCALE_PACKAGE, TAILSCALE_RECEIVER)
        }
        context.sendBroadcast(intent)
    }

    private fun checkCurrentState(): TailscaleState {
        if (!isTailscaleInstalled()) return TailscaleState.NOT_INSTALLED

        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null && isTailscaleNetwork(activeNetwork)) {
            return TailscaleState.CONNECTED
        }
        return TailscaleState.DISCONNECTED
    }

    private fun isTailscaleInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun isTailscaleNetwork(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false

        val linkProps = connectivityManager.getLinkProperties(network) ?: return false
        return hasTailscaleAddress(linkProps)
    }

    private fun hasTailscaleAddress(linkProps: LinkProperties): Boolean =
        linkProps.linkAddresses.any { linkAddr ->
            val addr = linkAddr.address.hostAddress ?: return@any false
            isTailscaleCgnatAddress(addr)
        }

    /**
     * Tailscale assigns addresses in 100.64.0.0/10 (CGNAT range: 100.64.x.x – 100.127.x.x)
     */
    private fun isTailscaleCgnatAddress(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 100 && parts[1] in 64..127
    }
}
