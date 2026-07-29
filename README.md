# WM PokeTrap (Android)

Phone farming companion for Whiskey Mike — sits next to the PC bot on your Desktop.

**Folder:** `Desktop\WMPokeTrap`  
**PC bot:** `Desktop\whiskey_mikes_farmin_trap`

## What it does

Same idea as the PC Farmin Trap, rebuilt for a **touch-based phone game app**:

- OCR name → identify Pokémon → catch target / tap Run to flee
- False Swipe → Poké Ball loop
- Floating overlay with START / PAUSE / STOP while the game is open
- Calibration for regions + tap points
- Movement: Horizontal / Vertical / Custom D-pad taps, or Swipe mode

## Requirements

- Android **10+** (API 30+) recommended — uses Accessibility screenshots + gestures
- Enable **Accessibility** for WM PokeTrap
- Allow **Display over other apps** (overlay)

## Build the APK

### Option A — Android Studio (easiest)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open `Desktop\WMPokeTrap`
3. Let Gradle sync
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. Install `app/build/outputs/apk/debug/app-debug.apk` on your phone

### Option B — Command line

```powershell
cd $env:USERPROFILE\OneDrive\Desktop\WMPokeTrap
.\gradlew.bat assembleDebug
```

APK output:

`app\build\outputs\apk\debug\app-debug.apk`

## First-run setup on phone

1. Install the APK (allow unknown sources)
2. Open **WM PokeTrap → Setup**
3. Turn on **Accessibility** for WM PokeTrap
4. Allow **overlay** permission → Show Overlay
5. Open your game, enter a battle
6. In **Calibration**, set:
   - Battle menu / Name / HP / Message regions (drag boxes)
   - Fight, Run, False Swipe, Items, Poké Ball tap points
7. On **Farm**, pick your target Pokémon → **START**
8. Switch to the game — use the floating overlay if needed

## Notes

- This is a sideload tool, not a Play Store app
- Some games block Accessibility taps — if taps do nothing, that game may be restricting input
- Recalibrate if you change phone resolution / orientation
- Keep the name region tight around the opponent name only

## Project layout

```
WMPokeTrap/
  app/src/main/java/com/whiskeymike/wmpoketrap/
    bot/        # engine, OCR, matcher, settings
    service/    # Accessibility + overlay
    ui/         # Compose screens + calibrator
```
