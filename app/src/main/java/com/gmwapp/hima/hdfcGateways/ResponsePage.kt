package com.gmwapp.hima.hdfcGateways

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.RetrofitClient
import com.gmwapp.hima.activities.WalletActivity
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResponsePage : AppCompatActivity() {
    private lateinit var constraintLayout: ConstraintLayout
    val apiService = RetrofitClient.instance
    private var progressDialog: AlertDialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_response_page)
        constraintLayout = findViewById(R.id.responsePageLayout);
        showLoadingDialog(this)
    }

    override fun onStart() {
        super.onStart()
        val i = intent
        val orderId = i.getStringExtra("orderId")
        val okay = findViewById<Button>(R.id.rectangle_12)
        
        // block:start:sendGetRequest

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id

        val callStatus = orderId?.let { userId?.let { it1 ->
            apiService.getHdfcOrderStatus(it,
                it1
            )
        } }

        callStatus?.enqueue(object : Callback<HdfcOrderStatusResponse> {
            override fun onResponse(
                call: Call<HdfcOrderStatusResponse>,
                response: Response<HdfcOrderStatusResponse>
            ) {
                hideLoadingDialog()
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data
                    val status = data?.status
                    val amount = data?.amount
                    val order_id = data?.order_id

                    val statusText = findViewById<TextView>(R.id.payment_suc)
                    val statusIcon = findViewById<ImageView>(R.id.checked_1)
                    val amountText= findViewById<TextView>(R.id.payment_amount)
                    val orderIDText= findViewById<TextView>(R.id.orderID)

                    amountText.text = "Amount - Rs $amount"
                    orderIDText.text = "Order ID - $order_id"

                    Log.d("hdfclastOrderStatus","$status")

                    when (status) {
                        "CHARGED" ->{
                            statusText.text = "Status - Payment Successfull"
                            statusIcon.setImageDrawable(resources.getDrawable(R.drawable.payment_success))
                        }
                        "PENDING_VBV" -> {
                            statusText.text = "Status - Payment Pending"
                            statusIcon.setImageDrawable(resources.getDrawable(R.drawable.pending))
                        }
                        else -> {
                            statusText.text = "Status - Payment Failed"
                            statusIcon.setImageDrawable(resources.getDrawable(R.drawable.payment_failed))
                        }
                    }

                } else {
                    Toast.makeText(this@ResponsePage, "Error fetching order status", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<HdfcOrderStatusResponse>, t: Throwable) {
                Toast.makeText(this@ResponsePage, "Failure: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })


        // block:end:sendGetRequest

        val back = findViewById<ImageView>(R.id.imageView1)
        back.setOnClickListener {
            finish()
            val i = Intent(this@ResponsePage, WalletActivity::class.java)
            startActivity(i)
        }

        okay.setOnClickListener {
            val i = Intent(this@ResponsePage, WalletActivity::class.java)
            startActivity(i)
            finish()
            89541
        }
    }


    fun showLoadingDialog(context: Context) {
        val padding = (32 * context.resources.displayMetrics.density).toInt() // 32dp padding
        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = ContextCompat.getColorStateList(context, R.color.pink)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            minimumHeight = (100 * context.resources.displayMetrics.density).toInt() // 100dp min height
            gravity = Gravity.CENTER
            addView(progressBar)
        }

        progressDialog = AlertDialog.Builder(context)
            .setView(container)
            .setCancelable(false)
            .create()

        progressDialog?.show()
    }

    fun hideLoadingDialog() {
        progressDialog?.dismiss()
    }

}
