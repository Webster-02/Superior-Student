package com.superiorstudent.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.widget.FrameLayout
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.superiorstudent.app.core.config.ErpConfig
import com.superiorstudent.app.core.parsing.ParsingUtils
import com.superiorstudent.app.core.session.SessionStore
import com.superiorstudent.app.core.webview.Scripts
import com.superiorstudent.app.feature.profile.data.ProfileCache
import com.superiorstudent.app.databinding.ActivityMainBinding
import com.superiorstudent.app.ui.Palette

import com.superiorstudent.app.data.model.AttendanceDetailData
import com.superiorstudent.app.data.model.AttendanceDetailLink
import com.superiorstudent.app.data.model.AttendanceSession
import com.superiorstudent.app.data.model.ModuleData
import com.superiorstudent.app.data.model.StudentModule
import com.superiorstudent.app.data.model.ModuleRecord
import com.superiorstudent.app.data.model.TableData
import com.superiorstudent.app.data.model.TimetableRow
import com.superiorstudent.app.data.parser.TimetableParser
import com.superiorstudent.app.data.repository.ErpRepository
import com.superiorstudent.app.feature.attendance.domain.AttendanceCalculator
import com.superiorstudent.app.feature.attendance.presentation.AttendanceViewModel
import androidx.lifecycle.ViewModelProvider
import com.superiorstudent.app.feature.attendance.ui.AttendanceRenderer
import com.superiorstudent.app.feature.timetable.ui.TimetableRenderer
import com.superiorstudent.app.feature.profile.ui.ProfileRenderer
import com.superiorstudent.app.feature.fees.ui.FeeRenderer
import com.superiorstudent.app.feature.results.ui.ResultsRenderer
import com.superiorstudent.app.feature.timetable.domain.NextClassSelector
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private var statusBarScrim: View? = null
    private var navigationBarScrim: View? = null
    private var webViewReady = false
    private var activityResumed = false
    private var dashboardEntranceAnimated = false
    private val handler = Handler(Looper.getMainLooper())
    private val sessionStore by lazy { SessionStore(this) }
    private val profileCache by lazy { ProfileCache(this) }
    private val erpRepository by lazy { ErpRepository() }
    private val attendanceViewModel by lazy { ViewModelProvider(this)[AttendanceViewModel::class.java] }
    private val attendanceRenderer by lazy {
        AttendanceRenderer(this, attendanceViewModel, ::openAttendanceDetail, ::showAttendanceSummary)
    }
    private val timetableRenderer by lazy { TimetableRenderer(this) }
    private val profileRenderer by lazy {
        ProfileRenderer(this, ::createSectionHeading, ::createTableSection, ::createEmptyState)
    }
    private val feeRenderer by lazy {
        FeeRenderer(this, ::createSectionHeading, ::createMetricCard, ::createEmptyState, ::downloadInvoicePdf)
    }
    private val resultsRenderer by lazy {
        ResultsRenderer(this, ::createSectionHeading, ::createTableSection, ::createEmptyState, ::selectResultsSemester)
    }

    private var loginInProgress = false
    private var loginSubmitted = false
    private var restoringSession = false
    private var loggedIn = false
    private var profileFetchInProgress = false
    private var profileReadAttempts = 0
    private var profileFetchGeneration = 0L
    private var moduleReadAttempts = 0
    private var activeModule: StudentModule? = null
    private var username = ""
    private var password = ""
    private var loginAttempt = 0
    private var loginProbeInProgress = false
    private var loginProbeAttempts = 0
    private var preloadingModule: StudentModule? = null
    private val preloadQueue = mutableListOf<StudentModule>()
    private var moduleFetchInProgress = false
    private var moduleReadRequestId = 0
    private var activeAttendanceDetailUrl: String? = null
    private var pendingAttendanceResolution = false
    private var pendingAttendanceCourse = ""
    private var pendingAttendanceCode = ""
    private var lastPausedAt = 0L
    /** Brand display font (Roboto Medium) used across programmatic views. */
    private val typeface: android.graphics.Typeface
        get() = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    private var sessionHeartbeatRunning = false
    private val sessionResumeThresholdMs = 5 * 60 * 1000L
    private val sessionHeartbeatRunnable = object : Runnable {
        override fun run() {
            if (!sessionHeartbeatRunning || !loggedIn || !activityResumed) return
            pingSession()
            handler.postDelayed(this, SESSION_HEARTBEAT_INTERVAL_MS)
        }
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
        configureSystemBarInsets()
        try { sessionStore.migrateFromLegacy() } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "Could not migrate saved session; showing login screen", t)
        }

        binding.loginScroll.visibility = View.VISIBLE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.sideMenuOverlay.visibility = View.GONE
        applySystemBarStyle(
            statusColor = Color.rgb(7, 20, 38),
            navigationColor = Color.rgb(7, 20, 38),
            lightStatusIcons = true,
            lightNavigationIcons = true
        )
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
            loginProbeInProgress = false
            loginProbeAttempts = 0
            profileReadAttempts = 0
            erpRepository.clearCache()
            binding.loginButton.isEnabled = false
            binding.loginStatus.setTextColor(Palette.ink500(this@MainActivity))
            binding.loginStatus.text = "Signing in securely…"
            webView.visibility = View.GONE
            webView.loadUrl(ErpConfig.LOGIN_URL)
        }

        binding.attendanceButton.setOnClickListener { loadModule(StudentModule.ATTENDANCE) }
        binding.timetableButton.setOnClickListener { loadModule(StudentModule.TIMETABLE) }
        binding.feeButton.setOnClickListener { loadModule(StudentModule.FEE) }
        binding.refreshButton.setOnClickListener { refreshActiveModule() }
        binding.homeButton.setOnClickListener { showDashboard() }
        binding.menuButton.setOnClickListener { openSideMenu() }
        binding.closeMenuButton.setOnClickListener { closeSideMenu() }
        binding.menuScrim.setOnClickListener { closeSideMenu() }
        binding.menuProfileButton.setOnClickListener { closeSideMenu(); loadModule(StudentModule.PROFILE) }
        binding.menuResultsButton.setOnClickListener { closeSideMenu(); loadModule(StudentModule.RESULTS) }
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
     * Edge-to-edge is explicitly handled so Android 15 and older devices use
     * the same safe layout. App content begins below the status bar and above
     * the navigation gesture area; dedicated scrims keep both system areas
     * visually separate from page titles and controls.
     */
    private fun configureSystemBarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = binding.root as FrameLayout
        root.clipToPadding = false
        val statusScrim = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
        }
        val navigationScrim = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
        }
        root.addView(
            statusScrim,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP
            )
        )
        root.addView(
            navigationScrim,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM
            )
        )
        statusBarScrim = statusScrim
        navigationBarScrim = navigationScrim

        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(baseLeft, bars.top.coerceAtLeast(baseTop), baseRight, bars.bottom.coerceAtLeast(baseBottom))

            (statusBarScrim?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = bars.top
                params.topMargin = -bars.top
                statusBarScrim?.layoutParams = params
            }
            (navigationBarScrim?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = bars.bottom
                params.bottomMargin = -bars.bottom
                navigationBarScrim?.layoutParams = params
            }
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Keep status and navigation bars readable and coordinated with whichever
     * native screen is active, while preserving the app's own safe-area padding.
     */
    private fun applySystemBarStyle(
        statusColor: Int,
        navigationColor: Int,
        lightStatusIcons: Boolean,
        lightNavigationIcons: Boolean
    ) {
        statusBarScrim?.setBackgroundColor(statusColor)
        navigationBarScrim?.setBackgroundColor(navigationColor)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !lightStatusIcons
        controller.isAppearanceLightNavigationBars = !lightNavigationIcons
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
                        if (loginInProgress) {
                            failLogin("Secure connection to the university sign-in failed. Check your network and try again.")
                        } else if (restoringSession) {
                            restoringSession = false
                            sessionStore.sessionActive = false
                            showLogin()
                            Toast.makeText(
                                this@MainActivity,
                                "Secure connection failed. Please sign in again.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (activeModule != null) {
                            handleModuleLoadFailure("The secure connection to the university portal failed. Check your internet and try again.")
                        }
                    }
        
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        super.onReceivedError(view, request, error)
                        if (!request.isForMainFrame) return

                        when {
                            loginInProgress -> failLogin(
                                "Could not load the university sign-in page. Check your internet connection and try again."
                            )
                            restoringSession -> {
                                restoringSession = false
                                sessionStore.sessionActive = false
                                showLogin()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Could not restore your university session. Please sign in again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            activeModule != null -> handleModuleLoadFailure(
                                "Unable to reach the ERP. Check your internet connection and tap Refresh to try again."
                            )
                        }
                    }
        
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        if (loggedIn && activeModule == StudentModule.ATTENDANCE &&
                            ErpConfig.belongsToErp(url) &&
                            url.contains("/student/attendancedetail", ignoreCase = true)) {
                            activeAttendanceDetailUrl = url
                        }
        
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
                                        if (!loginInProgress) return@evaluateJavascript
                                        if (result == "true") {
                                            failLogin("Login failed. Check your university username and password.")
                                        } else {
                                            probeLoginPage(view)
                                        }
                                    }
                                }
                            } else {
                                probeLoginPage(view)
                            }
                            return
                        }
        
                        if (profileFetchInProgress && isAuthenticatedUrl(url)) {
                            waitForProfileDom(view)
                            return
                        }

                        if (pendingAttendanceResolution &&
                            activeModule == StudentModule.ATTENDANCE &&
                            ErpConfig.belongsToErp(url) &&
                            url.contains(StudentModule.ATTENDANCE.path, ignoreCase = true) &&
                            !url.contains("/student/attendancedetail", ignoreCase = true)) {
                            pendingAttendanceResolution = false
                            resolvePendingAttendanceSubject(view, pendingAttendanceCourse, pendingAttendanceCode)
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
        activityResumed = true
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }

        val pausedFor = if (lastPausedAt > 0L) System.currentTimeMillis() - lastPausedAt else 0L
        lastPausedAt = 0L
        if (loggedIn) {
            if (pausedFor >= sessionResumeThresholdMs) {
                recoverSessionAfterBackground()
            } else {
                startSessionHeartbeat()
                val module = activeModule
                if (module != null && binding.moduleScreen.visibility == View.VISIBLE && moduleFetchInProgress) {
                    // If Android paused the WebView mid-navigation, restart only that
                    // request. Keep cached content visible while the request completes.
                    moduleReadRequestId++
                    moduleReadAttempts = 0
                    webView.stopLoading()
                    val targetUrl = if (module == StudentModule.ATTENDANCE && !activeAttendanceDetailUrl.isNullOrBlank()) {
                        activeAttendanceDetailUrl!!
                    } else {
                        ErpConfig.moduleUrl(module.path)
                    }
                    webView.loadUrl(targetUrl)
                    scheduleModuleLoadFallback(module, moduleReadRequestId)
                } else if (binding.dashboardScroll.visibility == View.VISIBLE &&
                    !profileFetchInProgress && preloadingModule == null && preloadQueue.isEmpty()) {
                    preloadAllModules()
                }
            }
        }

        if (loggedIn && binding.dashboardScroll.visibility == View.VISIBLE && !dashboardEntranceAnimated) {
            dashboardEntranceAnimated = true
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
        activityResumed = false
        stopSessionHeartbeat()

        if (::webView.isInitialized) {
            // Preload navigation has no user-visible value while app is backgrounded.
            if (loggedIn && activeModule == null && !profileFetchInProgress) {
                webView.stopLoading()
                moduleReadRequestId++
                preloadingModule = null
                preloadQueue.clear()
            }
            webView.onPause()
            webView.pauseTimers()
        }
        super.onPause()
    }

    private fun recoverSessionAfterBackground() {
        if (!loggedIn || !activityResumed) return

        val module = activeModule
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.visibility = View.INVISIBLE
        }
        preloadingModule = null
        preloadQueue.clear()

        if (module != null && binding.moduleScreen.visibility == View.VISIBLE) {
            moduleReadRequestId++
            moduleReadAttempts = 0
            binding.moduleInfo.text = "Reconnecting to your student account…"
            binding.moduleProgress.visibility = View.VISIBLE

            erpRepository.cachedPayload(module)?.let { payload ->
                parseModuleData(payload)
                    ?.takeIf { erpRepository.hasUsableData(module, it) }
                    ?.let { cached ->
                        renderModuleData(module, cached)
                        binding.moduleInfo.text = "Last synced data • Updating from ERP…"
                        binding.moduleProgress.visibility = View.VISIBLE
                    }
            }

            moduleFetchInProgress = true
            webView.loadUrl(ErpConfig.moduleUrl(module.path))
            scheduleModuleLoadFallback(module, moduleReadRequestId)
        } else {
            // Dashboard/profile are native UI. Don't reload the full ERP dashboard
            // after returning from background, and invalidate a profile read that
            // may have been interrupted while the app was suspended.
            profileFetchInProgress = false
            profileFetchGeneration++
            webView.visibility = View.GONE
            pingSession()
            preloadAllModules()
        }
        startSessionHeartbeat()
    }

    /**
     * Fallback for portal redirects whose URL doesn't identify the login route.
     * It checks the visible ERP DOM instead of assuming one fixed login pathname.
     */
    private fun probeLoginPage(view: WebView) {
        if (!loginInProgress || loginProbeInProgress) return
        if (loginProbeAttempts >= MAX_LOGIN_PROBE_ATTEMPTS) {
            failLogin("The university portal did not finish signing in. Check your credentials or verification prompt and try again.")
            return
        }
        loginProbeAttempts++
        loginProbeInProgress = true
        view.evaluateJavascript(Scripts.loginPageProbe) { result ->
            loginProbeInProgress = false
            if (!loginInProgress) return@evaluateJavascript

            when (ParsingUtils.decodeJavascriptString(result)) {
                "LOGIN_FORM" -> {
                    if (loginSubmitted) {
                        failLogin("The university portal is still showing the sign-in form. Check your username, password, or any verification prompt.")
                    } else {
                        scheduleLoginInjection(view)
                    }
                }
                "LOGIN_ERROR" -> failLogin("Login failed. Check your university username and password.")
                "AUTHENTICATED" -> completeLogin()
                else -> handler.postDelayed({
                    if (loginInProgress) probeLoginPage(view)
                }, LOGIN_INJECTION_DELAY_MS)
            }
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
        loginProbeInProgress = false
        loginProbeAttempts = 0
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
        password = ""
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
        if (!loggedIn || !activityResumed || profileFetchInProgress || preloadingModule != null) return
        preloadQueue.clear()
        preloadQueue.addAll(StudentModule.values().filter { module ->
            val cached = erpRepository.cachedPayload(module)?.let { parseModuleData(it) }
            cached == null || !erpRepository.hasUsableData(module, cached)
        })
        preloadNextModule()
    }

    private fun preloadNextModule() {
        if (!loggedIn || !activityResumed) return
        // Each preloaded module owns a new generation; late JS callbacks from
        // the prior URL must not write data under the next module's cache key.
        moduleReadRequestId++
        if (preloadQueue.isEmpty()) {
            preloadingModule = null
            webView.visibility = View.GONE
            updateNextClassPreview()
            return
        }
        preloadingModule = preloadQueue.removeAt(0)
        moduleReadAttempts = 0
        webView.visibility = View.INVISIBLE
        webView.loadUrl(ErpConfig.moduleUrl(preloadingModule!!.path))
    }

    private fun loadModule(module: StudentModule) {
        if (!loggedIn) return
        if (module == StudentModule.ATTENDANCE) activeAttendanceDetailUrl = null
        closeSideMenu()

        // Cancel hidden preload before the tapped module becomes active.
        // Incrementing the generation invalidates callbacks already in flight.
        if (::webView.isInitialized) webView.stopLoading()
        preloadingModule = null
        preloadQueue.clear()
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++

        activeModule = module
        binding.loginScroll.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.VISIBLE
        applySystemBarStyle(
            statusColor = Color.WHITE,
            navigationColor = Color.rgb(245, 247, 251),
            lightStatusIcons = false,
            lightNavigationIcons = false
        )
        webView.visibility = View.INVISIBLE
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleContent.removeAllViews()
        binding.moduleInfo.text = "Syncing your latest information…"

        when (module) {
            StudentModule.ATTENDANCE -> {
                binding.moduleTitle.text = "Attendance"
                binding.moduleSubtitle.text = "Your attendance"
            }
            StudentModule.TIMETABLE -> {
                binding.moduleTitle.text = "Timetable"
                binding.moduleSubtitle.text = "Your class schedule"
            }
            StudentModule.FEE -> {
                binding.moduleTitle.text = "Invoices"
                binding.moduleSubtitle.text = "Your invoice history"
            }
            StudentModule.PROFILE -> {
                binding.moduleTitle.text = "My Profile"
                binding.moduleSubtitle.text = "Your student information"
            }
            StudentModule.RESULTS -> {
                binding.moduleTitle.text = "Results"
                binding.moduleSubtitle.text = "Your academic results"
            }
        }

        val cachedData = erpRepository.cachedPayload(module)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(module, it) }
        if (cachedData != null) {
            renderModuleData(module, cachedData)
            binding.moduleInfo.text = "Last synced data • Updating from ERP…"
            binding.moduleProgress.visibility = View.VISIBLE
        } else {
            binding.moduleContent.removeAllViews()
            binding.moduleProgress.visibility = View.VISIBLE
            binding.moduleInfo.text = "Syncing your latest information…"
        }

        moduleFetchInProgress = true
        webView.loadUrl(ErpConfig.moduleUrl(module.path))
        scheduleModuleLoadFallback(module, moduleReadRequestId)
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        val detailUrl = activeAttendanceDetailUrl
        if (module == StudentModule.ATTENDANCE && detailUrl != null) {
            openAttendanceDetail(detailUrl)
            return
        }
        if (module == StudentModule.ATTENDANCE) activeAttendanceDetailUrl = null

        if (::webView.isInitialized) webView.stopLoading()
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++
        val cachedData = erpRepository.cachedPayload(module)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(module, it) }
        if (cachedData != null) {
            renderModuleData(module, cachedData)
            binding.moduleInfo.text = "Last synced data • Updating from ERP…"
            binding.moduleProgress.visibility = View.VISIBLE
        } else {
            binding.moduleContent.removeAllViews()
            binding.moduleProgress.visibility = View.VISIBLE
            binding.moduleInfo.text = "Syncing the latest information…"
        }

        webView.visibility = View.INVISIBLE
        moduleFetchInProgress = true
        webView.loadUrl(ErpConfig.moduleUrl(module.path))
        scheduleModuleLoadFallback(module, moduleReadRequestId)
    }

    private fun scheduleModuleLoadFallback(module: StudentModule, requestId: Int) {
        handler.postDelayed({
            if (!loggedIn || !activityResumed || activeModule != module ||
                !moduleFetchInProgress || requestId != moduleReadRequestId) return@postDelayed

            val currentUrl = webView.url.orEmpty()
            val pageMatches = try {
                ErpConfig.belongsToErp(currentUrl) &&
                    java.net.URI(currentUrl).path.orEmpty().contains(module.path, ignoreCase = true)
            } catch (_: Exception) { false }

            if (pageMatches) {
                moduleFetchInProgress = false
                moduleReadAttempts = 0
                readModuleData(webView, module, true)
                return@postDelayed
            }

            moduleFetchInProgress = false
            binding.moduleProgress.visibility = View.GONE
            val hasCachedContent = erpRepository.cachedPayload(module)
                ?.let { parseModuleData(it) }
                ?.let { erpRepository.hasUsableData(module, it) } == true
            if (hasCachedContent) {
                binding.moduleInfo.text = "Couldn't refresh • Showing last synced data"
            } else {
                binding.moduleInfo.text = "Connection needs retry"
                binding.moduleContent.removeAllViews()
                binding.moduleContent.addView(createEmptyState(
                    "Couldn't load this page",
                    "The ERP took too long to respond. Check your connection and tap Refresh to try again."
                ))
            }
        }, MODULE_LOAD_FALLBACK_MS)
    }

    private fun downloadInvoicePdf(url: String, invoiceNumber: String) {
        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        val path = uri?.path.orEmpty()
        if (!loggedIn || !ErpConfig.belongsToErp(url) ||
            !Regex("^/student/invoices/download/[0-9]+/?$").matches(path)) {
            Toast.makeText(this, "This invoice PDF link is unavailable.", Toast.LENGTH_LONG).show()
            return
        }
        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
        if (cookie.isBlank()) {
            Toast.makeText(this, "Your ERP session needs refreshing. Refresh invoices and try again.", Toast.LENGTH_LONG).show()
            return
        }
        val safeNumber = invoiceNumber.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_').take(60).ifBlank { "invoice" }
        val filename = "Superior_Invoice_${safeNumber}_${System.currentTimeMillis()}.pdf"
        try {
            val request = DownloadManager.Request(uri!!)
                .setTitle("Invoice $invoiceNumber")
                .setDescription("Downloading invoice PDF")
                .setMimeType("application/pdf")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            request.addRequestHeader("Cookie", cookie)
            request.addRequestHeader("User-Agent", webView.settings.userAgentString.orEmpty())
            request.addRequestHeader("Referer", ErpConfig.moduleUrl(StudentModule.FEE.path))
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Invoice PDF is downloading to Downloads.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            android.util.Log.w("MainActivity", "Could not start invoice PDF download", error)
            Toast.makeText(this, "Could not start the PDF download. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderModuleData(module: StudentModule, data: ModuleData) {
        binding.moduleContent.removeAllViews()
        if (module != StudentModule.ATTENDANCE || data.attendanceDetail == null) addModuleIntro(module)
        when (module) {
            StudentModule.ATTENDANCE -> renderAttendance(data)
            StudentModule.TIMETABLE -> renderTimetable(data)
            StudentModule.FEE -> renderFee(data)
            StudentModule.PROFILE -> renderProfile(data)
            StudentModule.RESULTS -> renderResults(data)
        }
        binding.moduleProgress.visibility = View.GONE
        binding.moduleInfo.text = "Updated from your student account"
    }

    private fun renderAttendance(data: ModuleData) {
        attendanceRenderer.render(binding.moduleContent, data)
    }

    private fun openAttendanceDetail(url: String, course: String = "", code: String = "") {
        if (!loggedIn) return

        if (url.isNotBlank()) {
            pendingAttendanceResolution = false
            pendingAttendanceCourse = ""
            pendingAttendanceCode = ""
            if (!ErpConfig.belongsToErp(url) || !url.contains("/student/attendancedetail", ignoreCase = true)) {
                Toast.makeText(this, "This subject's ERP detail link is unavailable.", Toast.LENGTH_SHORT).show()
                return
            }
            beginAttendanceDetailNavigation(url)
            return
        }

        if (course.isBlank() && code.isBlank()) {
            Toast.makeText(this, "The subject could not be identified. Refresh attendance and try again.", Toast.LENGTH_LONG).show()
            return
        }

        // The hidden WebView may currently be showing a preloaded module.
        // Always return to the authenticated attendance summary before resolving a subject.
        pendingAttendanceCourse = course
        pendingAttendanceCode = code
        pendingAttendanceResolution = true
        beginAttendanceDetailNavigation(null)
        webView.loadUrl(ErpConfig.moduleUrl(StudentModule.ATTENDANCE.path))
    }

    private fun resolvePendingAttendanceSubject(view: WebView, course: String, code: String) {
        if (!loggedIn || activeModule != StudentModule.ATTENDANCE) return
        binding.moduleInfo.text = "Finding this subject's class history…"
        view.evaluateJavascript(Scripts.attendanceSubjectResolverScript(course, code)) { result ->
            if (!loggedIn || activeModule != StudentModule.ATTENDANCE) return@evaluateJavascript

            val payload = ParsingUtils.decodeJavascriptString(result)
            val resolution = try {
                JSONObject(payload)
            } catch (_: Exception) {
                null
            }
            val resolvedUrl = resolution?.optString("url", "").orEmpty()
            val clicked = resolution?.optBoolean("clicked", false) == true

            when {
                resolvedUrl.isNotBlank() -> {
                    if (!ErpConfig.belongsToErp(resolvedUrl) ||
                        !resolvedUrl.contains("/student/attendancedetail", ignoreCase = true)) {
                        failAttendanceDetailResolution()
                    } else {
                        pendingAttendanceCourse = ""
                        pendingAttendanceCode = ""
                        activeAttendanceDetailUrl = resolvedUrl
                        moduleFetchInProgress = true
                        view.loadUrl(resolvedUrl)
                    }
                }
                clicked -> handler.postDelayed({
                    if (!loggedIn || activeModule != StudentModule.ATTENDANCE) return@postDelayed
                    val currentUrl = webView.url.orEmpty()
                    if (ErpConfig.belongsToErp(currentUrl) &&
                        currentUrl.contains("/student/attendancedetail", ignoreCase = true)) {
                        if (activeAttendanceDetailUrl.isNullOrBlank()) {
                            activeAttendanceDetailUrl = currentUrl
                            moduleFetchInProgress = false
                            readModuleData(webView, StudentModule.ATTENDANCE, true)
                        }
                    } else if (activeAttendanceDetailUrl.isNullOrBlank()) {
                        failAttendanceDetailResolution()
                    }
                }, 1800L)
                else -> failAttendanceDetailResolution()
            }
        }
    }

    private fun beginAttendanceDetailNavigation(url: String?) {
        activeModule = StudentModule.ATTENDANCE
        activeAttendanceDetailUrl = url
        preloadingModule = null
        preloadQueue.clear()
        moduleReadAttempts = 0
        moduleReadRequestId++
        moduleFetchInProgress = true
        binding.loginScroll.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.VISIBLE
        applySystemBarStyle(
            statusColor = Color.WHITE,
            navigationColor = Color.rgb(245, 247, 251),
            lightStatusIcons = false,
            lightNavigationIcons = false
        )
        binding.moduleTitle.text = "Attendance Planner"
        binding.moduleSubtitle.text = "Subject attendance and 75% target"
        binding.moduleContent.removeAllViews()
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleInfo.text = "Finding this subject's attendance detail in ERP…"
        webView.visibility = View.INVISIBLE
        if (!url.isNullOrBlank()) webView.loadUrl(url)
    }

    private fun failAttendanceDetailResolution() {
        pendingAttendanceResolution = false
        pendingAttendanceCourse = ""
        pendingAttendanceCode = ""
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++
        binding.moduleProgress.visibility = View.GONE
        binding.moduleInfo.text = "Subject detail unavailable"
        binding.moduleContent.removeAllViews()
        binding.moduleContent.addView(
            createEmptyState(
                "Could not open this subject's records",
                "The ERP did not expose a detail link or click action for this subject. Return to Attendance and refresh to retry."
            )
        )
        Toast.makeText(this, "Could not find this subject's ERP detail action. Please refresh attendance and try again.", Toast.LENGTH_LONG).show()
    }

    private fun showAttendanceSummary() {
        pendingAttendanceResolution = false
        pendingAttendanceCourse = ""
        pendingAttendanceCode = ""
        activeAttendanceDetailUrl = null
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++
        val summary = erpRepository.cachedPayload(StudentModule.ATTENDANCE)?.let(::parseModuleData)
        if (summary != null && summary.attendanceDetail == null) {
            activeModule = StudentModule.ATTENDANCE
            binding.moduleScreen.visibility = View.VISIBLE
            binding.loginScroll.visibility = View.GONE
            binding.dashboardScroll.visibility = View.GONE
            webView.visibility = View.INVISIBLE
            renderModuleData(StudentModule.ATTENDANCE, summary)
        } else {
            loadModule(StudentModule.ATTENDANCE)
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
        resultsRenderer.render(binding.moduleContent, data)
    }

    private fun selectResultsSemester(label: String) {
        val script = Scripts.semesterSelectScript(label)
        binding.moduleProgress.visibility = View.VISIBLE
        binding.moduleInfo.text = "Loading $label…"
        webView.evaluateJavascript(script) {
            handler.postDelayed({
                if (activeModule == StudentModule.RESULTS) {
                    readModuleData(webView, StudentModule.RESULTS, true)
                }
            }, 900L)
        }
    }

    private fun renderFee(data: ModuleData) {
        feeRenderer.render(binding.moduleContent, data)
    }

    private fun addModuleIntro(module: StudentModule) {
        val title = when (module) {
            StudentModule.ATTENDANCE -> "Attendance overview"
            StudentModule.TIMETABLE -> "Class schedule"
            StudentModule.FEE -> "Invoices overview"
            StudentModule.PROFILE -> "Student profile"
            StudentModule.RESULTS -> "Academic results"
        }
        val subtitle = when (module) {
            StudentModule.ATTENDANCE -> "Subject wise attendance and current percentage"
            StudentModule.TIMETABLE -> "Your weekly classes and timings"
            StudentModule.FEE -> "Paid and unpaid vouchers, due dates and PDF downloads"
            StudentModule.PROFILE -> "Live student information from your ERP account"
            StudentModule.RESULTS -> "Live academic results from your ERP account"
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


    
    
    private fun moduleDefaultTableTitle(module: StudentModule, index: Int): String {
        return when (module) {
            StudentModule.ATTENDANCE -> if (index == 0) "Attendance records" else "Attendance details"
            StudentModule.TIMETABLE -> if (index == 0) "Class schedule" else "Schedule details"
            StudentModule.FEE -> if (index == 0) "Fee records" else "Fee details"
            StudentModule.PROFILE -> if (index == 0) "Profile details" else "Additional profile information"
            StudentModule.RESULTS -> if (index == 0) "Academic results" else "Result details"
        }
    }


    private fun parseModuleData(payload: String): ModuleData? = erpRepository.parse(payload)

    private fun readModuleData(view: WebView, module: StudentModule, displayWhenReady: Boolean) {
        if (!loggedIn || (!displayWhenReady && !activityResumed)) return
        val requestId = moduleReadRequestId

        fun readNow() {
            if (!loggedIn || requestId != moduleReadRequestId ||
                (!displayWhenReady && !activityResumed)) return
            moduleReadAttempts++
            val isAttendanceDetailPage = view.url.orEmpty()
                .contains("/student/attendancedetail", ignoreCase = true)
            val extractionScript = if (module == StudentModule.ATTENDANCE && !isAttendanceDetailPage) {
                Scripts.attendanceSummaryData
            } else {
                Scripts.moduleData
            }
            view.evaluateJavascript(extractionScript) { result ->
                if (!loggedIn || requestId != moduleReadRequestId ||
                    (!displayWhenReady && !activityResumed)) return@evaluateJavascript

                val payload = ParsingUtils.decodeJavascriptString(result)
                val data = parseModuleData(payload)
                val usable = data != null && erpRepository.hasUsableData(module, data)

                if (usable && data != null) {
                    if (module != StudentModule.ATTENDANCE || data.attendanceDetail == null) {
                        erpRepository.cachePayload(module, payload)
                    }
                    if (displayWhenReady && activeModule == module) {
                        moduleFetchInProgress = false
                        renderModuleData(module, data)
                    }
                } else if (displayWhenReady && activeModule == module) {
                    if (moduleReadAttempts < MAX_MODULE_READ_ATTEMPTS) {
                        binding.moduleInfo.text = "Waiting for the ERP data…"
                        handler.postDelayed({ readModuleData(view, module, true) }, MODULE_RETRY_DELAY_MS)
                        return@evaluateJavascript
                    }

                    moduleFetchInProgress = false
                    binding.moduleProgress.visibility = View.GONE
                    val cachedData = erpRepository.cachedPayload(module)
                        ?.let { parseModuleData(it) }
                        ?.takeIf { erpRepository.hasUsableData(module, it) }
                    if (cachedData != null) {
                        binding.moduleInfo.text = "Couldn't refresh • Showing last synced data"
                    } else {
                        binding.moduleInfo.text = "No readable data returned"
                        binding.moduleContent.removeAllViews()
                        binding.moduleContent.addView(createEmptyState(
                            "No readable data returned",
                            "The ERP page loaded, but its data is not ready yet. Tap Refresh to try again."
                        ))
                    }
                } else if (!displayWhenReady && preloadingModule == module) {
                    if (moduleReadAttempts < PRELOAD_READ_ATTEMPTS) {
                        handler.postDelayed({ readModuleData(view, module, false) }, MODULE_RETRY_DELAY_MS)
                        return@evaluateJavascript
                    }
                    preloadingModule = null
                    preloadNextModule()
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            view.postVisualStateCallback(requestId.toLong(), object : WebView.VisualStateCallback() {
                override fun onComplete(requestIdFromWebView: Long) {
                    if (!loggedIn || requestId != moduleReadRequestId ||
                        (!displayWhenReady && !activityResumed)) return
                    handler.postDelayed({ readNow() }, MODULE_VISUAL_DELAY_MS)
                }
            })
        } else {
            handler.postDelayed({ readNow() }, MODULE_READ_DELAY_MS)
        }
    }

    private fun updateNextClassPreview() {
        val label = binding.nextClassLabel
        val payload = erpRepository.cachedPayload(StudentModule.TIMETABLE)
        val data = payload?.let { parseModuleData(it) }
        val rows = data?.let { TimetableParser.toRows(it.records) }
            ?.filter { it.time.isNotBlank() }
            ?.distinctBy { it.day + "|" + it.time + "|" + it.course + "|" + it.code }
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

        label.text = next?.let { "Next: " + ParsingUtils.formatScheduleTime(it.time).replace("\n–\n", " – ").replace("\n", " ") + " • " + it.course } ?: "Schedule synced"
    }

    private fun startSessionHeartbeat() {
        if (!activityResumed || !loggedIn || sessionHeartbeatRunning) return
        sessionHeartbeatRunning = true
        handler.removeCallbacks(sessionHeartbeatRunnable)
        handler.postDelayed(sessionHeartbeatRunnable, SESSION_HEARTBEAT_INTERVAL_MS)
    }

    private fun stopSessionHeartbeat() {
        sessionHeartbeatRunning = false
        handler.removeCallbacks(sessionHeartbeatRunnable)
    }

    private fun pingSession() {
        if (!loggedIn || !activityResumed || !::webView.isInitialized) return
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

    private fun handleModuleLoadFailure(message: String) {
        val module = activeModule ?: return
        moduleFetchInProgress = false
        binding.moduleProgress.visibility = View.GONE
        val cachedData = erpRepository.cachedPayload(module)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(module, it) }

        if (cachedData != null) {
            // Keep the current last-known-good content interactive during outages.
            binding.moduleInfo.text = "Offline • Showing last synced data"
        } else {
            binding.moduleInfo.text = "Connection needs retry"
            binding.moduleContent.removeAllViews()
            binding.moduleContent.addView(createEmptyState(
                "Couldn't connect to the ERP",
                message
            ))
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
        applySystemBarStyle(
            statusColor = Color.rgb(8, 69, 190),
            navigationColor = Color.rgb(245, 247, 251),
            lightStatusIcons = true,
            lightNavigationIcons = false
        )

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
        erpRepository.clearCache()
        webView.stopLoading()
        webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.loginScroll.visibility = View.VISIBLE
        binding.sideMenuOverlay.visibility = View.GONE
        applySystemBarStyle(
            statusColor = Color.rgb(7, 20, 38),
            navigationColor = Color.rgb(7, 20, 38),
            lightStatusIcons = true,
            lightNavigationIcons = true
        )
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
        erpRepository.clearCache()
        showLogin()
    }

    private fun isAuthenticatedUrl(url: String): Boolean = ErpConfig.isAuthenticatedUrl(url)

    private fun failLogin(message: String) {
        loginInProgress = false
        loginSubmitted = false
        loginProbeInProgress = false
        loginProbeAttempts = 0
        loginAttempt = 0
        password = ""
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
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
        private const val MAX_LOGIN_PROBE_ATTEMPTS = 20
        private const val PROFILE_READ_DELAY_MS = 1200L
        private const val PROFILE_VISUAL_DELAY_MS = 500L
        private const val PROFILE_RETRY_DELAY_MS = 1000L
        private const val MAX_PROFILE_READ_ATTEMPTS = 8
        private const val MODULE_READ_DELAY_MS = 650L
        private const val MODULE_VISUAL_DELAY_MS = 220L
        private const val MODULE_RETRY_DELAY_MS = 550L
        private const val MAX_MODULE_READ_ATTEMPTS = 6
        private const val PRELOAD_READ_ATTEMPTS = 3
        private const val MODULE_LOAD_FALLBACK_MS = 10000L
        private const val SESSION_HEARTBEAT_INTERVAL_MS = 4 * 60 * 1000L

    }
}
