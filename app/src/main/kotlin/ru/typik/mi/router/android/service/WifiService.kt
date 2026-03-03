package ru.typik.mi.router.android.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

class WifiService(
    context: Context
) {
    private val appContext = context.applicationContext

    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filterNot(::isPermissionGranted)
    }

    @SuppressLint("MissingPermission")
    fun getWifiSSID(): String? {
        return runCatching {
            if (!hasWifiAccessPermission()) {
                null
            } else {
                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return@runCatching null

                val rawSsid = wifiManager.connectionInfo?.ssid ?: return@runCatching null
                val normalizedSsid = rawSsid.removeSurrounding("\"")
                normalizedSsid.takeIf {
                    it.isNotBlank() && it != WifiManager.UNKNOWN_SSID
                }
            }
        }.getOrNull()
    }

    private fun hasWifiAccessPermission(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return permissions
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
