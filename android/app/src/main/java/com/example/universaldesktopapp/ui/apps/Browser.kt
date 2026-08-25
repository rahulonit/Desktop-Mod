package com.example.universaldesktopapp.ui.apps

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val HOME_PAGE = "https://www.google.com"

private fun browserTarget(input: String): String {
    val value = input.trim()
    if (value.isBlank()) return HOME_PAGE
    if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
    if (!value.contains(' ') && (value.contains('.') || value.startsWith("localhost"))) return "https://$value"
    return "https://www.google.com/search?q=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserApp() {
    var address by remember { mutableStateOf(HOME_PAGE) }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("New tab") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun refreshNavigationState(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }
    fun navigate() {
        val target = browserTarget(address)
        address = target
        errorMessage = null
        webView?.loadUrl(target)
        focusManager.clearFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Text("←") }
            IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) { Text("→") }
            IconButton(onClick = { webView?.reload() }) { Text("↻") }
            IconButton(onClick = { address = HOME_PAGE; webView?.loadUrl(HOME_PAGE) }) { Text("⌂") }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate() }),
            )
            Button(onClick = { navigate() }) { Text("Go") }
        }
        if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        errorMessage?.let {
            Text(it, Modifier.fillMaxWidth().background(Color(0xFFFFE8E6)).padding(8.dp), color = Color(0xFF9B1C12))
        }
        Text(pageTitle, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    val currentWebView = this
                    webView = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        mediaPlaybackRequiresUserGesture = true
                    }
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(currentWebView, true)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            return if (scheme == "http" || scheme == "https") {
                                false
                            } else {
                                errorMessage = "Blocked external link: ${request.url}"
                                true
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            errorMessage = null
                            address = url
                            refreshNavigationState(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            address = url
                            pageTitle = view.title ?: Uri.parse(url).host.orEmpty()
                            refreshNavigationState(view)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                loading = false
                                errorMessage = "Page failed to load: ${error.description}"
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                            loading = newProgress < 100
                        }

                        override fun onReceivedTitle(view: WebView, title: String?) {
                            if (!title.isNullOrBlank()) pageTitle = title
                        }
                    }
                    loadUrl(HOME_PAGE)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
