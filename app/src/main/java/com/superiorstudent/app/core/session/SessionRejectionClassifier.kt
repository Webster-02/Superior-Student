package com.superiorstudent.app.core.session

import com.superiorstudent.app.core.config.ErpConfig

/**
 * Distinguishes explicit ERP authentication rejection from transport/server failures.
 * A missing response or generic failure must never be treated as proof that a
 * student's saved session is invalid.
 */
object SessionRejectionClassifier {
    enum class Decision { REJECTED, TRANSIENT, UNKNOWN }

    fun classify(
        statusCode: Int? = null,
        finalUrl: String = "",
        responseBody: String = ""
    ): Decision {
        if (statusCode == 401 || statusCode == 403) return Decision.REJECTED
        if (statusCode == 408 || statusCode == 429 || (statusCode != null && statusCode in 500..599)) {
            return Decision.TRANSIENT
        }
        if (ErpConfig.isLoginUrl(finalUrl)) return Decision.REJECTED
        if (Regex(
                """invalid session|session expired|session is expired|not logged in|please log in again""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(responseBody)
        ) return Decision.REJECTED
        return Decision.UNKNOWN
    }
}
