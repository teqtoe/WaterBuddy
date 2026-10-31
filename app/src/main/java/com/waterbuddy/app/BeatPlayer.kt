package com.waterbuddy.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Buddy வரும்போது ஒலிக்கும் மென்மையான (light) இசை — code-லயே உருவாக்கப்படுது (பாட்டு file எதுவும் இல்லை,
 * copyright பிரச்சனை இல்லை). 120 BPM, 4 வினாடி loop — கேரக்டர் dance-உம் இதே வேகம்.
 */
class BeatPlayer {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        try {
            val pcm = BeatSynth.loop
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(BeatSynth.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            t.setVolume(0.32f)
            t.play()
            track = t
        } catch (_: Exception) {
            track = null
        }
    }

    /** Buddy பேசும்போது இசையை குறை, முடிஞ்சதும் பழைய அளவு */
    fun duck(on: Boolean) {
        try { track?.setVolume(if (on) 0.08f else 0.32f) } catch (_: Exception) {}
    }

    fun stop() {
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
    }
}

private object BeatSynth {
    const val RATE = 22050
    private const val STEP_SEC = 0.25f          // 8th note @ 120 BPM
    private const val STEPS = 16                 // 8 beats

    val loop: ShortArray by lazy { render() }

    private fun render(): ShortArray {
        val stepLen = (RATE * STEP_SEC).toInt()
        val total = stepLen * STEPS
        val buf = FloatArray(total)
        val rnd = Random(7)

        fun add(start: Int, len: Int, f: (t: Float, i: Int) -> Float) {
            for (i in 0 until len) {
                val idx = (start + i) % total
                buf[idx] += f(i.toFloat() / RATE, i)
            }
        }

        val kickSteps = setOf(0, 4, 8, 12)
        val tomSteps = emptySet<Int>()
        val clapSteps = setOf(4, 12)
        // பெண்டாடோனிக் மெலடி (A4 = 440Hz-ல இருந்து semitone), -1 = மௌனம்
        val melody = intArrayOf(12, 7, 9, 7, 4, 7, 9, 12, 14, 12, 9, 7, 9, -1, 7, -1)
        val bass = floatArrayOf(110f, 110f, 146.83f, 130.81f)

        for (s in 0 until STEPS) {
            val start = s * stepLen
            if (s in kickSteps) {
                var ph = 0.0
                add(start, (RATE * 0.35f).toInt()) { t, _ ->
                    val f = 50f + 70f * exp(-t * 30f)
                    ph += 2 * PI * f / RATE
                    (sin(ph) * exp(-t * 12f) * 0.45).toFloat()
                }
            }
            if (s in tomSteps) {   // தப்பு மாதிரி
                var ph = 0.0
                add(start, (RATE * 0.25f).toInt()) { t, _ ->
                    val f = 150f + 60f * exp(-t * 25f)
                    ph += 2 * PI * f / RATE
                    val click = if (t < 0.01f) (rnd.nextFloat() * 2f - 1f) * 0.4f else 0f
                    (sin(ph) * exp(-t * 14f) * 0.6).toFloat() + click
                }
            }
            if (s in clapSteps) {
                add(start, (RATE * 0.18f).toInt()) { t, _ -> (rnd.nextFloat() * 2f - 1f) * exp(-t * 40f) * 0.12f }
            }
            // ஷேக்கர் — ஒவ்வொரு step-லயும்
            var prev = 0f
            add(start, (RATE * 0.05f).toInt()) { t, _ ->
                val n = rnd.nextFloat() * 2f - 1f
                val hp = n - prev; prev = n
                hp * exp(-t * 90f) * 0.05f
            }
            // மெலடி
            val note = melody[s]
            if (note >= 0) {
                val f = 440.0 * 2.0.pow((note - 12) / 12.0)
                add(start, (stepLen * 0.95f).toInt()) { t, _ ->
                    val x = 2 * PI * f * t
                    // marimba மாதிரி மென்மையான ஒலி
                    val env = (t / 0.004f).coerceAtMost(1f) * exp(-t * 9f)
                    ((sin(x) + 0.25 * sin(4 * x) * exp(-t * 30.0)) * env * 0.26).toFloat()
                }
            }
            // bass — ஒவ்வொரு beat-க்கும்
            if (s % 4 == 0) {
                val f = bass[s / 4]
                add(start, stepLen * 2) { t, _ ->
                    val env = (t / 0.01f).coerceAtMost(1f) * exp(-t * 3.5f)
                    (sin(2 * PI * f * t) * env * 0.22).toFloat()
                }
            }
        }

        var peak = 0.0001f
        for (v in buf) peak = maxOf(peak, abs(v))
        val g = 0.9f / peak
        return ShortArray(total) { i -> (buf[i] * g * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }
}
