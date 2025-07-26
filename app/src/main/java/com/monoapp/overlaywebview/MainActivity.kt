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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var urlInput: EditText
    private lateinit var addUrlButton: Button
    private lateinit var urlListRecyclerView: RecyclerView
    private lateinit var urlListAdapter: UrlListAdapter
    private var urlList: MutableList<String> = mutableListOf()
    private lateinit var sharedPreferences: SharedPreferences
    private val URL_LIST_KEY = "saved_url_list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        addUrlButton = findViewById(R.id.addUrlButton)
        urlListRecyclerView = findViewById(R.id.urlListRecyclerView)

        sharedPreferences = getSharedPreferences("overlay_prefs", MODE_PRIVATE)

        // onCreateではRecyclerViewのセットアップのみ
        setupRecyclerView()

        // アプリ起動時に前回のURLでオーバーレイを起動するロジックは、
        // URLリストから選択する方式に変わるため、ここではコメントアウトまたは削除
        // val savedUrl = sharedPreferences.getString("last_url", "")
        // if (savedUrl?.isNotEmpty() == true && Settings.canDrawOverlays(this)) {
        //     startOverlay(savedUrl)
        //     finish()
        //     return
        // }

        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(this)) {
                sharedPreferences.getString("pending_url_for_permission", null)?.let { url ->
                    startOverlay(url)
                    sharedPreferences.edit().remove("pending_url_for_permission").apply()
                }
            } else {
                Toast.makeText(this, "オーバーレイ許可が必要です", Toast.LENGTH_SHORT).show()
            }
        }

        addUrlButton.setOnClickListener {
            val input = urlInput.text.toString().trim()
            if (input.isBlank()) {
                Toast.makeText(this, "URLまたは検索ワードを入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var targetUrl: String
            val urlPattern = Regex("^(https?|ftp)://.+|www\\..+|.+\\..+$")

            if (input.matches(urlPattern)) {
                targetUrl = input
                if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    targetUrl = "https://$targetUrl"
                }
            } else {
                targetUrl = "https://www.google.com/search?q=${Uri.encode(input)}"
            }

            addUrlToList(targetUrl) // リストに追加と保存
            urlInput.text.clear()
            Toast.makeText(this, "URLをリストに追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // ActivityがResumeされるたびにリストを再読み込みして更新
        loadUrlList()
        urlListAdapter.updateUrls(urlList) // アダプターを更新
    }

    private fun setupRecyclerView() {
        // urlListの初期化はloadUrlListで行うので、ここでは不要
        urlListAdapter = UrlListAdapter(
            urlList, // ここでurlListを渡す
            onUrlClick = { url ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    sharedPreferences.edit().putString("pending_url_for_permission", url).apply()
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                    overlayPermissionLauncher.launch(intent)
                } else {
                    startOverlay(url)
                }
            },
            onDeleteClick = { url ->
                removeUrlFromList(url)
                Toast.makeText(this, "URLを削除しました", Toast.LENGTH_SHORT).show()
            }
        )
        urlListRecyclerView.layoutManager = LinearLayoutManager(this)
        urlListRecyclerView.adapter = urlListAdapter
    }

    private fun loadUrlList() {
        val json = sharedPreferences.getString(URL_LIST_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            urlList = Gson().fromJson(json, type) ?: mutableListOf()
        } else {
            urlList = mutableListOf() // JSONがない場合は空のリストをセット
        }
    }

    private fun saveUrlList() {
        val json = Gson().toJson(urlList)
        sharedPreferences.edit().putString(URL_LIST_KEY, json).apply()
    }

    private fun addUrlToList(url: String) {
        if (!urlList.contains(url)) {
            urlList.add(0, url) // リストの先頭に追加して最新のものを上部に表示
            saveUrlList()
            urlListAdapter.updateUrls(urlList) // UIを更新
        } else {
            Toast.makeText(this, "そのURLはすでにリストにあります", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeUrlFromList(url: String) {
        urlList.remove(url)
        saveUrlList()
        urlListAdapter.updateUrls(urlList)
    }

    private fun startOverlay(url: String) {
        sharedPreferences.edit().putString("last_url", url).apply()

        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("url", url)
        startService(intent)
        finish()
    }
}