# JNToolz

Minimalist utility suite and widgets designed for Nothing OS (Nothing Phone (1), (2), (2a), (2a) Plus, and Android 14+ / 15 / 16 / 17).

<p align="center">
  <img src="app/src/main/res/drawable/ic_jntoolz_logo.png" width="128" alt="JNToolz Logo" />
</p>

---

## 📥 Installation Guide (For Any Android / Nothing Phone)

Installing JNToolz on your phone (or a friend's phone) takes less than 1 minute. No PC or USB debugging is required.

### Step 1: Download the APK
1. Open your mobile browser and navigate to the **[Latest Release](https://github.com/JudeCJz/JNToolz/releases/latest)**.
2. Under **Assets**, tap **`JNToolz-v1.0.0.apk`** to download it.

### Step 2: Install the App
1. When download completes, tap the notification or open your **Files** / **Downloads** app and tap the APK file.
2. If Android prompts *"For your security, your phone is not allowed to install unknown apps from this source"*:
   - Tap **Settings**.
   - Turn **ON** the toggle for **Allow from this source**.
   - Press the Back gesture/button.
3. Tap **Install**.
   > *Note on Play Protect*: If Play Protect displays *"Unrecognized app details"*, tap **More details** ➔ **Install anyway** (standard for open-source self-built GitHub APKs).

### Step 3: Grant Calendar Permission & Sync
1. Open **JNToolz** from your app drawer.
2. Tap on the **Calendar** tool card.
3. When prompted, tap **Allow** for the Calendar permission so the app can securely read your device's synced calendar events.
4. Tap **SYNC GOOGLE ACCOUNTS NOW** to immediately detect all your connected Google accounts and calendars.
5. *(Optional)* Tap on any account dropdown to customize which individual calendars appear in the widget.

### Step 4: Add the Widget to Your Home Screen
* **Method 1 (Fastest)**: Inside the Calendar screen in JNToolz, simply tap the **`+ ADD SCROLLABLE WIDGET`** button at the top, then tap **Add to Home screen**.
* **Method 2 (Manual)**:
  1. Long-press on an empty area of your home screen.
  2. Tap **Widgets**.
  3. Scroll down and locate **JNToolz**.
  4. Drag the **JNToolz Calendar Widget** to your desired home screen page.
  5. Long-press the placed widget and drag the resize handles to expand it vertically to fit your preferred layout!

---

## ⚡ Features & Modules

### 📅 Calendar & Agenda Widget
- **Pure AMOLED Black (`#000000`)**: Zero background battery drain, designed to seamlessly blend with Nothing OS dark backgrounds.
- **Dynamic 5-Tier Opacity Decay**: The top event begins with a subtle slate card (`#202121`) and gradually fades into complete AMOLED black (`#000000`) for older events.
- **Collapsible Google Account Dropdowns**: Clean overview showing active calendar counts (e.g. `1 of 9 calendars active`), with expandable checkboxes per calendar.
- **Nothing OS Typography**: Minimal letter-spaced uppercase headers (`U P C O M I N G`), hollow timeline markers, and native squircle rounding.
- **100% Offline & Autonomous**: Operates via Android's native `CalendarContract` ContentProvider. No background services, no battery drain, and **works permanently with USB/Wireless debugging turned off**.
- **Tap to Open**: Tapping any event card immediately launches Google Calendar directly to that date.

### 🔄 In-App GitHub Auto-Updater
- Checks for new releases directly from this repository upon launch.
- Displays the current version tag (`v1.0`) on the hub dashboard.
- Features an animated **glowing Nothing-red update pill** at the bottom-right when a new version is available.
- Displays complete changelogs (fixes, patches, features) with one-tap download and relaunch patch summaries.

---

## 🔒 Privacy & Battery Efficiency
- **No Background Services**: No persistent background daemons running in the RAM. Updates are triggered on-demand by system calendar provider broadcasts (`PROVIDER_CHANGED`) and standard OS time changes.
- **Privacy First**: Calendar data is queried locally on-device. Events never leave your phone.

---

## 🛠️ Build from Source

```bash
git clone https://github.com/JudeCJz/JNToolz.git
cd JNToolz
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
