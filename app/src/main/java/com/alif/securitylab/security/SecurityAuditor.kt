package com.alif.securitylab.security

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.alif.securitylab.model.SecurityAuditReport
import java.io.File

class SecurityAuditor(private val context: Context) {
    fun generateReport(): SecurityAuditReport {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val hasScreenLock = keyguardManager.isDeviceSecure
        val patchDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Build.VERSION.SECURITY_PATCH else "Unknown (Pre-Android 6)"

        val installedPackages = try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }
        } catch (_: Exception) { emptyList() }

        return SecurityAuditReport(
            hasScreenLock = hasScreenLock,
            securityPatchDate = patchDate,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            isDeviceRootedCheck = checkRootExecutables(),
            installedAppsCount = installedPackages.size,
            installedPackages = installedPackages
        )
    }

    private fun checkRootExecutables(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }
}
