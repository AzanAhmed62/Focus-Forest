package com.azanahmed.focusforest.platform.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.core.content.ContextCompat
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class AmbientPlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    PAUSED_FOR_FOCUS_LOSS,
    FOCUS_DENIED,
    UNAVAILABLE
}

interface AmbientAudioController {
    val isPlaying: StateFlow<Boolean>
    val activePreset: StateFlow<AmbientSoundPreset>
    val playbackState: StateFlow<AmbientPlaybackState>
    fun startOrUpdate(preset: AmbientSoundPreset, volume: Float)
    fun setVolume(volume: Float)
    fun pause()
    fun stop()
}

/**
 * Pure procedural DSP soundscape synthesizer using Android's AudioTrack.
 * Generates 100% offline soft rain, forest wind, ocean waves, hearth ember,
 * and brown noise with zero external files or network access.
 *
 * Audio focus is requested for the whole playback session. Focus loss pauses synthesis,
 * becoming-noisy (headphone unplug) pauses playback, and every AudioTrack is released in
 * the coroutine's finally block. Only one synthesis loop can run at a time.
 */
class AndroidAmbientAudioController(
    context: Context? = null
) : AmbientAudioController {

    private val appContext: Context? = context?.applicationContext
    private val audioManager: AudioManager? =
        appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val synthesisMutex = Mutex()
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activePreset = MutableStateFlow(AmbientSoundPreset.NONE)
    override val activePreset: StateFlow<AmbientSoundPreset> = _activePreset.asStateFlow()

    private val _playbackState = MutableStateFlow(AmbientPlaybackState.IDLE)
    override val playbackState: StateFlow<AmbientPlaybackState> = _playbackState.asStateFlow()

    @Volatile
    private var targetVolume: Float = 0.55f

    @Volatile
    private var duckFactor: Float = 1f

    private var playbackGeneration: Int = 0
    private val stateLock = Any()

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change -> onAudioFocusChange(change) }
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseForFocusLoss()
            }
        }
    }

    init {
        appContext?.let { ctx ->
            runCatching {
                ContextCompat.registerReceiver(
                    ctx,
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        }
    }

    @Synchronized
    override fun startOrUpdate(preset: AmbientSoundPreset, volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        if (preset == AmbientSoundPreset.NONE) {
            stop()
            return
        }
        if (_activePreset.value == preset && _isPlaying.value && playbackJob?.isActive == true) {
            return
        }

        // Cancel the previous loop; the mutex makes the new loop wait for AudioTrack release.
        playbackJob?.cancel()
        playbackJob = null

        if (!requestAudioFocus()) {
            _activePreset.value = preset
            _isPlaying.value = false
            _playbackState.value = AmbientPlaybackState.FOCUS_DENIED
            return
        }

        val generation = synchronized(stateLock) {
            playbackGeneration += 1
            playbackGeneration
        }
        _activePreset.value = preset
        _isPlaying.value = true
        _playbackState.value = AmbientPlaybackState.PLAYING
        playbackJob = scope.launch {
            synthesisMutex.withLock {
                runSynthesisLoop(preset, generation)
            }
        }
    }

    override fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
    }

    @Synchronized
    override fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        synchronized(stateLock) { playbackGeneration += 1 }
        _isPlaying.value = false
        if (_playbackState.value != AmbientPlaybackState.IDLE) {
            _playbackState.value = AmbientPlaybackState.PAUSED
        }
        abandonAudioFocus()
    }

    @Synchronized
    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        synchronized(stateLock) { playbackGeneration += 1 }
        _isPlaying.value = false
        _activePreset.value = AmbientSoundPreset.NONE
        _playbackState.value = AmbientPlaybackState.IDLE
        abandonAudioFocus()
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                duckFactor = 1f
                if (_playbackState.value == AmbientPlaybackState.PAUSED_FOR_FOCUS_LOSS &&
                    _activePreset.value != AmbientSoundPreset.NONE
                ) {
                    startOrUpdate(_activePreset.value, targetVolume)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Keep playing, but stay clearly underneath calls/navigation prompts.
                duckFactor = 0.2f
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                duckFactor = 1f
                pauseForFocusLoss(abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                duckFactor = 1f
                pauseForFocusLoss(abandonFocus = true)
            }
        }
    }

    private fun pauseForFocusLoss(abandonFocus: Boolean = false) {
        playbackJob?.cancel()
        playbackJob = null
        synchronized(stateLock) { playbackGeneration += 1 }
        _isPlaying.value = false
        if (_activePreset.value != AmbientSoundPreset.NONE) {
            _playbackState.value = AmbientPlaybackState.PAUSED_FOR_FOCUS_LOSS
        }
        if (abandonFocus) {
            abandonAudioFocus()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        return manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }

    private suspend fun runSynthesisLoop(preset: AmbientSoundPreset, generation: Int) {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            markUnavailable(generation)
            return
        }
        val bufferSamples = (minBuf / 2).coerceAtLeast(1024)
        val pcmBuffer = ShortArray(bufferSamples)

        val audioTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSamples * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull()

        if (audioTrack == null || audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { audioTrack?.release() }
            markUnavailable(generation)
            return
        }

        val rng = Random(preset.ordinal * 7919 + 42)
        var lpState1 = 0.0
        var lpState2 = 0.0
        var lfoPhase = 0.0
        var crackleDecay = 0.0

        try {
            audioTrack.play()
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                markUnavailable(generation)
                return
            }
            while (currentCoroutineContext().isActive) {
                val vol = targetVolume * duckFactor
                for (i in 0 until bufferSamples) {
                    val white = rng.nextDouble() * 2.0 - 1.0
                    lfoPhase += 2.0 * PI / (sampleRate * 8.0)
                    if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI

                    val sample = when (preset) {
                        AmbientSoundPreset.SOFT_RAIN -> {
                            lpState1 = lpState1 * 0.86 + white * 0.14
                            lpState2 = lpState2 * 0.55 + white * 0.45
                            val drop = if (rng.nextDouble() < 0.00045) 0.5 else 0.0
                            (lpState1 * 0.75 + lpState2 * 0.25 + drop * (rng.nextDouble() - 0.3)) * 0.65
                        }
                        AmbientSoundPreset.FOREST_WIND -> {
                            lpState1 = lpState1 * 0.94 + white * 0.06
                            val gust = 0.45 + 0.55 * (0.5 + 0.5 * sin(lfoPhase))
                            lpState1 * gust * 0.85
                        }
                        AmbientSoundPreset.OCEAN_WAVES -> {
                            lpState1 = lpState1 * 0.955 + white * 0.045
                            val surf = 0.25 + 0.75 * (0.5 + 0.5 * sin(lfoPhase * 0.85))
                            lpState1 * surf * 0.95
                        }
                        AmbientSoundPreset.HEARTH_FIRE -> {
                            lpState1 = lpState1 * 0.97 + white * 0.03
                            if (rng.nextDouble() < 0.0007) {
                                crackleDecay = 0.35 + rng.nextDouble() * 0.4
                            }
                            crackleDecay *= 0.96
                            val ember = lpState1 * 0.8 + white * crackleDecay * 0.35
                            ember * 0.75
                        }
                        AmbientSoundPreset.BROWN_NOISE -> {
                            lpState1 = (lpState1 + white * 0.04).coerceIn(-1.0, 1.0)
                            lpState1 * 0.65
                        }
                        AmbientSoundPreset.NONE -> 0.0
                    }

                    pcmBuffer[i] = (sample * vol * Short.MAX_VALUE * 0.55)
                        .coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        .toInt()
                        .toShort()
                }
                val written = audioTrack.write(pcmBuffer, 0, bufferSamples)
                if (written < 0) {
                    markUnavailable(generation)
                    break
                }
            }
        } catch (_: Throwable) {
            // Cancellation or audio-route interruption: release below, report only if still current.
            if (currentCoroutineContext().isActive) {
                markUnavailable(generation)
            }
        } finally {
            runCatching { if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
        }
    }

    private fun markUnavailable(generation: Int) {
        synchronized(stateLock) {
            if (generation != playbackGeneration) return
            _isPlaying.value = false
            _playbackState.value = AmbientPlaybackState.UNAVAILABLE
        }
    }
}
