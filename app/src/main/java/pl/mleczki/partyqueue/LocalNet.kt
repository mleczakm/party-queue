package pl.mleczki.partyqueue

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNet {
    /** Private IPv4 addresses of this device, Wi-Fi / hotspot interfaces first. */
    fun addresses(): List<String> {
        val found = ArrayList<Pair<Int, String>>()
        for (nif in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
            if (!nif.isUp || nif.isLoopback) continue
            val rank = when {
                nif.name.startsWith("wlan") -> 0
                nif.name.startsWith("ap") || nif.name.startsWith("swlan") -> 1
                nif.name.startsWith("eth") -> 2
                else -> 3
            }
            for (a in nif.inetAddresses.toList()) {
                if (a is Inet4Address && a.isSiteLocalAddress) found += rank to a.hostAddress!!
            }
        }
        return found.sortedBy { it.first }.map { it.second }
    }
}
