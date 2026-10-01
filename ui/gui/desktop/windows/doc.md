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
  runtime it needs, so a user installs nothing else first. It is an MSI made by Compose's own
  packaging through the JDK's jpackage, `./gradlew :ui:packageMsi`, which needs the WiX toolset;
  the plugin fetches WiX into the build's cache, so the machine that builds installs nothing. The
  runtime goes in whole rather than as a list of modules, since a module left out would fail only
  when the code needing it ran — for Bluetooth, during a download. The dive computer library is
  one of the application's resources there, `LOGIC-27`, the installer laying jars out differently
  from `installDist`. The launcher opens the window given no arguments and runs the command it is
  given otherwise, which is how an agent starts `yemoja api`; it has no console, so the terminal
  front end is not run from it. The installer, the exe and its shortcuts carry `ui/icons/yemoja.ico`,
  which `tool/icons.py` makes from the window's own `yemoja.svg`; the window draws the SVG itself.
- **WIN-2 — Code signing.** *Settled:* **none, for the first version.** Windows says on first run
  that the publisher is unknown, and *More info* then *Run anyway* starts it; README says so where
  it says how to install. Signing costs money every year and its warning fades only as an
  application earns a reputation anyway, so it waits for users beyond the author's own circle.
  Microsoft's own signing service is the cheapest real option when it comes, if it takes an
  individual in the author's country.
- **WIN-3 — Which Windows.** *Settled:* **Windows 10 and Windows 11.** Older versions are not
  supported.
- **WIN-4 — Bluetooth LE.** *Settled:* **what Windows 10 and 11 offer is enough**, and it is
  proven on one Windows 11 machine with its own adapter: a scan finds a computer and a download
  reads it. A Windows 10 machine has not been tried, which is a test owed before the first version
  rather than a question.

## Open questions

None for the first version.
