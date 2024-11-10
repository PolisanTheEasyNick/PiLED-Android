package dev.polisan.piled

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

enum class OP(var value: Int) {
    LED_SET_COLOR(0),
    LED_GET_CURRENTCOLOR(1),
    ANIM_SET_FADE(2),
    ANIM_SET_PULSE(3),
    SYS_TOGGLE_SUSPEND(4),
    SYS_COLOR_CHANGED(5)
}

object PiLED {
    private var sharedSecret: String? = null
    var defaultIp = "192.168.0.5"
    var defaultPort = "3384"
    private const val version = 4
    lateinit var appContext: Context

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isConnected by mutableStateOf(false)
        private set

    var currentColor by mutableStateOf(Color(0, 0, 0))
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("PiLEDPrefs", Context.MODE_PRIVATE)

        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            prefs.edit()
                .putString("shared_secret", "shared_secret")
                .putString("piled_ip", "192.168.0.4")
                .putString("piled_port", "3384")
                .putBoolean("isFirstRun", false)
                .apply()
            Log.d("PiLED", "Default settings applied on first startup")
        }

        sharedSecret = prefs.getString("shared_secret", null)
        if (sharedSecret == null) {
            Log.w("PiLED", "Shared secret not found in preferences. Please set it in settings.")
            Toast.makeText(context, "Shared secret not found in preferences. Please set it in settings.", Toast.LENGTH_LONG).show()
        }

        defaultIp = prefs.getString("piled_ip", null) ?: "192.168.0.4"
        defaultPort = prefs.getString("piled_port", null) ?: "3384"
        Log.d("PiLED", "Initialized PiLED with $defaultIp:$defaultPort")
    }

    private inline fun <T> withSharedSecret(action: () -> T): T? {
        val prefs = appContext.getSharedPreferences("PiLEDPrefs", Context.MODE_PRIVATE)
        sharedSecret = prefs.getString("shared_secret", null)
        return if (sharedSecret != null) {
            action()
        } else {
            Log.e("PiLED", "Shared secret is not defined. Please set it in settings.")
            Toast.makeText(appContext, "Shared secret is not defined. Please set it in settings.", Toast.LENGTH_LONG).show()
            null
        }
    }

    /**
     * Establishes a connection to the server at the specified IP and port.
     */
    suspend fun connect(ip: String = defaultIp, port: String = defaultPort, onConnected: (() -> Unit)? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket = Socket()
                socket!!.connect(InetSocketAddress(ip, port.toInt()), 5000)
                inputStream = BufferedInputStream(socket!!.getInputStream())
                outputStream = socket!!.getOutputStream()
                isConnected = true
                Log.d("PiLED", "Connected to $ip:$port")
                startListening()

                onConnected?.invoke()
                requestColor()
                true
            } catch (e: Exception) {
                Log.e("PiLED", "Connection failed: ${e.message}")
                disconnect()
                false
            }
        }
    }


    /**
     * Closes the connection.
     */
    fun disconnect() {
        coroutineScope.coroutineContext.cancelChildren()
        socket?.close()
        inputStream?.close()
        outputStream?.close()
        socket = null
        inputStream = null
        outputStream = null
        isConnected = false
        Log.d("PiLED", "Disconnected")
    }

    /**
     * Starts a listener that waits for incoming packets and spawns a coroutine for each.
     */
    private fun startListening() {
        coroutineScope.launch {
            try {
                while (socket?.isConnected == true) {
                    val buffer = ByteArray(55)  // Adjust size as per protocol
                    val bytesRead = inputStream?.read(buffer)
                    if (bytesRead != null && bytesRead > 0) {
                        handleReceivedData(buffer.copyOf(bytesRead))
                    }
                }
            } catch (e: Exception) {
                Log.e("PiLED", "Error in listening: ${e}")
                disconnect()
            }
        }
    }

    /**
     * Handles received data by parsing it in a background coroutine.
     */
    private fun handleReceivedData(data: ByteArray) {
        coroutineScope.launch {
            val tag = "PiLED-Parser"
            // Parse the data as needed. For now, log the received data.
            Log.d(tag, "Received data: ${data.joinToString(" ") { "%02x".format(it) }}")
            //parsing HEADER
            val receivedVersion = data[16].toInt()
            val receivedOP = data[17].toInt()
            Log.d(tag, "Version: $receivedVersion")
            Log.d(tag, "Operational Code: $receivedOP")
            when(receivedOP) {
                OP.SYS_COLOR_CHANGED.value -> {
                    Log.d(tag, "SYS_COLOR_CHANGED")
                    val red = data[50].toInt() and 0xFF
                    val green = data[51].toInt() and 0xFF
                    val blue = data[52].toInt() and 0xFF
                    currentColor = Color(red / 255f, green / 255f, blue / 255f)
                    Log.d(tag, "New color: $currentColor")
                }
            }
        }
    }

    /**
     * Sends color data to the server.
     */
    fun sendColor(color: Color) = withSharedSecret {
        val header = generateHeader(OP.LED_SET_COLOR)
        val red = (color.red * 255).toInt().toByte()
        val green = (color.green * 255).toInt().toByte()
        val blue = (color.blue * 255).toInt().toByte()
        val payload = byteArrayOf(red, green, blue, 0, 0)
        val packet = createPacket(header, payload)
        coroutineScope.launch {
            sendPacket(packet)
        }
    }

    /**
     * Sends a toggle suspend command to the server.
     */
    fun sendSuspend() = withSharedSecret {
        val header = generateHeader(OP.SYS_TOGGLE_SUSPEND)
        val payload = byteArrayOf(209.toByte(), 0.toByte(), 255.toByte(), 3.toByte(), 0.toByte())
        val packet = createPacket(header, payload)
        coroutineScope.launch {
            sendPacket(packet)
        }
    }

    /**
     * Start Fade animation
     */
    fun startFadeAnimation(color: Color, duration: Int, speed: Int) = withSharedSecret {
        val header = generateHeader(OP.ANIM_SET_FADE)
        val payload = byteArrayOf((color.red*255).toInt().toByte(), (color.green*255).toInt().toByte(), (color.blue*255).toInt().toByte(), duration.toByte(), speed.toByte())
        val packet = createPacket(header, payload)
        coroutineScope.launch {
            sendPacket(packet)
        }
    }

    fun startPulseAnimation(color: Color, duration: Int, speed: Int) = withSharedSecret {
        val header = generateHeader(OP.ANIM_SET_PULSE)
        //                                    v-- tf is that to int to byte cursed shit
        val payload = byteArrayOf((color.red*255).toInt().toByte(), (color.green*255).toInt().toByte(), (color.blue*255).toInt().toByte(), duration.toByte(), speed.toByte())
        val packet = createPacket(header, payload)
        coroutineScope.launch {
            sendPacket(packet)
        }
    }

    /**
     * Requests the current color from the server.
     */
    fun requestColor() = withSharedSecret {
        val header = generateHeader(OP.LED_GET_CURRENTCOLOR)
        val packet = createPacket(header, ByteArray(0))
        coroutineScope.launch {
            sendPacket(packet)
        }
    }

    private fun generateHeader(opCode: OP): ByteArray {
        val timestamp = System.currentTimeMillis() / 1000
        val nonce = Random.nextLong()
        val timestampBytes = ByteBuffer.allocate(8).putLong(timestamp).array()
        val nonceBytes = ByteBuffer.allocate(8).putLong(nonce).array()
        return timestampBytes + nonceBytes + version.toByte() + opCode.value.toByte()
    }

    private fun createPacket(header: ByteArray, payload: ByteArray): ByteArray {
        if(sharedSecret == null) return byteArrayOf()
        val hmac = hmacSha256(sharedSecret!!, header + payload)
        return header + hmac + payload
    }

    private suspend fun sendPacket(data: ByteArray){
        return withContext(Dispatchers.IO) {
            try {
                val address = socket?.remoteSocketAddress?.toString() ?: "Unknown"
                Log.d("PiLED", "Sending packet to $address")

                outputStream?.write(data)
                outputStream?.flush()
                Log.d("PiLED", "Packet sent to $address")
            } catch (e: Exception) {
                Log.e("PiLED", "Error sending packet to ${socket?.remoteSocketAddress}: ${e}")
                disconnect()
            }
        }
    }


    private fun hmacSha256(secret: String, data: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }
}
