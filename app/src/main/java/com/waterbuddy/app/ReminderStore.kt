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

/** ஒரு அலர்ட் நேரம் (தினமும் repeat ஆகும்). */
data class Reminder(val id: Int, val hour: Int, val minute: Int, val enabled: Boolean = true) {
    val minuteOfDay: Int get() = hour * 60 + minute

    fun timeLabel(): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val ampm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", h12, minute, ampm)
    }

    fun periodLabel(): String = when (hour) {
        in 4..11 -> "காலை"
        in 12..15 -> "மதியம்"
        in 16..18 -> "மாலை"
        else -> "இரவு"
    }
}

data class DayStats(val drank: Int = 0, val skipped: Int = 0)

/**
 * எல்லா data-வும் SharedPreferences-ல சேமிக்கப்படுது.
 * StateFlow மூலமா UI உடனே update ஆகும்.
 */
object ReminderStore {
    const val DAILY_GOAL = 8

    private const val PREFS = "water_buddy"
    private const val K_REMINDERS = "reminders"
    private const val K_NEXT_ID = "next_id"
    private const val K_MASTER = "master_on"
    private const val K_VOICE = "voice_on"
    private const val K_SEEDED = "seeded"
    private const val K_DATE = "stats_date"
    private const val K_DRANK = "drank"
    private const val K_SKIPPED = "skipped"

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _masterOn = MutableStateFlow(true)
    val masterOn: StateFlow<Boolean> = _masterOn.asStateFlow()

    private val _voiceOn = MutableStateFlow(true)
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()

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
            loaded = true
        }
        refreshDay(ctx)
    }

    /** முதல் முறை app திறக்கும்போது சில default அலர்ட்ஸ் சேர்க்க. */
    fun seedDefaultsIfFirstRun(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(K_SEEDED, false)) return
        p.edit().putBoolean(K_SEEDED, true).apply()
        listOf(9, 11, 13, 15, 17, 19).forEach { add(ctx, it, 0) }
    }

    fun find(id: Int): Reminder? = _reminders.value.firstOrNull { it.id == id }

    /** புதிய நேரம் சேர்க்கும். அதே நேரம் ஏற்கனவே இருந்தா null. */
    @Synchronized
    fun add(ctx: Context, hour: Int, minute: Int): Reminder? {
        if (_reminders.value.any { it.hour == hour && it.minute == minute }) return null
        val p = prefs(ctx)
        val id = p.getInt(K_NEXT_ID, 1)
        p.edit().putInt(K_NEXT_ID, id + 1).apply()
        val r = Reminder(id, hour, minute, true)
        save(ctx, _reminders.value + r)
        return r
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

    fun recordDrink(ctx: Context) {
        refreshDay(ctx)
        saveStats(ctx, _today.value.copy(drank = _today.value.drank + 1))
    }

    fun recordSkip(ctx: Context) {
        refreshDay(ctx)
        saveStats(ctx, _today.value.copy(skipped = _today.value.skipped + 1))
    }

    /** நாள் மாறியிருந்தா count-ஐ 0-ல இருந்து ஆரம்பிக்கும். */
    fun refreshDay(ctx: Context) {
        val p = prefs(ctx)
        val key = todayKey()
        if (p.getString(K_DATE, null) != key) {
            saveStats(ctx, DayStats())
        } else {
            _today.value = DayStats(p.getInt(K_DRANK, 0), p.getInt(K_SKIPPED, 0))
        }
    }

    /** அடுத்து வரப்போற அலர்ட் (ON-ல இருக்குறதுல). */
    fun nextUpcoming(): Reminder? {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return _reminders.value.filter { it.enabled }
            .minByOrNull { r -> ((r.minuteOfDay - now) + 1440 - 1) % 1440 }
    }

    private fun saveStats(ctx: Context, s: DayStats) {
        _today.value = s
        prefs(ctx).edit()
            .putString(K_DATE, todayKey())
            .putInt(K_DRANK, s.drank)
            .putInt(K_SKIPPED, s.skipped)
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
            )
        }
        prefs(ctx).edit().putString(K_REMINDERS, arr.toString()).apply()
    }

    private fun parse(json: String): List<Reminder> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Reminder(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optBoolean("on", true))
        }.sortedBy { it.minuteOfDay }
    } catch (e: Exception) {
        emptyList()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
