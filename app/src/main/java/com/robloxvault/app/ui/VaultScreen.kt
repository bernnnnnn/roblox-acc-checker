package com.robloxvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robloxvault.app.data.Account
import com.robloxvault.app.data.CheckStatus

@Composable
fun VaultScreen(
    accounts: List<Account>,
    query: String,
    onQueryChange: (String) -> Unit,
    onImport: (String) -> Int,
    onAdd: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCheck: (Account) -> Unit,
    onOpen: (Account) -> Unit,
    onCopy: (String, String) -> Unit,
    contentPadding: PaddingValues,
) {
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Account?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GradientHeader(
                title = "Roblox Vault",
                subtitle = "${accounts.size} account${if (accounts.size == 1) "" else "s"} stored securely",
                icon = Icons.Filled.Shield,
                trailing = {
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White)
                        }
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search accounts") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
            )
        }

        if (accounts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No accounts yet.\nTap + to paste account:pass lines.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(accounts, key = { it.id }) { account ->
                Box(Modifier.padding(horizontal = 14.dp)) {
                    AccountCard(
                        account = account,
                        onCheck = { onCheck(account) },
                        onOpen = { onOpen(account) },
                        onCopyUser = { onCopy(account.username, "Username") },
                        onCopyPass = { onCopy(account.password, "Password") },
                        onCopyCombo = { onCopy("${account.username}:${account.password}", "account:pass") },
                        onDelete = { confirmDelete = account },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddImportDialog(
            onDismiss = { showAdd = false },
            onImport = onImport,
            onAdd = onAdd,
        )
    }

    confirmDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove account?") },
            text = { Text("Remove @${acc.username} from the vault? This only deletes it locally.") },
            confirmButton = {
                TextButton(onClick = { onDelete(acc.id); confirmDelete = null }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
    onCopyUser: () -> Unit,
    onCopyPass: () -> Unit,
    onCopyCombo: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("@${account.username}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "••••••••",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                StatusChip(account.status)
            }
            if (account.note.isNotBlank()) {
                Text(
                    account.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCheck, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check")
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallCopy("User", onCopyUser)
                SmallCopy("Pass", onCopyPass)
                SmallCopy("Combo", onCopyCombo)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallCopy(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun StatusChip(status: CheckStatus) {
    val (label, color) = when (status) {
        CheckStatus.VALID -> "Valid" to Color(0xFF16A34A)
        CheckStatus.INVALID -> "Invalid" to Color(0xFFDC2626)
        CheckStatus.NEEDS_VERIFICATION -> "Verify" to Color(0xFFD97706)
        CheckStatus.ERROR -> "Error" to Color(0xFF6B7280)
        CheckStatus.UNKNOWN -> "Unchecked" to Color(0xFF6B7280)
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AddImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Int,
    onAdd: (String, String, String) -> Unit,
) {
    var combo by remember { mutableStateOf("") }
    var singleUser by remember { mutableStateOf("") }
    var singlePass by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add or import accounts") },
        text = {
            Column {
                Text("Paste account:pass lines (one per line):", fontSize = 13.sp)
                OutlinedTextField(
                    value = combo,
                    onValueChange = { combo = it; message = "" },
                    placeholder = { Text("user1:password1\nuser2:password2") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(top = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("…or add one manually:", fontSize = 13.sp)
                OutlinedTextField(
                    value = singleUser,
                    onValueChange = { singleUser = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = singlePass,
                    onValueChange = { singlePass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                if (message.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var added = 0
                if (combo.isNotBlank()) added += onImport(combo)
                if (singleUser.isNotBlank() && singlePass.isNotEmpty()) {
                    onAdd(singleUser, singlePass, note); added += 1
                }
                if (added == 0) message = "Nothing to add — check the format."
                else onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
