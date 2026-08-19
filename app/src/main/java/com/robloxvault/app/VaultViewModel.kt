package com.robloxvault.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.robloxvault.app.data.Account
import com.robloxvault.app.data.AccountStore
import com.robloxvault.app.data.CheckStatus

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)

    val accounts = mutableStateListOf<Account>()
    var query by mutableStateOf("")
        private set

    init {
        accounts.addAll(store.load())
    }

    fun setQuery(value: String) { query = value }

    fun visibleAccounts(): List<Account> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) accounts
        else accounts.filter { it.username.lowercase().contains(q) || it.note.lowercase().contains(q) }
    }

    /** Import combos, skipping usernames already present (case-insensitive). */
    fun importCombos(text: String): Int {
        val existing = accounts.map { it.username.lowercase() }.toHashSet()
        val parsed = AccountStore.parseComboList(text)
            .filter { existing.add(it.username.lowercase()) }
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

    fun updateStatus(id: String, status: CheckStatus) {
        val idx = accounts.indexOfFirst { it.id == id }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(
                status = status,
                lastCheckedEpoch = System.currentTimeMillis(),
            )
            persist()
        }
    }

    fun exportText(): String =
        accounts.joinToString("\n") { "${it.username}:${it.password}" }

    private fun persist() = store.save(accounts.toList())
}
