package com.monoapp.overlaywebview

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var urlInput: EditText
    private lateinit var startButton: Button
    private var pendingUrl: String? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        startButton = findViewById(R.id.startOverlayButton)

        // SharedPreferences初期化
        sharedPreferences = getSharedPreferences("url_prefs", MODE_PRIVATE)

        // 保存されたURLを復元
        val savedUrl = sharedPreferences.getString("last_url", "")
        if (savedUrl?.isNotEmpty() == true && Settings.canDrawOverlays(this)) {
            // 保存されたURLがあり、権限もある場合は直接オーバーレイを起動
            startOverlay(savedUrl)
            finish() // MainActivityを終了
            return
        }

        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(this)) {
                pendingUrl?.let { startOverlay(it) }
            } else {
                Toast.makeText(this, "オーバーレイ許可が必要です", Toast.LENGTH_SHORT).show()
            }
        }

        startButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isBlank()) {
                Toast.makeText(this, "URLを入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // URLを保存
            sharedPreferences.edit().putString("last_url", url).apply()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                pendingUrl = url
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            } else {
                startOverlay(url)
            }
        }
    }

    private fun startOverlay(url: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("url", url)
        startService(intent)
    }
}