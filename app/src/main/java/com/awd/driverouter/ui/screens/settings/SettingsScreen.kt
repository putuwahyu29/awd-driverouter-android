package com.awd.driverouter.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.awd.driverouter.BuildConfig
import com.awd.driverouter.R
import com.awd.driverouter.data.local.AppLanguage
import com.awd.driverouter.data.local.AppTheme
import com.awd.driverouter.ui.screens.about.UpdateUiState
import com.awd.driverouter.ui.screens.about.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val theme by viewModel.theme.collectAsState()
    val language by viewModel.language.collectAsState()
    val appLockEnabled by viewModel.isAppLockEnabled.collectAsState()
    val downloadLocationName by viewModel.downloadLocationName.collectAsState()

    val updateUiState by updateViewModel.uiState.collectAsState()
    val currentVersion = BuildConfig.VERSION_NAME

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val folderName = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
            viewModel.setDownloadLocation(uri, folderName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { resId ->
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(stringResource(R.string.personalization))
            
            SettingsItem(
                title = stringResource(R.string.theme),
                icon = Icons.Default.Palette,
                summary = when(theme) {
                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                    AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                }
            ) { showThemeDialog = true }

            SettingsItem(
                title = stringResource(R.string.language),
                icon = Icons.Default.Language,
                summary = if (language == AppLanguage.ENGLISH) stringResource(R.string.language_en) else stringResource(R.string.language_id)
            ) { showLanguageDialog = true }

            SettingsItem(
                title = stringResource(R.string.download_location),
                icon = Icons.Default.FolderOpen,
                summary = downloadLocationName ?: stringResource(R.string.default_download_location)
            ) {
                folderPickerLauncher.launch(null)
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(stringResource(R.string.security))
            SwitchSettingsItem(
                title = stringResource(R.string.app_lock),
                icon = Icons.Default.Lock,
                checked = appLockEnabled,
                onCheckedChange = { viewModel.setAppLockEnabled(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSection(stringResource(R.string.about))
            SettingsItem(
                title = stringResource(R.string.github_repo),
                icon = Icons.Default.Code,
                summary = "putuwahyu29/awd-driverouter-android"
            ) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/putuwahyu29/awd-driverouter-android"))
                context.startActivity(intent)
            }
            SettingsItem(
                title = stringResource(R.string.official_website),
                icon = Icons.Default.Public,
                summary = "driverouter.biz.id"
            ) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://driverouter.biz.id"))
                context.startActivity(intent)
            }
            SettingsItem(
                title = stringResource(R.string.license),
                icon = Icons.Default.Description,
                summary = "MIT License"
            ) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/putuwahyu29/awd-driverouter-android/blob/main/LICENSE"))
                context.startActivity(intent)
            }
            SettingsItem(
                title = stringResource(R.string.check_updates),
                icon = Icons.Default.Update,
                summary = stringResource(R.string.version, currentVersion)
            ) {
                updateViewModel.checkForUpdates(currentVersion)
            }
            
            Text(
                text = stringResource(R.string.developed_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        val options = listOf(
            AppTheme.LIGHT to stringResource(R.string.theme_light),
            AppTheme.DARK to stringResource(R.string.theme_dark),
            AppTheme.SYSTEM to stringResource(R.string.theme_system)
        )
        OptionDialog(
            title = stringResource(R.string.theme),
            options = options.map { it.second },
            selectedOption = options.find { it.first == theme }?.second ?: "",
            onOptionSelected = { label ->
                val selected = options.find { it.second == label }?.first ?: AppTheme.SYSTEM
                viewModel.setTheme(selected)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        val options = listOf(
            AppLanguage.ENGLISH to stringResource(R.string.language_en),
            AppLanguage.INDONESIAN to stringResource(R.string.language_id)
        )
        OptionDialog(
            title = stringResource(R.string.language),
            options = options.map { it.second },
            selectedOption = options.find { it.first == language }?.second ?: "",
            onOptionSelected = { label ->
                val selected = options.find { it.second == label }?.first ?: AppLanguage.ENGLISH
                viewModel.setLanguage(selected)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Update Dialogs
    when (val state = updateUiState) {
        is UpdateUiState.NewVersionAvailable -> {
            AlertDialog(
                onDismissRequest = { updateViewModel.resetState() },
                title = { Text(stringResource(R.string.update_available)) },
                text = { Text(stringResource(R.string.update_message, state.release.tag_name, state.release.body)) },
                confirmButton = {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.release.html_url))
                        context.startActivity(intent)
                        updateViewModel.resetState()
                    }) {
                        Text(stringResource(R.string.download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateViewModel.resetState() }) {
                        Text(stringResource(R.string.later))
                    }
                }
            )
        }
        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = { updateViewModel.resetState() },
                title = { Text(stringResource(R.string.app_up_to_date)) },
                text = { Text(stringResource(R.string.up_to_date_message, currentVersion)) },
                confirmButton = {
                    Button(onClick = { updateViewModel.resetState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = { updateViewModel.resetState() },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { updateViewModel.resetState() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is UpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = { /* Cannot dismiss while checking */ },
                title = { Text(stringResource(R.string.check_updates)) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.processing))
                    }
                },
                confirmButton = {}
            )
        }
        else -> {}
    }
}

@Composable
fun SwitchSettingsItem(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun OptionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOptionSelected(option) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selectedOption, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

