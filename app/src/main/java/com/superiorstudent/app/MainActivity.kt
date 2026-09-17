package com.superiorstudent.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
    private var activeModule: Module? = null
    private var username = ""
    private var password = ""
    private var loginAttempt = 0
    private var preloadingModule: Module? = null
    private var showingCachedModule = false
    private val preloadQueue = mutableListOf<Module>()
    private val cachedHtml = mutableMapOf<Module, String>()

    private enum class Module(val title: String, val path: String) {
        ATTENDANCE("Attendance", "student/attendance"),
        TIMETABLE("Timetable", "student/class/schedule"),
        FEE("Fee Details", "student/invoices")
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
                    Toast.makeText(this@MainActivity, "Unable to load ERP page. Check internet and try Refresh.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                if (restoringSession) {
                    if (isAuthenticatedUrl(url)) {
                        restoringSession = false
                        completeLogin(isRestoredSession = true)
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

                val module = preloadingModule
                if (module != null) {
                    handler.postDelayed({
                        view.evaluateJavascript("document.documentElement.outerHTML") { result ->
                            val html = decodeJavascriptString(result)
                            if (html.isNotBlank()) cachedHtml[module] = html
                            preloadingModule = null
                            preloadNextModule()
                        }
                    }, 500L)
                }
            }
        }

        binding.loginButton.setOnClickListener {
            username = binding.usernameInput.text.toString().trim()
            password = binding.passwordInput.text.toString()
            if (username.isBlank() || password.isBlank()) {
                binding.loginStatus.setTextColor(Color.rgb(198, 40, 40))
                binding.loginStatus.text = "Please enter your ERP username and password."
                return@setOnClickListener
            }
            loginInProgress = true
            restoringSession = false
            loginSubmitted = false
            loggedIn = false
            loginAttempt = 0
            cachedHtml.clear()
            binding.loginButton.isEnabled = false
            binding.loginStatus.setTextColor(Color.rgb(102, 112, 133))
            binding.loginStatus.text = "Signing in securely…"
            binding.webView.visibility = View.GONE
            binding.webView.loadUrl(ERP_LOGIN_URL)
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
            binding.dashboardScroll.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            binding.loginStatus.text = "Restoring your session…"
            webView.loadUrl(ERP_DASHBOARD_URL)
        }
    }

    private fun scheduleLoginInjection(view: WebView) {
        if (!loginInProgress || loginSubmitted) return
        if (loginAttempt >= MAX_LOGIN_INJECTION_ATTEMPTS) {
            failLogin("Unable to connect to the ERP login form. Please try again.")
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
                binding.loginStatus.text = "Verifying ERP credentials…"
            } else {
                scheduleLoginInjection(view)
            }
        }
    }

    private fun completeLogin(isRestoredSession: Boolean = false) {
        loginInProgress = false
        restoringSession = false
        loggedIn = true
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putString(KEY_USERNAME, username)
            .apply()
        binding.loginStatus.text = ""
        binding.loginButton.isEnabled = true
        binding.passwordInput.text?.clear()
        showDashboard()
        fetchStudentProfile()
        preloadAllModules()
    }

    private fun fetchStudentProfile() {
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(ERP_DASHBOARD_URL)
        handler.postDelayed({
            if (!loggedIn) return@postDelayed
            binding.webView.evaluateJavascript(STUDENT_PROFILE_SCRIPT) { result ->
                val profile = decodeJavascriptString(result)
                if (profile.isNotBlank()) {
                    try {
                        val json = JSONObject(profile)
                        val name = json.optString("name").trim()
                        val cgpa = json.optString("cgpa").trim()
                        val sgpa = json.optString("sgpa").trim()
                        if (name.isNotBlank()) {
                            preferences.edit().putString(KEY_STUDENT_NAME, name).apply()
                            binding.studentName.text = name
                        }
                        if (cgpa.isNotBlank() || sgpa.isNotBlank()) {
                            val displayGpa = buildString {
                                if (cgpa.isNotBlank()) append("CGPA: ").append(cgpa)
                                if (sgpa.isNotBlank()) {
                                    if (isNotEmpty()) append("  |  ")
                                    append("SGPA: ").append(sgpa)
                                }
                            }
                            preferences.edit().putString(KEY_GPA, displayGpa).apply()
                            binding.studentGpa.text = displayGpa
                        }
                    } catch (_: Exception) {
                        // Keep the previously saved profile if the ERP response is not JSON.
                    }
                }
                preloadAllModules()
            }
        }, 1200L)
    }

    private fun preloadAllModules() {
        if (!loggedIn || preloadingModule != null) return
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
        binding.webView.visibility = View.GONE
        binding.webView.loadUrl(ERP_BASE_URL + preloadingModule!!.path)
    }

    private fun loadModule(module: Module) {
        if (!loggedIn) return
        activeModule = module
        binding.dashboardScroll.visibility = View.GONE
        binding.loginScroll.visibility = View.GONE
        binding.refreshButton.visibility = View.VISIBLE
        binding.homeButton.visibility = View.VISIBLE
        binding.webView.visibility = View.VISIBLE

        val html = cachedHtml[module]
        if (!html.isNullOrBlank()) {
            showingCachedModule = true
            binding.webView.loadDataWithBaseURL(
                ERP_BASE_URL,
                html,
                "text/html",
                "UTF-8",
                ERP_BASE_URL + module.path
            )
        } else {
            showingCachedModule = false
            binding.webView.loadUrl(ERP_BASE_URL + module.path)
        }
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        cachedHtml.remove(module)
        showingCachedModule = false
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(ERP_BASE_URL + module.path)
    }

    private fun showDashboard() {
        activeModule = null
        showingCachedModule = false
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        binding.homeButton.visibility = View.GONE
        binding.dashboardScroll.visibility = View.VISIBLE
        binding.loginScroll.visibility = View.GONE
        val savedName = preferences.getString(KEY_STUDENT_NAME, "") ?: ""
        val savedGpa = preferences.getString(KEY_GPA, "") ?: ""
        binding.studentName.text = if (savedName.isBlank()) "Student" else savedName
        binding.studentGpa.text = if (savedGpa.isBlank()) "GPA: Not available yet" else savedGpa
    }

    private fun showLogin() {
        activeModule = null
        loggedIn = false
        loginInProgress = false
        restoringSession = false
        loginSubmitted = false
        loginAttempt = 0
        preloadingModule = null
        preloadQueue.clear()
        cachedHtml.clear()
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        binding.homeButton.visibility = View.GONE
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
        showLogin()
    }

    private fun isAuthenticatedUrl(url: String): Boolean =
        url.contains("/student/", ignoreCase = true) &&
            !url.contains("/web/login", ignoreCase = true)

    private fun failLogin(message: String) {
        loginInProgress = false
        loginSubmitted = false
        binding.loginButton.isEnabled = true
        binding.loginStatus.setTextColor(Color.rgb(198, 40, 40))
        binding.loginStatus.text = message
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
        private const val KEY_GPA = "student_gpa"
        private const val ERP_BASE_URL = "https://erp.superior.edu.pk/"
        private const val ERP_LOGIN_URL = "https://erp.superior.edu.pk/web/login"
        private const val ERP_DASHBOARD_URL = "https://erp.superior.edu.pk/student/dashboard"
        private const val LOGIN_INJECTION_DELAY_MS = 500L
        private const val MAX_LOGIN_INJECTION_ATTEMPTS = 20

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

        private const val STUDENT_PROFILE_SCRIPT = """
            (function(){
              function clean(value){
                return (value || '').replace(/\\s+/g,' ').trim();
              }
              function textOf(element){
                return element ? clean(element.innerText || element.textContent) : '';
              }
              function valueNearLabel(label){
                const elements=Array.from(document.querySelectorAll('.stat-card'));
                for(const card of elements){
                  const cardText=textOf(card);
                  if(new RegExp('\\\\b'+label+'\\\\b','i').test(cardText)){
                    const value=card.querySelector('.stat-value');
                    if(value) return textOf(value);
                  }
                }
                return '';
              }

              let name=textOf(document.querySelector('.student-details h1'));
              if(!name){
                const heading=document.querySelector('.student-details');
                if(heading){
                  const firstLine=textOf(heading).split('SU91-')[0];
                  name=clean(firstLine);
                }
              }
              if(!name){
                const candidates=Array.from(document.querySelectorAll('h1,h2,h3,.student-name,.student_name'));
                for(const element of candidates){
                  const candidate=textOf(element);
                  if(candidate && !/dashboard|welcome|result|attendance|timetable/i.test(candidate)){
                    name=candidate;
                    break;
                  }
                }
              }

              const cgpa=valueNearLabel('CGPA');
              const sgpa=valueNearLabel('SGPA');
              return JSON.stringify({name:name,cgpa:cgpa,sgpa:sgpa});
            })();
        """
    }
}
