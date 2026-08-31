# GUI — desktop — macOS

Planned, not started. Comes after Windows and Android.

Only macOS-specific matters belong here; see [../doc.md](../doc.md) for the desktop
form factor.

## Scope

- Build, packaging and distribution: app bundle, notarisation, updates.
- The macOS menu bar, which is a stronger convention than on other platforms.
- Sandboxing, and the entitlements needed for file access and Bluetooth.
- Where settings and logbooks live by convention.
- Standard macOS behaviours users expect.

## Open questions

- **MAC-1 — Distribution:** direct download with notarisation, or the App Store — the App
   Store imposes sandboxing constraints that may affect where a logbook can live.
- **MAC-2 — Apple Developer Program membership** is required for notarisation.
- **MAC-3 — Minimum supported macOS version.**
- **MAC-4 — Whether development and testing hardware is available.**
