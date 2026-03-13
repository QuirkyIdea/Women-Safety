package com.example.suraksha.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecordingsScreen — Lists both audio (.m4a) and video (.mp4) evidence
 * recordings made by SafetyService and VideoRecordingService.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen() {
    val context = LocalContext.current
    var recordings by remember {
        mutableStateOf(
            listRecordingFiles(
                context.cacheDir,
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No recordings yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(recordings) { file ->
                    val isVideo = file.name.endsWith(".mp4")
                    RecordingItem(
                        file = file,
                        isVideo = isVideo,
                        onShare = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".provider",
                                file
                            )
                            val mimeType = if (isVideo) "video/*" else "audio/*"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share recording"))
                        },
                        onDelete = {
                            if (file.delete()) {
                                recordings = listRecordingFiles(
                                    context.cacheDir,
                                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingItem(
    file: File,
    isVideo: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val dateText = remember(file) { sdf.format(Date(file.lastModified())) }
    val sizeKb = remember(file) { (file.length() / 1024).coerceAtLeast(1) }

    ListItem(
        leadingContent = {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Mic,
                contentDescription = if (isVideo) "Video" else "Audio",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                "$dateText  •  ${sizeKb}KB  •  ${if (isVideo) "Video" else "Audio"}",
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider()
}

/**
 * Scans cacheDir, Music, and Movies directories for evidence files.
 * Audio: emergency_recording_*.m4a
 * Video: evidence_video_*.mp4
 */
private fun listRecordingFiles(
    internalDir: File,
    externalMusic: File?,
    externalMovies: File?
): List<File> {
    val out = mutableListOf<File>()
    listOfNotNull(externalMusic, externalMovies, internalDir).forEach { dir ->
        dir.listFiles()?.filter { file ->
            file.isFile && (
                (file.name.startsWith("emergency_recording_") && file.name.endsWith(".m4a")) ||
                (file.name.startsWith("evidence_video_") && file.name.endsWith(".mp4"))
            )
        }?.let { out.addAll(it) }
    }
    return out.sortedByDescending { it.lastModified() }
}
