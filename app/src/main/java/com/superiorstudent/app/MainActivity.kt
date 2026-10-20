package com.superiorstudent.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Environment
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ImageView
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
import com.superiorstudent.app.core.session.SavedCredentialsStore
import com.superiorstudent.app.core.webview.ModuleDataScriptSelector
import com.superiorstudent.app.core.webview.Scripts
import com.superiorstudent.app.feature.profile.data.ProfileCache
import com.superiorstudent.app.databinding.ActivityMainBinding
import com.superiorstudent.app.ui.Palette
import com.superiorstudent.app.ui.AppTheme
import com.superiorstudent.app.ui.AppThemePreferences
import com.superiorstudent.app.ui.HomeDisplayRules
import com.superiorstudent.app.ui.components.*

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
import com.superiorstudent.app.feature.attendance.domain.AttendanceSummaryCalculator
import com.superiorstudent.app.feature.attendance.presentation.AttendanceViewModel
import androidx.lifecycle.ViewModelProvider
import com.superiorstudent.app.feature.attendance.ui.AttendanceRenderer
import com.superiorstudent.app.feature.timetable.ui.TimetableRenderer
import com.superiorstudent.app.feature.profile.ui.ProfileRenderer
import com.superiorstudent.app.feature.fees.ui.FeeRenderer
import com.superiorstudent.app.feature.results.ui.ResultsRenderer
import com.superiorstudent.app.feature.enrolledcourses.ui.EnrolledCoursesRenderer
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
    private var homePreloadComplete = false
    private val handler = Handler(Looper.getMainLooper())
    private val sessionStore by lazy { SessionStore(this) }
    private val savedCredentialsStore by lazy { SavedCredentialsStore(this) }
    private val profileCache by lazy { ProfileCache(this, erpRepository) }
    private val erpRepository by lazy { ErpRepository(this) }
    private val attendanceViewModel by lazy { ViewModelProvider(this)[AttendanceViewModel::class.java] }
    private val attendanceRenderer by lazy {
        AttendanceRenderer(this, attendanceViewModel, ::openAttendanceDetail, ::showAttendanceSummary)
    }
    private val timetableRenderer by lazy { TimetableRenderer(this, ::refreshActiveModule) }
    private val profileRenderer by lazy {
        ProfileRenderer(this, ::createSectionHeading, ::createTableSection, ::createEmptyState, ::refreshActiveModule)
    }
    private val feeRenderer by lazy {
        FeeRenderer(this, ::createSectionHeading, ::createMetricCard, ::createEmptyState, ::downloadInvoicePdf, ::refreshActiveModule)
    }
    private val resultsRenderer by lazy {
        ResultsRenderer(this, ::createSectionHeading, ::createTableSection, ::createEmptyState, ::selectResultsSemester, ::refreshActiveModule) { profileCache.cgpa }
    }
    private val enrolledCoursesRenderer by lazy {
        EnrolledCoursesRenderer(this, ::attendanceCourseLinks, ::refreshActiveModule)
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
    private var parallelPrefetchBatchId: String? = null
    private var parallelPrefetchStartedAtMs = 0L
    private var parallelPrefetchPollRunnable: Runnable? = null
    private val parallelPrefetchModules = mutableListOf<StudentModule>()
    private val parallelPrefetchProcessedModules = mutableSetOf<StudentModule>()
    private val parallelPrefetchSucceededModules = mutableSetOf<StudentModule>()
    private val parallelPrefetchGenerations = mutableMapOf<StudentModule, Long>()
    private val moduleFetchGenerations = mutableMapOf<StudentModule, Long>()
    private val moduleFetchStartedAtMs = mutableMapOf<StudentModule, Long>()
    private val lastModuleFetchAttemptAtElapsedMs = mutableMapOf<StudentModule, Long>()
    private var firstRenderPendingModule: StudentModule? = null
    private var firstRenderStartedAtMs = 0L
    private var firstRenderSource = "network"
    private var profileFetchStartedAtMs = 0L
    private var moduleFetchInProgress = false
    private var moduleReadRequestId = 0
    private var activeAttendanceDetailUrl: String? = null
    private var pendingAttendanceResolution = false
    private var pendingAttendanceCourse = ""
    private var pendingAttendanceCode = ""
    private var lastPausedAt = 0L
    private var offlineMode = false
    private var silentReloginInProgress = false
    private var sessionRetryAttempted = false
    private var networkCallbackRegistered = false
    private var offlineIndicator: TextView? = null
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val erpNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { handleNetworkAvailable() }
        }

        override fun onLost(network: Network) {
            handler.postDelayed({
                if (!hasInternetAvailable() && (loggedIn || sessionStore.sessionActive)) {
                    setOfflineMode(true)
                }
            }, 250L)
        }
    }
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
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        try {
            safeOnCreate(savedInstanceState)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Startup failed before login UI was ready", t)
            showErrorAndFinish(
                "Pachattar% could not start.\n\n" +
                    "Error: " + t.javaClass.simpleName + "\n" +
                    (t.message ?: "No additional error details.") +
                    "\n\nPlease send this screen to support."
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest handles uiMode changes without recreating MainActivity.
        // Re-apply semantic tokens so Auto follows the newly selected system appearance.
        applySelectedTheme()
        if (::binding.isInitialized && binding.dashboardScroll.visibility == View.VISIBLE) {
            renderHomeDashboard()
        } else {
            rerenderBatchModuleForTheme()
        }
    }

    private fun safeOnCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBarInsets()
        try { sessionStore.migrateFromLegacy() } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "Could not migrate saved session; showing login screen", t)
        }

        binding.modulePullRefresh.setColorSchemeColors(Palette.primary(this), Palette.primaryDark(this))
        binding.modulePullRefresh.setOnRefreshListener { refreshActiveModule() }

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
        applySelectedTheme()
        configureLoginLayoutAndKeyboard()

        binding.loginButton.setOnClickListener {
            username = binding.usernameInput.text.toString().trim()
            password = binding.passwordInput.text.toString()
            if (username.isBlank() || password.isBlank()) {
                binding.loginStatus.text = "Please enter your university username and password."
                styleLoginStatus()
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
            homePreloadComplete = false
            binding.loginButton.isEnabled = false
            binding.loginStatus.text = "Signing in securely…"
            styleLoginStatus()
            webView.visibility = View.GONE
            webView.loadUrl(ErpConfig.LOGIN_URL)
        }

        binding.attendanceButton.setOnClickListener { loadModule(StudentModule.ATTENDANCE) }
        binding.timetableButton.setOnClickListener { loadModule(StudentModule.TIMETABLE) }
        binding.resultsButton.setOnClickListener { loadModule(StudentModule.RESULTS) }
        binding.feeButton.setOnClickListener { loadModule(StudentModule.FEE) }
        binding.profileButton.setOnClickListener { loadModule(StudentModule.PROFILE) }
        binding.enrolledCoursesButton.setOnClickListener { loadModule(StudentModule.ENROLLED_COURSES) }
        binding.navHomeButton.setOnClickListener { showDashboard() }
        binding.navTimetableButton.setOnClickListener { loadModule(StudentModule.TIMETABLE) }
        binding.navAttendanceButton.setOnClickListener { loadModule(StudentModule.ATTENDANCE) }
        binding.navResultsButton.setOnClickListener { loadModule(StudentModule.RESULTS) }
        binding.navMoreButton.setOnClickListener { openSideMenu() }
        binding.refreshButton.setOnClickListener { refreshActiveModule() }
        binding.homeButton.setOnClickListener { showDashboard() }
        binding.menuButton.setOnClickListener { openSideMenu() }
        binding.closeMenuButton.setOnClickListener { closeSideMenu() }
        binding.menuScrim.setOnClickListener { closeSideMenu() }
        binding.menuProfileCard.setOnClickListener { closeSideMenu(); loadModule(StudentModule.PROFILE) }
        binding.menuProfileButton.setOnClickListener { closeSideMenu(); loadModule(StudentModule.PROFILE) }
        binding.menuStudentConnectButton.setOnClickListener { closeSideMenu(); openStudentConnectFromMenu() }
        binding.moreConnectCard.setOnClickListener { closeSideMenu(); openStudentConnectFromMenu() }
        binding.menuResultsButton.setOnClickListener { closeSideMenu(); loadModule(StudentModule.RESULTS) }
        binding.menuResultsRow.setOnClickListener { closeSideMenu(); loadModule(StudentModule.RESULTS) }
        binding.menuInvoicesButton.setOnClickListener { closeSideMenu(); loadModule(StudentModule.FEE) }
        binding.menuInvoicesRow.setOnClickListener { closeSideMenu(); loadModule(StudentModule.FEE) }
        binding.menuEnrolledCoursesButton.setOnClickListener { closeSideMenu(); loadModule(StudentModule.ENROLLED_COURSES) }
        binding.menuCoursesRow.setOnClickListener { closeSideMenu(); loadModule(StudentModule.ENROLLED_COURSES) }
        binding.menuSettingsButton.setOnClickListener { closeSideMenu(); showThemePicker() }
        binding.menuLogoutButton.setOnClickListener { closeSideMenu(); showLogoutConfirmation() }
        binding.moreLogoutCard.setOnClickListener { closeSideMenu(); showLogoutConfirmation() }
        binding.themePaperButton.setOnClickListener { selectAppearance(AppTheme.PAPER) }
        binding.themeNightButton.setOnClickListener { selectAppearance(AppTheme.NIGHT) }
        binding.themeAutoButton.setOnClickListener { selectAppearance(AppTheme.AUTO) }

        // Design-system touches: press-scale feedback on dashboard action cards.
        attachPressFeedback(binding.attendanceButton)
        attachPressFeedback(binding.timetableButton)
        attachPressFeedback(binding.resultsButton)
        attachPressFeedback(binding.feeButton)
        attachPressFeedback(binding.profileButton)
        attachPressFeedback(binding.enrolledCoursesButton)
        attachPressFeedback(binding.navHomeButton)
        attachPressFeedback(binding.navTimetableButton)
        attachPressFeedback(binding.navAttendanceButton)
        attachPressFeedback(binding.navResultsButton)
        attachPressFeedback(binding.navMoreButton)
        attachPressFeedback(binding.menuStudentConnectButton)
        attachPressFeedback(binding.menuInvoicesButton)

        val hasSavedSession = try { sessionStore.sessionActive } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "Ignoring unreadable saved session", t)
            false
        }
        if (savedInstanceState != null) {
            if (ensureWebView()) webView.restoreState(savedInstanceState)
        } else if (hasSavedSession) {
            username = try { sessionStore.username } catch (_: Throwable) { "" }
            if (username.isNotBlank()) erpRepository.activateAccount(username)
            profileCache.reload()
            if (ensureWebView()) {
                if (!hasInternetAvailable()) {
                    enterCachedSessionOffline()
                } else {
                    restoringSession = true
                    binding.loginScroll.visibility = View.GONE
                    binding.dashboardScroll.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    showDashboard()
                    webView.visibility = View.INVISIBLE
                    webView.loadUrl(ErpConfig.DASHBOARD_URL)
                }
            }
        }

        handler.postDelayed({
            if (!::webView.isInitialized) ensureWebView()
        }, WEBVIEW_WARMUP_DELAY_MS)
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
        binding.loginTopSpacer.layoutParams = binding.loginTopSpacer.layoutParams.apply { height = 0 }
        binding.loginTopSpacer.visibility = View.GONE
        val loginViewport = binding.loginContentScroll
        loginViewport.isSmoothScrollingEnabled = true
        loginViewport.post { loginViewport.scrollTo(0, 0) }

        val keepFocusedFieldVisible = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Wait for adjustResize, then keep the focused field and submit action in view
                // whenever both fit into the available keyboard-reduced viewport.
                loginViewport.postDelayed({
                    if (!view.isFocused || binding.loginScroll.visibility != View.VISIBLE) return@postDelayed
                    val fieldRect = android.graphics.Rect()
                    view.getDrawingRect(fieldRect)
                    loginViewport.offsetDescendantRectToMyCoords(view, fieldRect)

                    val buttonRect = android.graphics.Rect()
                    binding.loginButton.getDrawingRect(buttonRect)
                    loginViewport.offsetDescendantRectToMyCoords(binding.loginButton, buttonRect)

                    val visibleTop = loginViewport.scrollY + dp(14)
                    val visibleBottom = loginViewport.scrollY + loginViewport.height - dp(24)
                    val fitsTogether = buttonRect.bottom - fieldRect.top <= loginViewport.height - dp(38)
                    when {
                        fitsTogether && buttonRect.bottom > visibleBottom ->
                            loginViewport.smoothScrollBy(0, buttonRect.bottom - visibleBottom)
                        fieldRect.bottom > visibleBottom ->
                            loginViewport.smoothScrollBy(0, fieldRect.bottom - visibleBottom)
                        fieldRect.top < visibleTop ->
                            loginViewport.smoothScrollTo(0, (fieldRect.top - dp(14)).coerceAtLeast(0))
                    }
                }, 260L)
            }
        }

        // Presentation only. Retain field input behavior, values and bindings.
        binding.usernameInput.apply {
            setTextColor(Palette.ink(this@MainActivity))
            setHintTextColor(Palette.ink3(this@MainActivity))
            alpha = 1f
            isEnabled = true
        }
        binding.passwordInput.apply {
            setTextColor(Palette.ink(this@MainActivity))
            setHintTextColor(Palette.ink3(this@MainActivity))
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
                        } else if (restoringSession || silentReloginInProgress) {
                            keepCachedSessionAfterTransientFailure(
                                "The secure connection could not be verified. Saved data is still available."
                            )
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
                            restoringSession || silentReloginInProgress -> {
                                keepCachedSessionAfterTransientFailure(
                                    "The university portal could not be reached. Saved data is still available."
                                )
                            }
                            activeModule != null -> handleModuleLoadFailure(
                                "Unable to reach the ERP. Check your internet connection and tap Refresh to try again."
                            )
                        }
                    }
        
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: android.webkit.WebResourceResponse
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (!request.isForMainFrame) return
                        val status = errorResponse.statusCode
                        val rejectionSignal = com.superiorstudent.app.core.session.SessionRejectionClassifier
                            .classify(statusCode = status)
                        val transientSignal = rejectionSignal ==
                            com.superiorstudent.app.core.session.SessionRejectionClassifier.Decision.TRANSIENT
                        if (loginInProgress) {
                            if (silentReloginInProgress && rejectionSignal ==
                                com.superiorstudent.app.core.session.SessionRejectionClassifier.Decision.REJECTED) {
                                rejectSilentRelogin()
                            } else if (!silentReloginInProgress && rejectionSignal ==
                                com.superiorstudent.app.core.session.SessionRejectionClassifier.Decision.REJECTED) {
                                failLogin("Login failed. Check your university username and password.")
                            }
                            return
                        }
                        if (rejectionSignal ==
                            com.superiorstudent.app.core.session.SessionRejectionClassifier.Decision.REJECTED &&
                            (loggedIn || restoringSession)) {
                            handler.post { if (!silentReloginInProgress) handleSessionExpired() }
                        } else if (transientSignal && restoringSession) {
                            keepCachedSessionAfterTransientFailure(
                                "The university server is temporarily unavailable. Showing saved data."
                            )
                        } else if (transientSignal && activeModule != null) {
                            handleModuleLoadFailure("The university server is temporarily unavailable. Showing saved data where available.")
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        if (loggedIn && activeModule == StudentModule.ATTENDANCE &&
                            ErpConfig.belongsToErp(url) &&
                            url.contains("/student/attendancedetail", ignoreCase = true)) {
                            activeAttendanceDetailUrl = url
                        }
        
                        if (!loginInProgress && !profileFetchInProgress &&
                            com.superiorstudent.app.core.session.SessionRejectionClassifier.classify(finalUrl = url) ==
                                com.superiorstudent.app.core.session.SessionRejectionClassifier.Decision.REJECTED) {
                            handleSessionExpired()
                            return
                        }
        
                        if (restoringSession) {
                            if (isAuthenticatedUrl(url)) {
                                restoringSession = false
                                completeLogin()
                            } else if (ErpConfig.isLoginUrl(url)) {
                                handleSessionExpired()
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
                                            if (silentReloginInProgress) rejectSilentRelogin()
                                            else failLogin("Login failed. Check your university username and password.")
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
                        if (preload != null && ErpConfig.belongsToErp(url) &&
                            preload.acceptsPath(try { java.net.URI(url).path.orEmpty() } catch (_: Exception) { "" })) {
                            readModuleData(view, preload, false)
                            return
                        }
        
                        val active = activeModule
                        if (moduleFetchInProgress && active != null && ErpConfig.belongsToErp(url) &&
                            active.acceptsPath(try { java.net.URI(url).path.orEmpty() } catch (_: Exception) { "" })) {
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
            binding.loginStatus.text = "University sign-in is unavailable. Update Android System WebView and Chrome, then try again."
            styleLoginStatus()
            false
        }
    }

    override fun onStart() {
        super.onStart()
        registerNetworkMonitoring()
    }

    override fun onStop() {
        unregisterNetworkMonitoring()
        super.onStop()
    }

    private fun registerNetworkMonitoring() {
        if (networkCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, erpNetworkCallback)
            networkCallbackRegistered = true
        } catch (error: Exception) {
            android.util.Log.w("MainActivity", "Could not register network observer", error)
        }
    }

    private fun unregisterNetworkMonitoring() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(erpNetworkCallback)
        } catch (error: Exception) {
            android.util.Log.w("MainActivity", "Could not unregister network observer", error)
        }
        networkCallbackRegistered = false
    }

    private fun hasInternetAvailable(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                connectivityManager.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun handleNetworkAvailable() {
        if (!hasInternetAvailable() || !activityResumed) return
        if (!(loggedIn || sessionStore.sessionActive)) {
            setOfflineMode(false)
            return
        }
        if (offlineMode || restoringSession) {
            revalidateSessionQuietly()
        } else {
            setOfflineMode(false)
            pingSession()
            preloadAllModules()
        }
    }

    private fun revalidateSessionQuietly() {
        if (!activityResumed || !hasInternetAvailable() ||
            loginInProgress || restoringSession || silentReloginInProgress
        ) return
        if (!(loggedIn || sessionStore.sessionActive)) return
        if (!::webView.isInitialized && !ensureWebView()) return
        cancelParallelPrefetch()
        restoringSession = true
        webView.stopLoading()
        webView.visibility = View.INVISIBLE
        webView.loadUrl(ErpConfig.DASHBOARD_URL)
    }

    private fun setOfflineMode(offline: Boolean) {
        offlineMode = offline
        if (!::binding.isInitialized) return
        if (offline) homePreloadComplete = true
        val chip = getOrCreateOfflineIndicator()
        chip.visibility = if (offline && binding.dashboardScroll.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        chip.text = "Offline - showing saved data"
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Palette.card(this@MainActivity))
        }
        chip.setTextColor(Palette.ink(this))
        if (binding.moduleScreen.visibility == View.VISIBLE && activeModule != null && offline) {
            val module = activeModule!!
            val cached = erpRepository.cachedPayload(module)
                ?.let(::parseModuleData)
                ?.let { erpRepository.hasUsableData(module, it) } == true
            binding.moduleInfo.text = if (cached) "Offline - showing saved data" else "Offline - no saved data yet"
            binding.moduleProgress.visibility = View.GONE
        }
        if (offline && binding.dashboardScroll.visibility == View.VISIBLE) renderHomeDashboard()
    }

    private fun getOrCreateOfflineIndicator(): TextView {
        offlineIndicator?.let { return it }
        val chip = TextView(this).apply {
            text = "Offline - showing saved data"
            textSize = 10f
            setPadding(dp(9), dp(6), dp(9), dp(6))
            gravity = Gravity.CENTER
            elevation = dp(3).toFloat()
            visibility = View.GONE
            contentDescription = "Offline. Showing saved data."
        }
        binding.dashboardHeader.addView(chip, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(8)
            marginEnd = dp(12)
        })
        offlineIndicator = chip
        return chip
    }

    private fun enterCachedSessionOffline() {
        restoringSession = false
        loginInProgress = false
        loggedIn = true
        sessionStore.sessionActive = true
        profileFetchInProgress = false
        moduleFetchInProgress = false
        homePreloadComplete = true
        offlineMode = true
        profileCache.reload()
        webView.stopLoading()
        webView.visibility = View.GONE
        showDashboard()
        setOfflineMode(true)
    }

    private fun keepCachedSessionAfterTransientFailure(message: String) {
        val hadSavedSession = sessionStore.sessionActive || loggedIn || restoringSession
        loginInProgress = false
        loginSubmitted = false
        loginProbeInProgress = false
        loginProbeAttempts = 0
        restoringSession = false
        if (silentReloginInProgress) {
            silentReloginInProgress = false
            sessionRetryAttempted = false
            password = ""
        }
        moduleFetchInProgress = false
        profileFetchInProgress = false
        webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE

        if (!hadSavedSession) {
            setOfflineMode(!hasInternetAvailable())
            binding.loginStatus.text = message
            styleLoginStatus()
            return
        }

        loggedIn = true
        sessionStore.sessionActive = true
        homePreloadComplete = true
        profileCache.reload()
        if (activeModule != null && binding.moduleScreen.visibility == View.VISIBLE) {
            val module = activeModule!!
            val cached = erpRepository.cachedPayload(module)
                ?.let(::parseModuleData)
                ?.takeIf { erpRepository.hasUsableData(module, it) }
            if (cached != null) {
                renderModuleData(module, cached)
            } else {
                binding.moduleContent.removeAllViews()
                binding.moduleContent.addView(createEmptyState(
                    "No saved data yet",
                    "The university portal could not be reached. Reconnect to load this section."
                ))
            }
        } else {
            showDashboard()
        }
        setOfflineMode(!hasInternetAvailable())
        if (!offlineMode) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun beginSilentRelogin(savedUsername: String, savedPassword: String) {
        if (!hasInternetAvailable()) {
            keepCachedSessionAfterTransientFailure("Offline - showing saved data")
            return
        }
        if (!::webView.isInitialized && !ensureWebView()) {
            keepCachedSessionAfterTransientFailure("University sign-in is temporarily unavailable. Saved data is still available.")
            return
        }
        sessionRetryAttempted = true
        silentReloginInProgress = true
        restoringSession = false
        loginInProgress = true
        loginSubmitted = false
        loginAttempt = 0
        loginProbeInProgress = false
        loginProbeAttempts = 0
        username = savedUsername
        password = savedPassword
        webView.stopLoading()
        webView.visibility = View.INVISIBLE
        webView.loadUrl(ErpConfig.LOGIN_URL)
    }

    private fun rejectSilentRelogin() {
        silentReloginInProgress = false
        sessionRetryAttempted = false
        loginInProgress = false
        loginSubmitted = false
        restoringSession = false
        password = ""
        savedCredentialsStore.clear()
        sessionStore.sessionActive = false
        loggedIn = false
        stopSessionHeartbeat()
        showLogin()
        binding.loginStatus.text = "Saved university credentials were rejected. Please sign in again."
        styleLoginStatus()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if ((loggedIn || sessionStore.sessionActive) && !hasInternetAvailable()) {
            setOfflineMode(true)
        } else if ((loggedIn || sessionStore.sessionActive) && offlineMode) {
            revalidateSessionQuietly()
        }
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
        cancelParallelPrefetch()

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
        cancelParallelPrefetch()
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
                "LOGIN_ERROR" -> {
                    if (silentReloginInProgress) rejectSilentRelogin()
                    else failLogin("Login failed. Check your university username and password.")
                }
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
                                styleLoginStatus()
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
        silentReloginInProgress = false
        sessionRetryAttempted = false
        loggedIn = true
        setOfflineMode(false)
        profileReadAttempts = 0
        startSessionHeartbeat()
        sessionStore.sessionActive = true
        sessionStore.username = username
        if (password.isNotBlank()) savedCredentialsStore.save(username, password)
        if (erpRepository.activateAccount(username)) profileCache.clear() else profileCache.reload()
        CookieManager.getInstance().flush()
        binding.loginStatus.text = ""
        styleLoginStatus()
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        password = ""
        // Prevent dashboard entry from starting a module preload before the
        // profile request takes ownership of the single main WebView.
        profileFetchInProgress = true
        showDashboard()
        fetchStudentProfile()
    }

    private fun fetchStudentProfile() {
        if (!loggedIn) return
        profileFetchGeneration++
        profileFetchInProgress = true
        profileFetchStartedAtMs = android.os.SystemClock.elapsedRealtime()
        profileReadAttempts = 0
        android.util.Log.i("ERPPerf", "module_fetch_start module=PROFILE source=network")
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

                profileCache.save(
                    name.takeIf { ParsingUtils.isValidStudentName(it) },
                    cgpa.ifBlank { null },
                    sgpa.ifBlank { null }
                )
                renderHomeDashboard()
            } catch (_: Exception) {
                // The ERP may still be hydrating dynamic fields.
            }

            val ready = ParsingUtils.isValidStudentName(name) && (cgpa.isNotBlank() || sgpa.isNotBlank())
            if (ready || (hasUsefulProfileFields && ParsingUtils.isValidStudentName(name))) {
                profileFetchInProgress = false
                val elapsed = (android.os.SystemClock.elapsedRealtime() - profileFetchStartedAtMs).coerceAtLeast(0L)
                android.util.Log.i("ERPPerf", "module_fetch module=PROFILE elapsed_ms=$elapsed source=network outcome=success")
                preloadAllModules()
                return@evaluateJavascript
            }

            if (profileReadAttempts < MAX_PROFILE_READ_ATTEMPTS) {
                handler.postDelayed({ extractAndApplyProfile(view) }, PROFILE_RETRY_DELAY_MS)
            } else {
                profileFetchInProgress = false
                val elapsed = (android.os.SystemClock.elapsedRealtime() - profileFetchStartedAtMs).coerceAtLeast(0L)
                android.util.Log.w("ERPPerf", "module_fetch module=PROFILE elapsed_ms=$elapsed source=network outcome=unusable_response")
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
        if (!loggedIn || !activityResumed || profileFetchInProgress ||
            preloadingModule != null || parallelPrefetchBatchId != null || activeModule != null
        ) return
        if (offlineMode || !hasInternetAvailable()) {
            homePreloadComplete = true
            setOfflineMode(true)
            return
        }

        val staleModules = StudentModule.values().filter(::shouldAutoRefreshModule)
        preloadQueue.clear()
        homePreloadComplete = staleModules.isEmpty()
        if (staleModules.isEmpty()) {
            if (binding.dashboardScroll.visibility == View.VISIBLE) renderHomeDashboard()
            return
        }
        if (binding.dashboardScroll.visibility == View.VISIBLE) renderHomeDashboard()

        // Parallel prefetch uses authenticated, same-origin iframes and never
        // navigates the primary WebView while the dashboard is displayed.
        if (startParallelPrefetch(staleModules)) return

        // Conservative fallback for a provider/page where frame-based prefetch
        // cannot start. Keep the established one-WebView-at-a-time behaviour.
        preloadQueue.addAll(staleModules)
        preloadNextModule()
    }

    private fun shouldAutoRefreshModule(module: StudentModule): Boolean {
        val data = erpRepository.cachedPayload(module)?.let(::parseModuleData)
        val hasUsableCache = data != null && erpRepository.hasUsableData(module, data)
        val needsRefresh = !hasUsableCache || erpRepository.needsRefresh(module)
        if (!needsRefresh) return false
        val now = android.os.SystemClock.elapsedRealtime()
        val lastAttempt = lastModuleFetchAttemptAtElapsedMs[module]
        return lastAttempt == null || now - lastAttempt >= ErpRepository.CACHE_TTL_MS
    }

    private fun beginModuleFetch(module: StudentModule): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        val generation = erpRepository.beginFetch(module)
        moduleFetchGenerations[module] = generation
        moduleFetchStartedAtMs[module] = now
        lastModuleFetchAttemptAtElapsedMs[module] = now
        android.util.Log.i("ERPPerf", "module_fetch_start module=${module.name} source=network")
        return generation
    }

    private fun logModuleFetchOutcome(module: StudentModule, outcome: String) {
        val startedAt = moduleFetchStartedAtMs.remove(module)
        val elapsed = if (startedAt == null) -1L
            else (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        android.util.Log.i(
            "ERPPerf",
            "module_fetch module=${module.name} elapsed_ms=$elapsed source=network outcome=$outcome"
        )
    }

    private fun logModuleFirstRender(module: StudentModule, source: String, success: Boolean) {
        if (firstRenderPendingModule != module) return
        val elapsed = (android.os.SystemClock.elapsedRealtime() - firstRenderStartedAtMs).coerceAtLeast(0L)
        android.util.Log.i(
            "ERPPerf",
            "screen_first_render module=${module.name} elapsed_ms=$elapsed source=$source success=$success"
        )
        firstRenderPendingModule = null
    }

    private fun startParallelPrefetch(modules: List<StudentModule>): Boolean {
        if (modules.size < 2 || !::webView.isInitialized || !webViewReady) return false
        val currentUrl = webView.url.orEmpty()
        if (!ErpConfig.belongsToErp(currentUrl) || ErpConfig.isLoginUrl(currentUrl)) return false

        val batchId = java.util.UUID.randomUUID().toString()
        val jobs = org.json.JSONArray()
        parallelPrefetchModules.clear()
        parallelPrefetchProcessedModules.clear()
        parallelPrefetchSucceededModules.clear()
        parallelPrefetchGenerations.clear()

        modules.forEach { module ->
            val targetUrl = ErpConfig.moduleUrl(module.path)
            if (!ErpConfig.belongsToErp(targetUrl) || ErpConfig.isLoginUrl(targetUrl)) return@forEach
            val generation = beginModuleFetch(module)
            parallelPrefetchGenerations[module] = generation
            parallelPrefetchModules.add(module)
            jobs.put(
                JSONObject()
                    .put("key", module.name)
                    .put("url", targetUrl)
                    .put("script", Scripts.moduleDataFor(module, targetUrl))
                    .put("fallback", Scripts.moduleData)
                    .put("timeoutMs", PARALLEL_PREFETCH_JOB_TIMEOUT_MS)
            )
        }
        if (jobs.length() < 2) {
            parallelPrefetchModules.forEach {
                moduleFetchStartedAtMs.remove(it)
                moduleFetchGenerations.remove(it)
                lastModuleFetchAttemptAtElapsedMs.remove(it)
            }
            parallelPrefetchModules.clear()
            parallelPrefetchGenerations.clear()
            return false
        }

        parallelPrefetchBatchId = batchId
        parallelPrefetchStartedAtMs = android.os.SystemClock.elapsedRealtime()
        homePreloadComplete = false
        android.util.Log.i("ERPPerf", "parallel_prefetch_start modules=${parallelPrefetchModules.joinToString(",") { it.name }}")

        return try {
            webView.evaluateJavascript(Scripts.parallelPrefetchScript(batchId, jobs.toString())) { raw ->
                if (parallelPrefetchBatchId != batchId) return@evaluateJavascript
                val reply = ParsingUtils.decodeJavascriptString(raw)
                if (reply != "STARTED") {
                    android.util.Log.w("ERPPerf", "parallel_prefetch_start_failed")
                    finishParallelPrefetch(batchId, retryFailed = true, cancelInPage = true)
                } else {
                    scheduleParallelPrefetchPoll(batchId, PARALLEL_PREFETCH_POLL_INTERVAL_MS)
                }
            }
            true
        } catch (error: Exception) {
            android.util.Log.w("ERPPerf", "parallel_prefetch_start_failed error=${error.javaClass.simpleName}")
            cancelParallelPrefetch()
            false
        }
    }

    private fun scheduleParallelPrefetchPoll(batchId: String, delayMs: Long) {
        val poll = Runnable {
            if (parallelPrefetchBatchId != batchId) return@Runnable
            if (!loggedIn || !activityResumed || !hasInternetAvailable() || activeModule != null) {
                cancelParallelPrefetch()
                return@Runnable
            }
            val elapsed = android.os.SystemClock.elapsedRealtime() - parallelPrefetchStartedAtMs
            if (elapsed >= PARALLEL_PREFETCH_TOTAL_TIMEOUT_MS) {
                finishParallelPrefetch(batchId, retryFailed = true, cancelInPage = true)
                return@Runnable
            }

            try {
                webView.evaluateJavascript(Scripts.parallelPrefetchStatusScript(batchId)) { raw ->
                    if (parallelPrefetchBatchId != batchId) return@evaluateJavascript
                    val statusJson = ParsingUtils.decodeJavascriptString(raw)
                    val state = try {
                        if (statusJson.isBlank()) null else JSONObject(statusJson)
                    } catch (_: Exception) {
                        null
                    }
                    if (state == null) {
                        finishParallelPrefetch(batchId, retryFailed = true, cancelInPage = true)
                        return@evaluateJavascript
                    }

                    val results = state.optJSONObject("results")
                    parallelPrefetchModules.toList().forEach { module ->
                        if (module in parallelPrefetchProcessedModules) return@forEach
                        val result = results?.optJSONObject(module.name) ?: return@forEach
                        parallelPrefetchProcessedModules.add(module)
                        val resultStatus = result.optString("status", "error")
                        if (resultStatus == "auth") {
                            android.util.Log.w("ERPPerf", "parallel_prefetch_session_rejected")
                            cancelParallelPrefetch()
                            handleSessionExpired()
                            return@evaluateJavascript
                        }

                        val generation = parallelPrefetchGenerations[module]
                        var accepted = false
                        if (resultStatus == "done" && generation != null) {
                            val payload = result.optString("payload", "")
                            accepted = payload.isNotBlank() &&
                                erpRepository.cachePayload(module, payload, generation)
                            if (!accepted) {
                                val fallback = result.optString("fallbackPayload", "")
                                accepted = fallback.isNotBlank() &&
                                    erpRepository.cachePayload(module, fallback, generation)
                            }
                        }
                        if (accepted) {
                            parallelPrefetchSucceededModules.add(module)
                            logModuleFetchOutcome(module, "success")
                            android.util.Log.i(
                                "ERPPerf",
                                "parallel_prefetch_item module=${module.name} elapsed_ms=${result.optLong("durationMs", -1L)} outcome=success"
                            )
                            if (binding.dashboardScroll.visibility == View.VISIBLE) renderHomeDashboard()
                        } else {
                            logModuleFetchOutcome(module, if (resultStatus == "done") "unusable_response" else resultStatus)
                            android.util.Log.w(
                                "ERPPerf",
                                "parallel_prefetch_item module=${module.name} elapsed_ms=${result.optLong("durationMs", -1L)} outcome=${resultStatus}"
                            )
                        }
                    }

                    val pending = state.optInt("pending", -1)
                    if (pending <= 0 || parallelPrefetchProcessedModules.size >= parallelPrefetchModules.size) {
                        finishParallelPrefetch(batchId, retryFailed = true, cancelInPage = false)
                    } else {
                        scheduleParallelPrefetchPoll(batchId, PARALLEL_PREFETCH_POLL_INTERVAL_MS)
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("ERPPerf", "parallel_prefetch_poll_failed error=${error.javaClass.simpleName}")
                finishParallelPrefetch(batchId, retryFailed = true, cancelInPage = true)
            }
        }
        parallelPrefetchPollRunnable = poll
        handler.postDelayed(poll, delayMs)
    }

    private fun finishParallelPrefetch(batchId: String, retryFailed: Boolean, cancelInPage: Boolean) {
        if (parallelPrefetchBatchId != batchId) return
        parallelPrefetchPollRunnable?.let(handler::removeCallbacks)

        val modules = parallelPrefetchModules.toList()
        val failed = modules.filter { it !in parallelPrefetchSucceededModules }
        modules.filter { it !in parallelPrefetchProcessedModules }.forEach {
            logModuleFetchOutcome(it, "timeout_or_missing_result")
        }
        val elapsed = (android.os.SystemClock.elapsedRealtime() - parallelPrefetchStartedAtMs).coerceAtLeast(0L)
        if (cancelInPage && ::webView.isInitialized && ErpConfig.belongsToErp(webView.url.orEmpty())) {
            try {
                webView.evaluateJavascript(Scripts.cancelParallelPrefetchScript(batchId), null)
            } catch (_: Exception) { }
        }

        parallelPrefetchBatchId = null
        parallelPrefetchPollRunnable = null
        parallelPrefetchModules.clear()
        parallelPrefetchProcessedModules.clear()
        parallelPrefetchSucceededModules.clear()
        parallelPrefetchGenerations.clear()
        homePreloadComplete = failed.isEmpty()
        android.util.Log.i(
            "ERPPerf",
            "parallel_prefetch_complete elapsed_ms=$elapsed success_count=${modules.size - failed.size} failed_count=${failed.size}"
        )

        if (retryFailed && failed.isNotEmpty() && loggedIn && activityResumed &&
            hasInternetAvailable() && activeModule == null
        ) {
            preloadQueue.clear()
            preloadQueue.addAll(failed)
            homePreloadComplete = false
            preloadNextModule()
            return
        }

        if (activeModule == null) {
            if (failed.isNotEmpty()) homePreloadComplete = false
            if (::webView.isInitialized) webView.visibility = View.GONE
            if (binding.dashboardScroll.visibility == View.VISIBLE) renderHomeDashboard()
        }
    }

    private fun cancelParallelPrefetch() {
        val batchId = parallelPrefetchBatchId ?: return
        parallelPrefetchPollRunnable?.let(handler::removeCallbacks)
        if (::webView.isInitialized && ErpConfig.belongsToErp(webView.url.orEmpty())) {
            try {
                webView.evaluateJavascript(Scripts.cancelParallelPrefetchScript(batchId), null)
            } catch (_: Exception) { }
        }
        parallelPrefetchModules.forEach { module ->
            moduleFetchStartedAtMs.remove(module)
            moduleFetchGenerations.remove(module)
            lastModuleFetchAttemptAtElapsedMs.remove(module)
        }
        parallelPrefetchBatchId = null
        parallelPrefetchPollRunnable = null
        parallelPrefetchModules.clear()
        parallelPrefetchProcessedModules.clear()
        parallelPrefetchSucceededModules.clear()
        parallelPrefetchGenerations.clear()
        homePreloadComplete = false
        android.util.Log.i("ERPPerf", "parallel_prefetch_cancelled")
    }

    private fun preloadNextModule() {
        if (!loggedIn || !activityResumed) return
        // Each sequential fallback gets a new fetch generation; delayed callbacks
        // from an earlier module may not overwrite newer cache snapshots.
        moduleReadRequestId++
        if (preloadQueue.isEmpty()) {
            preloadingModule = null
            homePreloadComplete = true
            webView.visibility = View.GONE
            if (binding.dashboardScroll.visibility == View.VISIBLE) renderHomeDashboard()
            return
        }
        preloadingModule = preloadQueue.removeAt(0)
        moduleReadAttempts = 0
        webView.visibility = View.INVISIBLE
        beginModuleFetch(preloadingModule!!)
        webView.loadUrl(ErpConfig.moduleUrl(preloadingModule!!.path))
    }

    private fun loadModule(module: StudentModule) {
        if (!loggedIn) return
        if (module == StudentModule.ATTENDANCE) activeAttendanceDetailUrl = null
        closeSideMenu()
        cancelParallelPrefetch()
        firstRenderPendingModule = module
        firstRenderStartedAtMs = android.os.SystemClock.elapsedRealtime()
        firstRenderSource = "network"

        // Cancel hidden preload before the tapped module becomes active.
        // Incrementing the generation invalidates callbacks already in flight.
        if (::webView.isInitialized) webView.stopLoading()
        preloadingModule = null
        preloadQueue.clear()
        homePreloadComplete = true
        moduleFetchInProgress = false
        moduleReadAttempts = 0
        moduleReadRequestId++

        activeModule = module
        binding.loginScroll.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.dashboardBottomNav.visibility = View.GONE
        binding.moduleScreen.visibility = View.VISIBLE
        applyModuleNavigation()
        applyModuleContentPadding(module)
        applySystemBarStyle(
            statusColor = Palette.surfaceAlt(this),
            navigationColor = Palette.surfaceAlt(this),
            lightStatusIcons = Palette.isNight(this),
            lightNavigationIcons = Palette.isNight(this)
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
                PageHeader.bindPage(this, binding.moduleTitle, binding.moduleSubtitle, "Timetable", timetableDateSubtitle())
            }
            StudentModule.FEE -> {
                BackHeader.bindExisting(this, binding.homeButton, binding.moduleTitle, binding.moduleSubtitle, "Fee invoices", "")
            }
            StudentModule.PROFILE -> {
                BackHeader.bindExisting(this, binding.homeButton, binding.moduleTitle, binding.moduleSubtitle, "Profile", "")
            }
            StudentModule.RESULTS -> {
                PageHeader.bindPage(this, binding.moduleTitle, binding.moduleSubtitle, "Results", "Academic results")
            }
            StudentModule.ENROLLED_COURSES -> {
                BackHeader.bindExisting(this, binding.homeButton, binding.moduleTitle, binding.moduleSubtitle, "Enrolled courses", "")
            }
        }

        val cachedData = erpRepository.cachedPayload(module)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(module, it) }
        if (cachedData != null) {
            firstRenderSource = "cache"
            renderModuleData(module, cachedData)
            binding.moduleInfo.text = if (erpRepository.needsRefresh(module)) {
                "Last synced data • Updating in background…"
            } else {
                "Showing saved data"
            }
            // Existing usable content remains interactive while stale data refreshes.
            binding.moduleProgress.visibility = View.GONE
        } else {
            binding.moduleContent.removeAllViews()
            if (isRedesignedModule(module)) {
                binding.moduleContent.addView(Skeleton.rows(
                    this,
                    when (module) {
                        StudentModule.TIMETABLE -> 3
                        StudentModule.RESULTS -> 4
                        StudentModule.FEE -> 3
                        StudentModule.PROFILE -> 3
                        StudentModule.ENROLLED_COURSES -> 4
                        else -> 3
                    },
                    module == StudentModule.TIMETABLE
                ))
                binding.moduleProgress.visibility = View.GONE
            } else {
                binding.moduleProgress.visibility = View.VISIBLE
            }
            binding.moduleInfo.text = "Syncing your latest information…"
        }

        if (!hasInternetAvailable() || offlineMode) {
            moduleFetchInProgress = false
            setOfflineMode(true)
            if (cachedData == null) {
                binding.moduleContent.removeAllViews()
                binding.moduleContent.addView(createEmptyState(
                    "No saved data yet",
                    "Connect to the internet once to load this section."
                ))
            }
            binding.moduleProgress.visibility = View.GONE
            binding.moduleInfo.text = if (cachedData != null) "Offline - showing saved data" else "Offline - no saved data yet"
            return
        }
        val needsRefresh = cachedData == null || erpRepository.needsRefresh(module)
        if (!needsRefresh) {
            moduleFetchInProgress = false
            binding.moduleProgress.visibility = View.GONE
            binding.modulePullRefresh.isRefreshing = false
            return
        }
        if (!shouldAutoRefreshModule(module)) {
            moduleFetchInProgress = false
            binding.moduleProgress.visibility = View.GONE
            binding.modulePullRefresh.isRefreshing = false
            if (cachedData == null) {
                binding.moduleContent.removeAllViews()
                binding.moduleContent.addView(createEmptyState(
                    "No saved data yet",
                    "Automatic retries are limited to once every five minutes. Tap Refresh to try again."
                ))
                binding.moduleInfo.text = "No saved data yet"
                logModuleFirstRender(module, "empty", false)
            } else {
                binding.moduleInfo.text = "Showing saved data • Automatic refresh is throttled"
            }
            return
        }
        moduleFetchInProgress = true
        beginModuleFetch(module)
        webView.loadUrl(ErpConfig.moduleUrl(module.path))
        scheduleModuleLoadFallback(module, moduleReadRequestId)
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        if (!hasInternetAvailable() || offlineMode) {
            binding.modulePullRefresh.isRefreshing = false
            setOfflineMode(true)
            val cached = erpRepository.cachedPayload(module)
                ?.let(::parseModuleData)
                ?.takeIf { erpRepository.hasUsableData(module, it) }
            moduleFetchInProgress = false
            binding.moduleProgress.visibility = View.GONE
            if (cached != null) renderModuleData(module, cached) else {
                binding.moduleContent.removeAllViews()
                binding.moduleContent.addView(createEmptyState(
                    "No saved data yet",
                    "Connect to the internet once to load this section."
                ))
            }
            binding.moduleInfo.text = if (cached != null) "Offline - showing saved data" else "Offline - no saved data yet"
            return
        }
        val detailUrl = activeAttendanceDetailUrl
        if (module == StudentModule.ATTENDANCE && detailUrl != null) {
            openAttendanceDetail(detailUrl)
            return
        }
        if (module == StudentModule.ATTENDANCE) activeAttendanceDetailUrl = null

        cancelParallelPrefetch()
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
            binding.moduleProgress.visibility = if (isRedesignedModule(module)) View.GONE else View.VISIBLE
        } else {
            binding.moduleContent.removeAllViews()
            if (isRedesignedModule(module)) {
                binding.moduleContent.addView(Skeleton.rows(
                    this,
                    when (module) {
                        StudentModule.TIMETABLE -> 3
                        StudentModule.RESULTS -> 4
                        StudentModule.FEE -> 3
                        StudentModule.PROFILE -> 3
                        StudentModule.ENROLLED_COURSES -> 4
                        else -> 3
                    },
                    module == StudentModule.TIMETABLE
                ))
                binding.moduleProgress.visibility = View.GONE
            } else {
                binding.moduleProgress.visibility = View.VISIBLE
            }
            binding.moduleInfo.text = "Syncing the latest information…"
        }

        webView.visibility = View.INVISIBLE
        moduleFetchInProgress = true
        beginModuleFetch(module)
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
                    module.acceptsPath(java.net.URI(currentUrl).path.orEmpty())
            } catch (_: Exception) { false }

            if (pageMatches) {
                moduleFetchInProgress = false
                moduleReadAttempts = 0
                readModuleData(webView, module, true)
                return@postDelayed
            }

            moduleFetchInProgress = false
            binding.moduleProgress.visibility = View.GONE
            binding.modulePullRefresh.isRefreshing = false
            logModuleFetchOutcome(module, "timeout")
            val hasCachedContent = erpRepository.cachedPayload(module)
                ?.let { parseModuleData(it) }
                ?.let { erpRepository.hasUsableData(module, it) } == true
            if (hasCachedContent) {
                binding.moduleInfo.text = "Couldn't refresh • Showing last synced data"
            } else {
                binding.moduleInfo.text = "Connection needs retry"
                binding.moduleContent.removeAllViews()
                if (isRedesignedModule(module)) {
                    binding.moduleContent.addView(ErrorState.create(
                        this,
                        "Couldn't load this page",
                        "The ERP took too long to respond. Check your connection and tap Retry to try again.",
                        ::refreshActiveModule
                    ))
                } else {
                    binding.moduleContent.addView(createEmptyState(
                        "Couldn't load this page",
                        "The ERP took too long to respond. Check your connection and tap Refresh to try again."
                    ))
                }
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
        applyModuleContentPadding(module)
        if (module != StudentModule.ENROLLED_COURSES &&
            module != StudentModule.TIMETABLE &&
            module != StudentModule.RESULTS &&
            module != StudentModule.FEE &&
            module != StudentModule.PROFILE &&
            (module != StudentModule.ATTENDANCE || data.attendanceDetail == null)) addModuleIntro(module)
        when (module) {
            StudentModule.ATTENDANCE -> renderAttendance(data)
            StudentModule.TIMETABLE -> renderTimetable(data)
            StudentModule.FEE -> renderFee(data)
            StudentModule.PROFILE -> renderProfile(data)
            StudentModule.RESULTS -> renderResults(data)
            StudentModule.ENROLLED_COURSES -> renderEnrolledCourses(data)
        }
        binding.moduleProgress.visibility = View.GONE
        binding.moduleInfo.text = if (offlineMode || !hasInternetAvailable()) {
            "Offline - showing saved data"
        } else {
            "Updated from your student account"
        }
        logModuleFirstRender(module, firstRenderSource, success = true)
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
        binding.dashboardBottomNav.visibility = View.GONE
        applySystemBarStyle(
            statusColor = Palette.surfaceAlt(this),
            navigationColor = Palette.surfaceAlt(this),
            lightStatusIcons = Palette.isNight(this),
            lightNavigationIcons = Palette.isNight(this)
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
            firstRenderPendingModule = StudentModule.ATTENDANCE
            firstRenderStartedAtMs = android.os.SystemClock.elapsedRealtime()
            firstRenderSource = "cache"
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
                visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            })
        }
    }

    private fun renderTimetable(data: ModuleData) {
        PageHeader.bindPage(this, binding.moduleTitle, binding.moduleSubtitle, "Timetable", timetableDateSubtitle())
        applyModuleContentPadding(StudentModule.TIMETABLE)
        timetableRenderer.render(binding.moduleContent, data)
    }

    private fun renderProfile(data: ModuleData) {
        BackHeader.bindExisting(this, binding.homeButton, binding.moduleTitle, binding.moduleSubtitle, "Profile", "")
        applyModuleContentPadding(StudentModule.PROFILE)
        profileRenderer.render(binding.moduleContent, data, profileCache.studentName)
    }

    private fun renderResults(data: ModuleData) {
        PageHeader.bindPage(
            this,
            binding.moduleTitle,
            binding.moduleSubtitle,
            "Results",
            if (hasPublishedResultsForHeader(data)) "Published results" else "Results not published yet"
        )
        applyModuleContentPadding(StudentModule.RESULTS)
        resultsRenderer.render(binding.moduleContent, data)
    }

    private fun hasPublishedResultsForHeader(data: ModuleData): Boolean {
        fun positive(value: String): Boolean =
            Regex("""\d+(?:\.\d+)?""").find(value.replace(",", ""))?.value
                ?.toDoubleOrNull()?.let { it > 0.0 } == true

        if (data.academicResults.any { term ->
            positive(term.gradePoints) || positive(term.gpa) || positive(term.cgpa) ||
                term.courses.any { course ->
                    course.finalGrade.isNotBlank() || course.midGrade.isNotBlank() ||
                        positive(course.marksObtained) || positive(course.gradePoints)
                }
        }) return true

        return data.tables.any { table ->
            table.headers.any { it.contains("grade", true) || it.contains("marks", true) } &&
                table.rows.any { row -> row.any { value ->
                    value.isNotBlank() && (positive(value) || value.any { char -> char.isLetter() })
                } }
        } || data.records.any { record ->
            record.headers.any { it.contains("grade", true) || it.contains("marks", true) } &&
                record.values.any { value ->
                    value.isNotBlank() && (positive(value) || value.any { char -> char.isLetter() })
                }
        }
    }

    private fun isBatchDesignModule(module: StudentModule): Boolean =
        module == StudentModule.TIMETABLE || module == StudentModule.RESULTS

    private fun isBatchBModule(module: StudentModule): Boolean =
        module == StudentModule.FEE || module == StudentModule.PROFILE || module == StudentModule.ENROLLED_COURSES

    private fun isRedesignedModule(module: StudentModule): Boolean =
        isBatchDesignModule(module) || isBatchBModule(module)

    private fun timetableDateSubtitle(): String =
        java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.ENGLISH).format(java.util.Date())

    private fun applyModuleContentPadding(module: StudentModule?) {
        if (!::binding.isInitialized) return
        val bottom = if (module != null && isBatchDesignModule(module)) {
            val systemInset = ViewCompat.getRootWindowInsets(binding.root)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            dp(58 + 16) + systemInset
        } else dp(24)
        binding.moduleContent.setPadding(
            binding.moduleContent.paddingLeft,
            binding.moduleContent.paddingTop,
            binding.moduleContent.paddingRight,
            bottom
        )
    }

    private fun rerenderBatchModuleForTheme() {
        val module = activeModule ?: return
        if (!isRedesignedModule(module) || binding.moduleScreen.visibility != View.VISIBLE) return

        val cachedData = erpRepository.cachedPayload(module)
            ?.let(::parseModuleData)
            ?.takeIf { erpRepository.hasUsableData(module, it) }
        if (cachedData != null) {
            renderModuleData(module, cachedData)
            if (moduleFetchInProgress) {
                binding.moduleInfo.text = "Last synced data • Updating from ERP…"
            }
        } else if (moduleFetchInProgress) {
            binding.moduleContent.removeAllViews()
            applyModuleContentPadding(module)
            binding.moduleContent.addView(Skeleton.rows(
                this,
                when (module) {
                    StudentModule.TIMETABLE -> 3
                    StudentModule.RESULTS -> 4
                    StudentModule.FEE -> 3
                    StudentModule.PROFILE -> 3
                    StudentModule.ENROLLED_COURSES -> 4
                    else -> 3
                },
                module == StudentModule.TIMETABLE
            ))
            binding.moduleProgress.visibility = View.GONE
        } else {
            binding.moduleContent.removeAllViews()
            applyModuleContentPadding(module)
            binding.moduleContent.addView(ErrorState.create(
                this,
                "Data unavailable",
                "Refresh to load the latest information from your university account.",
                ::refreshActiveModule
            ))
        }
        applyModuleNavigation()
    }

    private fun applyModuleNavigation() {
        if (!::binding.isInitialized) return
        val homeActive = binding.dashboardScroll.visibility == View.VISIBLE &&
            binding.moduleScreen.visibility != View.VISIBLE
        val timetableActive = binding.moduleScreen.visibility == View.VISIBLE && activeModule == StudentModule.TIMETABLE
        val attendanceActive = binding.moduleScreen.visibility == View.VISIBLE && activeModule == StudentModule.ATTENDANCE
        val resultsActive = binding.moduleScreen.visibility == View.VISIBLE && activeModule == StudentModule.RESULTS

        FloatingNav.apply(this, binding.dashboardBottomNav, listOf(
            FloatingNavItem(binding.navHomeButton, R.drawable.ic_pt_home, "Home", homeActive),
            FloatingNavItem(binding.navTimetableButton, R.drawable.ic_pt_calendar, "Timetable", timetableActive),
            FloatingNavItem(binding.navAttendanceButton, R.drawable.ic_pt_clipboard, "Attendance", attendanceActive),
            FloatingNavItem(binding.navResultsButton, R.drawable.ic_pt_chart, "Results", resultsActive),
            FloatingNavItem(binding.navMoreButton, R.drawable.ic_pt_menu, "More", false)
        ))

        val showNav = homeActive || timetableActive || resultsActive
        binding.dashboardBottomNav.visibility = if (showNav) View.VISIBLE else View.GONE
        if (timetableActive || resultsActive) binding.dashboardBottomNav.bringToFront()
    }

    private fun renderEnrolledCourses(data: ModuleData) {
        BackHeader.bindExisting(
            this,
            binding.homeButton,
            binding.moduleTitle,
            binding.moduleSubtitle,
            "Enrolled courses",
            enrolledCoursesRenderer.headerSubtitle(data).orEmpty()
        )
        applyModuleContentPadding(StudentModule.ENROLLED_COURSES)
        enrolledCoursesRenderer.render(binding.moduleContent, data)
    }

    private fun attendanceCourseLinks(): List<AttendanceDetailLink> {
        val data = erpRepository.cachedPayload(StudentModule.ATTENDANCE)
            ?.let(::parseModuleData)
            ?.takeIf { erpRepository.hasUsableData(StudentModule.ATTENDANCE, it) }
        return data?.attendanceDetailLinks.orEmpty()
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
        BackHeader.bindExisting(
            this,
            binding.homeButton,
            binding.moduleTitle,
            binding.moduleSubtitle,
            "Fee invoices",
            feeRenderer.headerSubtitle(data).orEmpty()
        )
        applyModuleContentPadding(StudentModule.FEE)
        feeRenderer.render(binding.moduleContent, data)
    }

    private fun addModuleIntro(module: StudentModule) {
        val title = when (module) {
            StudentModule.ATTENDANCE -> "Attendance overview"
            StudentModule.TIMETABLE -> "Class schedule"
            StudentModule.FEE -> "Invoices overview"
            StudentModule.PROFILE -> "Student profile"
            StudentModule.RESULTS -> "Academic results"
            StudentModule.ENROLLED_COURSES -> "Enrolled courses"
        }
        val subtitle = when (module) {
            StudentModule.ATTENDANCE -> "Subject wise attendance and current percentage"
            StudentModule.TIMETABLE -> "Your weekly classes and timings"
            StudentModule.FEE -> "Paid and unpaid vouchers, due dates and PDF downloads"
            StudentModule.PROFILE -> "Live student information from your ERP account"
            StudentModule.RESULTS -> "Live academic results from your ERP account"
            StudentModule.ENROLLED_COURSES -> "Your active courses, course codes, credits and semester from ERP"
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
            StudentModule.ENROLLED_COURSES -> if (index == 0) "Enrolled courses" else "Course details"
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
            val scriptKind = ModuleDataScriptSelector.select(module, view.url.orEmpty())
            val extractionScript = Scripts.moduleDataFor(module, view.url.orEmpty())
            view.evaluateJavascript(extractionScript) { result ->
                if (!loggedIn || requestId != moduleReadRequestId ||
                    (!displayWhenReady && !activityResumed)) return@evaluateJavascript

                fun handleExtraction(rawResult: String, usedGeneralFallback: Boolean) {
                    if (!loggedIn || requestId != moduleReadRequestId ||
                        (!displayWhenReady && !activityResumed)) return

                    val payload = ParsingUtils.decodeJavascriptString(rawResult)
                    val data = parseModuleData(payload)
                    val usable = data != null && erpRepository.hasUsableData(module, data)

                    if (!usable && !usedGeneralFallback &&
                        ModuleDataScriptSelector.shouldFallbackToGeneral(scriptKind)) {
                        view.evaluateJavascript(Scripts.moduleData) { fallbackResult ->
                            handleExtraction(fallbackResult, true)
                        }
                        return
                    }

                    if (usable && data != null) {
                        val isAttendanceDetail = module == StudentModule.ATTENDANCE && data.attendanceDetail != null
                        val cacheAccepted = if (isAttendanceDetail) {
                            true
                        } else {
                            val generation = moduleFetchGenerations[module]
                            if (generation != null) erpRepository.cachePayload(module, payload, generation)
                            else erpRepository.cachePayload(module, payload)
                        }
                        if (!cacheAccepted) {
                            android.util.Log.w("ERPPerf", "module_fetch module=@@{module.name} source=network outcome=stale_response_rejected")
                            if (displayWhenReady && activeModule == module) {
                                moduleFetchInProgress = false
                                binding.moduleProgress.visibility = View.GONE
                                binding.modulePullRefresh.isRefreshing = false
                                val cachedData = erpRepository.cachedPayload(module)
                                    ?.let(::parseModuleData)
                                    ?.takeIf { erpRepository.hasUsableData(module, it) }
                                if (cachedData != null) {
                                    renderModuleData(module, cachedData)
                                    binding.moduleInfo.text = "Showing last synced data"
                                } else {
                                    binding.moduleContent.removeAllViews()
                                    binding.moduleContent.addView(createEmptyState(
                                        "Couldn't refresh this section",
                                        "The latest response was older than a newer saved result. Tap Refresh to try again."
                                    ))
                                    logModuleFirstRender(module, "empty", false)
                                }
                            } else if (!displayWhenReady && preloadingModule == module) {
                                preloadingModule = null
                                preloadNextModule()
                            }
                            return
                        }
                        logModuleFetchOutcome(module, "success")
                        if (displayWhenReady && activeModule == module) {
                            moduleFetchInProgress = false
                            binding.modulePullRefresh.isRefreshing = false
                            renderModuleData(module, data)
                        } else if (!displayWhenReady && preloadingModule == module) {
                            preloadingModule = null
                            preloadNextModule()
                        }
                    } else if (displayWhenReady && activeModule == module) {
                        if (moduleReadAttempts < MAX_MODULE_READ_ATTEMPTS) {
                            binding.moduleInfo.text = "Waiting for the ERP data…"
                            handler.postDelayed({ readModuleData(view, module, true) }, MODULE_RETRY_DELAY_MS)
                            return
                        }

                        moduleFetchInProgress = false
                        binding.moduleProgress.visibility = View.GONE
                        binding.modulePullRefresh.isRefreshing = false
                        logModuleFetchOutcome(module, "unusable_response")
                        val cachedData = erpRepository.cachedPayload(module)
                            ?.let { parseModuleData(it) }
                            ?.takeIf { erpRepository.hasUsableData(module, it) }
                        if (cachedData != null) {
                            binding.moduleInfo.text = "Couldn't refresh • Showing last synced data"
                        } else {
                            binding.moduleInfo.text = "No readable data returned"
                            binding.moduleContent.removeAllViews()
                            if (isRedesignedModule(module)) {
                                binding.moduleContent.addView(ErrorState.create(
                                    this,
                                    "No readable data returned",
                                    "The ERP page loaded, but its data is not ready yet. Retry to try again.",
                                    ::refreshActiveModule
                                ))
                            } else {
                                binding.moduleContent.addView(createEmptyState(
                                    "No readable data returned",
                                    "The ERP page loaded, but its data is not ready yet. Tap Refresh to try again."
                                ))
                            }
                        }
                    } else if (!displayWhenReady && preloadingModule == module) {
                        if (moduleReadAttempts < PRELOAD_READ_ATTEMPTS) {
                            handler.postDelayed({ readModuleData(view, module, false) }, MODULE_RETRY_DELAY_MS)
                            return
                        }
                        logModuleFetchOutcome(module, "unusable_response")
                        preloadingModule = null
                        preloadNextModule()
                    }
                }

                handleExtraction(result, false)
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
        renderHomeDashboard()
    }

    private fun renderHomeDashboard() {
        if (!::binding.isInitialized) return

        val fullName = profileCache.studentName.takeIf { ParsingUtils.isValidStudentName(it) }.orEmpty()
        PageHeader.bind(this, binding.homeDateLine, binding.studentName, binding.studentAvatarInitial, fullName)
        renderMoreProfileCard()

        val attendanceData = erpRepository.cachedPayload(StudentModule.ATTENDANCE)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(StudentModule.ATTENDANCE, it) }
        val attendanceSummary = attendanceData?.let { attendanceRenderer.summaryPercentageForHome(it) }
        val attendancePercent = attendanceSummary?.percentage
            ?.takeIf { HomeDisplayRules.attendanceBand(it) != null }
        val cgpa = profileCache.cgpa.takeIf { ParsingUtils.validGpa(it).isNotBlank() }
            ?.let { ParsingUtils.validGpa(it) }.orEmpty()
        val isLoading = loggedIn && !homePreloadComplete

        binding.homeHeroContainer.removeAllViews()
        val hero = when {
            isLoading -> Skeleton.hero(this)
            attendancePercent != null -> {
                val counts = attendanceData?.let { findOverallAttendanceCounts(it, attendancePercent) }
                val canMiss = counts?.let { pair ->
                    AttendanceCalculator.calculate(pair.first, pair.second)?.safeFutureLeaves
                }
                HeroCard.attendance(this, attendancePercent, canMiss).also { card ->
                    card.setOnClickListener { loadModule(StudentModule.ATTENDANCE) }
                    attachPressFeedback(card)
                }
            }
            else -> HeroCard.cgpa(this, cgpa).also { card ->
                card.setOnClickListener { loadModule(StudentModule.RESULTS) }
                attachPressFeedback(card)
            }
        }
        binding.homeHeroContainer.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        renderHomeNextClassCard()

        val feeData = erpRepository.cachedPayload(StudentModule.FEE)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(StudentModule.FEE, it) }
        val dueInvoice = feeData?.let { HomeDisplayRules.feeDue(it.invoices) }
        val dueDate = dueInvoice?.let { HomeDisplayRules.dueDateLabel(it.dueDate) }
        val payable = dueInvoice?.let { HomeDisplayRules.payableLabel(it.payable) }
        val showFeeDue = dueInvoice != null && !dueDate.isNullOrBlank() && !payable.isNullOrBlank()
        val overdue = showFeeDue && dueInvoice?.let { HomeDisplayRules.isPastDue(it.dueDate) } == true
        val feeCaption = dueInvoice?.takeIf { showFeeDue }?.let { invoice ->
            if (overdue) "Overdue since " + HomeDisplayRules.dueDateFullLabel(invoice.dueDate).orEmpty()
            else "by " + dueDate.orEmpty()
        }.orEmpty()
        val sgpa = profileCache.sgpa.takeIf { ParsingUtils.validGpa(it).isNotBlank() }
            ?.let { ParsingUtils.validGpa(it) }.orEmpty()
        // Attendance-free Home already uses the large CGPA hero; put SGPA and (when valid) fees in the bento row.
        val cgpaHeroShown = !isLoading && attendancePercent == null
        BentoCard.bind(
            this, binding.cgpaCard, binding.studentCgpaLabel, binding.studentCgpa, binding.studentCgpaCaption,
            if (cgpaHeroShown) "SGPA" else "CGPA",
            if (cgpaHeroShown) sgpa else cgpa,
            "",
            if (cgpaHeroShown) Palette.sky(this) else Palette.lav(this),
            isLoading,
            38f
        )
        binding.cgpaCard.visibility = if (cgpaHeroShown && sgpa.isBlank() && !isLoading) View.GONE else View.VISIBLE

        BentoCard.bind(
            this, binding.sgpaCard, binding.studentSgpaLabel, binding.studentSgpa, binding.studentSgpaCaption,
            if (showFeeDue) if (overdue) "Overdue" else "Fee due" else "SGPA",
            if (showFeeDue) payable.orEmpty() else sgpa,
            feeCaption,
            if (overdue) Palette.badBg(this) else Palette.sky(this),
            isLoading,
            if (showFeeDue) 23f else 38f
        )
        binding.sgpaCard.visibility = if (cgpaHeroShown && !showFeeDue && !isLoading) View.GONE else View.VISIBLE
        if (overdue) {
            binding.studentSgpaLabel.setTextColor(Palette.bad(this))
            binding.studentSgpa.setTextColor(Palette.bad(this))
            binding.studentSgpaCaption.setTextColor(Palette.bad(this))
        }

        ListCard.bind(this, binding.timetableButton, R.drawable.ic_pt_calendar, "Timetable")
        ListCard.bind(this, binding.resultsButton, R.drawable.ic_pt_chart, "Results")
        ListCard.bind(this, binding.enrolledCoursesButton, R.drawable.ic_pt_book, "Courses")
        ListCard.bind(this, binding.profileButton, R.drawable.ic_pt_user, "Profile")
        binding.attendanceButton.visibility = View.GONE
        binding.feeButton.visibility = View.GONE
    }

    private fun renderHomeNextClassCard() {
        val container = binding.homeNextClassContainer
        container.visibility = View.GONE
        val data = erpRepository.cachedPayload(StudentModule.TIMETABLE)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(StudentModule.TIMETABLE, it) }
            ?: return
        val rows = TimetableParser.toRows(data.records)
            .filter { it.time.isNotBlank() && it.course.isNotBlank() }
            .distinctBy { it.day + "|" + it.time + "|" + it.course + "|" + it.code }
        if (rows.isEmpty()) return

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
        val next = NextClassSelector.selectNext(rows, currentDay, currentMinutes) ?: return
        if (next.course.isBlank()) return
        val startMinutes = ParsingUtils.startMinutesOf(next.time) ?: return
        val weekday = mapOf(
            "Monday" to 1, "Tuesday" to 2, "Wednesday" to 3, "Thursday" to 4,
            "Friday" to 5, "Saturday" to 6, "Sunday" to 7
        )
        val dayNumber = weekday.entries.firstOrNull { it.key.equals(next.day, true) }?.value ?: return
        val todayNumber = weekday.entries.firstOrNull { it.key.equals(currentDay, true) }?.value ?: return
        var daysUntil = (dayNumber - todayNumber + 7) % 7
        if (daysUntil == 0 && startMinutes < currentMinutes) daysUntil = 7
        val minutesUntil = (daysUntil * 1440 + startMinutes - currentMinutes).coerceAtLeast(0)
        val timeText = ParsingUtils.formatScheduleTime(next.time)
            .replace("\n–\n", " – ").replace("\n", " ").trim()
        val startTimeText = timeText.substringBefore("–").trim().ifBlank { timeText }
        val dayLabel = when {
            daysUntil == 0 -> "Today"
            daysUntil == 1 -> "Tomorrow"
            else -> next.day.take(3).replaceFirstChar { it.uppercase() }
        }
        val nextClassLabel = if (minutesUntil > 100) {
            "$dayLabel, $startTimeText"
        } else if (minutesUntil == 0) {
            "Next class · starting now"
        } else {
            "Next class · in $minutesUntil min"
        }

        container.visibility = View.VISIBLE
        container.background = roundedBackground(Palette.peach(this), 24f)
        binding.nextClassLabel.text = nextClassLabel
        binding.homeNextClassTitle.text = ParsingUtils.cleanDisplayText(next.course)
        binding.homeNextClassTitle.maxLines = 2
        binding.homeNextClassTitle.isSingleLine = false
        binding.homeNextClassTime.text = timeText
        binding.homeNextClassTitle.setTextColor(Palette.ink(this))
        binding.homeNextClassTitle.typeface =
            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.poppins_medium) ?: typeface
        binding.nextClassLabel.setTextColor(Palette.ink2(this))
        binding.homeNextClassTime.setTextColor(Palette.ink2(this))
        binding.homeNextClassIcon.background = roundedBackground(Palette.ink(this), 999f)
        val arrow = binding.homeNextClassIcon.getChildAt(0) as? android.widget.ImageView
        arrow?.imageTintList = ColorStateList.valueOf(Palette.bg(this))
        container.setOnClickListener { loadModule(StudentModule.TIMETABLE) }
        attachPressFeedback(container)
    }

    /** Uses aggregate ERP counts only; a mean of subject percentages is not an overall total. */
    private fun findOverallAttendanceCounts(data: ModuleData, expectedPercentage: Double): Pair<Int, Int>? {
        var attended: Int? = null
        var conducted: Int? = null

        fun accept(label: String, rawValue: String) {
            val normalized = label.lowercase(java.util.Locale.US)
            val aggregate = normalized.contains("overall") || normalized.contains("total") || normalized.contains("aggregate")
            if (!aggregate) return
            val count = Regex("""\d+""").find(rawValue)?.value?.toIntOrNull() ?: return
            when {
                normalized.contains("conducted") -> conducted = count
                normalized.contains("attended") || normalized.contains("present") -> attended = count
            }
        }

        data.cards.forEach { (label, value) -> accept(label, value) }
        data.records.forEach { record ->
            record.headers.forEachIndexed { index, label ->
                record.values.getOrNull(index)?.let { accept(label, it) }
            }
        }
        data.lines.forEach { line -> accept(line, line) }
        val present = attended ?: return null
        val total = conducted ?: return null
        if (total <= 0 || present !in 0..total) return null
        val computed = present.toDouble() * 100.0 / total
        // Only attach the miss-count forecast when the aggregate counts agree with the hero percentage.
        if (kotlin.math.abs(computed - expectedPercentage) > 0.15) return null
        return present to total
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
        if (!hasInternetAvailable()) {
            setOfflineMode(true)
            return
        }
        if (!ErpConfig.belongsToErp(webView.url.orEmpty())) {
            revalidateSessionQuietly()
            return
        }
        webView.evaluateJavascript(Scripts.sessionHeartbeat) { result ->
            if (!loggedIn) return@evaluateJavascript
            val state = ParsingUtils.decodeJavascriptString(result)
            when (state) {
                "EXPIRED" -> handleSessionExpired()
                "OK" -> setOfflineMode(false)
                "OFFLINE" -> {
                    if (!hasInternetAvailable()) setOfflineMode(true)
                    // Probe/network errors never clear the saved session.
                }
            }
        }
    }

    private fun handleModuleLoadFailure(message: String) {
        val module = activeModule ?: return
        moduleFetchInProgress = false
        binding.moduleProgress.visibility = View.GONE
        binding.modulePullRefresh.isRefreshing = false
        logModuleFetchOutcome(module, "network_error")
        val cachedData = erpRepository.cachedPayload(module)
            ?.let { parseModuleData(it) }
            ?.takeIf { erpRepository.hasUsableData(module, it) }

        if (cachedData != null) {
            // Keep the current last-known-good content interactive during outages.
            binding.moduleInfo.text = "Offline • Showing last synced data"
        } else {
            binding.moduleInfo.text = "Connection needs retry"
            binding.moduleContent.removeAllViews()
            if (isRedesignedModule(module)) {
                binding.moduleContent.addView(ErrorState.create(
                    this,
                    "Couldn't connect to the ERP",
                    message,
                    ::refreshActiveModule
                ))
            } else {
                binding.moduleContent.addView(createEmptyState(
                    "Couldn't connect to the ERP",
                    message
                ))
            }
        }
    }

    private fun handleSessionExpired() {
        cancelParallelPrefetch()
        if (!(loggedIn || restoringSession || sessionStore.sessionActive)) return
        if (!hasInternetAvailable()) {
            keepCachedSessionAfterTransientFailure("Offline - showing saved data")
            return
        }
        if (silentReloginInProgress) return
        if (sessionRetryAttempted) {
            rejectSilentRelogin()
            return
        }
        val credentials = savedCredentialsStore.load()
        if (credentials == null) {
            loggedIn = false
            sessionStore.sessionActive = false
            showLogin()
            binding.loginStatus.text = "Your university session expired. Please sign in again."
            styleLoginStatus()
            return
        }
        beginSilentRelogin(credentials.username, credentials.password)
    }

    /**
     * Applies the selected accent and dark surfaces to the existing native views.
     * Data/rendering components still use the same Palette API, so ERP logic stays untouched.
     */
    private fun applySelectedTheme() {
        if (!::binding.isInitialized) return
        val background = Palette.bg(this)
        val surface = Palette.card(this)
        val primary = Palette.primary(this)

        binding.root.setBackgroundColor(background)
        applyLoginThemeStyles()
        binding.dashboardScroll.setBackgroundColor(background)
        binding.dashboardScreen.setBackgroundColor(background)
        binding.dashboardHeader.setBackgroundColor(background)
        binding.welcomeCard.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        binding.moduleScreen.setBackgroundColor(background)
        binding.moduleScroll.setBackgroundColor(background)
        binding.moduleContent.setBackgroundColor(background)

        binding.menuButton.visibility = View.GONE
        binding.quickAccessTitle.visibility = View.GONE
        binding.cgpaCard.background = roundedBackground(Palette.lav(this), 24f)
        binding.sgpaCard.background = roundedBackground(Palette.sky(this), 24f)
        binding.studentName.setTextColor(Palette.ink(this))
        binding.homeDateLine.setTextColor(Palette.ink2(this))
        binding.studentAvatarInitial.background = roundedBackground(Palette.lime(this), 999f)
        binding.studentAvatarInitial.setTextColor(Palette.limeInk(this))
        binding.studentCgpaLabel.setTextColor(Palette.ink2(this))
        binding.studentSgpaLabel.setTextColor(Palette.ink2(this))
        binding.studentCgpa.setTextColor(Palette.ink(this))
        binding.studentSgpa.setTextColor(Palette.ink(this))
        binding.nextClassLabel.setTextColor(Palette.ink2(this))
        binding.homeNextClassTitle.setTextColor(Palette.ink(this))
        binding.homeNextClassTime.setTextColor(Palette.ink2(this))

        applyModuleNavigation()

        applyMoreThemeStyles()

        binding.menuButton.background = themedCard(tintAlpha(primary, 30), 15f)
        binding.menuButton.imageTintList = ColorStateList.valueOf(Palette.ink(this))
        binding.homeButton.background = themedCard(tintAlpha(primary, 22), 15f)
        binding.homeButton.imageTintList = ColorStateList.valueOf(Palette.primaryLight(this))
        binding.refreshButton.background = themedCard(tintAlpha(primary, 22), 15f)
        binding.refreshButton.imageTintList = ColorStateList.valueOf(Palette.primaryLight(this))

        val moduleToolbar = (binding.moduleTitle.parent as? View)?.parent as? View
        // Keep all module headers on the page background, without a white strip or divider.
        moduleToolbar?.background = android.graphics.drawable.ColorDrawable(background)
        moduleToolbar?.elevation = 0f
        binding.moduleTitle.setTextColor(Palette.ink(this))
        binding.moduleSubtitle.setTextColor(Palette.ink2(this))
        when (activeModule) {
            StudentModule.TIMETABLE ->
                PageHeader.bindPage(this, binding.moduleTitle, binding.moduleSubtitle, "Timetable", timetableDateSubtitle())
            StudentModule.RESULTS ->
                PageHeader.bindPage(this, binding.moduleTitle, binding.moduleSubtitle, "Results", binding.moduleSubtitle.text?.toString().orEmpty().ifBlank { "Academic results" })
            else -> Unit
        }
        binding.moduleInfo.background = themedCard(Palette.infoBg(this), 12f)
        binding.moduleInfo.setTextColor(Palette.primaryLight(this))
        binding.moduleProgress.indeterminateTintList = ColorStateList.valueOf(primary)

        binding.loginButton.background = gradientBackground(Palette.lime(this), Palette.lime(this), 16f)
        binding.loginButton.setTextColor(Palette.limeInk(this))
        binding.passwordVisibilityToggle.setTextColor(Palette.ink(this))
        if (binding.sideMenuOverlay.visibility == View.VISIBLE) applyMoreNavigation()

        if (binding.dashboardScroll.visibility == View.VISIBLE || binding.moduleScreen.visibility == View.VISIBLE) {
            applySystemBarStyle(
                statusColor = background,
                navigationColor = background,
                lightStatusIcons = Palette.isNight(this),
                lightNavigationIcons = Palette.isNight(this)
            )
        }
    }

    private fun themedCard(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable {
        return roundedBackground(color, radiusDp).apply {
            setStroke(dp(1), Palette.stroke(this@MainActivity))
        }
    }

    private fun styleTextAndIcons(view: View) {
        when (view) {
            is TextView -> {
                val label = view.text?.toString()?.trim()?.lowercase().orEmpty()
                val textSp = view.textSize / resources.displayMetrics.scaledDensity
                val color = when {
                    label.contains("sign out") || label.contains("log out") -> Palette.danger(this)
                    textSp <= 10.5f -> Palette.ink500(this)
                    else -> Palette.ink900(this)
                }
                view.setTextColor(color)
            }
            is android.widget.ImageView -> {
                view.imageTintList = ColorStateList.valueOf(Palette.primaryLight(this))
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) styleTextAndIcons(view.getChildAt(index))
        }
    }

    private fun styleNavigationItem(view: View, selected: Boolean) {
        val primary = Palette.primary(this)
        view.background = if (selected) {
            roundedBackground(tintAlpha(primary, 28), 14f).apply {
                setStroke(dp(1), Palette.stroke(this@MainActivity))
            }
        } else {
            roundedBackground(Color.TRANSPARENT, 14f)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            when (child) {
                is android.widget.ImageView -> {
                    child.imageTintList = ColorStateList.valueOf(
                        if (selected) Palette.primaryLight(this) else Palette.ink500(this)
                    )
                }
                is TextView -> child.setTextColor(
                    if (selected) Palette.primaryLight(this) else Palette.ink500(this)
                )
            }
        }
    }

    private fun topRoundedBackground(color: Int, radiusDp: Float): android.graphics.drawable.GradientDrawable {
        val radius = radiusDp * resources.displayMetrics.density
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setStroke(dp(1), Palette.line(this@MainActivity))
        }
    }

    private fun applyLoginThemeStyles() {
        if (!::binding.isInitialized) return
        binding.loginScroll.setBackgroundColor(Color.rgb(11, 61, 46))
        binding.loginBackground.visibility = View.GONE
        binding.loginFormCard.background = topRoundedBackground(Palette.bg(this), 32f)
        binding.loginHeading.setTextColor(Palette.ink(this))
        binding.loginSubtitle.setTextColor(Palette.ink2(this))
        binding.loginFooterNotice.setTextColor(Palette.ink3(this))
        binding.loginFooterCredit.setTextColor(Palette.ink2(this))
        binding.loginSecureText.setTextColor(Palette.ink2(this))
        binding.loginSecureIcon.imageTintList = ColorStateList.valueOf(Palette.ink2(this))
        binding.usernameInput.setTextColor(Palette.ink(this))
        binding.usernameInput.setHintTextColor(Palette.ink3(this))
        binding.usernameInput.background = themedCard(Palette.card(this), 16f)
        binding.usernameInput.compoundDrawableTintList = ColorStateList.valueOf(Palette.ink2(this))
        binding.passwordInput.setTextColor(Palette.ink(this))
        binding.passwordInput.setHintTextColor(Palette.ink3(this))
        binding.passwordInput.background = themedCard(Palette.card(this), 16f)
        binding.passwordInput.compoundDrawableTintList = ColorStateList.valueOf(Palette.ink2(this))
        binding.passwordVisibilityToggle.setTextColor(Palette.ink(this))
        binding.loginButton.background = roundedBackground(Palette.lime(this), 16f)
        binding.loginButton.setTextColor(Palette.limeInk(this))
        binding.loginButton.minHeight = dp(54)
        binding.loginButton.minimumHeight = dp(54)
        styleLoginStatus()
    }

    private fun styleLoginStatus() {
        if (!::binding.isInitialized) return
        val status = binding.loginStatus
        val message = status.text?.toString()?.trim().orEmpty()
        val error = message.isNotBlank() &&
            !message.startsWith("Signing in", ignoreCase = true) &&
            !message.startsWith("Verifying your account", ignoreCase = true)
        status.setTextColor(if (error) Palette.bad(this) else Palette.ink2(this))
        status.background = if (error) roundedBackground(Palette.badBg(this), 10f)
            else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        val horizontal = if (error) dp(12) else 0
        val vertical = if (error) dp(9) else 0
        status.setPadding(horizontal, vertical, horizontal, vertical)
    }

    private fun applyMoreThemeStyles() {
        if (!::binding.isInitialized) return
        binding.sideMenu.setBackgroundColor(Palette.bg(this))
        binding.moreContentColumn.setBackgroundColor(Palette.bg(this))
        binding.moreTitle.setTextColor(Palette.ink(this))
        binding.moreTitlePercent.setTextColor(Palette.lime(this))
        binding.moreConnectSubtitle.setTextColor(Palette.ink2(this))
        binding.menuProfileCard.background = themedCard(Palette.card(this), 22f)
        binding.menuProfileAvatar.background = roundedBackground(Palette.lime(this), 999f)
        binding.menuProfileAvatar.setTextColor(Palette.limeInk(this))
        binding.menuProfileChevron.imageTintList = ColorStateList.valueOf(Palette.ink3(this))
        binding.moreAccountCard.background = themedCard(Palette.card(this), 22f)
        binding.moreConnectCard.background = themedCard(Palette.card(this), 22f)
        binding.moreLogoutCard.background = themedCard(Palette.card(this), 16f)
        binding.themeSelector.background = themedCard(Palette.card(this), 22f)
        binding.menuInvoicesIconBox.background = roundedBackground(Palette.sky(this), 12f)
        binding.menuCoursesIconBox.background = roundedBackground(Palette.peach(this), 12f)
        binding.menuResultsIconBox.background = roundedBackground(Palette.lav(this), 12f)
        binding.menuStudentConnectIconBox.background = roundedBackground(Palette.mint(this), 12f)
        listOf(
            binding.menuInvoicesIcon, binding.menuCoursesIcon, binding.menuResultsIcon,
            binding.menuStudentConnectIcon
        ).forEach { it.imageTintList = ColorStateList.valueOf(Palette.ink2(this)) }
        binding.menuLogoutIcon.imageTintList = ColorStateList.valueOf(Palette.bad(this))
        binding.moreDividerInvoice.setBackgroundColor(Palette.line(this))
        binding.moreDividerCourses.setBackgroundColor(Palette.line(this))
        binding.menuSettingsButton.visibility = View.GONE

        styleTextAndIcons(binding.sideMenu)

        // Restore semantic icon colours after the generic fallback styling pass.
        val menuIconTint = ColorStateList.valueOf(Palette.ink(this))
        val menuChevronTint = ColorStateList.valueOf(Palette.ink3(this))
        listOf(
            binding.menuInvoicesIcon, binding.menuCoursesIcon,
            binding.menuResultsIcon, binding.menuStudentConnectIcon
        ).forEach { it.imageTintList = menuIconTint }
        binding.menuProfileChevron.imageTintList = menuChevronTint
        listOf(
            binding.menuInvoicesRow, binding.menuCoursesRow,
            binding.menuResultsRow, binding.moreConnectCard
        ).forEach { row ->
            (row.getChildAt(row.childCount - 1) as? ImageView)?.imageTintList = menuChevronTint
        }

        binding.menuProfileAvatar.background = roundedBackground(Palette.lime(this), 999f)
        binding.menuProfileAvatar.setTextColor(Palette.limeInk(this))
        binding.menuProfileButton.setTextColor(Palette.ink(this))
        binding.menuProfileSubtitle.setTextColor(Palette.ink2(this))
        binding.menuStudentConnectButton.setTextColor(Palette.ink(this))
        binding.menuEnrolledCoursesButton.setTextColor(Palette.ink(this))
        binding.menuInvoicesButton.setTextColor(Palette.ink(this))
        binding.menuResultsButton.setTextColor(Palette.ink(this))
        binding.menuLogoutButton.setTextColor(Palette.bad(this))
        binding.menuLogoutIcon.imageTintList = ColorStateList.valueOf(Palette.bad(this))
        binding.closeMenuButton.background = themedCard(Palette.card(this), 15f)
        binding.closeMenuButton.imageTintList = ColorStateList.valueOf(Palette.ink(this))

        val selectedTheme = AppThemePreferences.current(this)
        val buttons = listOf(
            binding.themePaperButton to AppTheme.PAPER,
            binding.themeNightButton to AppTheme.NIGHT,
            binding.themeAutoButton to AppTheme.AUTO
        )
        buttons.forEach { (button, theme) ->
            val selected = selectedTheme == theme
            button.background = if (selected) roundedBackground(Palette.segment(this), 18f)
                else roundedBackground(Color.TRANSPARENT, 18f)
            button.setTextColor(if (selected) Palette.segmentInk(this) else Palette.ink2(this))
            button.contentDescription = if (selected) theme.title + ", selected appearance" else "Use " + theme.title + " appearance"
        }
    }

    private fun renderMoreProfileCard() {
        if (!::binding.isInitialized) return
        val fullName = profileCache.studentName
            .takeIf { ParsingUtils.isValidStudentName(it) }.orEmpty()
        binding.menuProfileButton.text = fullName.ifBlank { "My Profile" }
        val studentId = username.trim()
        binding.menuProfileSubtitle.text = studentId
        binding.menuProfileSubtitle.visibility = if (studentId.isBlank()) View.GONE else View.VISIBLE
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val initials = when {
            parts.size >= 2 -> parts.first().first().toString() + parts.last().first().toString()
            parts.size == 1 -> parts.first().take(1)
            else -> "S"
        }.uppercase(java.util.Locale.getDefault())
        binding.menuProfileAvatar.text = initials
        binding.menuProfileAvatar.contentDescription = if (fullName.isBlank()) "Student profile" else fullName + " initials"
        binding.menuProfileChevron.imageTintList = ColorStateList.valueOf(Palette.ink3(this))
    }

    private fun updateMoreContentInsets() {
        if (!::binding.isInitialized) return
        val bottomInset = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val side = dp(16)
        binding.moreContentColumn.setPadding(side, 0, side, dp(74) + bottomInset)
    }

    private fun applyMoreNavigation() {
        if (!::binding.isInitialized) return
        FloatingNav.apply(this, binding.dashboardBottomNav, listOf(
            FloatingNavItem(binding.navHomeButton, R.drawable.ic_pt_home, "Home", false),
            FloatingNavItem(binding.navTimetableButton, R.drawable.ic_pt_calendar, "Timetable", false),
            FloatingNavItem(binding.navAttendanceButton, R.drawable.ic_pt_clipboard, "Attendance", false),
            FloatingNavItem(binding.navResultsButton, R.drawable.ic_pt_chart, "Results", false),
            FloatingNavItem(binding.navMoreButton, R.drawable.ic_pt_menu, "More", true)
        ))
        binding.dashboardBottomNav.visibility = View.VISIBLE
        binding.dashboardBottomNav.bringToFront()
    }

    private fun openStudentConnectFromMenu() {
        if (!loggedIn) {
            Toast.makeText(this, "Please sign in to your university account first.", Toast.LENGTH_LONG).show()
        } else {
            startActivity(Intent(this, com.superiorstudent.app.feature.studentconnect.StudentConnectActivity::class.java))
        }
    }

    private fun selectAppearance(theme: AppTheme) {
        val changed = AppThemePreferences.current(this) != theme
        AppThemePreferences.save(this, theme)
        AppThemePreferences.applyToApp(this)
        applySelectedTheme()
        renderHomeDashboard()
        rerenderBatchModuleForTheme()
        if (binding.sideMenuOverlay.visibility == View.VISIBLE) {
            renderMoreProfileCard()
            updateMoreContentInsets()
            applyMoreThemeStyles()
            applyMoreNavigation()
        }
        if (changed) showToastAboveNavigation(theme.title + " theme applied", Toast.LENGTH_SHORT)
    }

    private fun showLogoutConfirmation() {
        val dialog = android.app.Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = themedCard(Palette.card(this@MainActivity), 24f)
        }
        fun label(textValue: String, size: Float, color: Int, medium: Boolean = false): TextView =
            TextView(this).apply {
                text = textValue
                textSize = size
                setTextColor(color)
                includeFontPadding = false
                typeface = androidx.core.content.res.ResourcesCompat.getFont(
                    this@MainActivity, if (medium) R.font.poppins_medium else R.font.poppins
                )
            }
        content.addView(label("Log out?", 21f, Palette.ink(this), true))
        content.addView(label(
            "You’ll need to sign in again to access your student account.",
            13f, Palette.ink2(this)
        ).apply { setPadding(0, dp(8), 0, dp(18)) })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cancel = label("Cancel", 14f, Palette.ink(this), true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            background = roundedBackground(Palette.bg(this@MainActivity), 14f)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        val confirm = label("Log out", 14f, Palette.bad(this), true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            background = roundedBackground(Palette.badBg(this@MainActivity), 14f)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss(); logout() }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        actions.addView(confirm, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        content.addView(actions)
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.46f)
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showThemePicker() {
        val dialog = android.app.Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = themedCard(Palette.surface(this@MainActivity), 24f)
        }

        fun label(textValue: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
            return TextView(this).apply {
                text = textValue
                textSize = sizeSp
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }

        content.addView(label("Choose your look", 21f, Palette.ink900(this), true))
        content.addView(label(
            "Choose a colour style for your student dashboard.",
            12f,
            Palette.ink500(this)
        ).apply {
            setPadding(0, dp(5), 0, dp(12))
        })

        val current = AppThemePreferences.current(this)
        AppTheme.values().forEach { theme ->
            val selected = current == theme
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = roundedBackground(
                    if (selected) tintAlpha(Palette.primary(this@MainActivity), 24)
                    else Palette.surfaceAlt(this@MainActivity),
                    16f
                ).apply {
                    setStroke(
                        dp(1),
                        if (selected) Palette.primary(this@MainActivity)
                        else Palette.stroke(this@MainActivity)
                    )
                }
                isClickable = true
                isFocusable = true
            }

            val swatch = View(this).apply {
                background = gradientBackground(
                    Color.parseColor(theme.previewStartHex),
                    Color.parseColor(theme.previewEndHex),
                    12f
                )
            }
            row.addView(swatch, LinearLayout.LayoutParams(dp(42), dp(42)))

            val copy = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(12) }
            }
            copy.addView(label(theme.title, 15f, Palette.ink900(this), true))
            copy.addView(label(theme.description, 11f, Palette.ink500(this)).apply {
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(copy)

            row.addView(label(
                if (selected) "SELECTED" else "APPLY",
                10f,
                if (selected) Palette.primaryLight(this) else Palette.ink500(this),
                true
            ))

            row.setOnClickListener {
                val changed = AppThemePreferences.current(this) != theme
                AppThemePreferences.save(this, theme)
                dialog.dismiss()
                AppThemePreferences.applyToApp(this)
                applySelectedTheme()
                renderHomeDashboard()
                rerenderBatchModuleForTheme()
                if (changed) showToastAboveNavigation(theme.title + " theme applied", Toast.LENGTH_SHORT)
            }

            content.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }

        content.addView(label(
            "Your choice is saved on this device, even after signing out.",
            10f,
            Palette.ink500(this)
        ).apply { setPadding(0, dp(14), 0, 0) })

        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.56f)
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun openSideMenu() {
        if (!loggedIn || binding.dashboardScroll.visibility != View.VISIBLE) return
        try {
            renderMoreProfileCard()
            updateMoreContentInsets()
            binding.sideMenuOverlay.bringToFront()
            binding.sideMenuOverlay.visibility = View.VISIBLE
            binding.sideMenu.bringToFront()
            applyMoreNavigation()
            applySystemBarStyle(
                statusColor = Palette.bg(this),
                navigationColor = Palette.bg(this),
                lightStatusIcons = Palette.isNight(this),
                lightNavigationIcons = Palette.isNight(this)
            )
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "More destination failed to open", t)
            try { binding.sideMenuOverlay.visibility = View.GONE } catch (ignored: Throwable) { }
        }
    }

    private fun closeSideMenu() {
        if (::binding.isInitialized) {
            binding.sideMenuOverlay.visibility = View.GONE
            applyModuleNavigation()
            applySystemBarStyle(
                statusColor = Palette.bg(this),
                navigationColor = Palette.bg(this),
                lightStatusIcons = Palette.isNight(this),
                lightNavigationIcons = Palette.isNight(this)
            )
        }
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
        cancelParallelPrefetch()
        activeAttendanceDetailUrl = null
        activeModule = null
        if (::webView.isInitialized) webView.stopLoading()
        webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.moduleContent.removeAllViews()
        moduleFetchInProgress = false
        moduleReadRequestId++
        binding.dashboardScroll.visibility = View.VISIBLE
        binding.loginScroll.visibility = View.GONE
        binding.sideMenuOverlay.visibility = View.GONE
        applySelectedTheme()
        binding.dashboardBottomNav.visibility = View.VISIBLE
        applySystemBarStyle(
            statusColor = Palette.bg(this),
            navigationColor = Palette.bg(this),
            lightStatusIcons = Palette.isNight(this),
            lightNavigationIcons = Palette.isNight(this)
        )
        val dashboardRenderStartedAt = android.os.SystemClock.elapsedRealtime()
        renderHomeDashboard()
        val dashboardRenderElapsed = (android.os.SystemClock.elapsedRealtime() - dashboardRenderStartedAt).coerceAtLeast(0L)
        android.util.Log.i(
            "ERPPerf",
            "screen_first_render module=DASHBOARD elapsed_ms=$dashboardRenderElapsed source=cache_or_native success=true"
        )
        setOfflineMode(offlineMode || !hasInternetAvailable())
        // If opening a module interrupted a previous preload, resume missing or stale ERP modules on return.
        preloadAllModules()
    }

    private fun showToastAboveNavigation(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        if (::binding.isInitialized &&
            binding.dashboardScroll.visibility == View.VISIBLE &&
            binding.dashboardBottomNav.visibility == View.VISIBLE
        ) {
            val navHeight = binding.dashboardBottomNav.height.takeIf { it > 0 } ?: dp(58)
            val bottomInset = ViewCompat.getRootWindowInsets(binding.root)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, bottomInset + navHeight + dp(32))
        }
        toast.show()
    }

    private fun showLogin() {
        cancelParallelPrefetch()
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
        webView.stopLoading()
        webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
        binding.dashboardBottomNav.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.loginScroll.visibility = View.VISIBLE
        binding.sideMenuOverlay.visibility = View.GONE
        applySystemBarStyle(
            statusColor = Color.rgb(11, 61, 46),
            navigationColor = Palette.bg(this),
            lightStatusIcons = true,
            lightNavigationIcons = Palette.isNight(this)
        )
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        binding.loginStatus.text = ""
        styleLoginStatus()
    }

    private fun logout() {
        cancelParallelPrefetch()
        moduleFetchGenerations.clear()
        moduleFetchStartedAtMs.clear()
        lastModuleFetchAttemptAtElapsedMs.clear()
        savedCredentialsStore.clear()
        sessionStore.clear()
        profileCache.clear()
        erpRepository.clearAccountIdentity()
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
        if (silentReloginInProgress) {
            keepCachedSessionAfterTransientFailure(message)
            return
        }
        loginInProgress = false
        loginSubmitted = false
        loginProbeInProgress = false
        loginProbeAttempts = 0
        loginAttempt = 0
        password = ""
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        binding.loginStatus.text = message
        styleLoginStatus()
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
        private const val WEBVIEW_WARMUP_DELAY_MS = 350L
        private const val PARALLEL_PREFETCH_POLL_INTERVAL_MS = 300L
        private const val PARALLEL_PREFETCH_JOB_TIMEOUT_MS = 18000
        private const val PARALLEL_PREFETCH_TOTAL_TIMEOUT_MS = 23000L
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
