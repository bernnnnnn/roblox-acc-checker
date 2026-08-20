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

    /** Public profile data readable from just a username — no login required. */
    data class PublicInfo(
        val userId: Long,
        val username: String,
        val displayName: String,
        val createdIso: String,
        val banned: Boolean,
        val friends: Long,
        val followers: Long,
        val rap: Long,
        val itemCount: Long,
        val inventoryPrivate: Boolean,
    )

    /** Thrown when the stored session is no longer valid. */
    class SessionExpired : Exception("Session expired — tap Check to log in again")

    /**
     * Fetches everything publicly visible for a username — created date, friends,
     * followers, and (if the inventory isn't hidden) RAP + item count. No cookie,
     * no login, so it works for locked accounts too. Robux is NOT public and is
     * therefore never returned here.
     */
    suspend fun fetchPublicInfo(username: String): PublicInfo? = withContext(Dispatchers.IO) {
        val userId = resolveUserId(username) ?: return@withContext null

        val detail = JSONObject(getPublic("https://users.roblox.com/v1/users/$userId"))
        val name = detail.optString("name", username)
        val displayName = detail.optString("displayName", name)
        val createdIso = detail.optString("created")
        val banned = detail.optBoolean("isBanned", false)

        val friends = runCatching {
            JSONObject(getPublic("https://friends.roblox.com/v1/users/$userId/friends/count")).optLong("count", -1L)
        }.getOrDefault(-1L)
        val followers = runCatching {
            JSONObject(getPublic("https://friends.roblox.com/v1/users/$userId/followers/count")).optLong("count", -1L)
        }.getOrDefault(-1L)

        val canView = runCatching {
            JSONObject(getPublic("https://inventory.roblox.com/v1/users/$userId/can-view-inventory")).optBoolean("canView", false)
        }.getOrDefault(false)

        var rap = -1L
        var items = -1L
        if (canView) {
            runCatching { collectibles(userId, null) }.getOrNull()?.let { (r, c) -> rap = r; items = c }
        }

        PublicInfo(userId, name, displayName, createdIso, banned, friends, followers, rap, items, inventoryPrivate = !canView)
    }

    /** Public avatar (full body) PNG url for a user. */
    fun fetchAvatarUrl(userId: Long): String? = try {
        val json = getPublic("https://thumbnails.roblox.com/v1/users/avatar?userIds=$userId&size=420x420&format=Png&isCircular=false")
        JSONObject(json).optJSONArray("data")?.getJSONObject(0)?.optString("imageUrl")?.takeIf { it.startsWith("http") }
    } catch (e: Exception) {
        null
    }

    /** Resolves a username to its userId (public); exposed for card building. */
    fun userIdForUsername(username: String): Long? = resolveUserId(username)

    /** Downloads raw bytes from a public URL (e.g. an avatar PNG). */
    fun downloadBytes(urlStr: String): ByteArray? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Resolves a username to its userId via the public usernames endpoint. */
    private fun resolveUserId(username: String): Long? = try {
        val body = JSONObject().apply {
            put("usernames", org.json.JSONArray().put(username))
            put("excludeBannedUsers", false)
        }.toString()
        val resp = postPublic("https://users.roblox.com/v1/usernames/users", body)
        val arr = JSONObject(resp).optJSONArray("data")
        if (arr != null && arr.length() > 0) arr.getJSONObject(0).optLong("id").takeIf { it > 0 } else null
    } catch (e: Exception) {
        null
    }

    /** Returns (rap, itemCount) summed over public collectibles. */
    private fun collectibles(userId: Long, cookieOrNull: String?): Pair<Long, Long> {
        var rap = 0L
        var count = 0L
        var cursor: String? = null
        var page = 0
        do {
            val url = buildString {
                append("https://inventory.roblox.com/v1/users/").append(userId)
                append("/assets/collectibles?sortOrder=Asc&limit=100")
                if (!cursor.isNullOrBlank()) append("&cursor=").append(cursor)
            }
            val json = if (cookieOrNull != null) get(url, cookieOrNull) else getPublic(url)
            val obj = JSONObject(json)
            val data = obj.optJSONArray("data") ?: break
            for (i in 0 until data.length()) {
                rap += data.getJSONObject(i).optLong("recentAveragePrice", 0L)
                count += 1
            }
            cursor = obj.optString("nextPageCursor").ifBlank { null }
            page++
        } while (cursor != null && page < 20)
        return rap to count
    }

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

    /** Unauthenticated GET for public endpoints. */
    private fun getPublic(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** Unauthenticated POST with a JSON body (public endpoints only). */
    private fun postPublic(urlStr: String, jsonBody: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile")
        }
        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
