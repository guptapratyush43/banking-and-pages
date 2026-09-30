package com.bankingpages.backup

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bankingpages.data.Crypto
import com.bankingpages.data.Vault
import com.bankingpages.logo.LogoStore
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class NeedsSignInException : IOException("Google access expired. Sign in again to keep backing up.")
class WrongPasswordException : IOException("That PIN doesn't open this backup. Use the PIN from when it was made.")

/**
 * Backs up the whole vault (details, photos, PDFs, custom logos) as one file in
 * Drive's hidden app folder, locked with a key made from the app PIN. Restoring on
 * a new phone asks for that PIN, and the restored PIN keeps working there.
 * Any change is backed up a few seconds later in the background.
 */
object BackupManager {
    private const val BACKUP_NAME = "banking-pages-backup.bin"
    private val MAGIC = "BPB1".toByteArray()

    data class State(val email: String? = null, val lastBackupAt: Long = 0L, val busy: String? = null, val error: String? = null, val hasPassword: Boolean = false)
    data class RemoteInfo(val modified: Long)

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    @Volatile private var restoring = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("backup", Context.MODE_PRIVATE)
        _state.value = State(prefs.getString("email", null), prefs.getLong("last_backup", 0L), error = prefs.getString("error", null), hasPassword = prefs.contains("key"))
        Vault.onChanged = ::onDataChanged
    }

    val signedIn get() = _state.value.email != null
    private val ready get() = signedIn && prefs.contains("key")

    private fun onDataChanged() {
        if (!ready || restoring) return
        val work = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("auto-backup", ExistingWorkPolicy.REPLACE, work)
    }

    suspend fun completeSignIn(token: String): RemoteInfo? = withContext(Dispatchers.IO) {
        val drive = Drive(token)
        val email = drive.email() ?: "Google account"
        prefs.edit().putString("email", email).remove("error").apply()
        _state.update { it.copy(email = email, error = null) }
        drive.find(BACKUP_NAME)?.let { RemoteInfo(it.modified) }
    }

    /** Called whenever the PIN is set or changed: keeps the backup key in step with it. */
    fun setPassword(password: String, salt: ByteArray = Crypto.random(16)) {
        if (!::prefs.isInitialized) return
        val key = Crypto.passwordKey(password, salt)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("key", Base64.encodeToString(Crypto.encrypt(key.encoded), Base64.NO_WRAP))
            .apply()
        _state.update { it.copy(hasPassword = true) }
        onDataChanged()
    }

    private fun storedKey(): Pair<ByteArray, SecretKeySpec>? {
        val salt = prefs.getString("salt", null) ?: return null
        val key = prefs.getString("key", null) ?: return null
        return Base64.decode(salt, Base64.NO_WRAP) to SecretKeySpec(Crypto.decrypt(Base64.decode(key, Base64.NO_WRAP)), "AES")
    }

    fun signOut() {
        WorkManager.getInstance(app).cancelUniqueWork("auto-backup")
        prefs.edit().remove("email").remove("last_backup").remove("error").apply()
        _state.value = State(hasPassword = prefs.contains("key"))
    }

    suspend fun remoteInfo(): RemoteInfo? = withDrive("Checking Drive…") { d -> d.find(BACKUP_NAME)?.let { RemoteInfo(it.modified) } }

    suspend fun backupNow() = withDrive("Backing up…") { drive ->
        val (salt, key) = storedKey() ?: throw IOException("Set your PIN first.")
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("vault.json", Vault.toJson().toString().toByteArray())
            Vault.allBlobIds().distinct().filter(Vault::hasBlob).forEach { put("blobs/$it", Vault.readBlob(it)) }
            LogoStore.exportable().forEach { (k, b) -> put("logos/$k.png", b) }
            put("logos.json", LogoStore.exportState().toString().toByteArray())
        }
        drive.replace(BACKUP_NAME, "application/octet-stream", MAGIC + salt + Crypto.seal(key, zip.toByteArray()))
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_backup", now).remove("error").apply()
        _state.update { it.copy(lastBackupAt = now, error = null) }
    }

    /** Replaces everything on this phone with the backup. Returns how many accounts came back. */
    suspend fun restore(password: String): Int = withDrive("Restoring…") { drive ->
        val ref = drive.find(BACKUP_NAME) ?: throw IOException("No backup found in this Google account yet.")
        val blob = drive.download(ref.id)
        if (blob.size < 4 + 16 + 12 || !blob.copyOfRange(0, 4).contentEquals(MAGIC)) throw IOException("That backup file is damaged.")
        val salt = blob.copyOfRange(4, 20)
        val key = Crypto.passwordKey(password, salt)
        val zipBytes = try { Crypto.open(key, blob.copyOfRange(20, blob.size)) } catch (e: AEADBadTagException) { throw WrongPasswordException() }

        var json: JSONObject? = null
        var logoState: JSONObject? = null
        val blobs = HashMap<String, ByteArray>()
        val logos = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
            generateSequence { z.nextEntry }.forEach { e ->
                val bytes = z.readBytes()
                when {
                    e.name == "vault.json" -> json = JSONObject(String(bytes))
                    e.name == "logos.json" -> logoState = JSONObject(String(bytes))
                    e.name.startsWith("blobs/") -> blobs[e.name.removePrefix("blobs/")] = bytes
                    e.name.startsWith("logos/") -> logos[e.name.removePrefix("logos/").removeSuffix(".png")] = bytes
                }
            }
        }
        val data = json ?: throw IOException("That backup file is damaged.")
        restoring = true
        try {
            Vault.replaceAll(data, blobs)
            LogoStore.import(logos, logoState)
        } finally {
            restoring = false
        }
        com.bankingpages.data.Pin.set(password) // the old PIN now unlocks this phone too
        Vault.accounts.value.size
    }

    suspend fun deleteBackup() = withDrive("Deleting backup…") { drive ->
        drive.list(BACKUP_NAME).forEach { drive.delete(it.id) }
        prefs.edit().remove("last_backup").apply()
        _state.update { it.copy(lastBackupAt = 0L) }
    }

    private suspend fun <T> withDrive(label: String, block: (Drive) -> T): T = lock.withLock {
        _state.update { it.copy(busy = label) }
        try {
            withContext(Dispatchers.IO) {
                val token = DriveAuth.silentToken(app) ?: throw NeedsSignInException()
                try {
                    block(Drive(token))
                } catch (e: HttpException) {
                    if (e.code != 401) throw e
                    runCatching { GoogleAuthUtil.clearToken(app, token) }
                    block(Drive(DriveAuth.silentToken(app) ?: throw NeedsSignInException()))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: WrongPasswordException) {
            throw e // shown in the password dialog, not as a lasting error
        } catch (e: Throwable) {
            val msg = friendly(e)
            prefs.edit().putString("error", msg).apply()
            _state.update { it.copy(error = msg) }
            throw e
        } finally {
            _state.update { it.copy(busy = null) }
        }
    }

    fun friendly(e: Throwable): String = when (e) {
        is kotlinx.coroutines.CancellationException -> ""
        is NeedsSignInException, is WrongPasswordException -> e.message!!
        is HttpException -> if (e.code == 403) "Drive said no (403). Check this account is a test user." else "Drive hiccup (${e.code}). Try again."
        is java.net.UnknownHostException, is java.net.SocketTimeoutException -> "No internet right now. We'll retry."
        else -> e.message ?: "Something went wrong."
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BackupManager.signedIn) return Result.success()
        return try {
            BackupManager.backupNow()
            Result.success()
        } catch (e: NeedsSignInException) {
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (e: Throwable) {
            Result.failure()
        }
    }
}
