package com.baak.astronode.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.baak.astronode.core.constants.AppConstants
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

enum class UsbConnectionState {
    DISCONNECTED,
    PERMISSION_PENDING,
    CONNECTED,
    ERROR
}

@Singleton
class SqmUsbManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.baak.astronode.USB_PERMISSION"
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow(UsbConnectionState.DISCONNECTED)
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    private var serialPort: UsbSerialPort? = null
    private var driverName: String? = null

    val connectedDriverName: String? get() = driverName

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> tryConnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> disconnect()
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) openConnection()
                    else _connectionState.value = UsbConnectionState.ERROR
                }
            }
        }
    }

    fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun unregisterReceiver() {
        runCatching { context.unregisterReceiver(usbReceiver) }
    }

    fun tryConnect() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            _connectionState.value = UsbConnectionState.DISCONNECTED
            return
        }

        val driver = availableDrivers[0]
        val device: UsbDevice = driver.device
        driverName = driver.javaClass.simpleName

        if (usbManager.hasPermission(device)) {
            openConnection()
        } else {
            _connectionState.value = UsbConnectionState.PERMISSION_PENDING
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val permIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permIntent)
        }
    }

    private fun openConnection() {
        try {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (drivers.isEmpty()) {
                _connectionState.value = UsbConnectionState.ERROR
                return
            }
            val driver = drivers[0]
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) {
                _connectionState.value = UsbConnectionState.ERROR
                return
            }

            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                AppConstants.SQM_BAUD_RATE,
                AppConstants.SQM_DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            serialPort = port
            _connectionState.value = UsbConnectionState.CONNECTED
        } catch (e: Exception) {
            _connectionState.value = UsbConnectionState.ERROR
        }
    }

    fun disconnect() {
        runCatching { serialPort?.close() }
        serialPort = null
        driverName = null
        _connectionState.value = UsbConnectionState.DISCONNECTED
    }

    suspend fun readMeasurement(): SqmReading? = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext null
        try {
            withTimeout(AppConstants.SQM_READ_TIMEOUT_MS) {
                port.write(SqmProtocol.buildReadCommand(), 1000)
                val buffer = ByteArray(256)
                val len = port.read(buffer, AppConstants.SQM_READ_TIMEOUT_MS.toInt())
                if (len > 0) {
                    val response = String(buffer, 0, len, Charsets.US_ASCII)
                    SqmProtocol.parseResponse(response)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
