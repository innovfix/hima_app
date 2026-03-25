package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.SuggestedPeopleAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityHimaForYouBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.gmwapp.hima.viewmodels.WalletViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HimaForYouActivity : BaseActivity() {

    private lateinit var binding: ActivityHimaForYouBinding
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private val walletViewModel: WalletViewModel by viewModels()
    private lateinit var peopleAdapter: SuggestedPeopleAdapter

    private var cachedAutoPayCoinId: String? = null
    private var cachedAutoPayAmount: String? = null
    private var coinListLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHimaForYouBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
        loadPeople()
        loadCoinPackagesForAutoPay()
    }

    private fun initUI() {
        val choice = intent.getStringExtra(DConstants.WHY_HIMA_CHOICE) ?: "lonely"
        applyContent(choice)

        peopleAdapter = SuggestedPeopleAdapter(this, emptyList())
        binding.rvPeople.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvPeople.adapter = peopleAdapter

        binding.btnPay.setOnSingleClickListener {
            navigateToWallet()
        }

        binding.ivClose.setOnSingleClickListener {
            navigateToMain()
        }
    }

    private fun applyContent(choice: String) {
        when (choice) {
            "lonely" -> {
                binding.tvBigEmoji.text = "🤗"
                binding.tvTitle.text = getString(R.string.hima_for_you_title_lonely)
                binding.tvBody.text = getString(R.string.hima_for_you_body_lonely)
            }
            "breakup" -> {
                binding.tvBigEmoji.text = "💪"
                binding.tvTitle.text = getString(R.string.hima_for_you_title_breakup)
                binding.tvBody.text = getString(R.string.hima_for_you_body_breakup)
            }
            "friends" -> {
                binding.tvBigEmoji.text = "🎊"
                binding.tvTitle.text = getString(R.string.hima_for_you_title_friends)
                binding.tvBody.text = getString(R.string.hima_for_you_body_friends)
            }
            "fun" -> {
                binding.tvBigEmoji.text = "🥳"
                binding.tvTitle.text = getString(R.string.hima_for_you_title_fun)
                binding.tvBody.text = getString(R.string.hima_for_you_body_fun)
            }
            "understand" -> {
                binding.tvBigEmoji.text = "🫶"
                binding.tvTitle.text = getString(R.string.hima_for_you_title_understand)
                binding.tvBody.text = getString(R.string.hima_for_you_body_understand)
            }
            else -> {
                binding.tvBigEmoji.text = "🤗"
                binding.tvTitle.text = getString(R.string.hima_for_you_title_lonely)
                binding.tvBody.text = getString(R.string.hima_for_you_body_lonely)
            }
        }
    }

    private fun loadPeople() {
        binding.pbPeople.visibility = View.VISIBLE
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return

        femaleUsersViewModel.femaleUsersResponseLiveData.observe(this, Observer { response ->
            binding.pbPeople.visibility = View.GONE
            if (response?.success == true && !response.data.isNullOrEmpty()) {
                val limited = response.data.take(5)
                peopleAdapter.updateData(limited)
            }
        })

        femaleUsersViewModel.femaleUsersErrorLiveData.observe(this, Observer {
            binding.pbPeople.visibility = View.GONE
        })

        femaleUsersViewModel.getFemaleUsers(userId)
    }

    private fun loadCoinPackagesForAutoPay() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        walletViewModel.coinsLiveData.observe(this, Observer { response ->
            if (coinListLoaded) return@Observer
            if (response?.success != true || response.data.isNullOrEmpty()) return@Observer
            val cheapest = response.data.minByOrNull { it.price } ?: response.data[0]
            cachedAutoPayCoinId = cheapest.id.toString()
            cachedAutoPayAmount = cheapest.price.toString()
            coinListLoaded = true
        })
        walletViewModel.getCoins(userId)
    }

    private fun navigateToWallet() {
        android.util.Log.d("AutoPay", "navigateToWallet: coinId=$cachedAutoPayCoinId, amount=$cachedAutoPayAmount")
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
        intent.putExtra(DConstants.LANGUAGE, getIntent().getStringExtra(DConstants.LANGUAGE))
        if (cachedAutoPayCoinId != null && cachedAutoPayAmount != null) {
            intent.putExtra(DConstants.AUTO_PAY_COIN_ID, cachedAutoPayCoinId)
            intent.putExtra(DConstants.AUTO_PAY_AMOUNT, cachedAutoPayAmount)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(DConstants.AVATAR_ID, getIntent().getIntExtra(DConstants.AVATAR_ID, 0))
        intent.putExtra(DConstants.LANGUAGE, getIntent().getStringExtra(DConstants.LANGUAGE))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
