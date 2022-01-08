package com.msisuzney.glideheifdecoder.demo

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.msisuzney.glideheifdecoder.R

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val iv = findViewById<ImageView>(R.id.iv)
        val iv2 = findViewById<ImageView>(R.id.iv2)
        Glide.with(this).load(R.raw.avif_example).into(iv)
        Glide.with(this).load(R.raw.heic_example).into(iv2)
    }
}