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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
        try {
            safeOnCreate(savedInstanceState)
        } catch (t: Throwable) {
            try {
                recoverFromStartupCrash(t)
            } catch (inner: Throwable) {
                android.util.Log.e("MainActivity", "Unrecoverable startup failure", inner)
                showFatalStartupView("Superior Student could not start.\n\nWebView initialization failed. Update Android System WebView/Chrome and reopen the app.")
            }
        }
    }

    private var startupRecovered = false

    private fun recoverFromStartupCrash(t: Throwable) {
        android.util.Log.e("MainActivity", "Startup failed; wiping local state and retrying", t)
        if (startupRecovered) {
            android.util.Log.e("MainActivity", "Second startup attempt failed. Keeping activity alive for diagnosis.", t)
            showFatalStartupView(
                "Superior Student could not start correctly.\n\n" +
                    "WebView initialization failed. Update Android System WebView and Chrome, then reopen the app."
            )
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
        } catch (_: Throwable) {
        }
        onCreate(null)
    }

    private fun safeOnCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Some Android builds have provider-specific WebView compatibility
        // problems. Do not call getCurrentWebViewPackage() here; simply create
        // the WebView and handle provider exceptions in the outer startup guard.
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
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebViewCompat.setForceDark(webView, WebViewCompat.FORCE_DARK_OFF)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                request.deny()
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                Toast.makeText(view.context, "External popups are blocked for your security.", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                return !ErpConfig.belongsToErp(target)
            }

            @Suppress("DEPRECATION", "Overriding")
            override fun onReceivedSslError(
                view: WebView,
                handler: android.webkit.SslErrorHandler,
                error: android.net.http.SslError
            ) {
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

    // The remainder of the activity implementation is unchanged.
    // This marker must never be reached in the final source.
    private fun showFatalStartupView(message: String) {
        val view = android.widget.TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF071426.toInt())
        }
        setContentView(view)
    }

    override fun onDestroy() {
        stopSessionHeartbeat()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (::binding.isInitialized && binding.webView.visibility == View.VISIBLE) showDashboard() else super.onBackPressed()
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
