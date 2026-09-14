package com.superiorstudent.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
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
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.setSupportZoom(false)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        binding.erpButton.setOnClickListener { openPortal(ERP_URL) }
        binding.lmsButton.setOnClickListener { openPortal(LMS_URL) }
        binding.refreshButton.setOnClickListener { webView.reload() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun openPortal(url: String) {
        binding.introScreen.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.refreshButton.visibility = View.VISIBLE
        binding.webView.loadUrl(url)
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
            binding.webView.visibility = View.GONE
            binding.refreshButton.visibility = View.GONE
            binding.introScreen.visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val ERP_URL = "https://erp.superior.edu.pk/"
        private const val LMS_URL = "https://lms.superior.edu.pk/local/mycustomlogin/index.php"
    }
}
