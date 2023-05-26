/*
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package onlymash.flexbooru.ui.activity

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import onlymash.flexbooru.R
import onlymash.flexbooru.app.Settings
import onlymash.flexbooru.data.api.OrderApi
import onlymash.flexbooru.databinding.ActivityPurchaseBinding
import onlymash.flexbooru.extension.NetResult
import onlymash.flexbooru.extension.copyText
import onlymash.flexbooru.ui.base.BaseActivity
import onlymash.flexbooru.ui.viewbinding.viewBinding

class PurchaseActivity : BaseActivity() {

    companion object {
        const val SKU = "flexbooru_pro"
    }

    private val binding by viewBinding(ActivityPurchaseBinding::inflate)

    private var billingClient: BillingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.purchase_title)
        }
        if (Settings.isGoogleSign) {
            binding.payAlipay.visibility = View.GONE
            binding.payRedeemCode.visibility = View.GONE
            val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
                val responseCode = billingResult.responseCode
                if (responseCode == BillingClient.BillingResponseCode.OK || responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    Settings.isOrderSuccess = true
                    Settings.orderTime = System.currentTimeMillis()
                    val index = purchases?.indexOfFirst { it.products[0] == SKU && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    if (index != null && index >= 0) {
                        val purchase = purchases[index]
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient?.acknowledgePurchase(ackParams){}
                        Settings.orderId = purchase.orderId
                        Settings.orderTime = purchase.purchaseTime
                        Settings.orderToken = purchase.purchaseToken
                    }
                }
            }
            billingClient = BillingClient
                .newBuilder(this)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build()
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {}
                override fun onBillingServiceDisconnected() {}
            })
            binding.payGooglePlay.setOnClickListener {
                lifecycleScope.launch {
                    processGooglePurchases()
                }
            }
        } else {
            binding.payGooglePlay.visibility = View.GONE
            binding.payAlipay.setOnClickListener {
                orderByAlipay()
            }
            binding.payRedeemCode.setOnClickListener {
                submitRedeemCode()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingClient?.apply {
            if (isReady) endConnection()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun processGooglePurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SKU)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
        val productDetailsResult = withContext(Dispatchers.IO) {
            client.queryProductDetails(params.build())
        }
        val billingResult = productDetailsResult.billingResult
        val productDetails = productDetailsResult.productDetailsList ?: return
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetails.isNotEmpty()) {
            val index = productDetails.indexOfFirst {
                it.productId == SKU
            }
            if (index >= 0) {
                val productDetailsParams = BillingFlowParams.ProductDetailsParams
                    .newBuilder()
                    .setProductDetails(productDetails[index])
                    .build()
                val billingFlowParams = BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParams))
                    .build()
                val result = client.launchBillingFlow(this@PurchaseActivity, billingFlowParams)
                if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    Settings.isOrderSuccess = true
                    Settings.orderTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun orderByAlipay() {
        if (isFinishing) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.purchase_pay_alipay_title)
            .setMessage(R.string.purchase_pay_alipay_info)
            .setPositiveButton(R.string.purchase_pay_alipay_dialog_positive) { _, _ ->
                copyText("im@fiepi.com")
                val alipayPackageName = "com.eg.android.AlipayGphone"
                try {
                    val intent = packageManager.getLaunchIntentForPackage(alipayPackageName)
                    if (intent != null) {
                        startActivity(intent)
                    }
                } catch (_: PackageManager.NameNotFoundException) {

                } catch (_: ActivityNotFoundException) {

                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun submitRedeemCode() {
        if (isFinishing) {
            return
        }
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_mlarge)
        val layout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padding, padding, padding, 0)
        }
        val editText = EditText(this)
        editText.setHint(R.string.purchase_pay_order_code_hint)
        layout.addView(editText)
        AlertDialog.Builder(this)
            .setTitle(R.string.purchase_pay_order_code)
            .setView(layout)
            .setPositiveButton(R.string.purchase_pay_order_code_summit) { _, _ ->
                val orderId = (editText.text ?: "").toString().trim()
                if (orderId.isNotEmpty()) {
                    GlobalScope.launch(Dispatchers.Main) {
                        when (val result = OrderApi.orderRegister(orderId, Settings.orderDeviceId)) {
                            is NetResult.Success -> {
                                val data = result.data
                                if (data.success) {
                                    if (data.activated) {
                                        Toast.makeText(
                                            this@PurchaseActivity,
                                            getString(R.string.purchase_pay_order_code_active_success),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@PurchaseActivity,
                                            getString(R.string.purchase_pay_order_code_summit_success),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    Settings.isOrderSuccess = data.activated
                                    Settings.orderId = orderId
                                } else {
                                    Toast.makeText(
                                        this@PurchaseActivity,
                                        getString(R.string.purchase_pay_order_code_summit_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    Settings.isOrderSuccess = false
                                    Settings.orderId = ""
                                }
                            }
                            is NetResult.Error -> {
                                Toast.makeText(
                                    this@PurchaseActivity,
                                    getString(R.string.purchase_pay_order_code_summit_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                                Settings.isOrderSuccess = false
                                Settings.orderId = ""
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show()
    }
}
