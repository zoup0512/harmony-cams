package com.example.nvr.utils

import android.content.Context
import android.os.Environment
import java.io.File

class StorageManager(private val context: Context?) {

    private fun getBaseDirectoryPath(): String {
        return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            context?.getExternalFilesDir(null)?.absolutePath
        } else {
            context?.filesDir?.absolutePath
        } ?: "/sdcard/NVR"
    }

    fun getRecordingDirectoryPath(): String {
        val dir = File(getBaseDirectoryPath(), "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }

    fun getSnapshotDirectoryPath(rootPath: String? = null): String {
        val baseDir = rootPath?.takeIf { it.isNotBlank() } ?: getBaseDirectoryPath()
        val dir = File(baseDir, "snapshot")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }

    fun getAllRecordings(): List<com.example.nvr.model.RecordingFile> {
        val result = mutableListOf<com.example.nvr.model.RecordingFile>()
        val dir = File(getRecordingDirectoryPath())
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        dir.listFiles()?.filter { it.isFile }?.forEach { file ->
            result.add(
                com.example.nvr.model.RecordingFile(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                ),
            )
        }
        return result
    }

    fun getAllSnapshots(rootPath: String? = null): List<com.example.nvr.model.RecordingFile> {
        val result = mutableListOf<com.example.nvr.model.RecordingFile>()
        val dir = File(getSnapshotDirectoryPath(rootPath))
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                val cameraName = file.name.substringBeforeLast('_').ifBlank { file.nameWithoutExtension }
                result.add(
                    com.example.nvr.model.RecordingFile(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        cameraName = cameraName,
                    ),
                )
            }

        return result
    }
}
