package com.robloxvault.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists accounts in an AES-256 EncryptedSharedPreferences file whose master
 * key lives in the Android Keystore. Nothing is written in plaintext, and
 * nothing is transmitted anywhere. This is the "safe spot" the vault provides.
 */
class AccountStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vault_accounts",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun save(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    private fun toJson(a: Account) = JSONObject().apply {
        put("id", a.id)
        put("username", a.username)
        put("password", a.password)
        put("status", a.status.name)
        put("note", a.note)
        put("lastCheckedEpoch", a.lastCheckedEpoch)
        put("roblosecurity", a.roblosecurity)
        put("userId", a.userId)
        put("displayName", a.displayName)
        put("createdIso", a.createdIso)
        put("robux", a.robux)
        put("rap", a.rap)
        put("premium", a.premium)
        put("friends", a.friends)
        put("followers", a.followers)
        put("infoUpdatedEpoch", a.infoUpdatedEpoch)
        put("infoError", a.infoError)
    }

    private fun fromJson(o: JSONObject) = Account(
        id = o.optString("id"),
        username = o.optString("username"),
        password = o.optString("password"),
        status = runCatching { CheckStatus.valueOf(o.optString("status")) }
            .getOrDefault(CheckStatus.UNKNOWN),
        note = o.optString("note"),
        lastCheckedEpoch = o.optLong("lastCheckedEpoch", 0L),
        roblosecurity = o.optString("roblosecurity"),
        userId = o.optLong("userId", 0L),
        displayName = o.optString("displayName"),
        createdIso = o.optString("createdIso"),
        robux = o.optLong("robux", -1L),
        rap = o.optLong("rap", -1L),
        premium = o.optBoolean("premium", false),
        friends = o.optLong("friends", -1L),
        followers = o.optLong("followers", -1L),
        infoUpdatedEpoch = o.optLong("infoUpdatedEpoch", 0L),
        infoError = o.optString("infoError"),
    )

    companion object {
        private const val KEY_ACCOUNTS = "accounts_json"

        /**
         * Parses pasted text in `account:pass` format (one per line). The first
         * colon splits user from password, so passwords may themselves contain
         * colons. Blank lines and lines without a colon are ignored.
         */
        fun parseComboList(text: String): List<Account> =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(":") }
                .map { line ->
                    val idx = line.indexOf(':')
                    Account(
                        username = line.substring(0, idx).trim(),
                        password = line.substring(idx + 1),
                    )
                }
                .filter { it.username.isNotEmpty() }
                .toList()
    }
}
