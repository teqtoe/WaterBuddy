# Water Buddy 💧 — உங்க அலர்ட் நண்பன் (by Teqtoe)

ஒரு குட்டி கேரக்டர் (Buddy) திரையில இடது பக்கம் ஓடி வந்து, நடுவுல நின்னு அலர்ட் சொல்லிட்டு, வலது பக்கம் போயிடுவார்.
Popup notification எதுவும் இல்லை — Buddy மட்டும் தான்.

## அலர்ட் வகைகள்
💧 தண்ணீர் · 📅 Meeting · 💊 மாத்திரை · 🧘 Break · ⭐ Custom
- ஒவ்வொரு அலர்ட்டுக்கும்: பெயர், Buddy சொல்லணும்னு நீங்க எழுதுற வரி, நேரம்
- ஒரு தடவை (தேதியோட) / தினமும் / திங்கள்–வெள்ளி / நீங்க தேர்ந்தெடுக்கும் நாட்கள்
- "✓ சரி" அல்லது "⏰ அப்புறம்" (snooze) — பட்டன்கள் பேச்சு குமிழுக்குள்ளயே, இடம் அடைக்காது
- தண்ணீருக்கு: குடிச்சேன் → Buddy குடிச்சு 👍 காட்டி ஓடிப் போவார்; அப்புறம் → சோகமா நடந்து போவார்

## Buddy படங்கள்
`app/src/main/res/drawable-nodpi/buddy_*.webp` — run, happy, drink, sad.
புது pose சேர்க்க: transparent PNG கொடுத்தா போதும்.

## Run பண்றது எப்படி
1. **Android Studio** (Ladybug அல்லது புதுசு) install பண்ணுங்க.
2. `File → Open` → இந்த `WaterBuddy` folder-ஐ தேர்ந்தெடுங்க.
3. Gradle sync முடியட்டும் (முதல் முறை சில நிமிஷம் ஆகும், internet வேணும்).
4. Phone-ல **Developer options → USB debugging** ON பண்ணி USB-ல இணைங்க.
5. ▶ **Run** அழுத்துங்க. APK மட்டும் வேணும்னா: `Build → Build APK(s)`.

Android 8.0 (API 26) மற்றும் அதுக்கு மேல எல்லா phone-லயும் ஓடும்.

## முதல் முறை அனுமதிகள்
App-லயே ஆரஞ்சு கார்டு வரும், "சரி செய்" அழுத்தினா போதும்:
- **Notifications** — இது இல்லாம எதுவும் வராது
- **Alarms & reminders** (Android 12+) — நிமிஷம் தவறாம வர
- **Full screen notifications** (Android 14+) — Lock screen-ல Buddy முழு திரையில வர

> **Xiaomi / Realme / Oppo / Vivo phones:** Settings → Apps → Water Buddy → **Autostart ON** பண்ணுங்க,
> Battery saver-ல "No restrictions" வையுங்க. இல்லைனா இந்த company-கள் அலர்ட்டை தடுக்கலாம்.

## Code அமைப்பு
```
app/src/main/java/com/waterbuddy/app/
├── MainActivity.kt          Home திரை: ON/OFF, அலர்ட் லிஸ்ட், நேரம் சேர்க்க
├── ReminderActivity.kt      Lock screen மேல வரும் அலர்ட் திரை
├── WaterBuddyCharacter.kt   Buddy கேரக்டர் — Canvas-ல வரைந்த அனிமேஷன் (5 மனநிலைகள்)
├── AlarmScheduler.kt        AlarmManager மூலம் அலர்ட் செட் பண்றது
├── Receivers.kt             அலர்ட் / notification பட்டன் / phone restart கையாளுதல்
├── Notifier.kt              Notifications
├── ReminderStore.kt         அலர்ட்ஸ் & count சேமிப்பு
├── Speaker.kt               குரல் + Buddy பேசுற வசனங்கள்
└── Ui.kt                    Theme, பேச்சு குமிழ், கிளாஸ் ஐகான்
```

## மாற்றிக்கணும்னா
- **வசனங்கள்:** `Speaker.kt` → `Lines` object
- **தினசரி goal:** `ReminderStore.kt` → `DAILY_GOAL = 8`
- **Buddy நிறங்கள் (சட்டை, முடி, தோல்):** `WaterBuddyCharacter.kt` மேல இருக்குற color values
- **Default அலர்ட் நேரங்கள்:** `ReminderStore.seedDefaultsIfFirstRun()`
