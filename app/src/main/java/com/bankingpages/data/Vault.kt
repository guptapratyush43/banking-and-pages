package com.bankingpages.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The encrypted store: one sealed JSON file for every account and document,
 * plus one sealed file ("blob") per photo or PDF. Nothing is ever written in
 * the clear; only [Crypto]'s hardware-backed key can read it back.
 */
object Vault {
    private lateinit var dir: File
    private lateinit var blobs: File
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    private val _docs = MutableStateFlow<List<Doc>>(emptyList())
    val docs: StateFlow<List<Doc>> = _docs.asStateFlow()

    /** Set by the backup manager: called after any change so it can sync. */
    var onChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "vault").apply { mkdirs() }
        blobs = File(dir, "blobs").apply { mkdirs() }
        load()
    }

    private val file get() = File(dir, "vault.bin")

    private fun load() {
        val json = runCatching { JSONObject(String(Crypto.decrypt(file.readBytes()))) }.getOrNull() ?: return
        fromJson(json)
    }

    fun fromJson(json: JSONObject) {
        val a = json.optJSONArray("accounts") ?: JSONArray()
        val d = json.optJSONArray("docs") ?: JSONArray()
        _accounts.value = List(a.length()) { Account.fromJson(a.getJSONObject(it)) }
        _docs.value = List(d.length()) { Doc.fromJson(d.getJSONObject(it)) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("format", 1)
        .put("accounts", JSONArray().apply { _accounts.value.forEach { put(it.toJson()) } })
        .put("docs", JSONArray().apply { _docs.value.forEach { put(it.toJson()) } })

    @Synchronized
    private fun persist(notify: Boolean = true) {
        val tmp = File(dir, "vault.tmp")
        tmp.writeBytes(Crypto.encrypt(toJson().toString().toByteArray()))
        tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        if (notify) onChanged?.invoke()
    }

    fun account(id: String) = _accounts.value.firstOrNull { it.id == id }
    fun doc(id: String) = _docs.value.firstOrNull { it.id == id }

    fun saveAccount(a: Account) {
        val old = account(a.id)
        // Photos swapped out or removed during the edit are deleted from disk.
        old?.photos?.values?.filter { it !in a.photos.values }?.forEach(::deleteBlob)
        _accounts.value = if (old == null) _accounts.value + a else _accounts.value.map { if (it.id == a.id) a else it }
        persist()
    }

    fun deleteAccount(id: String) {
        account(id)?.photos?.values?.forEach(::deleteBlob)
        _accounts.value = _accounts.value.filterNot { it.id == id }
        persist()
    }

    fun saveDoc(d: Doc) {
        _docs.value = if (doc(d.id) == null) _docs.value + d else _docs.value.map { if (it.id == d.id) d else it }
        persist()
    }

    fun deleteDoc(id: String) {
        doc(id)?.let { deleteBlob(it.blobId) }
        _docs.value = _docs.value.filterNot { it.id == id }
        persist()
    }

    /** Saves the order the home rows were dragged into. */
    fun reorderAccounts(ids: List<String>) {
        val sorted = _accounts.value.sortedBy { ids.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        if (sorted != _accounts.value) { _accounts.value = sorted; persist() }
    }

    fun reorderDocs(ids: List<String>) {
        val sorted = _docs.value.sortedBy { ids.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        if (sorted != _docs.value) { _docs.value = sorted; persist() }
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun putBlob(bytes: ByteArray, id: String = newId()): String {
        File(blobs, id).writeBytes(Crypto.encrypt(bytes))
        return id
    }

    fun readBlob(id: String): ByteArray = Crypto.decrypt(File(blobs, id).readBytes())

    fun hasBlob(id: String) = File(blobs, id).exists()

    fun deleteBlob(id: String) { File(blobs, id).delete() }

    fun allBlobIds(): List<String> =
        _accounts.value.flatMap { it.photos.values } + _docs.value.map { it.blobId }

    /** Swaps in a restored backup: the JSON first, then any blob no longer referenced is removed. */
    fun replaceAll(json: JSONObject, blobData: Map<String, ByteArray>) {
        blobData.forEach { (id, bytes) -> putBlob(bytes, id) }
        fromJson(json)
        val keep = allBlobIds().toSet()
        blobs.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        persist(notify = false)
    }
}
