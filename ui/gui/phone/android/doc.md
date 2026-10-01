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

## What is built

**It builds, it starts, and a phone is laid out as one.** `./gradlew :android:assembleDebug`
writes a debug app. It opens on a logbook in the app's own storage, made the first time. Tried on
the SDK's emulator, Android 16, and on no phone yet.

**On a phone, one page at a time**, `PHONE-2`. The tab row is the open tab with a menu of the
others, back, and add, edit and delete; the agent's button is not there, `PHONE-1`. A tab shows
its list, and choosing opens the item full-screen. Locations goes from the regions to a region's
own page, its map, what is at it and the region itself, and from there to a site or wreck. Back,
the arrow or the phone's own, steps out one page at a time, cancelling a form on the way. Fields
stand one to a row. Calculations is chosen from a list above the form, the planner's runtime,
settings and gases stand one under another with the gases' table scrolling sideways, and the
model's warning scrolls with the form it heads. A tablet keeps the desktop's layout, `PHONE-3`,
told apart by the shorter side of its screen, 600 or more.

Rough still: the runtime's gas column is narrow, and the contingency's last tick sits at the
screen's edge.

The app is one activity in `android/`, which hands the screen to `Yemoja` in `ui/src/androidMain`;
everything the platform supplies is there. The manual and the map travel inside the app as they do
in the desktop's jar.

**Not yet:** a folder the user picks, `AND-5`, and with it import and export; reading a dive
computer, `AND-6`, with the permissions and the notification it needs, `AND-2` and `AND-3`; and
a signed release, `AND-1`. What a tooltip says over a greyed button is said on a long press, the
toolkit's own answer on a touch screen.

**The lock is let go at start.** Android ends an app without warning, so the edit lock, `JSON-27`,
is left behind each time. In the app's own storage nothing else reaches the logbook, so a lock found
there at start is one this app left, and is deleted. A folder the user picks can be reached by
other apps and needs an answer of its own, which `AND-5` owes.

## Settled

Decided on 2026-10-01, before anything Android is built. Nothing below is built yet.

- **AND-5 — Where a logbook lives.** *Settled:* **in a folder the user picks.** A logbook is a
  folder of readable files, and that is the point of the format: the user can see it, copy it
  and sync it with whatever they already use. Android gives an app no free path to a shared
  folder, so the folder is reached through the storage access framework, as a tree the user
  grants once. That needs a second file store beside the desktop's, and the edit lock,
  `JSON-27`, reworked on top of it: Android ends an app without warning, so a lock is left behind
  every time, which *What is built* says more of. App-private storage, reached by import and export, was the
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
