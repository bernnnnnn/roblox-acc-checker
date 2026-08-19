package com.robloxvault.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Roblox Vault")
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add / Import") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search accounts") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            if (accounts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No accounts yet.\nTap Add / Import to paste account:pass lines.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onCheck = { onCheck(account) },
                            onOpen = { onOpen(account) },
                            onCopyUser = { onCopy(account.username, "Username") },
                            onCopyPass = { onCopy(account.password, "Password") },
                            onDelete = { confirmDelete = account },
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
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
                TextButton(onClick = {
                    onDelete(acc.id); confirmDelete = null
                }) { Text("Remove") }
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
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "@${account.username}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(account.status)
            }
            Text(
                text = "••••••••",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (account.note.isNotBlank()) {
                Text(
                    account.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onCheck, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Check")
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Open")
                }
            }
            Row {
                IconButton(onClick = onCopyUser) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy username")
                }
                Text("User", fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onCopyPass) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password")
                }
                Text("Pass", fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
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
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
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
                if (added == 0) {
                    message = "Nothing to add — check the format."
                } else {
                    onDismiss()
                }
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
