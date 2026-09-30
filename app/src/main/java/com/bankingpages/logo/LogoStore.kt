package com.bankingpages.logo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bankingpages.AppScope
import com.bankingpages.data.BankCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Where each bank's logo lives and where it came from. A logo downloaded or
 * updated later (files/logos) wins over the one shipped in the app (assets/logos);
 * a logo the user picked themselves is never replaced by the daily check.
 */
object LogoStore {
    data class State(val kind: String, val url: String?, val sha: String?, val file: String?, val manual: Boolean = false)

    private lateinit var app: Context
    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences
    private val cache = LruCache<String, ImageBitmap>(64)
    private val _version = MutableStateFlow(0)
    /** Bumps whenever any logo changes, so on-screen logos reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        dir = File(app.filesDir, "logos").apply { mkdirs() }
        prefs = app.getSharedPreferences("logos", Context.MODE_PRIVATE)
        val daily = PeriodicWorkRequestBuilder<LogoWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("logo-daily", ExistingPeriodicWorkPolicy.KEEP, daily)
    }

    private fun file(key: String) = File(dir, "$key.png")

    fun state(key: String): State? {
        prefs.getString(key, null)?.let { s ->
            val o = JSONObject(s)
            return State(o.getString("kind"), o.optString("url").ifBlank { null }, o.optString("sha").ifBlank { null },
                o.optString("file").ifBlank { null }, o.optBoolean("manual"))
        }
        // Nothing checked yet: start from the fingerprint recorded when the app was built.
        val src = BankCatalog.get(key)?.takeIf { it.bundledLogo }?.source ?: return null
        return State(src.kind, src.url, src.sha256, src.file)
    }

    fun has(key: String) = file(key).exists() || BankCatalog.get(key)?.bundledLogo == true

    fun cachedOrNull(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bmp = runCatching {
                val f = file(key)
                if (f.exists()) BitmapFactory.decodeFile(f.path)
                else if (BankCatalog.get(key)?.bundledLogo == true) app.assets.open("logos/$key.webp").use(BitmapFactory::decodeStream)
                else null
            }.getOrNull()
            bmp?.asImageBitmap()?.also { cache.put(key, it) }
        }
    }

    fun save(key: String, bitmap: Bitmap, state: State) {
        val tmp = File(dir, "$key.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file(key)) || run { file(key).delete(); tmp.renameTo(file(key)) }
        prefs.edit().putString(key, JSONObject().put("kind", state.kind).put("url", state.url ?: "").put("sha", state.sha ?: "")
            .put("file", state.file ?: "").put("manual", state.manual).toString()).apply()
        cache.remove(key)
        _version.value++
    }

    /** The user's own picture for a bank. It stays until they reset it. */
    fun setManual(key: String, bitmap: Bitmap) = save(key, LogoFetcher.normalize(bitmap), State("manual", null, null, null, manual = true))

    fun resetManual(key: String, domain: String?) {
        file(key).delete()
        prefs.edit().remove(key).apply()
        cache.remove(key)
        _version.value++
        ensure(key, domain)
    }

    fun isManual(key: String) = state(key)?.manual == true

    /** Raw logo files the user chose or the app fetched, for the Drive backup. */
    fun exportable(): Map<String, ByteArray> =
        dir.listFiles()?.filter { it.extension == "png" }?.associate { it.nameWithoutExtension to it.readBytes() }.orEmpty()

    fun exportState(): JSONObject = JSONObject().apply { prefs.all.forEach { (k, v) -> put(k, v) } }

    fun import(files: Map<String, ByteArray>, state: JSONObject?) {
        files.forEach { (k, b) -> file(k).writeBytes(b) }
        state?.let { s -> prefs.edit().apply { s.keys().forEach { putString(it, s.getString(it)) } }.apply() }
        cache.evictAll()
        _version.value++
    }

    /**
     * A bank with no logo yet gets one as soon as possible: straight away while the
     * app is open, and through WorkManager (retrying with backoff) if that fails,
     * say because the phone is offline right now.
     */
    fun ensure(key: String, domain: String?) {
        if (has(key) || domain.isNullOrBlank()) return
        AppScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { LogoFetcher.check(key, domain) }.getOrDefault(false) }
            if (!ok && !has(key)) {
                val work = OneTimeWorkRequestBuilder<LogoWorker>()
                    .setInputData(workDataOf("key" to key, "domain" to domain))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(app).enqueueUniqueWork("logo-$key", ExistingWorkPolicy.REPLACE, work)
            }
        }
    }
}
