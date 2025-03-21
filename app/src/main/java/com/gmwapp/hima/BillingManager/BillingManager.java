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
        List<String> skuList = List.of("2", "3", "4", "5", "6", "7", "8", "9", "coins_12", "coins_11", "coins_11"); // Ensure these match Play Console
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
                handlePurchase(purchase, this.userId, this.coinId); // ✅ Pass userId and coinId explicitly
            }
        }
    };

    private void handlePurchase(Purchase purchase, int userId, int coinId) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.consumeAsync(consumeParams, (billingResult, purchaseToken) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "Product consumed successfully. Now it can be purchased again!");

                    // ✅ Ensure the correct userId and coinId are used
                    Log.e("Billing", "handlePurchase: userId=" + userId + ", coinId=" + coinId);

                    try {
                        WalletViewModel walletViewModel = new ViewModelProvider((ViewModelStoreOwner) activity).get(WalletViewModel.class);
                        walletViewModel.addCoins(userId, coinId);

                        // ✅ Show Toast on UI thread to avoid Looper.prepare() error
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "Coins purchased successfully", Toast.LENGTH_SHORT).show();
//                            Toast.makeText(activity, "Coins added! userId: " + userId + " coinId: " + coinId, Toast.LENGTH_SHORT).show();
                        });

                    } catch (Exception e) {
                        Log.e("Billing", "Error updating coins: " + e.getMessage());
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "Billing Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e("Billing", "Failed to consume purchase: " + billingResult.getResponseCode());
                }
            });
        }
    }
}