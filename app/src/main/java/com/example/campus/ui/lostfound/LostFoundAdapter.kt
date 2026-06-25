package com.example.campus.ui.lostfound

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campus.data.local.entity.LostFoundEntity
import com.example.campus.databinding.ItemLostFoundBinding

/**
 * 失物招领列表适配器。
 *
 * 实现要点：
 * - 基于 [ListAdapter] + [DiffUtil] 进行增量刷新
 * - 通过 [LostFoundEntity.type] 区分“寻物/招领”，并以颜色提示
 * - 点击事件通过 [onItemClick] 上抛，由页面决定是否进入详情或打开地图
 *
 * 注意：
 * - [LostFoundEntity.contactInfo] 可能包含敏感信息，后续可改为“用户点击后再显示”
 */
class LostFoundAdapter(private val onItemClick: (LostFoundEntity) -> Unit) :
    ListAdapter<LostFoundEntity, LostFoundAdapter.LostFoundViewHolder>(LostFoundDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LostFoundViewHolder {
        val binding = ItemLostFoundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LostFoundViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: LostFoundViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 失物招领条目 ViewHolder，负责将 [LostFoundEntity] 绑定到 item_lost_found 布局。
     */
    class LostFoundViewHolder(
        private val binding: ItemLostFoundBinding,
        private val onItemClick: (LostFoundEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LostFoundEntity) {
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvLocation.text = "地点: ${item.location}"
            binding.tvContact.text = "联系: ${item.contactInfo}"
            
            binding.tvType.text = if (item.type == "LOST") "寻物" else "招领"
            binding.tvType.setBackgroundColor(if (item.type == "LOST") Color.RED else Color.GREEN)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    /**
     * Diff 回调：以 [LostFoundEntity.id] 作为稳定唯一标识。
     */
    class LostFoundDiffCallback : DiffUtil.ItemCallback<LostFoundEntity>() {
        override fun areItemsTheSame(oldItem: LostFoundEntity, newItem: LostFoundEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LostFoundEntity, newItem: LostFoundEntity): Boolean {
            return oldItem == newItem
        }
    }
}
