package com.awd.driverouter.ui.screens.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.awd.driverouter.R
import com.awd.driverouter.data.local.AllocationStrategy
import com.awd.driverouter.data.local.BackupConfigEntity
import com.awd.driverouter.data.local.SyncMode
import com.awd.driverouter.ui.screens.accounts.AccountsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
    accountsViewModel: AccountsViewModel = hiltViewModel()
) {
    val backupConfigs by viewModel.backupConfigs.collectAsState()
    val accounts by accountsViewModel.accounts.collectAsState()
    val hasAccounts = accounts.isNotEmpty()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var configToEdit by remember { mutableStateOf<BackupConfigEntity?>(null) }
    var configToDelete by remember { mutableStateOf<BackupConfigEntity?>(null) }
    
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (hasAccounts) {
                            showAddDialog = true 
                        } else {
                            android.widget.Toast.makeText(context, context.getString(R.string.connect_account_first), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_account))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    if (hasAccounts) {
                        showAddDialog = true 
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.connect_account_first), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_another)) },
                containerColor = if (hasAccounts) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (hasAccounts) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
            )
        }
    ) { innerPadding ->
        if (backupConfigs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.backup_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(stringResource(R.string.start_backup))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.monitored_folders),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(backupConfigs) { config ->
                    BackupConfigItem(
                        config = config,
                        onToggle = { viewModel.toggleConfig(config, it) },
                        onEdit = { configToEdit = config },
                        onDelete = { configToDelete = config }
                    )
                }
            }
        }
    }

    if (showAddDialog || configToEdit != null) {
        AddBackupDialog(
            config = configToEdit,
            onDismiss = { 
                showAddDialog = false
                configToEdit = null
            },
            onConfirm = { uri, localName, cloudName, strategy, syncMode, wifiOnly ->
                if (configToEdit != null) {
                    viewModel.updateConfig(configToEdit!!.copy(
                        localFolderUri = uri,
                        localFolderName = localName,
                        cloudFolderName = cloudName,
                        strategy = strategy,
                        syncMode = syncMode,
                        wifiOnly = wifiOnly
                    ))
                } else {
                    viewModel.addBackupConfig(uri, localName, cloudName, strategy, syncMode, wifiOnly)
                }
                showAddDialog = false
                configToEdit = null
            }
        )
    }

    if (configToDelete != null) {
        AlertDialog(
            onDismissRequest = { configToDelete = null },
            title = { Text(stringResource(R.string.delete_config_title)) },
            text = { Text(stringResource(R.string.delete_config_msg, configToDelete?.localFolderName ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConfig(configToDelete!!)
                        configToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { configToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun BackupConfigItem(
    config: BackupConfigEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = config.localFolderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.to_cloud, config.cloudFolderName), 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = config.isEnabled, onCheckedChange = onToggle)
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${stringResource(R.string.upload_strategy)}: ${getStrategyName(config.strategy)}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (config.syncMode == SyncMode.TWO_WAY) Icons.Default.Sync else Icons.Default.ArrowUpward, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${stringResource(R.string.sync_mode)}: ${if (config.syncMode == SyncMode.TWO_WAY) stringResource(R.string.two_way_sync) else stringResource(R.string.one_way_backup)}", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (config.lastBackupTime > 0) stringResource(R.string.last_backup, formatTimestamp(config.lastBackupTime)) else stringResource(R.string.never_backed_up),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            if (config.wifiOnly) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.wifi_only_desc), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBackupDialog(
    config: BackupConfigEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, AllocationStrategy, SyncMode, Boolean) -> Unit
) {
    var selectedUri by remember { mutableStateOf(config?.localFolderUri?.let { Uri.parse(it) }) }
    var localName by remember { mutableStateOf(config?.localFolderName ?: "") }
    var cloudName by remember { mutableStateOf(config?.cloudFolderName ?: "") }
    var selectedStrategy by remember { mutableStateOf(config?.strategy ?: AllocationStrategy.MIRROR) }
    var selectedSyncMode by remember { mutableStateOf(config?.syncMode ?: SyncMode.ONE_WAY) }
    var wifiOnly by remember { mutableStateOf(config?.wifiOnly ?: true) }
    
    val context = LocalContext.current
    val defaultFolderName = stringResource(R.string.default_folder_name)
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedUri = uri
            localName = uri.path?.substringAfterLast(":") ?: defaultFolderName
            if (cloudName.isEmpty()) cloudName = localName
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (config == null) stringResource(R.string.add_backup_config) else stringResource(R.string.edit_config)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Button(
                    onClick = { pickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri == null) stringResource(R.string.select_local_folder) else stringResource(R.string.change_folder))
                }
                
                if (selectedUri != null) {
                    Text(
                        text = stringResource(R.string.path_label, selectedUri?.path ?: ""), 
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = cloudName,
                    onValueChange = { cloudName = it },
                    label = { Text(stringResource(R.string.cloud_folder_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.sync_mode), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedSyncMode == SyncMode.ONE_WAY,
                        onClick = { selectedSyncMode = SyncMode.ONE_WAY }
                    )
                    Text(stringResource(R.string.one_way_backup), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { selectedSyncMode = SyncMode.ONE_WAY })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(
                        selected = selectedSyncMode == SyncMode.TWO_WAY,
                        onClick = { selectedSyncMode = SyncMode.TWO_WAY }
                    )
                    Text(stringResource(R.string.two_way_sync), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { selectedSyncMode = SyncMode.TWO_WAY })
                }

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.cloud_allocation_strategy), style = MaterialTheme.typography.labelMedium)
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = getStrategyName(selectedStrategy),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AllocationStrategy.entries.forEach { strategy ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(getStrategyName(strategy))
                                        Text(getStrategyDesc(strategy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                onClick = {
                                    selectedStrategy = strategy
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                    Text(stringResource(R.string.wifi_only_desc), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    selectedUri?.let { onConfirm(it.toString(), localName, cloudName, selectedStrategy, selectedSyncMode, wifiOnly) }
                },
                enabled = selectedUri != null && cloudName.isNotEmpty()
            ) {
                Text(if (config == null) stringResource(R.string.save) else stringResource(R.string.update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun getStrategyName(strategy: AllocationStrategy): String = when(strategy) {
    AllocationStrategy.ROUND_ROBIN -> stringResource(R.string.strategy_round_robin)
    AllocationStrategy.WEIGHTED_ROUND_ROBIN -> stringResource(R.string.strategy_weighted)
    AllocationStrategy.MOST_FREE -> stringResource(R.string.strategy_most_free)
    AllocationStrategy.LEAST_USED -> stringResource(R.string.strategy_least_used)
    AllocationStrategy.CUSTOM_ORDER -> stringResource(R.string.strategy_custom)
    AllocationStrategy.MIRROR -> stringResource(R.string.strategy_mirror)
    AllocationStrategy.MANUAL -> stringResource(R.string.strategy_manual)
}

@Composable
fun getStrategyDesc(strategy: AllocationStrategy): String = when(strategy) {
    AllocationStrategy.ROUND_ROBIN -> stringResource(R.string.strategy_desc_round_robin)
    AllocationStrategy.WEIGHTED_ROUND_ROBIN -> stringResource(R.string.strategy_desc_weighted)
    AllocationStrategy.MOST_FREE -> stringResource(R.string.strategy_desc_most_free)
    AllocationStrategy.LEAST_USED -> stringResource(R.string.strategy_desc_least_used)
    AllocationStrategy.CUSTOM_ORDER -> stringResource(R.string.strategy_desc_custom)
    AllocationStrategy.MIRROR -> stringResource(R.string.strategy_desc_mirror)
    AllocationStrategy.MANUAL -> stringResource(R.string.strategy_desc_manual)
}

fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}
