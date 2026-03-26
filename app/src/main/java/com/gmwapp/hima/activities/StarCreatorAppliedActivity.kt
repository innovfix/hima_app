package com.gmwapp.hima.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.R

class StarCreatorAppliedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_star_creator_applied)

        findViewById<TextView>(R.id.btn_done).setOnClickListener {
            finish()
        }
    }
}
