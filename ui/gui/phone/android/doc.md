# GUI — phone — Android

**Second priority target**, after Windows desktop.

Only Android-specific matters belong here; see [../doc.md](../doc.md) for the phone
form factor.

## Scope

- Build, packaging, signing and distribution.
- The permission model, which is the most intrusive part of this target: Bluetooth
  scanning and connection permissions, how they changed across Android versions,
  and how to ask for them without alarming the user.
- Storage: where a logbook lives, and what scoped storage allows.
- Android conventions: back navigation, notifications for long downloads, staying
  alive during a dive computer download.
- Minimum and target API levels.

## Settled

Decided on 2026-10-01, before anything Android is built. Nothing below is built yet.

- **AND-5 — Where a logbook lives.** *Settled:* **in a folder the user picks.** A logbook is a
  folder of readable files, and that is the point of the format: the user can see it, copy it
  and sync it with whatever they already use. Android gives an app no free path to a shared
  folder, so the folder is reached through the storage access framework, as a tree the user
  grants once. That needs a second file store beside the desktop's, and the edit lock,
  `JSON-27`, reworked on top of it. App-private storage, reached by import and export, was the
  cheaper answer and hides the logbook from the user, which is the one thing the format exists
  not to do.
- **AND-1 — Distribution.** *Settled:* **an installable file published directly, first.** The
  same footing as the Windows installer, `WIN-1`: built here, published with the release, and
  signed with a key of the author's own, which Android requires of every app and which costs
  nothing. The key is kept outside the repository. The Play Store and F-Droid can follow once
  the app has been tried.
- **AND-2 — Minimum Android version.** *Settled:* **Android 12.** From 12 on, scanning for and
  connecting to a Bluetooth device have permissions of their own; before it a scan needs the
  location permission, which is hard to explain to a user and asks for more than is used.
  Phones from before about 2021 are left out.
- **AND-3 — Background downloads.** *Settled:* **a download carries on, with a notification.**
  A slow Bluetooth computer takes a quarter of an hour, which nobody watches with the screen on.
  The read runs in a foreground service whose notification shows its progress and a Cancel, as
  the desktop shows them on Home, `GUI-52`.
- **AND-6 — Which connections are read.** *Settled:* **Bluetooth only.** Every computer met so
  far talks Bluetooth LE, and the Bluetooth library already in use carries it on Android. A USB
  or serial cable on a phone needs an adapter, a USB-serial library and testing per chip, for
  few users.
- **AND-7 — What builds it.** *Settled:* **the Android Gradle Plugin and the Android SDK.**
  Google's plugin, Apache 2.0, is the only way Gradle builds an Android app, and it was approved
  as a dependency on that ground. The SDK is a build tool installed on the machine that builds,
  as WiX is for the installer, and carries nothing into the app. README lists the plugin among
  the dependencies once the build takes it.
- **AND-4 — Test devices.** *Settled:* **the author's phone and the SDK's emulator.** The
  emulator for the screens and the storage picker during development; the phone, on Android 12
  or later, for every download, since Bluetooth is proven only on real hardware.
