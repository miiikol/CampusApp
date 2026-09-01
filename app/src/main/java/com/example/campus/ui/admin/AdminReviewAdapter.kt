package com.example.campus.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.campus.data.remote.dto.AdminPendingReviewDto
import com.example.campus.databinding.ItemAdminReviewBinding

/**
 * 管理员审核列表适配器。
 *
 * 每条审核项展示类型(二手/失物招领)、标题、摘要，
 * 并提供通过/驳回两个操作按钮。
 */
class AdminReviewAdapter(
    private val onApprove: (AdminPendingReviewDto) -> Unit,
    private val onReject: (AdminPendingReviewDto) -> Unit
) : ListAdapter<AdminPendingReviewDto, AdminReviewAdapter.AdminReviewViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminReviewViewHolder {
        val binding = ItemAdminReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdminReviewViewHolder(binding, onApprove, onReject)
    }

    override fun onBindViewHolder(holder: AdminReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AdminReviewViewHolder(
        private val binding: ItemAdminReviewBinding,
        private val onApprove: (AdminPendingReviewDto) -> Unit,
        private val onReject: (AdminPendingReviewDto) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AdminPendingReviewDto) {
            // 按内容类型展示中文标签：二手商品 / 失物招领
            binding.tvType.text = if (item.itemType.equals("MARKET", true)) "二手商品" else "失物招领"
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.btnApprove.setOnClickListener { onApprove(item) }
            binding.btnReject.setOnClickListener { onReject(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AdminPendingReviewDto>() {
        // 以「类型 + ID」作为唯一键判断是否为同一条审核项
        override fun areItemsTheSame(oldItem: AdminPendingReviewDto, newItem: AdminPendingReviewDto): Boolean {
            return oldItem.itemType == newItem.itemType && oldItem.itemId == newItem.itemId
        }

        override fun areContentsTheSame(oldItem: AdminPendingReviewDto, newItem: AdminPendingReviewDto): Boolean {
            return oldItem == newItem
        }
    }
}
