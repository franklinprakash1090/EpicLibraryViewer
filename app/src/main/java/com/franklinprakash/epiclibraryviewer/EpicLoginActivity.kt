package com.franklinprakash.epiclibraryviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class EpicLoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "auth_code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val uri = request.url
                if (uri.host == "localhost" && uri.path?.contains("authorized") == true) {
                    val code = uri.getQueryParameter("code")
                    if (!code.isNullOrEmpty()) {
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_CODE, code)
                        )
                    }
                    finish()
                    return true
                }
                return false
            }
        }

        webView.loadUrl(intent.getStringExtra("auth_url")!!)
    }
}
