package com.bankingpages.logo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bankingpages.data.BankCatalog
import com.bankingpages.data.Vault
import com.caverock.androidsvg.SVG
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Finds and refreshes bank logos on the internet. Every failure is swallowed
 * and reported as "no change": a flaky bank website must never break the app.
 */
object LogoFetcher {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"

    private class Candidate(val url: String, val bytes: ByteArray, val bitmap: Bitmap) {
        val side get() = minOf(bitmap.width, bitmap.height)
        val squareish get() = maxOf(bitmap.width, bitmap.height) <= side * 1.3f
        val sha by lazy { sha256(bytes) }
    }

    /**
     * Checks one bank. Returns true when a new logo was saved: a missing one found,
     * or the bank's published logo changed since we last looked.
     */
    fun check(key: String, domain: String?): Boolean {
        val state = LogoStore.state(key)
        if (state?.manual == true) return false
        return when (state?.kind) {
            "repo" -> checkRepo(key, state)
            "wikidata" -> checkWikidata(key, state)
            else -> checkSite(key, domain, state)
        }
    }

    /** Hand-cleaned vector symbol from the open Indian bank logo set. */
    private fun checkRepo(key: String, state: LogoStore.State): Boolean {
        val url = state.url ?: return false
        val bytes = get(url) ?: throw IOException("offline")
        val sha = sha256(bytes)
        if (sha == state.sha) return false
        val bmp = renderSvg(bytes) ?: return false
        LogoStore.save(key, normalize(bmp), state.copy(sha = sha))
        return true
    }

    /** Wikidata names the bank's current logo file; a new file name means a new logo. */
    private fun checkWikidata(key: String, state: LogoStore.State): Boolean {
        val qid = BankCatalog.get(key)?.wikidata ?: return false
        val claims = get("https://www.wikidata.org/w/api.php?action=wbgetclaims&entity=$qid&property=P154&format=json")
            ?: throw IOException("offline")
        val arr = JSONObject(String(claims)).optJSONObject("claims")?.optJSONArray("P154") ?: return false
        if (arr.length() == 0) return false
        val name = arr.getJSONObject(arr.length() - 1).getJSONObject("mainsnak").getJSONObject("datavalue").getString("value")
        if (name == state.file) return false
        val url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(name.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=512"
        val bytes = get(url) ?: return false
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        LogoStore.save(key, normalize(bmp), LogoStore.State("wikidata", url, sha256(bytes), name))
        return true
    }

    /** The bank's own site icon. Only replaces a logo with one at least as trustworthy. */
    private fun checkSite(key: String, domain: String?, state: LogoStore.State?): Boolean {
        if (domain.isNullOrBlank()) return false
        val cands = discover(domain)
        if (cands.isEmpty()) throw IOException("nothing reachable")
        val best = cands.filter { it.squareish && it.side >= 24 }.maxByOrNull { it.side } ?: return false
        val accept = when {
            state == null -> true
            best.sha == state.sha -> false
            // The same file changed at its source, or a clearly better one appeared.
            best.url == state.url -> true
            else -> best.side >= 128
        }
        if (!accept) return false
        LogoStore.save(key, normalize(best.bitmap), LogoStore.State("site", best.url, best.sha, null))
        return true
    }

    private fun discover(domain: String): List<Candidate> {
        val host = domain.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        val urls = mutableListOf<String>()
        for (base in listOf("https://www.$host/", "https://$host/")) {
            val (final, body) = getWithUrl(base) ?: continue
            val html = String(body.copyOf(minOf(body.size, 600_000)))
            val link = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
            link.findAll(html).forEach { m ->
                val tag = m.value
                val rel = attr(tag, "rel")?.lowercase() ?: return@forEach
                val href = attr(tag, "href") ?: return@forEach
                when {
                    "manifest" in rel -> runCatching {
                        val murl = URL(URL(final), href).toString()
                        val icons = JSONObject(String(get(murl) ?: return@runCatching)).optJSONArray("icons")
                        if (icons != null) for (i in 0 until icons.length()) {
                            icons.getJSONObject(i).optString("src").takeIf { it.isNotBlank() }?.let { urls += URL(URL(murl), it).toString() }
                        }
                    }
                    "icon" in rel && "mask-icon" !in rel -> runCatching { urls += URL(URL(final), href).toString() }
                }
            }
            urls += URL(URL(final), "/apple-touch-icon.png").toString()
            break
        }
        urls += "https://www.google.com/s2/favicons?domain=$host&sz=256"
        urls += "https://icons.duckduckgo.com/ip3/$host.ico"
        return urls.distinct().filterNot { it.lowercase().substringBefore('?').endsWith(".svg") }.mapNotNull { u ->
            val bytes = get(u) ?: return@mapNotNull null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@mapNotNull null
            Candidate(u, bytes, bmp)
        }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)

    fun renderSvg(bytes: ByteArray): Bitmap? = runCatching {
        val svg = SVG.getFromString(String(bytes))
        val w = svg.documentViewBox?.width() ?: svg.documentWidth
        val h = svg.documentViewBox?.height() ?: svg.documentHeight
        val scale = 512f / maxOf(w, h)
        svg.setDocumentWidth(w * scale); svg.setDocumentHeight(h * scale)
        val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bmp))
        bmp
    }.getOrNull()

    /** Trims a white or transparent margin and centres the logo on a transparent 256px square. */
    fun normalize(src: Bitmap): Bitmap {
        val bmp = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        fun blank(c: Int) = Color.alpha(c) < 16 || (Color.red(c) > 240 && Color.green(c) > 240 && Color.blue(c) > 240)
        var box = Rect(0, 0, w, h)
        if (blank(px[0]) && blank(px[w - 1]) && blank(px[(h - 1) * w]) && blank(px[h * w - 1])) {
            var l = w; var t = h; var r = -1; var b = -1
            for (y in 0 until h) for (x in 0 until w) if (!blank(px[y * w + x])) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
            if (r >= l && b >= t) box = Rect(l, t, r + 1, b + 1)
        }
        val side = maxOf(box.width(), box.height())
        val out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val scale = 256f / side
        val dw = box.width() * scale; val dh = box.height() * scale
        val dst = android.graphics.RectF((256 - dw) / 2, (256 - dh) / 2, (256 + dw) / 2, (256 + dh) / 2)
        Canvas(out).drawBitmap(bmp, box, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun get(url: String): ByteArray? = getWithUrl(url)?.second

    /** GET with manual redirects (HttpURLConnection won't hop http↔https), capped at 4 MB. */
    private fun getWithUrl(start: String): Pair<String, ByteArray>? {
        var url = start
        repeat(6) {
            val c = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 12_000
                c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept-Language", "en-IN,en")
                val code = c.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), c.getHeaderField("Location") ?: return null).toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                val bytes = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < 4_000_000) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                    out.toByteArray()
                }
                return url to bytes
            } catch (e: Exception) {
                return null
            } finally {
                c.disconnect()
            }
        }
        return null
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}

/**
 * Runs once a day, silently: looks at every bank you've saved and refreshes
 * any logo the bank has changed. With a "key" input it fetches just that one
 * missing logo, retrying with backoff until the phone is back online.
 */
class LogoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val single = inputData.getString("key")
        if (single != null) {
            if (LogoStore.has(single)) return Result.success()
            return try {
                LogoFetcher.check(single, inputData.getString("domain"))
                if (LogoStore.has(single) || runAttemptCount >= 6) Result.success() else Result.retry()
            } catch (e: Exception) {
                if (runAttemptCount >= 6) Result.success() else Result.retry()
            }
        }
        Vault.accounts.value.distinctBy { it.bankId }.forEach { a ->
            val domain = a.bankDomain ?: BankCatalog.get(a.bankId)?.domain
            runCatching { LogoFetcher.check(a.bankId, domain) }
        }
        return Result.success()
    }
}
