package com.chicanex.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chicanex.R
import com.chicanex.audio.AudioConfig
import com.chicanex.audio.AudioEventListener
import com.chicanex.audio.RallyAudioEngine
import com.chicanex.audio.RallyPaceNotePlayer
import com.chicanex.gpx.GpxParser
import com.chicanex.model.AnalyzedRoute
import com.chicanex.model.PaceNote
import com.chicanex.route.PaceNoteGenerator
import com.chicanex.route.RouteAnalyzer

/**
 * Main activity for ChicaneX - the AI Rally Copilot.
 *
 * Provides UI for:
 * - Loading GPX route files
 * - Viewing route analysis results (features, statistics)
 * - Playing pace notes with rally-style audio output
 * - Controlling playback speed and simulation
 */
class MainActivity : AppCompatActivity(), AudioEventListener {

    private lateinit var audioEngine: RallyAudioEngine
    private lateinit var player: RallyPaceNotePlayer
    private val routeAnalyzer = RouteAnalyzer()
    private val paceNoteGenerator = PaceNoteGenerator()

    private var analyzedRoute: AnalyzedRoute? = null
    private var simulationHandler: Handler? = null
    private var isSimulating = false
    private var simulationSpeedMps = 20.0 // meters per second (~72 km/h)

    // UI elements
    private lateinit var textRouteName: TextView
    private lateinit var textRouteStats: TextView
    private lateinit var textCurrentNote: TextView
    private lateinit var textProgress: TextView
    private lateinit var buttonLoad: Button
    private lateinit var buttonPlay: Button
    private lateinit var buttonStop: Button
    private lateinit var seekBarProgress: SeekBar
    private lateinit var progressBarLoading: ProgressBar
    private lateinit var textFeatureList: TextView

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1001
        private const val REQUEST_PERMISSION_STORAGE = 1002
        private const val SIMULATION_INTERVAL_MS = 100L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeAudio()
    }

    private fun initializeViews() {
        textRouteName = findViewById(R.id.textRouteName)
        textRouteStats = findViewById(R.id.textRouteStats)
        textCurrentNote = findViewById(R.id.textCurrentNote)
        textProgress = findViewById(R.id.textProgress)
        buttonLoad = findViewById(R.id.buttonLoad)
        buttonPlay = findViewById(R.id.buttonPlay)
        buttonStop = findViewById(R.id.buttonStop)
        seekBarProgress = findViewById(R.id.seekBarProgress)
        progressBarLoading = findViewById(R.id.progressBarLoading)
        textFeatureList = findViewById(R.id.textFeatureList)

        buttonLoad.setOnClickListener { openFilePicker() }
        buttonPlay.setOnClickListener { togglePlayback() }
        buttonStop.setOnClickListener { stopPlayback() }

        seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val route = analyzedRoute ?: return
                    val distance = (progress.toDouble() / 1000) * route.totalDistanceMeters
                    player.seekToDistance(distance)
                    updateProgressDisplay()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        buttonPlay.isEnabled = false
        buttonStop.isEnabled = false
        seekBarProgress.isEnabled = false
    }

    private fun initializeAudio() {
        audioEngine = RallyAudioEngine(
            context = this,
            config = AudioConfig(
                baseSpeechRate = 1.3f,
                basePitch = 1.05f
            ),
            listener = this
        )
        player = RallyPaceNotePlayer(audioEngine)

        player.onNoteTriggered = { note, index ->
            runOnUiThread {
                textCurrentNote.text = note.noteText
                textProgress.text = getString(
                    R.string.progress_format,
                    index + 1,
                    player.totalNotes,
                    player.currentDistance.toInt()
                )
                seekBarProgress.progress = (player.progress * 1000).toInt()
            }
        }

        audioEngine.initialize(
            onReady = {
                runOnUiThread {
                    Toast.makeText(this, R.string.audio_ready, Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.audio_error, error), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun openFilePicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERMISSION_STORAGE
            )
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/gpx+xml", "text/xml", "application/xml")
            )
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri -> loadRoute(uri) }
        }
    }

    private fun loadRoute(uri: Uri) {
        progressBarLoading.visibility = android.view.View.VISIBLE
        buttonLoad.isEnabled = false

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file")

                val parser = GpxParser()
                val gpxResult = parser.parse(inputStream)
                inputStream.close()

                // Analyze route
                val features = routeAnalyzer.analyzeRoute(gpxResult.waypoints)
                val segments = routeAnalyzer.buildSegments(gpxResult.waypoints)
                val stats = routeAnalyzer.calculateStatistics(gpxResult.waypoints, features, segments)
                val paceNotes = paceNoteGenerator.generatePaceNotes(features)

                val route = AnalyzedRoute(
                    name = gpxResult.name,
                    waypoints = gpxResult.waypoints,
                    features = features,
                    paceNotes = paceNotes,
                    totalDistanceMeters = stats.totalDistanceMeters,
                    totalElevationGain = stats.elevationGain,
                    totalElevationLoss = stats.elevationLoss,
                    maxGradientPercent = stats.maxGradientPercent,
                    curveCount = stats.curveCount,
                    averageCurveSeverity = stats.averageSeverity
                )

                analyzedRoute = route
                player.loadPaceNotes(paceNotes)

                runOnUiThread {
                    displayRoute(route)
                    progressBarLoading.visibility = android.view.View.GONE
                    buttonLoad.isEnabled = true
                    buttonPlay.isEnabled = true
                    seekBarProgress.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBarLoading.visibility = android.view.View.GONE
                    buttonLoad.isEnabled = true
                    Toast.makeText(this, getString(R.string.load_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun displayRoute(route: AnalyzedRoute) {
        textRouteName.text = route.name
        textRouteStats.text = route.summary()

        // Display feature list
        val featureText = route.features.joinToString("\n") { feature ->
            val dist = "%.0f m".format(feature.distanceFromStart)
            val type = feature.type.name
            val detail = buildString {
                feature.direction?.let { append(" ${it.name}") }
                feature.severity?.let { append(" (severity: $it)") }
                feature.lengthMeters?.let { append(" [${it.toInt()}m]") }
                feature.gradientPercent?.let { append(" grade: ${"%.1f".format(it)}%") }
                feature.modifier?.let { append(" ($it)") }
                feature.radiusMeters?.let { append(" R=${"%.0f".format(it)}m") }
            }
            "$dist: $type$detail"
        }
        textFeatureList.text = featureText

        textProgress.text = getString(R.string.progress_format, 0, route.paceNotes.size, 0)
    }

    private fun togglePlayback() {
        if (isSimulating) {
            pauseSimulation()
        } else {
            startSimulation()
        }
    }

    private fun startSimulation() {
        if (analyzedRoute == null) return

        isSimulating = true
        buttonPlay.text = getString(R.string.pause)
        buttonStop.isEnabled = true

        player.start()

        // Start simulation timer
        simulationHandler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isSimulating) {
                    val advanceMeters = simulationSpeedMps * (SIMULATION_INTERVAL_MS / 1000.0)
                    player.advanceByDistance(advanceMeters)
                    updateProgressDisplay()
                    simulationHandler?.postDelayed(this, SIMULATION_INTERVAL_MS)
                }
            }
        }
        simulationHandler?.postDelayed(runnable, SIMULATION_INTERVAL_MS)
    }

    private fun pauseSimulation() {
        isSimulating = false
        buttonPlay.text = getString(R.string.play)
        simulationHandler?.removeCallbacksAndMessages(null)
    }

    private fun stopPlayback() {
        isSimulating = false
        buttonPlay.text = getString(R.string.play)
        buttonStop.isEnabled = false
        simulationHandler?.removeCallbacksAndMessages(null)
        player.reset()
        seekBarProgress.progress = 0
        textCurrentNote.text = ""
        textProgress.text = getString(R.string.progress_format, 0, player.totalNotes, 0)
    }

    private fun updateProgressDisplay() {
        seekBarProgress.progress = (player.progress * 1000).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        simulationHandler?.removeCallbacksAndMessages(null)
        audioEngine.shutdown()
    }

    // AudioEventListener
    override fun onNoteStarted(note: PaceNote?) {
        // Already handled via onNoteTriggered
    }

    override fun onNoteCompleted(utteranceId: String?) {
        // No action needed
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
