package com.awd.driverouter.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object FileIconHelper {
    fun getIconForMimeType(mimeType: String?, isFolder: Boolean): ImageVector {
        if (isFolder) return Icons.Default.Folder
        val type = mimeType?.lowercase() ?: ""
        return when {
            type.startsWith("image/") -> Icons.Default.Image
            type.startsWith("video/") -> Icons.Default.VideoFile
            type.startsWith("audio/") -> Icons.Default.AudioFile
            type == "application/pdf" -> Icons.Default.PictureAsPdf
            type.contains("zip") || type.contains("compressed") || type.contains("rar") || type.contains("archive") || type.contains("7z") -> Icons.Default.FolderZip
            type.contains("word") || type.contains("officedocument.wordprocessingml") || type.contains("msword") || type.contains("vnd.google-apps.document") -> Icons.Default.Description
            type.contains("excel") || type.contains("officedocument.spreadsheetml") || type.contains("sheet") || type.contains("vnd.google-apps.spreadsheet") || type.contains("csv") -> Icons.Default.TableChart
            type.contains("powerpoint") || type.contains("officedocument.presentationml") || type.contains("presentation") || type.contains("vnd.google-apps.presentation") -> Icons.Default.Slideshow
            type.startsWith("text/") || type.contains("plain") || type.contains("javascript") || type.contains("json") || type.contains("html") || type.contains("xml") -> Icons.AutoMirrored.Filled.Article
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }

    fun getColorForMimeType(mimeType: String?, isFolder: Boolean): Color {
        if (isFolder) return Color(0xFFFBC02D) // Folder Amber
        val type = mimeType?.lowercase() ?: ""
        return when {
            type.startsWith("image/") -> Color(0xFF4CAF50) // Green
            type.startsWith("video/") -> Color(0xFFFF5722) // Deep Orange
            type.startsWith("audio/") -> Color(0xFF9C27B0) // Purple
            type == "application/pdf" -> Color(0xFFF44336) // Red
            type.contains("word") || type.contains("msword") || type.contains("document") -> Color(0xFF2196F3) // Blue
            type.contains("excel") || type.contains("sheet") || type.contains("spreadsheet") || type.contains("csv") -> Color(0xFF2E7D32) // Dark Green
            type.contains("powerpoint") || type.contains("presentation") -> Color(0xFFFF9800) // Orange
            type.contains("zip") || type.contains("rar") || type.contains("archive") || type.contains("7z") -> Color(0xFF607D8B) // Blue Grey
            type.startsWith("text/") || type.contains("plain") || type.contains("json") || type.contains("xml") -> Color(0xFF00BCD4) // Cyan
            else -> Color(0xFF616161) // Darker Grey to avoid "blank" look
        }
    }
}
