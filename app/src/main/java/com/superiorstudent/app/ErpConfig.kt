package com.superiorstudent.app

/** Centralised ERP endpoints and session rules (single source of truth). */
object ErpConfig {
    const val BASE_URL = "https://erp.superior.edu.pk/"
    const val LOGIN_URL = BASE_URL + "web/login"
    const val DASHBOARD_URL = BASE_URL + "student/dashboard"

    fun moduleUrl(path: String): String = BASE_URL + path

    /** Session-invalidation detection is restricted to our own host. */
    fun belongsToErp(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return false
            host.equals("erp.superior.edu.pk", ignoreCase = true) ||
                host.endsWith(".erp.superior.edu.pk", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun isLoginUrl(url: String): Boolean =
        belongsToErp(url) && url.contains("/web/login", ignoreCase = true)

    fun isAuthenticatedUrl(url: String): Boolean =
        belongsToErp(url) &&
            url.contains("/student/", ignoreCase = true) &&
            !isLoginUrl(url)
}
