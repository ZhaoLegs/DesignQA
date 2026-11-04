package com.legs.specsample

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Opens the swipeable Spec Sample pager on the date page. */
class DateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).putExtra("page", 1))
        finish()
    }
}
