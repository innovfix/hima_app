package com.gmwapp.hima.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.models.UPIApp

class UPIAppDialogAdapter(
    private val upiApps: List<UPIApp>,
    private val selectedPackageName: String?,
    private val onAppSelected: (UPIApp) -> Unit
) : RecyclerView.Adapter<UPIAppDialogAdapter.UPIAppDialogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UPIAppDialogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_upi_app_dialog, parent, false)
        return UPIAppDialogViewHolder(view)
    }

    override fun onBindViewHolder(holder: UPIAppDialogViewHolder, position: Int) {
        val upiApp = upiApps[position]
        val isSelected = upiApp.packageName == selectedPackageName
        holder.bind(upiApp, isSelected)
        
        holder.itemView.setOnClickListener {
            onAppSelected(upiApp)
        }
    }

    override fun getItemCount() = upiApps.size

    class UPIAppDialogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.iv_upi_app_icon)
        private val appName: TextView = itemView.findViewById(R.id.tv_upi_app_name)
        private val selectedCheck: ImageView = itemView.findViewById(R.id.iv_selected_check)

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
            
            selectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}

