package com.unimart.app.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.unimart.app.databinding.ActivityFullScreenImageBinding

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUrl = intent.getStringExtra("IMAGE_URL")
        
        binding.toolbar.setNavigationOnClickListener { finish() }

        Glide.with(this)
            .load(imageUrl)
            .into(binding.ivFullScreen)
    }
}
