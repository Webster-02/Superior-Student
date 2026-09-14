package com.superiorstudent.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

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
    }
}
