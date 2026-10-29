package com.waterbuddy.app

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Buddy பேசுற குரல் (phone-ஓட Text-to-Speech). Tamil voice இல்லைனா English-ல பேசும். */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    private var tamil = false
    private var pending: Pair<String, String>? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts.setLanguage(Locale("ta", "IN"))
        tamil = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        if (!tamil) tts.setLanguage(Locale.ENGLISH)
        tts.setPitch(1.1f)
        ready = true
        pending?.let { say(it.first, it.second) }
        pending = null
    }

    fun say(tamilText: String, englishText: String) {
        if (!ready) {
            pending = tamilText to englishText
            return
        }
        tts.speak(if (tamil) tamilText else englishText, TextToSpeech.QUEUE_FLUSH, null, "buddy")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        /** Phone silent / vibrate-ல இருந்தா பேசாது — மரியாதை! */
        fun soundAllowed(ctx: Context): Boolean =
            ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}

object Lines {
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
