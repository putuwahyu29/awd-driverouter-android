package com.awd.driverouter.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.awd.driverouter.R
import com.awd.driverouter.ui.screens.about.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState.NewVersionAvailable,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available)) },
        text = { 
            Text(stringResource(R.string.update_message, state.release.tag_name, state.release.body)) 
        },
        confirmButton = {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.release.html_url))
                context.startActivity(intent)
                onDismiss()
            }) {
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}
