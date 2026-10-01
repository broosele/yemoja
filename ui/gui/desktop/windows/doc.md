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

## Settled

- **WIN-1 — How it is delivered.** *Settled:* **an installer, and nothing else for now.** No
  store listing, no MSIX and no portable build in the first version. The installer carries the
  runtime it needs, so a user installs nothing else first. Which tool makes it is not chosen and
  nothing in the build makes one yet.
- **WIN-3 — Which Windows.** *Settled:* **Windows 10 and Windows 11.** Older versions are not
  supported.
- **WIN-4 — Bluetooth LE.** *Settled:* **what Windows 10 and 11 offer is enough**, and it is
  proven on one Windows 11 machine with its own adapter: a scan finds a computer and a download
  reads it. A Windows 10 machine has not been tried, which is a test owed before the first version
  rather than a question.

## Open questions

- **WIN-2 — Code signing** — needed to avoid a warning on first run, and it costs money.
