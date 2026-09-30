package com.bankingpages.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.bankingpages.backup.BackupManager
import org.json.JSONObject
import java.security.MessageDigest

/**
 * The Aadhaar number that can reset a forgotten PIN. Only a salted, slow hash is kept,
 * sealed by the keystore like the PIN's, and a copy of that hash goes to the user's own
 * Google Drive backup so the reset still works after a reinstall. The number itself is
 * never stored.
 */
object Recovery {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("recovery", Context.MODE_PRIVATE)
    }

    val isSet get() = prefs.contains("hash")

    /** True until the copy in Google Drive matches this one. */
    val needsUpload get() = isSet && prefs.getBoolean("dirty", true)
    fun markUploaded() = prefs.edit().putBoolean("dirty", false).apply()
    fun markDirty() = prefs.edit().putBoolean("dirty", true).apply()

    /** 12 digits, not starting with 0 or 1, with a correct Verhoeff check digit (catches typos and swapped digits). */
    fun isValid(number: String): Boolean {
        val d = number.filter(Char::isDigit)
        if (d.length != 12 || d[0] == '0' || d[0] == '1') return false
        var c = 0
        d.reversed().forEachIndexed { i, ch -> c = MUL[c][PERM[i % 8][ch - '0']] }
        return c == 0
    }

    private fun hash(number: String, salt: ByteArray): ByteArray =
        Crypto.passwordKey("aadhaar:" + number.filter(Char::isDigit), salt).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun store(salt: ByteArray, raw: ByteArray) {
        prefs.edit().putString("salt", b64(salt)).putString("hash", b64(Crypto.encrypt(raw))).apply()
    }

    /** Slow on purpose (key stretching): call off the main thread. */
    fun set(number: String) {
        val salt = Crypto.random(16)
        store(salt, hash(number, salt))
        markDirty()
        BackupManager.requestBackup()
    }

    /** Slow on purpose: call off the main thread. */
    fun matches(number: String): Boolean = runCatching {
        val salt = unb64(prefs.getString("salt", null) ?: return false)
        val stored = Crypto.decrypt(unb64(prefs.getString("hash", null) ?: return false))
        MessageDigest.isEqual(stored, hash(number, salt))
    }.getOrDefault(false)

    /** The salt and hash, for the Drive copy. */
    fun export(): JSONObject? = runCatching {
        val salt = prefs.getString("salt", null) ?: return null
        val raw = Crypto.decrypt(unb64(prefs.getString("hash", null) ?: return null))
        JSONObject().put("salt", salt).put("hash", b64(raw))
    }.getOrNull()

    /** Takes in a copy from Drive (after a restore, or when the phone's own copy is missing). */
    fun import(o: JSONObject): Boolean = runCatching {
        store(unb64(o.getString("salt")), unb64(o.getString("hash")))
        markUploaded()
    }.isSuccess

    private val MUL = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6), intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4), intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
    private val PERM = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2), intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )
}
