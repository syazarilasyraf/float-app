package com.floatoverlay.app.ui.minecraft

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.Material

class MaterialAdapter(
    private var materials: List<Material>,
    private val listener: MaterialListener
) : RecyclerView.Adapter<MaterialAdapter.ViewHolder>() {

    interface MaterialListener {
        fun onToggle(material: Material, collected: Boolean)
        fun onDelete(material: Material)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.materialCheckBox)
        val name: TextView = itemView.findViewById(R.id.materialName)
        val quantity: TextView = itemView.findViewById(R.id.materialQuantity)
        val delete: ImageButton = itemView.findViewById(R.id.deleteMaterialButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_material, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val material = materials[position]
        holder.name.text = material.name
        holder.quantity.text = material.quantity.toString()
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = material.collected
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            listener.onToggle(material, isChecked)
        }
        holder.delete.setOnClickListener { listener.onDelete(material) }
    }

    override fun getItemCount(): Int = materials.size

    fun updateData(newMaterials: List<Material>) {
        materials = newMaterials
        notifyDataSetChanged()
    }
}
