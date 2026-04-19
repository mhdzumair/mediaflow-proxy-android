package com.mediaflow.proxy.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.mediaflow.proxy.R

class TvSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_root, TvSettingsFragment())
                .commit()
        }
    }
}
