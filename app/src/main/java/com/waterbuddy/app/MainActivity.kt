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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderStore.init(this)
        DebugLog.load(this)
        ReminderStore.seedDefaultsIfFirstRun(this)
        Notifier.ensureChannels(this)
        enableEdgeToEdge()
        setContent {
            WaterBuddyTheme {
                val onboarded by ReminderStore.onboarded.collectAsState()
                var section by remember { mutableStateOf<Section?>(null) }
                when {
                    !onboarded -> SetupFlow(onDone = { ReminderStore.setOnboarded(this) })
                    section != null -> SettingsPage(section!!, onClose = { section = null })
                    else -> HomeScreen(resumeTick, onOpenSection = { section = it })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ReminderStore.refreshDay(this)
        AlarmScheduler.rescheduleAll(this, "app திறந்தது")
        resumeTick++
    }
}

// ═══════════════════════ Home ═══════════════════════

private data class PermState(val notif: Boolean, val exact: Boolean, val fullScreen: Boolean, val battery: Boolean, val overlay: Boolean)

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
fun HomeScreen(resumeTick: Int, onOpenSection: (Section) -> Unit) {
    val ctx = LocalContext.current
    val reminders by ReminderStore.reminders.collectAsState()
    val masterOn by ReminderStore.masterOn.collectAsState()
    val voiceOn by ReminderStore.voiceOn.collectAsState()
    val stats by ReminderStore.today.collectAsState()
    val glassMl by ReminderStore.glassMl.collectAsState()
    val goalMl by ReminderStore.goalMl.collectAsState()
    val pauseUntil by ReminderStore.pauseUntil.collectAsState()
    val model by ReminderStore.model.collectAsState()
    val faceOn by ReminderStore.faceOn.collectAsState()
    val faceVersion by ReminderStore.faceVersion.collectAsState()
    val face = remember(faceOn, faceVersion) { if (faceOn) FaceMaker.load(ctx) else null }
    val perms = remember(resumeTick) { checkPerms(ctx) }
    val paused = remember(pauseUntil, resumeTick) { pauseUntil > System.currentTimeMillis() }
    var showPicker by remember { mutableStateOf(false) }
    var poked by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(poked) { if (poked) { delay(2200); poked = false } }

    val goalDone = stats.ml >= goalMl
    val mood = when {
        poked -> Mood.DANCING
        !masterOn || paused -> Mood.SLEEPY
        goalDone -> Mood.HAPPY
        else -> Mood.IDLE
    }
    val next = ReminderStore.nextUpcoming()
    val bubble = when {
        poked -> "யேய்! 💃 நீங்க குடிச்சா நான் ஆடுவேன்!"
        !masterOn -> "அலர்ட் OFF… நான் தூங்குறேன் 😴"
        paused -> "Pause-ல இருக்கேன் ⏸ கொஞ்சம் ரெஸ்ட்!"
        goalDone -> "இன்னைக்கு goal முடிஞ்சது! 🎉"
        next != null -> "அடுத்த dance: ${next.timeLabel()} 💃"
        else -> "ஒரு அலர்ட் நேரம் சேருங்க 👇"
    }

    Scaffold(
        containerColor = Brand.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("அலர்ட் சேர்") },
                containerColor = Brand.Primary,
                contentColor = Color.White,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header: brand + progress + கேரக்டர்
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(Brand.HeaderGradient)
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Water Buddy 💧", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("by ${Brand.COMPANY}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            Text(if (masterOn) "ON" else "OFF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SpeechBubble(bubble, Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WaterProgress(drankMl = stats.ml, goalMl = goalMl, size = 150.dp, modifier = Modifier.weight(1f))
                        Buddy3D(
                            model = model,
                            action = when (mood) {
                                Mood.DANCING -> Action3D.DANCE
                                Mood.HAPPY -> Action3D.YES
                                Mood.SLEEPY -> Action3D.SAD
                                else -> Action3D.IDLE
                            },
                            face = face,
                            modifier = Modifier
                                .size(150.dp, 210.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { poked = true },
                            fallback = { WaterBuddy(mood, BuddyCharacter.KUTTY, Modifier.height(200.dp)) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { ReminderStore.recordDrink(ctx); poked = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Primary)
                        ) { Text("+ $glassMl ml குடிச்சேன்", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        OutlinedButton(
                            onClick = { ReminderStore.undoDrink(ctx) },
                            enabled = stats.drank > 0,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = if (stats.drank > 0) 1f else 0.4f))
                        ) { Text("↶", fontSize = 20.sp, color = Color.White) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "இன்னைக்கு ${stats.drank} கிளாஸ் · ${stats.skipped} தடவை அப்புறம்ன்னு சொன்னீங்க",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }

            // ── Pause
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    if (paused) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("⏸ அலர்ட் pause-ல இருக்கு", fontWeight = FontWeight.Bold, color = Brand.Accent)
                                Text(
                                    "${SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(pauseUntil))} வரை",
                                    fontSize = 13.sp, color = Brand.Muted
                                )
                            }
                            Button(onClick = { ReminderStore.resume(ctx) }) { Text("Resume ▶") }
                        }
                    } else {
                        Text("⏸ கொஞ்ச நேரம் தொந்தரவு வேணாமா?", fontWeight = FontWeight.Bold, color = Brand.Ink)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { ReminderStore.pauseFor(ctx, 60 * 60_000L) }, label = { Text("1 மணி நேரம்") })
                            AssistChip(onClick = { ReminderStore.pauseFor(ctx, 3 * 60 * 60_000L) }, label = { Text("3 மணி நேரம்") })
                            AssistChip(onClick = { ReminderStore.pauseUntilTomorrow(ctx) }, label = { Text("நாளை காலை வரை") })
                        }
                    }
                }
            }

            // ── Permission எச்சரிக்கைகள்
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
            if (!perms.overlay) item {
                PermCard("Buddy ஓடி வர அனுமதி", "\"Display over other apps\" ON பண்ணா, phone use பண்ணும்போதும் எந்த app மேலயும் ஓடி வருவாங்க.") {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
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
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰ அலர்ட் நேரங்கள்", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = Brand.Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        var added = 0
                        for (h in 9..21) if (ReminderStore.add(ctx, h, 0) != null) added++
                        AlarmScheduler.rescheduleAll(ctx, "hourly preset")
                        Toast.makeText(ctx, "$added அலர்ட் சேர்த்தாச்சு", Toast.LENGTH_SHORT).show()
                    }) { Text("⚡ மணிக்கு ஒருமுறை") }
                }
            }
            if (reminders.isEmpty()) item {
                Text(
                    "இன்னும் அலர்ட் இல்லை. \"அலர்ட் சேர்\" அழுத்துங்க.",
                    color = Brand.Muted, modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(reminders, key = { it.id }) { r ->
                ReminderRow(
                    r = r,
                    masterOn = masterOn,
                    modifier = Modifier.padding(horizontal = 16.dp),
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
                SectionCard(Modifier.padding(horizontal = 16.dp)) {
                    Text("⚙️ Settings", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                    Spacer(Modifier.height(12.dp))

                    Section.entries.forEach { sec ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenSection(sec) }
                                .background(Color(0xFFF1F5FD))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sec.title, fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                                Text(
                                    when (sec) {
                                        Section.BUDDY -> "${model.displayName}${if (faceOn) " · உங்க முகத்தோட 📸" else ""}"
                                        Section.LINES -> "${ReminderStore.presets.value.size + ReminderStore.custom.value.size} வரிகள் தேர்வு"
                                        Section.VOICE -> ReminderStore.voiceStyle.value.label
                                    },
                                    fontSize = 13.sp, color = Brand.Muted
                                )
                            }
                            Text("›", fontSize = 24.sp, color = Brand.Muted)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(6.dp))

                    Text("🥛 ஒரு கிளாஸ் அளவு", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderStore.GLASS_OPTIONS.forEach { ml ->
                            FilterChip(selected = glassMl == ml, onClick = { ReminderStore.setGlass(ctx, ml) }, label = { Text("$ml ml") })
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    Text("🎯 தினசரி goal", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderStore.GOAL_OPTIONS.forEach { ml ->
                            FilterChip(
                                selected = goalMl == ml, onClick = { ReminderStore.setGoal(ctx, ml) },
                                label = { Text(String.format(Locale.US, "%.1f L", ml / 1000f)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🔊 பாட்டு & குரல்", fontWeight = FontWeight.SemiBold, color = Brand.Ink)
                            Text("Silent / vibrate mode-ல சத்தம் வராது", fontSize = 13.sp, color = Brand.Muted)
                        }
                        Switch(checked = voiceOn, onCheckedChange = { ReminderStore.setVoice(ctx, it) })
                    }
                    Spacer(Modifier.height(10.dp))
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

    if (showPicker) {
        AddReminderDialog(
            onDismiss = { showPicker = false },
            onAdd = { h, m ->
                showPicker = false
                if (ReminderStore.add(ctx, h, m) == null) {
                    Toast.makeText(ctx, "இந்த நேரம் ஏற்கனவே இருக்கு 🙂", Toast.LENGTH_SHORT).show()
                } else {
                    AlarmScheduler.rescheduleAll(ctx, "புது அலர்ட்")
                    Toast.makeText(ctx, "அலர்ட் சேர்த்தாச்சு ✓", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Brand.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ReminderRow(
    r: Reminder, masterOn: Boolean, modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit, onDelete: () -> Unit
) {
    val active = r.enabled && masterOn
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) Brand.Card else Color(0xFFE9EDF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 1.dp else 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) Brand.Primary.copy(alpha = 0.12f) else Color(0xFFDDE3EE)),
                contentAlignment = Alignment.Center
            ) { Text(if (active) "💧" else "💤", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.timeLabel(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    color = if (active) Brand.Ink else Brand.Muted)
                Text(r.periodLabel(), fontSize = 12.sp, color = Brand.Muted)
            }
            Switch(checked = r.enabled, onCheckedChange = onToggle, enabled = masterOn)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "நீக்கு", tint = Color(0xFF9AA8C2))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onAdd: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = false)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("எப்போ dance ஆடணும்? ⏰", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
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
private fun DiagnosticsCard(perms: PermState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val log by DebugLog.lines.collectAsState()
    var open by remember { mutableStateOf(false) }
    fun yn(b: Boolean) = if (b) "✅" else "❌"
    SectionCard(modifier) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("🧪 டெஸ்ட் & Diagnostics", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = Brand.Ink, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = Brand.Muted)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                AlarmScheduler.scheduleTest(ctx, 10)
                Toast.makeText(ctx, "10 வினாடியில் dance அலர்ட் — phone-ஐ lock பண்ணுங்க! 🔒", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("💃 டெஸ்ட் அலர்ட் (10 வினாடி)") }
        Button(
            onClick = {
                if (Settings.canDrawOverlays(ctx)) OverlayService.start(ctx)
                else ctx.startActivity(Intent(ctx, ReminderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("👀 இப்போவே ஓடி வர்றதை பாரு") }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "Notif ${yn(perms.notif)}  Exact ${yn(perms.exact)}  FullScreen ${yn(perms.fullScreen)}  Battery ${yn(perms.battery)}",
                fontSize = 12.sp, color = Brand.Muted
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { DebugLog.add(ctx, "▶ 'இப்போவே காட்டு' அழுத்தியாச்சு"); Notifier.showReminder(ctx) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Notification இப்போவே காட்டு") }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⚙️ App settings (Autostart / Battery)") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log (புதுசு மேல)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { DebugLog.clear(ctx) }) { Text("அழி") }
            }
            if (log.isEmpty()) Text("இன்னும் எதுவும் இல்லை", fontSize = 12.sp, color = Brand.Muted)
            log.takeLast(15).reversed().forEach {
                Text(it, fontSize = 11.sp, color = Brand.Ink, lineHeight = 14.sp)
            }
        }
    }
}
