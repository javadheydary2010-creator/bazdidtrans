package com.bazdid.trans.ui.offline

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.bazdid.trans.databinding.ActivityOfflineMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import java.io.File
import java.io.FileOutputStream

/**
 * نمایش نقشه آنلاین (با امکان استفاده از کش در صورت نبود اینترنت)
 */
class OfflineMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineMapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        prepareOfflineTiles()

        binding = ActivityOfflineMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "نقشه آنلاین"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(26.95, 54.80))
            // نقشه آنلاین — در صورت قطع اینترنت از کش استفاده می‌کند
            setUseDataConnection(true)
        }

        Toast.makeText(
            this,
            "نقشه آنلاین فعال است",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun prepareOfflineTiles() {
        try {
            val base = File(getExternalFilesDir(null), "osmdroid")
            if (!base.exists()) base.mkdirs()
            val mbtiles = File(base, "mehran_area.mbtiles")
            if (!mbtiles.exists()) {
                assets.open("maps/mehran_area.mbtiles").use { input ->
                    FileOutputStream(mbtiles).use { output -> input.copyTo(output) }
                }
            }
            Configuration.getInstance().osmdroidBasePath = base
            Configuration.getInstance().osmdroidTileCache = File(base, "tiles")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
