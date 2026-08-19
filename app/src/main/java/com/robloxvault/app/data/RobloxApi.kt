package com.robloxvault.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads profile data for a single account using THAT account's own
 * `.ROBLOSECURITY` session cookie — the same thing that happens when you open
 * the site in a browser. Only public read endpoints plus the account's own
 * balance are used; nothing is written and no other account is touched.
 */
object RobloxApi {

    data class Info(
        val userId: Long,
        val username: String,
        val displayName: String,
        val createdIso: String,
        val robux: Long,
        val rap: Long,
        val premium: Boolean,
        val friends: Long,
        val followers: Long,
    )

    /** Thrown when the stored session is no longer valid. */
    class SessionExpired : Exception("Session expired — tap Check to log in again")

    /**
     * Blocking check (call off the main thread) that returns true only if the
     * cookie is a fully valid, unlocked session — i.e. the authenticated
     * endpoint returns 200. A locked / "confirm you're a human" session returns
     * 401/403 and therefore false.
     */
    fun isValidSession(roblosecurity: String): Boolean = try {
        val conn = (URL("https://users.roblox.com/v1/users/authenticated").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = false
            setRequestProperty("Cookie", ".ROBLOSECURITY=$roblosecurity")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile")
        }
        val code = conn.responseCode
        conn.disconnect()
        code == 200
    } catch (e: Exception) {
        false
    }

    suspend fun fetchInfo(roblosecurity: String): Info = withContext(Dispatchers.IO) {
        val authed = JSONObject(get("https://users.roblox.com/v1/users/authenticated", roblosecurity))
        val userId = authed.getLong("id")
        val username = authed.optString("name")

        val detail = JSONObject(get("https://users.roblox.com/v1/users/$userId", roblosecurity))
        val displayName = detail.optString("displayName", authed.optString("displayName"))
        val createdIso = detail.optString("created")

        val robux = runCatching {
            JSONObject(get("https://economy.roblox.com/v1/user/currency", roblosecurity))
                .optLong("robux", -1L)
        }.getOrDefault(-1L)

        val premium = runCatching {
            get("https://premiumfeatures.roblox.com/v1/users/$userId/validate-membership", roblosecurity)
                .trim().equals("true", ignoreCase = true)
        }.getOrDefault(false)

        val friends = runCatching {
            JSONObject(get("https://friends.roblox.com/v1/users/$userId/friends/count", roblosecurity))
                .optLong("count", -1L)
        }.getOrDefault(-1L)

        val followers = runCatching {
            JSONObject(get("https://friends.roblox.com/v1/users/$userId/followers/count", roblosecurity))
                .optLong("count", -1L)
        }.getOrDefault(-1L)

        val rap = runCatching { fetchRap(userId, roblosecurity) }.getOrDefault(-1L)

        Info(userId, username, displayName, createdIso, robux, rap, premium, friends, followers)
    }

    /** Sums recent-average-price across the account's collectibles (capped). */
    private fun fetchRap(userId: Long, cookie: String): Long {
        var total = 0L
        var cursor: String? = null
        var page = 0
        do {
            val url = buildString {
                append("https://inventory.roblox.com/v1/users/")
                append(userId)
                append("/assets/collectibles?sortOrder=Asc&limit=100")
                if (!cursor.isNullOrBlank()) append("&cursor=").append(cursor)
            }
            val obj = JSONObject(get(url, cookie))
            val data = obj.optJSONArray("data") ?: break
            for (i in 0 until data.length()) {
                total += data.getJSONObject(i).optLong("recentAveragePrice", 0L)
            }
            cursor = obj.optString("nextPageCursor").ifBlank { null }
            page++
        } while (cursor != null && page < 20)
        return total
    }

    private fun get(urlStr: String, roblosecurity: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("Cookie", ".ROBLOSECURITY=$roblosecurity")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile",
            )
        }
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) throw SessionExpired()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw Exception("HTTP $code")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
