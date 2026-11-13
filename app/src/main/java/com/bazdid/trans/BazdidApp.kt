package com.bazdid.trans

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class BazdidApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // تنظیمات OsmDroid برای کار آفلاین
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }
}
