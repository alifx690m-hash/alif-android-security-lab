package com.alif.securitylab.network

import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.alif.securitylab.security.SecurityAuditor
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.net.NetworkInterface
import java.util.Collections

class LabWebSocketManager(
    private val context: Context,
    private val onLogAdded: (String, String) -> Unit,
    private val onConnectionStatusChanged: (Boolean) -> Unit,
    private val onSessionTokenReceived: (String) -> Unit
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val auditor = SecurityAuditor(context)

    fun connect(serverUrl: String, deviceId: String, token: String, pairingCode: String? = null) {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnectionStatusChanged(true)
                onLogAdded("CONNECTED", "Established connection to $serverUrl")
                val msg = JsonObject()
                if (pairingCode != null) {
                    msg.addProperty("type", "ENROLL")
                    msg.addProperty("pairing_code", pairingCode)
                    msg.addProperty("device_id", deviceId)
                    msg.addProperty("device_name", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                } else {
                    msg.addProperty("type", "CONNECT")
                    msg.addProperty("device_id", deviceId)
                    msg.addProperty("session_token", token)
                }
                webSocket.send(gson.toJson(msg))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = gson.fromJson(text, JsonObject::class.java)
                    when (obj.get("type")?.asString) {
                        "ENROLL_SUCCESS" -> {
                            val token = obj.get("session_token")?.asString
                            if (!token.isNullOrBlank()) {
                                onSessionTokenReceived(token)
                            }
                            onLogAdded("ENROLLED", "Device paired successfully.")
                        }
                        "ENROLL_FAILED" -> { onLogAdded("ERROR", obj.get("message").asString); disconnect() }
                        "EXECUTE_COMMAND" -> handleServerCommand(obj.get("command").asString)
                    }
                } catch (e: Exception) {
                    onLogAdded("ERROR", "Parsing error: ${e.localizedMessage}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionStatusChanged(false)
                onLogAdded("DISCONNECTED", "Connection closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnectionStatusChanged(false)
                onLogAdded("NETWORK_ERROR", t.message ?: "Unknown socket failure")
            }
        })
    }

    private fun handleServerCommand(command: String) {
        val response = JsonObject()
        when (command) {
            "SYS_INFO" -> {
                val result = JsonObject().apply {
                    addProperty("model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    addProperty("android_version", android.os.Build.VERSION.RELEASE)
                    addProperty("sdk_int", android.os.Build.VERSION.SDK_INT)
                    addProperty("board", android.os.Build.BOARD)
                }
                response.addProperty("type", "DIAGNOSTIC_RESPONSE")
                response.addProperty("command", command)
                response.add("result", result)
            }
            "BATTERY_INFO" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val result = JsonObject().apply {
                    addProperty("battery_percentage", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                    addProperty("is_charging", bm.isCharging)
                }
                response.addProperty("type", "DIAGNOSTIC_RESPONSE")
                response.addProperty("command", command)
                response.add("result", result)
            }
            "STORAGE_INFO" -> {
                val stat = StatFs(Environment.getDataDirectory().path)
                val totalBytes = stat.blockCountLong * stat.blockSizeLong
                val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                val result = JsonObject().apply {
                    addProperty("total_gb", totalBytes / (1024 * 1024 * 1024))
                    addProperty("free_gb", freeBytes / (1024 * 1024 * 1024))
                    addProperty("used_percentage", ((totalBytes - freeBytes).toDouble() / totalBytes * 100).toInt())
                }
                response.addProperty("type", "DIAGNOSTIC_RESPONSE")
                response.addProperty("command", command)
                response.add("result", result)
            }
            "NETWORK_INFO" -> {
                val result = JsonObject()
                try {
                    for (intf in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        for (addr in Collections.list(intf.inetAddresses)) {
                            if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false)
                                result.addProperty(intf.name, addr.hostAddress)
                        }
                    }
                } catch (e: Exception) { result.addProperty("error", e.localizedMessage) }
                response.addProperty("type", "DIAGNOSTIC_RESPONSE")
                response.addProperty("command", command)
                response.add("result", result)
            }
            "RUN_SECURITY_AUDIT" -> {
                response.addProperty("type", "AUDIT_RESPONSE")
                response.add("result", gson.toJsonTree(auditor.generateReport()))
            }
        }
        webSocket?.send(gson.toJson(response))
        onLogAdded("AUDIT_DISPATCH", "Handled command: $command")
    }

    fun disconnect() {
        webSocket?.close(1000, "User initiated unpair/disconnect")
        webSocket = null
        onConnectionStatusChanged(false)
    }
}
