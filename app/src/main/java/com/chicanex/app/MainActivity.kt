package com.chicanex.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chicanex.app.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationOverlay: MyLocationNewOverlay? = null
    private var compassOverlay: CompassOverlay? = null
    private var currentSpeedKmh: Float = 0f
    private var locationCallback: LocationCallback? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            enableLocationTracking()
        } else {
            Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupHudControls()
        checkLocationPermissions()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            // Default center - will update with real location
            controller.setCenter(GeoPoint(48.1351, 11.5820)) // Munich as default
        }

        compassOverlay = CompassOverlay(
            this,
            InternalCompassOrientationProvider(this),
            binding.mapView
        ).also {
            it.enableCompass()
            binding.mapView.overlays.add(it)
        }
    }

    private fun setupHudControls() {
        // Re-center button
        binding.fabRecenter.setOnClickListener {
            locationOverlay?.myLocation?.let { loc ->
                binding.mapView.controller.animateTo(loc)
            }
        }

        // Zoom in / zoom out
        binding.btnZoomIn.setOnClickListener {
            binding.mapView.controller.zoomIn()
        }
        binding.btnZoomOut.setOnClickListener {
            binding.mapView.controller.zoomOut()
        }

        // Search toggle
        binding.btnSearch.setOnClickListener {
            val isVisible = binding.searchContainer.visibility == android.view.View.VISIBLE
            binding.searchContainer.visibility = if (isVisible) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun checkLocationPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            enableLocationTracking()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableLocationTracking() {
        locationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this),
            binding.mapView
        ).also {
            it.enableMyLocation()
            it.enableFollowLocation()
            binding.mapView.overlays.add(it)
        }

        startSpeedTracking()
    }

    private fun startSpeedTracking() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMinUpdateIntervalMillis(500L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { updateSpeedDisplay(it) }
            }
        }

        val callback = locationCallback ?: return
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            mainLooper
        )
    }

    private fun updateSpeedDisplay(location: Location) {
        currentSpeedKmh = if (location.hasSpeed()) {
            location.speed * MS_TO_KMH
        } else {
            0f
        }
        val speedInt = currentSpeedKmh.toInt()
        binding.tvSpeed.text = speedInt.toString()
        updateSpeedWarning(speedInt)
    }

    private fun updateSpeedWarning(speed: Int) {
        val warningColor = when {
            speed > SPEED_THRESHOLD_CRITICAL -> ContextCompat.getColor(this, R.color.rally_red)
            speed > SPEED_THRESHOLD_WARNING -> ContextCompat.getColor(this, R.color.rally_orange)
            else -> ContextCompat.getColor(this, R.color.neon_cyan)
        }
        binding.tvSpeed.setTextColor(warningColor)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        compassOverlay?.enableCompass()
        locationOverlay?.enableMyLocation()
        locationOverlay?.enableFollowLocation()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        compassOverlay?.disableCompass()
        locationOverlay?.disableMyLocation()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationOverlay?.disableMyLocation()
        binding.mapView.onDetach()
    }

    companion object {
        private const val MS_TO_KMH = 3.6f
        private const val SPEED_THRESHOLD_WARNING = 100
        private const val SPEED_THRESHOLD_CRITICAL = 130
    }
}
