package com.robloxvault.app.data

import java.util.UUID

/** Status of the last login verification for an account. */
enum class CheckStatus { UNKNOWN, VALID, INVALID, NEEDS_VERIFICATION, ERROR }

/**
 * A single Roblox credential the user owns, stored locally and encrypted.
 * The password and session token never leave the device except into Roblox's
 * own endpoints. The `info*` fields cache profile data fetched with this
 * account's own session (Robux, RAP, etc.). A value of -1 means "unknown".
 */
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val password: String,
    val status: CheckStatus = CheckStatus.UNKNOWN,
    val note: String = "",
    val lastCheckedEpoch: Long = 0L,

    // Captured after a successful login; used to read this account's own data.
    val roblosecurity: String = "",

    // Cached account info.
    val userId: Long = 0L,
    val displayName: String = "",
    val createdIso: String = "",
    val robux: Long = -1L,
    val rap: Long = -1L,
    val premium: Boolean = false,
    val friends: Long = -1L,
    val followers: Long = -1L,
    val infoUpdatedEpoch: Long = 0L,
    val infoError: String = "",
) {
    val hasSession: Boolean get() = roblosecurity.isNotBlank()
    val hasInfo: Boolean get() = infoUpdatedEpoch > 0L
}
