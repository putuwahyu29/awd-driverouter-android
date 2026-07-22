package com.awd.driverouter.ui.screens.strategy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.awd.driverouter.R
import com.awd.driverouter.data.local.AllocationStrategy
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.ui.screens.accounts.BrandIcon
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyScreen(
    onBack: () -> Unit,
    viewModel: StrategyViewModel = hiltViewModel()
) {
    val strategy by viewModel.strategy.collectAsState()
    val accounts by viewModel.orderedAccounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_strategy), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.strategy_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        text = stringResource(R.string.strategy_mode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(AllocationStrategy.entries) { s ->
                    val strategyTitle = when(s) {
                        AllocationStrategy.ROUND_ROBIN -> stringResource(R.string.strategy_round_robin)
                        AllocationStrategy.WEIGHTED_ROUND_ROBIN -> stringResource(R.string.strategy_weighted)
                        AllocationStrategy.MOST_FREE -> stringResource(R.string.strategy_most_free)
                        AllocationStrategy.LEAST_USED -> stringResource(R.string.strategy_least_used)
                        AllocationStrategy.CUSTOM_ORDER -> stringResource(R.string.strategy_custom)
                        AllocationStrategy.MIRROR -> stringResource(R.string.strategy_mirror)
                        AllocationStrategy.MANUAL -> stringResource(R.string.strategy_manual)
                    }
                    
                    val strategyDesc = when(s) {
                        AllocationStrategy.ROUND_ROBIN -> stringResource(R.string.strategy_desc_round_robin)
                        AllocationStrategy.WEIGHTED_ROUND_ROBIN -> stringResource(R.string.strategy_desc_weighted)
                        AllocationStrategy.MOST_FREE -> stringResource(R.string.strategy_desc_most_free)
                        AllocationStrategy.LEAST_USED -> stringResource(R.string.strategy_desc_least_used)
                        AllocationStrategy.CUSTOM_ORDER -> stringResource(R.string.strategy_desc_custom)
                        AllocationStrategy.MIRROR -> stringResource(R.string.strategy_desc_mirror)
                        AllocationStrategy.MANUAL -> stringResource(R.string.strategy_desc_manual)
                    }

                    Card(
                        onClick = { viewModel.setStrategy(s) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (strategy == s) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = strategy == s, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(text = strategyTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = strategyDesc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (strategy == AllocationStrategy.CUSTOM_ORDER || strategy == AllocationStrategy.MANUAL) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.strategy_priority),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    itemsIndexed(accounts) { index, account ->
                        AccountOrderItem(
                            account = account,
                            isFirst = index == 0,
                            isLast = index == accounts.size - 1,
                            onMoveUp = {
                                val newList = accounts.toMutableList()
                                Collections.swap(newList, index, index - 1)
                                viewModel.updateOrder(newList)
                            },
                            onMoveDown = {
                                val newList = accounts.toMutableList()
                                Collections.swap(newList, index, index + 1)
                                viewModel.updateOrder(newList)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccountOrderItem(
    account: CloudAccount,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandIcon(providerId = account.provider.substringBefore("_"), size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = account.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(Icons.Default.ArrowUpward, null, tint = if (isFirst) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(Icons.Default.ArrowDownward, null, tint = if (isLast) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
            }
        }
    }
}
