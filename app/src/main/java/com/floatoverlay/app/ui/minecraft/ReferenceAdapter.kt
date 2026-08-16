package com.floatoverlay.app.ui.minecraft

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.Reference

class ReferenceAdapter(
    private var references: List<Reference>,
    private val listener: ReferenceListener
) : RecyclerView.Adapter<ReferenceAdapter.ViewHolder>() {

    interface ReferenceListener {
        fun onDelete(reference: Reference)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.referenceImage)
        val delete: ImageButton = itemView.findViewById(R.id.deleteReferenceButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reference, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reference = references[position]
        if (reference.imageUri.isNotBlank()) {
            try {
                holder.image.setImageURI(Uri.parse(reference.imageUri))
            } catch (e: Exception) {
                holder.image.setImageResource(R.drawable.ic_overlay)
            }
        } else {
            holder.image.setImageResource(R.drawable.ic_overlay)
        }
        holder.delete.setOnClickListener { listener.onDelete(reference) }
    }

    override fun getItemCount(): Int = references.size

    fun updateData(newReferences: List<Reference>) {
        references = newReferences
        notifyDataSetChanged()
    }
}
