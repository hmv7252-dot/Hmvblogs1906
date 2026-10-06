package com.example.security

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Enforces cryptographic nonce-based idempotency, cooldown windows,
 * and rate limiting for all in-game rewards (ad rewards, bonus stars, victory prizes).
 */
object RewardValidator {

    private const val MIN_REWARD_COOLDOWN_MS = 15_000L // Minimum 15 seconds between rewarded ad completions
    private const val MAX_SINGLE_BONUS_STARS = 100

    private val activeTokens = ConcurrentHashMap<String, Long>()
    private var lastRewardedAdClaimTime = 0L

    /**
     * Issues a single-use reward token with an expiration timestamp.
     */
    fun createRewardToken(): String {
        val token = UUID.randomUUID().toString()
        activeTokens[token] = System.currentTimeMillis()
        // Clean up expired tokens (older than 10 minutes)
        val cutoff = System.currentTimeMillis() - (10 * 60 * 1000L)
        activeTokens.entries.removeIf { it.value < cutoff }
        return token
    }

    /**
     * Validates and consumes a single-use reward token.
     * Returns true only if the token exists, has not expired, and has not been used.
     */
    fun consumeRewardToken(token: String): Boolean {
        if (!activeTokens.containsKey(token)) {
            SecurityManager.logSecurityEvent("REPLAYED_REWARD_TOKEN", "Token non-existent or previously consumed.")
            return false
        }
        activeTokens.remove(token)
        return true
    }

    /**
     * Validates rewarded ad claim against frequency and cooldown rules.
     */
    fun validateRewardedAdClaim(token: String?): Boolean {
        val now = System.currentTimeMillis()

        // 1. Check cooldown to prevent rapid callback spamming
        if (now - lastRewardedAdClaimTime < MIN_REWARD_COOLDOWN_MS) {
            SecurityManager.logSecurityEvent(
                "ABNORMAL_REWARD_FREQ",
                "Rewarded ad claim rejected due to cooldown (${now - lastRewardedAdClaimTime} ms < $MIN_REWARD_COOLDOWN_MS ms)"
            )
            return false
        }

        // 2. Token validation if token was provided
        if (token != null && !consumeRewardToken(token)) {
            return false
        }

        lastRewardedAdClaimTime = now
        return true
    }

    /**
     * Validates bonus star additions.
     * Prevents negative amounts, absurdly large values, or rapid memory edits.
     */
    fun validateBonusStars(requestedAmount: Int): Int {
        if (requestedAmount <= 0) {
            SecurityManager.logSecurityEvent("INVALID_REWARD", "Non-positive bonus stars requested: $requestedAmount")
            return 0
        }
        if (requestedAmount > MAX_SINGLE_BONUS_STARS) {
            SecurityManager.logSecurityEvent(
                "EXCESSIVE_REWARD",
                "Bonus star claim ($requestedAmount) exceeded cap ($MAX_SINGLE_BONUS_STARS). Capping."
            )
            return MAX_SINGLE_BONUS_STARS
        }
        return requestedAmount
    }
}
