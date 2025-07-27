package com.monoapp.overlaywebview

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences

    // Size settings (zoom level also linked)
    private val sizeOptions = listOf(
        Triple(600, 600, 0.7f),    // Small - 0.7x zoom
        Triple(600, 900, 0.8f),   // Medium - 0.8x zoom
        Triple(800, 1200, 0.9f),    // Large - 0.9x zoom
        Triple(1000, 1400, 1.0f)    // XLarge
    )
    private var currentSizeIndex = 1 // Default is Medium (M)
    private var isFocusEnabled = false // Track focus state

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sharedPreferences = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

        // Retrieve saved size setting
        currentSizeIndex = sharedPreferences.getInt("window_size_index", 1) // Default is Medium

        // Retrieve saved URL, use default if none
        val url = intent?.getStringExtra("url") ?: sharedPreferences.getString("last_url", "https://example.com") ?: "https://example.com"

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        // WindowManager.LayoutParams setup
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            sizeOptions[currentSizeIndex].first,  // Width
            sizeOptions[currentSizeIndex].second, // Height
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // WebView settings
        setupWebView(url)

        // Setup drag functionality
        setupDragFunctionality()

        // Setup size change buttons
        setupSizeButtons()

        // Setup URL change button
        setupUrlButton()

        // ★ここから追加★
        // Setup refresh button
        setupRefreshButton()
        // ★ここまで追加★

        // Setup focus button
        setupFocusButton()

        // Setup close button
        setupCloseButton()

        return START_STICKY
    }

    private fun setupWebView(url: String) {
        webView = overlayView.findViewById<WebView>(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(true)
        }
        webView.webViewClient = WebViewClient()

        // Initial zoom setting (linked to size)
        applyZoom()
        webView.loadUrl(url)
    }

    private fun applyZoom() {
        val zoomLevel = sizeOptions[currentSizeIndex].third
        webView.setInitialScale((zoomLevel * 100).toInt())
        webView.settings.textZoom = (zoomLevel * 100).toInt()
    }

    private fun setupDragFunctionality() {
        val dragBar = overlayView.findViewById<View>(R.id.dragBar)
        val sizeDecreaseButton = overlayView.findViewById<View>(R.id.sizeDecreaseButton)
        val sizeIncreaseButton = overlayView.findViewById<View>(R.id.sizeIncreaseButton)
        val urlButton = overlayView.findViewById<View>(R.id.urlButton)
        // ★ここから追加★
        val refreshButton = overlayView.findViewById<View>(R.id.refreshButton)
        // ★ここまで追加★
        val focusButton = overlayView.findViewById<View>(R.id.focusButton)
        val closeButton = overlayView.findViewById<View>(R.id.closeButton)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragBar.setOnTouchListener { v, event ->
            // Exclude button areas from drag
            if (isTouchOnButton(event, sizeDecreaseButton) ||
                isTouchOnButton(event, sizeIncreaseButton) ||
                isTouchOnButton(event, urlButton) ||
                isTouchOnButton(event, focusButton) ||
                isTouchOnButton(event, closeButton) ||
                // ★ここから追加★
                isTouchOnButton(event, refreshButton)) {
                // ★ここまで追加★
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()

                    try {
                        windowManager.updateViewLayout(overlayView, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    false
                }
                else -> false
            }
        }

        // Make it clickable
        dragBar.isClickable = true
    }

    private fun isTouchOnButton(event: MotionEvent, button: View): Boolean {
        val buttonLocation = IntArray(2)
        button.getLocationInWindow(buttonLocation)
        val buttonLeft = buttonLocation[0]
        val buttonRight = buttonLeft + button.width
        val buttonTop = buttonLocation[1]
        val buttonBottom = buttonTop + button.height

        return event.rawX >= buttonLeft && event.rawX <= buttonRight &&
                event.rawY >= buttonTop && event.rawY <= buttonBottom
    }

    private fun setupSizeButtons() {
        val sizeDecreaseButton = overlayView.findViewById<TextView>(R.id.sizeDecreaseButton)
        val sizeIncreaseButton = overlayView.findViewById<TextView>(R.id.sizeIncreaseButton)

        // Initial button state
        updateSizeButtonStates(sizeDecreaseButton, sizeIncreaseButton)

        sizeDecreaseButton.setOnClickListener {
            if (currentSizeIndex > 0) {
                currentSizeIndex--
                updateWindowSize()
                updateSizeButtonStates(sizeDecreaseButton, sizeIncreaseButton)
            }
        }

        sizeIncreaseButton.setOnClickListener {
            if (currentSizeIndex < sizeOptions.size - 1) {
                currentSizeIndex++
                updateWindowSize()
                updateSizeButtonStates(sizeDecreaseButton, sizeIncreaseButton)
            }
        }
    }

    private fun updateSizeButtonStates(decreaseButton: TextView, increaseButton: TextView) {
        // Update decrease button state
        if (currentSizeIndex <= 0) {
            decreaseButton.isEnabled = false
            decreaseButton.setBackgroundColor(0xFF9E9E9E.toInt()) // Grey for disabled
            decreaseButton.alpha = 0.5f
        } else {
            decreaseButton.isEnabled = true
            decreaseButton.setBackgroundColor(0xFF4CAF50.toInt()) // Green for enabled
            decreaseButton.alpha = 1.0f
        }

        // Update increase button state
        if (currentSizeIndex >= sizeOptions.size - 1) {
            increaseButton.isEnabled = false
            increaseButton.setBackgroundColor(0xFF9E9E9E.toInt()) // Grey for disabled
            increaseButton.alpha = 0.5f
        } else {
            increaseButton.isEnabled = true
            increaseButton.setBackgroundColor(0xFF4CAF50.toInt()) // Green for enabled
            increaseButton.alpha = 1.0f
        }
    }

    private fun updateWindowSize() {
        // Update window size
        params.width = sizeOptions[currentSizeIndex].first
        params.height = sizeOptions[currentSizeIndex].second

        // Change zoom linked to size
        applyZoom()

        // Save size setting
        sharedPreferences.edit().putInt("window_size_index", currentSizeIndex).apply()

        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUrlButton() {
        val urlButton = overlayView.findViewById<View>(R.id.urlButton)
        urlButton.setOnClickListener {
            showUrlDialog()
        }
    }

    private fun showUrlDialog() {
        val editText = EditText(this)
        editText.setText(webView.url ?: "")

        val dialogBuilder = AlertDialog.Builder(this, R.style.AppAlertDialogTheme)
            .setTitle("URLまたは検索ワードを入力")
            .setMessage("URLを直接入力するか、検索ワードを入力してください:")
            .setView(editText)
            .setPositiveButton("開く") { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    var targetUrl: String
                    val urlPattern = Regex("^(https?|ftp)://.+|www\\..+|.+\\..+$")

                    if (input.matches(urlPattern)) {
                        targetUrl = input
                        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                            targetUrl = "https://$targetUrl"
                        }
                    } else {
                        targetUrl = "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
                    }

                    webView.loadUrl(targetUrl)
                    sharedPreferences.edit().putString("last_url", targetUrl).apply()
                }
            }
            .setNegativeButton("キャンセル", null)

        dialogBuilder.setNeutralButton("今のURLを保存") { _, _ ->
            val currentUrl = webView.url
            if (currentUrl != null && currentUrl.isNotEmpty()) {
                sharedPreferences.edit().putString("last_url", currentUrl).apply()
                Toast.makeText(this, "現在のURLを保存しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "保存できるURLがありません", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = dialogBuilder.create()

        dialog.window?.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )

        dialog.show()
    }

    // ★ここから追加★
    private fun setupRefreshButton() {
        val refreshButton = overlayView.findViewById<View>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            webView.reload() // WebViewをリロードする
            Toast.makeText(this, "ページを更新しました", Toast.LENGTH_SHORT).show()
        }
    }
    // ★ここまで追加★

    private fun setupFocusButton() {
        val focusButton = overlayView.findViewById<TextView>(R.id.focusButton)
        updateFocusButtonText(focusButton)

        focusButton.setOnClickListener {
            toggleFocus()
            updateFocusButtonText(focusButton)
        }
    }

    private fun updateFocusButtonText(button: TextView) {
        button.text = if (isFocusEnabled)  "🚫" else "⌨"
        button.setBackgroundColor(if (isFocusEnabled)  0xFF9E9E9E.toInt() else 0xFF673AB7.toInt())
    }

    private fun toggleFocus() {
        isFocusEnabled = !isFocusEnabled
        if (isFocusEnabled) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupCloseButton() {
        val closeButton = overlayView.findViewById<View>(R.id.closeButton)
        closeButton.setOnClickListener {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::windowManager.isInitialized && ::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}