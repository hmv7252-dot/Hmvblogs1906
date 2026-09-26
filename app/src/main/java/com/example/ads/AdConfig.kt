package com.example.ads

/**
 * Unity LevelPlay configuration.
 *
 * Configured with production LevelPlay credentials and ad unit IDs.
 */
object AdConfig {

    const val UNITY_GAME_ID = "6193472"

    /**
     * LevelPlay App Key provided by the developer dashboard.
     */
    const val APP_KEY: String = "283de2d75"

    /**
     * LevelPlay Interstitial Ad Unit ID.
     */
    const val INTERSTITIAL_AD_UNIT_ID = "saupmzkm985mh2ei"

    /**
     * LevelPlay Rewarded Ad Unit ID.
     */
    const val REWARDED_AD_UNIT_ID = "upghsmeckwiwhpl5"

    /**
     * Fallback placement IDs for LevelPlay / IronSource listeners.
     */
    const val INTERSTITIAL_PLACEMENT_ID = "saupmzkm985mh2ei"

    const val REWARDED_PLACEMENT_ID = "upghsmeckwiwhpl5"

    const val BANNER_PLACEMENT_ID = "DefaultBanner"

    /**
     * Fallback test key for local automated test runners.
     */
    const val TEST_APP_KEY = "85460dcd"

    /**
     * Minimum cooldown between interstitial ad displays to prevent spamming (30 seconds).
     */
    const val INTERSTITIAL_MIN_INTERVAL_MS = 30_000L

    /**
     * Helper to verify whether an ad placement ID has been replaced with the full ID
     * or is still a placeholder.
     */
    fun isPlaceholder(id: String): Boolean {
        return id.isBlank() || id.contains("PASTE_FULL")
    }
}

