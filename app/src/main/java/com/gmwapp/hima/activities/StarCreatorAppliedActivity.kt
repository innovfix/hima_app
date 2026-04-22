package com.gmwapp.hima.activities

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.R
import com.gmwapp.hima.utils.applySystemBarInsets

class StarCreatorAppliedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_star_creator_applied)
        applySystemBarInsets(findViewById<View>(android.R.id.content), R.color.white, darkStatusBarIcons = true)

        findViewById<TextView>(R.id.btn_done).setOnClickListener {
            finish()
        }
    }
}
