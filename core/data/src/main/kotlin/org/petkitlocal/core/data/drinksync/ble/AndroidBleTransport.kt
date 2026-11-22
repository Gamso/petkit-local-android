package org.petkitlocal.core.data.drinksync.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure

/**
 * [BleTransport] on Android's own Bluetooth API (no library): a scan filtered on the fountain's
 * address, then GATT over LE as PetKit's app opens it (`PetkitBleClientImpl.connect`:
 * `connectGatt(context, false, callback, TRANSPORT_LE)`; `onServicesDiscovered`: notifications on
 * the notify characteristic through its 0x2902 descriptor; `GattCallback.onDescriptorWrite`:
 * `requestMtu(255)`).
 *
 * The screen has checked the permissions (`BluetoothAccess`); one revoked meanwhile surfaces as
 * a [SecurityException], said as [FountainLinkFailure.PermissionMissing].
 */
@SuppressLint("MissingPermission") // Checked before a sync starts; a SecurityException is mapped.
class AndroidBleTransport @Inject constructor(@ApplicationContext private val context: Context) : BleTransport {
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun availability(): BluetoothAvailability {
        val adapter = adapter
        return when {
            adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ->
                BluetoothAvailability.Unavailable

            adapter.isEnabled -> BluetoothAvailability.Ready

            else -> BluetoothAvailability.Off
        }
    }

    override suspend fun connect(mac: String): BleLink = try {
        val adapter = adapter ?: throw BleLinkException(FountainLinkFailure.BluetoothUnavailable, "no adapter")
        if (!adapter.isEnabled) throw BleLinkException(FountainLinkFailure.BluetoothOff, "Bluetooth is off")
        val address = mac.uppercase()
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw BleLinkException(FountainLinkFailure.NotFound, "not a Bluetooth address")
        }
        val device = withTimeoutOrNull(SCAN_TIMEOUT_MS) { scan(adapter, address) }
            ?: throw BleLinkException(FountainLinkFailure.NotFound, "not found within ${SCAN_TIMEOUT_MS / MS_PER_S} s")
        open(device)
    } catch (e: SecurityException) {
        throw BleLinkException(FountainLinkFailure.PermissionMissing, "a Bluetooth permission is missing", e)
    }

    /** The first advertisement from [address]; the scan stops with it, or when cancelled. */
    private suspend fun scan(adapter: BluetoothAdapter, address: String): BluetoothDevice =
        suspendCancellableCoroutine { continuation ->
            val scanner = adapter.bluetoothLeScanner
                ?: throw BleLinkException(FountainLinkFailure.BluetoothOff, "no scanner: Bluetooth is off")
            val over = AtomicBoolean(false)
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (result.device.address.equals(address, ignoreCase = true) && over.compareAndSet(false, true)) {
                        stop(scanner, this)
                        continuation.resume(result.device)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    if (over.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            BleLinkException(FountainLinkFailure.NotFound, "the scan failed ($errorCode)"),
                        )
                    }
                }
            }
            continuation.invokeOnCancellation { if (over.compareAndSet(false, true)) stop(scanner, callback) }
            scanner.startScan(
                listOf(ScanFilter.Builder().setDeviceAddress(address).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
        }

    private fun stop(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback) {
        try {
            scanner.stopScan(callback)
        } catch (_: IllegalStateException) {
            // Bluetooth went off meanwhile: there is no scan to stop.
        }
    }

    /** Connects, once more after Android's common first-try failure (status 133). */
    private suspend fun open(device: BluetoothDevice): BleLink {
        var last = BleLinkException(FountainLinkFailure.ConnectionLost, "no connection")
        repeat(CONNECT_ATTEMPTS) {
            when (val attempt = attempt(device)) {
                is Attempt.Open -> return attempt.link
                is Attempt.Failed -> last = attempt.failure
            }
        }
        throw last
    }

    private sealed interface Attempt {
        class Open(val link: BleLink) : Attempt

        class Failed(val failure: BleLinkException) : Attempt
    }

    /** One connection attempt; a link that did not open is closed, whatever stopped it (cancellation too). */
    private suspend fun attempt(device: BluetoothDevice): Attempt {
        val link = AndroidBleLink(context, device)
        var opened = false
        return try {
            val ready = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { link.open() } != null
            opened = ready
            if (ready) {
                Attempt.Open(link)
            } else {
                Attempt.Failed(
                    BleLinkException(
                        FountainLinkFailure.NotFound,
                        "no connection within ${CONNECT_TIMEOUT_MS / MS_PER_S} s",
                    ),
                )
            }
        } catch (e: BleLinkException) {
            Attempt.Failed(e)
        } finally {
            if (!opened) link.close()
        }
    }

    private companion object {
        /** PetKit's own scan for a CTW3 (`PetkitBleParameter.PETKIT_CTW3_SCAN_TIME`, 30 000 ms). */
        const val SCAN_TIMEOUT_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val CONNECT_ATTEMPTS = 2
        const val MS_PER_S = 1000
    }
}

/** One GATT connection to a fountain, its callbacks turned into suspending calls and a channel. */
@SuppressLint("MissingPermission")
private class AndroidBleLink(private val context: Context, private val device: BluetoothDevice) : BleLink {
    override val notifications = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile
    override var mtu: Int = PetkitGatt.DEFAULT_MTU
        private set

    private val ready = CompletableDeferred<Unit>()
    private val writeLock = Mutex()

    @Volatile
    private var pendingWrite: CompletableDeferred<Int>? = null
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                    if (!gatt.discoverServices()) lost("service discovery did not start")

                newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS ->
                    lost("disconnected (status $status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(PetkitGatt.SERVICE)
            val write = service?.getCharacteristic(PetkitGatt.WRITE)
            val notify = service?.getCharacteristic(PetkitGatt.NOTIFY)
            if (status != BluetoothGatt.GATT_SUCCESS || write == null || notify == null) {
                lost("no PetKit service")
                return
            }
            writeCharacteristic = write
            gatt.setCharacteristicNotification(notify, true)
            val config = notify.getDescriptor(PetkitGatt.CLIENT_CONFIG)
            if (config == null || !writeDescriptor(gatt, config)) requestMtu(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            requestMtu(gatt)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@AndroidBleLink.mtu = mtu
            ready.complete(Unit)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == PetkitGatt.NOTIFY) notifications.trySend(value.copyOf())
        }

        @Deprecated("Called below Android 13 only")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid ==
                PetkitGatt.NOTIFY
            ) {
                characteristic.value?.let { notifications.trySend(it.copyOf()) }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWrite?.complete(status)
        }
    }

    // Deprecated in SDK 37 for a settings-object variant that Android 10-16 lack: this is PetKit's own call.
    @Suppress("DEPRECATION")
    suspend fun open() {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw BleLinkException(FountainLinkFailure.ConnectionLost, "connectGatt failed")
        ready.await()
    }

    override suspend fun write(bytes: ByteArray) = writeLock.withLock {
        val gatt = gatt ?: throw BleLinkException(FountainLinkFailure.ConnectionLost, "not connected")
        val characteristic = writeCharacteristic
            ?: throw BleLinkException(FountainLinkFailure.ConnectionLost, "no write characteristic")
        // A frame is at most 17 bytes, under any MTU; cut it all the same if it were longer.
        val chunk = (mtu - ATT_HEADER).coerceAtLeast(PetkitGatt.DEFAULT_MTU - ATT_HEADER)
        bytes.toList().chunked(chunk).forEach { part ->
            val done = CompletableDeferred<Int>()
            pendingWrite = done
            if (!send(gatt, characteristic, part.toByteArray())) {
                throw BleLinkException(FountainLinkFailure.ConnectionLost, "the write did not start")
            }
            val status = withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() }
                ?: throw BleLinkException(FountainLinkFailure.Timeout, "the write did not complete")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                throw BleLinkException(FountainLinkFailure.ConnectionLost, "the write failed ($status)")
            }
        }
    }

    override fun close() {
        notifications.close()
        ready.cancel()
        pendingWrite?.cancel()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    private fun lost(why: String) {
        val failure = BleLinkException(FountainLinkFailure.ConnectionLost, why)
        ready.completeExceptionally(failure)
        pendingWrite?.completeExceptionally(failure)
        notifications.close(failure)
    }

    private fun requestMtu(gatt: BluetoothGatt) {
        if (!gatt.requestMtu(PetkitGatt.REQUESTED_MTU)) ready.complete(Unit)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean {
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, enable) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = enable
            gatt.writeDescriptor(descriptor)
        }
    }

    /** The app writes with the characteristic's default type: with a response when it takes one. */
    @Suppress("DEPRECATION")
    private fun send(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, type) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = type
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
        }
    }

    private companion object {
        const val ATT_HEADER = 3
        const val WRITE_TIMEOUT_MS = 5_000L
    }
}
