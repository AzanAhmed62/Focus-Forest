package com.example.platform.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.domain.model.AmbientSoundPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

interface AmbientAudioController {
    val isPlaying: StateFlow<Boolean>
    val activePreset: StateFlow<AmbientSoundPreset>
    fun startOrUpdate(preset: AmbientSoundPreset, volume: Float)
    fun setVolume(volume: Float)
    fun pause()
    fun stop()
}

/**
 * Pure procedural DSP soundscape synthesizer using Android's AudioTrack.
 * Generates 100% offline soft rain, forest wind, ocean waves, hearth ember,
 * and brown noise with zero external files or network access.
 */
class AndroidAmbientAudioController : AmbientAudioController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activePreset = MutableStateFlow(AmbientSoundPreset.NONE)
    override val activePreset: StateFlow<AmbientSoundPreset> = _activePreset.asStateFlow()

    @Volatile
    private var targetVolume: Float = 0.55f

    override fun startOrUpdate(preset: AmbientSoundPreset, volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        if (preset == AmbientSoundPreset.NONE) {
            stop()
            return
        }
        if (_activePreset.value == preset && _isPlaying.value && playbackJob?.isActive == true) {
            return
        }
        stop()
        _activePreset.value = preset
        _isPlaying.value = true

        playbackJob = scope.launch {
            runSynthesisLoop(preset)
        }
    }

    override fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
    }

    override fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _activePreset.value = AmbientSoundPreset.NONE
    }

    private fun CoroutineScope.runSynthesisLoop(preset: AmbientSoundPreset) {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val bufferSamples = (minBuf / 2).coerceAtLeast(1024)
        val pcmBuffer = ShortArray(bufferSamples)

        val audioTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
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

        if (audioTrack == null) {
            _isPlaying.value = false
            return
        }

        val rng = Random(preset.ordinal * 7919 + 42)
        var lpState1 = 0.0
        var lpState2 = 0.0
        var lfoPhase = 0.0
        var crackleDecay = 0.0

        try {
            audioTrack.play()
            while (isActive) {
                val vol = targetVolume
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

                    val scaled = (sample * vol * Short.MAX_VALUE * 0.55)
                        .coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        .toInt()
                        .toShort()
                    pcmBuffer[i] = scaled
                }
                audioTrack.write(pcmBuffer, 0, bufferSamples)
            }
        } catch (_: Throwable) {
            // Safe shutdown on audio focus loss / interrupted coroutine / Robolectric shadow
        } finally {
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
        }
    }
}
