package com.awd.driverouter.ui.screens.accounts

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.awd.driverouter.R
import com.awd.driverouter.data.local.CredentialManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.util.formatSize
import com.google.android.gms.auth.api.signin.GoogleSignIn

import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val providerValidation by viewModel.providerValidation.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProviderForConfig by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    
    var accountToRemove by remember { mutableStateOf<CloudAccount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.statusMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.onGoogleSignInResult(account)
            } catch (e: ApiException) {
                viewModel.onGoogleSignInResult(null, errorCode = e.statusCode)
            } catch (e: Exception) {
                viewModel.onGoogleSignInResult(null, errorMsg = e.message)
            }
        } else {
            viewModel.onGoogleSignInResult(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_accounts), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        Toast.makeText(context, context.getString(R.string.setup_guide), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.HelpCenter, contentDescription = stringResource(R.string.help))
                    }
                }
            )
        }

    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                ProviderSection(
                    providerId = "google",
                    name = stringResource(R.string.google_drive),
                    accounts = accounts.filter { it.provider == "google_drive" },
                    isEnabled = providerValidation["google"] ?: false,
                    onAddClick = { 
                        viewModel.startGoogleSignIn { intent ->
                            googleSignInLauncher.launch(intent)
                        }
                    },
                    onRemoveAccount = { accountToRemove = it },
                    onConfigClick = { selectedProviderForConfig = "google"; showSheet = true }
                )

                ProviderSection(
                    providerId = "onedrive",
                    name = stringResource(R.string.onedrive),
                    accounts = accounts.filter { it.provider == "onedrive" },
                    isEnabled = providerValidation["onedrive"] ?: false,
                    onAddClick = { context.findActivity()?.let { viewModel.oneDriveSignIn(it) } },
                    onRemoveAccount = { accountToRemove = it },
                    onConfigClick = { selectedProviderForConfig = "onedrive"; showSheet = true }
                )

                ProviderSection(
                    providerId = "dropbox",
                    name = stringResource(R.string.dropbox),
                    accounts = accounts.filter { it.provider == "dropbox" },
                    isEnabled = providerValidation["dropbox"] ?: false,
                    onAddClick = { context.findActivity()?.let { viewModel.dropboxSignIn(it) } },
                    onRemoveAccount = { accountToRemove = it },
                    onConfigClick = { selectedProviderForConfig = "dropbox"; showSheet = true }
                )

                ProviderSection(
                    providerId = "box",
                    name = stringResource(R.string.box),
                    accounts = accounts.filter { it.provider == "box" },
                    isEnabled = providerValidation["box"] ?: false,
                    onAddClick = { context.findActivity()?.let { viewModel.boxSignIn(it) } },
                    onRemoveAccount = { accountToRemove = it },
                    onConfigClick = { selectedProviderForConfig = "box"; showSheet = true }
                )

                ProviderSection(
                    providerId = "webdav",
                    name = stringResource(R.string.webdav),
                    accounts = accounts.filter { it.provider == "webdav" },
                    isEnabled = true, // WebDAV directly prompts for login
                    onAddClick = { selectedProviderForConfig = "webdav"; showSheet = true },
                    onRemoveAccount = { accountToRemove = it },
                    onConfigClick = { selectedProviderForConfig = "webdav"; showSheet = true }
                )

                ProviderSection(
                    providerId = "sftp",
                    name = stringResource(R.string.sftp),
                    accounts = accounts.filter { it.provider == "sftp" },
                    isEnabled = true, // SFTP directly prompts for login
                    onAddClick = { selectedProviderForConfig = "sftp"; showSheet = true },
                    onRemoveAccount = { accountToRemove = it },
                    onConfigClick = { selectedProviderForConfig = "sftp"; showSheet = true }
                )

                
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (isLoggingIn) {
                Dialog(onDismissRequest = { }) {
                    Card(shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.authenticating))
                        }
                    }
                }
            }
        }
    }

    if (accountToRemove != null) {
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text(stringResource(R.string.remove_account)) },
            text = { Text(stringResource(R.string.confirm_remove_account)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeAccount(accountToRemove!!)
                    accountToRemove = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSheet && selectedProviderForConfig != null) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            ConfigSheetContent(selectedProviderForConfig!!, { showSheet = false }, viewModel)
        }
    }
}

@Composable
fun ProviderSection(
    providerId: String,
    name: String,
    accounts: List<CloudAccount>,
    isEnabled: Boolean = true,
    onAddClick: () -> Unit,
    onRemoveAccount: (CloudAccount) -> Unit,
    onConfigClick: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandIcon(providerId, size = 40.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                
                // Only show settings icon for providers that need pre-configuration (Google, OneDrive, etc.)
                if (providerId != "webdav" && providerId != "sftp") {
                    IconButton(onClick = onConfigClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            
            if (accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                accounts.forEach { account ->
                    AccountStorageItem(account, onRemoveAccount, onSetMain = { viewModel.setMainAccount(account.id) })
                    if (account != accounts.last()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddClick,
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer, 
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (accounts.isEmpty()) stringResource(R.string.add_account) else stringResource(R.string.add_another))
            }
            if (!isEnabled) {
                Text(
                    text = stringResource(R.string.setup_guide), // Actually should be a more specific string
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun AccountStorageItem(account: CloudAccount, onRemove: (CloudAccount) -> Unit, onSetMain: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.name, 
                            style = MaterialTheme.typography.titleSmall, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (account.isMainAccount) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = stringResource(R.string.main_account_badge),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    Text(
                        text = account.email ?: "", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                if (!account.isMainAccount) {
                    TextButton(onClick = onSetMain) {
                        Text(stringResource(R.string.set_main), style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(
                    onClick = { onRemove(account) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircle, 
                        contentDescription = stringResource(R.string.remove), 
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { account.usedPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                color = if (account.usedPercentage > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.storage_used, formatSize(account.usedSpace), formatSize(account.totalSpace)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(account.usedPercentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Composable
fun BrandIcon(providerId: String, size: androidx.compose.ui.unit.Dp) {
    val logoRes = when (providerId) {
        "google" -> R.drawable.ic_google_drive
        "onedrive" -> R.drawable.ic_onedrive
        "dropbox" -> R.drawable.ic_dropbox
        "box" -> R.drawable.ic_box
        else -> null
    }
    
    val color = when (providerId) {
        "google" -> Color(0xFF4285F4)
        "onedrive" -> Color(0xFF0078D4)
        "dropbox" -> Color(0xFF0061FF)
        "box" -> Color(0xFF0061D5)
        "mega" -> Color(0xFFD92121)
        else -> MaterialTheme.colorScheme.primary
    }
    
    Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (logoRes != null) {
                Icon(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(size * 0.6f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    }
}


@Composable
fun ConfigSheetContent(provider: String, onDismiss: () -> Unit, viewModel: AccountsViewModel) {

    var field1 by remember { mutableStateOf("") }
    var field2 by remember { mutableStateOf("") }
    var field3 by remember { mutableStateOf("") }
    var field4 by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    val label1 = when (provider) { 
        "google" -> stringResource(R.string.client_id_web_label)
        "onedrive" -> stringResource(R.string.client_id_app_label)
        "dropbox" -> stringResource(R.string.app_key_label)
        "box" -> stringResource(R.string.client_id_label)
        "webdav" -> stringResource(R.string.server_url_label)
        "sftp" -> stringResource(R.string.host_address_label)
        else -> "" 
    }
    val label2 = when (provider) { 
        "box" -> stringResource(R.string.client_secret_label)
        "onedrive" -> stringResource(R.string.redirect_uri_msauth_label)
        "webdav" -> stringResource(R.string.username_label)
        "sftp" -> stringResource(R.string.username_label)
        else -> "" 
    }
    
    val field3Label = when (provider) {
        "box" -> stringResource(R.string.redirect_uri_label)
        "webdav" -> stringResource(R.string.password_label)
        "sftp" -> stringResource(R.string.password_label)
        else -> ""
    }

    val field4Label = if (provider == "sftp") stringResource(R.string.port_label) else ""

    val key1 = when (provider) { 
        "google" -> CredentialManager.GOOGLE_CLIENT_ID 
        "onedrive" -> CredentialManager.ONEDRIVE_CLIENT_ID 
        "dropbox" -> CredentialManager.DROPBOX_APP_KEY 
        "box" -> CredentialManager.BOX_CLIENT_ID 
        "webdav" -> CredentialManager.WEBDAV_URL
        "sftp" -> CredentialManager.SFTP_HOST
        else -> "" 
    }
    val key2 = when (provider) {
        "box" -> CredentialManager.BOX_CLIENT_SECRET 
        "onedrive" -> CredentialManager.ONEDRIVE_REDIRECT_URI 
        "webdav" -> CredentialManager.WEBDAV_USER
        "sftp" -> CredentialManager.SFTP_USER
        else -> ""
    }
    val key3 = when (provider) {
        "box" -> CredentialManager.BOX_REDIRECT_URI 
        "webdav" -> CredentialManager.WEBDAV_PASS
        "sftp" -> CredentialManager.SFTP_PASS
        else -> ""
    }
    val key4 = if (provider == "sftp") CredentialManager.SFTP_PORT else ""

    LaunchedEffect(provider) {
        if (key1.isNotEmpty()) field1 = viewModel.getCredential(key1)
        if (key2.isNotEmpty()) field2 = viewModel.getCredential(key2)
        if (key3.isNotEmpty()) field3 = viewModel.getCredential(key3)
        if (key4.isNotEmpty()) field4 = viewModel.getCredential(key4)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        val providerName = when (provider) {
            "google" -> stringResource(R.string.google_drive)
            "onedrive" -> stringResource(R.string.onedrive)
            "dropbox" -> stringResource(R.string.dropbox)
            "box" -> stringResource(R.string.box)
            "webdav" -> stringResource(R.string.webdav)
            "sftp" -> stringResource(R.string.sftp)
            else -> provider.replaceFirstChar { it.uppercase() }
        }
        
        val title = if (provider == "webdav" || provider == "sftp") stringResource(R.string.connect_account_title, providerName) else stringResource(R.string.configure_provider, providerName)
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (provider == "google" || provider == "onedrive") {
            AppIdentitySection(
                packageName = viewModel.getPackageName(), 
                sha1 = viewModel.getAppSHA1(), 
                sha1Base64 = viewModel.getAppSHA1Base64(),
                msalHash = viewModel.getMSALSignatureHash()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        ConfigGuideSection(provider)
        Spacer(modifier = Modifier.height(16.dp))

        if (provider == "webdav" || provider == "sftp") {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.account_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.account_name_placeholder)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (label1.isNotEmpty()) {
            OutlinedTextField(
                value = field1, 
                onValueChange = { field1 = it }, 
                label = { Text(label1) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )
        }
        if (label2.isNotEmpty()) { 
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = field2, 
                onValueChange = { field2 = it }, 
                label = { Text(label2) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            ) 
        }
        if (field3Label.isNotEmpty()) { 
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = field3, 
                onValueChange = { field3 = it }, 
                label = { Text(field3Label) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = MaterialTheme.shapes.medium,
                singleLine = provider != "webdav" && provider != "sftp",
                visualTransformation = if (provider == "webdav" || provider == "sftp") androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
            ) 
        }
        if (field4Label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = field4, 
                onValueChange = { field4 = it }, 
                label = { Text(field4Label) }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { 
                if (provider == "webdav") {
                    viewModel.webDavSignIn(field1, field2, field3, displayName)
                } else if (provider == "sftp") {
                    viewModel.sftpSignIn(field1, field4, field2, field3, displayName)
                } else {
                    if (key1.isNotEmpty()) viewModel.saveCredential(key1, field1)
                    if (key2.isNotEmpty()) viewModel.saveCredential(key2, field2)
                    if (key3.isNotEmpty()) viewModel.saveCredential(key3, field3)
                    if (key4.isNotEmpty()) viewModel.saveCredential(key4, field4)
                }
                
                onDismiss() 
            }, 
            modifier = Modifier.fillMaxWidth(), 
            shape = MaterialTheme.shapes.large
        ) { 
            val buttonText = if (provider == "webdav" || provider == "sftp") stringResource(R.string.connect_account) else stringResource(R.string.save_config)
            Text(buttonText) 
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { 
            Text(stringResource(R.string.cancel)) 
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ConfigGuideSection(provider: String) {
    var isExpanded by remember { mutableStateOf(true) }
    ElevatedCard(onClick = { isExpanded = !isExpanded }, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(R.string.setup_guide), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = when (provider) {
                    "google" -> stringResource(R.string.google_guide)
                    "onedrive" -> stringResource(R.string.onedrive_guide)
                    "dropbox" -> stringResource(R.string.dropbox_guide)
                    "box" -> stringResource(R.string.box_guide)
                    "webdav" -> stringResource(R.string.webdav_guide)
                    "sftp" -> stringResource(R.string.sftp_guide)
                    else -> ""
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun AppIdentitySection(packageName: String, sha1: String, sha1Base64: String, msalHash: String) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.registration_info), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            IdentityItem(stringResource(R.string.package_name), packageName, context)
            Spacer(modifier = Modifier.height(8.dp))
            IdentityItem(stringResource(R.string.sha1_fingerprint), sha1, context)
            Spacer(modifier = Modifier.height(8.dp))
            IdentityItem(stringResource(R.string.sha1_base64_fingerprint), sha1Base64, context)
            Spacer(modifier = Modifier.height(8.dp))
            IdentityItem(stringResource(R.string.msal_signature_hash), msalHash, context)
        }
    }
}

@Composable
fun IdentityItem(label: String, value: String, context: Context) {
    val scope = rememberCoroutineScope()
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { 
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard, label), Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) { if (context is Activity) return context; context = context.baseContext }
    return null
}
