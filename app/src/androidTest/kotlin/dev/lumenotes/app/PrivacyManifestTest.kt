package dev.lumenotes.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyManifestTest {
    @Suppress("DEPRECATION")
    @Test
    fun mergedManifestDoesNotRequestNetworkAccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertFalse(Manifest.permission.INTERNET in requested)
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in requested)
    }

    @Suppress("DEPRECATION")
    @Test
    fun mergedManifestHasNoServicesOrBroadcastReceivers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        assertEquals(emptyList<String>(), info.services.orEmpty().map { it.name })
        assertEquals(emptyList<String>(), info.receivers.orEmpty().map { it.name })
    }
}
