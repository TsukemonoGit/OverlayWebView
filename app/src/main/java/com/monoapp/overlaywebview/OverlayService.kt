package com.monoapp.overlaywebview

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.util.DisplayMetrics
import android.content.res.Configuration 

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences

    // ★★★ズームレベルのためのsizeOptionsを再定義★★★
    // 今回はウィンドウの物理サイズではなく、ズームの選択肢として使用します
    private val zoomOptions = listOf(
        0.5f,  // 50%
        0.7f,  // 70%
        0.8f,  // 80%
        0.9f,  // 90%
        1.0f,  // 100% (デフォルト)
        1.1f,  // 110%
        1.2f,  // 120%
        1.5f,  // 150%
        2.0f   // 200%
    )
    private var currentZoomIndex = 4 // 1.0f (100%) をデフォルトにする (zoomOptionsのインデックス)

    private var isFocusEnabled = false

    private var maxScreenWidth = 0
    private var maxScreenHeight = 0

    private var isResizing = false
    private var initialWidth = 0
    private var initialHeight = 0
    private var initialX = 0 // ドラッグ移動のための初期座標 (params.x)
    private var initialY = 0 // ドラッグ移動のための初期座標 (params.y)
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sharedPreferences = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        maxScreenWidth = displayMetrics.widthPixels
        maxScreenHeight = displayMetrics.heightPixels

        // ★★★保存されたズーム設定を読み込む★★★
        currentZoomIndex = sharedPreferences.getInt("webview_zoom_index", currentZoomIndex)
        // zoomOptionsの範囲外にならないように調整
        if (currentZoomIndex < 0) currentZoomIndex = 0
        if (currentZoomIndex >= zoomOptions.size) currentZoomIndex = zoomOptions.lastIndex


        val url = intent?.getStringExtra("url") ?: sharedPreferences.getString("last_url", "https://example.com") ?: "https://example.com"

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 初期ウィンドウサイズを設定 (前回と同様、画面の60%程度)
        val defaultWidth = (maxScreenWidth * 0.6f).toInt()
        val defaultHeight = (maxScreenHeight * 0.6f).toInt()

        params = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 100

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        setupWebView(url)

        setupDragFunctionality()
        setupResizeHandle()

        // ★★★ズームボタンのセットアップを呼び出す★★★
        setupZoomButtons()

        setupUrlButton()

        setupRefreshButton()

        // ★★★ 戻るボタンのセットアップを追加 ★★★
        setupBackButton()

        setupFocusButton()

        setupCloseButton()
        setupResizeHandleStyle()
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

        // ★★★ズームレベルを適用★★★
        applyZoom(zoomOptions[currentZoomIndex])
        webView.loadUrl(url)
    }

    // applyZoom メソッドの引数はズームレベルを直接受け取る
    private fun applyZoom(zoomLevel: Float) {
        webView.setInitialScale((zoomLevel * 100).toInt())
        webView.settings.textZoom = (zoomLevel * 100).toInt()
        // ズームレベルを保存
        sharedPreferences.edit().putInt("webview_zoom_index", currentZoomIndex).apply()
    }

    private fun setupDragFunctionality() {
        val dragBar = overlayView.findViewById<View>(R.id.dragBar)

        dragBar.setOnTouchListener { v, event ->
            // ボタン領域にタッチしている場合はドラッグを無効化
            if (isTouchOnAnyButton(event)) {
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

                    // 画面の端からはみ出さないように位置を調整
                    params.x = Math.max(0, Math.min(params.x, maxScreenWidth - params.width))
                    params.y = Math.max(0, Math.min(params.y, maxScreenHeight - params.height))

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
        dragBar.isClickable = true
    }

    private fun setupResizeHandle() {
        val resizeHandle = overlayView.findViewById<View>(R.id.resizeHandle)

        resizeHandle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizing = true
                    initialWidth = params.width
                    initialHeight = params.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        val newWidth = initialWidth + dx
                        val newHeight = initialHeight + dy

                        // 最小サイズを設定（例: 画面の20%）
                        val minWidth = (maxScreenWidth * 0.2f).toInt()
                        val minHeight = (maxScreenHeight * 0.2f).toInt()

                        // 最大サイズと最小サイズでクリップ (画面の90%を維持)
                        params.width = Math.max(minWidth, Math.min(newWidth, (maxScreenWidth * 0.90f).toInt()))
                        params.height = Math.max(minHeight, Math.min(newHeight, (maxScreenHeight * 0.90f).toInt()))

                        try {
                            windowManager.updateViewLayout(overlayView, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isResizing = false
                    v.performClick()
                    false
                }
                else -> false
            }
        }
    }


    private fun isTouchInViewBounds(touchX: Float, touchY: Float, view: View): Boolean {
        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val viewLeft = viewLocation[0]
        val viewTop = viewLocation[1]
        val viewRight = viewLeft + view.width
        val viewBottom = viewTop + view.height

        return touchX >= viewLeft && touchX <= viewRight &&
                touchY >= viewTop && touchY <= viewBottom
    }

    private fun isTouchOnAnyButton(event: MotionEvent): Boolean {
        val sizeDecreaseButton = overlayView.findViewById<View>(R.id.sizeDecreaseButton) // 復活
        val sizeIncreaseButton = overlayView.findViewById<View>(R.id.sizeIncreaseButton) // 復活
        val urlButton = overlayView.findViewById<View>(R.id.urlButton)
        val refreshButton = overlayView.findViewById<View>(R.id.refreshButton)
        val backButton = overlayView.findViewById<View>(R.id.backButton) // ★★★ 追加 ★★★
        val focusButton = overlayView.findViewById<View>(R.id.focusButton)
        val closeButton = overlayView.findViewById<View>(R.id.closeButton)

        return isTouchInViewBounds(event.rawX, event.rawY, sizeDecreaseButton) ||
                isTouchInViewBounds(event.rawX, event.rawY, sizeIncreaseButton) ||
                isTouchInViewBounds(event.rawX, event.rawY, urlButton) ||
                isTouchInViewBounds(event.rawX, event.rawY, refreshButton) ||
                isTouchInViewBounds(event.rawX, event.rawY, backButton) || // ★★★ 追加 ★★★
                isTouchInViewBounds(event.rawX, event.rawY, focusButton) ||
                isTouchInViewBounds(event.rawX, event.rawY, closeButton)
    }

    // ★★★ズームボタンのセットアップ (名称変更 & ロジック修正)★★★
    private fun setupZoomButtons() {
        val zoomDecreaseButton = overlayView.findViewById<TextView>(R.id.sizeDecreaseButton)
        val zoomIncreaseButton = overlayView.findViewById<TextView>(R.id.sizeIncreaseButton)

        updateZoomButtonStates(zoomDecreaseButton, zoomIncreaseButton)

        zoomDecreaseButton.setOnClickListener {
            if (currentZoomIndex > 0) {
                currentZoomIndex--
                applyZoom(zoomOptions[currentZoomIndex])
                updateZoomButtonStates(zoomDecreaseButton, zoomIncreaseButton)
            }
        }

        zoomIncreaseButton.setOnClickListener {
            if (currentZoomIndex < zoomOptions.size - 1) {
                currentZoomIndex++
                applyZoom(zoomOptions[currentZoomIndex])
                updateZoomButtonStates(zoomDecreaseButton, zoomIncreaseButton)
            }
        }
    }

    // ★★★ズームボタンの状態更新 (名称変更 & ロジック修正)★★★
    private fun updateZoomButtonStates(decreaseButton: TextView, increaseButton: TextView) {
        if (currentZoomIndex <= 0) {
            decreaseButton.isEnabled = false
            decreaseButton.setBackgroundColor(0xFF9E9E9E.toInt()) // Grey for disabled
            decreaseButton.alpha = 0.5f
        } else {
            decreaseButton.isEnabled = true
            decreaseButton.setBackgroundColor(0xFF4CAF50.toInt()) // Green for enabled
            decreaseButton.alpha = 1.0f
        }

        if (currentZoomIndex >= zoomOptions.size - 1) {
            increaseButton.isEnabled = false
            increaseButton.setBackgroundColor(0xFF9E9E9E.toInt()) // Grey for disabled
            increaseButton.alpha = 0.5f
        } else {
            increaseButton.isEnabled = true
            increaseButton.setBackgroundColor(0xFF4CAF50.toInt()) // Green for enabled
            increaseButton.alpha = 1.0f
        }
    }

    // updateWindowSizeはズームボタンではなく、ウィンドウの物理サイズ変更に使われるが、
    // 今回は右下スワイプが担当するため、このメソッドは直接は呼ばれません。
    // 将来的な拡張のために残すか、完全に削除するかは判断次第ですが、現時点では直接の呼び出しはないでしょう。
    private fun updateWindowSize() {
        // このメソッドは、ウィンドウの物理サイズを特定のプリセットに戻す必要がある場合に利用できます。
        // 現在の右下スワイプによるリサイズ機能とは独立しています。
        // applyZoom()はズームボタンで制御されるため、ここからは呼び出しません。
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
    private fun setupResizeHandleStyle() {
        val resizeHandle = overlayView.findViewById<View>(R.id.resizeHandle)
        val size = 32f // サイズはXMLと合わせる

        val path = android.graphics.Path()
        path.moveTo(0f, size) // 左下
        path.lineTo(size, size) // 右下
        path.lineTo(size, 0f) // 右上
        path.close()

        val paint = Paint()
        paint.color = Color.parseColor("#DDDDDD") // 明るいグレー
        paint.style = Paint.Style.FILL

        val shape = PathShape(path, size, size)
        val drawable = ShapeDrawable(shape)
        drawable.paint.set(paint)

        resizeHandle?.background = drawable
    }
    private fun setupRefreshButton() {
        val refreshButton = overlayView.findViewById<View>(R.id.refreshButton)
        refreshButton.setOnClickListener {
            webView.reload()
            Toast.makeText(this, "ページを更新しました", Toast.LENGTH_SHORT).show()
        }
    }

    // ★★★ 戻るボタンのセットアップメソッドを追加 ★★★
    private fun setupBackButton() {
        val backButton = overlayView.findViewById<View>(R.id.backButton)
        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
                Toast.makeText(this, "前のページに戻りました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "これ以上戻るページはありません", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFocusButton() {
        val focusButton = overlayView.findViewById<TextView>(R.id.focusButton)
        updateFocusButtonText(focusButton)

        focusButton.setOnClickListener {
            toggleFocus()
            updateFocusButtonText(focusButton)
        }
    }

    private fun updateFocusButtonText(button: TextView) {
        button.text = if (isFocusEnabled) "🚫" else "⌨"
        button.setBackgroundColor(if (isFocusEnabled) 0xFF9E9E9E.toInt() else 0xFF673AB7.toInt())
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 画面の向きが変わったときに画面サイズを再取得
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30 (Android 11) 以降
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            maxScreenWidth = bounds.width()
            maxScreenHeight = bounds.height()
        } else { // API 29 (Android 10) 以前
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            maxScreenWidth = displayMetrics.widthPixels
            maxScreenHeight = displayMetrics.heightPixels
        }

        // ウィンドウの位置とサイズを調整（任意）
        // 画面サイズが変わった際に、既存のウィンドウが画面外にはみ出さないように、
        // あるいは新しい画面サイズに合わせて中央に再配置するなどのロジックを追加できます。
        // 例: はみ出し防止
        params.x = Math.max(0, Math.min(params.x, maxScreenWidth - params.width))
        params.y = Math.max(0, Math.min(params.y, maxScreenHeight - params.height))
        // 例: 中央に再配置
        // params.x = (maxScreenWidth - params.width) / 2
        // params.y = (maxScreenHeight - params.height) / 2

        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Toast.makeText(this, "画面の向きが変更されました: 可動範囲を更新", Toast.LENGTH_SHORT).show()
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