package com.bankingpages.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.Locale

/** Where a bank's logo came from, and its fingerprint then, so the daily check can spot a change. */
data class LogoSource(val kind: String, val url: String, val sha256: String, val file: String? = null)

data class Bank(
    val id: String,
    val name: String,
    val category: String,
    val domain: String?,
    val wikidata: String? = null,
    val bundledLogo: Boolean = false,
    val source: LogoSource? = null
)

/** The picker list (assets/banks.json), built by tools/fetch_logos.py. */
object BankCatalog {
    val categories = listOf("Public sector", "Private sector", "Small finance", "Payments bank", "Foreign", "Regional rural", "Co-operative")
    lateinit var banks: List<Bank>
        private set
    private lateinit var byId: Map<String, Bank>

    fun init(context: Context) {
        if (::banks.isInitialized) return
        val arr = JSONArray(context.assets.open("banks.json").bufferedReader().use { it.readText() })
        banks = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val s = o.optJSONObject("source")
            Bank(
                id = o.getString("id"),
                name = o.getString("name"),
                category = o.getString("category"),
                domain = o.optString("domain").takeIf { it.isNotBlank() && it != "null" },
                wikidata = o.optString("wikidata").takeIf { it.isNotBlank() },
                bundledLogo = o.optBoolean("logo"),
                source = s?.let { LogoSource(it.getString("kind"), it.getString("url"), it.getString("sha256"), it.optString("file").takeIf { f -> f.isNotBlank() }) }
            )
        }
        byId = banks.associateBy { it.id }
    }

    fun get(id: String): Bank? = byId[id]

    /** Matches words anywhere and initials too: "pnb", "state bank", "kotak" all work. */
    fun search(query: String): List<Bank> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return banks
        val words = q.split(Regex("\\s+"))
        return banks.filter { b ->
            val name = b.name.lowercase(Locale.ROOT)
            val initials = name.split(Regex("[\\s&-]+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
            words.all { w -> name.contains(w) } || initials.startsWith(q.replace(" ", "")) || b.id.startsWith(q) ||
                (b.domain?.startsWith(q) == true)
        }.sortedBy { b -> if (b.name.lowercase(Locale.ROOT).startsWith(q)) 0 else 1 }
    }

    /** Stable id for a bank the user typed in, so two accounts at it share one logo. */
    fun customId(name: String): String =
        "c_" + name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "bank" }
}

/** App preferences. Screenshot blocking defaults to on; fingerprint unlock to off. */
object AppSettings {
    data class State(val fingerprint: Boolean = false, val secureScreen: Boolean = true, val onboarded: Boolean = false)

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = State(prefs.getBoolean("fingerprint", false), prefs.getBoolean("secure_screen", true), prefs.getBoolean("onboarded", false))
    }

    fun setFingerprint(on: Boolean) { prefs.edit().putBoolean("fingerprint", on).apply(); _state.value = _state.value.copy(fingerprint = on) }
    fun setSecureScreen(on: Boolean) { prefs.edit().putBoolean("secure_screen", on).apply(); _state.value = _state.value.copy(secureScreen = on) }
    fun setOnboarded() { prefs.edit().putBoolean("onboarded", true).apply(); _state.value = _state.value.copy(onboarded = true) }
}
