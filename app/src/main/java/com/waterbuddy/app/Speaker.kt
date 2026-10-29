package com.waterbuddy.app

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Buddy பேசுற குரல் (phone-ஓட Text-to-Speech).
 * Phone-ல இருக்குற Tamil voice-கள்ல மிக இயல்பான (high quality / network) voice-ஐ தானா தேர்ந்தெடுக்கும்.
 * Tamil voice இல்லைனா English-ல பேசும்.
 */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private val app = ctx.applicationContext
    private val tts = TextToSpeech(app, this)
    private var ready = false
    private var tamil = false
    private var pending: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val ta = Locale("ta", "IN")
        val r = tts.setLanguage(ta)
        tamil = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        if (!tamil) tts.setLanguage(Locale("en", "IN"))
        pickBestVoice(if (tamil) "ta" else "en")
        tts.setSpeechRate(0.95f)
        tts.setPitch(1.0f)
        ready = true
        pending?.invoke()
        pending = null
    }

    /** Robot மாதிரி இல்லாம இயல்பா ஒலிக்கிற voice-ஐ தேர்வு */
    private fun pickBestVoice(lang: String) {
        try {
            val online = app.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null
            val best = tts.voices
                ?.filter { v ->
                    v.locale.language == lang &&
                        !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                        (online || !v.isNetworkConnectionRequired)
                }
                ?.maxByOrNull { v ->
                    v.quality * 10 + (if (v.isNetworkConnectionRequired) 50 else 0) +
                        (if (v.locale.country == "IN") 5 else 0) - v.latency
                }
            if (best != null) tts.voice = best
        } catch (_: Exception) {
        }
    }

    fun say(tamilText: String, englishText: String) {
        if (!ready) { pending = { say(tamilText, englishText) }; return }
        tts.speak(if (tamil) tamilText else englishText, TextToSpeech.QUEUE_FLUSH, null, "buddy")
    }

    /** வெவ்வேற வரிகளை இடைவெளி விட்டு தொடர்ந்து சொல்லும் (3–5 தடவை). */
    fun sayRepeated(lines: List<Pair<String, String>>, gapMs: Long = 1400L) {
        if (!ready) { pending = { sayRepeated(lines, gapMs) }; return }
        tts.stop()
        lines.forEachIndexed { i, (ta, en) ->
            tts.speak(if (tamil) ta else en, TextToSpeech.QUEUE_ADD, null, "ask$i")
            if (i < lines.lastIndex) tts.playSilentUtterance(gapMs, TextToSpeech.QUEUE_ADD, "gap$i")
        }
    }

    fun stop() {
        pending = null
        try { tts.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    companion object {
        /** Phone silent / vibrate-ல இருந்தா பேசாது. */
        fun soundAllowed(ctx: Context): Boolean =
            ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}

object Lines {
    /** அலர்ட்ல தொடர்ந்து சொல்லும் வரிகள் (5 தடவை, ஒவ்வொன்னும் வேற மாதிரி) */
    val askSpoken = listOf(
        "ஹலோ! தண்ணி குடிக்கிற நேரம் வந்தாச்சு!" to "Hey! It's water time!",
        "ஒரு கிளாஸ் தண்ணி குடிங்க, ப்ளீஸ்!" to "Please drink a glass of water!",
        "வாங்க வாங்க, தண்ணி குடிச்சிட்டு வாங்க!" to "Come on, go grab some water!",
        "நான் இங்கயே வெயிட் பண்றேன், தண்ணி குடிங்க!" to "I'm right here waiting. Drink your water!",
        "ப்ளீஸ்… ஒரே ஒரு கிளாஸ் தான்!" to "Please, just one glass!"
    )
    const val ASK_TA = "ஹலோ! தண்ணி குடிக்கிற நேரம் வந்தாச்சு. ஒரு கிளாஸ் குடிங்க ப்ளீஸ்!"
    const val ASK_EN = "Hey! It's water time. Please drink a glass of water!"
    const val HAPPY_TA = "சூப்பர்! ரொம்ப சந்தோஷம்!"
    const val HAPPY_EN = "Yay! Awesome, I'm so happy!"
    const val SAD_TA = "ஹ்ம்ம்... சரி. எனக்கு கொஞ்சம் சோகமா இருக்கு."
    const val SAD_EN = "Oh no... okay. That makes me a little sad."

    val askBubbles = listOf(
        "Dance break! 💃🕺\nஒரு கிளாஸ் தண்ணி குடிச்சிட்டு வாங்க!",
        "ஹலோ! 👋 தண்ணி குடிச்சீங்களா?\nகுடிக்கிற வரைக்கும் நான் ஆடுவேன்!",
        "உங்க உடம்பு தாகமா இருக்கு! 🥵\nஒரு கிளாஸ் ப்ளீஸ்!"
    )
    val happyBubbles = listOf(
        "சூப்பர்! 🎉\nஉங்க உடம்பு Thanks சொல்லுது!",
        "வாவ்! நீங்க தான்\nHydration Hero! 💪",
        "அருமை! 😄\nஇப்படியே தொடருங்க!"
    )
    val sadBubbles = listOf(
        "ஹ்ம்ம்… 😢\nஅடுத்த முறை கண்டிப்பா குடிக்கணும், சரியா?",
        "நான் ரொம்ப சோகமா இருக்கேன்… 💔",
        "சரி… நான் வெயிட் பண்றேன் 🥺"
    )
}
