package com.gmwapp.hima.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.databinding.ActivityCreatorLevelBinding
import com.gmwapp.hima.utils.setOnSingleClickListener

class CreatorLevelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorLevelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        binding.includeProfileToolbar.tvFlowTitle.text = "Creator Level"
        binding.includeProfileToolbar.cvBack.setOnSingleClickListener { finish() }
    }
}
