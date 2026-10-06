package com.example.security

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central coordinator for application security, anti-cheat detection,
 * integrity verification, and secure storage in Slidecraft.
 */
object SecurityManager {

    private const val TAG = "SlidecraftSecurity"

    enum class RiskLevel {
        SAFE,
        LOW,
        MEDIUM,
        HIGH
    }

    private var isInitialized = false

    private val _securityAlerts = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 5)
    val securityAlerts: SharedFlow<String> = _securityAlerts.asSharedFlow()

    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        // 1. Initialize Secure Storage & Android Keystore
        SecureStorageManager.initialize(appContext)

        // 2. Perform Environment & Integrity Risk Assessment
        val environmentReport = IntegrityManager.assessEnvironment(appContext)
        logSecurityEvent("INIT_RISK_CHECK", "Assessed environment risk: ${environmentReport.riskLevel}")

        isInitialized = true
    }

    /**
     * Dispatches a user-friendly generic verification warning.
     * Never exposes internal security heuristics to end users or logs.
     */
    fun notifySecurityWarning(reason: String = "Verification Notice") {
        logSecurityEvent("SUSPICIOUS_ACTIVITY", reason)
        _securityAlerts.tryEmit("Game data could not be verified.")
    }

    /**
     * Safe developer logging. Completely stripped/disabled in release builds.
     * Strictly avoids logging passwords, API keys, credentials, or user identifiable info.
     */
    fun logDebug(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun logSecurityEvent(eventType: String, nonSensitiveDescription: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "[$eventType] $nonSensitiveDescription")
        }
    }
}
