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
        put("passwordWorked", a.passwordWorked)
        put("roblosecurity", a.roblosecurity)
        put("userId", a.userId)
        put("displayName", a.displayName)
        put("createdIso", a.createdIso)
        put("robux", a.robux)
        put("rap", a.rap)
        put("premium", a.premium)
        put("friends", a.friends)
        put("followers", a.followers)
        put("itemCount", a.itemCount)
        put("inventoryPrivate", a.inventoryPrivate)
        put("infoUpdatedEpoch", a.infoUpdatedEpoch)
        put("infoError", a.infoError)
        put("screenshotPath", a.screenshotPath)
        put("shared", a.shared)
    }

    private fun fromJson(o: JSONObject) = Account(
        id = o.optString("id"),
        username = o.optString("username"),
        password = o.optString("password"),
        status = runCatching { CheckStatus.valueOf(o.optString("status")) }
            .getOrDefault(CheckStatus.UNKNOWN),
        note = o.optString("note"),
        lastCheckedEpoch = o.optLong("lastCheckedEpoch", 0L),
        passwordWorked = o.optBoolean("passwordWorked", false),
        roblosecurity = o.optString("roblosecurity"),
        userId = o.optLong("userId", 0L),
        displayName = o.optString("displayName"),
        createdIso = o.optString("createdIso"),
        robux = o.optLong("robux", -1L),
        rap = o.optLong("rap", -1L),
        premium = o.optBoolean("premium", false),
        friends = o.optLong("friends", -1L),
        followers = o.optLong("followers", -1L),
        itemCount = o.optLong("itemCount", -1L),
        inventoryPrivate = o.optBoolean("inventoryPrivate", false),
        infoUpdatedEpoch = o.optLong("infoUpdatedEpoch", 0L),
        infoError = o.optString("infoError"),
        screenshotPath = o.optString("screenshotPath"),
        shared = o.optBoolean("shared", false),
    )

    companion object {
        private const val KEY_ACCOUNTS = "accounts_json"

        /**
         * Parses pasted accounts, one per line. Each line can be:
         *  - a `.ROBLOSECURITY` cookie (starts with `_|WARNING` or contains
         *    `.ROBLOSECURITY=`) → a cookie-only account (username filled on Check),
         *  - `user:pass` → a password account (first colon splits user/pass),
         *  - `user:pass:cookie` → both.
         */
        fun parseComboList(text: String): List<Account> =
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
                when {
                    // Pure cookie: the raw token, or a "...\.ROBLOSECURITY=<token>..." blob.
                    line.startsWith("_|") ->
                        Account(username = "", password = "", roblosecurity = line)
                    line.contains(".ROBLOSECURITY=") -> {
                        val ck = line.substringAfter(".ROBLOSECURITY=").substringBefore(";").trim()
                        if (ck.length > 40) Account(username = "", password = "", roblosecurity = ck) else null
                    }
                    line.contains(":") -> {
                        val user = line.substringBefore(":").trim()
                        val rest = line.substringAfter(":")
                        // Optional trailing cookie: user:pass:_|WARNING...
                        val warn = rest.indexOf("_|WARNING")
                        val pass = if (warn >= 0) rest.substring(0, warn).trimEnd(':', ' ') else rest
                        val ck = if (warn >= 0) rest.substring(warn).trim() else ""
                        if (user.isEmpty()) null else Account(username = user, password = pass, roblosecurity = ck)
                    }
                    else -> null
                }
            }.toList()
    }
}
