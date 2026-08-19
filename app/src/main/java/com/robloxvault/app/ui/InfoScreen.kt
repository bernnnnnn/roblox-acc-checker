package com.robloxvault.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robloxvault.app.VaultViewModel
import com.robloxvault.app.data.Account

@Composable
fun InfoScreen(
    accounts: List<Account>,
    refreshingAll: Boolean,
    onRefreshAll: () -> Unit,
    onRefreshOne: (Account) -> Unit,
    onCopyText: (String, String) -> Unit,
    onShare: (Set<String>) -> Unit,
    accountInfoText: (Account) -> String,
    contentPadding: PaddingValues,
) {
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    fun selectedText(): String =
        accounts.filter { it.id in selected }.joinToString("\n\n") { accountInfoText(it) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GradientHeader(
                title = "Account Info",
                subtitle = if (selectMode) "${selected.size} selected" else "Creation · RAP · Robux · more",
                icon = Icons.Filled.QueryStats,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderIcon(Icons.Filled.Checklist, "Select") {
                            selectMode = !selectMode
                            if (!selectMode) selected.clear()
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(color = NoctraAccent.copy(alpha = 0.16f), shape = CircleShape) {
                            IconButton(onClick = onRefreshAll, enabled = !refreshingAll) {
                                if (refreshingAll) {
                                    CircularProgressIndicator(color = NoctraAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh all", tint = NoctraAccent)
                                }
                            }
                        }
                    }
                },
            )
        }

        if (selectMode) {
            item {
                SelectionBar(
                    count = selected.size,
                    total = accounts.size,
                    onSelectAll = { selected.clear(); selected.addAll(accounts.map { it.id }) },
                    onClear = { selected.clear() },
                    onCopy = { onCopyText(selectedText(), "${selected.size} accounts") },
                    onShare = { onShare(selected.toSet()) },
                )
            }
        }

        if (accounts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No accounts yet. Add some in the Vault tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (!selectMode) {
                item {
                    Text(
                        "Tap ↻ to load info. Robux/RAP need a login — use Check in the Vault tab first.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(accounts, key = { it.id }) { account ->
                Box(Modifier.padding(horizontal = 14.dp)) {
                    InfoCard(
                        account = account,
                        selectMode = selectMode,
                        selected = account.id in selected,
                        onToggleSelect = {
                            if (account.id in selected) selected.remove(account.id) else selected.add(account.id)
                        },
                        onRefresh = { onRefreshOne(account) },
                        onCopy = onCopyText,
                        onCopyAll = { onCopyText(accountInfoText(account), "@${account.username} info") },
                        onShareAll = { onShare(setOf(account.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(color = NoctraAccent.copy(alpha = 0.16f), shape = CircleShape) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = desc, tint = NoctraAccent)
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = if (count == total) onClear else onSelectAll) {
                Text(if (count == total) "None" else "All")
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCopy, enabled = count > 0) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Copy")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onShare, enabled = count > 0) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Share")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(
    account: Account,
    selectMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: (String, String) -> Unit,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("@${account.username}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (account.displayName.isNotBlank()) {
                        Text(account.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                if (account.premium) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = "Premium", tint = StatusWarn)
                    Spacer(Modifier.width(4.dp))
                }
                if (!selectMode) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = NoctraAccent)
                    }
                }
            }

            if (account.hasScreenshot) {
                Spacer(Modifier.height(10.dp))
                ScreenshotThumb(account.screenshotPath)
            }

            if (account.hasInfo) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Robux", VaultViewModel.formatNumber(account.robux), StatusGood)
                    StatPill("RAP", VaultViewModel.formatNumber(account.rap), NoctraAccent)
                    StatPill("Friends", VaultViewModel.formatNumber(account.friends), NoctraChip)
                    StatPill("Followers", VaultViewModel.formatNumber(account.followers), NoctraChip)
                    StatPill("Created", VaultViewModel.formatCreated(account.createdIso), NoctraMuted)
                }

                Spacer(Modifier.height(12.dp))
                CopyableRow("Username", account.username) { onCopy(account.username, "Username") }
                if (account.userId > 0) CopyableRow("User ID", account.userId.toString()) { onCopy(account.userId.toString(), "User ID") }
                CopyableRow("Created", VaultViewModel.formatCreated(account.createdIso)) { onCopy(VaultViewModel.formatCreated(account.createdIso), "Created") }
                CopyableRow("Robux", VaultViewModel.formatNumber(account.robux)) { onCopy(account.robux.coerceAtLeast(0L).toString(), "Robux") }
                CopyableRow("RAP", VaultViewModel.formatNumber(account.rap)) { onCopy(account.rap.coerceAtLeast(0L).toString(), "RAP") }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopyAll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Copy")
                    }
                    FilledTonalButton(onClick = onShareAll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Share")
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    account.infoError.ifBlank {
                        if (account.hasSession) "Tap ↻ to load info." else "Not logged in — use Check in Vault first."
                    },
                    fontSize = 13.sp,
                    color = if (account.infoError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}
