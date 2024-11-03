package dev.polisan.piled

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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

fun Color.toHexCode(): String {
    val red = this.red * 255
    val green = this.green * 255
    val blue = this.blue * 255
    return String.format("%02x%02x%02x", red.toInt(), green.toInt(), blue.toInt())
}

enum class OP(var value: Int) {
    LED_SET_COLOR(0),
    LED_GET_CURRENTCOLOR(1),
    ANIM_SET_FADE(2),
    ANIM_SET_PULSE(3),
    SYS_TOGGLE_SUSPEND(4),
    SYS_COLOR_CHANGED(5)
}

object PiLED {
    private const val sharedSecret = "shared_secret"
    private const val defaultIp = "192.168.0.5"
    private const val defaultPort = 3384
    private const val version = 4;

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isConnected by mutableStateOf(false)
        private set

    /**
     * Establishes a connection to the server at the specified IP and port.
     */
    suspend fun connect(ip: String = defaultIp, port: Int = defaultPort): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket = Socket()
                socket!!.connect(InetSocketAddress(ip, port), 5000)
                inputStream = BufferedInputStream(socket!!.getInputStream())
                outputStream = socket!!.getOutputStream()
                isConnected = true
                Log.d("PiLED", "Connected to $ip:$port")
                startListening()
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
                Log.e("PiLED", "Error in listening: ${e.message}")
                disconnect()
            }
        }
    }

    /**
     * Handles received data by parsing it in a background coroutine.
     */
    private fun handleReceivedData(data: ByteArray) {
        coroutineScope.launch {
            // Parse the data as needed. For now, log the received data.
            Log.d("PiLED", "Received data: ${data.joinToString(" ") { "%02x".format(it) }}")
            // Add actual parsing logic here
        }
    }

    /**
     * Sends color data to the server.
     */
    fun sendColor(color: Color) {
        val header = generateHeader(OP.LED_SET_COLOR)
        val red = (color.red * 255).toInt().toByte()
        val green = (color.green * 255).toInt().toByte()
        val blue = (color.blue * 255).toInt().toByte()
        val payload = byteArrayOf(red, green, blue, 3, 0)
        val packet = createPacket(header, payload)
        sendPacket(packet)
    }

    /**
     * Sends a toggle suspend command to the server.
     */
    fun sendSuspend() {
        val header = generateHeader(OP.SYS_TOGGLE_SUSPEND)
        val payload = byteArrayOf(209.toByte(), 0.toByte(), 255.toByte(), 3.toByte(), 0.toByte())
        val packet = createPacket(header, payload)
        sendPacket(packet)
    }

    /**
     * Requests the current color from the server.
     */
    suspend fun requestColor(): Color? {
        val header = generateHeader(OP.LED_GET_CURRENTCOLOR)
        val packet = createPacket(header, ByteArray(0))
        val response = sendPacket(packet, waitResponse = true) ?: return null

        return if (response.size >= 11) {
            val red = response[8].toInt() and 0xFF
            val green = response[9].toInt() and 0xFF
            val blue = response[10].toInt() and 0xFF
            Color(red / 255f, green / 255f, blue / 255f)
        } else {
            null
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
        val hmac = hmacSha256(sharedSecret, header + payload)
        return header + hmac + payload
    }

    private fun sendPacket(data: ByteArray, waitResponse: Boolean = false): ByteArray? {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            Log.d("PiLED", "Packet sent")
            if (waitResponse) inputStream?.readBytes() else null
        } catch (e: Exception) {
            Log.e("PiLED", "Error sending packet: ${e.message}")
            disconnect()
            null
        }
    }

    private fun hmacSha256(secret: String, data: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }
}
