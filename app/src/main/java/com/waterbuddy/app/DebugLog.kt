package com.waterbuddy.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-க்குள்ளயே ஒரு சின்ன log. அலர்ட் செட் ஆச்சா, Android எழுப்புச்சா,
 * notification போச்சா — எல்லாம் இங்க பதிவாகும். Diagnostics கார்டுல தெரியும்.
 */
object DebugLog {
    private const val PREFS = "water_buddy_log"
    private const val KEY = "lines"
    private const val MAX = 40

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        val raw = prefs(ctx).getString(KEY, "") ?: ""
        _lines.value = raw.split('\n').filter { it.isNotBlank() }
        loaded = true
    }

    fun load(ctx: Context) = ensure(ctx)

    @Synchronized
    fun add(ctx: Context, msg: String) {
        ensure(ctx)
        val ts = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        val list = (_lines.value + "$ts  $msg").takeLast(MAX)
        _lines.value = list
        prefs(ctx).edit().putString(KEY, list.joinToString("\n")).commit()
    }

    @Synchronized
    fun clear(ctx: Context) {
        _lines.value = emptyList()
        prefs(ctx).edit().remove(KEY).commit()
    }
}
