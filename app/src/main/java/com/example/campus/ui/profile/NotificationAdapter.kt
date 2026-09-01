package com.example.campus.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campus.data.local.entity.NotificationEntity
import com.example.campus.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知列表适配器。
 *
 * 展示通知标题、内容与时间，已读通知以半透明样式区分。
 * 基于 [ListAdapter] + [DiffUtil] 实现增量刷新。
 */
class NotificationAdapter : ListAdapter<NotificationEntity, NotificationAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationEntity) {
            binding.tvTitle.text = item.title
            binding.tvContent.text = item.content
            binding.tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(item.createdAt))
            binding.root.alpha = if (item.isRead) 0.6f else 1.0f
        }
    }

    private object Diff : DiffUtil.ItemCallback<NotificationEntity>() {
        // 以通知 ID 作为唯一键判断是否为同一条通知
        override fun areItemsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity): Boolean = oldItem == newItem
    }
}
