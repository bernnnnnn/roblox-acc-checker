package com.robloxvault.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onCopy: (String, String) -> Unit,
    accountInfoText: (Account) -> String,
    allInfoText: () -> String,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GradientHeader(
                title = "Account Info",
                subtitle = "Creation date · RAP · Robux · more",
                icon = Icons.Filled.QueryStats,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                            IconButton(onClick = { onCopy(allInfoText(), "All accounts info") }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                            IconButton(onClick = onRefreshAll, enabled = !refreshingAll) {
                                if (refreshingAll) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh all", tint = Color.White)
                                }
                            }
                        }
                    }
                },
            )
        }

        if (accounts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No accounts yet. Add some in the Vault tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                Text(
                    "Tap ↻ to load info. Robux/RAP need a login — use Check in the Vault tab first.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(accounts, key = { it.id }) { account ->
                Box(Modifier.padding(horizontal = 14.dp)) {
                    InfoCard(
                        account = account,
                        onRefresh = { onRefreshOne(account) },
                        onCopy = onCopy,
                        onCopyAll = { onCopy(accountInfoText(account), "@${account.username} info") },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(
    account: Account,
    onRefresh: () -> Unit,
    onCopy: (String, String) -> Unit,
    onCopyAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("@${account.username}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (account.displayName.isNotBlank()) {
                        Text(
                            account.displayName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (account.premium) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = "Premium",
                        tint = Color(0xFFD97706),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (account.hasInfo) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Robux", VaultViewModel.formatNumber(account.robux), Color(0xFF16A34A))
                    StatPill("RAP", VaultViewModel.formatNumber(account.rap), BrandViolet)
                    StatPill("Friends", VaultViewModel.formatNumber(account.friends), BrandCyan)
                    StatPill("Followers", VaultViewModel.formatNumber(account.followers), BrandIndigo)
                    StatPill("Created", VaultViewModel.formatCreated(account.createdIso), Color(0xFF64748B))
                }

                Spacer(Modifier.height(12.dp))
                CopyableRow("Username", account.username) { onCopy(account.username, "Username") }
                CopyableRow("Password", account.password) { onCopy(account.password, "Password") }
                if (account.userId > 0) CopyableRow("User ID", account.userId.toString()) { onCopy(account.userId.toString(), "User ID") }
                CopyableRow("Created", VaultViewModel.formatCreated(account.createdIso)) { onCopy(VaultViewModel.formatCreated(account.createdIso), "Created") }
                CopyableRow("Robux", VaultViewModel.formatNumber(account.robux)) { onCopy(account.robux.coerceAtLeast(0L).toString(), "Robux") }
                CopyableRow("RAP", VaultViewModel.formatNumber(account.rap)) { onCopy(account.rap.coerceAtLeast(0L).toString(), "RAP") }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCopyAll, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy all info")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    account.infoError.ifBlank {
                        if (account.hasSession) "Tap ↻ to load info." else "Not logged in — use Check in Vault first."
                    },
                    fontSize = 13.sp,
                    color = if (account.infoError.isNotBlank()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
