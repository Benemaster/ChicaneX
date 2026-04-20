package com.chicanex.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.chicanex.model.NotePriority
import com.chicanex.model.PaceNote
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * RallyAudioEngine manages text-to-speech output with rally co-driver characteristics.
 *
 * Key features:
 * - Configurable speech rate and pitch for rally-authentic delivery
 * - Priority-based queuing (critical notes interrupt normal notes)
 * - Pace note timing based on simulated or GPS-based position
 * - Distinct speech patterns for different note types (curves, warnings, distances)
 * - Queue management to prevent note pile-up
 *
 * The speech style mimics a professional rally co-driver:
 * - Crisp, clear pronunciation
 * - Slightly elevated pitch for urgency
 * - Fast but comprehensible pace
 * - Emphasis on critical warnings
 */
class RallyAudioEngine(
    private val context: Context,
    private val config: AudioConfig = AudioConfig(),
    private val listener: AudioEventListener? = null
) {
    companion object {
        private const val TAG = "RallyAudioEngine"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val noteQueue = ConcurrentLinkedQueue<QueuedNote>()
    private var currentlyPlaying: QueuedNote? = null
    private var isSpeaking = false

    /**
     * Initializes the TTS engine. Must be called before any speaking.
     * @param onReady Callback when TTS is ready
     * @param onError Callback on initialization error
     */
    fun initialize(onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ttsEngine = tts ?: return@TextToSpeech

                // Set language
                val locale = config.locale
                val result = ttsEngine.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language $locale not supported, falling back to default")
                    ttsEngine.setLanguage(Locale.US)
                }

                // Configure speech parameters for rally style
                ttsEngine.setSpeechRate(config.baseSpeechRate)
                ttsEngine.setPitch(config.basePitch)

                // Set up progress listener
                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        listener?.onNoteStarted(currentlyPlaying?.note)
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        currentlyPlaying = null
                        listener?.onNoteCompleted(utteranceId)
                        processQueue()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        currentlyPlaying = null
                        Log.e(TAG, "TTS error for utterance: $utteranceId")
                        listener?.onError("TTS error for utterance: $utteranceId")
                        processQueue()
                    }
                })

                isInitialized = true
                Log.i(TAG, "Rally audio engine initialized")
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                onError("TTS initialization failed with status: $status")
            }
        }
    }

    /**
     * Speaks a pace note with rally-appropriate characteristics.
     *
     * The note's priority determines whether it interrupts the current note:
     * - CRITICAL: Interrupts immediately (e.g., "Caution! Hairpin left")
     * - NORMAL: Queued after current note
     * - INFO: Queued only if queue is short
     */
    fun speakPaceNote(note: PaceNote) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, queueing note: ${note.noteText}")
        }

        val queuedNote = QueuedNote(
            note = note,
            speechText = prepareSpeechText(note),
            speechRate = getSpeechRateForNote(note),
            pitch = getPitchForNote(note)
        )

        when (note.priority) {
            NotePriority.CRITICAL -> {
                // Critical notes interrupt current speech
                noteQueue.clear()
                noteQueue.add(queuedNote)
                if (isSpeaking) {
                    tts?.stop()
                }
                processQueue()
            }

            NotePriority.NORMAL -> {
                noteQueue.add(queuedNote)
                if (!isSpeaking) {
                    processQueue()
                }
            }

            NotePriority.INFO -> {
                // Only queue info notes if the queue is short
                if (noteQueue.size < config.maxQueueSize) {
                    noteQueue.add(queuedNote)
                    if (!isSpeaking) {
                        processQueue()
                    }
                }
            }
        }
    }

    /**
     * Speaks arbitrary text with rally co-driver style.
     */
    fun speak(text: String, priority: NotePriority = NotePriority.NORMAL) {
        val dummyNote = PaceNote(
            distanceFromStart = 0.0,
            noteText = text,
            features = emptyList(),
            priority = priority
        )
        speakPaceNote(dummyNote)
    }

    /**
     * Stops all speech and clears the queue.
     */
    fun stop() {
        noteQueue.clear()
        tts?.stop()
        isSpeaking = false
        currentlyPlaying = null
    }

    /**
     * Releases TTS resources. Must be called when the engine is no longer needed.
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    /**
     * Processes the next note in the queue.
     */
    private fun processQueue() {
        val next = noteQueue.poll() ?: return
        currentlyPlaying = next

        val ttsEngine = tts ?: return
        if (!isInitialized) return

        // Adjust speech rate and pitch for this specific note
        ttsEngine.setSpeechRate(next.speechRate)
        ttsEngine.setPitch(next.pitch)

        val utteranceId = "pace_note_${System.currentTimeMillis()}"
        val params = android.os.Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.volume)

        ttsEngine.speak(next.speechText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Prepares the spoken text with pauses and emphasis for rally-style delivery.
     *
     * Rally co-drivers use specific speech patterns:
     * - Short pauses between the direction and severity
     * - Longer pauses between separate notes
     * - Emphasis (louder/higher pitch) on severity numbers
     * - Quick delivery of "into" connections
     */
    internal fun prepareSpeechText(note: PaceNote): String {
        var text = note.noteText

        // Add SSML-style pauses for natural rally cadence
        // Note: Android TTS doesn't support full SSML but we can use text manipulation

        // Add slight pauses after distance calls
        text = text.replace(Regex("(\\d{2,3})\\s+(?=[A-Z])")) { match ->
            "${match.groupValues[1]}. "
        }

        // Make "into" flow quickly (no pause)
        text = text.replace(" into ", ", into, ")

        // Add emphasis for caution/warning words
        text = text.replace("caution", "CAUTION!")
        text = text.replace("Caution", "CAUTION!")

        // Add clarity for similar-sounding numbers
        text = text.replace(Regex("\\b([1-6])\\b")) { match ->
            when (match.groupValues[1]) {
                "1" -> "one"
                "2" -> "two"
                "3" -> "three"
                "4" -> "four"
                "5" -> "five"
                "6" -> "six"
                else -> match.value
            }
        }

        return text
    }

    /**
     * Determines the speech rate for a given note based on its content and priority.
     * Rally co-drivers speed up for linked notes and slow down for critical warnings.
     */
    private fun getSpeechRateForNote(note: PaceNote): Float {
        return when (note.priority) {
            NotePriority.CRITICAL -> config.baseSpeechRate * 0.9f  // Slightly slower for clarity
            NotePriority.NORMAL -> {
                // Speed up slightly for notes with many linked features
                val featureCount = note.features.size
                if (featureCount > 2) {
                    config.baseSpeechRate * 1.1f
                } else {
                    config.baseSpeechRate
                }
            }
            NotePriority.INFO -> config.baseSpeechRate * 0.95f
        }
    }

    /**
     * Determines the pitch for a given note. Higher pitch for urgent notes.
     */
    private fun getPitchForNote(note: PaceNote): Float {
        return when (note.priority) {
            NotePriority.CRITICAL -> config.basePitch * 1.15f  // Higher pitch for urgency
            NotePriority.NORMAL -> config.basePitch
            NotePriority.INFO -> config.basePitch * 0.95f
        }
    }

    /** Whether the engine is currently speaking */
    val isCurrentlySpeaking: Boolean get() = isSpeaking

    /** Number of notes waiting in the queue */
    val queueSize: Int get() = noteQueue.size
}

/**
 * A pace note prepared for speaking with adjusted parameters.
 */
internal data class QueuedNote(
    val note: PaceNote,
    val speechText: String,
    val speechRate: Float,
    val pitch: Float
)

/**
 * Configuration for the rally audio engine.
 */
data class AudioConfig(
    /** Base speech rate (1.0 = normal). Rally co-drivers typically speak faster. */
    val baseSpeechRate: Float = 1.3f,
    /** Base pitch (1.0 = normal). Slightly elevated for rally style. */
    val basePitch: Float = 1.05f,
    /** Audio volume (0.0 to 1.0) */
    val volume: Float = 1.0f,
    /** Locale for TTS */
    val locale: Locale = Locale.UK,
    /** Maximum notes in the queue before info notes are dropped */
    val maxQueueSize: Int = 5
)

/**
 * Listener interface for audio engine events.
 */
interface AudioEventListener {
    fun onNoteStarted(note: PaceNote?)
    fun onNoteCompleted(utteranceId: String?)
    fun onError(message: String)
}
