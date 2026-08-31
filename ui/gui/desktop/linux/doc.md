# GUI — desktop — Linux

Planned, not started. Comes after Windows and Android.

Only Linux-specific matters belong here; see [../doc.md](../doc.md) for the desktop
form factor.

## Scope

- Build and packaging, and the fact that "Linux" is many distributions.
- Desktop integration: launcher entry, icons, file associations.
- Where settings and logbooks live by convention.
- Bluetooth access, which on Linux goes through the system Bluetooth stack and is
  the least uniform part of this target.
- Which desktop environments and versions are actually supported.

## Open questions

- **LNX-1 — Packaging:** Flatpak, AppImage, Snap, native packages, or several. Determines
   how much the sandbox restricts file and Bluetooth access.
- **LNX-2 — Which distributions are supported**, and what runtime dependencies may be
   assumed present.
- **LNX-3 — Bluetooth LE** behaviour differs across stack versions; needs real testing.
