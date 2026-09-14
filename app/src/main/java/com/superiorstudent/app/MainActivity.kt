package com.superiorstudent.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.superiorstudent.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val webView = binding.webView
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.postDelayed({
                    when {
                        url.contains("/student/attendance") -> view.evaluateJavascript(ATTENDANCE_SCRIPT, null)
                        url.contains("/student/class/schedule") -> view.evaluateJavascript(TIMETABLE_SCRIPT, null)
                        url.contains("/student/invoices") -> view.evaluateJavascript(FEE_SCRIPT, null)
                    }
                }, 650)
            }
        }

        binding.attendanceButton.setOnClickListener {
            openPortal(ERP_URL + "student/attendance")
        }
        binding.timetableButton.setOnClickListener {
            openPortal(ERP_URL + "student/class/schedule")
        }
        binding.feeButton.setOnClickListener {
            openPortal(ERP_URL + "student/invoices")
        }
        binding.refreshButton.setOnClickListener { webView.reload() }
        binding.homeButton.setOnClickListener { showDashboard() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun openPortal(url: String) {
        binding.dashboardScroll.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.refreshButton.visibility = View.VISIBLE
        binding.homeButton.visibility = View.VISIBLE
        binding.webView.loadUrl(url)
    }

    private fun showDashboard() {
        binding.webView.visibility = View.GONE
        binding.refreshButton.visibility = View.GONE
        binding.homeButton.visibility = View.GONE
        binding.dashboardScroll.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.visibility == View.VISIBLE && binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else if (binding.webView.visibility == View.VISIBLE) {
            showDashboard()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val ERP_URL = "https://erp.superior.edu.pk/"

        private const val COMMON_STYLE = """
            (function(){
              if(document.getElementById('miniAppRoot')) return;
              const s=document.createElement('style');
              s.textContent=`body{background:#f5f7fb!important;font-family:Arial,sans-serif!important}.mini-app{padding:20px;color:#172033}.mini-head{background:#1e5eff;color:#fff;border-radius:18px;padding:22px;margin-bottom:18px}.mini-head h1{margin:0;font-size:25px}.mini-head p{margin:7px 0 0;color:#dce7ff}.mini-card{background:#fff;border:1px solid #e5eaf2;border-radius:14px;padding:16px;margin-bottom:14px;box-shadow:0 3px 12px #17203312}.mini-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:13px}.mini-label{font-size:12px;color:#667085;font-weight:bold;text-transform:uppercase}.mini-value{font-size:24px;font-weight:bold;margin-top:6px}.mini-table{width:100%;border-collapse:collapse;background:#fff;border-radius:12px;overflow:hidden}.mini-table th,.mini-table td{padding:12px;border-bottom:1px solid #e8edf3;text-align:left;font-size:13px}.mini-table th{background:#435b68;color:#fff}.mini-badge{display:inline-block;padding:4px 8px;border-radius:6px;background:#e8f5e9;color:#2e7d32;font-size:12px;font-weight:bold}.mini-muted{color:#667085;font-size:13px}`;
              document.head.appendChild(s);
              const old=document.querySelector('#page_content')||document.querySelector('main')||document.body;
              old.innerHTML='<div id="miniAppRoot"></div>';
              return document.getElementById('miniAppRoot');
            })()
        """

        private val ATTENDANCE_SCRIPT = """
            (function(){
              const root=(function(){${COMMON_STYLE.removeSuffix("\n        ")}})();
              if(!root) return;
              const source=document.body.innerText;
              const percentages=[...source.matchAll(/(\\d+(?:\\.\\d+)?)\\s*%/g)].map(x=>Number(x[1])).filter(x=>x>=0&&x<=100);
              const cards=[...document.querySelectorAll('.md-card,.card,.panel')];
              const rows=cards.map(c=>c.innerText.replace(/\\s+/g,' ').trim()).filter(x=>x&&/attendance|course|class/i.test(x));
              const overall=percentages.length?Math.round(percentages.reduce((a,b)=>a+b,0)/percentages.length):0;
              root.innerHTML='<div class="mini-app"><div class="mini-head"><h1>Attendance</h1><p>Your attendance data from the official ERP</p></div><div class="mini-grid"><div class="mini-card"><div class="mini-label">Subjects detected</div><div class="mini-value">'+(rows.length||percentages.length)+'</div></div><div class="mini-card"><div class="mini-label">Average attendance</div><div class="mini-value">'+overall+'%</div></div></div><div class="mini-card"><div class="mini-label">Subject records</div><p class="mini-muted">Tap a subject record in the ERP view to see detailed attendance.</p>'+rows.map(x=>'<div style="padding:12px 0;border-bottom:1px solid #e8edf3">'+x+'</div>').join('')+'</div></div>';
            })();
        """

        private val TIMETABLE_SCRIPT = """
            (function(){
              const root=(function(){${COMMON_STYLE.removeSuffix("\n        ")}})();
              if(!root) return;
              const table=document.querySelector('table');
              const html=table?table.outerHTML:'<p class="mini-muted">Timetable loaded, but no table was detected. Please use the ERP schedule controls.</p>';
              root.innerHTML='<div class="mini-app"><div class="mini-head"><h1>Timetable</h1><p>Your class schedule from the official ERP</p></div><div class="mini-card" style="overflow:auto">'+html.replace(/<table/,'<table class="mini-table"')+'</div></div>';
            })();
        """

        private val FEE_SCRIPT = """
            (function(){
              const root=(function(){${COMMON_STYLE.removeSuffix("\n        ")}})();
              if(!root) return;
              const table=document.querySelector('table');
              let total=0, unpaid=0;
              if(table){[...table.querySelectorAll('tr')].forEach(r=>{const t=r.innerText;const nums=[...t.matchAll(/(?:^|\\s)(\\d+(?:\\.\\d+)?)(?=\\s|$)/g)].map(x=>Number(x[1]));if(nums.length) total+=nums[nums.length-1];if(/unpaid/i.test(t)) unpaid+=nums.length?nums[nums.length-1]:0;});}
              const html=table?table.outerHTML:'<p class="mini-muted">Fee records loaded, but no table was detected.</p>';
              root.innerHTML='<div class="mini-app"><div class="mini-head"><h1>Fee Details</h1><p>Your invoices and payment status from the official ERP</p></div><div class="mini-grid"><div class="mini-card"><div class="mini-label">Listed amount</div><div class="mini-value">Rs. '+total.toLocaleString()+'</div></div><div class="mini-card"><div class="mini-label">Unpaid amount</div><div class="mini-value">Rs. '+unpaid.toLocaleString()+'</div></div></div><div class="mini-card" style="overflow:auto">'+html.replace(/<table/,'<table class="mini-table"')+'</div></div>';
            })();
        """
    }
}
