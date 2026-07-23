package com.awd.driverouter.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object FileIconHelper {
    fun getIconForMimeType(mimeType: String?, isFolder: Boolean, fileName: String? = null): ImageVector {
        if (isFolder) return Icons.Default.Folder
        
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        val type = mimeType?.lowercase() ?: ""
        
        return when {
            type.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") -> Icons.Default.Image
            type.startsWith("video/") || extension in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm") -> Icons.Default.VideoFile
            type.startsWith("audio/") || extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> Icons.Default.AudioFile
            type == "application/pdf" || extension == "pdf" -> Icons.Default.PictureAsPdf
            
            // Apps & Executables
            extension == "apk" -> Icons.Default.Android
            extension in listOf("exe", "msi", "bat", "sh") -> Icons.Default.Terminal
            
            // Archives
            type.contains("zip") || type.contains("compressed") || type.contains("rar") || type.contains("archive") || type.contains("7z") ||
            extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> Icons.Default.FolderZip
            
            // Documents
            type.contains("word") || type.contains("officedocument.wordprocessingml") || type.contains("msword") || 
            type.contains("vnd.google-apps.document") || extension in listOf("doc", "docx", "odt", "rtf") -> Icons.Default.Description
            
            type.contains("excel") || type.contains("officedocument.spreadsheetml") || type.contains("sheet") || 
            type.contains("vnd.google-apps.spreadsheet") || extension in listOf("xls", "xlsx", "ods", "csv") -> Icons.Default.TableChart
            
            type.contains("powerpoint") || type.contains("officedocument.presentationml") || type.contains("presentation") || 
            type.contains("vnd.google-apps.presentation") || extension in listOf("ppt", "pptx", "odp") -> Icons.Default.Slideshow
            
            // Code & Web
            type.startsWith("text/") || type.contains("javascript") || type.contains("json") || type.contains("html") || type.contains("xml") ||
            extension in listOf("txt", "js", "html", "css", "json", "xml", "kt", "java", "py", "cpp", "c", "php", "sql") -> Icons.Default.Code
            
            // Others
            extension in listOf("iso", "img", "dmg") -> Icons.Default.DiscFull
            extension in listOf("db", "sqlite", "sqlite3") -> Icons.Default.Storage
            
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }

    fun getColorForMimeType(mimeType: String?, isFolder: Boolean, fileName: String? = null): Color {
        if (isFolder) return Color(0xFFFBC02D) // Folder Amber
        
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        val type = mimeType?.lowercase() ?: ""
        
        return when {
            type.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") -> Color(0xFF4CAF50) // Green
            type.startsWith("video/") || extension in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm") -> Color(0xFFFF5722) // Deep Orange
            type.startsWith("audio/") || extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> Color(0xFF9C27B0) // Purple
            type == "application/pdf" || extension == "pdf" -> Color(0xFFF44336) // Red
            
            // Apps
            extension == "apk" -> Color(0xFF8BC34A) // Light Green (Android)
            
            // Documents
            type.contains("word") || type.contains("msword") || type.contains("vnd.google-apps.document") || 
            extension in listOf("doc", "docx") -> Color(0xFF2196F3) // Blue
            
            type.contains("excel") || type.contains("sheet") || type.contains("spreadsheet") || 
            type.contains("vnd.google-apps.spreadsheet") || extension in listOf("xls", "xlsx", "csv") -> Color(0xFF2E7D32) // Dark Green
            
            type.contains("powerpoint") || type.contains("presentation") || 
            type.contains("vnd.google-apps.presentation") || extension in listOf("ppt", "pptx") -> Color(0xFFFF9800) // Orange
            
            // Archives
            type.contains("zip") || type.contains("rar") || type.contains("archive") || extension in listOf("zip", "rar", "7z") -> Color(0xFF607D8B) // Blue Grey
            
            // Code
            type.startsWith("text/") || extension in listOf("txt", "js", "html", "kt", "py", "sql") -> Color(0xFF009688) // Teal
            
            // Others
            extension in listOf("iso", "img") -> Color(0xFF673AB7) // Deep Purple
            extension in listOf("db", "sqlite") -> Color(0xFF3F51B5) // Indigo
            
            else -> Color(0xFF757575) // Darker Grey
        }
    }
}
