package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.AdapterLanguageBinding
import com.gmwapp.hima.retrofit.responses.Language
import com.gmwapp.hima.utils.setOnSingleClickListener


class LanguageAdapter(
    val activity: Activity,
    private val languages: ArrayList<Language>,
    val onItemSelectionListener: OnItemSelectionListener<Language>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Map native scripts for each language
    private val nativeScripts = mapOf(
        "Hindi" to "ह",
        "Telugu" to "త",
        "Malayalam" to "മ",
        "Kannada" to "ಕ",
        "Punjabi" to "ਪ",
        "Tamil" to "த",
        "Marathi" to "म",
        "Bengali" to "ব",
        "Assamese" to "অ",
        "Odia" to "ଓ",
        "Gujarati" to "ગ"
    )


    private val nativeNames = mapOf(
        "Hindi" to "हिंदी",
        "Telugu" to "తెలుగు",
        "Malayalam" to "മലയാളം",
        "Kannada" to "ಕನ್ನಡ",
        "Punjabi" to "ਪੰਜਾਬੀ",
        "Tamil" to "தமிழ்",
        "Marathi" to "मराठी",
        "Bengali" to "বাংলা",
        "Assamese" to "অসমীয়া",
        "Odia" to "ଓଡ଼ିଆ",
        "Gujarati" to "ગુજરાતી"
    )


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterLanguageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val language: Language = languages[position]

        // Set language name
        holder.binding.tvLanguage.text = language.name
        
        // Set native script and name
        holder.binding.ivLanguage.text = nativeScripts[language.name] ?: language.name.substring(0, 1)
        holder.binding.tvLanguageSubtitle.text = nativeNames[language.name] ?: language.name

        // Update selection state
        if (language.isSelected == true) {
            // Selected state
            holder.binding.main.setStrokeColor(ColorStateList.valueOf(activity.getColor(R.color.colorAccent)))
            holder.binding.main.strokeWidth = 6
            holder.binding.main.setCardBackgroundColor(activity.getColor(R.color.white))
            holder.binding.ivCheck.visibility = View.VISIBLE
        } else {
            // Unselected state
            holder.binding.main.setStrokeColor(ColorStateList.valueOf(activity.getColor(R.color.divider)))
            holder.binding.main.strokeWidth = 3
            holder.binding.main.setCardBackgroundColor(activity.getColor(R.color.white))
            holder.binding.ivCheck.visibility = View.GONE
        }

        holder.binding.main.setOnSingleClickListener {
            onItemSelectionListener.onItemSelected(language)
            languages.onEach { it.isSelected = false }
            language.isSelected = true
            languages[position] = language
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        return languages.size
    }

    internal class ItemHolder(val binding: AdapterLanguageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
