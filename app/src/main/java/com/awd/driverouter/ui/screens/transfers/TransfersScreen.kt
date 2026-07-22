package com.awd.driverouter.ui.screens.transfers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.awd.driverouter.R
import com.awd.driverouter.domain.model.Transfer
import com.awd.driverouter.domain.model.TransferStatus
import com.awd.driverouter.domain.model.TransferType
import com.awd.driverouter.util.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val transfers by viewModel.transfers.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    
    val activeTransfers = transfers.filter { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PENDING }
    val completedTransfers = transfers.filter { it.status == TransferStatus.COMPLETED }
    val failedTransfers = transfers.filter { it.status == TransferStatus.FAILED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfers_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_history))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(R.string.clear_history)) },
                text = { Text(stringResource(R.string.clear_history_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearHistory()
                            showClearConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        if (transfers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SyncAlt, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_transfers), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTransfers.isNotEmpty()) {
                    item { TransferHeader(stringResource(R.string.header_active, activeTransfers.size)) }
                    items(activeTransfers, key = { it.id }) { transfer ->
                        TransferItem(transfer = transfer, onCancel = { viewModel.cancelTransfer(transfer.id) })
                    }
                }

                if (failedTransfers.isNotEmpty()) {
                    item { TransferHeader(stringResource(R.string.header_failed, failedTransfers.size)) }
                    items(failedTransfers, key = { it.id }) { transfer ->
                        TransferItem(transfer = transfer, onCancel = { viewModel.cancelTransfer(transfer.id) })
                    }
                }

                if (completedTransfers.isNotEmpty()) {
                    item { TransferHeader(stringResource(R.string.header_completed, completedTransfers.size)) }
                    items(completedTransfers, key = { it.id }) { transfer ->
                        TransferItem(transfer = transfer, onCancel = { viewModel.cancelTransfer(transfer.id) })
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun TransferHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
fun TransferItem(
    transfer: Transfer,
    onCancel: () -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = (if (transfer.type == TransferType.DOWNLOAD) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer).copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (transfer.type == TransferType.DOWNLOAD) Icons.Default.Download else Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (transfer.type == TransferType.DOWNLOAD) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName, 
                        style = MaterialTheme.typography.bodyLarge, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    TransferProviderBadge(transfer)
                }
                
                if (transfer.status == TransferStatus.RUNNING || transfer.status == TransferStatus.PENDING) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                } else if (transfer.status == TransferStatus.FAILED) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                } else if (transfer.status == TransferStatus.COMPLETED) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { transfer.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = when (transfer.status) {
                    TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = when(transfer.status) {
                            TransferStatus.PENDING -> stringResource(R.string.status_pending)
                            TransferStatus.RUNNING -> stringResource(R.string.status_running)
                            TransferStatus.COMPLETED -> stringResource(R.string.status_completed)
                            TransferStatus.FAILED -> stringResource(R.string.status_failed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (transfer.status) {
                            TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                            TransferStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (transfer.status == TransferStatus.RUNNING) {
                        val currentSize = (transfer.progress * transfer.totalSize).toLong()
                        Text(
                            text = "${formatSize(currentSize)} / ${formatSize(transfer.totalSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            text = formatSize(transfer.totalSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Text(
                    text = "${(transfer.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun TransferProviderBadge(transfer: Transfer) {
    val providerId = transfer.provider.lowercase()
    val logoRes = when {
        providerId.contains("google", true) -> R.drawable.ic_google_drive
        providerId.contains("onedrive", true) -> R.drawable.ic_onedrive
        providerId.contains("dropbox", true) -> R.drawable.ic_dropbox
        providerId.contains("box", true) -> R.drawable.ic_box
        else -> null
    }

    val color = when {
        providerId.contains("google", true) -> Color(0xFF4285F4)
        providerId.contains("onedrive", true) -> Color(0xFF0078D4)
        providerId.contains("dropbox", true) -> Color(0xFF0061FF)
        providerId.contains("box", true) -> Color(0xFF0061D5)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logoRes != null) {
                Icon(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(10.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = transfer.accountEmail ?: transfer.provider,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
