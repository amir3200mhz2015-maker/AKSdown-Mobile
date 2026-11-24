package com.aksdown.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aksdown.mobile.ui.AksRoot
import com.aksdown.mobile.ui.MainViewModel

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AksRoot(vm, onLanguage = ::applyLanguage) }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: return
        val testFormat = if (BuildConfig.DEBUG) intent.getStringExtra("aks_test_format") else null
        if (testFormat != null) vm.testDownload(text, com.aksdown.mobile.data.Format.valueOf(testFormat))
        else vm.onShared(text)
        intent.action = null // don't re-process on configuration change
    }

    private fun applyLanguage(tag: String) {
        (application as AksApp).settings.edit { copy(language = tag) }
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
