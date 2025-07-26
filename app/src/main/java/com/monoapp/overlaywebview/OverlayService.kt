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

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences

    // サイズの設定（ズーム倍率も連動）
    private val sizeOptions = listOf(
        Triple(400, 600, 0.7f),    // Small - 0.6x zoom
        Triple(600, 1000, 0.8f),    // Medium - 0.8x zoom
        Triple(800, 1200, 0.9f)    // Large - 1.0x zoom
    )
    private val sizeLabels = listOf("S", "M", "L")
    private var currentSizeIndex = 1 // デフォルトはMedium (M)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sharedPreferences = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

        // 保存されたサイズ設定を取得
        currentSizeIndex = sharedPreferences.getInt("window_size_index", 1) // デフォルトはMedium

        // 保存されたURLを取得、なければデフォルトを使用
        val url = intent?.getStringExtra("url") ?: sharedPreferences.getString("last_url", "https://example.com") ?: "https://example.com"

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        // WindowManager.LayoutParams の設定
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            sizeOptions[currentSizeIndex].first,  // 幅
            sizeOptions[currentSizeIndex].second, // 高さ
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

        // WebView 設定
        setupWebView(url)

        // ドラッグ機能の設定
        setupDragFunctionality()

        // サイズ変更ボタンの設定
        setupSizeButton()

        // URL変更ボタンの設定
        setupUrlButton()

        // 終了ボタンの設定
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

        // 初期ズーム設定（サイズ連動）
        applyZoom()
        webView.loadUrl(url)
    }

    private fun applyZoom() {
        val zoomLevel = sizeOptions[currentSizeIndex].third
        webView.setInitialScale((zoomLevel * 100).toInt())
        webView.settings.textZoom = (zoomLevel * 100).toInt()
      //  webView.reload()
    }

    private fun setupDragFunctionality() {
        val dragBar = overlayView.findViewById<View>(R.id.dragBar)
        val sizeButton = overlayView.findViewById<View>(R.id.sizeButton)
        val urlButton = overlayView.findViewById<View>(R.id.urlButton)
        val closeButton = overlayView.findViewById<View>(R.id.closeButton)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragBar.setOnTouchListener { v, event ->
            // ボタンの領域はドラッグ対象外
            if (isTouchOnButton(event, sizeButton) ||
                isTouchOnButton(event, urlButton) ||
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

        // クリック可能にする
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
        val sizeButton = overlayView.findViewById<android.widget.TextView>(R.id.sizeButton)
        sizeButton.text = sizeLabels[currentSizeIndex]

        sizeButton.setOnClickListener {
            // 次のサイズに変更
            currentSizeIndex = (currentSizeIndex + 1) % sizeOptions.size

            // ウィンドウサイズを更新
            params.width = sizeOptions[currentSizeIndex].first
            params.height = sizeOptions[currentSizeIndex].second

            // ボタンのテキストを更新
            sizeButton.text = sizeLabels[currentSizeIndex]

            // サイズに連動してズームも変更
            applyZoom()

            // サイズ設定を保存
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
        editText.setText(webView.url ?: "")

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("URL変更")
            .setMessage("新しいURLを入力してください:")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val newUrl = editText.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    var finalUrl = newUrl
                    // プロトコルが指定されていない場合はhttpsを追加
                    if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                        finalUrl = "https://$finalUrl"
                    }

                    // URLを変更
                    webView.loadUrl(finalUrl)

                    // URLを保存
                    sharedPreferences.edit().putString("last_url", finalUrl).apply()
                }
            }
            .setNegativeButton("キャンセル", null)
            .create()

        // ダイアログのウィンドウタイプを設定
        dialog.window?.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )

        dialog.show()
    }

    private fun setupCloseButton() {
        val closeButton = overlayView.findViewById<View>(R.id.closeButton)
        closeButton.setOnClickListener {
            // サービスを停止してオーバーレイを終了
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