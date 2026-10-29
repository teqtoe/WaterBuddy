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
    private const val K_CHAR = "character"
    private const val K_GLASS = "glass_ml"
    private const val K_GOAL = "goal_ml"
    private const val K_PAUSE = "pause_until"
    private const val K_MODEL = "model3d"
    private const val K_FACE_ON = "face_on"
    private const val K_PRESETS = "preset_lines"
    private const val K_CUSTOM = "custom_lines"
    private const val K_VSTYLE = "voice_style"
    private const val K_VNAME = "voice_name"
    private const val K_ONBOARDED = "onboarded"

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _masterOn = MutableStateFlow(true)
    val masterOn: StateFlow<Boolean> = _masterOn.asStateFlow()

    private val _voiceOn = MutableStateFlow(true)
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()

    /** தேர்ந்தெடுத்த கேரக்டர் — null = இன்னும் தேர்வு பண்ணல (onboarding காட்டு) */
    private val _character = MutableStateFlow<BuddyCharacter?>(null)
    val character: StateFlow<BuddyCharacter?> = _character.asStateFlow()

    private val _glassMl = MutableStateFlow(250)
    val glassMl: StateFlow<Int> = _glassMl.asStateFlow()

    private val _goalMl = MutableStateFlow(2500)
    val goalMl: StateFlow<Int> = _goalMl.asStateFlow()

    /** இந்த நேரம் வரை அலர்ட் pause (0 = pause இல்லை) */
    private val _pauseUntil = MutableStateFlow(0L)
    val pauseUntil: StateFlow<Long> = _pauseUntil.asStateFlow()

    // ── 3D கேரக்டர் & முகம்
    private val _model = MutableStateFlow(Model3D.MEERA)
    val model: StateFlow<Model3D> = _model.asStateFlow()
    private val _faceOn = MutableStateFlow(false)
    val faceOn: StateFlow<Boolean> = _faceOn.asStateFlow()
    /** முகம் மாறும்போதெல்லாம் +1 (படத்தை மறுபடி load பண்ண) */
    private val _faceVersion = MutableStateFlow(0)
    val faceVersion: StateFlow<Int> = _faceVersion.asStateFlow()

    // ── பேச்சு வரிகள்
    private val _presets = MutableStateFlow(setOf(0, 1, 2, 3, 4))
    val presets: StateFlow<Set<Int>> = _presets.asStateFlow()
    private val _custom = MutableStateFlow<List<String>>(emptyList())
    val custom: StateFlow<List<String>> = _custom.asStateFlow()

    // ── குரல்
    private val _voiceStyle = MutableStateFlow(VoiceStyle.NATURAL)
    val voiceStyle: StateFlow<VoiceStyle> = _voiceStyle.asStateFlow()
    private val _voiceName = MutableStateFlow<String?>(null)
    val voiceName: StateFlow<String?> = _voiceName.asStateFlow()

    private val _onboarded = MutableStateFlow(false)
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

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
            _character.value = BuddyCharacter.fromKey(p.getString(K_CHAR, null))
            _glassMl.value = p.getInt(K_GLASS, 250)
            _goalMl.value = p.getInt(K_GOAL, 2500)
            _pauseUntil.value = p.getLong(K_PAUSE, 0L)
            _model.value = Model3D.fromKey(p.getString(K_MODEL, null)) ?: Model3D.MEERA
            _faceOn.value = p.getBoolean(K_FACE_ON, false) && FaceMaker.file(ctx).exists()
            _presets.value = (p.getString(K_PRESETS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet())
                ?: setOf(0, 1, 2, 3, 4)
            _custom.value = try {
                val a = JSONArray(p.getString(K_CUSTOM, "[]")); (0 until a.length()).map { a.getString(it) }
            } catch (_: Exception) { emptyList() }
            _voiceStyle.value = VoiceStyle.fromKey(p.getString(K_VSTYLE, null))
            _voiceName.value = p.getString(K_VNAME, null)
            _onboarded.value = p.getBoolean(K_ONBOARDED, false)
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

    fun setModel(ctx: Context, m: Model3D) {
        _model.value = m
        prefs(ctx).edit().putString(K_MODEL, m.key).apply()
    }

    fun setFaceOn(ctx: Context, on: Boolean) {
        _faceOn.value = on && FaceMaker.file(ctx).exists()
        _faceVersion.value = _faceVersion.value + 1
        prefs(ctx).edit().putBoolean(K_FACE_ON, _faceOn.value).apply()
    }

    fun togglePreset(ctx: Context, i: Int) {
        val now = _presets.value
        val next = if (i in now) now - i else now + i
        _presets.value = next
        prefs(ctx).edit().putString(K_PRESETS, next.joinToString(",")).apply()
    }

    fun addCustom(ctx: Context, line: String) {
        val t = line.trim()
        if (t.isEmpty() || t in _custom.value) return
        saveCustom(ctx, _custom.value + t)
    }

    fun removeCustom(ctx: Context, line: String) = saveCustom(ctx, _custom.value - line)

    private fun saveCustom(ctx: Context, list: List<String>) {
        _custom.value = list
        prefs(ctx).edit().putString(K_CUSTOM, JSONArray(list).toString()).apply()
    }

    fun setVoiceStyle(ctx: Context, v: VoiceStyle) {
        _voiceStyle.value = v
        prefs(ctx).edit().putString(K_VSTYLE, v.key).apply()
    }

    fun setVoiceName(ctx: Context, name: String?) {
        _voiceName.value = name
        prefs(ctx).edit().putString(K_VNAME, name).apply()
    }

    fun setOnboarded(ctx: Context) {
        _onboarded.value = true
        prefs(ctx).edit().putBoolean(K_ONBOARDED, true).apply()
    }

    /** அலர்ட்ல சொல்லப்போற 5 வரிகள் (தேர்ந்தெடுத்தது + உங்க சொந்த வரிகள், கலந்து) */
    fun playlist(count: Int = 5): List<Pair<String, String>> {
        val pool = _presets.value.sorted().mapNotNull { Lines.presets.getOrNull(it) } +
            _custom.value.map { it to it }
        val base = pool.ifEmpty { Lines.presets.take(3) }.shuffled()
        return List(count) { base[it % base.size] }
    }

    fun setCharacter(ctx: Context, c: BuddyCharacter) {
        _character.value = c
        prefs(ctx).edit().putString(K_CHAR, c.key).apply()
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
        val key = todayKey()
        if (p.getString(K_DATE, null) != key) {
            saveStats(ctx, DayStats())
        } else {
            _today.value = DayStats(p.getInt(K_DRANK, 0), p.getInt(K_SKIPPED, 0), p.getInt(K_ML, 0))
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
