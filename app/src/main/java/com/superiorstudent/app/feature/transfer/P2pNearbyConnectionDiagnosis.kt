package com.superiorstudent.app.feature.transfer

/** Produces practical guidance from local checks without claiming to detect router client-isolation directly. */
internal object P2pNearbyConnectionDiagnosis {
    fun advice(
        wifiEnabled: Boolean,
        wifiNetworkConnected: Boolean,
        hasPrivateWifiAddress: Boolean,
        nearbyPermissionGranted: Boolean
    ): String = when {
        !nearbyPermissionGranted ->
            "Nearby Wi-Fi permission is missing. Allow it, then retry local discovery."
        !hasPrivateWifiAddress && !wifiNetworkConnected && !wifiEnabled ->
            "No local Wi-Fi path was detected. Turn on Wi-Fi, or start a hotspot on one phone and connect the other phone to it."
        !hasPrivateWifiAddress && !wifiNetworkConnected ->
            "Android did not detect a local Wi-Fi address. Join the same Wi-Fi network on both phones or connect one phone to the other's hotspot."
        !hasPrivateWifiAddress ->
            "Wi-Fi appears available, but no private Wi-Fi IPv4 address was found. Reconnect to Wi-Fi or try a phone hotspot, then check again."
        else ->
            "A local Wi-Fi interface was detected. If nearby discovery still fails, the phones may be on different networks or guest Wi-Fi/client isolation may block device-to-device discovery. Try a non-guest Wi-Fi network or use a phone hotspot."
    }
}
