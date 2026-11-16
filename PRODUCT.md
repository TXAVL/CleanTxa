# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Kotlin + Jetpack Compose (Material 3), two Gradle modules: `:app` (phone app, `com.txa.cleantxa`, v1.2.8) and `:launcher` (the clean TV home screen, `com.gathin.tvhome`). ADB is done in-app with `libadb-android` (JitPack) plus Conscrypt and BouncyCastle for the keys and certificate; the earlier `dadb` choice was dropped. Build: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, compileSdk 37, targetSdk 36, minSdk 26, JDK 17, Android SDK at C:\Android. Code identifiers are mostly Vietnamese without diacritics (`ThaoTacTv`, `PhienLamViec`, `KeHoach`). Unit tests cover updates, formatting, the disable plan, restore, advanced options and TV model matching.

## Users
General, non-technical people sitting next to an Android TV / Google TV who want to clean it of manufacturer telemetry, ads and bloat using only their phone. Many have never enabled developer options or ADB and need to be walked through it. The app also runs on a TV itself (it declares the leanback launcher category and a TV banner).

## Product Purpose
CleanTxa is a phone app that connects to the user's Android TV over wireless ADB and cleans it through a GUI, in place of handing the whole job to an AI assistant with shell access. The user sees the packages and chooses; the app suggests a plan from a database of TV models other people have already cleaned. Success: a TV that is cleaner, still boots and plays inputs, and can be restored.

## Positioning
Same job as the "debloat my TV with an AI assistant" prompt, but the human stays in control: visible choices, safety rules, a backup and a restore path, no AI or computer required. Unlike the upstream project it is a ready-made phone app with a first-time guide and Vietnamese-first wording.

## Operating Context
- Phone and TV on the same Wi-Fi.
- The TV needs Developer options on (Build pressed 7 times) and USB/network or Wireless debugging; the TV shows an "Allow USB debugging?" prompt the user must accept.
- Wireless debugging pairing with a 6-digit code is supported, with the IP and port found automatically over mDNS.
- Not for Samsung Tizen, LG webOS, Roku or Fire TV.
- Some boxes cut USB power while debugging is on, and a USB Ethernet adapter can drop; do the whole session in one sitting.
- The app and the TV data come from the open-source cleanyourtv project (cleanyourtv model database, clean home launcher); the app has its own open-source licenses screen.

## Capabilities and Constraints
Confirmed in the code (read at the file and function level, not every screen tested on a device):
- Connect: manual IP and port, network scan, quick reconnect, wireless pairing code, error screen with QR.
- Guide: 7-step picture guide in Vietnamese and English, shown on the home screen as "turn on developer mode first".
- TV info: reads model, Android version, storage and time zone; flags a time zone different from the phone and low storage.
- Clean: matches the TV to a known model in `du-lieu.json` and suggests which packages to disable, with a note per package and how many models disabled or kept it; the user chooses; packages are disabled, not uninstalled; a backup is saved first (phone and TV) and a restore screen re-enables them.
- Clean Home: installs the bundled launcher (`tvhome.apk`, Xiaomi build `tvhome-xiaomi.apk`), sets it as Home, with settings for dock apps, columns, wallpaper and channel tiles (VTV channels via VTVgo).
- Remote keys: maps the TV remote's app and settings buttons to the new Home; record-a-key helper.
- Extras: kids lock, TV speed boost (animation scale, trim RAM, reboot), cache clean, ad-blocking DNS, remote control screen, screen capture, schedule TV off, wallpaper, phone-side logs with a web viewer.
- Updates: checks GitHub Releases every hour, downloads and installs the APK with a progress dialog; update notifications.
- Languages: Vietnamese and English (switchable inside the app).
- Safety: refuses load-bearing packages, never launches factory-menu packages, and closes network ADB at the end (carry-over rules from the source prompt; check the exact block list in `KeHoach.kt` before claiming it).
- Open questions: no tested-device list in the repo; not verified on a real TV in this record.

## Brand Commitments
Name: CleanTxa, from the TXA family of tools by the same author. Related to, and built on, the open-source cleanyourtv project (credit and licenses are shown in the app). Launcher package id is still `com.gathin.tvhome`. No fixed visual identity beyond the current logo (a TV with a check mark) and the teal accent.

## Evidence on Hand
The source prompt from the user, the repository with git history up to v1.2.8, the bundled `du-lieu.json` model database, a 7-step guide with screenshots in both languages, and unit tests. No device test logs, user feedback or compatibility list in the repo; do not fabricate them.

## Product Principles
1. The user decides; the app explains. Every disable is visible, chosen and reversible.
2. Safety first: refuse load-bearing packages and always back up before changing anything.
3. A first-time user must be able to get connected without outside help.
4. Leave the TV closed up: shut network ADB when the job is done.
5. Plain words over jargon; show the technical detail only on request.

## Accessibility & Inclusion
Users are non-technical and may be older; the guide and warnings stay in plain Vietnamese and English at readable text sizes. The app also has to work with a TV remote (D-pad) when run on a TV. No specific accessibility standard was requested.
