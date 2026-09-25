package com.alif.securitylab.model

data class PairingState(
    val isPaired: Boolean = false,
    val serverUrl: String = "",
    val deviceId: String = "",
    val sessionToken: String = "",
    val isConnected: Boolean = false,
    val lastSeenTime: String = "Never"
)

data class SecurityAuditReport(
    val hasScreenLock: Boolean,
    val securityPatchDate: String,
    val androidVersion: String,
    val isDeviceRootedCheck: Boolean,
    val installedAppsCount: Int,
    val installedPackages: List<String>
)

data class LogEntry(
    val timestamp: String,
    val event: String,
    val details: String
)
