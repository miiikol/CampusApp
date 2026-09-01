package com.example.campus.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.data.local.entity.NewsEntity
import com.example.campus.databinding.ItemNewsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 资讯列表适配器。
 *
 * 实现要点：
 * - 基于 [ListAdapter] + [DiffUtil] 提升列表刷新性能
 * - 使用 Glide 加载封面图，并提供占位与错误兜底资源
 * - 通过回调 [onItemClick] 将点击事件上抛到 Fragment/Activity
 */
class NewsAdapter(private val onItemClick: (NewsEntity) -> Unit) :
    ListAdapter<NewsEntity, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 资讯条目 ViewHolder，负责将 [NewsEntity] 绑定到 Item 视图。
     */
    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val onItemClick: (NewsEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(news: NewsEntity) {
            binding.tvTitle.text = news.title
            binding.tvSummary.text = news.summary
            binding.tvDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(news.publishDate))

            // 有封面图时用 Glide 加载，否则使用兜底占位图
            if (!news.imageUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(news.imageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(binding.ivNewsImage)
            } else {
                binding.ivNewsImage.setImageResource(R.drawable.ic_launcher_foreground)
            }

            // 点击整条条目，将资讯实体上抛给调用方
            binding.root.setOnClickListener {
                onItemClick(news)
            }
        }
    }

    /**
     * Diff 回调：用于判断列表项是否为同一条数据以及内容是否变化。
     */
    class NewsDiffCallback : DiffUtil.ItemCallback<NewsEntity>() {
        override fun areItemsTheSame(oldItem: NewsEntity, newItem: NewsEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NewsEntity, newItem: NewsEntity): Boolean {
            return oldItem == newItem
        }
    }
}
