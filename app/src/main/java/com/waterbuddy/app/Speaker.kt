package com.waterbuddy.app

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/** குரல் ஸ்டைல் — எந்த voice-லயும் pitch/வேகம் மாத்தி வேற மாதிரி ஒலிக்க வைக்கும். */
enum class VoiceStyle(val key: String, val label: String, val pitch: Float, val rate: Float) {
    NATURAL("natural", "🙂 இயல்பு", 1.0f, 0.95f),
    HERO("hero", "😎 Mass ஹீரோ", 0.75f, 0.9f),
    MALE("male", "👨 ஆழ்ந்த ஆண்", 0.85f, 0.95f),
    FEMALE("female", "👩 இனிமையான பெண்", 1.18f, 0.97f),
    KID("kid", "🧒 குழந்தை", 1.5f, 1.05f),
    ELDER("elder", "👵 பாட்டி", 0.95f, 0.8f);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: NATURAL
    }
}

/**
 * Buddy பேசுற குரல் (phone-ஓட Text-to-Speech).
 * நீங்க தேர்ந்தெடுத்த voice இருந்தா அது; இல்லைனா மிக இயல்பான Tamil voice-ஐ தானா தேர்ந்தெடுக்கும்.
 */
class Speaker(
    ctx: Context,
    private val style: VoiceStyle = ReminderStore.voiceStyle.value,
    private val voiceName: String? = ReminderStore.voiceName.value
) : TextToSpeech.OnInitListener {
    private val app = ctx.applicationContext
    private val tts = TextToSpeech(app, this)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var tamil = false
    private var pending: (() -> Unit)? = null
    private val texts = HashMap<String, String>()

    /** இப்போ பேசுற வரி (bubble-ல காட்ட) — main thread-ல வரும் */
    var onSpeak: ((String) -> Unit)? = null
    var onReady: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts.setLanguage(Locale("ta", "IN"))
        tamil = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        if (!tamil) tts.setLanguage(Locale("en", "IN"))
        val chosen = voiceName?.let { n -> try { tts.voices?.firstOrNull { it.name == n } } catch (_: Exception) { null } }
        if (chosen != null) {
            tts.voice = chosen
            tamil = chosen.locale.language == "ta"
        } else pickBestVoice(if (tamil) "ta" else "en")
        tts.setPitch(style.pitch)
        tts.setSpeechRate(style.rate)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                val t = texts[id] ?: return
                main.post { onSpeak?.invoke(t) }
            }
            override fun onDone(id: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {}
        })
        ready = true
        main.post { onReady?.invoke() }
        pending?.invoke()
        pending = null
    }

    val isTamil get() = tamil

    /** Phone-ல இருக்குற voice-கள் (Tamil முதல்ல, அப்புறம் Indian English) */
    fun voices(): List<Voice> = try {
        (tts.voices ?: emptySet()).filter {
            (it.locale.language == "ta" || (it.locale.language == "en" && it.locale.country == "IN")) &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }.sortedWith(compareBy({ if (it.locale.language == "ta") 0 else 1 }, { it.name }))
    } catch (_: Exception) { emptyList() }

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

    private fun pick(p: Pair<String, String>) = if (tamil) p.first else p.second

    fun say(tamilText: String, englishText: String) {
        if (!ready) { pending = { say(tamilText, englishText) }; return }
        val t = if (tamil) tamilText else englishText
        texts["one"] = t
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "one")
    }

    /** வெவ்வேற வரிகளை இடைவெளி விட்டு தொடர்ந்து சொல்லும் (3–5 தடவை). */
    fun sayRepeated(lines: List<Pair<String, String>>, gapMs: Long = 1300L) {
        if (!ready) { pending = { sayRepeated(lines, gapMs) }; return }
        tts.stop()
        lines.forEachIndexed { i, p ->
            val t = pick(p)
            texts["ask$i"] = t
            tts.speak(t, TextToSpeech.QUEUE_ADD, null, "ask$i")
            if (i < lines.lastIndex) tts.playSilentUtterance(gapMs, TextToSpeech.QUEUE_ADD, "gap$i")
        }
    }

    fun stop() {
        pending = null
        try { tts.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        stop()
        onSpeak = null; onReady = null
        tts.shutdown()
    }

    companion object {
        /** Phone silent / vibrate-ல இருந்தா பேசாது. */
        fun soundAllowed(ctx: Context): Boolean =
            ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}

object Lines {
    /** தேர்ந்தெடுக்கக்கூடிய 10 வரிகள் (Tamil voice இல்லைனா English) */
    val presets = listOf(
        "ஹலோ! தண்ணி குடிக்கிற நேரம் வந்தாச்சு!" to "Hey! It's water time!",
        "ஒரு கிளாஸ் தண்ணி குடிங்க, ப்ளீஸ்!" to "Please drink a glass of water!",
        "வாங்க வாங்க, தண்ணி குடிச்சிட்டு வாங்க!" to "Come on, go grab some water!",
        "நான் இங்கயே வெயிட் பண்றேன், தண்ணி குடிங்க!" to "I'm right here waiting. Drink your water!",
        "ஒரே ஒரு கிளாஸ் தான், சீக்கிரம்!" to "Just one glass, quickly!",
        "தண்ணி குடிச்சா தான் தெம்பா இருப்பீங்க!" to "Water keeps you energetic!",
        "Work அப்புறம் பாத்துக்கலாம், முதல்ல தண்ணி!" to "Work can wait. Water first!",
        "உங்க உடம்புக்கு தண்ணி வேணுமாம்!" to "Your body is asking for water!",
        "தண்ணி குடிச்சா முகம் பளபளன்னு இருக்கும்!" to "Water makes your skin glow!",
        "Boss சொல்றேன், தண்ணி குடிங்க!" to "Boss's orders: drink water!"
    )

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
