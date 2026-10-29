# Water Buddy 💧 — தண்ணி குடிக்க ஞாபகப்படுத்தும் app

ஒரு cartoon நண்பர் (Buddy) கையில கிளாஸோட வந்து "தண்ணி குடிங்க!" ன்னு சொல்லுவார்.
- **குடிச்சேன் 💧** அழுத்தினா → குதிச்சு, கை தூக்கி, நட்சத்திரங்களோட சந்தோஷப்படுவார் 🎉
- **இப்போ வேணாம்** அழுத்தினா → தலை குனிஞ்சு, கண்ணீர் விட்டு சோகமா போயிடுவார் 😢

## என்னென்ன இருக்கு
| அம்சம் | விவரம் |
|---|---|
| Lock-லயும் வேலை செய்யும் | Phone lock-ல இருந்தா திரை ON ஆகி Buddy முழு திரையில வருவார் |
| பல அலர்ட்ஸ் | எத்தனை நேரம் வேணாலும் சேர்க்கலாம், ஒவ்வொன்னையும் தனியா ON/OFF |
| ஒரே switch | மேல இருக்குற ON/OFF — எல்லா அலர்ட்ஸையும் ஒரே நேரத்துல நிறுத்தும் (Buddy தூங்கிடுவார் 😴) |
| பேட்டரி சேமிப்பு | Background service இல்லை. AlarmManager மட்டும் — அலர்ட் நேரத்துல ஒரு நொடி எழும் |
| குரல் | Phone-ஓட Text-to-Speech-ல Buddy பேசுவார் (Tamil voice இல்லைனா English). Silent mode-ல பேசமாட்டார் |
| Notification பட்டன் | App திறக்காமலே notification-லயே பதில் சொல்லலாம் |
| இன்னைக்கு count | 8 கிளாஸ் goal, கிளாஸ் ஐகான்கள் நிரம்பும். நள்ளிரவு தானா reset |
| Restart | Phone restart ஆனாலும் அலர்ட்ஸ் தானா மறுபடி செட் ஆகும் |
| டெஸ்ட் | "டெஸ்ட் அலர்ட்" → 10 வினாடியில் அலர்ட் வரும், lock பண்ணி பாருங்க |

Buddy-ஐ home screen-ல தொட்டா கிச்சுகிச்சு மூட்டின மாதிரி சிரிப்பார் 😆

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
