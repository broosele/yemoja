# GUI — desktop — Windows

**First priority target.** The platform the project is developed on, and the first
one that must work end to end.

Only Windows-specific matters belong here; everything else is in
[../doc.md](../doc.md).

## Scope

- Build, packaging and distribution: installer, unsigned versus signed, updates.
- Where the application stores settings and where a logbook lives by default.
- Windows conventions: title bar, high-DPI and multi-monitor scaling, light/dark
  following the system, notifications.
- Bluetooth access for dive computer downloads, and the permissions it needs.
- Development prerequisites for building on Windows.

## Open questions

- **WIN-1 — Distribution:** plain installer, MSIX, a store listing, or a portable build.
   This decides how updates and code signing work.
- **WIN-2 — Code signing** — needed to avoid a warning on first run, and it costs money.
- **WIN-3 — Minimum supported Windows version.**
- **WIN-4 — Bluetooth LE support** varies with Windows version and adapter; needs testing
   against real hardware early, since it may constrain the minimum version.
