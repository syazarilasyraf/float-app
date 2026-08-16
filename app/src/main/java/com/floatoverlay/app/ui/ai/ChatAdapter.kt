package com.floatoverlay.app.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.Message

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private var messages: List<Message> = emptyList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleText: TextView = itemView.findViewById(R.id.messageText)
        val metaText: TextView = itemView.findViewById(R.id.messageMeta)
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].role) {
            Message.Role.USER -> R.layout.item_chat_user
            Message.Role.SYSTEM -> R.layout.item_chat_system
            else -> R.layout.item_chat_ai
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.bubbleText.text = message.content
        holder.metaText.text = when {
            message.toolCall != null -> "Tool: ${message.toolCall.toolName}"
            message.toolResult != null -> "Result: ${message.toolResult.toolName}"
            message.role == Message.Role.SYSTEM -> "System"
            else -> ""
        }
        holder.metaText.visibility = if (holder.metaText.text.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun getItemCount(): Int = messages.size

    fun submitList(newMessages: List<Message>) {
        messages = newMessages.filter { it.role != Message.Role.SYSTEM }
        notifyDataSetChanged()
    }
}
