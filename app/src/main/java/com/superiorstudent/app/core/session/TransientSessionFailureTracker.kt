package com.superiorstudent.app.core.session

import com.superiorstudent.app.core.config.ErpConfig
import java.util.Locale

/**
 * One-shot guard for WebView's follow-up page-finished callback after a transient
 * main-frame failure. A server error can leave a login-looking final URL; this
 * brief marker prevents that follow-up from being mistaken for session rejection.
 */
class TransientSessionFailureTracker(
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS
) {
    private var failedAtElapsedMs: Long? = null

    @Synchronized
    fun record(failedUrl: String, nowElapsedMs: Long) {
        failedAtElapsedMs = if (ErpConfig.belongsToErp(failedUrl)) nowElapsedMs else null
    }

    @Synchronized
    fun shouldDeferLoginRedirect(finalUrl: String, nowElapsedMs: Long): Boolean {
        if (!ErpConfig.isLoginUrl(finalUrl)) return false
        val failedAt = failedAtElapsedMs ?: return false
        val age = nowElapsedMs - failedAt
        failedAtElapsedMs = null
        return age in 0..maxAgeMs
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 15_000L
    }
}
