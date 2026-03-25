package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.WhyHimaChoice
import com.gmwapp.hima.adapters.WhyHimaChoiceAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityWhyHimaBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WhyHimaActivity : BaseActivity() {

    private lateinit var binding: ActivityWhyHimaBinding
    private var selectedChoice: WhyHimaChoice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhyHimaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
    }

    private fun initUI() {
        val choices = listOf(
            WhyHimaChoice("lonely", "😔", getString(R.string.choice_lonely)),
            WhyHimaChoice("breakup", "💔", getString(R.string.choice_breakup)),
            WhyHimaChoice("friends", "🤝", getString(R.string.choice_friends)),
            WhyHimaChoice("fun", "🎉", getString(R.string.choice_fun)),
            WhyHimaChoice("understand", "🫶", getString(R.string.choice_understand))
        )

        val adapter = WhyHimaChoiceAdapter(choices) { choice ->
            selectedChoice = choice
            updateContinueButton(true)
        }

        binding.rvChoices.layoutManager = LinearLayoutManager(this)
        binding.rvChoices.adapter = adapter

        binding.btnContinue.setOnSingleClickListener {
            if (selectedChoice == null) {
                Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show()
                return@setOnSingleClickListener
            }
            navigateToHimaForYou()
        }
    }

    private fun updateContinueButton(enabled: Boolean) {
        binding.btnContinue.backgroundTintList = if (enabled) {
            resources.getColorStateList(R.color.colorAccent, null)
        } else {
            resources.getColorStateList(R.color.kyc_button_disabled, null)
        }
    }

    private fun navigateToHimaForYou() {
        val intent = Intent(this, HimaForYouActivity::class.java)
        intent.putExtra(DConstants.WHY_HIMA_CHOICE, selectedChoice?.key)
        intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
        intent.putExtra(DConstants.LANGUAGE, getIntent().getStringExtra(DConstants.LANGUAGE))
        intent.putExtra(DConstants.GENDER, getIntent().getStringExtra(DConstants.GENDER))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
