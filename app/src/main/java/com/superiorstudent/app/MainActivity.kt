package com.superiorstudent.app

import android.annotation.SuppressLint
import android.os.Bundle
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

        binding.erpButton.setOnClickListener { webView.loadUrl(ERP_URL) }
        binding.lmsButton.setOnClickListener { webView.loadUrl(LMS_URL) }
        binding.refreshButton.setOnClickListener { webView.reload() }

        if (savedInstanceState == null) {
            webView.loadUrl(ERP_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack() else super.onBackPressed()
    }

    companion object {
        private const val ERP_URL = "https://erp.superior.edu.pk/"
        // Replace this URL with the official Superior LMS URL if it differs.
        private const val LMS_URL = "https://lms.superior.edu.pk/"
    }
}
