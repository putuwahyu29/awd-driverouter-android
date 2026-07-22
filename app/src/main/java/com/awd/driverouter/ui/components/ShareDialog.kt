package com.awd.driverouter.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.awd.driverouter.R
import com.awd.driverouter.domain.model.CloudFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    file: CloudFile,
    onDismiss: () -> Unit,
    onShare: (String, String) -> Unit,
    onSetGeneralAccess: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val link = file.shareLink ?: file.webViewLink ?: ""
    
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Viewer") }
    var showRoleMenu by remember { mutableStateOf(false) }
    var showAccessMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.share) + " \"${file.name}\"",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text(stringResource(R.string.add_collaborator)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box {
                        TextButton(onClick = { showRoleMenu = true }) {
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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { 
                        if (email.isNotEmpty()) {
                            onShare(email, selectedRole)
                            email = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = email.contains("@")
                ) {
                    Text(stringResource(R.string.send))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.general_access),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (file.isPublic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (file.isPublic) Icons.Default.Public else Icons.Default.Lock, 
                                contentDescription = null, 
                                tint = if (file.isPublic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (file.isPublic) stringResource(R.string.anyone_with_link) else stringResource(R.string.restricted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (file.isPublic) stringResource(R.string.anyone_with_link_desc) else stringResource(R.string.restricted_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Box {
                        TextButton(onClick = { showAccessMenu = true }) {
                            Text(stringResource(R.string.change_access))
                        }
                        DropdownMenu(expanded = showAccessMenu, onDismissRequest = { showAccessMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restricted)) },
                                onClick = { onSetGeneralAccess(false); showAccessMenu = false },
                                leadingIcon = { Icon(Icons.Default.Lock, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.anyone_with_link)) },
                                onClick = { onSetGeneralAccess(true); showAccessMenu = false },
                                leadingIcon = { Icon(Icons.Default.Public, null) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (link.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Link", link))
                            Toast.makeText(context, context.getString(R.string.copy_link_done), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = link,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
