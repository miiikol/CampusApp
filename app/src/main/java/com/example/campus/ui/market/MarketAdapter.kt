package com.example.campus.ui.market

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.data.local.entity.MarketEntity
import com.example.campus.databinding.ItemMarketBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * 二手市场列表适配器。
 *
 * 实现要点：
 * - 基于 [ListAdapter] + [DiffUtil] 实现高效刷新
 * - 使用 Glide 加载主图 [MarketEntity.imageUrl]
 * - 点击事件通过 [onItemClick] 上抛给页面处理（例如进入详情页）
 */
class MarketAdapter(private val onItemClick: (MarketEntity) -> Unit) :
    ListAdapter<MarketEntity, MarketAdapter.MarketViewHolder>(MarketDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarketViewHolder {
        val binding = ItemMarketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MarketViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: MarketViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 商品条目 ViewHolder，负责将 [MarketEntity] 绑定到 item_market 布局。
     */
    class MarketViewHolder(
        private val binding: ItemMarketBinding,
        private val onItemClick: (MarketEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MarketEntity) {
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvPrice.text = NumberFormat.getCurrencyInstance(Locale.CHINA).format(item.price)
            binding.tvSeller.text = "卖家: ${item.sellerId}"

            // 后端状态码映射为中文文案，无特殊状态时隐藏状态标签
            val statusText = when (item.status?.uppercase(Locale.getDefault())) {
                "OFFLINE" -> "已下架"
                "SOLD" -> "已售"
                "PENDING" -> "审核中"
                "REJECTED" -> "未通过"
                else -> null
            }
            if (statusText == null) {
                binding.tvStatus.visibility = android.view.View.GONE
                binding.tvStatus.text = ""
            } else {
                binding.tvStatus.visibility = android.view.View.VISIBLE
                binding.tvStatus.text = statusText
            }

            // 有图用 Glide 加载，无图则显示占位图
            if (!item.imageUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(binding.ivMarketImage)
            } else {
                binding.ivMarketImage.setImageResource(R.drawable.ic_launcher_foreground)
            }

            // 点击条目上抛回调，由页面跳转到详情页
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    /**
     * Diff 回调：以 [MarketEntity.id] 作为稳定唯一标识。
     */
    class MarketDiffCallback : DiffUtil.ItemCallback<MarketEntity>() {
        override fun areItemsTheSame(oldItem: MarketEntity, newItem: MarketEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MarketEntity, newItem: MarketEntity): Boolean {
            return oldItem == newItem
        }
    }
}
