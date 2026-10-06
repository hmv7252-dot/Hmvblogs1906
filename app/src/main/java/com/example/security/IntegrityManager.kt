package com.example.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import com.example.BuildConfig
import java.io.File

/**
 * Handles package integrity, installer heuristics, and environment risk signals.
 * Follows defensive security principles: treats signals as risk indicators
 * rather than hard-crashing or aggressively banning players.
 */
object IntegrityManager {

    private const val EXPECTED_PACKAGE_NAME = "com.my.slidecraft"

    data class EnvironmentReport(
        val isPackageValid: Boolean,
        val isRootSuspected: Boolean,
        val isDebuggerAttached: Boolean,
        val isHookingDetected: Boolean,
        val riskLevel: SecurityManager.RiskLevel,
        val signalCount: Int
    )

    private val COMMON_ROOT_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    )

    private val SUSPICIOUS_HOOKING_CLASSES = arrayOf(
        "de.robv.android.xposed.XposedBridge",
        "com.saurik.substrate.MS\$MethodHook"
    )

    /**
     * Assesses environment integrity and returns a weighted risk level.
     */
    fun assessEnvironment(context: Context): EnvironmentReport {
        var signals = 0

        // 1. Package Name Validation
        val isPackageValid = context.packageName == EXPECTED_PACKAGE_NAME
        if (!isPackageValid) {
            signals += 2
        }

        // 2. Multi-Vector Root Detection
        var isRootSuspected = false
        if (checkSuBinaryPresence() || checkTestKeys()) {
            isRootSuspected = true
            signals += 1
        }

        // 3. Debugger / Instrumentation Detection
        var isDebuggerAttached = false
        if (!BuildConfig.DEBUG) {
            val isAppDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val isDebugging = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
            if (isAppDebuggable || isDebugging) {
                isDebuggerAttached = true
                signals += 2
            }
        }

        // 4. Hooking / Framework Detection
        var isHookingDetected = false
        for (hookClass in SUSPICIOUS_HOOKING_CLASSES) {
            try {
                Class.forName(hookClass)
                isHookingDetected = true
                signals += 2
                break
            } catch (_: ClassNotFoundException) {
                // Normal
            }
        }

        val risk = when {
            signals >= 3 -> SecurityManager.RiskLevel.HIGH
            signals in 1..2 -> SecurityManager.RiskLevel.MEDIUM
            else -> SecurityManager.RiskLevel.SAFE
        }

        return EnvironmentReport(
            isPackageValid = isPackageValid,
            isRootSuspected = isRootSuspected,
            isDebuggerAttached = isDebuggerAttached,
            isHookingDetected = isHookingDetected,
            riskLevel = risk,
            signalCount = signals
        )
    }

    private fun checkSuBinaryPresence(): Boolean {
        for (path in COMMON_ROOT_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: SecurityException) {
                // Restricted access
            }
        }
        return false
    }

    private fun checkTestKeys(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
}
