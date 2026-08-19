package com.robloxvault.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robloxvault.app.VaultViewModel
import com.robloxvault.app.data.Account
import com.robloxvault.app.data.CheckStatus

@Composable
fun MainScreen(
    accounts: List<Account>,
    query: String,
    onQueryChange: (String) -> Unit,
    onImport: (String) -> Int,
    onAdd: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onLogin: (Account) -> Unit,
    onOpen: (Account) -> Unit,
    onOpenRoblox: () -> Unit,
    onCopy: (String, String) -> Unit,
    onCycleStatus: (String) -> Unit,
    onLoadStats: (Account) -> Unit,
    onShare: (Account) -> Unit,
    onCheckAll: () -> Unit,
    checking: Boolean,
    onEnableAutofill: () -> Unit,
    contentPadding: PaddingValues,
) {
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Account?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GradientHeader(
                title = "Roblox Vault",
                subtitle = "${accounts.size} account${if (accounts.size == 1) "" else "s"}",
                icon = Icons.Filled.Shield,
                trailing = {
                    Row {
                        Surface(color = NoctraAccent.copy(alpha = 0.16f), shape = CircleShape) {
                            IconButton(onClick = onCheckAll, enabled = !checking) {
                                if (checking) {
                                    CircularProgressIndicator(color = NoctraAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Check all", tint = NoctraAccent)
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        HeaderCircle(Icons.Filled.Password, "Enable autofill", onEnableAutofill)
                        Spacer(Modifier.width(8.dp))
                        HeaderCircle(Icons.Filled.Add, "Add", { showAdd = true })
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            )
        }

        if (accounts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No accounts.\nTap + to paste account:pass lines.", color = NoctraMuted)
                }
            }
        } else {
            items(accounts, key = { it.id }) { account ->
                Box(Modifier.padding(horizontal = 14.dp)) {
                    AccountCard(
                        account = account,
                        onLogin = { onLogin(account) },
                        onOpen = { onOpen(account) },
                        onOpenRoblox = onOpenRoblox,
                        onCopy = onCopy,
                        onCycleStatus = { onCycleStatus(account.id) },
                        onLoadStats = { onLoadStats(account) },
                        onShare = { onShare(account) },
                        onDelete = { confirmDelete = account },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(onDismiss = { showAdd = false }, onImport = onImport, onAdd = onAdd)
    }

    confirmDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove @${acc.username}?") },
            text = { Text("This only deletes it locally.") },
            confirmButton = { TextButton(onClick = { onDelete(acc.id); confirmDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HeaderCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(color = NoctraAccent.copy(alpha = 0.16f), shape = CircleShape) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = desc, tint = NoctraAccent) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(
    account: Account,
    onLogin: () -> Unit,
    onOpen: () -> Unit,
    onOpenRoblox: () -> Unit,
    onCopy: (String, String) -> Unit,
    onCycleStatus: () -> Unit,
    onLoadStats: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = account.username.ifBlank { if (account.userId > 0) "id ${account.userId}" else "cookie account" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NoctraSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("@$name", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NoctraTextHi)
                    if (account.note.isNotBlank()) {
                        Text(account.note, fontSize = 12.sp, color = NoctraMuted)
                    } else if (account.hasSession && account.username.isBlank()) {
                        Text("cookie — tap Check for name", fontSize = 12.sp, color = NoctraMuted)
                    }
                }
                StatusChip(account.status, onCycleStatus)
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (account.hasSession) {
                    PrimaryChip(Icons.Filled.Login, "Open", onOpen)
                    GhostChip(Icons.Filled.Refresh, "Check") { onLoadStats() }
                } else {
                    PrimaryChip(Icons.Filled.Login, "Login", onLogin)
                }
                GhostChip(Icons.Filled.SportsEsports, "Roblox app", onOpenRoblox)
                if (account.username.isNotBlank()) {
                    GhostChip(Icons.Filled.ContentCopy, "User") { onCopy(account.username, "Username") }
                }
                if (account.password.isNotEmpty()) {
                    GhostChip(Icons.Filled.ContentCopy, "Pass") { onCopy(account.password, "Password") }
                }
                GhostChip(Icons.Filled.ExpandMore, if (expanded) "Hide" else "Stats") { expanded = !expanded }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    if (account.hasScreenshot) {
                        ScreenshotThumb(account.screenshotPath)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (account.hasInfo) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill("Robux", if (account.robux >= 0) VaultViewModel.formatNumber(account.robux) else "login", StatusGood)
                            StatPill("RAP", if (account.inventoryPrivate) "private" else VaultViewModel.formatNumber(account.rap), NoctraAccent)
                            StatPill("Items", if (account.inventoryPrivate) "private" else VaultViewModel.formatNumber(account.itemCount), NoctraAccent)
                            StatPill("Friends", VaultViewModel.formatNumber(account.friends), NoctraChip)
                            StatPill("Followers", VaultViewModel.formatNumber(account.followers), NoctraChip)
                            StatPill("Created", VaultViewModel.formatCreated(account.createdIso), NoctraMuted)
                        }
                        Spacer(Modifier.height(10.dp))
                    } else if (account.infoError.isNotBlank()) {
                        Text(account.infoError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text("Log in once, then load stats.", color = NoctraMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onLoadStats, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(if (account.hasInfo) "Refresh" else "Load")
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Share")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(username: String) {
    Surface(color = NoctraAccent.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                (username.firstOrNull()?.uppercaseChar() ?: '?').toString(),
                color = NoctraAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun StatusChip(status: CheckStatus, onClick: () -> Unit) {
    val (label, color) = when (status) {
        CheckStatus.VALID -> "Working" to StatusGood
        CheckStatus.NEEDS_VERIFICATION -> "Locked" to StatusWarn
        CheckStatus.INVALID -> "Dead" to StatusBad
        CheckStatus.ERROR -> "Error" to NoctraMuted
        CheckStatus.UNKNOWN -> "Unchecked" to NoctraMuted
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
private fun PrimaryChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(color = NoctraAccent, shape = RoundedCornerShape(50), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NoctraBlack, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = NoctraBlack, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GhostChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(color = NoctraSurfaceHi, shape = RoundedCornerShape(50), onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NoctraText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = NoctraText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ScreenshotThumb(path: String) {
    val image: ImageBitmap? = remember(path) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Logged-in screenshot",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .background(NoctraSurfaceHi, RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Int,
    onAdd: (String, String, String) -> Unit,
) {
    var combo by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add accounts") },
        text = {
            Column {
                Text("Paste account:pass OR .ROBLOSECURITY cookies (one per line):", fontSize = 13.sp, color = NoctraMuted)
                OutlinedTextField(
                    value = combo,
                    onValueChange = { combo = it; message = "" },
                    placeholder = { Text("user:pass\n_|WARNING:-DO-NOT-SHARE…") },
                    modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("…or one manually:", fontSize = 13.sp, color = NoctraMuted)
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (message.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)); Text(message, color = NoctraAccent, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var added = 0
                if (combo.isNotBlank()) added += onImport(combo)
                if (user.isNotBlank() && pass.isNotEmpty()) { onAdd(user, pass, note); added += 1 }
                if (added == 0) message = "Nothing to add — check the format." else onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
