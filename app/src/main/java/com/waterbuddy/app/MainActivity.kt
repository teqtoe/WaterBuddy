package com.waterbuddy.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderStore.init(this)
        DebugLog.load(this)
        Notifier.ensureChannels(this)
        enableEdgeToEdge()
        setContent { WaterBuddyTheme { HomeScreen(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        ReminderStore.refreshDay(this)
        AlarmScheduler.rescheduleAll(this, "app திறந்தது")
        resumeTick++
    }
}

private data class PermState(
    val notif: Boolean, val exact: Boolean, val fullScreen: Boolean, val battery: Boolean, val overlay: Boolean
)

private fun checkPerms(ctx: Context): PermState {
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val full = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    return PermState(notif, exact, full, battery, Settings.canDrawOverlays(ctx))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val reminders by ReminderStore.reminders.collectAsState()
    val masterOn by ReminderStore.masterOn.collectAsState()
    val voiceOn by ReminderStore.voiceOn.collectAsState()
    val stats by ReminderStore.today.collectAsState()
    val glassMl by ReminderStore.glassMl.collectAsState()
    val goalMl by ReminderStore.goalMl.collectAsState()
    val pauseUntil by ReminderStore.pauseUntil.collectAsState()
    val perms = remember(resumeTick) { checkPerms(ctx) }
    val paused = remember(pauseUntil, resumeTick) { pauseUntil > System.currentTimeMillis() }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var deleting by remember { mutableStateOf<Reminder?>(null) }
    val hasWater = reminders.any { it.kind == AlertKind.WATER }
    var poked by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(poked) { if (poked) { delay(1800); poked = false } }

    val next = remember(reminders, masterOn, resumeTick) { if (masterOn) ReminderStore.nextUpcoming() else null }
    val bubble = when {
        poked -> "ஹாய்! 👋 நான் உங்க Buddy!"
        !masterOn -> "அலர்ட் OFF… நான் ரெஸ்ட் எடுக்குறேன் 😴"
        next != null -> "அடுத்து: ${next.first.kind.emoji} ${next.first.displayTitle}\n${whenLabel(next.second)}"
        else -> "ஒரு அலர்ட் சேருங்க 👇"
    }

    Scaffold(
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Reminder(0, 10, 0, kind = AlertKind.CUSTOM, days = 0) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("புது அலர்ட்") },
                containerColor = Brand.Primary,
                contentColor = Color.White,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Header (எல்லா அலர்ட்டுக்கும் பொதுவானது)
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(Brand.HeaderGradient)
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Buddy Alerts", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("உங்க அலர்ட் நண்பன் · by ${Brand.COMPANY}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        Switch(
                            checked = masterOn,
                            onCheckedChange = {
                                ReminderStore.setMaster(ctx, it)
                                AlarmScheduler.rescheduleAll(ctx, if (it) "ON" else "OFF")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Brand.Primary, checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            SpeechBubble(bubble)
                            Text(
                                "${reminders.count { it.enabled }} அலர்ட் ON",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        BuddySprite(
                            if (!masterOn) Pose.SADWALK else Pose.CHEER,
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null
                            ) { poked = true },
                            scale = 0.3f
                        )
                    }
                }
            }

            // ── அனுமதிகள் (இல்லாதது மட்டும்)
            if (!perms.overlay) item {
                PermCard("Buddy திரையில் வர அனுமதி", "\"Display over other apps\" ON பண்ணுங்க. இது இல்லாம Buddy ஓடி வர முடியாது.") {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
            }
            if (!perms.notif) item {
                PermCard("Lock screen அனுமதி", "Phone lock-ல இருக்கும்போது Buddy வர இது தேவை (popup எதுவும் வராது).") {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            }
            if (!perms.fullScreen && Build.VERSION.SDK_INT >= 34) item {
                PermCard("Lock screen-ல முழு திரை", "\"Full screen notifications\" ON பண்ணுங்க.") {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }
            }
            if (!perms.exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                PermCard("சரியான நேரம்", "\"Alarms & reminders\" ON பண்ணா நிமிஷம் தவறாம வரும்.") {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
            }
            if (!perms.battery) item {
                PermCard("Battery கட்டுப்பாடு", "Phone தூங்கும்போது அலர்ட் தடுக்கப்படாம இருக்க \"Allow\" பண்ணுங்க.") {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }

            // ── அலர்ட் லிஸ்ட்
            item {
                Text(
                    "⏰ என் அலர்ட்ஸ்", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Brand.Ink,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (reminders.isEmpty()) item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Text("இன்னும் அலர்ட் இல்லை", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                    Text("கீழே \"புது அலர்ட்\" அழுத்தி Meeting, மாத்திரை, தண்ணீர்… எதுவும் சேர்க்கலாம்.", fontSize = 13.sp, color = Brand.Muted)
                }
            }
            items(reminders, key = { it.id }) { r ->
                ReminderRow(
                    r = r, masterOn = masterOn,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { editing = r },
                    onDelete = { deleting = r },
                    onToggle = {
                        var nr = r.copy(enabled = it)
                        // கடந்து போன ஒரு தடவை அலர்ட்டை ON பண்ணா → அடுத்த அதே நேரத்துக்கு
                        if (it && nr.isOnce && AlarmScheduler.nextTrigger(nr, System.currentTimeMillis()) == null) {
                            nr = nr.copy(onceDate = autoDate(nr.hour, nr.minute))
                        }
                        ReminderStore.upsert(ctx, nr)
                        AlarmScheduler.rescheduleAll(ctx)
                    }
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { editing = Reminder(0, 10, 0, kind = AlertKind.MEETING, days = 0) },
                        label = { Text("📅 Meeting") }
                    )
                    AssistChip(
                        onClick = { editing = Reminder(0, 9, 0, kind = AlertKind.MEDICINE) },
                        label = { Text("💊 மாத்திரை") }
                    )
                    if (!hasWater) AssistChip(
                        onClick = {
                            for (h in 9..21 step 2) ReminderStore.upsert(ctx, Reminder(0, h, 0, kind = AlertKind.WATER))
                            AlarmScheduler.rescheduleAll(ctx, "water preset")
                            Toast.makeText(ctx, "💧 தண்ணீர் அலர்ட்ஸ் சேர்த்தாச்சு", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("💧 தண்ணீர்") }
                    )
                }
            }

            // ── 💧 தண்ணீர் tracker (தண்ணீர் அலர்ட் இருந்தா மட்டும்)
            if (hasWater) item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaterProgress(drankMl = stats.ml, goalMl = goalMl, size = 96.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("💧 இன்னைக்கு தண்ணீர்", fontWeight = FontWeight.Bold, color = Brand.Ink)
                            Text("${stats.drank} கிளாஸ் · ${stats.skipped} தடவை அப்புறம்", fontSize = 12.sp, color = Brand.Muted)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { ReminderStore.recordDrink(ctx); poked = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Brand.WaterDeep)
                                ) { Text("+$glassMl ml") }
                                OutlinedButton(
                                    onClick = { ReminderStore.undoDrink(ctx) }, enabled = stats.drank > 0,
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) { Text("↶") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("கிளாஸ் அளவு", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReminderStore.GLASS_OPTIONS.forEach { ml ->
                            FilterChip(selected = glassMl == ml, onClick = { ReminderStore.setGlass(ctx, ml) }, label = { Text("$ml ml") })
                        }
                    }
                    Text("தினசரி goal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReminderStore.GOAL_OPTIONS.forEach { ml ->
                            FilterChip(
                                selected = goalMl == ml, onClick = { ReminderStore.setGoal(ctx, ml) },
                                label = { Text(String.format(Locale.US, "%.1f L", ml / 1000f)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (paused) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⏸ ${SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(pauseUntil))} வரை pause",
                                fontSize = 13.sp, color = Brand.Accent, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { ReminderStore.resume(ctx) }) { Text("Resume ▶") }
                        }
                    } else {
                        Text("⏸ தண்ணீர் அலர்ட் மட்டும் நிறுத்த", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(onClick = { ReminderStore.pauseFor(ctx, 60 * 60_000L) }, label = { Text("1 மணி") })
                            AssistChip(onClick = { ReminderStore.pauseFor(ctx, 3 * 60 * 60_000L) }, label = { Text("3 மணி") })
                            AssistChip(onClick = { ReminderStore.pauseUntilTomorrow(ctx) }, label = { Text("நாளை காலை வரை") })
                        }
                    }
                }
            }

            // ── Settings
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🔊 Buddy குரல் & இசை", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                            Text("Silent / vibrate mode-ல சத்தம் வராது", fontSize = 12.sp, color = Brand.Muted)
                        }
                        Switch(checked = voiceOn, onCheckedChange = { ReminderStore.setVoice(ctx, it) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🔋 Background-ல எதுவும் ஓடாது. அலர்ட் நேரத்துல மட்டும் app ஒரு நொடி எழுந்து, மறுபடி தூங்கிடும்.",
                        fontSize = 12.sp, color = Brand.Muted
                    )
                }
            }

            item { DiagnosticsCard(perms, Modifier.padding(horizontal = 16.dp)) }
            item { TeqtoeFooter(Modifier.navigationBarsPadding().padding(bottom = 72.dp)) }
        }
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("அலர்ட் நீக்கவா?") },
            text = { Text("${r.kind.emoji} ${r.displayTitle} · ${r.timeLabel()} · ${r.repeatLabel()}") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(ctx, r.id)
                    ReminderStore.remove(ctx, r.id)
                    deleting = null
                    Toast.makeText(ctx, "நீக்கியாச்சு 🗑", Toast.LENGTH_SHORT).show()
                }) { Text("நீக்கு", color = Brand.Accent) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("வேணாம்") } }
        )
    }

    editing?.let { r ->
        AlertEditor(
            initial = r,
            onDismiss = { editing = null },
            onSave = { nr ->
                editing = null
                val saved = ReminderStore.upsert(ctx, nr)
                AlarmScheduler.rescheduleAll(ctx, "அலர்ட் சேமிப்பு")
                val at = AlarmScheduler.nextTrigger(saved, System.currentTimeMillis())
                Toast.makeText(ctx, if (at != null) "சேமிச்சாச்சு ✓ ${whenLabel(at)}" else "சேமிச்சாச்சு (நேரம் கடந்துடுச்சு)", Toast.LENGTH_SHORT).show()
            },
            onDelete = { id ->
                editing = null
                AlarmScheduler.cancel(ctx, id)
                ReminderStore.remove(ctx, id)
            }
        )
    }
}

/** "இன்னைக்கு 3:00 PM" / "நாளை 9:00 AM" / "5 Nov, 10:00 AM" */
fun whenLabel(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    val tom = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(t))
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(c, today) -> "இன்னைக்கு $time"
        same(c, tom) -> "நாளை $time"
        else -> SimpleDateFormat("d MMM, ", Locale.US).format(Date(t)) + time
    }
}

/** நேரம் இன்னைக்கு கடந்துடுச்சுன்னா நாளை, இல்லைனா இன்னைக்கு (yyyyMMdd) */
fun autoDate(hour: Int, minute: Int): Int {
    val c = Calendar.getInstance()
    val now = c.timeInMillis
    c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0)
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

private enum class RepeatMode(val label: String) { ONCE("ஒரு தடவை"), DAILY("தினமும்"), WEEKDAYS("தி–வெ"), CUSTOM("நாட்கள்") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlertEditor(initial: Reminder, onDismiss: () -> Unit, onSave: (Reminder) -> Unit, onDelete: (Int) -> Unit) {
    var kind by remember { mutableStateOf(initial.kind) }
    var title by remember { mutableStateOf(initial.title) }
    var message by remember { mutableStateOf(initial.message) }
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    var mode by remember {
        mutableStateOf(
            when (initial.days) {
                0 -> RepeatMode.ONCE
                Reminder.ALL_DAYS -> RepeatMode.DAILY
                Reminder.WEEKDAYS -> RepeatMode.WEEKDAYS
                else -> RepeatMode.CUSTOM
            }
        )
    }
    var days by remember { mutableIntStateOf(if (initial.days == 0) Reminder.WEEKDAYS else initial.days) }
    var onceDate by remember { mutableIntStateOf(initial.onceDate) }   // 0 = தானா (இன்னைக்கு/நாளை)
    var showDate by remember { mutableStateOf(false) }
    val isNew = initial.id == 0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White, modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text(if (isNew) "புது அலர்ட்" else "அலர்ட் மாத்து", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text("${k.emoji} ${k.label}") })
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(40) }, singleLine = true,
                    label = { Text("பெயர்") }, placeholder = { Text(kind.label) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(140) },
                    label = { Text("Buddy என்ன சொல்லணும்?") },
                    placeholder = {
                        Text(if (kind == AlertKind.WATER) "காலியா விட்டா தண்ணீர் வரிகள் மாத்தி மாத்தி சொல்லுவார்" else AlertFlow.defaultMessage(kind))
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2
                )
                Spacer(Modifier.height(12.dp))
                Text("நேரம்", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state = timeState) }

                Text("எப்போ?", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                }
                when (mode) {
                    RepeatMode.ONCE -> {
                        val d = if (onceDate != 0) onceDate else autoDate(timeState.hour, timeState.minute)
                        val c = Calendar.getInstance().apply { set(d / 10000, d / 100 % 100 - 1, d % 100) }
                        OutlinedButton(onClick = { showDate = true }) {
                            Text("📆 " + SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(c.time) + if (onceDate == 0) " (தானா)" else "")
                        }
                    }
                    RepeatMode.CUSTOM -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Reminder.DAY_SHORT.forEachIndexed { i, label ->
                                val on = days and (1 shl i) != 0
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (on) Brand.Primary else Color(0xFFE6ECF7))
                                        .clickable { days = days xor (1 shl i) },
                                    contentAlignment = Alignment.Center
                                ) { Text(label, color = if (on) Color.White else Brand.Ink, fontSize = 13.sp) }
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isNew) TextButton(onClick = { onDelete(initial.id) }) { Text("🗑 நீக்கு", color = Brand.Accent) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("வேணாம்") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        enabled = mode != RepeatMode.CUSTOM || days != 0,
                        onClick = {
                            val h = timeState.hour; val m = timeState.minute
                            val d = when (mode) {
                                RepeatMode.ONCE -> 0
                                RepeatMode.DAILY -> Reminder.ALL_DAYS
                                RepeatMode.WEEKDAYS -> Reminder.WEEKDAYS
                                RepeatMode.CUSTOM -> days
                            }
                            val od = if (mode == RepeatMode.ONCE) (if (onceDate != 0) onceDate else autoDate(h, m)) else 0
                            onSave(
                                initial.copy(
                                    hour = h, minute = m, enabled = true, kind = kind,
                                    title = title.trim(), message = message.trim(), days = d, onceDate = od
                                )
                            )
                        }
                    ) { Text("சேமி ✓") }
                }
            }
        }
    }

    if (showDate) {
        val init = (if (onceDate != 0) onceDate else autoDate(timeState.hour, timeState.minute)).let { d ->
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(d / 10000, d / 100 % 100 - 1, d % 100) }.timeInMillis
        }
        val ds = rememberDatePickerState(initialSelectedDateMillis = init)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { ms ->
                        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                        onceDate = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
                    }
                    showDate = false
                }) { Text("சரி") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("வேணாம்") } }
        ) { DatePicker(state = ds) }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Brand.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun ReminderRow(
    r: Reminder, masterOn: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit, onDelete: () -> Unit, onToggle: (Boolean) -> Unit
) {
    val active = r.enabled && masterOn
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) Brand.Card else Color(0xFFE9EDF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 1.dp else 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (active) Brand.Primary.copy(alpha = 0.12f) else Color(0xFFDDE3EE)),
                contentAlignment = Alignment.Center
            ) { Text(r.kind.emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.timeLabel(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = if (active) Brand.Ink else Brand.Muted)
                Text(
                    "${r.displayTitle} · ${r.repeatLabel()}${if (r.isOnce && !r.enabled) " · முடிந்தது" else ""}",
                    fontSize = 12.sp, color = Brand.Muted, maxLines = 1
                )
            }
            Switch(checked = r.enabled, onCheckedChange = onToggle, enabled = masterOn)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "நீக்கு", tint = Color(0xFFB0AEC4))
            }
        }
    }
}

@Composable
private fun PermCard(title: String, body: String, onFix: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1EA))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("⚠️ $title", fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                Text(body, fontSize = 13.sp, color = Color(0xFF7C4A35))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = Brand.Accent)) { Text("சரி செய்") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsCard(perms: PermState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val log by DebugLog.lines.collectAsState()
    var open by remember { mutableStateOf(false) }
    fun yn(b: Boolean) = if (b) "✅" else "❌"
    SectionCard(modifier) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("🧪 டெஸ்ட் & Diagnostics", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Brand.Ink, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = Brand.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Text("10 வினாடியில் Buddy வருவார் — phone-ஐ lock பண்ணியும் பாக்கலாம் 🔒", fontSize = 12.sp, color = Brand.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                AlarmScheduler.scheduleTest(ctx, AlarmScheduler.TEST_WATER, 10)
                Toast.makeText(ctx, "10 வினாடியில் 💧", Toast.LENGTH_SHORT).show()
            }) { Text("💧 டெஸ்ட்") }
            OutlinedButton(onClick = {
                AlarmScheduler.scheduleTest(ctx, AlarmScheduler.TEST_MEETING, 10)
                Toast.makeText(ctx, "10 வினாடியில் 📅", Toast.LENGTH_SHORT).show()
            }) { Text("📅 Meeting டெஸ்ட்") }
            Button(onClick = {
                if (Settings.canDrawOverlays(ctx)) OverlayService.start(ctx, AlarmScheduler.TEST_WATER)
                else ctx.startActivity(Intent(ctx, ReminderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("👀 இப்போவே") }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "Overlay ${yn(perms.overlay)}  Notif ${yn(perms.notif)}  Exact ${yn(perms.exact)}  FullScreen ${yn(perms.fullScreen)}  Battery ${yn(perms.battery)}",
                fontSize = 12.sp, color = Brand.Muted
            )
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⚙️ App settings (Autostart / Battery)") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log (புதுசு மேல)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { DebugLog.clear(ctx) }) { Text("அழி") }
            }
            if (log.isEmpty()) Text("இன்னும் எதுவும் இல்லை", fontSize = 12.sp, color = Brand.Muted)
            log.takeLast(15).reversed().forEach { Text(it, fontSize = 11.sp, color = Brand.Ink, lineHeight = 14.sp) }
        }
    }
}
