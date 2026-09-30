package com.bankingpages

import android.app.Application
import com.bankingpages.backup.BackupManager
import com.bankingpages.data.AppSettings
import com.bankingpages.data.BankCatalog
import com.bankingpages.data.Pin
import com.bankingpages.data.Vault
import com.bankingpages.files.Media
import com.bankingpages.logo.LogoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide scope for work that must outlive a screen (sign-in, backup, logo fetches). */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class BankingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BankCatalog.init(this)
        AppSettings.init(this)
        Pin.init(this)
        com.bankingpages.data.Recovery.init(this)
        Vault.init(this)
        Media.init(this)
        LogoStore.init(this)
        BackupManager.init(this)
        com.bankingpages.update.UpdateManager.init(this)
        com.bankingpages.update.UpdateWorker.schedule(this)
    }
}
