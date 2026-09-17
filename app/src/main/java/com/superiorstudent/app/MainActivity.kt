package com.superiorstudent.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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

    private data class ModuleData(
        val cards: List<Pair<String, String>>,
        val tables: List<TableData>,
        val lines: List<String>
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
        binding.moduleInfo.text = "Student data synced successfully"
    }

    private fun renderAttendance(data: ModuleData) {
        data.cards.take(3).forEach { (label, value) ->
            binding.moduleContent.addView(createMetricCard(label, value))
        }

        data.lines
            .map(::cleanDisplayText)
            .filter(::isAttendanceCourseLine)
            .distinct()
            .take(12)
            .forEachIndexed { index, item ->
                binding.moduleContent.addView(createCourseCard(item, index + 1))
            }

        data.tables.forEachIndexed { index, table ->
            binding.moduleContent.addView(
                createTableSection(
                    table.title.ifBlank { if (index == 0) "Attendance records" else "Attendance details" },
                    table
                )
            )
        }

        if (data.cards.isEmpty() && data.tables.isEmpty() &&
            data.lines.none { isAttendanceCourseLine(cleanDisplayText(it)) }) {
            binding.moduleContent.addView(createEmptyState())
        }
    }

    private fun renderTimetable(data: ModuleData) {
        val times = data.lines
            .map(::cleanDisplayText)
            .filter { it.matches(Regex("""^(?:[01]?\d|2[0-3]):[0-5]\d$""")) }
            .distinct()

        if (times.isNotEmpty()) binding.moduleContent.addView(createScheduleSummary(times))

        data.lines
            .map(::cleanDisplayText)
            .filter(::isTimetableCourseLine)
            .distinct()
            .take(20)
            .forEachIndexed { index, item ->
                binding.moduleContent.addView(createScheduleCard(item, index + 1))
            }

        data.tables.forEachIndexed { index, table ->
            binding.moduleContent.addView(
                createTableSection(
                    table.title.ifBlank { if (index == 0) "Class schedule" else "Schedule details" },
                    table
                )
            )
        }

        if (times.isEmpty() && data.tables.isEmpty() &&
            data.lines.none { isTimetableCourseLine(cleanDisplayText(it)) }) {
            binding.moduleContent.addView(createEmptyState())
        }
    }

    private fun renderFee(data: ModuleData) {
        data.cards.take(4).forEach { (label, value) ->
            binding.moduleContent.addView(createMetricCard(label, value))
        }

        data.tables.forEachIndexed { index, table ->
            binding.moduleContent.addView(
                createTableSection(
                    table.title.ifBlank { if (index == 0) "Fee records" else "Fee details" },
                    table
                )
            )
        }

        if (data.cards.isEmpty() && data.tables.isEmpty()) {
            val useful = data.lines
                .map(::cleanDisplayText)
                .filter(::isUsefulDisplayText)
                .distinct()
                .take(12)
            if (useful.isNotEmpty()) binding.moduleContent.addView(createInformationSection(useful))
            else binding.moduleContent.addView(createEmptyState())
        }
    }

    private fun addModuleIntro(module: Module) {
        val title = when (module) {
            Module.ATTENDANCE -> "Attendance overview"
            Module.TIMETABLE -> "Class schedule"
            Module.FEE -> "Fee overview"
        }
        val subtitle = when (module) {
            Module.ATTENDANCE -> "Your attendance information in a clean Superior Student view"
            Module.TIMETABLE -> "Your classes and schedule in a clean Superior Student view"
            Module.FEE -> "Your fee information in a clean Superior Student view"
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


    private fun isAttendanceCourseLine(value: String): Boolean {
        val text = value.trim()
        if (text.length < 8 || text.length > 180) return false
        if (text.contains("session", true) || text.contains("inactive", true) ||
            text.contains("stay online", true)) return false
        if (text.equals("attendance", true) || text.equals("attendance classes", true) ||
            text.equals("active classes", true)) return false
        return Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            listOf(
                "Functional English",
                "Quantitative Reasoning",
                "Civics and Community Engagement",
                "Management of refractive errors",
                "Visual Optics and Image Processing",
                "Redefining Success"
            ).any { text.contains(it, true) }
    }

    private fun isTimetableCourseLine(value: String): Boolean {
        val text = value.trim()
        if (text.length < 8 || text.length > 180) return false
        if (text.contains("session", true) || text.contains("inactive", true) ||
            text.contains("stay online", true)) return false
        if (text.matches(Regex("""^(?:Class Schedule|Term\s*:?.*|Month\s*:?.*)$""", RegexOption.IGNORE_CASE))) return false
        if (text.matches(Regex("""^(?:[01]?\d|2[0-3]):[0-5]\d$"""))) return false
        return Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            listOf(
                "Functional English",
                "Quantitative Reasoning",
                "Civics and Community Engagement",
                "Management of refractive errors",
                "Visual Optics and Image Processing",
                "Redefining Success"
            ).any { text.contains(it, true) }
    }

    private fun createCourseCard(course: String, position: Int): View {
        val codeMatch = Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE).find(course)
        val code = codeMatch?.value ?: ""
        val title = if (code.isNotBlank()) course.substringBefore(code).trim() else course

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(this@MainActivity).apply {
                text = position.toString()
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.rgb(30, 91, 155), 12f)
                minWidth = dp(34)
                minHeight = dp(34)
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)

                addView(TextView(this@MainActivity).apply {
                    text = title.ifBlank { course }
                    setTextColor(Color.rgb(23, 42, 70))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                if (code.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = code
                    setTextColor(Color.rgb(126, 139, 158))
                    textSize = 10f
                    setPadding(0, dp(4), 0, 0)
                })
            })
        }
    }

    private fun createScheduleSummary(times: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(8)) }
            setPadding(dp(16), dp(14), dp(16), dp(14))

            addView(TextView(this@MainActivity).apply {
                text = "Available time slots"
                setTextColor(Color.rgb(23, 42, 70))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
                times.take(8).forEach { time ->
                    addView(TextView(this@MainActivity).apply {
                        text = time
                        gravity = Gravity.CENTER
                        setTextColor(Color.rgb(30, 91, 155))
                        textSize = 11f
                        background = roundedBackground(Color.rgb(239, 245, 255), 10f)
                        setPadding(dp(10), dp(7), dp(10), dp(7))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, dp(6), 0) }
                    })
                }
            })
        }
    }

    private fun createScheduleCard(item: String, position: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.WHITE, 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(this@MainActivity).apply {
                text = position.toString()
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(30, 91, 155))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.rgb(239, 245, 255), 12f)
                minWidth = dp(34)
                minHeight = dp(34)
            })
            addView(TextView(this@MainActivity).apply {
                text = item
                setTextColor(Color.rgb(52, 64, 84))
                textSize = 13f
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
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

    private fun createEmptyState(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Color.WHITE, 16f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            setPadding(dp(24), dp(32), dp(24), dp(32))

            addView(TextView(this@MainActivity).apply {
                text = "No data available"
                setTextColor(Color.rgb(18, 58, 112))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "Tap Refresh to request the latest information."
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
            val json = JSONObject(payload)
            val cards = mutableListOf<Pair<String, String>>()
            json.optJSONArray("cards")?.let { array ->
                for (i in 0 until array.length()) {
                    val card = array.optJSONObject(i) ?: continue
                    val label = cleanDisplayText(card.optString("label", ""))
                    val value = cleanDisplayText(card.optString("value", ""))
                    if (label.isNotBlank() && value.isNotBlank()) cards.add(label to value)
                }
            }

            val tables = mutableListOf<TableData>()
            json.optJSONArray("tables")?.let { array ->
                for (i in 0 until array.length()) {
                    val tableJson = array.optJSONObject(i) ?: continue
                    val headers = mutableListOf<String>()
                    tableJson.optJSONArray("headers")?.let { headerArray ->
                        for (h in 0 until headerArray.length()) headers.add(headerArray.optString(h, ""))
                    }
                    val rows = mutableListOf<List<String>>()
                    tableJson.optJSONArray("rows")?.let { rowArray ->
                        for (r in 0 until rowArray.length()) {
                            val rowJson = rowArray.optJSONArray(r) ?: continue
                            val row = mutableListOf<String>()
                            for (c in 0 until rowJson.length()) row.add(rowJson.optString(c, ""))
                            rows.add(row)
                        }
                    }
                    val title = cleanDisplayText(tableJson.optString("title", ""))
                    if (headers.isNotEmpty() || rows.isNotEmpty()) tables.add(TableData(title, headers, rows))
                }
            }

            val lines = mutableListOf<String>()
            json.optJSONArray("lines")?.let { array ->
                for (i in 0 until array.length()) {
                    val line = cleanDisplayText(array.optString(i, ""))
                    if (isUsefulDisplayText(line)) lines.add(line)
                }
            }

            ModuleData(cards.distinct(), tables, lines.distinct())
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

              return JSON.stringify({
                cards:unique(cards).slice(0,8),
                tables:unique(tables).slice(0,12),
                lines:unique(lines).slice(0,30)
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
