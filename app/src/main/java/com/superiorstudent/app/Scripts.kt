package com.superiorstudent.app

import android.content.Context
import android.util.Base64

/**
 * Loads the ERP page-interaction JavaScript from assets (js/*.js) instead of
 * embedding huge string literals in Kotlin. Keeping the scripts as plain JS
 * files makes them editable, syntax-checkable and diffable independently.
 *
 * Dynamic values (username, password, semester label) are injected through
 * base64-encoded JSON placeholders so arbitrary user input can never break
 * out of the script or be executed as code.
 */
object Scripts {
    var sessionHeartbeat: String = ""
        private set
    var loginErrorCheck: String = ""
        private set
    var moduleData: String = ""
        private set
    var studentProfileText: String = ""
        private set

    private var loginTemplate: String = ""
    private var semesterSelectTemplate: String = ""

    fun loadAll(context: Context) {
        val assets = context.applicationContext.assets
        sessionHeartbeat = assets.read("js/session_heartbeat.js")
        loginErrorCheck = assets.read("js/login_error_check.js")
        moduleData = assets.read("js/module_data.js")
        studentProfileText = assets.read("js/student_profile_text.js")
        loginTemplate = assets.read("js/login.js")
        semesterSelectTemplate = assets.read("js/semester_select.js")
    }

    fun loginScript(username: String, password: String): String =
        loginTemplate
            .replace("%USERNAME_B64%", b64JsonString(username))
            .replace("%PASSWORD_B64%", b64JsonString(password))

    fun semesterSelectScript(label: String): String =
        semesterSelectTemplate.replace("%SEMESTER_B64%", b64JsonString(label))

    /** Encodes [value] as base64 of its JSON string literal (e.g. "a\"b" -> safe inside '...'). */
    private fun b64JsonString(value: String): String {
        val quoted = StringBuilder(""")
        for (ch in value) {
            when (ch) {
                '"' -> quoted.append("\\"")
                '\\' -> quoted.append("\\\\")
                '\n' -> quoted.append("\\n")
                '\r' -> quoted.append("\\r")
                '\t' -> quoted.append("\\t")
                else -> if (ch.code < 0x20) quoted.append(String.format("\\u%04x", ch.code)) else quoted.append(ch)
            }
        }
        quoted.append('"')
        return Base64.encodeToString(quoted.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun android.content.res.AssetManager.read(path: String): String =
        open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
