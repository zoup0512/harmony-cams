package com.example.nvr.ui

import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.nvr.R
import com.example.nvr.model.RecordingFile
import java.io.File

@Composable
fun RecordingPreviewDialog(
    dialogFile: RecordingFile,
    onDismiss: () -> Unit,
) {
    val extension = dialogFile.filePath.substringAfterLast('.', "").lowercase()
    val isImageFile = extension in setOf("png", "jpg", "jpeg", "webp")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
        title = { Text(dialogFile.fileName) },
        text = {
            if (isImageFile) {
                // 使用 Coil 异步加载，避免在主线程 decodeFile 导致 OOM/ANR
                AsyncImage(
                    model = File(dialogFile.filePath),
                    contentDescription = dialogFile.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(dialogFile.filePath)
                            setOnPreparedListener { it.start() }
                        }
                    },
                )
            }
        },
    )
}

@Composable
fun DeleteSnapshotConfirmDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirmDelete) {
                Text(stringResource(R.string.dialog_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        title = { Text(stringResource(R.string.dialog_delete_snapshot_title)) },
        text = { Text(fileName) },
    )
}
