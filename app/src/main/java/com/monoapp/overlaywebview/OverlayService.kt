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
import android.widget.TextView // Import TextView
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
                Triple(1000, 1200, 1.0f)    // XLarge
    )
    private val sizeLabels = listOf("S", "M", "L","XL")
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
            // Initial flags: Not touch modal, watch outside touch.
            // These flags will be updated by toggleFocus()
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

        // Setup size change button
        setupSizeButton()

        // Setup URL change button
        setupUrlButton()

        // Setup focus button
        setupFocusButton() // Call the new setupFocusButton method

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
        val sizeButton = overlayView.findViewById<View>(R.id.sizeButton)
        val urlButton = overlayView.findViewById<View>(R.id.urlButton)
        val focusButton = overlayView.findViewById<View>(R.id.focusButton) // Get reference to focus button
        val closeButton = overlayView.findViewById<View>(R.id.closeButton)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragBar.setOnTouchListener { v, event ->
            // Exclude button areas from drag
            if (isTouchOnButton(event, sizeButton) ||
                isTouchOnButton(event, urlButton) ||
                isTouchOnButton(event, focusButton) || // Include focus button
                isTouchOnButton(event, closeButton)) {
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

    private fun setupSizeButton() {
        val sizeButton = overlayView.findViewById<TextView>(R.id.sizeButton)
        sizeButton.text = sizeLabels[currentSizeIndex]

        sizeButton.setOnClickListener {
            // Change to next size
            currentSizeIndex = (currentSizeIndex + 1) % sizeOptions.size

            // Update window size
            params.width = sizeOptions[currentSizeIndex].first
            params.height = sizeOptions[currentSizeIndex].second

            // Update button text
            sizeButton.text = sizeLabels[currentSizeIndex]

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
    }

    private fun setupUrlButton() {
        val urlButton = overlayView.findViewById<View>(R.id.urlButton)
        urlButton.setOnClickListener {
            showUrlDialog()
        }
    }

    private fun showUrlDialog() {
        val editText = EditText(this)
        editText.setText(webView.url ?: "") // 現在のWebViewのURLをデフォルト値として表示

        val dialogBuilder = AlertDialog.Builder(this, R.style.AppAlertDialogTheme) // カスタムテーマを適用
            .setTitle("URLまたは検索ワードを入力")
            .setMessage("URLを直接入力するか、検索ワードを入力してください:")
            .setView(editText)
            .setPositiveButton("開く") { _, _ -> // ボタン名を「OK」から「開く」に変更
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
                    // URLを保存。ここでは「開く」ボタンを押したら必ず保存される挙動とする
                    sharedPreferences.edit().putString("last_url", targetUrl).apply()
                }
            }
            .setNegativeButton("キャンセル", null)

        // 「今のURLを保存」ボタンの追加
        dialogBuilder.setNeutralButton("今のURLを保存") { _, _ ->
            val currentUrl = webView.url // WebViewの現在のURLを取得
            if (currentUrl != null && currentUrl.isNotEmpty()) {
                sharedPreferences.edit().putString("last_url", currentUrl).apply()
                Toast.makeText(this, "現在のURLを保存しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "保存できるURLがありません", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // 「最後に開いていたページを保存」は、現在の「開く」ボタンで自動的に保存されるため、
        // 明示的なボタンは不要かもしれません。
        // もし「最後に開いていた」が別の意味（例：アプリ終了時のURL）であれば、別の保存キーで管理が必要です。
        // 今回は「開く」ボタンでロードしたURLが「last_url」として保存されるので、これで対応します。


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

    private fun setupFocusButton() {
        val focusButton = overlayView.findViewById<TextView>(R.id.focusButton)
        updateFocusButtonText(focusButton) // Set initial text

        focusButton.setOnClickListener {
            toggleFocus()
            updateFocusButtonText(focusButton) // Update text after toggling
        }
    }

    private fun updateFocusButtonText(button: TextView) {
        button.text = if (isFocusEnabled)  "🚫" else "⌨"// Keyboard or No-entry symbol
        button.setBackgroundColor(if (isFocusEnabled)  0xFF9E9E9E.toInt() else 0xFF673AB7.toInt() )  // Purple if enabled, Grey if disabled
    }

    private fun toggleFocus() {
        isFocusEnabled = !isFocusEnabled
        if (isFocusEnabled) {
            // When focus is enabled, allow touch events to go through to WebView
            // FLAG_NOT_FOCUSABLE allows the overlay to not consume input focus,
            // letting the WebView receive focus.
            // FLAG_NOT_TOUCH_MODAL allows touches outside the window to be received
            // by windows underneath, but we want touches inside the overlay to be handled.
            // So we remove FLAG_NOT_TOUCH_MODAL to make the overlay "modal" to touches.
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            // Or if you want the webview to always handle touches when focused:
            // params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            // When focus is disabled, prevent WebView from receiving touches,
            // and allow dragging.
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
            // Stop the service and end the overlay
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