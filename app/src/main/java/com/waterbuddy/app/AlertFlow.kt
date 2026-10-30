package com.waterbuddy.app

import android.content.Context

/** அலர்ட் வந்ததும் Buddy என்ன சொல்லணும், பதிலுக்கு என்ன பண்ணணும் — எல்லாம் இங்க. */
object AlertFlow {

    /** id-க்கு உரிய அலர்ட் (டெஸ்ட் id-களுக்கு மாதிரி அலர்ட்) */
    fun resolve(id: Int): Reminder = when (id) {
        AlarmScheduler.TEST_MEETING -> Reminder(
            id, 10, 0, kind = AlertKind.MEETING, title = "Team meeting",
            message = "இன்னும் 5 நிமிஷத்துல meeting ஆரம்பிக்குது!"
        )
        AlarmScheduler.TEST_WATER -> Reminder(id, 10, 0, kind = AlertKind.WATER)
        else -> ReminderStore.find(id) ?: Reminder(id, 10, 0, kind = AlertKind.WATER)
    }

    /** தொடர்ந்து சொல்லும் 4–5 வரிகள் */
    fun spokenLines(r: Reminder): List<Pair<String, String>> {
        if (r.kind == AlertKind.WATER && r.message.isBlank()) {
            return Lines.presets.shuffled().take(5)
        }
        val title = r.displayTitle
        val intro = "ஹலோ! $title நேரம் வந்தாச்சு!" to "Hey! It's time for $title!"
        val msg = r.message.ifBlank { defaultMessage(r.kind) }
        val body = msg to msg
        val nudge = "ப்ளீஸ், மறந்துடாதீங்க!" to "Please don't forget!"
        return listOf(intro, body, nudge, body)
    }

    fun defaultMessage(k: AlertKind): String = when (k) {
        AlertKind.WATER -> "ஒரு கிளாஸ் தண்ணி குடிங்க!"
        AlertKind.MEETING -> "Meeting-க்கு ரெடி ஆகுங்க!"
        AlertKind.MEDICINE -> "மாத்திரை சாப்பிடுற நேரம்!"
        AlertKind.BREAK -> "கொஞ்சம் எழுந்து நடந்துட்டு வாங்க!"
        AlertKind.CUSTOM -> "உங்க அலர்ட் நேரம் வந்தாச்சு!"
    }

    /** பதில் பட்டன்களோட எழுத்து: (ஆமா, அப்புறம்) */
    fun buttonLabels(r: Reminder): Pair<String, String> = when (r.kind) {
        AlertKind.WATER -> "💧 குடிச்சேன்" to "⏰ அப்புறம்"
        AlertKind.MEDICINE -> "✓ சாப்பிட்டேன்" to "⏰ ${r.kind.snoozeMin} நிமி"
        else -> "✓ சரி" to "⏰ ${r.kind.snoozeMin} நிமி"
    }

    fun reactionBubble(r: Reminder, positive: Boolean): String = when {
        positive && r.kind == AlertKind.WATER -> Lines.happyBubbles.random()
        positive -> "சூப்பர்! 👍"
        else -> "சரி… ${r.kind.snoozeMin} நிமிஷத்துல மறுபடி வர்றேன் 🥺"
    }

    fun reactionSpoken(r: Reminder, positive: Boolean): Pair<String, String> = when {
        positive && r.kind == AlertKind.WATER -> Lines.HAPPY_TA to Lines.HAPPY_EN
        positive -> "சூப்பர்!" to "Great!"
        else -> "சரி, ${r.kind.snoozeMin} நிமிஷத்துல மறுபடி வர்றேன்." to "Okay, I'll be back in ${r.kind.snoozeMin} minutes."
    }

    /** பதிலை பதிவு பண்ணும். அப்புறம்னா snooze. */
    fun onAnswer(ctx: Context, r: Reminder, positive: Boolean) {
        if (r.kind == AlertKind.WATER) {
            if (positive) ReminderStore.recordDrink(ctx) else ReminderStore.recordSkip(ctx)
        }
        if (!positive) AlarmScheduler.scheduleSnooze(ctx, r.id, r.kind.snoozeMin)
        DebugLog.add(ctx, "${r.kind.emoji} ${r.displayTitle}: ${if (positive) "✓" else "அப்புறம்"}")
    }
}
