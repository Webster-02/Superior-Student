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

    private enum class Module(val path: String) {
        ATTENDANCE("student/attendance"),
        TIMETABLE("student/class/schedule"),
        FEE("student/invoices")
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
            webView.loadUrl(ERP_DASHBOARD_URL)
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
        }

        val payload = cachedModuleData[module]
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
        val records: List<ModuleRecord>
    )


    private fun renderModuleData(module: Module, data: ModuleData) {
        binding.moduleContent.removeAllViews()
        addModuleIntro(module)
        when (module) {
            Module.ATTENDANCE -> renderAttendance(data)
            Module.TIMETABLE -> renderTimetable(data)
            Module.FEE -> renderFee(data)
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
            binding.moduleContent.addView(createAttendanceSummary(percentages, rows.size))
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

    private fun createAttendanceSummary(percentages: List<Double>, count: Int): View {
        val average = if (percentages.isNotEmpty()) percentages.average() else 0.0
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.rgb(18, 58, 112), 20f)
            elevation = dp(3).toFloat()
            setPadding(dp(18), dp(17), dp(18), dp(17))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(6), dp(12), dp(10)) }

            addView(TextView(this@MainActivity).apply {
                text = "ATTENDANCE OVERVIEW"
                setTextColor(Color.rgb(190, 214, 244))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.08f
            })
            addView(TextView(this@MainActivity).apply {
                text = if (percentages.isNotEmpty()) String.format(java.util.Locale.US, "%.1f%%", average) else "—"
                setTextColor(Color.WHITE)
                textSize = 32f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(3), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = count.toString() + " subjects tracked"
                setTextColor(Color.rgb(221, 232, 247))
                textSize = 11f
            })
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
            rows.groupBy { it.day.ifBlank { "Class schedule" } }.forEach { (day, items) ->
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
            Regex("""(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[-–]\s*(?:[01]?\d|2[0-3]):[0-5]\d)?""").find(joined)?.value.orEmpty()
        }
        val day = headerValue("day", "date").ifBlank {
            Regex("""(?i)\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""").find(joined)?.value.orEmpty()
        }
        val code = Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE).find(joined)?.value.orEmpty()
        val course = headerValue("subject", "course", "class", "name").ifBlank {
            joined.replace(time, "").replace(day, "").trim(' ', '-', '–', '|').substringBefore(code).trim().ifBlank { code }
        }
        val room = headerValue("room", "venue", "location").ifBlank {
            values.lastOrNull().orEmpty().takeIf { it != course && it != time && it != day && it != code }.orEmpty()
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
            setPadding(dp(14), dp(13), dp(14), dp(13))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            addView(TextView(this@MainActivity).apply {
                text = row.time.ifBlank { "—" }
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(30, 91, 155))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.rgb(239, 245, 255), 12f)
                minWidth = dp(78)
                minHeight = dp(40)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(13), 0, 0, 0) }
                addView(TextView(this@MainActivity).apply {
                    text = row.course
                    setTextColor(Color.rgb(23, 42, 70))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (row.room.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = row.room
                    setTextColor(Color.rgb(126, 139, 158))
                    textSize = 10f
                    setPadding(0, dp(4), 0, 0)
                })
            })
        }

    private fun renderFee(data: ModuleData) {
        val invoices = data.tables.flatMap { table ->
            table.rows.map { row -> table.headers.zip(row).toMap() }
        }.filter { it.isNotEmpty() }

        if (invoices.isNotEmpty()) {
            binding.moduleContent.addView(createFeeSummary(invoices.size))
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
            addView(TextView(this).apply {
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
        }
        val subtitle = when (module) {
            Module.ATTENDANCE -> "Subject wise attendance and current percentage"
            Module.TIMETABLE -> "Your classes, timings and rooms"
            Module.FEE -> "Invoices, due dates and payment information"
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
        }
    }

    private fun parseModuleData(payload: String): ModuleData? {
        return try {
            val json=JSONObject(payload)
            val cards=mutableListOf<Pair<String,String>>()
            json.optJSONArray("cards")?.let{arr->for(i in 0 until arr.length()){val o=arr.optJSONObject(i)?:continue;val l=cleanDisplayText(o.optString("label",""));val v=cleanDisplayText(o.optString("value",""));if(l.isNotBlank()&&v.isNotBlank())cards.add(l to v)}}
            val tables=mutableListOf<TableData>()
            val records=mutableListOf<ModuleRecord>()
            json.optJSONArray("tables")?.let{arr->for(i in 0 until arr.length()){
                val t=arr.optJSONObject(i)?:continue;val headers=mutableListOf<String>()
                t.optJSONArray("headers")?.let{h->for(j in 0 until h.length())headers.add(cleanDisplayText(h.optString(j,"")))}
                val rows=mutableListOf<List<String>>()
                t.optJSONArray("rows")?.let{rs->for(j in 0 until rs.length()){val ro=rs.optJSONArray(j)?:continue;val row=mutableListOf<String>();for(k in 0 until ro.length())row.add(cleanDisplayText(ro.optString(k,"")));if(row.any{it.isNotBlank()}){rows.add(row);records.add(ModuleRecord(headers,row))}}}
                val title=cleanDisplayText(t.optString("title",""));if(headers.isNotEmpty()||rows.isNotEmpty())tables.add(TableData(title,headers,rows))
            }}
            json.optJSONArray("records")?.let{arr->for(i in 0 until arr.length()){val o=arr.optJSONObject(i)?:continue;val h=mutableListOf<String>();val v=mutableListOf<String>();o.optJSONArray("headers")?.let{q->for(j in 0 until q.length())h.add(cleanDisplayText(q.optString(j,"")))};o.optJSONArray("values")?.let{q->for(j in 0 until q.length())v.add(cleanDisplayText(q.optString(j,"")))};if(v.any{it.isNotBlank()})records.add(ModuleRecord(h,v))}}
            val lines=mutableListOf<String>();json.optJSONArray("lines")?.let{arr->for(i in 0 until arr.length()){val line=cleanDisplayText(arr.optString(i,""));if(isUsefulDisplayText(line))lines.add(line)}}
            ModuleData(cards.distinct(),tables,lines.distinct(),records.distinctBy{it.headers to it.values})
        }catch(_:Exception){null}
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
              
              return JSON.stringify({
                cards:unique(cards).slice(0,8),
                tables:unique(tables).slice(0,12),
                lines:unique(lines).slice(0,60),
                records:unique(records).slice(0,120)
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
