package com.robloxvault.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.robloxvault.app.share.ProfileCard
import kotlinx.coroutines.launch
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.robloxvault.app.data.Account
import com.robloxvault.app.login.LoginActivity
import com.robloxvault.app.security.LockManager
import com.robloxvault.app.ui.LockScreen
import com.robloxvault.app.ui.MainScreen
import com.robloxvault.app.ui.NoctraText
import com.robloxvault.app.ui.RobloxVaultTheme

class MainActivity : FragmentActivity() {

    private lateinit var lockManager: LockManager
    private val vm: VaultViewModel by viewModels()

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
                    Surface(color = Color.Transparent, contentColor = NoctraText) {
                        val hasPin = remember { lockManager.hasPin() }
                        val bioAvailable = remember { canUseBiometrics() && hasPin }
                        var unlocked by remember { mutableStateOf(!hasPin && lockManager.setupSkipped()) }

                        val loginLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                            val data = result.data
                            val id = data?.getStringExtra(LoginActivity.EXTRA_ACCOUNT_ID)
                            val cookie = data?.getStringExtra(LoginActivity.EXTRA_COOKIE).orEmpty()
                            val shot = data?.getStringExtra(LoginActivity.EXTRA_SHOT).orEmpty()
                            val outcome = data?.getStringExtra(LoginActivity.EXTRA_RESULT)
                            if (id != null && outcome != null) vm.recordLogin(id, outcome, cookie, shot)
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
                            MainScreen(
                                accounts = vm.visibleAccounts(),
                                query = vm.query,
                                onQueryChange = vm::updateQuery,
                                onImport = { text -> vm.importCombos(text) },
                                onAdd = { u, p, n -> vm.addAccount(u, p, n) },
                                onDelete = vm::delete,
                                onLogin = { acc -> startLogin(acc, LoginActivity.MODE_CHECK) { i -> loginLauncher.launch(i) } },
                                onOpen = { acc -> startLogin(acc, LoginActivity.MODE_OPEN) { i -> loginLauncher.launch(i) } },
                                onOpenRoblox = { openRobloxApp() },
                                onCopy = { value, label -> copyToClipboard(value, label) },
                                onCycleStatus = { id -> vm.cycleStatus(id) },
                                onLoadStats = { acc -> vm.refreshInfo(acc.id) },
                                onCopyCard = { acc -> copyCard(acc) },
                                onShareDiscord = { list -> shareToDiscord(list) },
                                onCheckAll = { vm.checkAll() },
                                onCheckSelected = { ids -> vm.checkSelected(ids) },
                                checking = vm.refreshingAll,
                                contentPadding = PaddingValues(0.dp),
                            )
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

    private fun copyToClipboard(value: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Builds a profile card for [account] and copies the image to the clipboard. */
    private fun copyCard(account: Account) {
        Toast.makeText(this, "Building card…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val file = ProfileCard.build(this@MainActivity, account)
            if (file == null) { Toast.makeText(this@MainActivity, "Couldn't build card (check the account)", Toast.LENGTH_SHORT).show(); return@launch }
            val uri = runCatching { androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file) }.getOrNull()
                ?: return@launch
            grantUriPermission("com.discord", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val clip = ClipData.newUri(contentResolver, "Profile card", uri)
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(this@MainActivity, "Card copied — paste it in Discord", Toast.LENGTH_LONG).show()
        }
    }

    /** Builds profile card(s) and shares them (image + text) straight to Discord if installed. */
    private fun shareToDiscord(accounts: List<Account>) {
        if (accounts.isEmpty()) return
        Toast.makeText(this, "Building ${accounts.size} card${if (accounts.size == 1) "" else "s"}…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val authority = "$packageName.fileprovider"
            val uris = ArrayList<Uri>()
            for (acc in accounts) {
                val file = ProfileCard.build(this@MainActivity, acc) ?: continue
                runCatching { androidx.core.content.FileProvider.getUriForFile(this@MainActivity, authority, file) }
                    .getOrNull()?.let { uris.add(it) }
            }
            if (uris.isEmpty()) { Toast.makeText(this@MainActivity, "Couldn't build cards", Toast.LENGTH_SHORT).show(); return@launch }
            // Images only — no text added to the share.
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uris[0]) }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/png"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
            }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val discordInstalled = packageManager.getLaunchIntentForPackage("com.discord") != null
            try {
                if (discordInstalled) { send.setPackage("com.discord"); startActivity(send) }
                else startActivity(Intent.createChooser(send, "Share"))
            } catch (e: Exception) {
                send.setPackage(null)
                startActivity(Intent.createChooser(send, "Share"))
            }
            vm.markShared(accounts.map { it.id }.toSet())
        }
    }

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

    private fun canUseBiometrics(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Roblox Vault")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }
}
