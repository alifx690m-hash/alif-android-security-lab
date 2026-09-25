package com.alif.securitylab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alif.securitylab.model.LogEntry
import com.alif.securitylab.model.PairingState
import com.alif.securitylab.network.LabWebSocketManager
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) { MainLabApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLabApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ALIF_LAB_PREFS", Context.MODE_PRIVATE) }
    var pairingState by remember {
        mutableStateOf(PairingState(
            isPaired = prefs.getBoolean("is_paired", false),
            serverUrl = prefs.getString("server_url", "ws://192.168.1.100:8765") ?: "",
            deviceId = prefs.getString("device_id", UUID.randomUUID().toString()) ?: "",
            sessionToken = prefs.getString("session_token", "") ?: ""
        ))
    }
    val logs = remember { mutableStateListOf<LogEntry>() }
    var isConnected by remember { mutableStateOf(false) }
    val logHandler = { event: String, details: String ->
        logs.add(0, LogEntry(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()), event, details))
    }
    val wsManager = remember {
        LabWebSocketManager(
            context = context,
            onLogAdded = logHandler,
            onConnectionStatusChanged = { status -> isConnected = status },
            onSessionTokenReceived = { token ->
                pairingState = pairingState.copy(isPaired = true, sessionToken = token)
                prefs.edit().putString("session_token", token).putBoolean("is_paired", true).apply()
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("ALIF SECURITY LAB", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isConnected) Color(0xFF1B5E20) else Color(0xFF333333))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (isConnected) "STATUS: ENROLLED & CONNECTED" else "STATUS: NOT CONNECTED", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(if (pairingState.isPaired) "Server: ${pairingState.serverUrl}" else "Device unpaired", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }

            if (!pairingState.isPaired) {
                EnrollmentView(pairingState.serverUrl) { url, code ->
                    val devId = UUID.randomUUID().toString().take(8)
                    pairingState = pairingState.copy(serverUrl = url, deviceId = devId, isPaired = false, sessionToken = "")
                    prefs.edit().putString("server_url", url).putString("device_id", devId).putBoolean("is_paired", false).remove("session_token").apply()
                    wsManager.connect(url, devId, "", code)
                }
            } else {
                DashboardView(pairingState, isConnected, logs,
                    onReconnect = { wsManager.connect(pairingState.serverUrl, pairingState.deviceId, pairingState.sessionToken) },
                    onUnpair = { wsManager.disconnect(); prefs.edit().clear().apply(); pairingState = PairingState() })
            }
        }
    }
}

@Composable
fun EnrollmentView(initialUrl: String, onEnroll: (String, String) -> Unit) {
    var url by remember { mutableStateOf(initialUrl) }
    var code by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Column(Modifier.padding(16.dp)) {
            Text("Enroll Device to Lab Server", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Cyan)
            Spacer(Modifier.height(8.dp))
            Text("Enter the server address and generated pairing code from Termux CLI.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(url, { url = it }, label = { Text("Server WebSocket URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(code, { code = it }, label = { Text("Pairing Code (6 Digits)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (url.isNotBlank() && code.isNotBlank()) onEnroll(url, code) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("Enroll & Connect")
            }
        }
    }
}

@Composable
fun DashboardView(pairingState: PairingState, isConnected: Boolean, logs: List<LogEntry>,
                  onReconnect: () -> Unit, onUnpair: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onReconnect, enabled = !isConnected) { Text("Reconnect") }
            Button(onClick = onUnpair) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Unpair Device") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Device Activity & Audit Log", fontWeight = FontWeight.Bold, color = Color.LightGray)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            LazyColumn(Modifier.padding(12.dp)) {
                items(logs) { log ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Text("[${log.timestamp}] ", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                            Text(log.event, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (log.details.isNotBlank()) Text(log.details, fontSize = 12.sp, color = Color.White)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
