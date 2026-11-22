package site.casiopea.cruizer

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.S_V2
import android.os.Build.VERSION_CODES.TIRAMISU
import org.junit.Assert.assertEquals
import org.junit.Test

class PermsTest {
    @Test fun android11UsesLocationForBleAndNoRuntimeConnect() {
        assertEquals(listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), Perms.required(R))
        assertEquals(emptyList<String>(), Perms.connect(R))
    }

    @Test fun android12SkipsNotificationPermission() {
        val nearby = listOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN)
        assertEquals(nearby, Perms.required(S))
        assertEquals(nearby, Perms.required(S_V2))
    }

    @Test fun android13AddsNotificationPermission() =
        assertEquals(listOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN, POST_NOTIFICATIONS), Perms.required(TIRAMISU))
}
