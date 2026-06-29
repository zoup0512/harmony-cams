package com.example.nvr

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.example.nvr.model.CameraDevice
import com.example.nvr.ui.theme.applyAppearanceThemeMode
import com.example.nvr.utils.DatabaseHelper
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

class NVRApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        applyAppearanceThemeMode(this)

        addDefaultCameraData()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "未捕获的异常: ${throwable.message}")

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()
            Log.e(TAG, "堆栈跟踪:\n$stackTrace")

            if (throwable is NullPointerException && stackTrace.contains("CameraDevice$1")) {
                Log.e(TAG, "检测到CameraDevice$1空指针异常，可能与Camera2 API相关")
                throwable.stackTrace.forEach { element ->
                    if (element.className?.contains("CameraDevice$1") == true) {
                        Log.e(TAG, "异常发生在: ${element.className}:${element.methodName} 行: ${element.lineNumber}")
                    }
                }
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun addDefaultCameraData() {
        try {
            val dbHelper = DatabaseHelper(this)
            if (dbHelper.getCamerasCount() == 0) {
                val defaultCamera = CameraDevice(
                    id = UUID.randomUUID().toString(),
                    name = "默认摄像头",
                    rtspUrl = "",
                )

                val isSuccess = dbHelper.addCamera(defaultCamera)
                if (isSuccess) {
                    Log.d(TAG, "默认摄像头数据添加成功")
                } else {
                    Log.e(TAG, "默认摄像头数据添加失败")
                }
            } else {
                Log.d(TAG, "数据库中已存在摄像头数据，跳过添加默认数据")
            }
            dbHelper.close()
        } catch (e: Exception) {
            Log.e(TAG, "添加默认摄像头数据时发生异常: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "NVRApplication"
    }
}
