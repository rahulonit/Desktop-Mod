package com.example.universaldesktopapp.ui.apps

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.universaldesktopapp.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val HOME_PAGE = "https://www.google.com"
private data class BrowserTab(val id: Long, val title: String = "New tab", val url: String = HOME_PAGE)

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
    var menuOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var desktopSite by remember { mutableStateOf(true) }
    var tabs by remember { mutableStateOf(listOf(BrowserTab(System.nanoTime()))) }
    var activeTabId by remember { mutableLongStateOf(tabs.first().id) }
    val bookmarks = remember { mutableStateListOf<Pair<String, String>>() }
    val focusManager = LocalFocusManager.current

    fun refreshNavigationState(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    fun navigate() {
        val target = browserTarget(address)
        address = target
        tabs = tabs.map { if (it.id == activeTabId) it.copy(url = target) else it }
        errorMessage = null
        webView?.loadUrl(target)
        focusManager.clearFocus()
    }

    fun newTab() {
        val tab = BrowserTab(System.nanoTime())
        tabs = tabs + tab
        activeTabId = tab.id
        address = tab.url
        pageTitle = tab.title
        webView?.loadUrl(tab.url)
    }

    fun selectTab(tab: BrowserTab) {
        if (tab.id == activeTabId) return
        activeTabId = tab.id
        address = tab.url
        pageTitle = tab.title
        webView?.loadUrl(tab.url)
    }

    fun closeTab(tab: BrowserTab) {
        if (tabs.size == 1) {
            tabs = listOf(tab.copy(title = "New tab", url = HOME_PAGE))
            address = HOME_PAGE; pageTitle = "New tab"; webView?.loadUrl(HOME_PAGE)
            return
        }
        val closingIndex = tabs.indexOfFirst { it.id == tab.id }
        tabs = tabs.filterNot { it.id == tab.id }
        if (activeTabId == tab.id) selectTab(tabs[(closingIndex - 1).coerceIn(tabs.indices)])
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
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).padding(start = 10.dp, top = 4.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    tabs.forEach { tab ->
                        Surface(
                            modifier = Modifier.width(220.dp).fillMaxHeight().padding(end = 3.dp).clickable { selectTab(tab) },
                            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                            color = if (tab.id == activeTabId) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(R.drawable.desktop_browser_modern), null, Modifier.size(18.dp), tint = Color.Unspecified)
                                Text(tab.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { closeTab(tab) }, modifier = Modifier.size(30.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("x", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { newTab() },
                        modifier = Modifier.size(38.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("+") }
                    Spacer(Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BrowserToolbarButton("<", canGoBack) { webView?.goBack() }
                    BrowserToolbarButton(">", canGoForward) { webView?.goForward() }
                    TextButton(onClick = { webView?.reload() }) { Text("Reload") }
                    TextButton(onClick = { address = HOME_PAGE; tabs = tabs.map { if (it.id == activeTabId) it.copy(url = HOME_PAGE) else it }; webView?.loadUrl(HOME_PAGE) }) { Text("Home") }
                    TextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        singleLine = true,
                        placeholder = { Text("Search the web or enter an address") },
                        leadingIcon = {
                            Text(
                                if (address.startsWith("https://")) "Secure" else "Web",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        trailingIcon = { TextButton(onClick = { navigate() }) { Text("Go") } },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() }),
                    )
                    Box {
                        BrowserToolbarButton("...") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("New tab") }, onClick = { newTab(); menuOpen = false })
                            DropdownMenuItem(text = { Text("Bookmark this page") }, onClick = {
                                if (bookmarks.none { it.second == address }) bookmarks += pageTitle to address
                                menuOpen = false
                            })
                            bookmarks.takeLast(5).forEach { bookmark ->
                                DropdownMenuItem(text = { Text("★ ${bookmark.first}", maxLines = 1) }, onClick = { address = bookmark.second; webView?.loadUrl(bookmark.second); menuOpen = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Find on page") }, onClick = { findOpen = true; menuOpen = false })
                            DropdownMenuItem(text = { Text(if (desktopSite) "Use mobile site" else "Use desktop site") }, onClick = {
                                desktopSite = !desktopSite
                                webView?.settings?.userAgentString = if (desktopSite) DESKTOP_USER_AGENT else null
                                webView?.reload()
                                menuOpen = false
                            })
                            DropdownMenuItem(text = { Text("Clear browsing data") }, onClick = {
                                webView?.clearHistory(); webView?.clearCache(true); CookieManager.getInstance().removeAllCookies(null)
                                menuOpen = false
                            })
                        }
                    }
                }
            }
        }
        if (findOpen) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(findQuery, { findQuery = it; webView?.findAllAsync(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Find on page") })
                TextButton(onClick = { webView?.findNext(false) }) { Text("Previous") }
                TextButton(onClick = { webView?.findNext(true) }) { Text("Next") }
                TextButton(onClick = { findOpen = false; webView?.clearMatches() }) { Text("Close") }
            }
        }
        if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        errorMessage?.let {
            Text(it, Modifier.fillMaxWidth().background(Color(0xFFFFE8E6)).padding(8.dp), color = Color(0xFF9B1C12))
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    val currentWebView = this
                    webView = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = false
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        mediaPlaybackRequiresUserGesture = true
                        userAgentString = DESKTOP_USER_AGENT
                    }
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(currentWebView, true)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            return if (scheme == "http" || scheme == "https") false else {
                                errorMessage = "Blocked external link: ${request.url}"
                                true
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            errorMessage = null
                            address = url
                            tabs = tabs.map { if (it.id == activeTabId) it.copy(url = url) else it }
                            refreshNavigationState(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            address = url
                            pageTitle = view.title ?: Uri.parse(url).host.orEmpty()
                            tabs = tabs.map { if (it.id == activeTabId) it.copy(title = pageTitle, url = url) else it }
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
                            if (!title.isNullOrBlank()) {
                                pageTitle = title
                                tabs = tabs.map { if (it.id == activeTabId) it.copy(title = title) else it }
                            }
                        }
                    }
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        runCatching {
                            val request = DownloadManager.Request(Uri.parse(url)).apply {
                                setMimeType(mimeType)
                                addRequestHeader("User-Agent", userAgent)
                                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                                setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType))
                            }
                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        }.onFailure { errorMessage = "Download failed: ${it.message}" }
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    loadUrl(HOME_PAGE)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

@Composable
private fun BrowserToolbarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(19.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}
