package com.bankingpages.data

import org.json.JSONArray
import org.json.JSONObject

enum class PhotoSlot(val label: String) {
    CHEQUE("Cancelled Cheque"),
    PASSBOOK("Passbook Front Page"),
    DEBIT_FRONT("Debit Card"),
    DEBIT_BACK("Debit Card (Back)"),
    CREDIT_FRONT("Credit Card"),
    CREDIT_BACK("Credit Card (Back)");

    /** Front and back sit together in one picture; the BACK slots only hold older, separate photos. */
    val isCard get() = this == DEBIT_FRONT || this == CREDIT_FRONT
    val legacy get() = this == DEBIT_BACK || this == CREDIT_BACK
}

data class SecurityQA(val question: String, val answer: String)

/** A second, third... debit or credit card: front and back in one picture, like the first. */
data class CardShot(val id: String, val credit: Boolean, val blobId: String)

data class Account(
    val id: String,
    val bankId: String,
    val bankName: String,
    /** Only for banks the user typed in: lets the app look up their logo. */
    val bankDomain: String? = null,
    val holder: String = "",
    val number: String = "",
    val ifsc: String = "",
    val branch: String = "",
    /** The branch's postal address, filled in from the IFSC. */
    val branchAddress: String = "",
    val type: String = "Savings",
    val customerId: String = "",
    val mobile: String = "",
    val email: String = "",
    val micr: String = "",
    val upi: String = "",
    val netUserId: String = "",
    val loginPassword: String = "",
    val txnPassword: String = "",
    val profilePassword: String = "",
    val questions: List<SecurityQA> = emptyList(),
    val photos: Map<PhotoSlot, String> = emptyMap(),
    val cards: List<CardShot> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val maskedNumber: String get() = if (number.length >= 4) "•• " + number.takeLast(4) else number

    /** Every photo this account holds, for clean-up and backup. */
    val allBlobs: List<String> get() = photos.values + cards.map { it.blobId }

    /**
     * Photo places are named by key: "s:CHEQUE" is a fixed slot, "n:d" / "n:c" a new
     * debit / credit card, "x:<id>" an extra card already saved.
     */
    fun withPhoto(key: String, blobId: String): Account = when {
        key.startsWith("s:") -> copy(photos = photos + (PhotoSlot.valueOf(key.drop(2)) to blobId))
        key == "n:d" || key == "n:c" -> copy(cards = cards + CardShot(java.util.UUID.randomUUID().toString(), key == "n:c", blobId))
        else -> this
    }

    fun withoutPhoto(key: String): Account = when {
        key.startsWith("s:") -> copy(photos = photos - PhotoSlot.valueOf(key.drop(2)))
        key.startsWith("x:") -> copy(cards = cards.filterNot { it.id == key.drop(2) })
        else -> this
    }

    /** Branch name and its address as one line. */
    val branchLine: String get() = listOf(branch, branchAddress).filter { it.isNotBlank() }.joinToString(", ")

    /** The one-tap share text: what someone needs to send you money. */
    fun shareText(): String = buildList {
        add(bankName)
        if (holder.isNotBlank()) add("Account holder: $holder")
        if (number.isNotBlank()) add("Account number: $number")
        if (ifsc.isNotBlank()) add("IFSC: $ifsc")
        if (type.isNotBlank()) add("Account type: $type")
        if (branchLine.isNotBlank()) add("Branch: $branchLine")
        if (mobile.isNotBlank()) add("Registered mobile: $mobile")
    }.joinToString("\n")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("bankId", bankId).put("bankName", bankName).put("bankDomain", bankDomain ?: JSONObject.NULL)
        .put("holder", holder).put("number", number).put("ifsc", ifsc).put("branch", branch).put("branchAddress", branchAddress).put("type", type)
        .put("customerId", customerId).put("mobile", mobile).put("email", email).put("micr", micr).put("upi", upi)
        .put("netUserId", netUserId).put("loginPassword", loginPassword).put("txnPassword", txnPassword)
        .put("profilePassword", profilePassword)
        .put("questions", JSONArray().apply { questions.forEach { put(JSONObject().put("q", it.question).put("a", it.answer)) } })
        .put("photos", JSONObject().apply { photos.forEach { (k, v) -> put(k.name, v) } })
        .put("cards", JSONArray().apply { cards.forEach { put(JSONObject().put("id", it.id).put("credit", it.credit).put("blob", it.blobId)) } })
        .put("notes", notes).put("createdAt", createdAt).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): Account {
            val qs = o.optJSONArray("questions") ?: JSONArray()
            val ph = o.optJSONObject("photos") ?: JSONObject()
            val cs = o.optJSONArray("cards") ?: JSONArray()
            return Account(
                id = o.getString("id"), bankId = o.getString("bankId"), bankName = o.getString("bankName"),
                bankDomain = o.optString("bankDomain").takeIf { it.isNotBlank() && it != "null" },
                holder = o.optString("holder"), number = o.optString("number"), ifsc = o.optString("ifsc"),
                branch = o.optString("branch"), branchAddress = o.optString("branchAddress"), type = o.optString("type", "Savings"), customerId = o.optString("customerId"),
                mobile = o.optString("mobile"), email = o.optString("email"), micr = o.optString("micr"), upi = o.optString("upi"),
                netUserId = o.optString("netUserId"), loginPassword = o.optString("loginPassword"),
                txnPassword = o.optString("txnPassword"), profilePassword = o.optString("profilePassword"),
                questions = List(qs.length()) { i -> qs.getJSONObject(i).let { SecurityQA(it.optString("q"), it.optString("a")) } },
                photos = ph.keys().asSequence().mapNotNull { k -> runCatching { PhotoSlot.valueOf(k) }.getOrNull()?.let { it to ph.getString(k) } }.toMap(),
                cards = List(cs.length()) { i -> cs.getJSONObject(i).let { CardShot(it.getString("id"), it.optBoolean("credit"), it.getString("blob")) } },
                notes = o.optString("notes"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt")
            )
        }
    }
}

enum class DocKind(val label: String) {
    AADHAAR("Aadhaar card"), PAN("PAN card"), VOTER("Voter ID"), PASSPORT("Passport"), DL("Driving licence"), OTHER("Document")
}

data class Doc(
    val id: String,
    val kind: DocKind,
    val title: String,
    val blobId: String,
    val mime: String,
    val fileName: String,
    /** e-Aadhaar and some PAN PDFs are locked; kept so the app can open them. */
    val password: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isPdf get() = mime == "application/pdf"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("title", title).put("blobId", blobId).put("mime", mime)
        .put("fileName", fileName).put("password", password ?: JSONObject.NULL).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Doc(
            id = o.getString("id"),
            kind = runCatching { DocKind.valueOf(o.getString("kind")) }.getOrDefault(DocKind.OTHER),
            title = o.optString("title"), blobId = o.getString("blobId"), mime = o.optString("mime", "application/pdf"),
            fileName = o.optString("fileName", "document.pdf"),
            password = o.optString("password").takeIf { it.isNotBlank() && it != "null" },
            createdAt = o.optLong("createdAt")
        )
    }
}
