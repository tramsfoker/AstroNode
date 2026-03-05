package com.baak.astronode.data.usb

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
import java.io.IOException
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
        const val ACTION_USB_PERMISSION = "com.baak.astronode.USB_PERMISSION"
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow(UsbConnectionState.DISCONNECTED)
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    private var usbConnection: android.hardware.usb.UsbDeviceConnection? = null
    private var serialPort: UsbSerialPort? = null
    private var pendingDevice: UsbDevice? = null

    private val deviceAttachedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && serialPort == null) {
                        pendingDevice = device
                        if (_connectionState.value == UsbConnectionState.DISCONNECTED) {
                            tryConnect(device)
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null) {
                        pendingDevice = null
                        disconnect()
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted) {
                        openPort(device)
                    } else if (device != null && !granted) {
                        _connectionState.value = UsbConnectionState.ERROR
                        pendingDevice = null
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(deviceAttachedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(deviceAttachedReceiver, filter)
        }

        // Başlangıçta bağlı cihaz var mı kontrol et
        usbManager.deviceList.values.firstOrNull()?.let { device ->
            pendingDevice = device
            tryConnect(device)
        }
    }

    fun connect() {
        val device = pendingDevice ?: usbManager.deviceList.values.firstOrNull()
        if (device != null) {
            pendingDevice = device
            tryConnect(device)
        } else {
            _connectionState.value = UsbConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        try {
            serialPort?.close()
        } catch (_: IOException) { }
        serialPort = null
        usbConnection?.close()
        usbConnection = null
        pendingDevice = null
        _connectionState.value = UsbConnectionState.DISCONNECTED
    }

    private fun tryConnect(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            _connectionState.value = UsbConnectionState.PERMISSION_PENDING
            val pi = android.app.PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_USB_PERMISSION),
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager.requestPermission(device, pi)
        } else {
            openPort(device)
        }
    }

    private fun openPort(device: UsbDevice) {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val driver = drivers.find { it.device == device } ?: run {
            _connectionState.value = UsbConnectionState.ERROR
            return
        }
        val connection = usbManager.openDevice(driver.device) ?: run {
            _connectionState.value = UsbConnectionState.ERROR
            return
        }
        val port = driver.ports.getOrNull(0) ?: run {
            connection.close()
            _connectionState.value = UsbConnectionState.ERROR
            return
        }
        try {
            port.open(connection)
            port.setParameters(
                AppConstants.SQM_BAUD_RATE,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            usbConnection = connection
            serialPort = port
            _connectionState.value = UsbConnectionState.CONNECTED
        } catch (e: IOException) {
            try { port.close() } catch (_: IOException) { }
            connection.close()
            _connectionState.value = UsbConnectionState.ERROR
        }
    }

    /**
     * SQM ölçümü yapar: komut gönder → 3 saniye timeout ile yanıt bekle → parse et.
     * Dispatchers.IO üzerinde çalışır.
     */
    suspend fun readMeasurement(): SqmReading? = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext null
        if (!port.isOpen) return@withContext null

        try {
            val command = SqmProtocol.buildReadCommand()
            port.write(command, 1000)

            val buffer = ByteArray(256)
            val bytesRead = withTimeout(AppConstants.SQM_TIMEOUT_MS) {
                port.read(buffer, AppConstants.SQM_TIMEOUT_MS.toInt())
            }
            if (bytesRead <= 0) return@withContext null

            val raw = String(buffer, 0, bytesRead, Charsets.US_ASCII)
            SqmProtocol.parseResponse(raw)
        } catch (_: Exception) {
            null
        }
    }

}
