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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderStore.init(this)
        DebugLog.load(this)
        ReminderStore.seedDefaultsIfFirstRun(this)
        Notifier.ensureChannels(this)
        enableEdgeToEdge()
        setContent { WaterBuddyTheme { HomeScreen(resumeTick) } }
    }

    override fun onResume() {
        super.onResume()
        ReminderStore.refreshDay(this)
        AlarmScheduler.rescheduleAll(this, "app திறந்தது")   // permission மாறியிருந்தாலும் சரியா செட் ஆகும்
        resumeTick++
    }
}

private data class PermState(val notif: Boolean, val exact: Boolean, val fullScreen: Boolean, val battery: Boolean)

private fun checkPerms(ctx: Context): PermState {
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val full = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    return PermState(notif, exact, full, battery)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val reminders by ReminderStore.reminders.collectAsState()
    val masterOn by ReminderStore.masterOn.collectAsState()
    val voiceOn by ReminderStore.voiceOn.collectAsState()
    val stats by ReminderStore.today.collectAsState()
    val perms = remember(resumeTick) { checkPerms(ctx) }
    var showPicker by remember { mutableStateOf(false) }
    var poked by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // முதல் முறை திறக்கும்போதே notification permission கேக்கும்
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(poked) { if (poked) { delay(1800); poked = false } }

    val goalDone = stats.drank >= ReminderStore.DAILY_GOAL
    val mood = when {
        poked -> Mood.HAPPY
        !masterOn -> Mood.SLEEPY
        goalDone -> Mood.HAPPY
        else -> Mood.IDLE
    }
    val next = ReminderStore.nextUpcoming()
    val bubble = when {
        poked -> "ஹி ஹி! கிச்சுகிச்சு மூட்டாதீங்க 😆"
        !masterOn -> "அலர்ட் OFF… நான் கொஞ்சம் தூங்குறேன் 😴"
        goalDone -> "இன்னைக்கு goal முடிஞ்சது!\nநீங்க சூப்பர் 🎉"
        next != null -> "அடுத்த அலர்ட்: ${next.timeLabel()} ⏰\nநான் ஞாபகப்படுத்துறேன்!"
        else -> "ஒரு அலர்ட் நேரம் சேருங்க 👇"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("அலர்ட் சேர்") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header + Master ON/OFF
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Water Buddy 💧", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D2B3E))
                        Text("உங்க தண்ணீர் நண்பன்", color = Color(0xFF4A6572))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = masterOn,
                            onCheckedChange = {
                                ReminderStore.setMaster(ctx, it)
                                AlarmScheduler.rescheduleAll(ctx)
                            }
                        )
                        Text(if (masterOn) "ON" else "OFF", fontWeight = FontWeight.Bold,
                            color = if (masterOn) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            }

            // ── கேரக்டர் கார்டு
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFFE0F7FA))))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SpeechBubble(bubble)
                        WaterBuddy(
                            mood = mood,
                            glassFill = 0.8f,
                            modifier = Modifier
                                .height(230.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { poked = true }
                        )
                        Text(
                            "இன்னைக்கு ${stats.drank} / ${ReminderStore.DAILY_GOAL} கிளாஸ்",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0D2B3E)
                        )
                        Spacer(Modifier.height(6.dp))
                        GlassRow(stats.drank, ReminderStore.DAILY_GOAL)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = {
                            ReminderStore.recordDrink(ctx); poked = true
                        }) { Text("இப்போ குடிச்சேன் +1 💧") }
                    }
                }
            }

            // ── Permission எச்சரிக்கைகள் (தேவைப்பட்டா மட்டும் வரும்)
            if (!perms.notif) item {
                PermCard("Notification அனுமதி இல்லை", "இது இல்லாம அலர்ட் வராது. Allow பண்ணுங்க.") {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
            }
            if (!perms.exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
                PermCard("சரியான நேர அலர்ட்", "\"Alarms & reminders\" ON பண்ணா நிமிஷம் தவறாம வரும்.") {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
            }
            if (!perms.fullScreen && Build.VERSION.SDK_INT >= 34) item {
                PermCard("Lock screen-ல Buddy வர", "\"Full screen notifications\" ON பண்ணுங்க.") {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }
            }

            if (!perms.battery) item {
                PermCard("Battery கட்டுப்பாடு", "Phone தூங்கும்போது அலர்ட் தடுக்கப்படாம இருக்க \"Allow\" பண்ணுங்க.") {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }

            // ── அலர்ட் லிஸ்ட்
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("உங்க அலர்ட்ஸ்", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D2B3E), modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        var added = 0
                        for (h in 8..22 step 2) if (ReminderStore.add(ctx, h, 0) != null) added++
                        AlarmScheduler.rescheduleAll(ctx)
                        Toast.makeText(ctx, "$added அலர்ட் சேர்த்தாச்சு", Toast.LENGTH_SHORT).show()
                    }) { Text("⚡ 2 மணிக்கு ஒருமுறை") }
                }
            }
            if (reminders.isEmpty()) item {
                Text("இன்னும் அலர்ட் எதுவும் இல்லை. கீழே \"அலர்ட் சேர்\" அழுத்துங்க.", color = Color.Gray)
            }
            items(reminders, key = { it.id }) { r ->
                ReminderRow(
                    r = r,
                    masterOn = masterOn,
                    onToggle = {
                        ReminderStore.setEnabled(ctx, r.id, it)
                        AlarmScheduler.rescheduleAll(ctx)
                    },
                    onDelete = {
                        AlarmScheduler.cancel(ctx, r.id)
                        ReminderStore.remove(ctx, r.id)
                    }
                )
            }

            // ── Settings
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("🔊 Buddy குரல்", fontWeight = FontWeight.Bold)
                                Text("அலர்ட்ல பேசுவார் (silent mode-ல பேசமாட்டார்)", fontSize = 13.sp, color = Color.Gray)
                            }
                            Switch(checked = voiceOn, onCheckedChange = { ReminderStore.setVoice(ctx, it) })
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "🔋 பேட்டரி: background-ல எதுவும் ஓடாது. அலர்ட் நேரத்துல மட்டும் app ஒரு நொடி எழுந்து, மறுபடி தூங்கிடும்.",
                            fontSize = 13.sp, color = Color(0xFF4A6572)
                        )
                    }
                }
            }
            item { DiagnosticsCard(perms) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showPicker) {
        AddReminderDialog(
            onDismiss = { showPicker = false },
            onAdd = { h, m ->
                showPicker = false
                if (ReminderStore.add(ctx, h, m) == null) {
                    Toast.makeText(ctx, "இந்த நேரம் ஏற்கனவே இருக்கு 🙂", Toast.LENGTH_SHORT).show()
                } else {
                    AlarmScheduler.rescheduleAll(ctx)
                    Toast.makeText(ctx, "அலர்ட் சேர்த்தாச்சு ✓", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun ReminderRow(r: Reminder, masterOn: Boolean, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val active = r.enabled && masterOn
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color(0xFFEFF2F4)
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (active) "💧" else "💤", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.timeLabel(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                    color = if (active) Color(0xFF0D2B3E) else Color.Gray)
                Text(r.periodLabel(), fontSize = 13.sp, color = Color.Gray)
            }
            Switch(checked = r.enabled, onCheckedChange = onToggle, enabled = masterOn)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "நீக்கு", tint = Color(0xFF90A4AE))
            }
        }
    }
}

@Composable
private fun PermCard(title: String, body: String, onFix: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("⚠️ $title", fontWeight = FontWeight.Bold, color = Color(0xFF8D4B00))
                Text(body, fontSize = 13.sp, color = Color(0xFF6D4C41))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))) { Text("சரி செய்") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onAdd: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = false)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("எப்போ ஞாபகப்படுத்தணும்? ⏰", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("வேணாம்") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAdd(state.hour, state.minute) }) { Text("சேர்") }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(perms: PermState) {
    val ctx = LocalContext.current
    val log by DebugLog.lines.collectAsState()
    fun yn(b: Boolean) = if (b) "✅" else "❌"
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1))) {
        Column(Modifier.padding(16.dp)) {
            Text("🧪 டெஸ்ட் & Diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "Notif ${yn(perms.notif)}  Exact ${yn(perms.exact)}  FullScreen ${yn(perms.fullScreen)}  Battery ${yn(perms.battery)}",
                fontSize = 12.sp, color = Color(0xFF455A64)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { DebugLog.add(ctx, "▶ 'இப்போவே காட்டு' அழுத்தியாச்சு"); Notifier.showReminder(ctx) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("1️⃣ Notification இப்போவே காட்டு") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    AlarmScheduler.scheduleTest(ctx, 10)
                    Toast.makeText(ctx, "10 வினாடியில் அலர்ட் — phone-ஐ lock பண்ணுங்க! 🔒", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("2️⃣ Lock டெஸ்ட் (10 வினாடி)") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⚙️ App settings (Autostart / Battery)") }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log (புதுசு மேல)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { DebugLog.clear(ctx) }) { Text("அழி") }
            }
            if (log.isEmpty()) Text("இன்னும் எதுவும் இல்லை", fontSize = 12.sp, color = Color.Gray)
            log.takeLast(15).reversed().forEach {
                Text(it, fontSize = 11.sp, color = Color(0xFF263238), lineHeight = 14.sp)
            }
        }
    }
}
