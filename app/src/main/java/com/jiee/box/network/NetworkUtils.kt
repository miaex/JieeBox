package com.jiee.box.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Finds the IPv4 address of this device that other devices connected to its
 * Wi-Fi hotspot would use to reach it.
 *
 * There is no single official Android API for "give me my hotspot IP" (WifiManager's
 * DHCP APIs are deprecated/restricted on modern Android, and behave inconsistently
 * across vendors). The reliable cross-vendor approach is to enumerate network
 * interfaces directly: the hotspot interface is typically named "ap0", "wlan0",
 * "swlan0" or similar, and carries a private IPv4 address once the hotspot is on.
 */
object NetworkUtils {

    // Interface name prefixes seen across manufacturers when a hotspot is active.
    private val PREFERRED_PREFIXES = listOf("ap", "wlan", "swlan", "softap")

    /**
     * Returns the best-guess local IPv4 address to advertise, or null if no
     * suitable interface is currently up (e.g. hotspot not enabled yet).
     */
    fun findLocalIPv4(): String? {
        val candidates = mutableListOf<Pair<String, String>>() // name to address

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = Collections.list(iface.inetAddresses)
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        candidates.add(iface.name.lowercase() to addr.hostAddress.orEmpty())
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }

        if (candidates.isEmpty()) return null

        // Prefer an interface whose name matches a known hotspot pattern.
        val preferred = candidates.firstOrNull { (name, _) ->
            PREFERRED_PREFIXES.any { name.startsWith(it) }
        }
        return (preferred ?: candidates.first()).second
    }
}
