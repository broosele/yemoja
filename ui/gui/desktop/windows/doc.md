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
  runtime it needs, so a user installs nothing else first. It is an MSI made through the JDK's
  jpackage, `./gradlew :ui:packageMsi`, from the app image Compose's packaging makes, `WIN-5`, which needs the WiX toolset;
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
- **WIN-5 — Whether the installer makes shortcuts unasked.** *Settled:* **it asks.** Decided on
  2026-10-04, at the author's word, who found a desktop shortcut made without being asked. After
  the folder is chosen a page offers a desktop shortcut and a Start menu one, each a box to tick,
  and only what is ticked is made. The Start menu's starts ticked and the desktop's unticked, at
  the author's word.

  It is jpackage's own `--win-shortcut-prompt`, which the Compose plugin has no setting for. So
  `packageMsi` keeps its name and its output and runs jpackage itself, on the app image the plugin
  makes and with the WiX the plugin fetched; `ui/build.gradle.kts` says so where it does it.
  jpackage ticks both and has no switch for either, so the build then takes the value that ticks
  the desktop's out of the installer, `ui/installer/desktop-shortcut-unticked.ps1`. A WiX fragment
  of our own in place of the template would do it too, and is far more to keep.

## Open questions

- **WIN-6 — A pinned icon keeps the old picture after an update.** Low importance, for later.
   Seen when the icon changed in 0.1.19 and 0.1.20: a shortcut pinned to the taskbar showed the
   old icon until it was unpinned and pinned again. Windows caches the icon by the exe's path,
   and an update replaces the exe at the same path, so nothing makes it look again; jpackage's
   installer does not say that icons have changed, as some installers do.

   The cheapest answer is the application's: on its first start after an update, noticed by
   remembering the version it last ran as, it tells the shell through JNA that icons have
   changed, `SHChangeNotify` with `SHCNE_ASSOCCHANGED`, and may run Windows' own
   `ie4uinit.exe -show`. The installer could do the same as a custom action, which is more work
   in the MSI. Whether either reaches a *pinned* icon varies between Windows builds, so the first
   step is to try `ie4uinit.exe -show` by hand the next time a pinned icon is stale, before
   building anything. Until then, unpinning and pinning again is the answer.
