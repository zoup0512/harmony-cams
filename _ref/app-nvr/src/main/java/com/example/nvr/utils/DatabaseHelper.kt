package com.example.nvr.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.nvr.model.CameraDevice

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_CAMERAS (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_NAME TEXT,
                $KEY_RTSP_URL TEXT
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CAMERAS")
        onCreate(db)
    }

    fun addCamera(camera: CameraDevice): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_ID, camera.id)
            put(KEY_NAME, camera.name)
            put(KEY_RTSP_URL, camera.rtspUrl)
        }
        val result = db.insert(TABLE_CAMERAS, null, values)
        db.close()
        return result != -1L
    }

    fun getCamera(id: String): CameraDevice? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CAMERAS,
            arrayOf(KEY_ID, KEY_NAME, KEY_RTSP_URL),
            "$KEY_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
        )

        val camera = if (cursor.moveToFirst()) {
            CameraDevice(
                id = cursor.getString(0) ?: "",
                name = cursor.getString(1) ?: "",
                rtspUrl = cursor.getString(2) ?: "",
            )
        } else {
            null
        }

        cursor.close()
        return camera
    }

    fun getAllCameras(): List<CameraDevice> {
        val result = mutableListOf<CameraDevice>()
        val selectQuery = "SELECT * FROM $TABLE_CAMERAS"
        val db = readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                result.add(
                    CameraDevice(
                        id = cursor.getString(0) ?: "",
                        name = cursor.getString(1) ?: "",
                        rtspUrl = cursor.getString(2) ?: "",
                    ),
                )
            } while (cursor.moveToNext())
        }

        cursor.close()
        return result
    }

    fun updateCamera(camera: CameraDevice): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_NAME, camera.name)
            put(KEY_RTSP_URL, camera.rtspUrl)
        }
        return db.update(
            TABLE_CAMERAS,
            values,
            "$KEY_ID = ?",
            arrayOf(camera.id),
        )
    }

    fun deleteCamera(camera: CameraDevice) {
        deleteCamera(camera.id)
    }

    fun deleteCamera(id: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(
            TABLE_CAMERAS,
            "$KEY_ID = ?",
            arrayOf(id),
        )
        db.close()
        return rows > 0
    }

    fun getCamerasCount(): Int {
        val countQuery = "SELECT * FROM $TABLE_CAMERAS"
        val db = readableDatabase
        val cursor = db.rawQuery(countQuery, null)
        val count = cursor.count
        cursor.close()
        return count
    }

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "NVRDatabase"
        private const val TABLE_CAMERAS = "cameras"

        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_RTSP_URL = "rtsp_url"
    }
}
