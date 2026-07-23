package com.awd.driverouter.ui.screens.files

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.awd.driverouter.R
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun AudioPreviewScreen(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration
                }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = e.message
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Audio Icon / Artwork
            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // File Info
            Text(
                text = file.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            Text(
                text = stringResource(R.string.unknown_artist),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (error != null) {
                Text(
                    text = stringResource(R.string.error_audio, error ?: ""),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                // Seekbar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { 
                            if (duration > 0) {
                                exoPlayer.seekTo((it * duration).toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controls
                IconButton(
                    onClick = { 
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
