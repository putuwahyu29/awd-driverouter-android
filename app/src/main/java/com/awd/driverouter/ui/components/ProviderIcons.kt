package com.awd.driverouter.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.awd.driverouter.R

@Composable
fun BrandIcon(providerId: String, size: Dp) {
    val cleanId = providerId.lowercase().replace("_drive", "")
    val logoRes = when (cleanId) {
        "google" -> R.drawable.ic_google_drive
        "onedrive" -> R.drawable.ic_onedrive
        "dropbox" -> R.drawable.ic_dropbox
        "box" -> R.drawable.ic_box
        else -> null
    }
    
    val color = when (cleanId) {
        "google" -> Color(0xFF4285F4)
        "onedrive" -> Color(0xFF0078D4)
        "dropbox" -> Color(0xFF0061FF)
        "box" -> Color(0xFF0061D5)
        else -> MaterialTheme.colorScheme.primary
    }
    
    Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (logoRes != null) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.7f),
                    contentScale = ContentScale.Fit
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
