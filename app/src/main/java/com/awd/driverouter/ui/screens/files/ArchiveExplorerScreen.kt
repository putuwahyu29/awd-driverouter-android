package com.awd.driverouter.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.awd.driverouter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ArchiveEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long
)

@Composable
fun ArchiveExplorerScreen(file: File) {
    var entries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val zipFile = ZipFile(file)
                val list = zipFile.entries().asSequence().map { entry ->
                    ArchiveEntry(
                        name = entry.name,
                        isDirectory = entry.isDirectory,
                        size = entry.size,
                        compressedSize = entry.compressedSize
                    )
                }.toList().sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                zipFile.close()
                entries = list
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(
                text = stringResource(R.string.error_archive, error ?: ""),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(32.dp)
            )
        } else if (entries.isEmpty()) {
            Text(stringResource(R.string.archive_empty))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.archive_entry_count, entries.size),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries) { entry ->
                        ArchiveEntryItem(entry)
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveEntryItem(entry: ArchiveEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) Color(0xFFFBC02D) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (!entry.isDirectory && entry.size > 0) {
                Row {
                    Text(
                        text = formatSize(entry.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.compressed_size, formatSize(entry.compressedSize)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
