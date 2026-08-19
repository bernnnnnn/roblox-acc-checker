package com.robloxvault.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.robloxvault.app.data.Account
import com.robloxvault.app.data.AccountStore
import com.robloxvault.app.data.CheckStatus
import com.robloxvault.app.data.RobloxApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)

    val accounts = mutableStateListOf<Account>()
    var query by mutableStateOf("")
        private set
    var refreshingAll by mutableStateOf(false)
        private set

    init {
        accounts.addAll(store.load())
    }

    fun updateQuery(value: String) { query = value }

    fun visibleAccounts(): List<Account> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) accounts
        else accounts.filter { it.username.lowercase().contains(q) || it.note.lowercase().contains(q) }
    }

    /** Import accounts (user:pass and/or cookies), skipping duplicates. */
    fun importCombos(text: String): Int {
        val users = accounts.filter { it.username.isNotBlank() }.map { it.username.lowercase() }.toHashSet()
        val cookies = accounts.filter { it.roblosecurity.isNotBlank() }.map { it.roblosecurity }.toHashSet()
        val parsed = AccountStore.parseComboList(text).filter { acc ->
            when {
                acc.username.isNotBlank() -> users.add(acc.username.lowercase())
                acc.roblosecurity.isNotBlank() -> cookies.add(acc.roblosecurity)
                else -> false
            }
        }
        accounts.addAll(parsed)
        persist()
        return parsed.size
    }

    fun addAccount(username: String, password: String, note: String) {
        accounts.add(Account(username = username.trim(), password = password, note = note.trim()))
        persist()
    }

    fun delete(id: String) {
        accounts.removeAll { it.id == id }
        persist()
    }

    fun updateNote(id: String, note: String) = update(id) { it.copy(note = note.trim()) }

    /** Manual status the user sets by tapping the chip: Unchecked → Working → Locked → Dead. */
    fun cycleStatus(id: String) = update(id) {
        val next = when (it.status) {
            CheckStatus.UNKNOWN -> CheckStatus.VALID
            CheckStatus.VALID -> CheckStatus.NEEDS_VERIFICATION
            CheckStatus.NEEDS_VERIFICATION -> CheckStatus.INVALID
            else -> CheckStatus.UNKNOWN
        }
        it.copy(status = next)
    }

    /** Records the outcome of a login check, storing the session + screenshot on success. */
    fun recordCheck(id: String, status: CheckStatus, cookie: String, shotPath: String) {
        update(id) {
            it.copy(
                status = status,
                lastCheckedEpoch = System.currentTimeMillis(),
                roblosecurity = if (status == CheckStatus.VALID && cookie.isNotBlank()) cookie else it.roblosecurity,
                screenshotPath = if (status == CheckStatus.VALID && shotPath.isNotBlank()) shotPath else it.screenshotPath,
            )
        }
        // Auto-pull info right after a successful login.
        if (status == CheckStatus.VALID && cookie.isNotBlank()) refreshInfo(id)
    }

    /** Checks one account via its cookie: validates + pulls info, updates status. */
    fun refreshInfo(id: String) {
        val account = accounts.firstOrNull { it.id == id } ?: return
        if (!account.hasSession) {
            update(id) { it.copy(infoError = "No session — log in once or import a cookie") }
            return
        }
        viewModelScope.launch { checkOne(account) }
    }

    /** Reliable batch check: validates every cookie account and loads its info. */
    fun checkAll() {
        viewModelScope.launch {
            refreshingAll = true
            for (account in accounts.toList()) {
                if (!account.hasSession) {
                    update(account.id) { it.copy(infoError = "No session — log in or import a cookie") }
                    continue
                }
                checkOne(accounts.firstOrNull { it.id == account.id } ?: account)
            }
            refreshingAll = false
        }
    }

    private suspend fun checkOne(account: Account) {
        runCatching { RobloxApi.fetchInfo(account.roblosecurity) }
            .onSuccess { info -> applyInfo(account.id, info) }
            .onFailure { e ->
                update(account.id) {
                    it.copy(
                        status = CheckStatus.INVALID,
                        infoError = e.message ?: "Dead / expired session",
                        lastCheckedEpoch = System.currentTimeMillis(),
                    )
                }
            }
    }

    private fun applyInfo(id: String, info: RobloxApi.Info) = update(id) {
        it.copy(
            username = if (it.username.isBlank() && info.username.isNotBlank()) info.username else it.username,
            userId = info.userId,
            displayName = info.displayName,
            createdIso = info.createdIso,
            robux = info.robux,
            rap = info.rap,
            premium = info.premium,
            friends = info.friends,
            followers = info.followers,
            status = CheckStatus.VALID,
            infoUpdatedEpoch = System.currentTimeMillis(),
            lastCheckedEpoch = System.currentTimeMillis(),
            infoError = "",
        )
    }

    // --- copy-text formatting ------------------------------------------------

    /** Account info as copyable text. Password is intentionally excluded. */
    fun accountInfoText(a: Account): String = buildString {
        appendLine("Username: ${a.username}")
        if (a.userId > 0) appendLine("User ID: ${a.userId}")
        if (a.displayName.isNotBlank()) appendLine("Display name: ${a.displayName}")
        appendLine("Created: ${formatCreated(a.createdIso)}")
        appendLine("Robux: ${formatNumber(a.robux)}")
        appendLine("RAP: ${formatNumber(a.rap)}")
        appendLine("Premium: ${if (a.premium) "Yes" else "No"}")
        appendLine("Friends: ${formatNumber(a.friends)}")
        append("Followers: ${formatNumber(a.followers)}")
    }

    fun allAccountsInfoText(): String =
        accounts.joinToString("\n\n") { accountInfoText(it) }

    fun infoTextForIds(ids: Set<String>): String =
        accounts.filter { it.id in ids }.joinToString("\n\n") { accountInfoText(it) }

    fun screenshotPathsForIds(ids: Set<String>): List<String> =
        accounts.filter { it.id in ids && it.hasScreenshot }.map { it.screenshotPath }

    private fun update(id: String, transform: (Account) -> Account) {
        val idx = accounts.indexOfFirst { it.id == id }
        if (idx >= 0) {
            accounts[idx] = transform(accounts[idx])
            persist()
        }
    }

    private fun persist() = store.save(accounts.toList())

    companion object {
        fun formatNumber(value: Long): String =
            if (value < 0) "—" else "%,d".format(value)

        fun formatCreated(iso: String): String {
            if (iso.isBlank()) return "—"
            // ISO like 2019-05-01T12:34:56.000Z -> 2019-05-01
            return iso.substringBefore('T').ifBlank { iso }
        }

        fun accountAgeYears(iso: String): String {
            if (iso.isBlank()) return ""
            return runCatching {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val created = fmt.parse(iso.substringBefore('T')) ?: return ""
                val days = (System.currentTimeMillis() - created.time) / 86_400_000.0
                val years = days / 365.0
                if (years >= 1) "%.1f yr".format(years) else "${days.toInt()} d"
            }.getOrDefault("")
        }
    }
}
