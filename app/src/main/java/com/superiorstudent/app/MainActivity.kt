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
import com.superiorstudent.app.ui.Palette
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val sessionStore by lazy { SessionStore(this) }
    private val profileCache by lazy { ProfileCache(this) }

    private var loginInProgress = false
    private var loginSubmitted = false
    private var restoringSession = false
    private var loggedIn = false
    private var profileFetchInProgress = false
    private var profileReadAttempts = 0
    private var profileFetchGeneration = 0L
    private var moduleReadAttempts = 0
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
    /** Brand display font (Roboto Medium) used across programmatic views. */
    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    private var sessionHeartbeatRunning = false
    private val sessionResumeThresholdMs = 5 * 60 * 1000L
    private val sessionHeartbeatRunnable = object : Runnable {
        override fun run() {
            if (!sessionHeartbeatRunning || !loggedIn) return
            pingSession()
            handler.postDelayed(this, SESSION_HEARTBEAT_INTERVAL_MS)
        }
    }

    private enum class Module(val path: String) {
        ATTENDANCE("student/attendance"),
        TIMETABLE("student/class/schedule"),
        FEE("student/invoices"),
        PROFILE("student/profile"),
        RESULTS("student/results")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Wrap the whole startup in a safety net: if anything unexpected throws
        // (corrupt encrypted prefs, missing resource, WebView provider issues)
        // the app must never die silently with a black screen. Instead it shows
        // a readable error and recovers by wiping the damaged state.
        try {
            safeOnCreate(savedInstanceState)
        } catch (t: Throwable) {
            try {
                recoverFromStartupCrash(t)
            } catch (inner: Throwable) {
                // Last resort: plain toast + graceful finish instead of a crash loop.
                android.util.Log.e("MainActivity", "Unrecoverable startup failure", inner)
                Toast.makeText(applicationContext, "App failed to start. Please reinstall.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * Emergency recovery for startup crashes. Wipes the damaged local state
     * (encrypted prefs, caches, cookies) and retries the launch exactly once.
     * If the retry also fails the user sees a clear message instead of a
     * silent black-screen crash loop.
     */
    private var startupRecovered = false

    private fun recoverFromStartupCrash(t: Throwable) {
        android.util.Log.e("MainActivity", "Startup failed; wiping local state and retrying", t)
        if (startupRecovered) {
            Toast.makeText(applicationContext, "App could not start even after reset. Please reinstall.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startupRecovered = true
        try {
            getSharedPreferences("superior_session_encrypted", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("superior_session_fallback", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("superior_student_preferences", MODE_PRIVATE).edit().clear().apply()
            deleteDatabase("superior_session_encrypted")
            java.io.File(filesDir, "student_profile_cache.json").delete()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (ignored: Throwable) {
        }
        // Re-run startup with clean state (no saved instance -> fresh login screen).
        onCreate(null)
    }

    private fun safeOnCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WebView provider can be missing/disabled on some devices (e.g. System
        // WebView disabled by user). Detect it up-front and tell the user
        // instead of crashing when inflating the layout.
        try {
            val pkg = android.webkit.WebView.getCurrentWebViewPackage()
            if (pkg == null || pkg.applicationInfo?.enabled != true) {
                showErrorAndFinish("Android System WebView is disabled or missing on this device. Enable it from Play Store, then reopen the app.")
                return
            }
        } catch (ignored: Throwable) {
        }
        Scripts.loadAll(this)
        sessionStore.migrateFromLegacy()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginScroll.visibility = View.VISIBLE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.sideMenuOverlay.visibility = View.GONE

        val webView = binding.webView
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.setSupportZoom(false)
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        // Defense-in-depth: mixed content (http resources on https pages) is blocked.
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                // The student portal never needs camera/mic; deny explicitly.
                request.deny()
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // Block popups that could leak the session to an external page.
                Toast.makeText(view.context, "External popups are blocked for your security.", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (!ErpConfig.belongsToErp(target)) {
                    // Never navigate the authenticated WebView away from the ERP host.
                    return true
                }
                return false
            }

            @Suppress("DEPRECATION", "Overriding")
            override fun onReceivedSslError(
                view: WebView,
                handler: android.webkit.SslErrorHandler,
                error: android.net.http.SslError
            ) {
                // Strict TLS: refuse to load content when certificate validation fails.
                handler.cancel()
                if (activeModule != null) {
                    binding.moduleProgress.visibility = View.GONE
                    binding.moduleInfo.text = "Secure connection failed. Check your network and tap Refresh."
                } else if (loginInProgress || restoringSession) {
                    failLogin("Secure connection to the university portal failed. Please try again.")
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && activeModule != null) {
                    binding.moduleProgress.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Unable to load your information. Check internet and try Refresh.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                if (!loginInProgress && !profileFetchInProgress && ErpConfig.isLoginUrl(url)) {
                    restoringSession = false
                    loggedIn = false
                    sessionStore.sessionActive = false
                    showLogin()
                    Toast.makeText(this@MainActivity, "Your university session expired. Please sign in again.", Toast.LENGTH_LONG).show()
                    return
                }

                if (restoringSession) {
                    if (isAuthenticatedUrl(url)) {
                        restoringSession = false
                        completeLogin()
                    } else if (ErpConfig.isLoginUrl(url)) {
                        restoringSession = false
                        sessionStore.sessionActive = false
                        showLogin()
                    }
                    return
                }

                if (loginInProgress) {
                    if (isAuthenticatedUrl(url)) {
                        completeLogin()
                    } else if (ErpConfig.isLoginUrl(url)) {
                        if (!loginSubmitted) {
                            loginAttempt = 0
                            scheduleLoginInjection(view)
                        } else {
                            view.evaluateJavascript(Scripts.loginErrorCheck) { result ->
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
                binding.loginStatus.setTextColor(Palette.danger(this@MainActivity))
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
            binding.loginStatus.setTextColor(Palette.ink500(this@MainActivity))
            binding.loginStatus.text = "Signing in securely…"
            webView.visibility = View.GONE
            webView.loadUrl(ErpConfig.LOGIN_URL)
        }

        binding.attendanceButton.setOnClickListener { loadModule(Module.ATTENDANCE) }
        binding.timetableButton.setOnClickListener { loadModule(Module.TIMETABLE) }
        binding.feeButton.setOnClickListener { loadModule(Module.FEE) }
        binding.refreshButton.setOnClickListener { refreshActiveModule() }
        binding.homeButton.setOnClickListener { showDashboard() }
        binding.menuButton.setOnClickListener { openSideMenu() }
        binding.closeMenuButton.setOnClickListener { closeSideMenu() }
        binding.menuScrim.setOnClickListener { closeSideMenu() }
        binding.menuProfileButton.setOnClickListener { closeSideMenu(); loadModule(Module.PROFILE) }
        binding.menuResultsButton.setOnClickListener { closeSideMenu(); loadModule(Module.RESULTS) }
        binding.menuLogoutButton.setOnClickListener { closeSideMenu(); logout() }

        // Design-system touches: press-scale feedback on dashboard action cards.
        attachPressFeedback(binding.attendanceButton)
        attachPressFeedback(binding.timetableButton)
        attachPressFeedback(binding.feeButton)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else if (sessionStore.sessionActive) {
            username = sessionStore.username
            restoringSession = true
            binding.loginScroll.visibility = View.GONE
            binding.dashboardScroll.visibility = View.VISIBLE
            webView.visibility = View.GONE
            showDashboard()
            binding.webView.loadUrl(ErpConfig.DASHBOARD_URL)
        }
    }

    override fun onResume() {
        super.onResume()
        val pausedFor = if (lastPausedAt > 0L) System.currentTimeMillis() - lastPausedAt else 0L
        lastPausedAt = 0L
        if (pausedFor >= sessionResumeThresholdMs && loggedIn) {
            recoverSessionAfterBackground()
        }
        // Staggered entrance animation whenever the dashboard becomes visible.
        if (loggedIn && binding.dashboardScroll.visibility == View.VISIBLE) {
            animateDashboardEntrance()
        }
    }

    /** Sequential fade/scale-in for the dashboard hero + stat + action cards. */
    private fun animateDashboardEntrance() {
        val targets = listOf(
            binding.dashboardScreen.getChildAt(0),  // gradient header
            binding.studentName                      // welcome card name
        )
        var delay = 0L
        for (t in targets) {
            t?.animate()?.alpha(0f)?.setDuration(0)?.withEndAction {
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_scale_in).let { anim ->
                    anim.startOffset = delay
                    t.startAnimation(anim)
                }
            }?.start()
            delay += 90
        }
    }

    override fun onPause() {
        lastPausedAt = System.currentTimeMillis()
        super.onPause()
    }

    private fun recoverSessionAfterBackground() {
        // Do not cancel the global Handler queue here. The session heartbeat and
        // pending extraction retries are lifecycle-aware and should survive a
        // normal background/foreground transition.
        handler.removeCallbacks(moduleRecoveryRunnable)

        if (!loggedIn) return

        val module = activeModule
        binding.webView.stopLoading()
        binding.webView.visibility = View.INVISIBLE

        if (module != null && binding.moduleScreen.visibility == View.VISIBLE) {
            moduleReadRequestId++
            moduleReadAttempts = 0
            binding.moduleInfo.text = "Reconnecting to your student account…"
            binding.moduleProgress.visibility = View.VISIBLE

            // Keep the last known good presentation visible during reconnects.
            // This prevents the blank-screen flash reported after the app sits
            // idle in the background for several minutes.
            cachedModuleData[module]?.let { payload ->
                parseModuleData(payload)?.takeIf { hasUsableModuleData(module, it) }?.let { cached ->
                    binding.moduleContent.removeAllViews()
                    renderModuleData(module, cached)
                    binding.moduleInfo.text = "Last synced data • Updating from ERP…"
                    binding.moduleProgress.visibility = View.VISIBLE
                }
            }

            moduleFetchInProgress = true
            binding.webView.loadUrl(ErpConfig.moduleUrl(module.path))
        } else {
            restoringSession = true
            binding.webView.loadUrl(ErpConfig.DASHBOARD_URL)
        }

        // Always restart the heartbeat after a long resume.
        startSessionHeartbeat()
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
        val script = Scripts.loginScript(username, password)
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
        startSessionHeartbeat()
        sessionStore.sessionActive = true
        sessionStore.username = username
        CookieManager.getInstance().flush()
        binding.loginStatus.text = ""
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        showDashboard()
        fetchStudentProfile()
    }

    private fun fetchStudentProfile() {
        if (!loggedIn) return
        profileFetchGeneration++
        profileFetchInProgress = true
        profileReadAttempts = 0
        // Keep the WebView attached while waiting for the visual DOM state.
        // This makes postVisualStateCallback reliable even though the ERP page is not shown to the user.
        binding.webView.visibility = View.INVISIBLE
        binding.webView.loadUrl(ErpConfig.DASHBOARD_URL)
    }

    private fun waitForProfileDom(view: WebView) {
        if (!loggedIn || !profileFetchInProgress) return
        val generation = profileFetchGeneration

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            view.postVisualStateCallback(profileReadAttempts.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (!loggedIn || !profileFetchInProgress || generation != profileFetchGeneration) return
                    handler.postDelayed({
                        if (generation == profileFetchGeneration) extractAndApplyProfile(view)
                    }, PROFILE_VISUAL_DELAY_MS)
                }
            })
        } else {
            handler.postDelayed({
                if (generation == profileFetchGeneration) extractAndApplyProfile(view)
            }, PROFILE_READ_DELAY_MS)
        }
    }

    private fun extractAndApplyProfile(view: WebView) {
        if (!loggedIn || !profileFetchInProgress) return

        profileReadAttempts++
        view.evaluateJavascript(Scripts.studentProfileText) { result ->
            val payload = ParsingUtils.decodeJavascriptString(result)
            var name = ""
            var cgpa = ""
            var sgpa = ""
            var hasUsefulProfileFields = false

            try {
                val json = JSONObject(payload)
                name = json.optString("name", "").trim()
                val bodyText = json.optString("body", "")
                val cards = json.optJSONArray("cards")
                cgpa = findGpaInText(cards, bodyText, "CGPA")
                sgpa = findGpaInText(cards, bodyText, "SGPA")
                hasUsefulProfileFields = json.optBoolean("hasUsefulFields", false)

                if (ParsingUtils.isValidStudentName(name)) {
                    binding.studentName.text = name
                }
                if (cgpa.isNotBlank()) binding.studentCgpa.text = cgpa
                if (sgpa.isNotBlank()) binding.studentSgpa.text = sgpa
                profileCache.save(
                    name.takeIf { ParsingUtils.isValidStudentName(it) },
                    cgpa.ifBlank { null },
                    sgpa.ifBlank { null }
                )
            } catch (_: Exception) {
                // The ERP may still be hydrating dynamic fields.
            }

            val ready = ParsingUtils.isValidStudentName(name) && (cgpa.isNotBlank() || sgpa.isNotBlank())
            if (ready || (hasUsefulProfileFields && ParsingUtils.isValidStudentName(name))) {
                profileFetchInProgress = false
                preloadAllModules()
                return@evaluateJavascript
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
                val value = ParsingUtils.findGpaValue(cardText, label)
                if (value.isNotBlank()) return value
            }
        }
        return ParsingUtils.findGpaValue(bodyText, label)
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
        binding.webView.loadUrl(ErpConfig.moduleUrl(preloadingModule!!.path))
    }

    private fun loadModule(module: Module) {
        if (!loggedIn) return
        closeSideMenu()

        preloadingModule = null
        preloadQueue.clear()
        moduleFetchInProgress = false
        moduleReadAttempts = 0
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
        binding.webView.loadUrl(ErpConfig.moduleUrl(module.path))
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        cachedModuleData.remove(module)
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleContent.removeAllViews()
        binding.moduleInfo.text = "Syncing the latest information…"
        binding.webView.visibility = View.INVISIBLE
        binding.webView.loadUrl(ErpConfig.moduleUrl(module.path))
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
        val overallAttendance: Double?,
        val semesterOptions: List<String> = emptyList()
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
        val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
        if (values.isEmpty()) return null
        val headers = record.headers.map(ParsingUtils::cleanDisplayText)
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

        val percent = ParsingUtils.parsePercent(percentFromHeader)
            ?: Regex("""(\d{1,3}(?:\.\d{1,2})?)\s*%""").find(joined)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)

        val code = codeFromHeader.ifBlank {
            Regex("""\b(?:HOM|HIM|GEN|HOQ)\d{5,}[A-Z0-9-]*\b""", RegexOption.IGNORE_CASE).find(joined)?.value.orEmpty()
        }

        val course = courseFromHeader.ifBlank {
            val withoutPercent = joined.replace(Regex("""\d{1,3}(?:\.\d{1,2})?\s*%"""), "")
            withoutPercent.substringBefore(code).trim().ifBlank { withoutPercent.trim() }
        }

        val present = presentFromHeader.ifBlank { ParsingUtils.extractCountNearLabel(joined, "present", "attended", "attendance") }
        val total = totalFromHeader.ifBlank { ParsingUtils.extractCountNearLabel(joined, "total", "classes") }

        if (course.length < 3 || (percent == null && code.isBlank())) return null
        return AttendanceRow(course, code, percent, present, total)
    }

    
    

    private fun attendanceStatus(percent: Double): Pair<Int, String> {
        return when {
            percent >= 85.0 -> Palette.success(this@MainActivity) to "Good standing"
            percent >= 75.0 -> Palette.warning(this@MainActivity) to "Needs attention"
            else -> Palette.danger(this@MainActivity) to "Low attendance"
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
                setTextColor(Palette.ink900(this@MainActivity))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(Palette.ink500(this@MainActivity))
                textSize = 11f
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun createAttendanceSummary(overall: Double?, percentages: List<Double>, count: Int): View {
        val activity = this@MainActivity
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(Palette.primary(activity), Palette.violet(activity), 22f)
            elevation = dp(4).toFloat()
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(6), dp(12), dp(12)) }

            val display = overall
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(activity).apply {
                    text = if (overall != null) "OVERALL ATTENDANCE" else "ATTENDANCE SUMMARY"
                    setTextColor(Color.argb(230, 255, 255, 255))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.10f
                })
                addView(TextView(activity).apply {
                    text = display?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "—"
                    setTextColor(Color.WHITE)
                    textSize = 34f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(4), 0, 0)
                })
                addView(TextView(activity).apply {
                    text = if (overall != null) {
                        "$count subjects • official overall percentage"
                    } else {
                        "$count subjects • percentage shown per subject"
                    }
                    setTextColor(Color.argb(210, 255, 255, 255))
                    textSize = 11f
                })
            })

            if (display != null) {
                // Animated-feel progress ring with centered label on the gradient card.
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(10), 0, 0, 0)
                    addView(createProgressRing(display, 74, 8, Color.WHITE))
                    addView(TextView(activity).apply {
                        text = attendanceStatus(display).second.uppercase(java.util.Locale.US)
                        setTextColor(Color.argb(220, 255, 255, 255))
                        textSize = 9f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        letterSpacing = 0.08f
                        setPadding(0, dp(6), 0, 0)
                        gravity = Gravity.CENTER
                    })
                })
            }
        }
    }

    private fun createAttendanceCard(row: AttendanceRow, position: Int): View {
        val percent = row.percent
        val statusColor = when {
            percent == null -> Palette.ink500(this@MainActivity)
            percent >= 85.0 -> Palette.success(this@MainActivity)
            percent >= 75.0 -> Palette.warning(this@MainActivity)
            else -> Palette.danger(this@MainActivity)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 18f)
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
                setTextColor(Palette.primary(this@MainActivity))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(this@MainActivity), 11f)
                minWidth = dp(38)
                minHeight = dp(34)
            })
            header.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(12), 0, dp(8), 0) }
                addView(TextView(this@MainActivity).apply {
                    text = row.course
                    setTextColor(Palette.ink900(this@MainActivity))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (row.code.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = row.code
                    setTextColor(Palette.ink500(this@MainActivity))
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
                progressBackgroundTintList = ColorStateList.valueOf(Palette.ringTrack(this@MainActivity))
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
            setTextColor(Palette.ink700(this@MainActivity))
            textSize = 11f
            setPadding(0, 0, dp(18), 0)
        })
    }

    private data class ScheduleRow(
        val day: String,
        val time: String,
        val course: String,
        val room: String,
        val code: String
    )

    private fun renderTimetable(data: ModuleData) {
        val rows = data.records.mapNotNull(::toScheduleRow)
            .distinctBy { it.day + "|" + it.time + "|" + it.course + "|" + it.room + "|" + it.code }
            .take(60)

        binding.moduleContent.addView(createTimetableToolbar(rows.size))

        if (rows.isEmpty()) {
            data.tables.forEachIndexed { index, table ->
                binding.moduleContent.addView(
                    createTableSection(
                        table.title.ifBlank { if (index == 0) "Class schedule" else "Schedule details" },
                        table
                    )
                )
            }
            binding.moduleContent.addView(
                createEmptyState(
                    "Timetable is still syncing",
                    "The ERP timetable is loaded dynamically. Tap Refresh and the app will retry the live schedule."
                )
            )
            return
        }

        val dayOrder = mapOf(
            "Monday" to 1, "Tuesday" to 2, "Wednesday" to 3,
            "Thursday" to 4, "Friday" to 5, "Saturday" to 6, "Sunday" to 7
        )
        val sortedRows = rows.sortedWith(
            compareBy<ScheduleRow> { row ->
                dayOrder.entries.firstOrNull { entry -> row.day.equals(entry.key, true) }?.value ?: 99
            }.thenBy { row ->
                ParsingUtils.timeSortKey(row.time)
            }
        )

        sortedRows.groupBy { it.day.ifBlank { "Scheduled classes" } }
            .toSortedMap(compareBy { dayOrder[it] ?: 99 })
            .forEach { (day, items) ->
                binding.moduleContent.addView(createDayHeader(day, items.size))
                items.forEach { row ->
                    binding.moduleContent.addView(createScheduleCard(row))
                }
            }

        binding.moduleContent.addView(createTimetableInfoCard())
    }

    
    private fun createTimetableToolbar(count: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(
                Palette.primary(this@MainActivity),
                Palette.violet(this@MainActivity), 24f
            )
            elevation = dp(5).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }

            // Calendar glyph badge
            addView(TextView(this@MainActivity).apply {
                text = "🗓"
                gravity = Gravity.CENTER
                textSize = 19f
                background = roundedBackground(Color.argb(60, 255, 255, 255), 14f)
                minWidth = dp(46)
                minHeight = dp(46)
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    setMargins(0, 0, dp(13), 0)
                }
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = "THIS WEEK"
                    setTextColor(Palette.primaryLight(this@MainActivity))
                    textSize = 9f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.12f
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Your Class Schedule"
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(2), 0, dp(1))
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (count == 1) "1 class synced from ERP" else "$count classes synced from ERP"
                    setTextColor(Color.argb(210, 255, 255, 255))
                    textSize = 11f
                })
            })

            // Live pill
            addView(TextView(this@MainActivity).apply {
                text = "LIVE"
                setTextColor(Color.WHITE)
                textSize = 9f
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Color.argb(60, 255, 255, 255), 20f)
                setPadding(dp(11), dp(6), dp(11), dp(6))
            })
        }

    private fun createTimetableInfoCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.infoBg(this@MainActivity), 18f)
            setPadding(dp(15), dp(14), dp(15), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(18)) }

            addView(TextView(this@MainActivity).apply {
                text = "ⓘ"
                setTextColor(Palette.primary(this@MainActivity))
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = "Timetable Information"
                    setTextColor(Palette.primaryDark(this@MainActivity))
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Timings, courses, codes and rooms are read from your ERP account."
                    setTextColor(Palette.ink700(this@MainActivity))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }

    private fun toScheduleRow(record: ModuleRecord): ScheduleRow? {
        val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
        if (values.isEmpty()) return null
        val headers = record.headers.map(ParsingUtils::cleanDisplayText)
        val joined = values.joinToString(" ")
        if (joined.contains("session", true) || joined.contains("inactive", true) || joined.contains("stay online", true)) return null

        fun headerValue(vararg keys: String): String {
            val index = headers.indexOfFirst { h -> keys.any { key -> h.contains(key, true) } }
            return if (index >= 0 && index < values.size) values[index] else ""
        }

        val time = headerValue("time", "timing", "start time", "period").ifBlank {
            Regex("""(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s*[-–]\\s*(?:[01]?\\d|2[0-3]):[0-5]\\d)?""")
                .find(joined)?.value.orEmpty()
        }
        val day = headerValue("day", "date", "weekday").ifBlank {
            Regex("""(?i)\\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b""")
                .find(joined)?.value.orEmpty()
        }
        val code = headerValue("course code", "subject code", "code").ifBlank {
            Regex("""\\b[A-Z]{2,8}[- ]?\\d{2,6}[A-Z0-9-]*\\b""", RegexOption.IGNORE_CASE)
                .find(joined)?.value.orEmpty()
        }
        val course = headerValue("subject", "course name", "course", "class", "title", "name").ifBlank {
            val withoutMeta = joined
                .replace(time, "")
                .replace(day, "")
                .replace(code, "")
                .replace(Regex("""(?i)\\b(?:room|venue|location)\\s*[:#-]?\\s*[A-Z0-9-]+\\b"""), "")
                .trim(' ', '-', '–', '|', '•')
            withoutMeta.split(Regex("""\\s{2,}|\\|"""))
                .map { it.trim() }
                .firstOrNull { it.length >= 3 }
                ?: withoutMeta
        }
        val room = headerValue("room", "venue", "location", "class room").ifBlank {
            Regex("""(?i)\\b(?:room|venue)\\s*[:#-]?\\s*([A-Z0-9-]+)\\b""")
                .find(joined)?.groupValues?.getOrNull(1).orEmpty()
        }
        if (course.length < 3 || (time.isBlank() && day.isBlank())) return null
        return ScheduleRow(day, time, course, room, code)
    }

    private fun createDayHeader(day: String, count: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(14), dp(14), dp(14), dp(4)) }

            // Accent rail
            addView(View(this@MainActivity).apply {
                background = gradientBackground(
                    Palette.primary(this@MainActivity),
                    Palette.accent(this@MainActivity), 4f
                )
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(20)).apply {
                    setMargins(0, 0, dp(9), 0)
                }
            })
            addView(TextView(this@MainActivity).apply {
                text = day.uppercase(java.util.Locale.US)
                setTextColor(Palette.ink900(this@MainActivity))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            // Count chip
            addView(TextView(this@MainActivity).apply {
                text = "$count"
                setTextColor(Palette.primaryDark(this@MainActivity))
                textSize = 10f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(this@MainActivity), 16f)
                minWidth = dp(26)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
            addView(TextView(this@MainActivity).apply {
                text = if (count == 1) "class" else "classes"
                setTextColor(Palette.ink500(this@MainActivity))
                textSize = 10f
                setPadding(dp(6), 0, 0, 0)
            })
        }

    private fun createScheduleCard(row: ScheduleRow): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 20f)
            elevation = dp(3).toFloat()
            setPadding(dp(12), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val accent = when (ParsingUtils.timeSortKey(row.time) % 5) {
                0 -> Palette.primary(this@MainActivity)
                1 -> Palette.success(this@MainActivity)
                2 -> Palette.warning(this@MainActivity)
                3 -> Palette.violet(this@MainActivity)
                else -> Palette.danger(this@MainActivity)
            }

            // Gradient time tile with top rail
            addView(android.widget.FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(86), dp(76)).apply {
                    setMargins(0, 0, dp(11), 0)
                }
                addView(View(this@MainActivity).apply {
                    background = gradientBackground(
                        tintAlpha(accent, 26),
                        tintAlpha(Palette.ink900(this@MainActivity), 26),
                        18f
                    )
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
                addView(View(this@MainActivity).apply {
                    background = roundedBackground(accent, 3f)
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(34), dp(4)).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    }
                })
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPadding(dp(4), dp(9), dp(4), dp(4))
                    addView(TextView(this@MainActivity).apply {
                        text = ParsingUtils.formatScheduleTime(row.time)
                            .ifBlank { "—" }
                        gravity = Gravity.CENTER
                        setTextColor(Palette.ink900(this@MainActivity))
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = row.day.take(3).uppercase(java.util.Locale.US).ifBlank { "TBD" }
                        gravity = Gravity.CENTER
                        setTextColor(Palette.ink500(this@MainActivity))
                        textSize = 9f
                        letterSpacing = 0.12f
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(this@MainActivity).apply {
                    text = row.course
                    setTextColor(Palette.ink900(this@MainActivity))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 2
                })

                val metaRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(7), 0, 0) }
                }
                if (row.code.isNotBlank()) metaRow.addView(TextView(this@MainActivity).apply {
                    text = row.code
                    setTextColor(Palette.primaryDark(this@MainActivity))
                    textSize = 9f
                    letterSpacing = 0.05f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Palette.infoBg(this@MainActivity), 9f)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                })
                metaRow.addView(TextView(this@MainActivity).apply {
                    text = if (row.room.isNotBlank()) "📍 ${row.room}" else "📍 Room TBA"
                    setTextColor(if (row.room.isNotBlank()) Palette.success(this@MainActivity) else Palette.ink400(this@MainActivity))
                    textSize = 10f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", if (row.room.isNotBlank()) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    setPadding(if (row.code.isNotBlank()) dp(9) else 0, 0, 0, 0)
                })
                addView(metaRow)
            })

            attachPressFeedback(this)
        }

    
    
    private fun createProfileFieldCard(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 16f)
            elevation = dp(2).toFloat()
            setPadding(dp(13), dp(11), dp(13), dp(11))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) }

            addView(TextView(this@MainActivity).apply {
                text = label.trim().uppercase(java.util.Locale.US)
                setTextColor(Palette.primary(this@MainActivity))
                textSize = 9f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.07f
            })
            addView(TextView(this@MainActivity).apply {
                text = value.trim()
                setTextColor(Palette.ink900(this@MainActivity))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun renderProfile(data: ModuleData) {
        val fields = extractProfileFields(data)

        binding.moduleContent.addView(
            createSectionHeading(
                "Student profile",
                "Live personal and academic information from your ERP account"
            )
        )

        if (fields.isNotEmpty()) {
            binding.moduleContent.addView(createProfileHero(fields))

            val priority = fields.filter { field ->
                ParsingUtils.isProfileLabel(
                    field.first,
                    "student name", "full name", "name",
                    "registration", "roll no", "student id", "student code",
                    "program", "degree", "course of study",
                    "department", "faculty", "semester", "session", "campus"
                )
            }.distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }

            if (priority.isNotEmpty()) {
                binding.moduleContent.addView(
                    createSectionHeading(
                        "Academic & identity",
                        "Key fields returned by the ERP"
                    )
                )
                priority.take(12).chunked(2).forEach { pair ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    pair.forEach { field ->
                        row.addView(createProfileFieldCard(field.first, field.second).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(dp(5), dp(4), dp(5), dp(4)) }
                        })
                    }
                    binding.moduleContent.addView(row)
                }
            }

            val secondary = fields.filterNot { priority.contains(it) }
            if (secondary.isNotEmpty()) {
                binding.moduleContent.addView(
                    createSectionHeading(
                        "Additional details",
                        "Other readable ERP fields"
                    )
                )
                secondary.take(24).forEach { (label, value) ->
                    binding.moduleContent.addView(createProfileFieldCard(label, value))
                }
            }
        } else {
            data.cards.take(6).forEach { (label, value) ->
                binding.moduleContent.addView(createProfileFieldCard(label, value))
            }
            data.tables.take(3).forEachIndexed { index, table ->
                binding.moduleContent.addView(
                    createTableSection(
                        table.title.ifBlank { if (index == 0) "Profile details" else "Additional information" },
                        table
                    )
                )
            }
            if (data.cards.isEmpty() && data.tables.isEmpty()) {
                binding.moduleContent.addView(
                    createEmptyState(
                        "Profile information unavailable",
                        "The ERP profile page did not expose readable fields. Tap Refresh to sync again."
                    )
                )
            }
        }
    }

    private fun extractProfileFields(data: ModuleData): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        data.records.forEach { record ->
            val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
            val fieldRecord = record.headers.any { h ->
                h.equals("Field", true) || h.equals("Label", true)
            }
            if (fieldRecord && values.size >= 2) {
                val label = values.first()
                val value = values.drop(1).joinToString(" • ")
                if (label.length <= 90 && value.length <= 180 && !label.equals(value, true)) {
                    result.add(label to value)
                }
            }
        }
        return result.distinctBy { it.first.lowercase() + "|" + it.second.lowercase() }
    }

    private fun createProfileHero(fields: List<Pair<String, String>>): View {
        val name = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "student name", "full name", "name") }
            ?.second
            ?.takeIf(ParsingUtils::isValidStudentName)
            ?: profileCache.studentName.takeIf(ParsingUtils::isValidStudentName)
            ?: "Student"
        val program = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "program", "degree", "course of study") }?.second.orEmpty()
        val reg = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "registration", "roll no", "student id", "student code") }?.second.orEmpty()
        val campus = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "campus") }?.second.orEmpty()
        val session = fields.firstOrNull { ParsingUtils.isProfileLabel(it.first, "session") }?.second.orEmpty()
        val initials = name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "S" }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = gradientBackground(
                Palette.primaryDark(this@MainActivity),
                Palette.violet(this@MainActivity), 26f
            )
            elevation = dp(6).toFloat()
            setPadding(dp(18), dp(20), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(10)) }

            // Avatar with white ring + inner tinted circle
            addView(android.widget.FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                    setMargins(0, 0, dp(15), 0)
                }
                addView(View(this@MainActivity).apply {
                    background = roundedBackground(Color.argb(70, 255, 255, 255), 22f)
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(64), dp(64))
                })
                addView(TextView(this@MainActivity).apply {
                    text = initials
                    gravity = Gravity.CENTER
                    setTextColor(Palette.primaryDark(this@MainActivity))
                    textSize = 20f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Color.WHITE, 19f)
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                        gravity = Gravity.CENTER
                    }
                })
            })

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(this@MainActivity).apply {
                    text = "STUDENT PROFILE"
                    setTextColor(Color.argb(190, 255, 255, 255))
                    textSize = 9f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.12f
                })
                addView(TextView(this@MainActivity).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    textSize = 19f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(3), 0, 0)
                })
                if (program.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = program
                    setTextColor(Color.argb(225, 255, 255, 255))
                    textSize = 11f
                    setPadding(0, dp(4), 0, 0)
                })

                // Badge chips row for reg / session / campus
                val chips = listOf(reg, session, campus).filter(String::isNotBlank)
                if (chips.isNotEmpty()) {
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(9), 0, 0)
                        chips.forEachIndexed { i, chip ->
                            addView(TextView(this@MainActivity).apply {
                                text = chip
                                setTextColor(Color.WHITE)
                                textSize = 9f
                                letterSpacing = 0.03f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                maxLines = 1
                                background = roundedBackground(Color.argb(55, 255, 255, 255), 20f)
                                setPadding(dp(10), dp(5), dp(10), dp(5))
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply { if (i > 0) setMargins(dp(6), 0, 0, 0) }
                            })
                        }
                    })
                }
            })
        }
    }

    private fun renderResults(data: ModuleData) {
        if (data.semesterOptions.size > 1) {
            binding.moduleContent.addView(createSemesterSelector(data.semesterOptions))
        }

        val resultTables = data.tables.filter { table ->
            table.headers.any { h ->
                h.contains("subject", true) || h.contains("course", true) ||
                h.contains("marks", true) || h.contains("obtained", true) ||
                h.contains("grade", true) || h.contains("credit", true)
            }
        }

        if (resultTables.isNotEmpty()) {
            binding.moduleContent.addView(
                createSectionHeading(
                    "Semester results",
                    "Subject wise marks, grades and credits from your ERP account"
                )
            )
            resultTables.take(6).forEach { table ->
                table.rows.forEachIndexed { index, row ->
                    createAcademicResultCard(table.headers, row, index + 1)?.let {
                        binding.moduleContent.addView(it)
                    }
                }
            }
        }

        val otherTables = data.tables.filterNot { resultTables.contains(it) }
        otherTables.take(6).forEachIndexed { index, table ->
            binding.moduleContent.addView(
                createTableSection(
                    table.title.ifBlank { if (index == 0) "Academic details" else "Additional details" },
                    table
                )
            )
        }

        val resultRecords = data.records.mapNotNull { record ->
            if (record.headers.any { it.equals("Semester options", true) }) return@mapNotNull null
            val values = record.values.map(ParsingUtils::cleanDisplayText).filter(String::isNotBlank)
            if (values.size < 2) null else values
        }.distinctBy { it.joinToString("|").lowercase() }

        if (resultTables.isEmpty()) {
            resultRecords.take(60).forEach { values ->
                val title = values.first()
                val details = values.drop(1).joinToString(" • ")
                if (details.isNotBlank()) binding.moduleContent.addView(createResultCard(title, details))
            }
        }

        val pageMessage = data.lines.firstOrNull {
            it.contains("no result", true) ||
            it.contains("not uploaded", true) ||
            it.contains("no record", true) ||
            it.contains("result is not", true) ||
            it.contains("not available", true)
        }

        if (resultTables.isEmpty() && resultRecords.isEmpty() && data.cards.isEmpty()) {
            binding.moduleContent.addView(
                createEmptyState(
                    "Current semester results are not uploaded yet",
                    pageMessage ?: "No published result record was returned by the ERP. Previous semester records will appear here when the ERP provides them."
                )
            )
        }
    }

    private fun createResultCard(title: String, details: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(15), dp(13), dp(15), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Palette.ink900(this@MainActivity))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            if (details.isNotBlank()) {
                addView(TextView(this@MainActivity).apply {
                    text = details
                    setTextColor(Palette.ink500(this@MainActivity))
                    textSize = 11f
                    setPadding(0, dp(5), 0, 0)
                })
            }
        }
    }

    private fun createSemesterSelector(options: List<String>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 16f)
            elevation = dp(1).toFloat()
            setPadding(dp(16), dp(13), dp(16), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
        }

        card.addView(TextView(this).apply {
            text = "Select semester"
            setTextColor(Palette.ink900(this@MainActivity))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val spinner = android.widget.Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { setMargins(0, dp(7), 0, 0) }
            adapter = android.widget.ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
        }

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var first = true

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit

            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (first) {
                    first = false
                    return
                }
                val label = options.getOrNull(position) ?: return
                val script = Scripts.semesterSelectScript(label)
                binding.moduleProgress.visibility = View.VISIBLE
                binding.moduleInfo.text = "Loading $label…"
                binding.webView.evaluateJavascript(script) {
                    handler.postDelayed({
                        if (activeModule == Module.RESULTS) {
                            readModuleData(binding.webView, Module.RESULTS, true)
                        }
                    }, 900L)
                }
            }
        }
        card.addView(spinner)
        return card
    }

    private fun createAcademicResultCard(headers: List<String>, values: List<String>, position: Int): View? {
        if (values.none { it.isNotBlank() }) return null

        fun valueFor(vararg names: String): String {
            val index = headers.indexOfFirst { header ->
                names.any { key -> header.contains(key, true) }
            }
            return if (index >= 0 && index < values.size) ParsingUtils.cleanDisplayText(values[index]) else ""
        }

        val subject = valueFor("subject", "course name", "course title", "course")
            .ifBlank { values.firstOrNull().orEmpty() }
        if (subject.isBlank()) return null

        val code = valueFor("code", "course code", "subject code")
        val marks = valueFor("marks", "obtained", "score", "total marks", "marks obtained")
        val maxMarks = valueFor("maximum", "max marks", "out of")
        val grade = valueFor("grade", "letter grade")
        val credits = valueFor("credit", "credit hours", "cr")
        val gpa = valueFor("gpa", "grade point", "points")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(top)
            top.addView(TextView(this@MainActivity).apply {
                text = position.toString().padStart(2, '0')
                gravity = Gravity.CENTER
                setTextColor(Palette.primary(this@MainActivity))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(this@MainActivity), 10f)
                minWidth = dp(36)
                minHeight = dp(32)
            })
            top.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setPadding(dp(12), 0, dp(8), 0) }
                addView(TextView(this@MainActivity).apply {
                    text = subject
                    setTextColor(Palette.ink900(this@MainActivity))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (code.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = code
                    setTextColor(Palette.ink500(this@MainActivity))
                    textSize = 10f
                    setPadding(0, dp(3), 0, 0)
                })
            })
            if (grade.isNotBlank()) addView(TextView(this@MainActivity).apply {
                text = grade
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.primaryDark(this@MainActivity), 10f)
                setPadding(dp(9), dp(6), dp(9), dp(6))
            })

            val metrics = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(48), dp(11), 0, 0) }
            }
            fun addMetric(label: String, value: String) {
                if (value.isBlank()) return
                metrics.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(dp(4), 0, dp(4), 0) }
                    addView(TextView(this@MainActivity).apply {
                        text = label.uppercase(java.util.Locale.US)
                        setTextColor(Palette.ink500(this@MainActivity))
                        textSize = 9f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = value
                        setTextColor(Palette.ink900(this@MainActivity))
                        textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, dp(3), 0, 0)
                    })
                })
            }
            addMetric("Marks", marks)
            addMetric("Max", maxMarks)
            addMetric("Credits", credits)
            addMetric("GPA", gpa)
            if (metrics.childCount > 0) addView(metrics)
        }
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
            background = roundedBackground(Palette.surface(this@MainActivity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(6), dp(12), dp(7)) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = "Fee records"
                    setTextColor(Palette.ink900(this@MainActivity))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "Your latest invoices"
                    setTextColor(Palette.ink500(this@MainActivity))
                    textSize = 11f
                    setPadding(0, dp(3), 0, 0)
                })
            })
            addView(TextView(this@MainActivity).apply {
                text = count.toString()
                gravity = Gravity.CENTER
                setTextColor(Palette.primary(this@MainActivity))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedBackground(Palette.infoBg(this@MainActivity), 12f)
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
            background = roundedBackground(Palette.surface(this@MainActivity), 18f)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = no
                    setTextColor(Palette.ink900(this@MainActivity))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (status.isNotBlank()) addView(TextView(this@MainActivity).apply {
                    text = status
                    setTextColor(Palette.success(this@MainActivity))
                    textSize = 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = roundedBackground(Palette.successBg(this@MainActivity), 10f)
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
                setTextColor(Palette.ink500(this@MainActivity))
                textSize = 9f
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                setTextColor(Palette.ink900(this@MainActivity))
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
            background = roundedBackground(Palette.surface(this@MainActivity), 18f)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(12), dp(12), dp(8)) }
        }

        card.addView(TextView(this).apply {
            text = title
            setTextColor(Palette.primaryDark(this@MainActivity))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Palette.ink500(this@MainActivity))
            textSize = 12f
            setPadding(0, dp(5), 0, 0)
        })

        binding.moduleContent.addView(card)
    }


    private fun createMetricCard(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 16f)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }

            addView(TextView(this@MainActivity).apply {
                text = ParsingUtils.cleanDisplayText(label)
                setTextColor(Palette.ink500(this@MainActivity))
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = ParsingUtils.cleanDisplayText(value)
                setTextColor(Palette.primaryDark(this@MainActivity))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun createTableSection(title: String, table: TableData): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(7), dp(12), dp(7)) }
        }

        outer.addView(TextView(this).apply {
            text = ParsingUtils.cleanDisplayText(title)
            setTextColor(Palette.ink900(this@MainActivity))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(9))
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

        if (table.headers.isNotEmpty()) tableLayout.addView(createTableRow(table.headers, true))
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
            background = if (header) roundedBackground(Palette.infoBg(this@MainActivity), 10f) else roundedBackground(Palette.surface(this@MainActivity), 0f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, if (header) dp(2) else dp(1), 0, 0) }
        }

        values.forEach { value ->
            row.addView(TextView(this).apply {
                text = ParsingUtils.cleanDisplayText(value).ifBlank { "—" }
                setTextColor(if (header) Palette.primaryDark(this@MainActivity) else Palette.ink900(this@MainActivity))
                textSize = if (header) 10f else 11f
                if (header) setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                minWidth = dp(if (header) 96 else 88)
                maxWidth = dp(220)
            })
        }
        return row
    }


    private fun createInformationSection(lines: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Palette.surface(this@MainActivity), 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }

            addView(TextView(this@MainActivity).apply {
                text = "Additional information"
                setTextColor(Palette.ink900(this@MainActivity))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(16), dp(15), dp(16), dp(8))
            })
            lines.forEach { line ->
                addView(TextView(this@MainActivity).apply {
                    text = "• $line"
                    setTextColor(Palette.ink700(this@MainActivity))
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
            background = roundedBackground(Palette.surface(this@MainActivity), 18f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(12), dp(12), dp(12), dp(12)) }
            setPadding(dp(24), dp(32), dp(24), dp(32))
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Palette.primaryDark(this@MainActivity))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = message
                setTextColor(Palette.ink500(this@MainActivity))
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

    /** Gradient pill background used across the redesigned screens. */
    private fun gradientBackground(startColor: Int, endColor: Int, radiusDp: Float, angleDeg: Float = 135f): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            orientation = when (angleDeg.toInt()) {
                90 -> android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                270 -> android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT
                else -> android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            }
            colors = intArrayOf(startColor, endColor)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
    }

    /** Very low-alpha tint of [color], for glass fills and soft washes on hero cards. */
    private fun tintAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** Custom circular progress ring with a centered percentage label. */
    private fun createProgressRing(percent: Double, sizeDp: Int, strokeWidthDp: Int, ringColor: Int): View {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt()
        val strokePx = (strokeWidthDp * density).roundToInt()
        val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            color = Palette.ringTrack(this@MainActivity)
        }
        val progressPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokePx.toFloat()
            strokeCap = android.graphics.Paint.Cap.ROUND
            color = ringColor
        }
        val tv = TextView(this)
        tv.text = String.format(java.util.Locale.US, "%.0f%%", percent)
        tv.setTextColor(Palette.ink900(this@MainActivity))
        tv.textSize = 14f
        tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER

        return object : android.widget.FrameLayout(this) {
            init {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                setWillNotDraw(false)
                addView(tv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val inset = strokePx / 2f + 1f
                val rect = android.graphics.RectF(inset, inset, width - inset, height - inset)
                canvas.drawArc(rect, 0f, 360f, false, trackPaint)
                val sweep = (percent.coerceIn(0.0, 100.0) / 100.0 * 360.0).toFloat()
                canvas.drawArc(rect, -90f, sweep, false, progressPaint)
            }
        }
    }

    /** Subtle press animation applied to tappable cards. */
    private fun attachPressFeedback(view: View) {
        view.setOnClickListener { /* ensures clickable ripple on API 21+ */ }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                else -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()


    
    
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
                    val label = ParsingUtils.cleanDisplayText(o.optString("label", ""))
                    val value = ParsingUtils.cleanDisplayText(o.optString("value", ""))
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
                        for (j in 0 until h.length()) headers.add(ParsingUtils.cleanDisplayText(h.optString(j, "")))
                    }
                    val rows = mutableListOf<List<String>>()
                    t.optJSONArray("rows")?.let { rs ->
                        for (j in 0 until rs.length()) {
                            val ro = rs.optJSONArray(j) ?: continue
                            val row = mutableListOf<String>()
                            for (k in 0 until ro.length()) row.add(ParsingUtils.cleanDisplayText(ro.optString(k, "")))
                            if (row.any { it.isNotBlank() }) {
                                rows.add(row)
                                records.add(ModuleRecord(headers, row))
                            }
                        }
                    }
                    val title = ParsingUtils.cleanDisplayText(t.optString("title", ""))
                    if (headers.isNotEmpty() || rows.isNotEmpty()) tables.add(TableData(title, headers, rows))
                }
            }

            json.optJSONArray("records")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val h = mutableListOf<String>()
                    val v = mutableListOf<String>()
                    o.optJSONArray("headers")?.let { q ->
                        for (j in 0 until q.length()) h.add(ParsingUtils.cleanDisplayText(q.optString(j, "")))
                    }
                    o.optJSONArray("values")?.let { q ->
                        for (j in 0 until q.length()) v.add(ParsingUtils.cleanDisplayText(q.optString(j, "")))
                    }
                    if (v.any { it.isNotBlank() }) records.add(ModuleRecord(h, v))
                }
            }

            val lines = mutableListOf<String>()
            json.optJSONArray("lines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val line = ParsingUtils.cleanDisplayText(arr.optString(i, ""))
                    if (ParsingUtils.isUsefulDisplayText(line)) lines.add(line)
                }
            }

            val semesterOptions = records
                .filter { it.headers.any { h -> h.equals("Semester options", true) } }
                .flatMap { it.values }
                .map(ParsingUtils::cleanDisplayText)
                .filter { it.isNotBlank() }
                .distinct()
                .take(12)

            val overall = json.optDouble("overallAttendance", Double.NaN)
                .takeUnless { it.isNaN() }
                ?.takeIf { it in 0.0..100.0 }

            ModuleData(
                cards.distinct(),
                tables,
                lines.distinct(),
                records.distinctBy { it.headers to it.values },
                overall,
                semesterOptions
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun hasUsableModuleData(module: Module, data: ModuleData): Boolean {
        return when (module) {
            Module.ATTENDANCE -> data.overallAttendance != null ||
                data.records.any { it.values.size >= 2 } ||
                data.tables.any { it.rows.isNotEmpty() } ||
                data.lines.any { it.contains("%") }
            Module.TIMETABLE -> data.records.any(::looksLikeScheduleRecord) ||
                data.records.any { record ->
                    record.headers.any { h -> h.contains("time", true) || h.contains("day", true) || h.contains("course", true) } &&
                        record.values.any { v -> Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(v) }
                } ||
                data.lines.any { Regex("""\b\d{1,2}:\d{2}\b""").containsMatchIn(it) }
            Module.FEE -> data.tables.any { it.rows.isNotEmpty() } ||
                data.cards.isNotEmpty() ||
                data.records.any { it.values.size >= 2 }
            Module.PROFILE -> data.cards.isNotEmpty() ||
                data.records.any { it.headers.any { h -> h.equals("Field", true) } } ||
                data.tables.any { it.rows.isNotEmpty() }
            Module.RESULTS -> data.semesterOptions.isNotEmpty() ||
                data.tables.any { it.rows.isNotEmpty() } ||
                data.records.any { it.values.size >= 2 } ||
                data.lines.any { it.contains("result", true) || it.contains("grade", true) }
        }
    }

    private fun looksLikeScheduleRecord(record: ModuleRecord): Boolean {
        val joined = record.values.joinToString(" ")
        return Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""").containsMatchIn(joined) ||
            Regex("""\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(joined)
    }

    private fun readModuleData(view: WebView, module: Module, displayWhenReady: Boolean) {
        if (!loggedIn) return
        val requestId = moduleReadRequestId

        fun readNow() {
            if (!loggedIn || requestId != moduleReadRequestId) return
            moduleReadAttempts++
            view.evaluateJavascript(Scripts.moduleData) { result ->
                if (!loggedIn || requestId != moduleReadRequestId) return@evaluateJavascript
                val payload = ParsingUtils.decodeJavascriptString(result)
                val data = parseModuleData(payload)

                val usable = data != null && hasUsableModuleData(module, data)
                if (usable && data != null) {
                    cachedModuleData[module] = payload
                    if (displayWhenReady && activeModule == module) renderModuleData(module, data)
                } else if (displayWhenReady && activeModule == module) {
                    if (moduleReadAttempts < MAX_MODULE_READ_ATTEMPTS) {
                        binding.moduleInfo.text = "Waiting for the ERP data…"
                        handler.postDelayed({ readModuleData(view, module, true) }, MODULE_RETRY_DELAY_MS)
                        return@evaluateJavascript
                    }
                    binding.moduleProgress.visibility = View.GONE
                    binding.moduleInfo.text = "No readable data returned"
                    binding.moduleContent.removeAllViews()
                    binding.moduleContent.addView(
                        createEmptyState(
                            "No readable data returned",
                            "The ERP page loaded, but its data is not ready yet. Tap Refresh to try again."
                        )
                    )
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

        val sameDay = rows.filter { it.day.equals(currentDay, true) }
        val next = sameDay.mapNotNull { row ->
            ParsingUtils.startMinutesOf(row.time)?.let { start -> if (start >= currentMinutes) start to row else null }
        }.minByOrNull { it.first }?.second
            ?: rows.mapNotNull { row -> ParsingUtils.startMinutesOf(row.time)?.let { it to row } }
                .minByOrNull { it.first }?.second

        if (next == null) {
            label.text = "Schedule synced"
        } else {
            label.text = "Next: " + next.time + " • " + next.course
        }
    }

    private fun startSessionHeartbeat() {
        if (sessionHeartbeatRunning) return
        sessionHeartbeatRunning = true
        handler.removeCallbacks(sessionHeartbeatRunnable)
        handler.postDelayed(sessionHeartbeatRunnable, SESSION_HEARTBEAT_INTERVAL_MS)
    }

    private fun stopSessionHeartbeat() {
        sessionHeartbeatRunning = false
        handler.removeCallbacks(sessionHeartbeatRunnable)
    }

    private fun pingSession() {
        if (!loggedIn) return
        binding.webView.evaluateJavascript(Scripts.sessionHeartbeat) { result ->
            if (!loggedIn) return@evaluateJavascript
            val state = ParsingUtils.decodeJavascriptString(result)
            if (state == "EXPIRED") {
                handleSessionExpired()
            } else if (state == "OFFLINE") {
                // Keep the local app session intact. A transient network failure
                // must not force the student back to the login screen.
            }
        }
    }

    private fun handleSessionExpired() {
        if (!loggedIn) return
        loggedIn = false
        profileFetchInProgress = false
        moduleFetchInProgress = false
        preloadingModule = null
        preloadQueue.clear()
        sessionStore.sessionActive = false
        stopSessionHeartbeat()
        showLogin()
        Toast.makeText(this, "Your university session has expired. Please sign in again.", Toast.LENGTH_LONG).show()
    }

    private fun openSideMenu() {
        if (!loggedIn || binding.dashboardScroll.visibility != View.VISIBLE) return
        try {
            binding.sideMenuOverlay.bringToFront()
            binding.sideMenuOverlay.visibility = View.VISIBLE
            binding.sideMenu.bringToFront()
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Side menu failed to open", t)
            try { binding.sideMenuOverlay.visibility = View.GONE } catch (ignored: Throwable) { }
        }
    }

    private fun closeSideMenu() {
        if (::binding.isInitialized) binding.sideMenuOverlay.visibility = View.GONE
    }

    private fun showDashboard() {
        try {
            showDashboardInternal()
        } catch (t: Throwable) {
            // Never leave the user on a dead screen: fall back to login.
            android.util.Log.e("MainActivity", "Dashboard render failed", t)
            try { showLogin() } catch (ignored: Throwable) { }
        }
    }

    private fun showDashboardInternal() {
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
        binding.sideMenuOverlay.visibility = View.GONE

        binding.studentName.text =
            if (ParsingUtils.isValidStudentName(profileCache.studentName)) profileCache.studentName else "Student"
        binding.studentCgpa.text = profileCache.cgpa.ifBlank { "—" }
        binding.studentSgpa.text = profileCache.sgpa.ifBlank { "—" }
        updateNextClassPreview()
    }

    private fun showLogin() {
        closeSideMenu()
        stopSessionHeartbeat()
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
        binding.sideMenuOverlay.visibility = View.GONE
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        binding.loginStatus.text = ""
    }

    private fun logout() {
        sessionStore.clear()
        profileCache.clear()
        getSharedPreferences("superior_student_preferences", MODE_PRIVATE).edit().clear().apply()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        binding.webView.clearHistory()
        binding.webView.clearCache(true)
        binding.webView.clearFormData()
        cachedModuleData.clear()
        showLogin()
    }

    private fun isAuthenticatedUrl(url: String): Boolean = ErpConfig.isAuthenticatedUrl(url)

    private fun failLogin(message: String) {
        loginInProgress = false
        loginSubmitted = false
        binding.loginButton.isEnabled = true
        binding.loginStatus.setTextColor(Palette.danger(this@MainActivity))
        binding.loginStatus.text = message
    }

    /** Full-screen readable error for fatal startup conditions (e.g. no WebView provider). */
    private fun showErrorAndFinish(message: String) {
        val view = android.widget.TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF071426.toInt())
        }
        setContentView(view)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    
    
    override fun onDestroy() {
        stopSessionHeartbeat()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
                                private const val LOGIN_INJECTION_DELAY_MS = 500L
        private const val MAX_LOGIN_INJECTION_ATTEMPTS = 20
        private const val PROFILE_READ_DELAY_MS = 1200L
        private const val PROFILE_VISUAL_DELAY_MS = 500L
        private const val PROFILE_RETRY_DELAY_MS = 1000L
        private const val MAX_PROFILE_READ_ATTEMPTS = 8
        private const val MODULE_READ_DELAY_MS = 1400L
        private const val MODULE_VISUAL_DELAY_MS = 900L
        private const val MODULE_RETRY_DELAY_MS = 1200L
        private const val MAX_MODULE_READ_ATTEMPTS = 12
        private const val SESSION_HEARTBEAT_INTERVAL_MS = 4 * 60 * 1000L

    }
}
