package com.superiorstudent.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.superiorstudent.app.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }

    private var loginInProgress = false
    private var loginSubmitted = false
    private var restoringSession = false
    private var loggedIn = false
    private var profileFetchInProgress = false
    private var profileReadAttempts = 0
    private var activeModule: Module? = null
    private var username = ""
    private var password = ""
    private var loginAttempt = 0
    private var preloadingModule: Module? = null
    private val preloadQueue = mutableListOf<Module>()
    private val cachedModuleData = mutableMapOf<Module, String>()
    private var moduleFetchInProgress = false
    private var moduleReadRequestId = 0
    private var lastPausedAt = 0L
    private val sessionResumeThresholdMs = 5 * 60 * 1000L

    private enum class Module(val path: String) {
        ATTENDANCE("student/attendance"),
        TIMETABLE("student/class/schedule"),
        FEE("student/invoices"),
        PROFILE("student/profile"),
        RESULTS("student/results")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val webView = binding.webView
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.setSupportZoom(false)
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && activeModule != null) {
                    binding.moduleProgress.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Unable to load your information. Check internet and try Refresh.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                if (activeModule != null && binding.moduleScreen.visibility == View.VISIBLE) {
                    binding.moduleProgress.visibility = View.GONE
                }

                if (!loginInProgress && !profileFetchInProgress && url.contains("/web/login", true)) {
                    restoringSession = false
                    loggedIn = false
                    preferences.edit().putBoolean(KEY_SESSION_ACTIVE, false).apply()
                    showLogin()
                    Toast.makeText(this@MainActivity, "Your university session expired. Please sign in again.", Toast.LENGTH_LONG).show()
                    return
                }

                if (restoringSession) {
                    if (isAuthenticatedUrl(url)) {
                        restoringSession = false
                        completeLogin()
                    } else if (url.contains("/web/login", true)) {
                        restoringSession = false
                        preferences.edit().putBoolean(KEY_SESSION_ACTIVE, false).apply()
                        showLogin()
                    }
                    return
                }

                if (loginInProgress) {
                    if (isAuthenticatedUrl(url)) {
                        completeLogin()
                    } else if (url.contains("/web/login", true)) {
                        if (!loginSubmitted) {
                            loginAttempt = 0
                            scheduleLoginInjection(view)
                        } else {
                            view.evaluateJavascript(LOGIN_ERROR_CHECK_SCRIPT) { result ->
                                if (result == "true") failLogin("Login failed. Check your ERP username or password.")
                            }
                        }
                    }
                    return
                }

                if (profileFetchInProgress && isAuthenticatedUrl(url)) {
                    waitForProfileDom(view)
                    return
                }

                val preload = preloadingModule
                if (preload != null && url.contains(preload.path, true)) {
                    readModuleData(view, preload, false)
                    return
                }

                val active = activeModule
                if (moduleFetchInProgress && active != null && url.contains(active.path, true)) {
                    moduleFetchInProgress = false
                    readModuleData(view, active, true)
                }
            }
        }

        binding.loginButton.setOnClickListener {
            username = binding.usernameInput.text.toString().trim()
            password = binding.passwordInput.text.toString()
            if (username.isBlank() || password.isBlank()) {
                binding.loginStatus.setTextColor(Color.rgb(198, 40, 40))
                binding.loginStatus.text = "Please enter your university username and password."
                return@setOnClickListener
            }
            loginInProgress = true
            loginSubmitted = false
            restoringSession = false
            loggedIn = false
            loginAttempt = 0
            profileReadAttempts = 0
            cachedModuleData.clear()
            binding.loginButton.isEnabled = false
            binding.loginStatus.setTextColor(Color.rgb(102, 112, 133))
            binding.loginStatus.text = "Signing in securely…"
            webView.visibility = View.GONE
            webView.loadUrl(ERP_LOGIN_URL)
        }

        binding.attendanceButton.setOnClickListener { loadModule(Module.ATTENDANCE) }
        binding.timetableButton.setOnClickListener { loadModule(Module.TIMETABLE) }
        binding.feeButton.setOnClickListener { loadModule(Module.FEE) }
        binding.profileButton.setOnClickListener { loadModule(Module.PROFILE) }
        binding.resultsButton.setOnClickListener { loadModule(Module.RESULTS) }
        binding.refreshButton.setOnClickListener { refreshActiveModule() }
        binding.homeButton.setOnClickListener { showDashboard() }
        binding.logoutButton.setOnClickListener { logout() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else if (preferences.getBoolean(KEY_SESSION_ACTIVE, false)) {
            username = preferences.getString(KEY_USERNAME, "") ?: ""
            restoringSession = true
            binding.loginScroll.visibility = View.GONE
            binding.dashboardScroll.visibility = View.VISIBLE
            webView.visibility = View.GONE
            showDashboard()
            binding.webView.loadUrl(ERP_DASHBOARD_URL)
        }
    }

    override fun onPause() {
        lastPausedAt = System.currentTimeMillis()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val pausedFor = if (lastPausedAt > 0L) System.currentTimeMillis() - lastPausedAt else 0L
        lastPausedAt = 0L
        if (pausedFor >= sessionResumeThresholdMs && loggedIn) {
            recoverSessionAfterBackground()
        }
    }

    private fun recoverSessionAfterBackground() {
        handler.removeCallbacksAndMessages(null)
        binding.webView.stopLoading()
        binding.webView.visibility = View.INVISIBLE
        val module = activeModule
        if (module != null && binding.moduleScreen.visibility == View.VISIBLE) {
            cachedModuleData.remove(module)
            moduleFetchInProgress = true
            moduleReadRequestId++
            binding.moduleProgress.visibility = View.VISIBLE
            binding.moduleContent.removeAllViews()
            binding.moduleInfo.text = "Reconnecting to your student account…"
            binding.webView.loadUrl(ERP_BASE_URL + module.path)
        } else {
            restoringSession = true
            binding.webView.loadUrl(ERP_DASHBOARD_URL)
        }
    }

    private fun scheduleLoginInjection(view: WebView) {
        if (!loginInProgress || loginSubmitted) return
        if (loginAttempt >= MAX_LOGIN_INJECTION_ATTEMPTS) {
            failLogin("Unable to connect to your university account. Please try again.")
            return
        }
        handler.postDelayed({ injectLogin(view) }, LOGIN_INJECTION_DELAY_MS)
    }

    private fun injectLogin(view: WebView) {
        if (!loginInProgress || loginSubmitted) return
        val script = LOGIN_SCRIPT
            .replace("%USERNAME%", JSONObject.quote(username))
            .replace("%PASSWORD%", JSONObject.quote(password))
        loginAttempt++
        view.evaluateJavascript(script) { result ->
            if (!loginInProgress || loginSubmitted) return@evaluateJavascript
            if (result == "\"SUBMITTED\"") {
                loginSubmitted = true
                binding.loginStatus.text = "Verifying your account…"
            } else {
                scheduleLoginInjection(view)
            }
        }
    }

    private fun completeLogin() {
        loginInProgress = false
        loginSubmitted = false
        restoringSession = false
        loggedIn = true
        profileReadAttempts = 0
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putString(KEY_USERNAME, username)
            .apply()
        binding.loginStatus.text = ""
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        showDashboard()
        fetchStudentProfile()
    }

    private fun fetchStudentProfile() {
        if (!loggedIn) return
        profileFetchInProgress = true
        profileReadAttempts = 0
        // Keep the WebView attached while waiting for the visual DOM state.
        // This makes postVisualStateCallback reliable even though the ERP page is not shown to the user.
        binding.webView.visibility = View.INVISIBLE
        binding.webView.loadUrl(ERP_DASHBOARD_URL)
    }

    private fun waitForProfileDom(view: WebView) {
        if (!loggedIn || !profileFetchInProgress) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            view.postVisualStateCallback(profileReadAttempts.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    handler.postDelayed({ extractAndApplyProfile(view) }, PROFILE_VISUAL_DELAY_MS)
                }
            })
        } else {
            handler.postDelayed({ extractAndApplyProfile(view) }, PROFILE_READ_DELAY_MS)
        }
    }

    private fun extractAndApplyProfile(view: WebView) {
        if (!loggedIn || !profileFetchInProgress) return

        profileReadAttempts++
        view.evaluateJavascript(STUDENT_PROFILE_TEXT_SCRIPT) { result ->
            val payload = decodeJavascriptString(result)
            try {
                val json = JSONObject(payload)
                val name = json.optString("name", "").trim()
                val bodyText = json.optString("body", "")
                val cards = json.optJSONArray("cards")

                if (isValidStudentName(name)) {
                    preferences.edit().putString(KEY_STUDENT_NAME, name).apply()
                    binding.studentName.text = name
                }

                val cgpa = findGpaInText(cards, bodyText, "CGPA")
                val sgpa = findGpaInText(cards, bodyText, "SGPA")

                if (cgpa.isNotBlank() && sgpa.isNotBlank()) {
                    val display = "CGPA: $cgpa  |  SGPA: $sgpa"
                    preferences.edit()
                        .putString(KEY_CGPA, cgpa)
                        .putString(KEY_SGPA, sgpa)
                        .putString(KEY_GPA, display)
                        .apply()
                    binding.studentCgpa.text = cgpa
                    binding.studentSgpa.text = sgpa
                    profileFetchInProgress = false
                    preloadAllModules()
                    return@evaluateJavascript
                }
            } catch (_: Exception) {
                // Retry when the WebView has not exposed the final ERP text yet.
            }

            if (profileReadAttempts < MAX_PROFILE_READ_ATTEMPTS) {
                handler.postDelayed({ extractAndApplyProfile(view) }, PROFILE_RETRY_DELAY_MS)
            } else {
                profileFetchInProgress = false
                preloadAllModules()
            }
        }
    }

    private fun findGpaInText(cards: org.json.JSONArray?, bodyText: String, label: String): String {
        if (cards != null) {
            for (index in 0 until cards.length()) {
                val cardText = cards.optString(index, "")
                val value = findGpaValue(cardText, label)
                if (value.isNotBlank()) return value
            }
        }
        return findGpaValue(bodyText, label)
    }

    private fun findGpaValue(text: String, label: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return ""

        val escapedLabel = Regex.escape(label)
        val after = Regex(
            """\b$escapedLabel\b\s*[:\-]?\s*([0-4](?:\.\d{1,2})?)\b""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (after != null) return validGpa(after.groupValues[1])

        val before = Regex(
            """([0-4](?:\.\d{1,2})?)\s*\b$escapedLabel\b""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (before != null) return validGpa(before.groupValues[1])

        return ""
    }

    private fun validGpa(value: String): String {
        val number = value.trim().toDoubleOrNull() ?: return ""
        return if (number in 0.0..4.0) String.format(java.util.Locale.US, "%.2f", number).trimEnd('0').trimEnd('.') else ""
    }


    private fun preloadAllModules() {
        if (!loggedIn || profileFetchInProgress || preloadingModule != null) return
        preloadQueue.clear()
        preloadQueue.addAll(Module.values())
        preloadNextModule()
    }

    private fun preloadNextModule() {
        if (!loggedIn) return
        if (preloadQueue.isEmpty()) {
            preloadingModule = null
            binding.webView.visibility = View.GONE
            updateNextClassPreview()
            return
        }
        preloadingModule = preloadQueue.removeAt(0)
        binding.webView.visibility = View.INVISIBLE
        binding.webView.loadUrl(ERP_BASE_URL + preloadingModule!!.path)
    }

    private fun loadModule(module: Module) {
        if (!loggedIn) return

        preloadingModule = null
        preloadQueue.clear()
        moduleFetchInProgress = false
        moduleReadRequestId++

        activeModule = module
        binding.loginScroll.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.VISIBLE
        binding.webView.visibility = View.INVISIBLE
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleContent.removeAllViews()
        binding.moduleInfo.text = "Syncing your latest information…"

        when (module) {
            Module.ATTENDANCE -> {
                binding.moduleTitle.text = "Attendance"
                binding.moduleSubtitle.text = "Your attendance"
            }
            Module.TIMETABLE -> {
                binding.moduleTitle.text = "Timetable"
                binding.moduleSubtitle.text = "Your class schedule"
            }
            Module.FEE -> {
                binding.moduleTitle.text = "Fee Details"
                binding.moduleSubtitle.text = "Your fee information"
            }
            Module.PROFILE -> {
                binding.moduleTitle.text = "My Profile"
                binding.moduleSubtitle.text = "Your student information"
            }
            Module.RESULTS -> {
                binding.moduleTitle.text = "Results"
                binding.moduleSubtitle.text = "Your academic results"
            }
        }

        val forceLive = module == Module.TIMETABLE || module == Module.PROFILE || module == Module.RESULTS
        val payload = if (forceLive) null else cachedModuleData[module]
        val data = payload?.let { parseModuleData(it) }
        if (data != null) {
            renderModuleData(module, data)
            return
        }

        moduleFetchInProgress = true
        binding.webView.loadUrl(ERP_BASE_URL + module.path)
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        cachedModuleData.remove(module)
        moduleFetchInProgress = false
        moduleReadRequestId++
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleContent.removeAllViews()
        binding.moduleInfo.text = "Syncing the latest information…"
        binding.webView.visibility = View.INVISIBLE
        binding.webView.loadUrl(ERP_BASE_URL + module.path)
        moduleFetchInProgress = true
    }


    private data class TableData(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>
    )

    private data class ModuleRecord(val headers: List<String>, val values: List<String>)

    private data class ModuleData(
        val cards: List<Pair<String, String>>,
        val tables: List<TableData>,
        val lines: List<String>,
        val records: List<ModuleRecord>,
        val overallAttendance: Double?
    )


    private fun renderModuleData(module: Module, data: ModuleData) {
        binding.moduleContent.removeAllViews()
        addModuleIntro(module)
        when (module) {
            Module.ATTENDANCE -> renderAttendance(data)
            Module.TIMETABLE -> renderTimetable(data)
            Module.FEE -> renderFee(data)
            Module.PROFILE -> renderProfile(data)
            Module.RESULTS -> renderResults(data)
        }
        binding.moduleProgress.visibility = View.GONE
        binding.moduleInfo.text = "Updated from your student account"
    }

    private data class AttendanceRow(
        val course: String,
        val code: String,
        val percent: Double?,
        val present: String,
        val total: String
    )

    private fun renderAttendance(data: ModuleData) {
        val rows = data.records.mapNotNull(::toAttendanceRow)
            .distinctBy { it.course + "|" + it.code }
            .take(20)

        if (rows.isNotEmpty()) {
            val percentages = rows.mapNotNull { it.percent }
            binding.moduleContent.addView(
                createAttendanceSummary(data.overallAttendance, percentages, rows.size)
            )
            rows.forEachIndexed { index, row ->
                binding.moduleContent.addView(createAttendanceCard(row, index + 1))
            }
        } else {
            data.tables.forEachIndexed { index, table ->
                binding.moduleContent.addView(
                    createTableSection(
                        table.title.ifBlank { if (index == 0) "Attendance records" else "Attendance details" },
                        table
                    )
                )
            }
            if (data.tables.isEmpty()) binding.moduleContent.addView(
                createEmptyState("Attendance data is unavailable", "Refresh to sync your latest subject attendance.")
            )
        }
    }

    private fun toAttendanceRow(record: ModuleRecord): AttendanceRow? {
        val values = record.values.map(::cleanDisplayText).filter(String::isNotBlank)
        if (values.isEmpty()) return null
        val headers = record.headers.map(::cleanDisplayText)
        val joined = values.joinToString(" ")
        if (joined.contains("session", true) || joined.contains("inactive", true) || joined.contains("stay online", true)) return null

        fun valueFor(vararg keys: String): String {
            val index = headers.indexOfFirst { header -> keys.any { key -> header.contains(key, true) } }
            return if (index >= 0 && index < values.size) values[index] else ""
        }

        val courseFromHeader = valueFor("subject", "course name", "course", "class name")
        val codeFromHeader = valueFor("code", "course code", "subject code")
        val percentFromHeader = valueFor("percentage", "attendance %", "attendance percentage", "percent")
        val presentFromHeader = valueFor("present", "attended", "classes attended")
        val totalFromHeader = valueFor("total", "total classes", "classes held")

        val percent = parsePercent(percentFromHeader)
            ?: Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*%""").find(joined)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)

        val code = codeFromHeader.ifBlank {
            Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE).find(joined)?.value.orEmpty()
        }

        val course = courseFromHeader.ifBlank {
            val withoutPercent = joined.replace(Regex("""\d{1,3}(?:\.\d{1,2})?\s*%"""), "")
            withoutPercent.substringBefore(code).trim().ifBlank { withoutPercent.trim() }
        }

        val present = presentFromHeader.ifBlank { extractCountNearLabel(joined, "present", "attended", "attendance") }
        val total = totalFromHeader.ifBlank { extractCountNearLabel(joined, "total", "classes") }

        if (course.length < 3 || (percent == null && code.isBlank())) return null
        return AttendanceRow(course, code, percent, present, total)
    }

    private fun parsePercent(value: String): Double? {
        val withSymbol = Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*%""")
            .find(value)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (withSymbol != null) return withSymbol.coerceIn(0.0, 100.0)
        return value.trim().toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    }

    private fun extractCountNearLabel(text: String, vararg labels: String): String {
        for (label in labels) {
            val pattern = "(?i)\\\\b" + Regex.escape(label) + "\\\\b\\\\s*[:\\\\-]?\\\\s*(\\\\d+(?:\\\\.\\\\d+)?)"
            val match = Regex(pattern).find(text)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }


    private fun attendanceStatus(percent: Double): Pair<Int, String> {
        return when {
            percent >= 85.0 -> Color.rgb(25, 135, 84) to "Good standing"
            percent >= 75.0 -> Color.rgb(205, 132, 24) to "Needs attention"
            else -> Color.rgb(205, 67, 67) to "Low attendance"
        }
    }

    private fun createSectionHeading(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.rgb(23, 42, 70))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(Color.rgb(126, 139, 158))
                textSize = 11f
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun createAttendanceSummary(overall: Double?, percentages: List<Double>, count: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.rgb(18, 58, 112), 20f)
            elevation = dp(3).toFloat()
            setPadding(dp(18), dp(17), dp(18), dp(17))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(6), dp(12), dp(10)) }

            val display = overall
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(this@MainActivity).apply {
                    text = if (overall != null) "OVERALL ATTENDANCE" else "ATTENDANCE SUMMARY"
                    setTextColor(Color.rgb(190, 214, 244))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.08f
                })
                addView(TextView(this@MainActivity).apply {
                    text = display?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "—"
                    setTextColor(Color.WHITE)
                    textSize = 32f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(3), 0, 0)
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (overall != null) {
                        "$count subjects • official overall percentage"
                    } else {
                        "$count subjects • percentage shown per subject"
                    }
                    setTextColor(Color.rgb(221, 232, 247))
                    textSize = 11f
                })
            })

            if (display != null) {
                addView(TextView(this@MainActivity).apply {
                    text = attendanceStatus(display).second
                    gravity = Gravity.CENTER
                    setTextColor(attendanceStatus(display).first)
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Color.WHITE, 12f)
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                })
            }
        }
    }

    private fun createAttendanceCard(row: AttendanceRow, position: Int): View {
        val percent = row.percent
        val statusColor = when {
            percent == null -> Color.rgb(102, 112, 133)
            percent >= 85.0 -> Color.rgb(25, 135, 84)
            percent >= 75.0 -> Color.rgb(205, 132, 24)
            else -> Color.rgb(205, 67, 67)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(this@MainActivity).apply {
                text = position.toString().padStart(2, '0')
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(30, 91, 155))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.rgb(239, 245, 255), 11f)
                minWidth = dp(38)
                minHeight = dp(34)
            })
            header.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(12), 0, dp(8), 0) }
                addView(TextView(this@MainActivity).apply {
                    text = row.course
                    setTextColor(Color.rgb(23, 42, 70))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (row.code.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = row.code
                    setTextColor(Color.rgb(126, 139, 158))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                })
            })
            header.addView(TextView(this@MainActivity).apply {
                text = percent?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "N/A"
                setTextColor(statusColor)
                textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = dp(64)
            })
            addView(header)

            if (percent != null) addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent.roundToInt()
                progressTintList = ColorStateList.valueOf(statusColor)
                progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(231, 236, 244))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply { topMargin = dp(12) }
            })

            val stats = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            if (row.present.isNotBlank()) addStat(stats, "Present", row.present)
            if (row.total.isNotBlank()) addStat(stats, "Total", row.total)
            if (row.present.isNotBlank() && row.total.isNotBlank()) {
                val totalNumber = row.total.toDoubleOrNull()
                val presentNumber = row.present.toDoubleOrNull()
                if (totalNumber != null && presentNumber != null && totalNumber >= presentNumber) {
                    addStat(stats, "Absent", (totalNumber - presentNumber).toString().removeSuffix(".0"))
                }
            }
            if (stats.childCount > 0) addView(stats)
        }
    }

    private fun addStat(container: LinearLayout, label: String, value: String) {
        container.addView(TextView(this).apply {
            text = label + "  " + value
            setTextColor(Color.rgb(82, 93, 112))
            textSize = 11f
            setPadding(0, 0, dp(18), 0)
        })
    }

    private data class ScheduleRow(
        val day: String,
        val time: String,
        val course: String,
        val room: String
    )

    private fun renderTimetable(data: ModuleData) {
        val rows = data.records.mapNotNull(::toScheduleRow)
            .distinctBy { it.day + "|" + it.time + "|" + it.course + "|" + it.room }
            .take(50)

        if (rows.isNotEmpty()) {
            val dayOrder = mapOf(
                "Monday" to 1, "Tuesday" to 2, "Wednesday" to 3,
                "Thursday" to 4, "Friday" to 5, "Saturday" to 6, "Sunday" to 7
            )
            val sortedRows = rows.sortedWith(
                compareBy<ScheduleRow> { row ->
                    dayOrder.entries.firstOrNull { entry -> row.day.equals(entry.key, true) }?.value ?: 99
                }.thenBy { row ->
                    Regex("""([01]?\d|2[0-3]):([0-5]\d)""").find(row.time)?.value ?: "99:99"
                }
            )
            sortedRows.groupBy { it.day.ifBlank { "Scheduled classes" } }.forEach { (day, items) ->
                binding.moduleContent.addView(createDayHeader(day, items.size))
                items.forEach { binding.moduleContent.addView(createScheduleCard(it)) }
            }
        } else {
            data.tables.forEachIndexed { index, table ->
                binding.moduleContent.addView(createTableSection(
                    table.title.ifBlank { if (index == 0) "Class schedule" else "Schedule details" }, table
                ))
            }
            if (data.tables.isEmpty()) binding.moduleContent.addView(
                createEmptyState("Timetable data is unavailable", "Refresh to sync your latest class schedule.")
            )
        }
    }

    private fun toScheduleRow(record: ModuleRecord): ScheduleRow? {
        val values = record.values.map(::cleanDisplayText).filter(String::isNotBlank)
        if (values.isEmpty()) return null
        val joined = values.joinToString(" ")
        if (joined.contains("session", true) || joined.contains("inactive", true) || joined.contains("stay online", true)) return null

        fun headerValue(vararg keys: String): String {
            val index = record.headers.indexOfFirst { h -> keys.any { key -> h.contains(key, true) } }
            return if (index >= 0 && index < values.size) values[index] else ""
        }

        val time = headerValue("time", "timing").ifBlank {
            Regex("""(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[-–]\s*(?:[01]?\d|2[0-3]):[0-5]\d)?""")
                .find(joined)?.value.orEmpty()
        }
        val day = headerValue("day", "date").ifBlank {
            Regex("""(?i)\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""")
                .find(joined)?.value.orEmpty()
        }
        val code = Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE).find(joined)?.value.orEmpty()
        val course = headerValue("subject", "course", "class", "name").ifBlank {
            joined.replace(time, "").replace(day, "").trim(' ', '-', '–', '|').substringBefore(code).trim().ifBlank { code }
        }
        val room = headerValue("room", "venue", "location").ifBlank {
            Regex("""(?:^|[| ])(?:Room\s*)?([A-Z]{1,3}-\d{1,3}|[A-Z]{1,3}\d{1,3})$""", RegexOption.IGNORE_CASE)
                .find(joined)?.groupValues?.getOrNull(1).orEmpty()
                .ifBlank {
                    values.lastOrNull().orEmpty()
                        .takeIf { it != course && it != time && it != day && it != code }
                        ?.replace(Regex("""\(Lecture\)""", RegexOption.IGNORE_CASE), "")
                        ?.trim(' ', '-', '|')
                        .orEmpty()
                }
        }
        if (course.length < 3 || (time.isBlank() && code.isBlank())) return null
        return ScheduleRow(day, time, course, room)
    }

    private fun createDayHeader(day: String, count: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(14), dp(12), dp(14), dp(3)) }
            addView(TextView(this@MainActivity).apply {
                text = day
                setTextColor(Color.rgb(18, 58, 112))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = count.toString() + " classes"
                setTextColor(Color.rgb(126, 139, 158))
                textSize = 10f
            })
        }

    private fun createScheduleCard(row: ScheduleRow): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.WHITE, 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedBackground(Color.rgb(239, 245, 255), 14f)
                layoutParams = LinearLayout.LayoutParams(dp(82), dp(58))
                setPadding(dp(5), dp(5), dp(5), dp(5))

                addView(TextView(this@MainActivity).apply {
                    text = row.time.ifBlank { "Time unavailable" }
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(30, 91, 155))
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (row.day.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = row.day.take(3).uppercase(java.util.Locale.US)
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(93, 112, 140))
                    textSize = 9f
                    setPadding(0, dp(2), 0, 0)
                })
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(13), 0, 0, 0) }

                addView(TextView(this@MainActivity).apply {
                    text = row.course
                    setTextColor(Color.rgb(23, 42, 70))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                if (row.room.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = "Room • " + row.room
                    setTextColor(Color.rgb(105, 119, 141))
                    textSize = 10f
                    setPadding(0, dp(5), 0, 0)
                }) else addView(TextView(this@MainActivity).apply {
                    text = "Time not provided by ERP"
                    setTextColor(Color.rgb(151, 160, 175))
                    textSize = 10f
                    setPadding(0, dp(5), 0, 0)
                })
            })
        }


    private fun renderProfile(data: ModuleData) {
        val fieldRecords = data.records.mapNotNull { record ->
            val values = record.values.map(::cleanDisplayText).filter(String::isNotBlank)
            if (values.size < 2) return@mapNotNull null
            val field = cleanDisplayText(values[0])
            val value = cleanDisplayText(values.drop(1).joinToString(" • "))
            if (field.length < 2 || value.isBlank()) null else field to value
        }.distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }

        if (fieldRecords.isNotEmpty()) {
            binding.moduleContent.addView(createSectionHeading("Personal information", "Details fetched from your Superior ERP profile"))
            fieldRecords.take(40).forEach { (label, value) ->
                binding.moduleContent.addView(createProfileFieldCard(label, value))
            }
        }

        data.cards.take(12).forEach { (label, value) ->
            binding.moduleContent.addView(createProfileFieldCard(label, value))
        }

        data.tables.take(10).forEachIndexed { index, table ->
            binding.moduleContent.addView(
                createTableSection(
                    table.title.ifBlank { if (index == 0) "Profile details" else "Additional profile information" },
                    table
                )
            )
        }

        if (fieldRecords.isEmpty() && data.cards.isEmpty() && data.tables.isEmpty()) {
            data.lines.distinct().take(40).forEach { line ->
                binding.moduleContent.addView(createProfileFieldCard("Student information", line))
            }
        }

        if (fieldRecords.isEmpty() && data.cards.isEmpty() && data.tables.isEmpty() && data.lines.isEmpty()) {
            binding.moduleContent.addView(
                createEmptyState("Profile information is unavailable", "Refresh to sync your latest ERP profile.")
            )
        }
    }

    private fun createProfileFieldCard(label: String, value: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            addView(TextView(this@MainActivity).apply {
                text = cleanDisplayText(label).uppercase(java.util.Locale.US)
                setTextColor(Color.rgb(102, 112, 133))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.04f
            })
            addView(TextView(this@MainActivity).apply {
                text = cleanDisplayText(value)
                setTextColor(Color.rgb(23, 42, 70))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            })
        }

    private fun renderResults(data: ModuleData) {
        data.cards.take(8).forEach { (label, value) ->
            binding.moduleContent.addView(createMetricCard(label, value))
        }

        if (data.tables.isNotEmpty()) {
            binding.moduleContent.addView(
                createSectionHeading("Result history", "Courses, grades, credits and other academic records from ERP")
            )
            data.tables.take(12).forEachIndexed { index, table ->
                binding.moduleContent.addView(
                    createTableSection(
                        table.title.ifBlank { if (index == 0) "Academic results" else "Result details" },
                        table
                    )
                )
            }
        }

        val resultRecords = data.records.mapNotNull { record ->
            val values = record.values.map(::cleanDisplayText).filter(String::isNotBlank)
            if (values.size < 2) return@mapNotNull null
            val joined = values.joinToString(" • ")
            if (joined.length < 3) null else values
        }.distinctBy { it.joinToString("|").lowercase() }

        resultRecords.take(60).forEach { values ->
            val title = values.first()
            val details = values.drop(1).joinToString(" • ")
            if (details.isNotBlank() && !data.tables.any { table -> table.rows.any { it == values } }) {
                binding.moduleContent.addView(createResultCard(title, details))
            }
        }

        if (data.cards.isEmpty() && data.tables.isEmpty() && resultRecords.isEmpty()) {
            binding.moduleContent.addView(
                createEmptyState("Results are unavailable", "Refresh to sync your latest academic results from ERP.")
            )
        }
    }

    private fun createResultCard(title: String, details: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            addView(TextView(this@MainActivity).apply {
                text = cleanDisplayText(title)
                setTextColor(Color.rgb(18, 58, 112))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = cleanDisplayText(details)
                setTextColor(Color.rgb(82, 93, 112))
                textSize = 11f
                setPadding(0, dp(6), 0, 0)
            })
        }

    private fun renderFee(data: ModuleData) {
        val invoices = data.tables.flatMap { table ->
            table.rows.map { row -> table.headers.zip(row).toMap() }
        }.filter { it.isNotEmpty() }

        if (invoices.isNotEmpty()) {
            binding.moduleContent.addView(createFeeSummary(invoices.size))
            binding.moduleContent.addView(createSectionHeading("Invoice history", "Recent fee records from your student account"))
            invoices.take(30).forEach { invoice -> binding.moduleContent.addView(createInvoiceCard(invoice)) }
        } else {
            data.cards.take(4).forEach { (label, value) -> binding.moduleContent.addView(createMetricCard(label, value)) }
            if (data.cards.isEmpty()) binding.moduleContent.addView(
                createEmptyState("Fee information is unavailable", "Refresh to sync your latest fee records.")
            )
        }
    }

    private fun createFeeSummary(count: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.WHITE, 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(6), dp(12), dp(7)) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = "Fee records"
                    setTextColor(Color.rgb(23, 42, 70))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Your latest invoices"
                    setTextColor(Color.rgb(126, 139, 158))
                    textSize = 11f
                    setPadding(0, dp(3), 0, 0)
                })
            })
            addView(TextView(this@MainActivity).apply {
                text = count.toString()
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(30, 91, 155))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.rgb(239, 245, 255), 12f)
                minWidth = dp(44)
                minHeight = dp(38)
            })
        }

    private fun findMapValue(map: Map<String, String>, vararg keys: String): String {
        val normalized = map.mapKeys { it.key.trim().lowercase() }
        for (key in keys) {
            val exact = normalized.entries.firstOrNull { it.key == key.lowercase() }
            if (exact != null) return exact.value
        }
        for (key in keys) {
            val partial = normalized.entries.firstOrNull { it.key.contains(key.lowercase()) }
            if (partial != null) return partial.value
        }
        return ""
    }

    private fun createInvoiceCard(invoice: Map<String, String>): View {
        val no = findMapValue(invoice, "invoice no", "invoice number", "number", "no").ifBlank { "Invoice" }
        val date = findMapValue(invoice, "invoice date", "date")
        val due = findMapValue(invoice, "due date", "due")
        val amount = findMapValue(invoice, "amount", "total", "balance")
        val status = findMapValue(invoice, "status", "state")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = no
                    setTextColor(Color.rgb(23, 42, 70))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (status.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = status
                    setTextColor(Color.rgb(25, 135, 84))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Color.rgb(233, 248, 241), 10f)
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                })
            })
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            addInvoiceDetail(details, "Invoice date", date)
            addInvoiceDetail(details, "Due date", due)
            if (amount.isNotBlank()) addInvoiceDetail(details, "Amount", amount)
            addView(details)
        }
    }

    private fun addInvoiceDetail(container: LinearLayout, label: String, value: String) {
        if (value.isBlank()) return
        container.addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(Color.rgb(126, 139, 158))
                textSize = 9f
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                setTextColor(Color.rgb(52, 64, 84))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(3), 0, 0)
            })
        })
    }

    private fun addModuleIntro(module: Module) {
        val title = when (module) {
            Module.ATTENDANCE -> "Attendance overview"
            Module.TIMETABLE -> "Class schedule"
            Module.FEE -> "Fee overview"
            Module.PROFILE -> "Student profile"
            Module.RESULTS -> "Academic results"
        }
        val subtitle = when (module) {
            Module.ATTENDANCE -> "Subject wise attendance and current percentage"
            Module.TIMETABLE -> "Your classes, timings and rooms"
            Module.FEE -> "Invoices, due dates and payment information"
            Module.PROFILE -> "Live student information from your ERP account"
            Module.RESULTS -> "Live academic results from your ERP account"
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 18f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(8)) }
        }

        card.addView(TextView(this).apply {
            text = title
            setTextColor(Color.rgb(18, 58, 112))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.rgb(102, 112, 133))
            textSize = 12f
            setPadding(0, dp(5), 0, 0)
        })

        binding.moduleContent.addView(card)
    }


    private fun createMetricCard(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(this@MainActivity).apply {
                text = cleanDisplayText(label)
                setTextColor(Color.rgb(102, 112, 133))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = cleanDisplayText(value)
                setTextColor(Color.rgb(18, 58, 112))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun createTableSection(title: String, table: TableData): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }
        }

        outer.addView(TextView(this).apply {
            text = cleanDisplayText(title)
            setTextColor(Color.rgb(23, 32, 51))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(15), dp(16), dp(11))
        })

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tableLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), dp(10))
        }

        if (table.headers.isNotEmpty()) {
            tableLayout.addView(createTableRow(table.headers, true))
        }
        table.rows.forEach { row ->
            if (row.any { it.isNotBlank() }) tableLayout.addView(createTableRow(row, false))
        }

        horizontal.addView(tableLayout)
        outer.addView(horizontal)
        return outer
    }

    private fun createTableRow(values: List<String>, header: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = if (header) roundedBackground(Color.rgb(239, 245, 255), 10f) else roundedBackground(Color.WHITE, 0f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, if (header) dp(2) else dp(1), 0, 0) }
        }

        values.forEach { value ->
            row.addView(TextView(this).apply {
                text = cleanDisplayText(value).ifBlank { "—" }
                setTextColor(if (header) Color.rgb(18, 58, 112) else Color.rgb(52, 64, 84))
                textSize = if (header) 11f else 12f
                if (header) setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                minWidth = dp(112)
                maxWidth = dp(240)
            })
        }
        return row
    }

    private fun createInformationSection(lines: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }

            addView(TextView(this@MainActivity).apply {
                text = "Additional information"
                setTextColor(Color.rgb(23, 32, 51))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(16), dp(15), dp(16), dp(8))
            })
            lines.forEach { line ->
                addView(TextView(this@MainActivity).apply {
                    text = "• $line"
                    setTextColor(Color.rgb(82, 93, 112))
                    textSize = 12f
                    setPadding(dp(16), dp(5), dp(16), dp(5))
                })
            }
        }
    }


    private fun createEmptyState(
        title: String = "No data available",
        message: String = "Refresh to request the latest information."
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Color.WHITE, 18f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            setPadding(dp(24), dp(32), dp(24), dp(32))
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.rgb(18, 58, 112))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = message
                setTextColor(Color.rgb(102, 112, 133))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()


    private fun cleanDisplayText(value: String): String {
        return value
            .replace(Regex("(?i)\\bERP\\b"), "")
            .replace(Regex("(?i)\\bOdoo\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isUsefulDisplayText(value: String): Boolean {
        val text = value.trim()
        if (text.length < 2) return false
        if (text.equals("home", true) || text.equals("logout", true)) return false
        if (text.equals("dashboard", true) || text.equals("menu", true)) return false
        if (text.contains("your session", true) || text.contains("you've been inactive", true)) return false
        if (text.contains("stay online", true) || text.contains("session will expire", true)) return false
        if (text.equals("attendance", true) || text.equals("attendance classes", true) ||
            text.equals("active classes", true)) return false
        return true
    }

    private fun moduleDefaultTableTitle(module: Module, index: Int): String {
        return when (module) {
            Module.ATTENDANCE -> if (index == 0) "Attendance records" else "Attendance details"
            Module.TIMETABLE -> if (index == 0) "Class schedule" else "Schedule details"
            Module.FEE -> if (index == 0) "Fee records" else "Fee details"
            Module.PROFILE -> if (index == 0) "Profile details" else "Additional profile information"
            Module.RESULTS -> if (index == 0) "Academic results" else "Result details"
        }
    }


    private fun parseModuleData(payload: String): ModuleData? {
        return try {
            val json = JSONObject(payload)
            val cards = mutableListOf<Pair<String, String>>()
            json.optJSONArray("cards")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val label = cleanDisplayText(o.optString("label", ""))
                    val value = cleanDisplayText(o.optString("value", ""))
                    if (label.isNotBlank() && value.isNotBlank()) cards.add(label to value)
                }
            }

            val tables = mutableListOf<TableData>()
            val records = mutableListOf<ModuleRecord>()
            json.optJSONArray("tables")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    val headers = mutableListOf<String>()
                    t.optJSONArray("headers")?.let { h ->
                        for (j in 0 until h.length()) headers.add(cleanDisplayText(h.optString(j, "")))
                    }
                    val rows = mutableListOf<List<String>>()
                    t.optJSONArray("rows")?.let { rs ->
                        for (j in 0 until rs.length()) {
                            val ro = rs.optJSONArray(j) ?: continue
                            val row = mutableListOf<String>()
                            for (k in 0 until ro.length()) row.add(cleanDisplayText(ro.optString(k, "")))
                            if (row.any { it.isNotBlank() }) {
                                rows.add(row)
                                records.add(ModuleRecord(headers, row))
                            }
                        }
                    }
                    val title = cleanDisplayText(t.optString("title", ""))
                    if (headers.isNotEmpty() || rows.isNotEmpty()) tables.add(TableData(title, headers, rows))
                }
            }

            json.optJSONArray("records")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val h = mutableListOf<String>()
                    val v = mutableListOf<String>()
                    o.optJSONArray("headers")?.let { q ->
                        for (j in 0 until q.length()) h.add(cleanDisplayText(q.optString(j, "")))
                    }
                    o.optJSONArray("values")?.let { q ->
                        for (j in 0 until q.length()) v.add(cleanDisplayText(q.optString(j, "")))
                    }
                    if (v.any { it.isNotBlank() }) records.add(ModuleRecord(h, v))
                }
            }

            val lines = mutableListOf<String>()
            json.optJSONArray("lines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val line = cleanDisplayText(arr.optString(i, ""))
                    if (isUsefulDisplayText(line)) lines.add(line)
                }
            }

            val overall = json.optDouble("overallAttendance", Double.NaN)
                .takeUnless { it.isNaN() }
                ?.takeIf { it in 0.0..100.0 }

            ModuleData(
                cards.distinct(),
                tables,
                lines.distinct(),
                records.distinctBy { it.headers to it.values },
                overall
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readModuleData(view: WebView, module: Module, displayWhenReady: Boolean) {
        if (!loggedIn) return
        val requestId = moduleReadRequestId

        fun readNow() {
            if (!loggedIn || requestId != moduleReadRequestId) return
            view.evaluateJavascript(MODULE_DATA_SCRIPT) { result ->
                if (!loggedIn || requestId != moduleReadRequestId) return@evaluateJavascript
                val payload = decodeJavascriptString(result)
                val data = parseModuleData(payload)

                if (data != null) {
                    cachedModuleData[module] = payload
                    if (displayWhenReady && activeModule == module) renderModuleData(module, data)
                } else if (displayWhenReady && activeModule == module) {
                    binding.moduleProgress.visibility = View.GONE
                    binding.moduleInfo.text = "Could not read the latest data"
                    binding.moduleContent.removeAllViews()
                    binding.moduleContent.addView(createEmptyState())
                }

                if (!displayWhenReady && preloadingModule == module) {
                    preloadingModule = null
                    preloadNextModule()
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            view.postVisualStateCallback(requestId.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestIdFromWebView: Long) {
                    handler.postDelayed({ readNow() }, MODULE_VISUAL_DELAY_MS)
                }
            })
        } else {
            handler.postDelayed({ readNow() }, MODULE_READ_DELAY_MS)
        }
    }

    private fun updateNextClassPreview() {
        val label = binding.nextClassLabel
        val payload = cachedModuleData[Module.TIMETABLE]
        val data = payload?.let { parseModuleData(it) }
        val rows = data?.records?.mapNotNull(::toScheduleRow)
            ?.filter { it.time.isNotBlank() }
            ?.distinctBy { it.day + "|" + it.time + "|" + it.course + "|" + it.room }
            .orEmpty()

        if (rows.isEmpty()) {
            label.text = "Schedule synced"
            return
        }

        val now = java.util.Calendar.getInstance()
        val currentDay = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "Monday"
            java.util.Calendar.TUESDAY -> "Tuesday"
            java.util.Calendar.WEDNESDAY -> "Wednesday"
            java.util.Calendar.THURSDAY -> "Thursday"
            java.util.Calendar.FRIDAY -> "Friday"
            java.util.Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
        }
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

        fun startMinutes(time: String): Int? {
            val match = Regex("""([01]?\d|2[0-3]):([0-5]\d)""").find(time) ?: return null
            return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
        }

        val sameDay = rows.filter { it.day.equals(currentDay, true) }
        val next = sameDay.mapNotNull { row ->
            startMinutes(row.time)?.let { start -> if (start >= currentMinutes) start to row else null }
        }.minByOrNull { it.first }?.second
            ?: rows.mapNotNull { row -> startMinutes(row.time)?.let { it to row } }
                .minByOrNull { it.first }?.second

        if (next == null) {
            label.text = "Schedule synced"
        } else {
            label.text = "Next: " + next.time + " • " + next.course
        }
    }

    private fun showDashboard() {
        activeModule = null
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.moduleContent.removeAllViews()
        moduleFetchInProgress = false
        moduleReadRequestId++
        binding.dashboardScroll.visibility = View.VISIBLE
        binding.loginScroll.visibility = View.GONE

        val savedName = preferences.getString(KEY_STUDENT_NAME, "") ?: ""
        val savedCgpa = preferences.getString(KEY_CGPA, "") ?: ""
        val savedSgpa = preferences.getString(KEY_SGPA, "") ?: ""
        val legacyGpa = preferences.getString(KEY_GPA, "") ?: ""

        binding.studentName.text = if (isValidStudentName(savedName)) savedName else "Student"
        binding.studentCgpa.text = if (savedCgpa.isNotBlank()) savedCgpa else findGpaValue(legacyGpa, "CGPA").ifBlank { "—" }
        binding.studentSgpa.text = if (savedSgpa.isNotBlank()) savedSgpa else findGpaValue(legacyGpa, "SGPA").ifBlank { "—" }
        updateNextClassPreview()
    }

    private fun showLogin() {
        activeModule = null
        loggedIn = false
        loginInProgress = false
        restoringSession = false
        loginSubmitted = false
        profileFetchInProgress = false
        profileReadAttempts = 0
        loginAttempt = 0
        preloadingModule = null
        preloadQueue.clear()
        cachedModuleData.clear()
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.loginScroll.visibility = View.VISIBLE
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        binding.loginStatus.text = ""
    }

    private fun logout() {
        preferences.edit().clear().apply()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        binding.webView.clearHistory()
        binding.webView.clearCache(true)
        binding.webView.clearFormData()
        cachedModuleData.clear()
        showLogin()
    }

    private fun isAuthenticatedUrl(url: String): Boolean =
        url.contains("/student/", true) && !url.contains("/web/login", true)

    private fun failLogin(message: String) {
        loginInProgress = false
        loginSubmitted = false
        binding.loginButton.isEnabled = true
        binding.loginStatus.setTextColor(Color.rgb(198, 40, 40))
        binding.loginStatus.text = message
    }

    private fun isValidStudentName(value: String): Boolean {
        val text = value.trim()
        return text.isNotBlank() &&
            text.length >= 2 &&
            !text.contains("session", true) &&
            !text.contains("expire", true) &&
            !text.contains("dashboard", true) &&
            !text.contains("welcome", true) &&
            !text.contains("student information", true) &&
            !text.matches(Regex("SU\\d+[-A-Z0-9]*", RegexOption.IGNORE_CASE))
    }

    private fun decodeJavascriptString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return try {
            JSONObject("{\"value\":$value}").optString("value", "")
        } catch (_: Exception) {
            ""
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.visibility == View.VISIBLE) showDashboard() else super.onBackPressed()
    }

    companion object {
        private const val PREFERENCES_NAME = "superior_student_preferences"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_USERNAME = "username"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_CGPA = "student_cgpa"
        private const val KEY_SGPA = "student_sgpa"
        private const val KEY_GPA = "student_gpa"
        private const val ERP_BASE_URL = "https://erp.superior.edu.pk/"
        private const val ERP_LOGIN_URL = "https://erp.superior.edu.pk/web/login"
        private const val ERP_DASHBOARD_URL = "https://erp.superior.edu.pk/student/dashboard"
        private const val LOGIN_INJECTION_DELAY_MS = 500L
        private const val MAX_LOGIN_INJECTION_ATTEMPTS = 20
        private const val PROFILE_READ_DELAY_MS = 1200L
        private const val PROFILE_VISUAL_DELAY_MS = 500L
        private const val PROFILE_RETRY_DELAY_MS = 1000L
        private const val MAX_PROFILE_READ_ATTEMPTS = 8
        private const val MODULE_READ_DELAY_MS = 900L
        private const val MODULE_VISUAL_DELAY_MS = 300L

        private const val LOGIN_SCRIPT = """
            (function(){
              const user=document.querySelector('input[name="login"]');
              const pass=document.querySelector('input[name="password"],input[type="password"]');
              if(!user||!pass)return 'NO_FORM';
              user.value=%USERNAME%;
              pass.value=%PASSWORD%;
              user.dispatchEvent(new Event('input',{bubbles:true}));
              user.dispatchEvent(new Event('change',{bubbles:true}));
              pass.dispatchEvent(new Event('input',{bubbles:true}));
              pass.dispatchEvent(new Event('change',{bubbles:true}));
              const form=pass.form||user.form;
              if(!form)return 'NO_FORM';
              const redirect=form.querySelector('input[name="redirect"]');
              if(redirect)redirect.value='/student/dashboard';
              HTMLFormElement.prototype.submit.call(form);
              return 'SUBMITTED';
            })();
        """

        private const val LOGIN_ERROR_CHECK_SCRIPT = """
            (function(){
              const text=(document.body&&document.body.innerText||'').toLowerCase();
              return text.includes('wrong login') || text.includes('invalid login') || text.includes('incorrect') || text.includes('authentication failed');
            })();
        """


        private const val MODULE_DATA_SCRIPT = """
            (function(){
              function clean(value){return (value||'').replace(/\s+/g,' ').trim();}
              function textOf(el){return el ? clean(el.innerText || el.textContent || '') : '';}
              function safe(value){return clean(value).replace(/\bERP\b/gi,'').replace(/\bOdoo\b/gi,'').replace(/\s+/g,' ').trim();}
              function unique(list){
                const seen=new Set();
                return list.filter(function(item){
                  const key=JSON.stringify(item);
                  if(seen.has(key)) return false;
                  seen.add(key);
                  return true;
                });
              }

              let root=document.querySelector('main,[role="main"],.oe_structure,.container-fluid,.container');
              const candidates=Array.from(document.querySelectorAll('main,[role="main"],.oe_structure,.container-fluid,.container'));
              for(const candidate of candidates){
                if(candidate.querySelector('table')){root=candidate;break;}
              }
              if(!root) root=document.body;

              const clone=root.cloneNode(true);
              clone.querySelectorAll('header,nav,footer,aside,script,style,noscript,form').forEach(function(el){el.remove();});

              const tables=[];
              clone.querySelectorAll('table').forEach(function(table){
                const rows=Array.from(table.querySelectorAll('tr')).map(function(tr){
                  return Array.from(tr.querySelectorAll('th,td')).map(function(cell){return safe(textOf(cell));}).filter(function(v){return v!=='';});
                }).filter(function(row){return row.length>0;});
                if(!rows.length) return;

                let headers=[];
                const headerCells=table.querySelectorAll('thead th');
                if(headerCells.length){
                  headers=Array.from(headerCells).map(function(cell){return safe(textOf(cell));});
                }else if(table.querySelector('tr th')){
                  headers=Array.from(table.querySelectorAll('tr:first-child th')).map(function(cell){return safe(textOf(cell));});
                }

                let dataRows=rows;
                if(headers.length && dataRows.length && dataRows[0].join('|')===headers.join('|')) dataRows=dataRows.slice(1);

                const caption=table.querySelector('caption');
                tables.push({
                  title:safe(caption ? textOf(caption) : ''),
                  headers:headers,
                  rows:dataRows.slice(0,100)
                });
              });

              const cards=[];
              clone.querySelectorAll('.stat-card,.summary-card,.info-box').forEach(function(card){
                if(card.querySelector('table')) return;
                const cardLines=(card.innerText||card.textContent||'').split(/\n+/).map(clean).filter(Boolean);
                if(cardLines.length>=2) cards.push({label:safe(cardLines[0]),value:safe(cardLines.slice(1).join(' '))});
              });

              const lines=[];
              clone.querySelectorAll('h1,h2,h3,h4,p,li,.alert').forEach(function(el){
                if(el.closest('table')) return;
                const value=safe(textOf(el));
                if(value && value.length<=180) lines.push(value);
              });

              clone.querySelectorAll('[class*="calendar"],[class*="schedule"],[class*="event"],[class*="course"],[class*="attendance"],[id*="calendar"],[id*="schedule"],[id*="event"],[id*="course"],[id*="attendance"]').forEach(function(el){
                if(el.closest('table')) return;
                const value=safe(textOf(el));
                if(value && value.length<=250) lines.push(value);
              });

              const records=[];
              tables.forEach(function(table){table.rows.forEach(function(row){records.push({headers:table.headers,values:row});});});
              const selectors=['[class*="attendance"] [class*="row"]','[class*="attendance"] [class*="item"]','[class*="course"]','[class*="event"]','[class*="schedule"] [class*="item"]','[class*="calendar"] [class*="event"]'];
              selectors.forEach(function(selector){
                clone.querySelectorAll(selector).forEach(function(el){
                  const value=safe(textOf(el));
                  const label=safe(el.getAttribute('aria-label')||el.getAttribute('title')||'');
                  const combined=safe([label,value].filter(Boolean).join(' | '));
                  if(combined&&combined.length<=350)records.push({headers:[],values:[combined]});
                });
              });

              clone.querySelectorAll('[class*="progress"],[class*="percentage"],[class*="percent"],[aria-valuenow]').forEach(function(el){
                const value=safe([
                  el.getAttribute('aria-label')||'',
                  el.getAttribute('title')||'',
                  el.getAttribute('aria-valuenow') ? el.getAttribute('aria-valuenow')+'%' : '',
                  textOf(el)
                ].filter(Boolean).join(' '));
                if(value&&/%/.test(value))records.push({headers:[],values:[value]});
              });
              
              const percentPattern=/([0-9]{1,3}(?:\.[0-9]+)?)\s*%/;
              const codePattern=/\b[A-Z]{2,6}\d{5,}[A-Z0-9-]*\b/i;
              const subjectPattern=/functional english|quantitative reasoning|civics and community engagement|management of refractive errors|visual optics and image processing|redefining success/i;

              // Profile pages often use label/value rows instead of tables. Capture
              // those pairs explicitly so the native presentation does not lose fields.
              if(/\/student\/profile/i.test(location.pathname)){
                const profileSelectors=[
                  'dt','dd','.form-group','.form-row','.profile-field','.profile-item',
                  '.info-row','.info-item','.student-details .row',
                  '[class*="profile"] [class*="row"]',
                  '[class*="profile"] [class*="item"]',
                  '[class*="profile"] [class*="field"]',
                  '[class*="student"] [class*="row"]'
                ];
                profileSelectors.forEach(function(selector){
                  clone.querySelectorAll(selector).forEach(function(el){
                    if(el.closest('table')) return;
                    const parts=Array.from(el.children || []).map(function(child){return safe(textOf(child));}).filter(Boolean);
                    const raw=safe(textOf(el));
                    if(parts.length>=2 && parts.length<=6){
                      records.push({
                        headers:['Field','Value'],
                        values:[parts[0],parts.slice(1).join(' • ')]
                      });
                    }else if(raw && raw.length<=240){
                      const split=raw.split(/\n+/).map(clean).filter(Boolean);
                      if(split.length>=2 && split.length<=6){
                        records.push({
                          headers:['Field','Value'],
                          values:[split[0],split.slice(1).join(' • ')]
                        });
                      }
                    }
                  });
                });
              }

              // Results can be rendered as cards/list rows depending on the ERP
              // version. Capture semantic result/grade rows in addition to tables.
              if(/\/student\/results/i.test(location.pathname)){
                const resultSelectors=[
                  '[class*="result"] [class*="row"]',
                  '[class*="result"] [class*="item"]',
                  '[class*="result"] [class*="card"]',
                  '[class*="grade"] [class*="row"]',
                  '[class*="grade"] [class*="item"]',
                  '[class*="marks"] [class*="row"]',
                  '[class*="marks"] [class*="item"]',
                  '[class*="course"] [class*="row"]'
                ];
                resultSelectors.forEach(function(selector){
                  clone.querySelectorAll(selector).forEach(function(el){
                    if(el.closest('table')) return;
                    const value=safe(textOf(el));
                    if(value && value.length>=3 && value.length<=350){
                      records.push({headers:['Result'],values:[value]});
                    }
                  });
                });
              }


              let overallAttendance=null;
              const pageText=safe(clone.innerText||clone.textContent||'');
              const overallMatches=[
                /(?:overall|total)\s+attendance(?:\s+percentage)?\s*[:\-]?\s*([0-9]{1,3}(?:\.[0-9]+)?)\s*%/i,
                /(?:attendance\s+percentage|overall\s+percentage)\s*[:\-]?\s*([0-9]{1,3}(?:\.[0-9]+)?)\s*%/i
              ];
              for(const pattern of overallMatches){
                const m=pageText.match(pattern);
                if(m){overallAttendance=parseFloat(m[1]);break;}
              }

              const structuredRecords=[];
              const structuredCandidates=Array.from(clone.querySelectorAll('div,li,td,tr,section,article,.card,.row,.item'));
              structuredCandidates.forEach(function(el){
                const raw=safe(textOf(el));
                if(!raw || raw.length>320) return;
                const percent=raw.match(percentPattern);
                if(!percent) return;
                if(!codePattern.test(raw) && !subjectPattern.test(raw)) return;

                let subject=raw.replace(percent[0],'').trim();
                const codeMatch=subject.match(codePattern);
                const code=codeMatch ? codeMatch[0] : '';
                if(code) subject=subject.replace(code,'').trim();
                subject=subject.replace(/^[-•:|]+|[-•:|]+$/g,'').trim();
                if(subject.length<3) return;

                structuredRecords.push({
                  headers:['Subject','Course Code','Attendance Percentage'],
                  values:[subject,code,percent[1]+'%']
                });
              });

              structuredRecords.forEach(function(record){records.push(record);});

              const scheduleStructured=[];
              const scheduleSeen={};

              function addScheduleRecord(time, day, title){
                time=safe(time); day=safe(day); title=safe(title);
                if(title.length<3 || (!time && !day)) return;
                const key=time+'|'+day+'|'+title;
                if(scheduleSeen[key]) return;
                scheduleSeen[key]=true;
                scheduleStructured.push({
                  headers:['Day','Time','Class'],
                  values:[day,time,title]
                });
              }

              function attrText(el){
                if(!el) return '';
                const attrs=[
                  'aria-label','title','data-time','data-start','data-end','data-date',
                  'data-start-time','data-end-time','data-event','data-event-data',
                  'data-datetime','data-date-time','data-starttime','data-endtime'
                ];
                return attrs.map(function(name){
                  return el.getAttribute ? (el.getAttribute(name)||'') : '';
                }).filter(Boolean).join(' | ');
              }

              function findTime(value){
                const m=safe(value).match(/\\b(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s*[-–]\\s*(?:[01]?\\d|2[0-3]):[0-5]\\d)?\\b/);
                return m ? m[0] : '';
              }

              function findDay(value){
                const m=safe(value).match(/\\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b/i);
                return m ? m[0] : '';
              }

              function timeFromDate(value){
                const raw=safe(value);
                const match=raw.match(/T(\\d{2}):(\\d{2})/);
                if(match) return match[1]+':'+match[2];
                return '';
              }

              function dayFromDate(value){
                const raw=safe(value);
                const iso=raw.match(/(\\d{4})-(\\d{2})-(\\d{2})/);
                if(!iso) return '';
                const date=new Date(iso[1]+'-'+iso[2]+'-'+iso[3]+'T12:00:00');
                if(Number.isNaN(date.getTime())) return '';
                return ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'][date.getDay()];
              }

              function titleFromElement(el){
                if(!el) return '';
                const selectors=[
                  '.fc-event-title','.fc-title','.fc-event-main',
                  '[class*="event-title"]','[class*="course-name"]',
                  '[class*="course_title"]','[class*="subject-name"]',
                  '[class*="subject_name"]'
                ];
                for(const selector of selectors){
                  const node=el.querySelector ? el.querySelector(selector) : null;
                  const value=safe(textOf(node));
                  if(value.length>=3) return value;
                }
                return safe(el.getAttribute && (el.getAttribute('data-title')||el.getAttribute('data-name')||''));
              }

              function cleanScheduleTitle(value, code, time, day){
                let title=safe(value);
                if(!title) return '';
                title=title.replace(code,'').trim();
                if(time) title=title.replace(time,'').trim();
                if(day) title=title.replace(new RegExp('\\\\b'+day+'\\\\b','ig'),'').trim();
                title=title.replace(/\\b(?:Lecture|Lab|Practical|Theory)\\b/ig,' ').trim();
                title=title.replace(/(?:^|[|•])\\s*(?:Room\\s*)?[A-Z]{1,3}-?\\d{1,3}\\s*(?:\\|)?/ig,' ').trim();
                title=title.replace(/\\s+/g,' ').replace(/^[-•:|]+|[-•:|]+$/g,'').trim();
                return title;
              }

              function collectTimeLabels(){
                const labels=[];
                const nodes=Array.from(document.querySelectorAll('.fc-timegrid-slot-label,.fc-timegrid-slot-label-cushion,.fc-timegrid-axis-cushion,[class*="timegrid-slot-label"],div,span,td,th'));
                nodes.forEach(function(el){
                  const value=safe(textOf(el));
                  if(!/^\d{1,2}:\d{2}$/.test(value)) return;
                  const rect=el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                  if(!rect || rect.width<=0 || rect.height<=0) return;
                  const minutes=parseInt(value.slice(0,2),10)*60+parseInt(value.slice(3),10);
                  labels.push({time:value,minutes:minutes,top:rect.top,left:rect.left});
                });
                labels.sort(function(a,b){return a.top-b.top;});
                const unique=[];
                labels.forEach(function(item){
                  const duplicate=unique.some(function(existing){
                    return existing.time===item.time && Math.abs(existing.top-item.top)<3;
                  });
                  if(!duplicate) unique.push(item);
                });
                return unique;
              }

              function collectDayLabels(){
                const labels=[];
                document.querySelectorAll('[data-date]').forEach(function(el){
                  const date=safe(el.getAttribute('data-date')||'');
                  const day=dayFromDate(date);
                  if(!day) return;
                  const rect=el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                  if(!rect || rect.width<=0 || rect.height<=0) return;
                  labels.push({
                    day:day,
                    date:date,
                    center:rect.left+(rect.width/2),
                    top:rect.top
                  });
                });

                document.querySelectorAll('.fc-col-header-cell-cushion,.fc-col-header-cell,.fc-day-header,[class*="day-header"]').forEach(function(el){
                  const value=safe(textOf(el));
                  const day=findDay(value);
                  if(!day) return;
                  const rect=el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                  if(!rect || rect.width<=0 || rect.height<=0) return;
                  labels.push({day:day,date:'',center:rect.left+(rect.width/2),top:rect.top});
                });
                return labels;
              }

              const timeLabels=collectTimeLabels();
              const dayLabels=collectDayLabels();

              function inferTimeFromPosition(el){
                if(!el || !timeLabels.length || !el.getBoundingClientRect) return '';
                const rect=el.getBoundingClientRect();
                if(rect.width<=0 || rect.height<=0) return '';

                // FullCalendar time-grid events are positioned vertically by their
                // start time. Use the event's TOP, not its center, to avoid shifting
                // a 08:00 class into the next slot.
                const eventTop=rect.top;
                let best=null;
                let bestDistance=Infinity;
                timeLabels.forEach(function(label){
                  const d=Math.abs(eventTop-label.top);
                  if(d<bestDistance){bestDistance=d;best=label;}
                });

                if(!best) return '';

                // If labels are evenly spaced, interpolate between adjacent labels
                // so 08:30/09:00/etc are recovered even when the event does not sit
                // exactly on the label pixel.
                const ordered=timeLabels.slice().sort(function(a,b){return a.top-b.top;});
                for(let i=0;i<ordered.length-1;i++){
                  const a=ordered[i], b=ordered[i+1];
                  if(eventTop>=a.top && eventTop<=b.top && b.top>a.top){
                    const ratio=(eventTop-a.top)/(b.top-a.top);
                    const minutes=Math.round(a.minutes+(b.minutes-a.minutes)*ratio);
                    const snapped=Math.max(0,Math.min(1439,minutes));
                    const hh=String(Math.floor(snapped/60)).padStart(2,'0');
                    const mm=String(snapped%60).padStart(2,'0');
                    return hh+':'+mm;
                  }
                }

                return bestDistance<=140 ? best.time : '';
              }

              function inferDayFromPosition(el){
                if(!el || !el.getBoundingClientRect) return '';

                let parent=el;
                for(let depth=0;depth<8 && parent;depth++,parent=parent.parentElement){
                  const date=safe(parent.getAttribute && (parent.getAttribute('data-date')||''));
                  const day=dayFromDate(date);
                  if(day) return day;
                }

                if(!dayLabels.length) return '';
                const rect=el.getBoundingClientRect();
                if(rect.width<=0 || rect.height<=0) return '';
                const center=rect.left+(rect.width/2);
                let nearest=null;
                let distance=Infinity;
                dayLabels.forEach(function(label){
                  const d=Math.abs(center-label.center);
                  if(d<distance){distance=d;nearest=label;}
                });
                return nearest && distance<=220 ? nearest.day : '';
              }

              const eventSelectors=[
                '.fc-timegrid-event','.fc-event','.fc-daygrid-event','.fc-event-main',
                '.o_calendar_event','.calendar_event','[class*="calendar-event"]',
                '[class*="schedule-event"]','[class*="timetable-event"]',
                '[data-start]','[data-event]','[data-event-data]',
                '[data-time]','[data-datetime]'
              ];
              const eventNodes=[];
              eventSelectors.forEach(function(selector){
                document.querySelectorAll(selector).forEach(function(el){
                  if(eventNodes.indexOf(el)<0) eventNodes.push(el);
                });
              });

              eventNodes.forEach(function(el){
                const raw=safe(textOf(el));
                const attrs=safe(attrText(el));
                const titleCandidate=titleFromElement(el);
                if((!raw && !attrs && !titleCandidate) || raw.length>900) return;

                const combined=safe([raw,attrs,titleCandidate].filter(Boolean).join(' | '));
                const codeMatch=combined.match(codePattern);
                const genericTitle = titleCandidate && titleCandidate.length >= 3 && titleCandidate.length <= 140;
                if(!codeMatch && !subjectPattern.test(combined) && !genericTitle) return;

                let time=findTime(attrs) || findTime(raw) || findTime(titleCandidate);
                let day=findDay(attrs) || findDay(raw) || findDay(titleCandidate);

                if(!time){
                  time=timeFromDate(el.getAttribute && (
                    el.getAttribute('data-start') ||
                    el.getAttribute('data-datetime') ||
                    el.getAttribute('data-date-time') ||
                    ''
                  ));
                }
                if(!day){
                  day=dayFromDate(el.getAttribute && (
                    el.getAttribute('data-start') ||
                    el.getAttribute('data-datetime') ||
                    el.getAttribute('data-date-time') ||
                    ''
                  ));
                }

                if(!time || !day){
                  let parent=el.parentElement;
                  for(let depth=0;depth<4 && parent;depth++,parent=parent.parentElement){
                    const parentAttrs=safe(attrText(parent));
                    if(!time) time=findTime(parentAttrs) || timeFromDate(parent.getAttribute && parent.getAttribute('data-start') || '');
                    if(!day) day=findDay(parentAttrs) || dayFromDate(parent.getAttribute && parent.getAttribute('data-start') || '');
                    if(time && day) break;
                  }
                }

                if(!time) time=inferTimeFromPosition(el);
                if(!day) day=inferDayFromPosition(el);

                const code=codeMatch ? codeMatch[0] : '';
                let title=titleCandidate || raw || attrs;
                title=cleanScheduleTitle(title,code,time,day);

                if(title.length>140){
                  const fragments=title.split(/\s{2,}|\|/).map(clean).filter(Boolean);
                  const candidate=fragments.find(function(fragment){
                    return fragment.length>=3 && fragment.length<=110 &&
                      (subjectPattern.test(fragment) || codePattern.test(fragment));
                  });
                  if(candidate) title=candidate;
                }

                if(title.length<3 && code) title=code;
                if(title.length<3) return;
                addScheduleRecord(time,day,title);
              });

              // Non-calendar fallback: only use explicit time/day values from the
              // row itself. This prevents the first visible 08:00 axis label from
              // becoming the time for every subject.
              const fallbackNodes=Array.from(clone.querySelectorAll('li,td,tr,.card,.item,.row'));
              fallbackNodes.forEach(function(el){
                if(eventNodes.indexOf(el)>=0) return;
                const raw=safe(textOf(el));
                if(!raw || raw.length>320) return;
                const attrs=safe(attrText(el));
                const combined=safe([raw,attrs].filter(Boolean).join(' | '));
                if(!codePattern.test(combined) && !subjectPattern.test(combined)) return;

                const codeMatch=combined.match(codePattern);
                const code=codeMatch ? codeMatch[0] : '';
                const time=findTime(attrs) || findTime(raw);
                const day=findDay(attrs) || findDay(raw);
                const title=cleanScheduleTitle(raw,code,time,day);
                if(title.length<3 || (!time && !day)) return;
                addScheduleRecord(time,day,title);
              });

              scheduleStructured.forEach(function(record){records.push(record);});

              return JSON.stringify({
                cards:unique(cards).slice(0,8),
                tables:unique(tables).slice(0,12),
                lines:unique(lines).slice(0,60),
                records:unique(records).slice(0,160),
                overallAttendance:overallAttendance
              });
            })();
        """;
        private const val STUDENT_PROFILE_TEXT_SCRIPT = """
            (function(){
              function clean(value){return (value||'').replace(/\\s+/g,' ').trim();}
              function textOf(el){return el ? clean(el.textContent || el.innerText || '') : '';}
              function validName(value){
                const v=clean(value);
                return v && v.length>1 &&
                  !/session|expire|dashboard|welcome|student information/i.test(v) &&
                  !/^SU\\d+[-A-Z0-9]*$/i.test(v) &&
                  !/^BS\\s/i.test(v);
              }

              let name='';
              const selectors=[
                '.student-details h1',
                '.student-details h2',
                '.student-name',
                '.student_name'
              ];
              for(const selector of selectors){
                const candidate=textOf(document.querySelector(selector));
                if(validName(candidate)){name=candidate;break;}
              }

              if(!name){
                const box=document.querySelector('.student-details');
                if(box){
                  const lines=(box.innerText || box.textContent || '')
                    .split(/\\n+/)
                    .map(clean)
                    .filter(Boolean);
                  for(const line of lines){
                    if(validName(line)){name=line;break;}
                  }
                }
              }

              if(!name){
                const headings=Array.from(document.querySelectorAll('h1,h2,h3'));
                for(const heading of headings){
                  const candidate=textOf(heading);
                  if(validName(candidate) && !/^Results$/i.test(candidate)){
                    name=candidate;
                    break;
                  }
                }
              }

              const cards=Array.from(document.querySelectorAll('.stat-card')).map(function(card){
                return clean(card.textContent || card.innerText || '');
              });
              const body=clean(document.body ? (document.body.innerText || document.body.textContent || '') : '');
              return JSON.stringify({name:name,cards:cards,body:body});
            })();
        """
    }
}
