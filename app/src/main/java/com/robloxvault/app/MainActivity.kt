package com.robloxvault.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.File

class MainActivity : FragmentActivity() {

    private lateinit var lockManager: LockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockManager = LockManager(this)

        setContent {
            RobloxVaultTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Image(
                        painter = painterResource(R.drawable.app_bg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.42f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
                    Surface(color = Color.Transparent, contentColor = com.robloxvault.app.ui.NoctraText) {
                    val vm: VaultViewModel = viewModel()

                    // Cached once — reading EncryptedSharedPreferences on every
                    // recomposition was a real source of jank.
                    val hasPin = remember { lockManager.hasPin() }
                    val bioAvailable = remember { canUseBiometrics() && hasPin }

                    var unlocked by remember { mutableStateOf(!hasPin && lockManager.setupSkipped()) }
                    var tab by remember { mutableIntStateOf(0) }

                    // Auto-check queue state.
                    var autoQueue by remember { mutableStateOf<List<String>>(emptyList()) }
                    var autoIndex by remember { mutableIntStateOf(0) }

                    val loginLauncher = rememberLauncherForActivityResult(
                        StartActivityForResult(),
                    ) { result ->
                        val data = result.data
                        val id = data?.getStringExtra(LoginActivity.EXTRA_ACCOUNT_ID)
                        val cookie = data?.getStringExtra(LoginActivity.EXTRA_COOKIE).orEmpty()
                        val shot = data?.getStringExtra(LoginActivity.EXTRA_SHOT).orEmpty()
                        if (id != null) {
                            when (data.getStringExtra(LoginActivity.EXTRA_RESULT)) {
                                LoginActivity.RESULT_VALID -> vm.recordCheck(id, CheckStatus.VALID, cookie, shot)
                                LoginActivity.RESULT_INVALID -> vm.recordCheck(id, CheckStatus.INVALID, "", "")
                                else -> Unit
                            }
                        }
                        // Advance the auto-check queue, if running.
                        if (autoQueue.isNotEmpty()) autoIndex += 1
                    }

                    // Drives the auto-check queue: launches each account in turn.
                    LaunchedEffect(autoQueue, autoIndex) {
                        if (autoQueue.isEmpty()) return@LaunchedEffect
                        if (autoIndex >= autoQueue.size) {
                            autoQueue = emptyList(); autoIndex = 0
                            return@LaunchedEffect
                        }
                        val next = vm.accounts.firstOrNull { it.id == autoQueue[autoIndex] }
                        if (next != null) {
                            startLogin(next, LoginActivity.MODE_CHECK) { i -> loginLauncher.launch(i) }
                        } else {
                            autoIndex += 1
                        }
                    }

                    if (!unlocked) {
                        LockScreen(
                            hasPin = hasPin,
                            biometricAvailable = bioAvailable,
                            onUnlock = { pin -> lockManager.verify(pin).also { if (it) unlocked = true } },
                            onSetPin = { pin -> lockManager.setPin(pin); unlocked = true },
                            onSkip = { lockManager.markSetupSkipped(); unlocked = true },
                            onBiometric = { promptBiometric { unlocked = true } },
                        )
                    } else {
                        Scaffold(
                            containerColor = Color.Transparent,
                            bottomBar = {
                                NavigationBar(containerColor = com.robloxvault.app.ui.NoctraSurface.copy(alpha = 0.92f)) {
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
                                    onAutoCheck = {
                                        val ids = vm.visibleAccounts().map { it.id }
                                        if (ids.isNotEmpty()) { autoIndex = 0; autoQueue = ids }
                                    },
                                    onEnableAutofill = { enableAutofill() },
                                    onOpenRoblox = { openRobloxApp() },
                                    contentPadding = innerPadding,
                                )
                                else -> InfoScreen(
                                    accounts = vm.accounts,
                                    refreshingAll = vm.refreshingAll,
                                    onRefreshAll = vm::refreshAllInfo,
                                    onRefreshOne = { acc -> vm.refreshInfo(acc.id) },
                                    onCopyText = { value, label -> copyToClipboard(value, label) },
                                    onShare = { ids -> shareInfo(vm.infoTextForIds(ids), vm.screenshotPathsForIds(ids)) },
                                    accountInfoText = { acc -> vm.accountInfoText(acc) },
                                    contentPadding = innerPadding,
                                )
                            }
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
        if (mode == LoginActivity.MODE_OPEN) {
            intent.putExtra(LoginActivity.EXTRA_COOKIE, account.roblosecurity)
        }
        launch(intent)
    }

    /** Opens system settings to set this app as the autofill provider. */
    private fun enableAutofill() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    .setData(Uri.parse("package:$packageName")),
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings → Passwords, passkeys & autofill → pick Roblox Vault", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    /** Launches the installed Roblox app (or its Play Store page). */
    private fun openRobloxApp() {
        val launch = packageManager.getLaunchIntentForPackage("com.roblox.client")
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, "Roblox app not installed", Toast.LENGTH_SHORT).show()
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.roblox.client")))
            }
        }
    }

    private fun copyToClipboard(value: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Shares info text plus any saved screenshots via the system share sheet. */
    private fun shareInfo(text: String, imagePaths: List<String>) {
        val authority = "$packageName.fileprovider"
        val uris = ArrayList<Uri>()
        imagePaths.forEach { p ->
            val f = File(p)
            if (f.exists()) {
                runCatching { FileProvider.getUriForFile(this, authority, f) }
                    .getOrNull()?.let { uris.add(it) }
            }
        }
        val intent = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TEXT, text)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share account info"))
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
