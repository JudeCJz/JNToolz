# JNToolz

Minimalist utility suite and widgets designed for Nothing OS (Nothing Phone (1), (2), (2a), (2a) Plus, (4a) Pro, and Android 14+ / 15 / 16 / 17).

<p align="center">
  <img src="app/src/main/res/drawable/ic_jntoolz_logo.png" width="128" alt="JNToolz Logo" />
</p>

---

## 📥 Installation Guide (For Any Android / Nothing Phone)

Installing JNToolz on your phone (or a friend's phone) takes less than 1 minute. No PC or USB debugging is required.

### Step 1: Download the APK
1. Open your mobile browser and navigate to the **[Latest Release](https://github.com/JudeCJz/JNToolz/releases/latest)**.
2. Under **Assets**, tap **`JNToolz-v1.0.6.apk`** to download it.

### Step 2: Install the App
1. When download completes, tap the notification or open your **Files** / **Downloads** app and tap the APK file.
2. If Android prompts *"For your security, your phone is not allowed to install unknown apps from this source"*:
   - Tap **Settings**.
   - Turn **ON** the toggle for **Allow from this source**.
   - Press the Back gesture/button.
3. Tap **Install**.
   > *Note on Play Protect*: If Play Protect displays *"Unrecognized app details"*, tap **More details** ➔ **Install anyway** (standard for open-source self-built GitHub APKs).

### Step 3: Grant Calendar Permission
1. Open **JNToolz** and tap the **Calendar** tool card.
2. Tap **Allow** for the Calendar permission so the app can read your device's synced calendar events (read-only).
3. Choose which calendars appear in the widget from the per-account lists.
4. *(Optional)* **Link your Google account** (read-only) to get Google's calendar and event colours. The app is not yet verified by Google, so sign-in shows a warning: tap **Advanced** then **Go to JNToolz**.

### Step 4: Add the Widget to Your Home Screen
* **Method 1 (Fastest)**: Inside the Calendar screen in JNToolz, simply tap the **`+ ADD SCROLLABLE WIDGET`** button at the top, then tap **Add to Home screen**.
* **Method 2 (Manual)**:
  1. Long-press on an empty area of your home screen.
  2. Tap **Widgets**.
  3. Scroll down and locate **JNToolz**.
  4. Drag the **JNToolz Calendar Widget** to your desired home screen page.
  5. Long-press the placed widget and drag the resize handles to expand it vertically to fit your preferred layout!

---

## ⚡ Features

### Calendar & Agenda Widget
- **Scrollable widget** with pull to refresh; tap an event card to open Google Calendar on that date.
- **Pure AMOLED black** background suited to Nothing OS.
- **Colours**: tap any card in the in-app preview to set a colour per event or per calendar label.
- **Marker styles**: OFF, DOT, RING, BAR or CARD.
- **Card fade modes**: Gradient, Dim or Fade.
- **Per-calendar selection** grouped by Google account.
- Reads events on-device through Android's `CalendarContract`. The optional Google account link is read-only and only used for colours.

### Battery & Screen-On Time (SOT)
- **Real-time Tracker**: Screen-on time, drain rates, per-app battery consumption, and history graphs.
- **Hardware Telemetry**: Detects device model (e.g. Nothing Phone (4a) Pro), OS build, battery design capacity, health percentage, and device age with battery cycle count.
- **Status Bar Icon**: Clean monochrome JNToolz logo in status bar, full battery icon in notification shade.
- **Graph Export**: One-tap copy to clipboard with comprehensive hardware specs and drain logs.

### Unscroll (Anti-Doomscroll)
- **Focus Guard**: Tracks time spent in target social apps with gentle reminder notifications and auto-returning to home.
- **Hard Mode Unlock**: Funny anti-scroll prompts with verification code to bypass lockout.

### Counter Widget
- Minimalist tap-counter widgets with customizable colors, gradient overlays, and glassmorphism.

### In-App Updater
- Checks this repository's GitHub releases on launch and shows an update pill when a newer version exists.
- Downloads the APK and hands it to the system installer (you confirm the install).

---

## 🔒 Privacy
Calendar and battery data stay on your device. See [PRIVACY.md](PRIVACY.md) for details.

---

## 🛠️ Build from Source

```bash
git clone https://github.com/JudeCJz/JNToolz.git
cd JNToolz
./gradlew assembleRelease
```
The output APK is `app/build/outputs/apk/release/JNToolz-v1.0.6.apk` (R8-minified; signed with the debug keystore so updates install over earlier builds).
