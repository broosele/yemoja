# GUI — desktop — macOS

Future, not started: put off on 2026-10-05 until there is a Mac to try it on, `MAC-4`. Comes after
Windows and Android.

Only macOS-specific matters belong here; see [../doc.md](../doc.md) for the desktop
form factor.

## Scope

- Build, packaging and distribution: app bundle, notarisation, updates.
- The macOS menu bar, which is a stronger convention than on other platforms.
- Sandboxing, and the entitlements needed for file access and Bluetooth.
- Where settings and logbooks live by convention.
- Standard macOS behaviours users expect.

## What it would take

Found when it was asked about, so the next look starts here rather than from nothing.

- **Already there.** Everything but packaging is shared JVM and Compose code. Kable carries
  btleplug built for both Intel and Apple Silicon. The settings fall back to `~/.yemoja` where
  Windows' `LOCALAPPDATA` is absent, which works but is not where a Mac keeps them.
- **libdivecomputer as a `.dylib`**, for both processors, bundled as the Windows DLL is. The
  build already copies a `.dylib` where it finds one, `LOGIC-27`.
- **A `.dmg`**, which jpackage makes only on a Mac. A macOS machine in CI could build it without
  one here, and `tool/release.py` would upload it beside the others.
- **`NSBluetoothAlwaysUsageDescription` in the bundle's Info.plist.** Without it macOS ends the
  app the first time it reaches for Bluetooth, which is during a download.
- **Command rather than Control** for the two shortcuts that use it, a menu bar, and the icon as
  `.icns`.
- **Bluetooth names a device differently.** macOS gives each device an identifier of its own
  rather than its address, so remembering a computer and pairing one are the paths to try first.

## Open questions

Not in the first version, which is Windows alone, `GUI-5`; these wait for the version that adds it.

- **MAC-1 — Distribution:** direct download with notarisation, or the App Store — the App
   Store imposes sandboxing constraints that may affect where a logbook can live.
- **MAC-2 — Apple Developer Program membership** is required for notarisation.
- **MAC-3 — Minimum supported macOS version.**
- **MAC-4 — Whether development and testing hardware is available.**
