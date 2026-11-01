# JNToolz

Minimalist utility suite designed for Nothing OS (Android 14+ / Android 15 / 16 / 17).

![JNToolz](app/src/main/res/drawable/ic_jntoolz_logo.png)

## Modules

### 1. Calendar & Agenda Widget
- **Pure AMOLED Black Background (`#000000`)** with zero background battery drain.
- **Dynamic 5-Tier Opacity Decay**: Top event begins at subtle dark slate (`#202121`) and gradually blends into pure background black (`#000000`).
- **Collapsible Google Account Dropdowns**: Selectively enable/disable synced calendars across multiple accounts (`@gmail.com`, Google Workspace, etc.).
- **Nothing OS Typography & Proportions**: Monospace letter-spaced section headers, hollow timeline markers, and native squircle radius.
- **Offline & Autonomous**: Operates 100% locally via Android's native `CalendarContract` ContentProvider. No background services, persistent sync daemons, or ADB/debugging needed.

### 2. Auto-Updater & Changelog
- Automated GitHub release checking via `JudeCJz/JNToolz`.
- Pulsing red indicator on dashboard when new release is available.
- Integrated What's New dialog displaying patches, fixes, and features on version upgrade.

## Tech Stack
- **Kotlin 2.2**
- **Jetpack Compose** + Material 3 (Nothing OS monochrome styling)
- **Android AppWidgets Framework** (`RemoteViews` + `RemoteViewsService`)
- Android 8.0+ (API 26) up to Android 17 (API 36)
