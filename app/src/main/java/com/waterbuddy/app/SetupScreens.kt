package com.waterbuddy.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/** Settings பகுதிகள் — முதல் முறை wizard-லயும், அப்புறம் Settings-லயும் இதே திரைகள். */
enum class Section(val title: String) {
    BUDDY("🧍 உங்க Buddy"),
    LINES("💬 என்ன சொல்லணும்?"),
    VOICE("🔊 எந்த குரல்?")
}

// ═══════════════════════ முதல் முறை Setup Wizard ═══════════════════════

@Composable
fun SetupFlow(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf<Section?>(null, Section.BUDDY, Section.LINES, Section.VOICE)
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(Brand.Background).systemBarsPadding()) {
        // மேல progress
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= step) Brand.Primary else Color(0xFFD6DEEF))
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (val sec = steps[step]) {
                null -> WelcomeStep()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(sec.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Brand.Ink)
                    Spacer(Modifier.height(12.dp))
                    SectionContent(sec)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("← பின்னால") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (step < steps.lastIndex) step++ else onDone() },
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text(
                    if (step == 0) "ஆரம்பிக்கலாம் →" else if (step < steps.lastIndex) "அடுத்து →" else "முடிஞ்சது ✓",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val model by ReminderStore.model.collectAsState()
    Column(
        Modifier.fillMaxSize().background(Brand.HeaderGradient).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💧 Water Buddy", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("by ${Brand.COMPANY}", color = Color.White.copy(alpha = 0.85f))
        Buddy3D(
            model = model, action = Action3D.DANCE,
            modifier = Modifier.size(240.dp, 300.dp),
            fallback = { WaterBuddy(Mood.DANCING, BuddyCharacter.KUTTY, Modifier.height(260.dp)) }
        )
        Text(
            "உங்க சொந்த 3D நண்பன் ஓடி வந்து, dance ஆடி, தண்ணி குடிக்க ஞாபகப்படுத்துவாங்க!\n\n" +
                "உங்க முகத்தையே வைக்கலாம் 📸, என்ன சொல்லணும்னு நீங்களே தேர்ந்தெடுக்கலாம் 💬, குரலையும் மாத்தலாம் 🔊",
            color = Color.White, textAlign = TextAlign.Center, fontSize = 15.sp
        )
        Spacer(Modifier.height(12.dp))
        TeqtoeFooter(light = true)
    }
}

// ═══════════════════════ Settings பக்கம் (தனியா திறக்க) ═══════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(section: Section, onClose: () -> Unit) {
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(Brand.Background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← திரும்ப", fontSize = 16.sp) }
            Text(section.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionContent(section)
            Spacer(Modifier.height(16.dp))
            TeqtoeFooter()
        }
    }
}

@Composable
fun SectionContent(section: Section) {
    when (section) {
        Section.BUDDY -> BuddySection()
        Section.LINES -> LinesSection()
        Section.VOICE -> VoiceSection()
    }
}

// ═══════════════════════ 1. Buddy + முகம் ═══════════════════════

@Composable
private fun BuddySection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val model by ReminderStore.model.collectAsState()
    val faceOn by ReminderStore.faceOn.collectAsState()
    val faceVersion by ReminderStore.faceVersion.collectAsState()
    val face = remember(faceOn, faceVersion) { if (faceOn) FaceMaker.load(ctx) else null }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun process(uri: Uri?) {
        if (uri == null) return
        busy = true; error = null
        scope.launch {
            when (val r = FaceMaker.make(ctx, uri)) {
                is FaceMaker.Result.Ok -> ReminderStore.setFaceOn(ctx, true)
                is FaceMaker.Result.Fail -> error = r.reason
            }
            busy = false
        }
    }

    val shotFile = remember { File(ctx.cacheDir, "shots/selfie.jpg").also { it.parentFile?.mkdirs() } }
    val shotUri = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shotFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) process(shotUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> process(uri) }

    Text("யாரு ஓடி வரணும்?", fontWeight = FontWeight.Bold, color = Brand.Ink)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Model3D.entries.forEach { m ->
            val sel = m == model
            Card(
                modifier = Modifier.weight(1f).clickable { ReminderStore.setModel(ctx, m) },
                shape = RoundedCornerShape(22.dp),
                border = if (sel) BorderStroke(3.dp, Brand.Primary) else null,
                colors = CardDefaults.cardColors(containerColor = if (sel) Color(0xFFE6EEFF) else Color.White)
            ) {
                Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Buddy3D(
                        model = m,
                        action = if (sel) Action3D.DANCE else Action3D.IDLE,
                        face = face,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        fallback = { WaterBuddy(Mood.IDLE, BuddyCharacter.KUTTY, Modifier.height(200.dp)) }
                    )
                    Text(m.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                    Text(m.tagline, fontSize = 12.sp, color = Brand.Muted)
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📸 உங்க முகத்தையே வைக்கலாமா?", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Brand.Ink)
            Text(
                "Selfie எடுங்க அல்லது ஒரு photo தேர்ந்தெடுங்க — முகத்தை cartoon-ஆ மாத்தி கேரக்டர் தலையில வச்சுடுவோம். Photo phone-லயே இருக்கும், எங்கயும் அனுப்பப்படாது.",
                fontSize = 13.sp, color = Brand.Muted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(130.dp).clip(CircleShape).background(Color(0xFFEAF0FB)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    busy -> CircularProgressIndicator()
                    face != null -> Image(face, contentDescription = "உங்க cartoon முகம்", modifier = Modifier.fillMaxSize())
                    else -> Text("🙂", fontSize = 56.sp)
                }
            }
            if (busy) Text("Cartoon ஆக்குறோம்…", fontSize = 13.sp, color = Brand.Muted)
            error?.let { Text(it, color = Brand.Accent, fontSize = 13.sp, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { camera.launch(shotUri) }, enabled = !busy) { Text("📷 Selfie") }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy
                ) { Text("🖼 Gallery") }
            }
            if (face != null) {
                TextButton(onClick = { ReminderStore.setFaceOn(ctx, false); FaceMaker.clear(ctx) }) {
                    Text("முகம் வேணாம், 3D முகமே போதும்", color = Brand.Muted)
                }
            }
        }
    }
}

// ═══════════════════════ 2. பேச்சு வரிகள் ═══════════════════════

@Composable
private fun LinesSection() {
    val ctx = LocalContext.current
    val selected by ReminderStore.presets.collectAsState()
    val custom by ReminderStore.custom.collectAsState()
    val style by ReminderStore.voiceStyle.collectAsState()
    val vname by ReminderStore.voiceName.collectAsState()
    val speaker = rememberSpeaker(style, vname)
    var input by remember { mutableStateOf("") }

    Text(
        "அலர்ட் வரும்போது இதுல நீங்க டிக் பண்ணதுல இருந்து 5 வரிகள் மாத்தி மாத்தி சொல்லுவாங்க. ▶ அழுத்தி கேட்டு பாருங்க.",
        fontSize = 13.sp, color = Brand.Muted
    )
    Spacer(Modifier.height(8.dp))
    Lines.presets.forEachIndexed { i, (ta, en) ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { ReminderStore.togglePreset(ctx, i) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (i in selected) Color(0xFFE6EEFF) else Color.White)
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i in selected, onCheckedChange = { ReminderStore.togglePreset(ctx, i) })
                Text(ta, modifier = Modifier.weight(1f), color = Brand.Ink, fontSize = 15.sp)
                IconButton(onClick = { speaker?.say(ta, en) }) { Text("▶", color = Brand.Primary, fontSize = 18.sp) }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("✍️ உங்க சொந்த வரிகள்", fontWeight = FontWeight.Bold, color = Brand.Ink)
    Text("உதா: \"டேய் மச்சான், தண்ணி குடிடா!\"", fontSize = 13.sp, color = Brand.Muted)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input, onValueChange = { input = it.take(120) },
            modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text("இங்க type பண்ணுங்க…") },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { ReminderStore.addCustom(ctx, input); input = "" }, enabled = input.isNotBlank()) { Text("சேர்") }
    }
    custom.forEach { line ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EC))
        ) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line, modifier = Modifier.weight(1f), color = Brand.Ink)
                IconButton(onClick = { speaker?.say(line, line) }) { Text("▶", color = Brand.Primary, fontSize = 18.sp) }
                IconButton(onClick = { ReminderStore.removeCustom(ctx, line) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "நீக்கு", tint = Color(0xFF9AA8C2))
                }
            }
        }
    }
    if (selected.isEmpty() && custom.isEmpty()) {
        Text("⚠️ ஒன்னாவது தேர்ந்தெடுங்க — இல்லைனா default வரிகள் சொல்லுவாங்க.", color = Brand.Accent, fontSize = 13.sp)
    }
}

// ═══════════════════════ 3. குரல் ═══════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection() {
    val ctx = LocalContext.current
    val style by ReminderStore.voiceStyle.collectAsState()
    val vname by ReminderStore.voiceName.collectAsState()
    val speaker = rememberSpeaker(style, vname)
    var voices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var tamilOk by remember { mutableStateOf(true) }
    DisposableEffect(speaker) {
        speaker?.let { sp -> sp.onReady = { voices = sp.voices(); tamilOk = sp.isTamil } }
        onDispose { }
    }
    val sample = Lines.presets[0]

    Text("குரல் ஸ்டைல்", fontWeight = FontWeight.Bold, color = Brand.Ink)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceStyle.entries.forEach { v ->
            FilterChip(
                selected = v == style,
                onClick = { ReminderStore.setVoiceStyle(ctx, v) },
                label = { Text(v.label) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { speaker?.say(sample.first, sample.second) }, modifier = Modifier.fillMaxWidth()) {
        Text("▶ கேட்டு பாருங்க")
    }

    Spacer(Modifier.height(18.dp))
    Text("Phone-ல இருக்குற குரல்கள்", fontWeight = FontWeight.Bold, color = Brand.Ink)
    Text("Online (☁️) குரல்கள் இன்னும் இயல்பா ஒலிக்கும்.", fontSize = 13.sp, color = Brand.Muted)
    VoiceRow("✨ தானா சிறந்ததை தேர்ந்தெடு", vname == null) { ReminderStore.setVoiceName(ctx, null) }
    voices.forEachIndexed { i, v ->
        val lang = if (v.locale.language == "ta") "தமிழ்" else "English (IN)"
        val net = if (v.isNetworkConnectionRequired) " ☁️" else ""
        val q = if (v.quality >= android.speech.tts.Voice.QUALITY_HIGH) " ⭐" else ""
        VoiceRow("$lang குரல் ${i + 1}$net$q", vname == v.name) { ReminderStore.setVoiceName(ctx, v.name) }
    }
    if (!tamilOk) {
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1EA)), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("⚠️ Phone-ல Tamil குரல் இல்லை", fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                Text("அதுவரைக்கும் English-ல பேசுவாங்க. Tamil voice download பண்ணுங்க:", fontSize = 13.sp, color = Color(0xFF7C4A35))
                TextButton(onClick = {
                    try { ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }) { Text("Tamil voice download →") }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = {
        try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }) { Text("⚙️ Phone Text-to-speech settings") }
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = Brand.Ink)
    }
}

/** Settings-ல preview கேக்க ஒரு Speaker (style / voice மாறினா புதுசா உருவாகும்) */
@Composable
private fun rememberSpeaker(style: VoiceStyle, voiceName: String?): Speaker? {
    val ctx = LocalContext.current
    val sp = remember(style, voiceName) { try { Speaker(ctx, style, voiceName) } catch (_: Exception) { null } }
    DisposableEffect(sp) { onDispose { sp?.shutdown() } }
    return sp
}
