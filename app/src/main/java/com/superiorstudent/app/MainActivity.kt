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
    private var profileFetchInProgress = false
    private var profileReadAttempts = 0
    private var activeModule: Module? = null
    private var username = ""
    private var password = ""
    private var loginAttempt = 0
    private var preloadingModule: Module? = null
    private val preloadQueue = mutableListOf<Module>()
    private val cachedHtml = mutableMapOf<Module, String>()

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
                    Toast.makeText(this@MainActivity, "Unable to load ERP page. Check internet and try Refresh.", Toast.LENGTH_LONG).show()
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

                val module = preloadingModule ?: return
                handler.postDelayed({
                    view.evaluateJavascript("document.documentElement.outerHTML") { result ->
                        val html = decodeJavascriptString(result)
                        if (html.isNotBlank()) cachedHtml[module] = html
                        preloadingModule = null
                        preloadNextModule()
                    }
                }, MODULE_READ_DELAY_MS)
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
            loginSubmitted = false
            restoringSession = false
            loggedIn = false
            loginAttempt = 0
            profileReadAttempts = 0
            cachedHtml.clear()
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
        binding.webView.visibility = View.GONE
        binding.webView.loadUrl(ERP_BASE_URL + preloadingModule!!.path)
    }

    private fun loadModule(module: Module) {
        if (!loggedIn) return

        activeModule = module
        binding.loginScroll.visibility = View.GONE
        binding.dashboardScroll.visibility = View.GONE
        binding.moduleScreen.visibility = View.VISIBLE
        binding.moduleProgress.visibility = View.VISIBLE

        when (module) {
            Module.ATTENDANCE -> {
                binding.moduleTitle.text = "Attendance"
                binding.moduleSubtitle.text = "Your official university attendance record"
            }
            Module.TIMETABLE -> {
                binding.moduleTitle.text = "Timetable"
                binding.moduleSubtitle.text = "Your official class schedule"
            }
            Module.FEE -> {
                binding.moduleTitle.text = "Fee Details"
                binding.moduleSubtitle.text = "Your official fee information"
            }
        }

        val html = cachedHtml[module]
        if (!html.isNullOrBlank()) {
            binding.webView.loadDataWithBaseURL(
                ERP_BASE_URL,
                html,
                "text/html",
                "UTF-8",
                ERP_BASE_URL + module.path
            )
        } else {
            binding.webView.loadUrl(ERP_BASE_URL + module.path)
        }
    }

    private fun refreshActiveModule() {
        val module = activeModule ?: return
        cachedHtml.remove(module)
        binding.moduleProgress.visibility = View.VISIBLE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(ERP_BASE_URL + module.path)
    }

    private fun showDashboard() {
        activeModule = null
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.moduleProgress.visibility = View.GONE
        binding.moduleScreen.visibility = View.GONE
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
        cachedHtml.clear()
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
        private const val MODULE_READ_DELAY_MS = 700L

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
