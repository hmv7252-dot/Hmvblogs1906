package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.ironsource.environment.ContextProvider
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.IronSourceBannerLayout
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.model.Placement
import com.ironsource.mediationsdk.sdk.LevelPlayBannerListener
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener
import com.ironsource.mediationsdk.sdk.LevelPlayRewardedVideoListener
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LevelPlayManager encapsulates modern Unity LevelPlay & IronSource mediation.
 * Provides unified ad loading and display for Interstitial, Rewarded, and Banner ads.
 */
object LevelPlayManager {

    private const val TAG = "LevelPlayManager"

    private val isInitializing = AtomicBoolean(false)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isInterstitialLoaded = MutableStateFlow(false)
    val isInterstitialLoaded: StateFlow<Boolean> = _isInterstitialLoaded.asStateFlow()

    private val _isRewardedLoaded = MutableStateFlow(false)
    val isRewardedLoaded: StateFlow<Boolean> = _isRewardedLoaded.asStateFlow()

    private val _isBannerLoaded = MutableStateFlow(false)
    val isBannerLoaded: StateFlow<Boolean> = _isBannerLoaded.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    data class MediatedNetwork(
        val companyName: String,
        val adapterStatus: String,
        val sdkVersion: String,
        val isIntegrated: Boolean = true
    )

    val mediatedNetworks = listOf(
        MediatedNetwork("Unity Ads", "Adapter v4.3.65 (Active)", "SDK v4.12.5"),
        MediatedNetwork("Google AdMob", "Adapter v4.3.43 (Active)", "Play Services Ads v23.0.0"),
        MediatedNetwork("Meta Audience Network", "Adapter v4.3.52 (Active)", "Audience SDK v6.18.0"),
        MediatedNetwork("ironSource Network", "Built-in Mediation", "LevelPlay v8.6.0")
    )

    private val _activeNetworkName = MutableStateFlow("Unity LevelPlay Multi-Network")
    val activeNetworkName: StateFlow<String> = _activeNetworkName.asStateFlow()

    // Ad instances
    private var interstitialAd: LevelPlayInterstitialAd? = null
    private var rewardedAd: LevelPlayRewardedAd? = null
    private var bannerAdView: LevelPlayBannerAdView? = null
    private var ironSourceBannerView: IronSourceBannerLayout? = null

    // Callbacks for currently showing ads
    private var pendingInterstitialCloseCallback: (() -> Unit)? = null
    private var pendingRewardCallback: (() -> Unit)? = null
    private var pendingRewardedCloseCallback: (() -> Unit)? = null
    private var userEarnedCurrentReward = false

    private var isShowingInterstitial = false
    private var isShowingRewarded = false

    private var networkCallbackRegistered = false
    private var currentActivity: Activity? = null
    private var lifecycleRegistered = false

    fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Updates IronSource and LevelPlay with the current foreground Activity.
     */
    fun updateCurrentActivity(activity: Activity) {
        currentActivity = activity
        try {
            ContextProvider.getInstance().updateActivity(activity)
            IronSource.onResume(activity)
        } catch (e: Throwable) {
            Log.w(TAG, "Notice updating IronSource activity: ${e.message}")
        }
    }

    private fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun canResolveMediationHost(): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName("i-sdk.mediation.unity3d.com")
            address.hostAddress != null && address.hostAddress.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun registerNetworkCallback(
        context: Context,
        key: String,
        onSuccess: (() -> Unit)?,
        onFailure: ((String) -> Unit)?
    ) {
        if (networkCallbackRegistered) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            lateinit var callback: ConnectivityManager.NetworkCallback
            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (canResolveMediationHost()) {
                            try {
                                cm.unregisterNetworkCallback(callback)
                                networkCallbackRegistered = false
                            } catch (_: Exception) {}

                            withContext(Dispatchers.Main) {
                                if (!_isInitialized.value && isInitializing.compareAndSet(false, true)) {
                                    internalInit(context, key, onSuccess, onFailure)
                                }
                            }
                        }
                    }
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback registration notice: ${e.message}")
        }
    }

    /**
     * Initializes Unity LevelPlay SDK. Must be called once during app startup (e.g. in MainActivity).
     */
    fun initialize(
        context: Context,
        appKey: String = AdConfig.APP_KEY,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val activity = (context as? Activity) ?: context.findActivity()
        if (activity != null) {
            updateCurrentActivity(activity)
        }

        if (!lifecycleRegistered && appContext is Application) {
            appContext.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(act: Activity, savedInstanceState: Bundle?) {
                    updateCurrentActivity(act)
                }
                override fun onActivityStarted(act: Activity) {
                    updateCurrentActivity(act)
                }
                override fun onActivityResumed(act: Activity) {
                    updateCurrentActivity(act)
                }
                override fun onActivityPaused(act: Activity) {}
                override fun onActivityStopped(act: Activity) {}
                override fun onActivitySaveInstanceState(act: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(act: Activity) {
                    if (currentActivity == act) {
                        currentActivity = null
                    }
                }
            })
            lifecycleRegistered = true
        }

        if (_isInitialized.value) {
            Log.d(TAG, "LevelPlay already initialized.")
            onSuccess?.invoke()
            return
        }

        if (!isInitializing.compareAndSet(false, true)) {
            Log.d(TAG, "LevelPlay initialization already in progress.")
            return
        }

        val targetKey = if (appKey.isBlank()) AdConfig.TEST_APP_KEY else appKey

        CoroutineScope(Dispatchers.IO).launch {
            if (!isNetworkConnected(appContext) || !canResolveMediationHost()) {
                Log.i(TAG, "Network or mediation server is currently unreachable. LevelPlay will initialize once connectivity is available.")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Offline Mode"
                    isInitializing.set(false)
                }
                registerNetworkCallback(appContext, targetKey, onSuccess, onFailure)
                return@launch
            }

            withContext(Dispatchers.Main) {
                internalInit(activity ?: appContext, targetKey, onSuccess, onFailure)
            }
        }
    }

    private fun formatAdInfo(adInfo: LevelPlayAdInfo?): String {
        if (adInfo == null) return "Unknown"
        val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
        val adUnitId = try { adInfo.getAdUnitId() } catch (_: Throwable) { "" }
        val instance = try { adInfo.getInstanceName() } catch (_: Throwable) { "" }
        val revenue = try { adInfo.getRevenue() } catch (_: Throwable) { 0.0 }
        return "network=$network, adUnitId=$adUnitId, instance=$instance, revenue=$revenue"
    }

    private fun setupIronSourceLegacyListeners() {
        try {
            IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
                override fun onAdReady(adInfo: AdInfo) {
                    isInterstitialLoading.set(false)
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "INTERSTITIAL_AD_LOADED: [Legacy IronSource, Network: $network]")
                    _isInterstitialLoaded.value = true
                    adInfo.adNetwork?.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                    _statusMessage.value = "Interstitial Ready ($network)"
                }

                override fun onAdLoadFailed(error: IronSourceError) {
                    isInterstitialLoading.set(false)
                    Log.w(TAG, "INTERSTITIAL_AD_FAILED: code=${error.errorCode}, message=${error.errorMessage}")
                }

                override fun onAdOpened(adInfo: AdInfo) {
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "INTERSTITIAL_AD_OPENED: [Legacy IronSource, Network: $network]")
                    adInfo.adNetwork?.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                    _statusMessage.value = "Interstitial Displayed ($network)"
                }

                override fun onAdShowSucceeded(adInfo: AdInfo) {
                    Log.i(TAG, "INTERSTITIAL_SHOW_SUCCEEDED: network=${adInfo.adNetwork}")
                }

                override fun onAdShowFailed(error: IronSourceError, adInfo: AdInfo) {
                    isInterstitialLoading.set(false)
                    Log.e(TAG, "INTERSTITIAL_AD_DISPLAY_FAILED: [Network: ${adInfo.adNetwork}] code=${error.errorCode}, message=${error.errorMessage}")
                    isShowingInterstitial = false
                    pendingInterstitialCloseCallback?.invoke()
                    pendingInterstitialCloseCallback = null
                    loadInterstitial()
                }

                override fun onAdClicked(adInfo: AdInfo) {
                    Log.i(TAG, "INTERSTITIAL_AD_CLICKED: network=${adInfo.adNetwork}")
                }

                override fun onAdClosed(adInfo: AdInfo) {
                    isInterstitialLoading.set(false)
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "INTERSTITIAL_AD_CLOSED: [Legacy IronSource, Network: $network]")
                    _isInterstitialLoaded.value = false
                    isShowingInterstitial = false
                    lastInterstitialShowTime = System.currentTimeMillis()
                    pendingInterstitialCloseCallback?.invoke()
                    pendingInterstitialCloseCallback = null
                    loadInterstitial()
                }
            })

            IronSource.setLevelPlayRewardedVideoListener(object : LevelPlayRewardedVideoListener {
                override fun onAdAvailable(adInfo: AdInfo) {
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "REWARDED_AD_LOADED: [Legacy IronSource, Network: $network]")
                    _isRewardedLoaded.value = true
                    adInfo.adNetwork?.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                    _statusMessage.value = "Rewarded Ready ($network)"
                }

                override fun onAdUnavailable() {
                    Log.w(TAG, "REWARDED_AD_FAILED: Ad unavailable / No fill currently.")
                    _isRewardedLoaded.value = false
                }

                override fun onAdOpened(adInfo: AdInfo) {
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "REWARDED_AD_OPENED: [Legacy IronSource, Network: $network]")
                    adInfo.adNetwork?.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                    _statusMessage.value = "Rewarded Displayed ($network)"
                }

                override fun onAdShowFailed(error: IronSourceError, adInfo: AdInfo) {
                    Log.e(TAG, "REWARDED_AD_DISPLAY_FAILED: [Network: ${adInfo.adNetwork}] code=${error.errorCode}, message=${error.errorMessage}")
                    isShowingRewarded = false
                    pendingRewardCallback = null
                    pendingRewardedCloseCallback?.invoke()
                    pendingRewardedCloseCallback = null
                    loadRewarded()
                }

                override fun onAdClicked(placement: Placement, adInfo: AdInfo) {
                    Log.i(TAG, "REWARDED_AD_CLICKED: placement=${placement.placementName}, network=${adInfo.adNetwork}")
                }

                override fun onAdRewarded(placement: Placement, adInfo: AdInfo) {
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "REWARD_RECEIVED: placement=${placement.placementName}, reward=${placement.rewardName}, amount=${placement.rewardAmount} [Network: $network]")
                    userEarnedCurrentReward = true
                    pendingRewardCallback?.invoke()
                    pendingRewardCallback = null
                }

                override fun onAdClosed(adInfo: AdInfo) {
                    val network = adInfo.adNetwork ?: "LevelPlay"
                    Log.i(TAG, "REWARDED_AD_CLOSED: [Legacy IronSource, Network: $network]")
                    _isRewardedLoaded.value = false
                    isShowingRewarded = false
                    // If user closed without receiving reward callback, cancel pending reward
                    pendingRewardCallback = null
                    pendingRewardedCloseCallback?.invoke()
                    pendingRewardedCloseCallback = null
                    loadRewarded()
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "Notice setting legacy listeners: ${e.message}")
        }
    }

    private var lastInterstitialShowTime: Long = 0L

    fun canShowInterstitial(): Boolean {
        val elapsed = System.currentTimeMillis() - lastInterstitialShowTime
        return isInterstitialReady() && (lastInterstitialShowTime == 0L || elapsed >= AdConfig.INTERSTITIAL_MIN_INTERVAL_MS)
    }

    private fun internalInit(
        context: Context,
        key: String,
        onSuccess: (() -> Unit)?,
        onFailure: ((String) -> Unit)?
    ) {
        Log.i(TAG, "LEVELPLAY_INIT_STARTED with App Key: $key")
        _statusMessage.value = "Initializing LevelPlay..."

        val act = (context as? Activity) ?: context.findActivity() ?: currentActivity
        if (act != null) {
            updateCurrentActivity(act)
        }
        val targetContext = act ?: context

        // 1. Configure user privacy & consent flags for LevelPlay / IronSource / Google Play compliance
        try {
            IronSource.setConsent(true)
            IronSource.setMetaData("do_not_sell", "false")
            IronSource.setMetaData("is_child_directed", "false")
            Log.i(TAG, "Privacy consent settings applied: GDPR consent=true, CCPA do_not_sell=false, COPPA=false")
        } catch (e: Throwable) {
            Log.w(TAG, "Notice applying privacy settings: ${e.message}")
        }

        try {
            setupIronSourceLegacyListeners()

            val initRequest = LevelPlayInitRequest.Builder(key)
                .withLegacyAdFormats(listOf(
                    LevelPlay.AdFormat.BANNER,
                    LevelPlay.AdFormat.INTERSTITIAL,
                    LevelPlay.AdFormat.REWARDED
                ))
                .build()

            LevelPlay.init(targetContext, initRequest, object : LevelPlayInitListener {
                override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                    isInitializing.set(false)
                    _isInitialized.value = true
                    Log.i(TAG, "LEVELPLAY_INIT_SUCCESS (AppKey: $key)")
                    _statusMessage.value = "Initialized Successfully"
                    onSuccess?.invoke()

                    // Automatically load ads after initialization
                    loadInterstitial()
                    loadRewarded()
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    val errorDesc = "code: ${error.errorCode}, message: ${error.errorMessage}"
                    Log.e(TAG, "LEVELPLAY_INIT_FAILED: $errorDesc (Key: $key)")

                    // If a newly created dashboard key is rejected with error 2110 (pending backend propagation),
                    // allow fallback to official test key so development/testing is never blocked.
                    if (error.errorCode == 2110 && key != AdConfig.TEST_APP_KEY) {
                        Log.i(TAG, "App Key '$key' pending dashboard sync (2110). Retrying with test key ${AdConfig.TEST_APP_KEY}...")
                        _statusMessage.value = "Retrying with Test Key..."
                        internalInit(context, AdConfig.TEST_APP_KEY, onSuccess, onFailure)
                        return
                    }

                    isInitializing.set(false)
                    _isInitialized.value = false
                    _statusMessage.value = "Init: ${error.errorMessage}"
                    onFailure?.invoke(errorDesc)
                }
            })
        } catch (e: Exception) {
            isInitializing.set(false)
            _isInitialized.value = false
            Log.e(TAG, "LEVELPLAY_INIT_FAILED with exception: ${e.message}", e)
            _statusMessage.value = "Init: ${e.message}"
            onFailure?.invoke(e.message ?: "Unknown error")
        }
    }

    // =========================================================================
    // INTERSTITIAL ADS
    // =========================================================================

    fun loadInterstitial() {
        if (!_isInitialized.value) {
            Log.w(TAG, "Cannot load interstitial: LevelPlay not initialized yet.")
            return
        }

        if (isInterstitialReady()) {
            return
        }

        if (!isInterstitialLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Interstitial load is already in progress.")
            return
        }

        try {
            IronSource.loadInterstitial()
        } catch (e: Throwable) {
            Log.w(TAG, "IronSource.loadInterstitial notice: ${e.message}")
        }

        val adUnitId = AdConfig.INTERSTITIAL_AD_UNIT_ID
        if (adUnitId.isNotBlank()) {
            try {
                if (interstitialAd == null) {
                    interstitialAd = LevelPlayInterstitialAd(adUnitId).apply {
                        setListener(object : LevelPlayInterstitialAdListener {
                            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                                isInterstitialLoading.set(false)
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "INTERSTITIAL_AD_LOADED: [Mediation Network: $network] Details: ${formatAdInfo(adInfo)}")
                                _isInterstitialLoaded.value = true
                                network.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                                _statusMessage.value = "Interstitial Ready ($network)"
                            }

                            override fun onAdLoadFailed(error: LevelPlayAdError) {
                                isInterstitialLoading.set(false)
                                Log.w(TAG, "INTERSTITIAL_AD_FAILED: code=${error.getErrorCode()}, message=${error.getErrorMessage()}")
                            }

                            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "INTERSTITIAL_AD_OPENED: [Mediation Network: $network] Details: ${formatAdInfo(adInfo)}")
                                network.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                                _statusMessage.value = "Interstitial Displayed ($network)"
                            }

                            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                                isInterstitialLoading.set(false)
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.e(TAG, "INTERSTITIAL_AD_DISPLAY_FAILED: [Network: $network] code=${error.getErrorCode()}, message=${error.getErrorMessage()}")
                                isShowingInterstitial = false
                                pendingInterstitialCloseCallback?.invoke()
                                pendingInterstitialCloseCallback = null
                                loadInterstitial()
                            }

                            override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "INTERSTITIAL_AD_CLICKED: [Network: $network]")
                            }

                            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                                isInterstitialLoading.set(false)
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "INTERSTITIAL_AD_CLOSED: [Mediation Network: $network]")
                                _isInterstitialLoaded.value = false
                                isShowingInterstitial = false
                                lastInterstitialShowTime = System.currentTimeMillis()
                                pendingInterstitialCloseCallback?.invoke()
                                pendingInterstitialCloseCallback = null
                                // Automatically prepare next interstitial
                                loadInterstitial()
                            }

                            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { null }
                                network?.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                            }
                        })
                    }
                }
                interstitialAd?.loadAd()
            } catch (e: Throwable) {
                isInterstitialLoading.set(false)
                Log.w(TAG, "LevelPlayInterstitialAd load notice: ${e.message}")
            }
        }
    }

    fun isInterstitialReady(): Boolean {
        return try {
            (interstitialAd?.isAdReady() == true) || IronSource.isInterstitialReady()
        } catch (e: Exception) {
            false
        }
    }

    fun showInterstitial(
        activity: Activity,
        force: Boolean = false,
        onClosed: (() -> Unit)? = null
    ) {
        if (isShowingInterstitial) {
            Log.w(TAG, "Interstitial already showing.")
            onClosed?.invoke()
            return
        }

        val elapsed = System.currentTimeMillis() - lastInterstitialShowTime
        if (!force && lastInterstitialShowTime != 0L && elapsed < AdConfig.INTERSTITIAL_MIN_INTERVAL_MS) {
            Log.d(TAG, "Interstitial throttled to prevent spamming ($elapsed ms < ${AdConfig.INTERSTITIAL_MIN_INTERVAL_MS} ms).")
            onClosed?.invoke()
            return
        }

        updateCurrentActivity(activity)

        if (!isInterstitialReady()) {
            Log.d(TAG, "Interstitial not ready yet. Requesting load and executing callback.")
            onClosed?.invoke()
            loadInterstitial()
            return
        }

        try {
            isShowingInterstitial = true
            pendingInterstitialCloseCallback = onClosed
            if (interstitialAd?.isAdReady() == true) {
                interstitialAd?.showAd(activity)
            } else if (IronSource.isInterstitialReady()) {
                IronSource.showInterstitial(activity)
            } else {
                isShowingInterstitial = false
                onClosed?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "INTERSTITIAL_AD_FAILED with exception", e)
            isShowingInterstitial = false
            pendingInterstitialCloseCallback = null
            onClosed?.invoke()
            loadInterstitial()
        }
    }

    // =========================================================================
    // REWARDED ADS
    // =========================================================================

    fun loadRewarded() {
        if (!_isInitialized.value) {
            Log.w(TAG, "Cannot load rewarded: LevelPlay not initialized yet.")
            return
        }

        try {
            if (IronSource.isRewardedVideoAvailable()) {
                _isRewardedLoaded.value = true
            }
        } catch (_: Throwable) {}

        val adUnitId = AdConfig.REWARDED_AD_UNIT_ID
        if (adUnitId.isNotBlank()) {
            try {
                if (rewardedAd == null) {
                    rewardedAd = LevelPlayRewardedAd(adUnitId).apply {
                        setListener(object : LevelPlayRewardedAdListener {
                            override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "REWARDED_AD_LOADED: [Mediation Network: $network] Details: ${formatAdInfo(adInfo)}")
                                _isRewardedLoaded.value = true
                                network.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                                _statusMessage.value = "Rewarded Ready ($network)"
                            }

                            override fun onAdLoadFailed(error: LevelPlayAdError) {
                                Log.w(TAG, "REWARDED_AD_FAILED: code=${error.getErrorCode()}, message=${error.getErrorMessage()}")
                            }

                            override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "REWARDED_AD_OPENED: [Mediation Network: $network] Details: ${formatAdInfo(adInfo)}")
                                network.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                                _statusMessage.value = "Rewarded Displayed ($network)"
                            }

                            override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.e(TAG, "REWARDED_AD_DISPLAY_FAILED: [Network: $network] code=${error.getErrorCode()}, message=${error.getErrorMessage()}")
                                isShowingRewarded = false
                                pendingRewardCallback = null
                                pendingRewardedCloseCallback?.invoke()
                                pendingRewardedCloseCallback = null
                                loadRewarded()
                            }

                            override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "REWARDED_AD_CLICKED: [Network: $network]")
                            }

                            override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "REWARD_RECEIVED: reward=${reward.name}, amount=${reward.amount} [Mediation Network: $network]")
                                userEarnedCurrentReward = true
                                pendingRewardCallback?.invoke()
                                pendingRewardCallback = null
                            }

                            override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { "LevelPlay" }
                                Log.i(TAG, "REWARDED_AD_CLOSED: [Mediation Network: $network]")
                                _isRewardedLoaded.value = false
                                isShowingRewarded = false
                                // Discard pending reward if user closed before reward callback
                                pendingRewardCallback = null
                                pendingRewardedCloseCallback?.invoke()
                                pendingRewardedCloseCallback = null
                                // Automatically prepare next rewarded ad
                                loadRewarded()
                            }

                            override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {
                                val network = try { adInfo.getAdNetwork() } catch (_: Throwable) { null }
                                network?.takeIf { it.isNotBlank() }?.let { _activeNetworkName.value = it }
                            }
                        })
                    }
                }
                rewardedAd?.loadAd()
            } catch (e: Throwable) {
                Log.w(TAG, "LevelPlayRewardedAd load notice: ${e.message}")
            }
        }
    }

    fun isRewardedReady(): Boolean {
        return try {
            (rewardedAd?.isAdReady() == true) || IronSource.isRewardedVideoAvailable()
        } catch (e: Exception) {
            false
        }
    }

    fun showRewarded(
        activity: Activity,
        onReward: () -> Unit,
        onClosed: (() -> Unit)? = null
    ) {
        if (isShowingRewarded) {
            Log.w(TAG, "Rewarded ad already showing.")
            onClosed?.invoke()
            return
        }

        updateCurrentActivity(activity)

        if (!isRewardedReady()) {
            Log.d(TAG, "Rewarded ad not ready. Requesting load and executing callback.")
            onClosed?.invoke()
            loadRewarded()
            return
        }

        try {
            isShowingRewarded = true
            userEarnedCurrentReward = false
            pendingRewardCallback = onReward
            pendingRewardedCloseCallback = onClosed
            if (rewardedAd?.isAdReady() == true) {
                rewardedAd?.showAd(activity)
            } else if (IronSource.isRewardedVideoAvailable()) {
                IronSource.showRewardedVideo(activity)
            } else {
                isShowingRewarded = false
                onClosed?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "REWARDED_AD_FAILED with exception", e)
            isShowingRewarded = false
            pendingRewardCallback = null
            pendingRewardedCloseCallback = null
            onClosed?.invoke()
            loadRewarded()
        }
    }


    // =========================================================================
    // BANNER ADS
    // =========================================================================

    /**
     * Creates or returns a banner View instance (LevelPlayBannerAdView or IronSourceBannerLayout).
     */
    fun getOrCreateBannerView(context: Context): View? {
        val act = (context as? Activity) ?: context.findActivity() ?: currentActivity
        if (act != null) {
            updateCurrentActivity(act)
        }

        if (bannerAdView != null) {
            return bannerAdView
        }
        if (ironSourceBannerView != null) {
            return ironSourceBannerView
        }

        val placementId = AdConfig.BANNER_PLACEMENT_ID
        val bannerContext = act ?: context

        // 1. Try LevelPlay modern banner view
        if (!AdConfig.isPlaceholder(placementId) && placementId.isNotBlank()) {
            try {
                bannerAdView = LevelPlayBannerAdView(bannerContext, placementId).apply {
                    setAdSize(LevelPlayAdSize.BANNER)
                    setBannerListener(object : LevelPlayBannerAdViewListener {
                        override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                            Log.i(TAG, "BANNER_LOADED: info=$adInfo")
                            _isBannerLoaded.value = true
                            _statusMessage.value = "Banner Ready"
                        }

                        override fun onAdLoadFailed(error: LevelPlayAdError) {
                            Log.w(TAG, "BANNER_LOAD_FAILED: $error")
                        }

                        override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {
                            Log.i(TAG, "BANNER_DISPLAYED")
                        }

                        override fun onAdDisplayFailed(adInfo: LevelPlayAdInfo, error: LevelPlayAdError) {
                            Log.e(TAG, "BANNER_DISPLAY_FAILED: $error")
                        }

                        override fun onAdClicked(adInfo: LevelPlayAdInfo) {
                            Log.i(TAG, "BANNER_CLICKED")
                        }

                        override fun onAdExpanded(adInfo: LevelPlayAdInfo) {}
                        override fun onAdCollapsed(adInfo: LevelPlayAdInfo) {}
                        override fun onAdLeftApplication(adInfo: LevelPlayAdInfo) {}
                    })
                    loadAd()
                }
                return bannerAdView
            } catch (e: Throwable) {
                Log.w(TAG, "LevelPlayBannerAdView notice: ${e.message}")
            }
        }

        // 2. Fallback to IronSource banner if Activity is available
        if (act != null) {
            try {
                val bannerLayout = IronSource.createBanner(act, ISBannerSize.BANNER)
                bannerLayout?.setLevelPlayBannerListener(object : LevelPlayBannerListener {
                    override fun onAdLoaded(adInfo: AdInfo) {
                        Log.i(TAG, "LEGACY_BANNER_LOADED: $adInfo")
                        _isBannerLoaded.value = true
                        _statusMessage.value = "Banner Ready"
                    }

                    override fun onAdLoadFailed(error: IronSourceError) {
                        Log.w(TAG, "LEGACY_BANNER_LOAD_FAILED: code=${error.errorCode}")
                    }

                    override fun onAdClicked(adInfo: AdInfo) {}
                    override fun onAdLeftApplication(adInfo: AdInfo) {}
                    override fun onAdScreenPresented(adInfo: AdInfo) {}
                    override fun onAdScreenDismissed(adInfo: AdInfo) {}
                })
                ironSourceBannerView = bannerLayout
                if (bannerLayout != null) {
                    IronSource.loadBanner(bannerLayout)
                }
                return ironSourceBannerView
            } catch (e: Throwable) {
                Log.w(TAG, "IronSource.createBanner notice: ${e.message}")
            }
        }

        return null
    }

    /**
     * Attaches banner to a native ViewGroup container.
     */
    fun showBanner(activity: Activity, container: ViewGroup) {
        val banner = getOrCreateBannerView(activity) ?: return
        try {
            if (banner.parent != null) {
                (banner.parent as? ViewGroup)?.removeView(banner)
            }
            container.removeAllViews()
            container.addView(banner)
            bannerAdView?.resumeAutoRefresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying banner in container", e)
        }
    }

    /**
     * Hides the banner and pauses auto-refresh.
     */
    fun hideBanner() {
        try {
            bannerAdView?.pauseAutoRefresh()
            (bannerAdView?.parent as? ViewGroup)?.removeView(bannerAdView)
            (ironSourceBannerView?.parent as? ViewGroup)?.removeView(ironSourceBannerView)
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding banner", e)
        }
    }

    // =========================================================================
    // LIFECYCLE MANAGEMENT
    // =========================================================================

    fun onResume(activity: Activity) {
        try {
            updateCurrentActivity(activity)
            bannerAdView?.resumeAutoRefresh()
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming banner auto refresh", e)
        }
    }

    fun onPause(activity: Activity) {
        try {
            IronSource.onPause(activity)
            bannerAdView?.pauseAutoRefresh()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing banner auto refresh", e)
        }
    }

    fun onDestroy(activity: Activity) {
        try {
            bannerAdView?.destroy()
            bannerAdView = null
            ironSourceBannerView?.let { IronSource.destroyBanner(it) }
            ironSourceBannerView = null
            _isBannerLoaded.value = false
            pendingInterstitialCloseCallback = null
            pendingRewardCallback = null
            pendingRewardedCloseCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying LevelPlay ads", e)
        }
    }
}
