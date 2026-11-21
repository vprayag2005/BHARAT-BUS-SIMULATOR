package com.bharatbussim.game.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Realistic dual-voice English speech + mechanical fuel dispenser audio controller.
 *
 * Uses Android's built-in on-device TextToSpeech engine with two distinct English voice profiles:
 *   1. Fuel Station Attendant voice (Worker 1 Petrol / Worker 2 Diesel — polite Indian-English tone)
 *   2. Bus Driver reply voice (distinct deeper second English voice for driver responses)
 *
 * Also synthesizes realistic mechanical fuel-nozzle latch clicks, pressurized fuel flow hum,
 * and rhythmic dispenser meter clicks synced with active refueling.
 */
internal class FuelStationVoiceManager(
    context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    private var workerDieselVoice: Voice? = null
    private var workerPetrolVoice: Voice? = null
    private var driverVoice: Voice? = null

    private var fuelFlowJob: Job? = null
    @Volatile private var fuelFlowActive = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Throwable) {
            tts = null
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        try {
            val indianEnglish = Locale("en", "IN")
            val resIn = engine.setLanguage(indianEnglish)
            if (resIn == TextToSpeech.LANG_MISSING_DATA || resIn == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }

            // Pick distinct offline English voices if available on the device so Worker and Driver
            // sound genuinely like two different English speakers!
            val allVoices = engine.voices?.filter { v ->
                v.locale?.language.equals("en", ignoreCase = true) && !v.isNetworkConnectionRequired
            } ?: emptyList()

            val inVoices = allVoices.filter { it.locale?.country.equals("IN", ignoreCase = true) }
            val otherEnVoices = allVoices.filter { !it.locale?.country.equals("IN", ignoreCase = true) }

            workerDieselVoice = inVoices.firstOrNull() ?: allVoices.firstOrNull()
            workerPetrolVoice = inVoices.getOrNull(1) ?: otherEnVoices.firstOrNull() ?: workerDieselVoice
            driverVoice = otherEnVoices.firstOrNull { it != workerDieselVoice }
                ?: inVoices.lastOrNull { it != workerDieselVoice }
                ?: allVoices.lastOrNull()
                ?: workerDieselVoice

            isReady = true
        } catch (_: Throwable) {
            isReady = false
        }
    }

    fun speakWorker(
        text: String,
        isPetrolWorker: Boolean,
        flush: Boolean = true
    ) {
        val engine = tts ?: return
        if (!isReady) return
        try {
            val targetVoice = if (isPetrolWorker) workerPetrolVoice else workerDieselVoice
            if (targetVoice != null) {
                engine.voice = targetVoice
            }
            engine.setPitch(if (isPetrolWorker) 1.07f else 0.96f)
            engine.setSpeechRate(if (isPetrolWorker) 0.98f else 0.94f)
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, "worker_${System.nanoTime()}")
        } catch (_: Throwable) {
        }
    }

    fun speakDriver(
        text: String,
        flush: Boolean = true
    ) {
        val engine = tts ?: return
        if (!isReady) return
        try {
            val v = driverVoice
            if (v != null) {
                engine.voice = v
            }
            // Deeper, brisker English cadence so the Driver clearly sounds like a second person
            engine.setPitch(0.80f)
            engine.setSpeechRate(1.04f)
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, "driver_${System.nanoTime()}")
        } catch (_: Throwable) {
        }
    }

    fun stopSpeech() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }

    fun playNozzleClick(inserting: Boolean) {
        scope.launch(Dispatchers.Default) {
            val pcm = buildNozzleLatchPcm(inserting)
            playOneShotPcm(pcm)
        }
    }

    fun playFuelDoorLatchSound(opening: Boolean) {
        scope.launch(Dispatchers.Default) {
            val pcm = buildFuelDoorLatchPcm(opening)
            playOneShotPcm(pcm)
        }
    }

    fun startFuelFlowSound() {
        if (fuelFlowActive) return
        fuelFlowActive = true
        fuelFlowJob?.cancel()
        fuelFlowJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val chunkSamples = 2048
            val buf = ShortArray(chunkSamples)
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 2)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Throwable) {
                return@launch
            }

            try {
                track.play()
                var seed = 0x71A3E95Bu
                var lp1 = 0.0
                var lp2 = 0.0
                var phasePump = 0.0
                var sampleCursor = 0L
                val tickPeriodSamples = (sampleRate * 0.145).toLong() // crisp mechanical meter relay tick

                while (isActive && fuelFlowActive) {
                    for (i in 0 until chunkSamples) {
                        seed = seed * 1664525u + 1013904223u
                        val white = ((seed shr 16).toInt() / 32768.0) - 1.0
                        // Band-limited pressurized fuel flow rush through rubber hose + tank glug
                        lp1 += 0.14 * (white - lp1)
                        lp2 += 0.035 * (lp1 - lp2)
                        val flowNoise = (lp1 - lp2 * 0.55) * 0.42

                        // Subtle electric fuel pump motor hum (118 Hz + 236 Hz)
                        phasePump += 2.0 * PI * 118.0 / sampleRate
                        val pumpHum = (sin(phasePump) * 0.16 + sin(phasePump * 2.0) * 0.06)

                        // Soft rhythmic dispenser digital/relay counter click
                        val modTick = sampleCursor % tickPeriodSamples
                        val tickPulse = if (modTick < 140) {
                            val u = modTick / 140.0
                            sin(2.0 * PI * 1850.0 * (modTick.toDouble() / sampleRate)) * exp(-u * 6.5) * 0.18
                        } else {
                            0.0
                        }

                        val sample = ((flowNoise + pumpHum + tickPulse) * 0.42).coerceIn(-0.95, 0.95)
                        buf[i] = (sample * 32767.0).toInt().toShort()
                        sampleCursor++
                    }
                    track.write(buf, 0, chunkSamples)
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stopFuelFlowSound(playShutoffClunk: Boolean = false) {
        fuelFlowActive = false
        fuelFlowJob?.cancel()
        fuelFlowJob = null
        if (playShutoffClunk) {
            playNozzleClick(inserting = false)
        }
    }

    private fun buildNozzleLatchPcm(inserting: Boolean): ShortArray {
        val sr = 22050
        val dur = if (inserting) 0.22 else 0.28
        val n = (sr * dur).toInt()
        val pcm = ShortArray(n)
        var seed = if (inserting) 0x29F13A1u else 0x8C4219Fu
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0
            val hit1 = (sin(2.0 * PI * (if (inserting) 640.0 else 480.0) * t) + white * 0.45) * exp(-t * 42.0)
            val t2 = t - (if (inserting) 0.075 else 0.095)
            val hit2 = if (t2 > 0.0) {
                (sin(2.0 * PI * (if (inserting) 1180.0 else 920.0) * t2) + white * 0.35) * exp(-t2 * 55.0)
            } else {
                0.0
            }
            val v = ((hit1 * 0.65 + hit2 * 0.55) * 0.72).coerceIn(-0.98, 0.98)
            pcm[i] = (v * 32767.0).toInt().toShort()
        }
        return pcm
    }

    private fun buildFuelDoorLatchPcm(opening: Boolean): ShortArray {
        val sr = 22050
        val dur = if (opening) 0.34 else 0.26
        val n = (sr * dur).toInt()
        val pcm = ShortArray(n)
        var seed = if (opening) 0x5A73E11u else 0x3D91C47u
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0
            val v = if (opening) {
                // Crisp metallic pop latch + light spring hinge whisper
                val click = (sin(2.0 * PI * 980.0 * t) + white * 0.40) * exp(-t * 58.0) * 0.62
                val hingeT = (t - 0.045).coerceAtLeast(0.0)
                val hinge = if (t > 0.045) {
                    val freq = 310.0 + hingeT * 190.0
                    sin(2.0 * PI * freq * hingeT) * exp(-hingeT * 11.5) * 0.16
                } else {
                    0.0
                }
                (click + hinge) * 0.72
            } else {
                // Solid sheet-metal thud + positive locking latch click
                val thud = (sin(2.0 * PI * 185.0 * t) * 0.75 + white * 0.38) * exp(-t * 32.0) * 0.70
                val t2 = t - 0.048
                val lockClick = if (t2 > 0.0) {
                    (sin(2.0 * PI * 1320.0 * t2) + white * 0.25) * exp(-t2 * 70.0) * 0.52
                } else {
                    0.0
                }
                (thud + lockClick) * 0.78
            }
            pcm[i] = (v.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
        }
        return pcm
    }

    private fun playOneShotPcm(pcm: ShortArray) {
        val sr = 22050
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Throwable) {
            return
        }
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((pcm.size * 1000L / sr) + 60L)
        } catch (_: Throwable) {
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun release() {
        stopFuelFlowSound(playShutoffClunk = false)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        isReady = false
    }
}
