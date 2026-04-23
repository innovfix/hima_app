package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.CoinAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.dialogs.BottomSheetInsufficientCoinsPaywall.OnPaywallAddCoinsListener
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.WalletViewModel
import com.gmwapp.hima.widgets.DButton
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint

/**
 * Old-user variant of the subscribe sheet: yellow Subscribe Now banner and, optionally,
 * a wallet-style coin grid + Add Coins button below it.
 *
 * - Chat (non-subscriber, old user) opens this with `bannerOnly = true`.
 * - Any other caller can show the full sheet (banner + coin list + button).
 */
@AndroidEntryPoint
class BottomSheetOldUserSubscribe : BottomSheetDialogFragment() {

    private val walletViewModel: WalletViewModel by viewModels()

    private var onSubscribeClick: (() -> Unit)? = null
    private var selectedCoin: CoinsResponseData? = null
    private var btnAddCoins: DButton? = null
    private var addCoinsListener: OnPaywallAddCoinsListener? = null

    fun setOnSubscribeClickListener(listener: () -> Unit) {
        onSubscribeClick = listener
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        addCoinsListener = context as? OnPaywallAddCoinsListener
    }

    override fun onDetach() {
        addCoinsListener = null
        super.onDetach()
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.bottom_sheet_old_user_subscribe, container, false)

        val bannerOnly = arguments?.getBoolean(ARG_BANNER_ONLY, false) ?: false
        val titleText = arguments?.getString(ARG_TITLE)

        view.findViewById<TextView>(R.id.tv_title).apply {
            if (!titleText.isNullOrBlank()) text = titleText
        }
        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            dismissAllowingStateLoss()
        }

        val banner = view.findViewById<FrameLayout>(R.id.cv_subscribe_banner)
        val btnSubscribe = view.findViewById<MaterialButton>(R.id.btn_subscribe_now)
        val onBanner = View.OnClickListener {
            onSubscribeClick?.invoke()
            dismissAllowingStateLoss()
        }
        banner.setOnSingleClickListener { onBanner.onClick(banner) }
        btnSubscribe.setOnSingleClickListener { onBanner.onClick(btnSubscribe) }

        val section = view.findViewById<LinearLayout>(R.id.ll_talktime_section)
        if (bannerOnly) {
            section.visibility = View.GONE
        } else {
            section.visibility = View.VISIBLE
            setupCoinsGrid(view.findViewById(R.id.rv_coins))
            btnAddCoins = view.findViewById(R.id.btn_add_coins)
            btnAddCoins?.setOnSingleClickListener {
                val coin = selectedCoin ?: return@setOnSingleClickListener
                addCoinsListener?.onAddCoins(coin.price.toString(), coin.id)
                dismissAllowingStateLoss()
            }
            loadCoins()
        }

        return view
    }

    private fun setupCoinsGrid(recycler: RecyclerView) {
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
    }

    private fun loadCoins() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        walletViewModel.coinsLiveData.observe(viewLifecycleOwner) { response ->
            val list = response?.data ?: return@observe
            if (list.isEmpty()) return@observe
            list.onEach { it.isSelected = false }
            list[0].isSelected = true
            selectedCoin = list[0]
            updateAddCoinsLabel(list[0])
            val activity = activity ?: return@observe
            val recycler = view?.findViewById<RecyclerView>(R.id.rv_coins) ?: return@observe
            recycler.adapter = CoinAdapter(
                activity,
                ArrayList(list),
                object : OnItemSelectionListener<CoinsResponseData> {
                    override fun onItemSelected(coin: CoinsResponseData) {
                        selectedCoin = coin
                        updateAddCoinsLabel(coin)
                    }
                }
            )
        }
        walletViewModel.getCoins(userId)
    }

    private fun updateAddCoinsLabel(coin: CoinsResponseData) {
        btnAddCoins?.text = getString(R.string.add_quantity_coins, coin.coins)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isFitToContents = true
    }

    companion object {
        const val TAG = "BottomSheetOldUserSubscribe"
        private const val ARG_BANNER_ONLY = "bannerOnly"
        private const val ARG_TITLE = "title"

        fun newInstance(bannerOnly: Boolean = false, title: String? = null): BottomSheetOldUserSubscribe =
            BottomSheetOldUserSubscribe().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_BANNER_ONLY, bannerOnly)
                    if (title != null) putString(ARG_TITLE, title)
                }
            }
    }
}
