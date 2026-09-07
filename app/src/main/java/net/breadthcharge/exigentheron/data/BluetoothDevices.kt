package net.breadthcharge.exigentheron.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class BondedBluetoothDevice(val name: String, val address: String)

/**
 * `BLUETOOTH_CONNECT` (minSdk 31, so this is the only permission model
 * that applies — no pre-S `BLUETOOTH` fallback needed) is this app's
 * only runtime permission, requested only once the user turns on the
 * per-device Bluetooth allow/deny feature in Settings — see
 * `Settings.bluetoothDeviceControlEnabled`'s own doc comment for why
 * that's off by default rather than requested up front.
 *
 * Returns every *paired* device (`BluetoothAdapter.getBondedDevices()`),
 * not just currently-connected ones — the settings screen lets a device
 * be allowed/denied before it's actively connected, the same way the
 * rule editor's app picker (`InstalledApps.kt`) lists every installed
 * app, not just ones with a rule already. Returns an empty list without
 * throwing when the permission isn't granted, mirroring
 * [loadInstalledApps][net.breadthcharge.exigentheron.ui.rules.loadInstalledApps]'s
 * "just don't have the data" shape rather than a caller needing to
 * catch `SecurityException`.
 *
 * Blocking (`BluetoothAdapter` calls) — call from a background dispatcher.
 */
fun loadBondedBluetoothDevices(context: Context): List<BondedBluetoothDevice> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        ?: return emptyList()
    return adapter.bondedDevices
        .map { BondedBluetoothDevice(name = it.name ?: it.address, address = it.address) }
        .sortedBy { it.name.lowercase() }
}
