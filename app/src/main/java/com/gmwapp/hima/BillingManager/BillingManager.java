package com.gmwapp.hima.BillingManager;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.gmwapp.hima.utils.DPreferences;
import com.gmwapp.hima.viewmodels.WalletViewModel;


import java.util.List;

public class BillingManager {
    private final BillingClient billingClient;
    private final Activity activity;
    private List<SkuDetails> skuDetailsList;
    private int userId;  // ✅ Declare userId at the class level
    private int coinId;  // ✅ Declare coinId at the class level

    public BillingManager(Activity activity) {
        this.activity = activity;
        billingClient = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();
        startBillingConnection();
    }

    private void startBillingConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "Billing Client is ready!");
                    queryAvailableProducts();
                } else {
                    Log.e("Billing", "Billing setup failed: " + billingResult.getResponseCode());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.e("Billing", "Billing service disconnected. Retrying...");
                startBillingConnection();
            }
        });
    }

    private void queryAvailableProducts() {
        List<String> skuList = List.of( "2", "3", "4", "5", "6", "7", "8", "9","coin_14"); // Ensure these match Play Console
        SkuDetailsParams params = SkuDetailsParams.newBuilder()
                .setSkusList(skuList)
                .setType(BillingClient.SkuType.INAPP)  // Use .SkuType.SUBS for subscriptions
                .build();

        billingClient.querySkuDetailsAsync(params, (billingResult, skuDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                this.skuDetailsList = skuDetailsList;

                if (skuDetailsList.isEmpty()) {
                    Log.e("Billing", "No products found in Google Play Console.");
                } else {
                    for (SkuDetails skuDetails : skuDetailsList) {
                        Log.d("Billing", "Product Found: " + skuDetails.getSku() + " Price: " + skuDetails.getPrice());
                    }
                }
            } else {
                Log.e("Billing", "Failed to fetch products: " + billingResult.getResponseCode());
            }
        });
    }

    public void purchaseProduct(String productId, int userId, int coinId) {
        if (skuDetailsList == null || skuDetailsList.isEmpty()) {
            Toast.makeText(activity, "No products available!", Toast.LENGTH_SHORT).show();
            return;
        }

        SkuDetails selectedProduct = skuDetailsList.stream()
                .filter(sku -> sku.getSku().equals(productId))
                .findFirst()
                .orElse(null);

        if (selectedProduct == null) {
            Toast.makeText(activity, "Product not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Save userId and coinId BEFORE launching billing
        this.userId = userId;
        this.coinId = coinId;

        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setSkuDetails(selectedProduct)
                .build();

        billingClient.launchBillingFlow(activity, params);

        Log.e("Billing", "Purchasing: userId=" + this.userId + ", coinId=" + this.coinId);
    }

    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase); // ✅ Pass userId and coinId explicitly
            }
        }
    };

    private void handlePurchase(Purchase purchase) {
        Log.e("Billing", "handlePurchase called!");

        // ✅ Retrieve userId and pointsIdInt from SharedPreferences
        DPreferences preferences = new DPreferences(activity);
        String savedUserId = preferences.getSelectedUserId();
        String savedCoinId = preferences.getSelectedPlanId();

        // ✅ Ensure retrieved values are valid
        if (savedUserId.equals("0") || savedCoinId.equals("0")) {
            Log.e("Billing", "Invalid saved userId or coinId");
            return;
        }

        int user_id = Integer.parseInt(savedUserId);
        int coin_id = Integer.parseInt(savedCoinId);

        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "Product consumed successfully!");

                    try {
                        WalletViewModel walletViewModel = new ViewModelProvider((ViewModelStoreOwner) activity).get(WalletViewModel.class);
                        Log.e("Billing", "Calling addCoins with userId=" + user_id + ", coinId=" + coin_id);
                        walletViewModel.addCoins(user_id, coin_id, 1, "Coins purchased");

                        activity.runOnUiThread(() ->
                                        Toast.makeText(activity, "Coins purchased successfully", Toast.LENGTH_SHORT).show()
//                                Toast.makeText(activity, "Calling addCoins with userId=" + user_id + ", coinId=" + coin_id, Toast.LENGTH_SHORT).show()
                        );

                    } catch (Exception e) {
                        Log.e("Billing", "Error updating coins: " + e.getMessage());
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity, "Billing Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
                    }
                } else {

                    try {
                        WalletViewModel walletViewModel = new ViewModelProvider((ViewModelStoreOwner) activity).get(WalletViewModel.class);
                        Log.e("Billing", "Calling addCoins with userId=" + user_id + ", coinId=" + coin_id);
                        walletViewModel.addCoins(user_id, coin_id, 2, "Failed to consume purchase: " + billingResult.getResponseCode());

//                        activity.runOnUiThread(() ->
//                                        Toast.makeText(activity, "Coins purchased successfully", Toast.LENGTH_SHORT).show()
////                                Toast.makeText(activity, "Calling addCoins with userId=" + user_id + ", coinId=" + coin_id, Toast.LENGTH_SHORT).show()
//                        );

                    } catch (Exception e) {
                        Log.e("Billing", "Error updating coins: " + e.getMessage());
                        activity.runOnUiThread(() ->
                                Toast.makeText(activity, "Billing Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
                    }

                    Toast.makeText(activity, "Failed to consume purchase: " + billingResult.getResponseCode(), Toast.LENGTH_SHORT).show();
                    Log.e("Billing", "Failed to consume purchase: " + billingResult.getResponseCode());
                }
            });
        }
    }


}