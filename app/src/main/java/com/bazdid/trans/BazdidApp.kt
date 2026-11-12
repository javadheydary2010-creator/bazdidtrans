package com.bazdid.trans

import android.app.Application
import android.util.Log
import org.osmdroid.config.Configuration
import java.io.File

class BazdidApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // تنظیمات OsmDroid برای کار آفلاین
            val baseDir = getExternalFilesDir(null) ?: filesDir
            val osmdroidPath = File(baseDir, "osmdroid")
            val tileCache = File(osmdroidPath, "tiles")

            // ساخت پوشه‌ها اگر وجود نداشته باشند
            osmdroidPath.mkdirs()
            tileCache.mkdirs()

            Configuration.getInstance().apply {
                userAgentValue = packageName
                osmdroidBasePath = osmdroidPath
                osmdroidTileCache = tileCache
            }
        } catch (e: Exception) {
            Log.e("BazdidApp", "Error initializing OsmDroid", e)
        }
    }
}
