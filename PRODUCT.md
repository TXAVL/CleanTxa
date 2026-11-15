# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Kotlin + Jetpack Compose, with the `dadb` library as the ADB client over the network (user confirmed this direction). Build tooling on the machine: JDK 17; ANDROID_HOME points at C:\Android (contents not yet checked); `adb` is not on PATH.

## Users
General, non-technical people sitting next to an Android TV / Google TV who want to clean it of manufacturer telemetry, ads and bloat using only their phone. They have not enabled developer options or ADB before, so many arrive unconnected and need to be walked through it.

## Product Purpose
CleanTxa is a phone app that connects to the user's Android TV over wireless ADB and debloats it through a GUI. It replaces handing the whole job to an AI assistant with shell access: the user sees every package and chooses what to disable. Success: a TV that is cleaner, still boots and plays inputs, and can be restored.

## Positioning
Same job as the "debloat my TV with an AI assistant" prompt, but the human stays in control: per-package choices, visible safety rules, and a restore path, with no AI or computer required.

## Operating Context
- The phone and the TV must be on the same Wi-Fi.
- The TV needs Developer options on (Build pressed 7 times) and USB/network debugging enabled; a prompt "Allow USB debugging?" appears on the TV and the user must accept it.
- Some TVs only offer Wireless debugging with a pairing code and port; others use port 5555 (some use other fixed ports).
- Not for Samsung Tizen, LG webOS, Roku or Fire TV; those users should be pointed to https://cleanyour.tv/not-android.
- Some boxes cut USB power while USB debugging is on, and a USB Ethernet adapter can drop; the whole session should be done in one sitting.

## Capabilities and Constraints
Version 1 scope (confirmed): connect, back up, debloat, step-by-step guide. Not in v1: the Apple TV style launcher (a later version), and DNS ad-blocking is a possible optional extra.
- Connect: enter the IP or scan the network; pairing-code flow for Wireless debugging.
- Guide: a full in-app walkthrough for users who are not yet connected (enable developer options, debugging, find the IP, accept the prompt).
- Back up before changing anything: package list, disabled-package list, getprop and settings, plus a restore action that re-enables everything disabled.
- Debloat: packages sorted into categories (telemetry/analytics, manufacturer content and ad services, background services, preloaded apps, streaming apps); user ticks what to disable; uses `pm disable-user --user 0`, never uninstall; small batches; one `pm` command per shell call.
- Hard-blocked packages the app refuses to disable: TV and input services, remote and Bluetooth, keyboard, Play Store and Play Services, Android core packages, and TV middleware (`com.cltv.mal`, `com.mediatek.tis`, `com.mediatek.tv.service`, `com.mediatek.tv.agent`). Factory-menu packages are never launched.
- Wait about 15 seconds after the last batch before reboot; compare `pm list packages -d` after reboot.
- Ask before touching streaming apps, voice assistant, Chromecast, Miracast and the update service.
- When finished: close network ADB, switch debugging off, revoke authorizations, and check port 5555 is closed.
- Interface languages: Vietnamese and English, following the device language.

## Brand Commitments
Name: CleanTxa. Related to the TXA family of tools by the same author. No logo or visual identity decided.

## Evidence on Hand
The source prompt (pasted by the user) listing the full debloat workflow and safety rules. No screenshots, device logs, or real user feedback yet. Do not fabricate device compatibility lists.

## Product Principles
1. The user decides; the app explains. Every disable is visible, chosen and reversible.
2. Safety first: refuse load-bearing packages and always back up before changing anything.
3. A first-time user must be able to get connected without outside help.
4. Leave the TV closed up: shut network ADB when the job is done.
5. Plain words over jargon; show the technical detail only on request.

## Accessibility & Inclusion
Users are non-technical and may be older; the guide and warnings should be written in plain Vietnamese and English with readable text sizes. No specific standard was requested.
