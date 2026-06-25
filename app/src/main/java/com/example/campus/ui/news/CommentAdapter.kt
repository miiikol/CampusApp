package com.example.campus.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.campus.R
import com.example.campus.data.remote.dto.CommentDto
import com.example.campus.databinding.ItemCommentBinding

/**
 * 评论列表适配器。
 *
 * 支持二级评论（子评论左侧缩进）、点赞/取消、回复、管理员屏蔽等交互。
 * 通过回调函数将事件上抛给 ViewModel 处理。
 */
class CommentAdapter(
    private val isAdmin: () -> Boolean,
    private val onBan: (CommentDto) -> Unit,
    private val onReply: (CommentDto) -> Unit,
    private val onToggleLike: (CommentDto) -> Unit
) : ListAdapter<CommentDto, CommentAdapter.CommentViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding, isAdmin, onBan, onReply, onToggleLike)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(
        private val binding: ItemCommentBinding,
        private val isAdmin: () -> Boolean,
        private val onBan: (CommentDto) -> Unit,
        private val onReply: (CommentDto) -> Unit,
        private val onToggleLike: (CommentDto) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CommentDto) {
            binding.tvUser.text = item.username
            binding.tvContent.text = item.content
            if (!item.avatarUrl.isNullOrBlank()) {
                Glide.with(binding.root.context)
                    .load(item.avatarUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_launcher_foreground)
            }
            val replyTo = item.replyToUsername?.takeIf { it.isNotBlank() }
            if (replyTo != null) {
                binding.tvReplyTo.visibility = android.view.View.VISIBLE
                binding.tvReplyTo.text = "回复 @$replyTo"
            } else {
                binding.tvReplyTo.visibility = android.view.View.GONE
                binding.tvReplyTo.text = ""
            }

            binding.tvLikeCount.text = if (item.likeCount > 0) "赞 ${item.likeCount}" else ""
            binding.btnLike.text = if (item.likedByMe) "已赞" else "点赞"
            binding.btnLike.setOnClickListener { onToggleLike(item) }
            binding.btnReply.setOnClickListener { onReply(item) }

            val showBan = isAdmin()
            binding.btnBan.visibility = if (showBan) android.view.View.VISIBLE else android.view.View.GONE
            binding.btnBan.setOnClickListener { onBan(item) }

            val density = binding.root.resources.displayMetrics.density
            val indent = if (item.parentCommentId != null) (24 * density).toInt() else 0
            binding.root.updatePadding(left = indent)
        }
    }

    private object Diff : DiffUtil.ItemCallback<CommentDto>() {
        override fun areItemsTheSame(oldItem: CommentDto, newItem: CommentDto): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CommentDto, newItem: CommentDto): Boolean = oldItem == newItem
    }
}
