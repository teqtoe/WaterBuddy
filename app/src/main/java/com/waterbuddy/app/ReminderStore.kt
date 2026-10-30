package com.waterbuddy.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** அலர்ட் வகைகள் */
enum class AlertKind(val key: String, val emoji: String, val label: String, val snoozeMin: Int) {
    WATER("water", "💧", "தண்ணீர்", 15),
    MEETING("meeting", "📅", "Meeting", 5),
    MEDICINE("medicine", "💊", "மாத்திரை", 10),
    BREAK("break", "🧘", "Break", 10),
    CUSTOM("custom", "⭐", "Custom", 10);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: WATER
    }
}

/**
 * ஒரு அலர்ட்.
 * days = வாரத்தின் நாட்கள் bitmask (bit0 = ஞாயிறு … bit6 = சனி). 0 என்றால் ஒரு தடவை மட்டும் ([onceDate] அன்று).
 * onceDate = yyyyMMdd (ஒரு தடவை அலர்ட்டுக்கு மட்டும்)
 */
data class Reminder(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val kind: AlertKind = AlertKind.WATER,
    val title: String = "",
    val message: String = "",
    val days: Int = ALL_DAYS,
    val onceDate: Int = 0
) {
    val minuteOfDay: Int get() = hour * 60 + minute
    val isOnce: Boolean get() = days == 0
    val displayTitle: String get() = title.ifBlank { kind.label }

    fun timeLabel(): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val ampm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", h12, minute, ampm)
    }

    fun repeatLabel(): String = when {
        isOnce -> {
            val y = onceDate / 10000; val m = onceDate / 100 % 100; val d = onceDate % 100
            val c = Calendar.getInstance().apply { set(y, m - 1, d) }
            "ஒரு தடவை · " + SimpleDateFormat("d MMM", Locale.US).format(c.time)
        }
        days == ALL_DAYS -> "தினமும்"
        days == WEEKDAYS -> "திங்கள் – வெள்ளி"
        else -> DAY_SHORT.indices.filter { days and (1 shl it) != 0 }.joinToString(" ") { DAY_SHORT[it] }
    }

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0111110
        val DAY_SHORT = listOf("ஞா", "தி", "செ", "பு", "வி", "வெ", "ச")
    }
}

data class DayStats(val drank: Int = 0, val skipped: Int = 0, val ml: Int = 0)

/**
 * எல்லா data-வும் SharedPreferences-ல சேமிக்கப்படுது.
 * StateFlow மூலமா UI உடனே update ஆகும்.
 */
object ReminderStore {
    val GLASS_OPTIONS = listOf(150, 200, 250, 300, 500)
    val GOAL_OPTIONS = listOf(1500, 2000, 2500, 3000, 3500)

    private const val PREFS = "water_buddy"
    private const val K_REMINDERS = "reminders"
    private const val K_NEXT_ID = "next_id"
    private const val K_MASTER = "master_on"
    private const val K_VOICE = "voice_on"
    private const val K_SEEDED = "seeded"
    private const val K_DATE = "stats_date"
    private const val K_DRANK = "drank"
    private const val K_SKIPPED = "skipped"
    private const val K_ML = "ml"
    private const val K_GLASS = "glass_ml"
    private const val K_GOAL = "goal_ml"
    private const val K_PAUSE = "pause_until"

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _masterOn = MutableStateFlow(true)
    val masterOn: StateFlow<Boolean> = _masterOn.asStateFlow()

    private val _voiceOn = MutableStateFlow(true)
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()

    private val _glassMl = MutableStateFlow(250)
    val glassMl: StateFlow<Int> = _glassMl.asStateFlow()

    private val _goalMl = MutableStateFlow(2500)
    val goalMl: StateFlow<Int> = _goalMl.asStateFlow()

    /** இந்த நேரம் வரை அலர்ட் pause (0 = pause இல்லை) */
    private val _pauseUntil = MutableStateFlow(0L)
    val pauseUntil: StateFlow<Long> = _pauseUntil.asStateFlow()

    private val _today = MutableStateFlow(DayStats())
    val today: StateFlow<DayStats> = _today.asStateFlow()

    @Volatile
    private var loaded = false

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun init(ctx: Context) {
        if (!loaded) {
            val p = prefs(ctx)
            _reminders.value = parse(p.getString(K_REMINDERS, "[]") ?: "[]")
            _masterOn.value = p.getBoolean(K_MASTER, true)
            _voiceOn.value = p.getBoolean(K_VOICE, true)
            _glassMl.value = p.getInt(K_GLASS, 250)
            _goalMl.value = p.getInt(K_GOAL, 2500)
            _pauseUntil.value = p.getLong(K_PAUSE, 0L)
            loaded = true
        }
        refreshDay(ctx)
    }

    /** முதல் முறை app திறக்கும்போது சில default தண்ணீர் அலர்ட்ஸ். */
    fun seedDefaultsIfFirstRun(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(K_SEEDED, false)) return
        p.edit().putBoolean(K_SEEDED, true).apply()
        listOf(9, 11, 13, 15, 17, 19).forEach { upsert(ctx, Reminder(0, it, 0, kind = AlertKind.WATER)) }
    }

    fun find(id: Int): Reminder? = _reminders.value.firstOrNull { it.id == id }

    /** id = 0 → புதுசு சேர்க்கும்; இல்லைனா அதே id-ஐ மாத்தும். சேமிச்ச அலர்ட்டை திருப்பி தரும். */
    @Synchronized
    fun upsert(ctx: Context, r: Reminder): Reminder {
        return if (r.id == 0) {
            val p = prefs(ctx)
            val id = p.getInt(K_NEXT_ID, 1)
            p.edit().putInt(K_NEXT_ID, id + 1).apply()
            val n = r.copy(id = id)
            save(ctx, _reminders.value + n)
            n
        } else {
            save(ctx, _reminders.value.map { if (it.id == r.id) r else it })
            r
        }
    }

    @Synchronized
    fun setEnabled(ctx: Context, id: Int, enabled: Boolean) {
        save(ctx, _reminders.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun remove(ctx: Context, id: Int) {
        save(ctx, _reminders.value.filterNot { it.id == id })
    }

    fun setMaster(ctx: Context, on: Boolean) {
        _masterOn.value = on
        prefs(ctx).edit().putBoolean(K_MASTER, on).apply()
    }

    fun setVoice(ctx: Context, on: Boolean) {
        _voiceOn.value = on
        prefs(ctx).edit().putBoolean(K_VOICE, on).apply()
    }

    fun setGlass(ctx: Context, ml: Int) {
        _glassMl.value = ml
        prefs(ctx).edit().putInt(K_GLASS, ml).apply()
    }

    fun setGoal(ctx: Context, ml: Int) {
        _goalMl.value = ml
        prefs(ctx).edit().putInt(K_GOAL, ml).apply()
    }

    fun pauseFor(ctx: Context, millis: Long) = setPauseUntil(ctx, System.currentTimeMillis() + millis)

    /** நாளை காலை 6 மணி வரை */
    fun pauseUntilTomorrow(ctx: Context) {
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        setPauseUntil(ctx, c.timeInMillis)
    }

    fun resume(ctx: Context) = setPauseUntil(ctx, 0L)

    fun isPaused(): Boolean = _pauseUntil.value > System.currentTimeMillis()

    private fun setPauseUntil(ctx: Context, t: Long) {
        _pauseUntil.value = t
        prefs(ctx).edit().putLong(K_PAUSE, t).apply()
    }

    fun recordDrink(ctx: Context) {
        refreshDay(ctx)
        val s = _today.value
        saveStats(ctx, s.copy(drank = s.drank + 1, ml = s.ml + _glassMl.value))
    }

    /** தவறா அழுத்தினா ஒரு கிளாஸ் குறைக்க */
    fun undoDrink(ctx: Context) {
        refreshDay(ctx)
        val s = _today.value
        if (s.drank <= 0) return
        saveStats(ctx, s.copy(drank = s.drank - 1, ml = (s.ml - _glassMl.value).coerceAtLeast(0)))
    }

    fun recordSkip(ctx: Context) {
        refreshDay(ctx)
        saveStats(ctx, _today.value.copy(skipped = _today.value.skipped + 1))
    }

    /** நாள் மாறியிருந்தா count-ஐ 0-ல இருந்து ஆரம்பிக்கும். */
    fun refreshDay(ctx: Context) {
        val p = prefs(ctx)
        if (p.getString(K_DATE, null) != todayKey()) {
            saveStats(ctx, DayStats())
        } else {
            _today.value = DayStats(p.getInt(K_DRANK, 0), p.getInt(K_SKIPPED, 0), p.getInt(K_ML, 0))
        }
    }

    /** அடுத்து வரப்போற அலர்ட் (ON-ல இருக்குறதுல). */
    fun nextUpcoming(): Pair<Reminder, Long>? {
        val now = System.currentTimeMillis()
        return _reminders.value.filter { it.enabled }
            .mapNotNull { r -> AlarmScheduler.nextTrigger(r, now)?.let { r to it } }
            .minByOrNull { it.second }
    }

    private fun saveStats(ctx: Context, s: DayStats) {
        _today.value = s
        prefs(ctx).edit()
            .putString(K_DATE, todayKey())
            .putInt(K_DRANK, s.drank)
            .putInt(K_SKIPPED, s.skipped)
            .putInt(K_ML, s.ml)
            .apply()
    }

    private fun save(ctx: Context, list: List<Reminder>) {
        val sorted = list.sortedBy { it.minuteOfDay }
        _reminders.value = sorted
        val arr = JSONArray()
        sorted.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("h", it.hour).put("m", it.minute).put("on", it.enabled)
                    .put("kind", it.kind.key).put("title", it.title).put("msg", it.message)
                    .put("days", it.days).put("once", it.onceDate)
            )
        }
        prefs(ctx).edit().putString(K_REMINDERS, arr.toString()).apply()
    }

    private fun parse(json: String): List<Reminder> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Reminder(
                id = o.getInt("id"), hour = o.getInt("h"), minute = o.getInt("m"),
                enabled = o.optBoolean("on", true),
                kind = AlertKind.fromKey(o.optString("kind", "water")),
                title = o.optString("title", ""), message = o.optString("msg", ""),
                days = o.optInt("days", Reminder.ALL_DAYS), onceDate = o.optInt("once", 0)
            )
        }.sortedBy { it.minuteOfDay }
    } catch (e: Exception) {
        emptyList()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
