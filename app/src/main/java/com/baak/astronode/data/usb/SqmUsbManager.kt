package com.baak.astronode.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.baak.astronode.core.constants.AppConstants
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

    private val knownDevices = listOf(
        Pair(4292, 60000),   // CP2102
        Pair(6790, 29987),   // CH340
        Pair(1027, 24577),   // FTDI
        Pair(1659, 8963)     // PL2303
    )

    private fun isKnownSqmDevice(device: UsbDevice): Boolean =
        knownDevices.any { it.first == device.vendorId && it.second == device.productId }

    fun scanConnectedDevices() {
        val deviceList = usbManager.deviceList ?: return
        if (deviceList.isEmpty()) return

        for ((_, device) in deviceList) {
            if (isKnownSqmDevice(device)) {
                if (usbManager.hasPermission(device)) {
                    tryConnect()
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
                return
            }
        }
    }

    fun registerReceiver() {
        scanConnectedDevices()
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
            port.setDTR(true)
            port.setRTS(true)

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
        try {
            var port = serialPort
            if (port == null) {
                Log.e("SQM", "Port null - bağlantı yok, yeniden tarama deneniyor")
                scanConnectedDevices()
                delay(500)
                port = serialPort
                if (port == null) {
                    Log.e("SQM", "Port hâlâ null")
                    return@withContext null
                }
            }

            // Önce buffer'ı temizle (eski veri kalmış olabilir)
            val flushBuffer = ByteArray(1024)
            try {
                port.read(flushBuffer, 100)
            } catch (_: Exception) {}
            delay(200)

            // Komutu gönder
            val command = "rx\r".toByteArray(Charsets.US_ASCII)
            Log.d("SQM", "Komut gönderiliyor: ${command.map { it.toInt() }}")
            port.write(command, 1000)

            // Yanıt oku — birden fazla parça gelebilir
            val responseBuilder = StringBuilder()
            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()
            val timeout = 5000L

            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    val bytesRead = port.read(buffer, 1000)
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                        Log.d("SQM", "Okunan ($bytesRead byte): '$chunk'")
                        responseBuilder.append(chunk)

                        val fullResponse = responseBuilder.toString()
                        if (fullResponse.contains("\r") || fullResponse.contains("\n")) {
                            if (fullResponse.contains("m,")) {
                                Log.d("SQM", "Tam yanıt: '$fullResponse'")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SQM", "Okuma hatası: ${e.message}")
                }
            }

            val rawResponse = responseBuilder.toString().trim()
            Log.d("SQM", "Ham yanıt: '$rawResponse'")

            if (rawResponse.isEmpty()) {
                Log.e("SQM", "Boş yanıt - cihaz cevap vermedi")
                return@withContext null
            }

            val reading = SqmProtocol.parseResponse(rawResponse)
            if (reading == null) {
                Log.e("SQM", "Parse başarısız. Raw: '$rawResponse'")
            } else {
                Log.d("SQM", "Başarılı! MPSAS: ${reading.mpsas}")
            }
            reading
        } catch (e: Exception) {
            Log.e("SQM", "readMeasurement exception: ${e.message}", e)
            null
        }
    }
}
