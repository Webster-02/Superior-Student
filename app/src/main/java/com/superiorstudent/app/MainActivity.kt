package com.superiorstudent.app

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

import com.superiorstudent.app.data.model.AttendanceDetailData
import com.superiorstudent.app.data.model.AttendanceDetailLink
import com.superiorstudent.app.data.model.AttendanceSession
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.data.model.TimetableRow
import com.superiorstudent.app.data.parser.TimetableParser
import com.superiorstudent.app.data.parser.ErpPageParser
import com.superiorstudent.app.feature.attendance.domain.AttendanceCalculator
import com.superiorstudent.app.feature.attendance.ui.AttendanceRenderer
import com.superiorstudent.app.feature.timetable.ui.TimetableRenderer
import com.superiorstudent.app.feature.profile.ui.ProfileRenderer
import com.superiorstudent.app.feature.timetable.domain.NextClassSelector
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private var webViewReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val sessionStore by lazy { SessionStore(this) }
    private val profileCache by lazy { ProfileCache(this) }
    private val attendanceRenderer by lazy { AttendanceRenderer(this, ::openAttendanceDetail, ::showAttendanceSummary) }
    private val timetableRenderer by lazy { TimetableRenderer(this) }
    private val profileRenderer by lazy {
        ProfileRenderer(this, ::createSectionHeading, ::createTableSection, ::createEmptyState)
    }

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
    private var activeAttendanceDetailUrl: String? = null
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
        super.onCreate(savedInstanceState)
        try {
            safeOnCreate(savedInstanceState)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Startup failed before login UI was ready", t)
            showErrorAndFinish(
                "Superior Student could not start.\n\n" +
                    "Error: " + t.javaClass.simpleName + "\n" +
                    (t.message ?: "No additional error details.") +
                    "\n\nPlease send this screen to support."
            )
        }
    }

    private fun safeOnCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { sessionStore.migrateFromLegacy() } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "Could not migrate saved session; showing login screen", t)
        }

        binding.loginScroll.visibility = View.VISIBLE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.sideMenuOverlay.visibility = View.GONE
        configureLoginLayoutAndKeyboard()

        binding.loginButton.setOnClickListener {
            username = binding.usernameInput.text.toString().trim()
            password = binding.passwordInput.text.toString()
            if (username.isBlank() || password.isBlank()) {
                binding.loginStatus.setTextColor(Palette.danger(this@MainActivity))
                binding.loginStatus.text = "Please enter your university username and password."
                return@setOnClickListener
            }
            if (!ensureWebView()) return@setOnClickListener
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(binding.passwordInput.windowToken, 0)
            binding.usernameInput.clearFocus()
            binding.passwordInput.clearFocus()
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

        val hasSavedSession = try { sessionStore.sessionActive } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "Ignoring unreadable saved session", t)
            false
        }
        if (savedInstanceState != null) {
            if (ensureWebView()) webView.restoreState(savedInstanceState)
        } else if (hasSavedSession) {
            username = try { sessionStore.username } catch (_: Throwable) { "" }
            if (ensureWebView()) {
                restoringSession = true
                binding.loginScroll.visibility = View.GONE
                binding.dashboardScroll.visibility = View.VISIBLE
                webView.visibility = View.GONE
                showDashboard()
                webView.loadUrl(ErpConfig.DASHBOARD_URL)
            }
        }
    }

    /**
     * Keeps the login card below the baked-in campus tagline on launch.
     * The form lives in a ScrollView so the focused field can be brought above
     * the soft keyboard on short screens and when the keyboard is open.
     */
    private fun configureLoginLayoutAndKeyboard() {
        val screenHeightDp = resources.configuration.screenHeightDp
        val topSpacerDp = (screenHeightDp * 0.39f).roundToInt().coerceIn(270, 365)
        binding.loginTopSpacer.layoutParams = binding.loginTopSpacer.layoutParams.apply {
            height = dp(topSpacerDp)
        }

        val loginViewport = binding.loginContentScroll
        loginViewport.isSmoothScrollingEnabled = true
        loginViewport.post { loginViewport.scrollTo(0, 0) }

        val keepFocusedFieldVisible = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Wait for adjustResize to report the keyboard-reduced viewport height.
                loginViewport.postDelayed({
                    if (!view.isFocused || binding.loginScroll.visibility != View.VISIBLE) return@postDelayed
                    val rect = android.graphics.Rect()
                    view.getDrawingRect(rect)
                    loginViewport.offsetDescendantRectToMyCoords(view, rect)

                    val visibleTop = loginViewport.scrollY + dp(14)
                    val visibleBottom = loginViewport.scrollY + loginViewport.height - dp(24)
                    when {
                        rect.bottom > visibleBottom ->
                            loginViewport.smoothScrollBy(0, rect.bottom - visibleBottom)
                        rect.top < visibleTop ->
                            loginViewport.smoothScrollTo(0, (rect.top - dp(14)).coerceAtLeast(0))
                    }
                }, 260L)
            }
        }

        // Force a strong text/hint contrast independently of device-specific theme defaults.
        binding.usernameInput.apply {
            setTextColor(Color.rgb(23, 32, 51))
            setHintTextColor(Color.rgb(102, 112, 133))
            alpha = 1f
            isEnabled = true
        }
        binding.passwordInput.apply {
            setTextColor(Color.rgb(23, 32, 51))
            setHintTextColor(Color.rgb(102, 112, 133))
            alpha = 1f
            isEnabled = true
        }

        var passwordVisible = false
        binding.passwordVisibilityToggle.setOnClickListener {
            val edit = binding.passwordInput
            val cursor = edit.selectionStart.coerceAtLeast(0)
            passwordVisible = !passwordVisible
            edit.transformationMethod = if (passwordVisible) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            binding.passwordVisibilityToggle.text = if (passwordVisible) "Hide" else "Show"
            binding.passwordVisibilityToggle.contentDescription =
                if (passwordVisible) "Hide password" else "Show password"
            edit.setSelection(cursor.coerceAtMost(edit.text?.length ?: 0))
            edit.requestFocus()
        }

        binding.usernameInput.onFocusChangeListener = keepFocusedFieldVisible
        binding.passwordInput.onFocusChangeListener = keepFocusedFieldVisible
        binding.usernameInput.imeOptions = EditorInfo.IME_ACTION_NEXT
        binding.passwordInput.imeOptions = EditorInfo.IME_ACTION_DONE

        binding.usernameInput.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_NEXT || enterPressed) {
                binding.passwordInput.requestFocus()
                true
            } else {
                false
            }
        }

        binding.passwordInput.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                binding.loginButton.performClick()
                true
            } else {
                false
            }
        }
    }

    /**
     * Create WebView only on demand so a bad provider cannot prevent the
     * native login screen from appearing at app launch.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): Boolean {
        return try {
            if (!::webView.isInitialized) {
                webView = WebView(this).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(1), dp(1))
                    visibility = View.INVISIBLE
                    alpha = 0f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                binding.webViewContainer.addView(webView)
            }
            if (!webViewReady) {
                Scripts.loadAll(this)
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
                webViewReady = true
            }
            true
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Could not initialize university portal WebView", t)
            binding.loginStatus.setTextColor(Palette.danger(this))
            binding.loginStatus.text = "University sign-in is unavailable. Update Android System WebView and Chrome, then try again."
            false
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
        if (!loggedIn) return

        val module = activeModule
        webView.stopLoading()
        webView.visibility = View.INVISIBLE

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
            webView.loadUrl(ErpConfig.moduleUrl(module.path))
        } else {
            restoringSession = true
            webView.loadUrl(ErpConfig.DASHBOARD_URL)
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
        webView.visibility = View.INVISIBLE
        webView.loadUrl(ErpConfig.DASHBOARD_URL)
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
            webView.visibility = View.GONE
            updateNextClassPreview()
            return
        }
        preloadingModule = preloadQueue.removeAt(0)
        webView.visibility = View.INVISIBLE
        webView.loadUrl(ErpConfig.moduleUrl(preloadingModule!!.path))
    }

    private fun loadModule(module: Module) {
        if (!loggedIn) return
        if (module == Module.ATTENDANCE) activeAttendanceDetailUrl = null
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
        webView.visibility = View.INVISIBLE
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
        webView.loadUrl(ErpConfig.moduleUrl(module.path))
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        val detailUrl = activeAttendanceDetailUrl
        if (module == Module.ATTENDANCE && detailUrl != null) {
            openAttendanceDetail(detailUrl)
            return
        }
        if (module == Module.ATTENDANCE) activeAttendanceDetailUrl = null
        cachedModuleData.remove(module)
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleContent.removeAllViews()
        binding.moduleInfo.text = "Syncing the latest information…"
        webView.visibility = View.INVISIBLE
        webView.loadUrl(ErpConfig.moduleUrl(module.path))
        moduleFetchInProgress = true
    }


    private fun renderModuleData(module: Module, data: ModuleData) {
        binding.moduleContent.removeAllViews()
        if (module != Module.ATTENDANCE || data.attendanceDetail == null) addModuleIntro(module)
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

    private fun renderAttendance(data: ModuleData) {
        attendanceRenderer.render(binding.moduleContent, data)
    }

    private fun openAttendanceDetail(url: String) {
        if (!loggedIn) return
        if (!ErpConfig.belongsToErp(url) || !url.contains("/student/attendancedetail", ignoreCase = true)) {
            Toast.makeText(this, "This subject's ERP detail link is unavailable.", Toast.LENGTH_SHORT).show()
            return
        }
        activeModule = Module.ATTENDANCE
        activeAttendanceDetailUrl = url
        preloadingModule = null
        preloadQueue.clear()
        moduleReadAttempts = 0
        moduleReadRequestId++
        moduleFetchInProgress = true
        binding.loginScroll.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.VISIBLE
        binding.moduleTitle.text = "Attendance Planner"
        binding.moduleSubtitle.text = "Subject attendance and 75% target"
        binding.moduleContent.removeAllViews()
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleInfo.text = "Loading the subject's attendance detail from ERP…"
        webView.visibility = View.INVISIBLE
        webView.loadUrl(url)
    }

    private fun showAttendanceSummary() {
        activeAttendanceDetailUrl = null
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++
        val summary = cachedModuleData[Module.ATTENDANCE]?.let(::parseModuleData)
        if (summary != null && summary.attendanceDetail == null) {
            activeModule = Module.ATTENDANCE
            binding.moduleScreen.visibility = View.VISIBLE
            binding.loginScroll.visibility = View.GONE
            binding.dashboardScroll.visibility = View.GONE
            webView.visibility = View.INVISIBLE
            renderModuleData(Module.ATTENDANCE, summary)
        } else {
            loadModule(Module.ATTENDANCE)
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

    private fun renderTimetable(data: ModuleData) {
        timetableRenderer.render(binding.moduleContent, data)
    }

    private fun renderProfile(data: ModuleData) {
        profileRenderer.render(binding.moduleContent, data, profileCache.studentName)
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
                webView.evaluateJavascript(script) {
                    handler.postDelayed({
                        if (activeModule == Module.RESULTS) {
                            readModuleData(webView, Module.RESULTS, true)
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
        // Do not assign a click listener here: callers already wire navigation
        // actions, and replacing those listeners makes dashboard buttons inert.
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


    private fun parseModuleData(payload: String): ModuleData? = ErpPageParser.parse(payload)

    private fun hasUsableModuleData(module: Module, data: ModuleData): Boolean {
        return when (module) {
            Module.ATTENDANCE -> data.attendanceDetail != null ||
                data.overallAttendance != null ||
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
                    if (module != Module.ATTENDANCE || data.attendanceDetail == null) {
                        cachedModuleData[module] = payload
                    }
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
        val rows = data?.records?.mapNotNull(TimetableParser::toRow)
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
        val next = NextClassSelector.selectNext(rows, currentDay, currentMinutes)

        label.text = next?.let { "Next: " + it.time + " • " + it.course } ?: "Schedule synced"
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
        webView.evaluateJavascript(Scripts.sessionHeartbeat) { result ->
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
        activeAttendanceDetailUrl = null
        activeModule = null
        webView.stopLoading()
        webView.visibility = View.GONE
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
        activeAttendanceDetailUrl = null
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
        webView.stopLoading()
        webView.visibility = View.GONE
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
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
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
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!::binding.isInitialized) {
            super.onBackPressed()
            return
        }

        when {
            binding.sideMenuOverlay.visibility == View.VISIBLE -> closeSideMenu()
            binding.moduleScreen.visibility == View.VISIBLE -> {
                // Returning from a subject detail should restore the attendance summary.
                // Other native feature screens return to the dashboard.
                if (activeAttendanceDetailUrl != null) {
                    showAttendanceSummary()
                } else {
                    showDashboard()
                }
            }
            else -> super.onBackPressed()
        }
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
