package com.superiorstudent.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.contains("/student/attendance")) {
                    view.postDelayed({ injectAttendanceDashboard(view) }, 500)
                }
            }
        }

        binding.erpButton.setOnClickListener { openPortal(ERP_URL) }
        binding.lmsButton.setOnClickListener { openPortal(LMS_URL) }
        binding.timetableButton.setOnClickListener { openPortal(ERP_URL) }
        binding.assignmentsButton.setOnClickListener { openPortal(LMS_URL) }
        binding.feeButton.setOnClickListener { openPortal(ERP_URL) }
        binding.profileButton.setOnClickListener { openPortal(ERP_URL) }
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

    private fun injectAttendanceDashboard(webView: WebView) {
        webView.evaluateJavascript(ATTENDANCE_SCRIPT, null)
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
        private const val LMS_URL = "https://lms.superior.edu.pk/local/mycustomlogin/index.php"

        private val ATTENDANCE_SCRIPT = """
            (function() {
              if (window.__superiorAttendanceEnhanced) return;
              window.__superiorAttendanceEnhanced = true;
              const style = document.createElement('style');
              style.textContent = `
                .sa-wrap{font-family:Arial,sans-serif;background:#f5f7fb;padding:22px;min-height:100vh;color:#14213d}
                .sa-head{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:20px}
                .sa-title{font-size:28px;font-weight:800;margin:0}.sa-sub{color:#667085;margin-top:6px}
                .sa-btn{border:0;border-radius:9px;padding:10px 15px;background:#1e5eff;color:#fff;font-weight:700;cursor:pointer}
                .sa-stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-bottom:18px}
                .sa-stat,.sa-card{background:#fff;border:1px solid #e4e8ef;border-radius:15px;padding:16px;box-shadow:0 3px 12px #14213d0b}
                .sa-label{font-size:12px;color:#667085;text-transform:uppercase;font-weight:700}.sa-value{font-size:27px;font-weight:800;margin-top:7px}
                .sa-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px}
                .sa-card{cursor:pointer}.sa-card h3{font-size:16px;margin:0 0 7px}.sa-code{font-size:11px;color:#667085;word-break:break-word}
                .sa-row{display:flex;justify-content:space-between;gap:10px;margin-top:14px;font-size:13px;color:#667085}.sa-pct{font-size:20px;font-weight:800}
                .sa-good{color:#16a34a}.sa-warn{color:#d97706}.sa-low{color:#dc2626}.sa-track{height:9px;background:#edf0f5;border-radius:20px;overflow:hidden;margin-top:13px}.sa-fill{height:100%;border-radius:20px}
                .sa-modal{position:fixed;inset:0;background:#0008;display:flex;align-items:center;justify-content:center;padding:18px;z-index:99999}.sa-dialog{background:#fff;border-radius:16px;padding:20px;width:min(720px,100%);max-height:90vh;overflow:auto}.sa-close{float:right;border:0;background:#f1f5f9;border-radius:50%;width:32px;height:32px;cursor:pointer}.sa-table{width:100%;border-collapse:collapse;margin-top:15px}.sa-table th,.sa-table td{padding:10px;border-bottom:1px solid #e4e8ef;text-align:left;font-size:13px}.sa-table th{color:#667085;background:#f8fafc}
              `;
              document.head.appendChild(style);

              const root = document.querySelector('#page_content');
              if (!root) return;
              const text = root.innerText.replace(/\\s+/g, ' ');
              const cards = [];
              const links = [...root.querySelectorAll('a[href*="/student/attendancedetail/id/"]')];
              links.forEach(a => {
                const box = a.closest('.md-card') || a.parentElement;
                const raw = box ? box.innerText.replace(/\\s+/g, ' ') : a.innerText;
                const name = (raw.match(/Course\\s*:?\\s*(.*?)\\s*(?:Course Code|Attendance|$)/i)||[])[1] || a.innerText.trim();
                const pct = Number((raw.match(/(\\d+(?:\\.\\d+)?)\\s*%/)||[])[1] || (raw.match(/(\\d+(?:\\.\\d+)?)\\s*$/)||[])[1] || 0);
                const conducted = Number((raw.match(/(?:Conducted|Classes Conducted)\\s*:?[ ]*(\\d+)/i)||[])[1] || 0);
                const attended = Number((raw.match(/(?:Attended|Classes Attended)\\s*:?[ ]*(\\d+)/i)||[])[1] || 0);
                cards.push({name:name.trim(), pct, conducted, attended, href:a.href});
              });

              const unique = []; const seen = new Set();
              cards.forEach(x => { if (!seen.has(x.href)) {seen.add(x.href); unique.push(x);} });
              const totalConducted = unique.reduce((s,x)=>s+x.conducted,0);
              const totalAttended = unique.reduce((s,x)=>s+x.attended,0);
              const overall = totalConducted ? (totalAttended/totalConducted*100).toFixed(1) : '0.0';
              const cls = p => p >= 75 ? 'sa-good' : p >= 50 ? 'sa-warn' : 'sa-low';

              root.innerHTML = `<div class="sa-wrap"><div class="sa-head"><div><h1 class="sa-title">Attendance Dashboard</h1><div class="sa-sub">Automatically extracted from your ERP attendance records</div></div><button class="sa-btn" onclick="location.reload()">Refresh Data</button></div>
                <div class="sa-stats"><div class="sa-stat"><div class="sa-label">Subjects</div><div class="sa-value">${unique.length}</div></div><div class="sa-stat"><div class="sa-label">Overall Attendance</div><div class="sa-value ${cls(Number(overall))}">${overall}%</div></div><div class="sa-stat"><div class="sa-label">Present</div><div class="sa-value sa-good">${totalAttended}</div></div><div class="sa-stat"><div class="sa-label">Absent</div><div class="sa-value sa-low">${Math.max(0,totalConducted-totalAttended)}</div></div></div>
                <div class="sa-grid">${unique.map((x,i)=>`<div class="sa-card" onclick="window.open('${x.href}','_self')"><div class="sa-row" style="margin-top:0"><h3>${x.name}</h3><b class="sa-pct ${cls(x.pct)}">${x.pct.toFixed(1)}%</b></div><div class="sa-track"><div class="sa-fill ${cls(x.pct)}" style="width:${Math.min(100,Math.max(0,x.pct))}%;background:currentColor"></div></div><div class="sa-row"><span>${x.attended} attended / ${x.conducted} conducted</span><span>${x.pct>=75?'Good':x.pct>=50?'Warning':'Low'}</span></div></div>`).join('')}</div></div>`;
            })();
        """.trimIndent()
    }
}
