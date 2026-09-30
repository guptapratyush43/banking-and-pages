package com.bankingpages.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.bankingpages.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * The 4-digit app PIN. Only a salted, slow hash is kept (and that sealed by the
 * keystore). After 5 wrong tries the pad pauses, 30 seconds and doubling.
 */
object Pin {
    private lateinit var prefs: SharedPreferences
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** True while the app itself has opened the camera, a picker or the share sheet: coming back isn't a new launch. */
    @Volatile var awayOnPurpose = false

    fun init(context: Context) {
        prefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)
    }

    val isSet get() = prefs.contains("hash")

    private fun hash(pin: String, salt: ByteArray): ByteArray = Crypto.passwordKey(pin, salt).encoded

    fun set(pin: String) {
        val salt = Crypto.random(16)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(Crypto.encrypt(hash(pin, salt)), Base64.NO_WRAP))
            .remove("fails").remove("until").apply()
        BackupManager.setPassword(pin)
        _unlocked.value = true
    }

    fun verify(pin: String): Boolean {
        val salt = Base64.decode(prefs.getString("salt", null) ?: return false, Base64.NO_WRAP)
        val stored = Crypto.decrypt(Base64.decode(prefs.getString("hash", null) ?: return false, Base64.NO_WRAP))
        val ok = MessageDigest.isEqual(stored, hash(pin, salt))
        if (ok) {
            prefs.edit().remove("fails").remove("until").apply()
            _unlocked.value = true
        } else {
            val fails = prefs.getInt("fails", 0) + 1
            val edit = prefs.edit().putInt("fails", fails)
            if (fails >= 5) edit.putLong("until", System.currentTimeMillis() + 30_000L * (1 shl (fails - 5).coerceAtMost(6)))
            edit.apply()
        }
        return ok
    }

    /** Milliseconds until the pad accepts tries again; 0 when it does. */
    fun waitMillis(): Long = (prefs.getLong("until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun unlockWithFingerprint() { _unlocked.value = true }

    fun lock() { if (isSet) _unlocked.value = false }
}
