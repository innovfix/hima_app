package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.models.UPIApp

class UPIAppAdapter(
    private val upiApps: MutableList<UPIApp>,
    private val onAppSelected: (UPIApp) -> Unit
) : RecyclerView.Adapter<UPIAppAdapter.UPIAppViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UPIAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_upi_app, parent, false)
        return UPIAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: UPIAppViewHolder, position: Int) {
        val upiApp = upiApps[position]
        holder.bind(upiApp, position == selectedPosition)
        
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            if (previousPosition != -1) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)
            onAppSelected(upiApp)
        }
    }

    override fun getItemCount() = upiApps.size

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        if (previousPosition != -1) {
            notifyItemChanged(previousPosition)
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition)
        }
    }

    class UPIAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.iv_upi_app_icon)
        private val appName: TextView = itemView.findViewById(R.id.tv_upi_app_name)
        private val selectedIndicator: View = itemView.findViewById(R.id.view_selected_indicator)

        fun bind(upiApp: UPIApp, isSelected: Boolean) {
            appName.text = upiApp.appName
            
            // Load app icon
            try {
                val icon = itemView.context.packageManager
                    .getApplicationIcon(upiApp.packageName)
                appIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                appIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }
            
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 1.0f else 0.6f
        }
    }
}

















