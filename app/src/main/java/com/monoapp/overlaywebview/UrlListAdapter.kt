package com.monoapp.overlaywebview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UrlListAdapter(
    private val urls: MutableList<String>,
    private val onUrlClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit // 削除用のリスナーを追加
) : RecyclerView.Adapter<UrlListAdapter.UrlViewHolder>() {

    class UrlViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val urlTextView: TextView = view.findViewById(R.id.urlTextView)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton) // 削除ボタン
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrlViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_url, parent, false)
        return UrlViewHolder(view)
    }

    override fun onBindViewHolder(holder: UrlViewHolder, position: Int) {
        val url = urls[position]
        holder.urlTextView.text = url
        holder.itemView.setOnClickListener { onUrlClick(url) }
        holder.deleteButton.setOnClickListener { onDeleteClick(url) }
    }

    override fun getItemCount() = urls.size

    // リスト更新用メソッド
    fun updateUrls(newUrls: List<String>) {
        urls.clear()
        urls.addAll(newUrls)
        notifyDataSetChanged()
    }
}