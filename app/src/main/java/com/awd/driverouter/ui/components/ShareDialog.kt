package com.awd.driverouter.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.awd.driverouter.R
import com.awd.driverouter.domain.model.CloudFile
import com.awd.driverouter.domain.model.SharePermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    file: CloudFile,
    permissions: List<SharePermission>,
    isLoadingPermissions: Boolean,
    onDismiss: () -> Unit,
    onShare: (String, String) -> Unit,
    onSetGeneralAccess: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val link = file.shareLink ?: file.webViewLink ?: ""
    
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Viewer") }
    var showRoleMenu by remember { mutableStateOf(false) }
    var showAccessMenu by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    // Reset processing when file state changes or permissions update
    LaunchedEffect(file.isPublic, permissions) {
        isProcessing = false
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text(stringResource(R.string.share), style = MaterialTheme.typography.titleMedium)
                            Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isProcessing) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (isProcessing || isLoadingPermissions) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp)
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                if (file.supportsMemberSharing) {
                    // Add People Section
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = { Text(stringResource(R.string.add_collaborator)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                            trailingIcon = {
                                if (email.contains("@")) {
                                    IconButton(onClick = { 
                                        isProcessing = true
                                        onShare(email, selectedRole)
                                        email = ""
                                    }, enabled = !isProcessing) {
                                        Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            singleLine = true,
                            enabled = !isProcessing
                        )
                        
                        if (email.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.permission_role) + ": ", style = MaterialTheme.typography.bodySmall)
                                Box {
                                    TextButton(onClick = { showRoleMenu = true }, enabled = !isProcessing) {
                                        Text(if (selectedRole == "Viewer") stringResource(R.string.permission_viewer) else stringResource(R.string.permission_editor))
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                    DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.permission_viewer)) },
                                            onClick = { selectedRole = "Viewer"; showRoleMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.permission_editor)) },
                                            onClick = { selectedRole = "Editor"; showRoleMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.who_has_access),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // List of People
                    if (isLoadingPermissions && permissions.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    } else {
                        items(permissions) { permission ->
                            PermissionItem(permission)
                        }
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.share_link_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // General Access Section
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.general_access),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box {
                        GeneralAccessItem(
                            isPublic = file.isPublic,
                            isProcessing = isProcessing,
                            onChange = { 
                                showAccessMenu = true
                            }
                        )

                        DropdownMenu(
                            expanded = showAccessMenu, 
                            onDismissRequest = { showAccessMenu = false },
                            modifier = Modifier.widthIn(min = 200.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restricted)) },
                                onClick = { 
                                    isProcessing = true
                                    onSetGeneralAccess(false)
                                    showAccessMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                trailingIcon = { if (!file.isPublic) Icon(Icons.Default.Check, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.anyone_with_link)) },
                                onClick = { 
                                    isProcessing = true
                                    onSetGeneralAccess(true)
                                    showAccessMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Public, null) },
                                trailingIcon = { if (file.isPublic) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (link.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Link", link))
                                Toast.makeText(context, context.getString(R.string.copy_link_done), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.copy_link))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun PermissionItem(permission: SharePermission) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (permission.photoLink != null) {
            AsyncImage(
                model = permission.photoLink,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.displayName ?: permission.email ?: stringResource(R.string.unknown_user),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (permission.email != null && permission.displayName != null) {
                Text(
                    text = permission.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Text(
            text = permission.role.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GeneralAccessItem(
    isPublic: Boolean,
    isProcessing: Boolean,
    onChange: () -> Unit
) {
    Surface(
        onClick = onChange,
        enabled = !isProcessing,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPublic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock, 
                        contentDescription = null, 
                        tint = if (isPublic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPublic) stringResource(R.string.anyone_with_link) else stringResource(R.string.restricted),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isPublic) stringResource(R.string.anyone_with_link_desc) else stringResource(R.string.restricted_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

