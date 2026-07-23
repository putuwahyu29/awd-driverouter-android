package com.awd.driverouter.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awd.driverouter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun TextPreviewScreen(file: File) {
    var content by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isTooLarge by remember { mutableStateOf(false) }

    val maxPreviewSize = 1024 * 1024 // 1MB limit for preview

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                if (file.length() > maxPreviewSize) {
                    isTooLarge = true
                    isLoading = false
                    return@withContext
                }
                
                content = file.readText()
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
            .background(Color(0xFF1E1E1E)), // Dark code background
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else if (isTooLarge) {
            Text(
                text = stringResource(R.string.file_too_large, formatSize(file.length())),
                color = Color.White,
                modifier = Modifier.padding(32.dp)
            )
        } else if (error != null) {
            Text(
                text = stringResource(R.string.error_text, error ?: ""),
                color = Color.White,
                modifier = Modifier.padding(32.dp)
            )
        } else {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = content ?: "",
                        color = Color(0xFFD4D4D4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
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
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
