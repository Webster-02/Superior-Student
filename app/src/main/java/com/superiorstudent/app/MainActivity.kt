package com.superiorstudent.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.superiorstudent.app.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var loginInProgress = false
    private var loginSubmitted = false
    private var loggedIn = false
    private var activeModule: Module? = null
    private var username = ""
    private var password = ""

    private enum class Module(val title: String, val path: String) {
        ATTENDANCE("Attendance", "student/attendance"),
        TIMETABLE("Timetable", "student/class/schedule"),
        FEE("Fee Details", "student/invoices")
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val webView = binding.webView
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.setSupportZoom(false)
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AppBridge(), "SuperiorApp")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                if (loginInProgress) {
                    if (isAuthenticatedUrl(url)) {
                        loginInProgress = false
                        loggedIn = true
                        binding.loginStatus.text = ""
                        binding.loginButton.isEnabled = true
                        showDashboard()
                    } else if (!loginSubmitted) {
                        loginSubmitted = true
                        view.postDelayed({ injectLogin(view) }, 350)
                    } else if (url.contains("/web/login")) {
                        loginInProgress = false
                        binding.loginButton.isEnabled = true
                        binding.loginStatus.setTextColor(Color.rgb(198, 40, 40))
                        binding.loginStatus.text = "Login failed. Please check your ERP username or password."
                    }
                    return
                }

                activeModule?.let { module ->
                    view.postDelayed({
                        view.evaluateJavascript(moduleScript(module), null)
                    }, 450)
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
            loginSubmitted = false
            loggedIn = false
            binding.loginButton.isEnabled = false
            binding.loginStatus.setTextColor(Color.rgb(102, 112, 133))
            binding.loginStatus.text = "Signing in securely…"
            webView.visibility = View.GONE
            webView.loadUrl(ERP_LOGIN_URL)
        }

        binding.attendanceButton.setOnClickListener { loadModule(Module.ATTENDANCE) }
        binding.timetableButton.setOnClickListener { loadModule(Module.TIMETABLE) }
        binding.feeButton.setOnClickListener { loadModule(Module.FEE) }
        binding.refreshButton.setOnClickListener { activeModule?.let { loadModule(it) } }
        binding.homeButton.setOnClickListener { showDashboard() }
        binding.logoutButton.setOnClickListener { logout() }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
    }

    private fun injectLogin(view: WebView) {
        val script = LOGIN_SCRIPT
            .replace("%USERNAME%", JSONObject.quote(username))
            .replace("%PASSWORD%", JSONObject.quote(password))
        view.evaluateJavascript(script, null)
    }

    private fun isAuthenticatedUrl(url: String): Boolean {
        return url.contains("/student/") && !url.contains("/web/login", ignoreCase = true)
    }

    private fun loadModule(module: Module) {
        if (!loggedIn) return
        activeModule = module
        binding.dashboardScroll.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        binding.homeButton.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.loginStatus.text = ""
        binding.webView.loadUrl(ERP_BASE_URL + module.path)
    }

    private fun showDashboard() {
        activeModule = null
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        binding.homeButton.visibility = View.GONE
        binding.dashboardScroll.visibility = View.VISIBLE
        binding.loginScroll.visibility = View.GONE
    }

    private fun showLogin() {
        activeModule = null
        loggedIn = false
        loginInProgress = false
        loginSubmitted = false
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
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }
        binding.webView.clearHistory()
        binding.webView.clearCache(true)
        binding.webView.clearFormData()
        showLogin()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.visibility == View.VISIBLE) {
            showDashboard()
        } else {
            super.onBackPressed()
        }
    }

    private inner class AppBridge {
        @android.webkit.JavascriptInterface
        fun moduleReady() {
            runOnUiThread {
                binding.refreshButton.visibility = View.VISIBLE
                binding.homeButton.visibility = View.VISIBLE
                binding.webView.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val ERP_BASE_URL = "https://erp.superior.edu.pk/"
        private const val ERP_LOGIN_URL = "https://erp.superior.edu.pk/web/login"

        private const val LOGIN_SCRIPT = """
            (function(){
              const user = document.querySelector('input[name="login"], input[type="email"], input[name*="user" i], input[name*="email" i], input[type="text"]');
              const pass = document.querySelector('input[name="password"], input[type="password"]');
              if (!user || !pass) return;
              user.value = %USERNAME%;
              pass.value = %PASSWORD%;
              user.dispatchEvent(new Event('input', {bubbles:true}));
              user.dispatchEvent(new Event('change', {bubbles:true}));
              pass.dispatchEvent(new Event('input', {bubbles:true}));
              pass.dispatchEvent(new Event('change', {bubbles:true}));
              const form = pass.form || user.form;
              if (form) {
                if (form.requestSubmit) form.requestSubmit(); else form.submit();
              }
            })();
        """

        private fun moduleScript(module: Module): String {
            val title = JSONObject.quote(module.title)
            return when (module) {
                Module.ATTENDANCE -> """
                    (function(){
                      const title=$title;
                      const text=document.body.innerText||'';
                      const cards=[...document.querySelectorAll('.md-card,.card,.panel,.o_student_attendance')]
                        .map(x=>x.innerText.replace(/\\s+/g,' ').trim()).filter(Boolean);
                      const percentages=[...text.matchAll(/(\\d+(?:\\.\\d+)?)\\s*%/g)].map(x=>Number(x[1])).filter(x=>x>=0&&x<=100);
                      const average=percentages.length?(percentages.reduce((a,b)=>a+b,0)/percentages.length).toFixed(1):'—';
                      const rows=cards.length?cards: text.split('\\n').map(x=>x.trim()).filter(x=>x&&/attendance|course|class|subject/i.test(x)).slice(0,30);
                      render(title,'Live attendance from your university ERP',
                        '<div class="stat-grid"><div class="stat"><small>Average attendance</small><strong>'+average+(average==='—'?'':'%')+'</strong></div><div class="stat"><small>Records found</small><strong>'+rows.length+'</strong></div></div>'+list(rows,'Attendance records'));
                    })();
                """.trimIndent()
                Module.TIMETABLE -> """
                    (function(){
                      const title=$title;
                      const table=document.querySelector('table');
                      let content=table?table.outerHTML:'<p class="muted">The timetable page loaded, but no schedule table was detected.</p>';
                      render(title,'Your live class schedule', '<div class="table-wrap">'+content.replace('<table','<table class="data-table"')+'</div>');
                    })();
                """.trimIndent()
                Module.FEE -> """
                    (function(){
                      const title=$title;
                      const table=document.querySelector('table');
                      let total=0, unpaid=0;
                      if(table){ [...table.querySelectorAll('tr')].forEach(row=>{
                        const value=[...row.innerText.matchAll(/(?:^|\\s)(\\d+(?:\\.\\d+)?)(?=\\s|$)/g)].map(x=>Number(x[1])).pop()||0;
                        total+=value; if(/unpaid/i.test(row.innerText)) unpaid+=value;
                      }); }
                      const content=table?'<div class="table-wrap">'+table.outerHTML.replace('<table','<table class="data-table"')+'</div>':'<p class="muted">The fee page loaded, but no invoice table was detected.</p>';
                      render(title,'Invoices and payment status from your university ERP','<div class="stat-grid"><div class="stat"><small>Listed amount</small><strong>Rs. '+total.toLocaleString()+'</strong></div><div class="stat"><small>Unpaid amount</small><strong>Rs. '+unpaid.toLocaleString()+'</strong></div></div>'+content);
                    })();
                """.trimIndent()
            }
        }
    }
}

private fun list(items: List<String>, heading: String): String = """
    <section class="panel"><h2>$heading</h2>${items.joinToString("") { "<div class=\"record\">${it.replace("<", "&lt;").replace(">", "&gt;")}</div>" }}</section>
""".trimIndent()
