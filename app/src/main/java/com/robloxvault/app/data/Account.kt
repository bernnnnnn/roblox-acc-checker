package com.robloxvault.app.data

import java.util.UUID

/** Status of the last login verification for an account. */
enum class CheckStatus { UNKNOWN, VALID, INVALID, NEEDS_VERIFICATION, ERROR }

/**
 * A single Roblox credential the user owns, stored locally and encrypted.
 * The password never leaves the device except into Roblox's own login page.
 */
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val password: String,
    val status: CheckStatus = CheckStatus.UNKNOWN,
    val note: String = "",
    val lastCheckedEpoch: Long = 0L,
)
