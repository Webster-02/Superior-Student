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
        webView.webChromeClient = WebChromeClient()

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
                        binding.loginStatus.text = "Login failed. Check your ERP username or password."
                    }
                    return
                }

                activeModule?.let { module ->
                    view.postDelayed({ view.evaluateJavascript(moduleScript(module), null) }, 450)
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

    private fun isAuthenticatedUrl(url: String): Boolean =
        url.contains("/student/") && !url.contains("/web/login", ignoreCase = true)

    private fun loadModule(module: Module) {
        if (!loggedIn) return
        activeModule = module
        binding.dashboardScroll.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        binding.homeButton.visibility = View.GONE
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
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
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
        if (binding.webView.visibility == View.VISIBLE) showDashboard() else super.onBackPressed()
    }

    companion object {
        private const val ERP_BASE_URL = "https://erp.superior.edu.pk/"
        private const val ERP_LOGIN_URL = "https://erp.superior.edu.pk/web/login"

        private const val LOGIN_SCRIPT = """
            (function(){
              const user=document.querySelector('input[name="login"],input[type="email"],input[name*="user" i],input[name*="email" i],input[type="text"]');
              const pass=document.querySelector('input[name="password"],input[type="password"]');
              if(!user||!pass)return;
              user.value=%USERNAME%; pass.value=%PASSWORD%;
              user.dispatchEvent(new Event('input',{bubbles:true}));
              pass.dispatchEvent(new Event('input',{bubbles:true}));
              const form=pass.form||user.form;
              if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();}
            })();
        """

        private fun moduleScript(module: Module): String {
            val title = JSONObject.quote(module.title)
            val helpers = """
                const esc=s=>String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                const render=(title,subtitle,body)=>{
                  document.head.insertAdjacentHTML('beforeend',`<style id="superiorMiniStyle">body{margin:0!important;background:#f5f7fb!important;font-family:Arial,sans-serif!important;color:#172033!important}.mini{padding:22px;max-width:1100px;margin:auto}.hero{background:linear-gradient(135deg,#5d3b70,#8b5c86);color:white;border-radius:18px;padding:24px;margin-bottom:18px}.hero h1{margin:0;font-size:27px}.hero p{margin:7px 0 0;color:#f0e7f3}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin-bottom:18px}.stat,.panel{background:white;border:1px solid #e5e7eb;border-radius:14px;padding:17px;box-shadow:0 3px 12px #17203312}.stat small{display:block;color:#667085;font-size:12px;text-transform:uppercase;font-weight:bold}.stat strong{display:block;font-size:25px;margin-top:7px}.panel h2{margin:0 0 12px;font-size:18px}.record{padding:12px 0;border-bottom:1px solid #edf0f4;font-size:13px}.record:last-child{border-bottom:0}.table-wrap{overflow:auto}.data-table{width:100%;border-collapse:collapse;background:white;font-size:13px}.data-table th{background:#435b68;color:white;padding:12px;text-align:left;white-space:nowrap}.data-table td{padding:11px;border-bottom:1px solid #e8edf3;white-space:nowrap}.muted{color:#667085;font-size:13px}`);document.body.innerHTML='<main class="mini"><section class="hero"><h1>'+esc(title)+'</h1><p>'+esc(subtitle)+'</p></section>'+body+'</main>';SuperiorApp.moduleReady();
                };
            """

            return when (module) {
                Module.ATTENDANCE -> """
                    (function(){
                      $helpers
                      const source=document.body.innerText||'';
                      const cards=[...document.querySelectorAll('.md-card,.card,.panel')].map(x=>x.innerText.replace(/\\s+/g,' ').trim()).filter(Boolean);
                      const percentages=[...source.matchAll(/(\\d+(?:\\.\\d+)?)\\s*%/g)].map(x=>Number(x[1])).filter(x=>x>=0&&x<=100);
                      const average=percentages.length?(percentages.reduce((a,b)=>a+b,0)/percentages.length).toFixed(1):'—';
                      const rows=cards.length?cards:source.split('\\n').map(x=>x.trim()).filter(x=>x&&/attendance|course|class|subject/i.test(x)).slice(0,30);
                      const records=rows.map(x=>'<div class="record">'+esc(x)+'</div>').join('')||'<p class="muted">No attendance records detected.</p>';
                      render($title,'Live attendance from your university ERP','<div class="grid"><div class="stat"><small>Average attendance</small><strong>'+average+(average==='—'?'':'%')+'</strong></div><div class="stat"><small>Records found</small><strong>'+rows.length+'</strong></div></div><section class="panel"><h2>Attendance records</h2>'+records+'</section>');
                    })();
                """.trimIndent()
                Module.TIMETABLE -> """
                    (function(){
                      $helpers
                      const table=document.querySelector('table');
                      const content=table?'<div class="table-wrap">'+table.outerHTML.replace('<table','<table class="data-table"')+'</div>':'<p class="muted">The timetable page loaded, but no schedule table was detected.</p>';
                      render($title,'Your live class schedule',content);
                    })();
                """.trimIndent()
                Module.FEE -> """
                    (function(){
                      $helpers
                      const table=document.querySelector('table'); let total=0,unpaid=0;
                      if(table){[...table.querySelectorAll('tr')].forEach(row=>{const nums=[...row.innerText.matchAll(/(?:^|\\s)(\\d+(?:\\.\\d+)?)(?=\\s|$)/g)].map(x=>Number(x[1]));const value=nums.pop()||0;total+=value;if(/unpaid/i.test(row.innerText))unpaid+=value;});}
                      const content=table?'<div class="table-wrap">'+table.outerHTML.replace('<table','<table class="data-table"')+'</div>':'<p class="muted">The fee page loaded, but no invoice table was detected.</p>';
                      render($title,'Invoices and payment status from your university ERP','<div class="grid"><div class="stat"><small>Listed amount</small><strong>Rs. '+total.toLocaleString()+'</strong></div><div class="stat"><small>Unpaid amount</small><strong>Rs. '+unpaid.toLocaleString()+'</strong></div></div>'+content);
                    })();
                """.trimIndent()
            }
        }
    }
}
