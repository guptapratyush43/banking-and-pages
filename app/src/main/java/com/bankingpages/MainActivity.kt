package com.bankingpages

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bankingpages.data.AppSettings
import com.bankingpages.data.Pin
import com.bankingpages.ui.AppRoot
import com.bankingpages.ui.NavViewModel
import com.bankingpages.ui.theme.BankingTheme
import kotlinx.coroutines.launch

/** A FragmentActivity because the fingerprint sheet (BiometricPrompt) needs one. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val nav = ViewModelProvider(this)[NavViewModel::class.java]

        // Screenshots and the recent-apps preview are blanked while the switch is on.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                AppSettings.state.collect { s ->
                    if (s.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            BankingTheme { AppRoot(nav) }
        }
    }

    override fun onStart() {
        super.onStart()
        Pin.awayOnPurpose = false
    }

    /** Leaving the app locks it, so every return asks for the PIN, unless we opened the camera, a picker or the share sheet. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !Pin.awayOnPurpose) Pin.lock()
    }
}
