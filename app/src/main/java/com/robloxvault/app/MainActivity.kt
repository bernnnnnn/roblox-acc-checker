package com.robloxvault.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robloxvault.app.data.Account
import com.robloxvault.app.data.CheckStatus
import com.robloxvault.app.login.LoginActivity
import com.robloxvault.app.security.LockManager
import com.robloxvault.app.ui.InfoScreen
import com.robloxvault.app.ui.LockScreen
import com.robloxvault.app.ui.RobloxVaultTheme
import com.robloxvault.app.ui.VaultScreen

class MainActivity : FragmentActivity() {

    private lateinit var lockManager: LockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockManager = LockManager(this)

        setContent {
            RobloxVaultTheme {
                Surface {
                    val vm: VaultViewModel = viewModel()

                    val startUnlocked = !lockManager.hasPin() && lockManager.setupSkipped()
                    var unlocked by remember { mutableStateOf(startUnlocked) }
                    var tab by remember { mutableIntStateOf(0) }

                    val loginLauncher = rememberLauncherForActivityResult(
                        StartActivityForResult(),
                    ) { result ->
                        val data = result.data ?: return@rememberLauncherForActivityResult
                        val id = data.getStringExtra(LoginActivity.EXTRA_ACCOUNT_ID)
                            ?: return@rememberLauncherForActivityResult
                        val cookie = data.getStringExtra(LoginActivity.EXTRA_COOKIE).orEmpty()
                        when (data.getStringExtra(LoginActivity.EXTRA_RESULT)) {
                            LoginActivity.RESULT_VALID -> vm.recordCheck(id, CheckStatus.VALID, cookie)
                            LoginActivity.RESULT_INVALID -> vm.recordCheck(id, CheckStatus.INVALID, "")
                            else -> Unit
                        }
                    }

                    if (!unlocked) {
                        LockScreen(
                            hasPin = lockManager.hasPin(),
                            biometricAvailable = canUseBiometrics() && lockManager.hasPin(),
                            onUnlock = { pin -> lockManager.verify(pin).also { if (it) unlocked = true } },
                            onSetPin = { pin -> lockManager.setPin(pin); unlocked = true },
                            onSkip = { lockManager.markSetupSkipped(); unlocked = true },
                            onBiometric = { promptBiometric { unlocked = true } },
                        )
                    } else {
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = tab == 0,
                                        onClick = { tab = 0 },
                                        icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                                        label = { Text("Vault") },
                                    )
                                    NavigationBarItem(
                                        selected = tab == 1,
                                        onClick = { tab = 1 },
                                        icon = { Icon(Icons.Filled.QueryStats, contentDescription = null) },
                                        label = { Text("Info") },
                                    )
                                }
                            },
                        ) { innerPadding ->
                            when (tab) {
                                0 -> VaultScreen(
                                    accounts = vm.visibleAccounts(),
                                    query = vm.query,
                                    onQueryChange = vm::updateQuery,
                                    onImport = { text -> vm.importCombos(text) },
                                    onAdd = { u, p, n -> vm.addAccount(u, p, n) },
                                    onDelete = vm::delete,
                                    onCheck = { acc -> startLogin(acc, LoginActivity.MODE_CHECK) { i -> loginLauncher.launch(i) } },
                                    onOpen = { acc -> startLogin(acc, LoginActivity.MODE_OPEN) { i -> loginLauncher.launch(i) } },
                                    onCopy = { value, label -> copyToClipboard(value, label) },
                                    contentPadding = innerPadding,
                                )
                                else -> InfoScreen(
                                    accounts = vm.accounts,
                                    refreshingAll = vm.refreshingAll,
                                    onRefreshAll = vm::refreshAllInfo,
                                    onRefreshOne = { acc -> vm.refreshInfo(acc.id) },
                                    onCopy = { value, label -> copyToClipboard(value, label) },
                                    accountInfoText = { acc -> vm.accountInfoText(acc) },
                                    allInfoText = { vm.allAccountsInfoText() },
                                    contentPadding = innerPadding,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startLogin(account: Account, mode: String, launch: (Intent) -> Unit) {
        val intent = Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_USERNAME, account.username)
            .putExtra(LoginActivity.EXTRA_PASSWORD, account.password)
            .putExtra(LoginActivity.EXTRA_MODE, mode)
            .putExtra(LoginActivity.EXTRA_ACCOUNT_ID, account.id)
        launch(intent)
    }

    private fun copyToClipboard(value: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun canUseBiometrics(): Boolean {
        val result = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Roblox Vault")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
