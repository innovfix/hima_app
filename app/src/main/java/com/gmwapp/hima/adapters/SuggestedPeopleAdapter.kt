package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.databinding.ItemSuggestedPersonBinding
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData

class SuggestedPeopleAdapter(
    private val activity: Activity,
    private var people: List<FemaleUsersResponseData>
) : RecyclerView.Adapter<SuggestedPeopleAdapter.PersonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val binding = ItemSuggestedPersonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PersonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(people[position])
    }

    override fun getItemCount(): Int = people.size

    fun updateData(newPeople: List<FemaleUsersResponseData>) {
        people = newPeople
        notifyDataSetChanged()
    }

    inner class PersonViewHolder(
        private val binding: ItemSuggestedPersonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: FemaleUsersResponseData) {
            binding.tvName.text = user.name.replace(Regex("\\d+$"), "").trim()
                .ifEmpty { user.name }
            Glide.with(activity)
                .load(user.image)
                .apply(RequestOptions().circleCrop())
                .into(binding.ivAvatar)
        }
    }
}
