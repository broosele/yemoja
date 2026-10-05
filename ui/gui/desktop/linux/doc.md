# GUI — desktop — Linux

Built from 0.1.17: a `.deb` and a `.tar.gz`, made in WSL and tried there without Bluetooth.

Only Linux-specific matters belong here; see [../doc.md](../doc.md) for the desktop
form factor.

## Scope

- Build and packaging, and the fact that "Linux" is many distributions.
- Desktop integration: launcher entry, icons, file associations.
- Where settings and logbooks live by convention.
- Bluetooth access, which on Linux goes through the system Bluetooth stack and is
  the least uniform part of this target.
- Which desktop environments and versions are actually supported.

## How it is built

jpackage makes a package only for the system it runs on, so the Linux packages are built on Linux,
or in WSL from the Windows checkout: `tool/linux-packages.sh`, given a JDK 21 and the
libdivecomputer source. It copies the tree to the Linux side, builds there, and copies the two
packages back beside the `.msi`, where `tool/release.py` takes them.

**libdivecomputer is built for Linux by `tool/libdivecomputer-linux.py`**, from the same tree and
commit as the Windows DLL, whose generated `configure` lets it build with the compiler and make
alone. USB is built in through libusb, as on Windows. **Classic Bluetooth through BlueZ is left
out**: BlueZ's library is GPL, which the application does not take on, and Bluetooth LE reaches
the system's stack through Kable, which talks to BlueZ over D-Bus rather than linking it. A
computer that speaks only classic Bluetooth is therefore not read on Linux. `LOGIC-27`.

The application keeps what it remembers, the last logbook and the copies of `JSON-28`, in
`~/.local/share/yemoja`, or under `XDG_DATA_HOME` where that is set: the XDG convention's place
for an application's data, as `LOCALAPPDATA` is on Windows. `DESK-12`.

**Tried in WSL, on Debian 12:** the archive unpacked and ran a command, the bundled library loaded
through JNA, and the window opened a logbook through WSL's own display and remembered it. Not
tried: installing the `.deb`, a desktop other than WSL's, and Bluetooth, which WSL does not have.

## Open questions

- **LNX-1 — Packaging.** *Settled:* **a `.deb` and a `.tar.gz`**, at the author's word. The `.deb`
   installs under `/opt/yemoja` with a menu entry and names its own dependencies, the system's
   libusb among them. The archive is the application folder, which runs from `bin/Yemoja` on any
   distribution once unpacked. Neither is sandboxed, so Bluetooth and a logbook anywhere need no
   permissions; Flatpak and Snap would, which is most of why they were passed over, with
   AppImage's extra tooling for little gain.
- **LNX-2 — Which distributions are supported.** *Settled:* **the Debian family through the
   `.deb`, and anything else through the archive** on a best-effort basis. Both carry their own
   Java runtime. The archive assumes what a desktop already has, libusb and the X11 and OpenGL
   libraries, rather than carrying them.
- **LNX-3 — Bluetooth LE** behaviour differs across stack versions; needs real testing. Open:
   nothing has been read over Bluetooth on Linux, WSL having no radio.
- **LNX-4 — Classic Bluetooth.** Open, and waiting for someone who needs it. What makes it GPL
   is BlueZ's library, not the radio: an RFCOMM connection is a kernel socket any program may
   open. So the application could open one itself through JNA and hand it to libdivecomputer as
   a custom stream, as it hands over Bluetooth LE, finding the device through BlueZ's D-Bus
   service as Kable does. The library's one other part is the lookup of a device's RFCOMM channel,
   which would be the first channel and then each in turn. A few days' work, and untestable here
   without a Linux machine and a computer that speaks only classic Bluetooth: an older Petrel or
   Perdix, or an OSTC 3.
