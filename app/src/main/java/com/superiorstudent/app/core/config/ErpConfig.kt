package com.superiorstudent.app.core.config

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

    /**
     * The university login can be served by either the standard Odoo route or
     * the legacy TSG route. Use the parsed path so query strings are harmless.
     */
    fun isLoginUrl(url: String): Boolean {
        if (!belongsToErp(url)) return false
        val path = try {
            java.net.URI(url).path.orEmpty().lowercase(java.util.Locale.US)
        } catch (_: Exception) {
            return false
        }
        return path == "/web/login" || path.startsWith("/web/login/") ||
            path == "/tsg/login" || path.startsWith("/tsg/login/") ||
            path == "/student/login" || path.startsWith("/student/login/")
    }

    fun isAuthenticatedUrl(url: String): Boolean =
        belongsToErp(url) &&
            url.contains("/student/", ignoreCase = true) &&
            !isLoginUrl(url)
}
