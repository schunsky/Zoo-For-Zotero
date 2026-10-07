package com.mickstarify.zooforzotero.search

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtil {
    /** True on Wi-Fi or another connection that isn't billed by the megabyte. */
    fun isUnmetered(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
