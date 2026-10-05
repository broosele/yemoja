# GUI — phone — iPhone

Future, not started: put off on 2026-10-05 until there is a Mac and an iPhone to try it on,
`IOS-4`. The last of the five targets.

Only iOS-specific matters belong here; see [../doc.md](../doc.md) for the phone
form factor.

## Scope

- Build, signing, provisioning and App Store distribution.
- Bluetooth permission and usage descriptions, and Apple's rules about them.
- Storage: where a logbook lives, and how it can be shared with or backed up from
  a device.
- iOS conventions: navigation, safe areas, notifications, background execution
  limits — which are stricter than Android's and may constrain downloads.
- Minimum supported iOS version.

## What it would take

Found when it was asked about, so the next look starts here rather than from nothing. A new target
rather than a new package of an old one, which is what sets it apart from the Mac.

- **Already there.** The data model, the logic and the decompression model are common code, and
  the website's planner already runs the logic off the JVM. Compose draws on iOS and the phone's
  layout exists. Kable reaches Bluetooth on iOS.
- **Files again.** What `DiskFileStore` and Android's `GrantedFileStore` do, through the Files
  picker and a bookmark that keeps a folder in iCloud Drive reachable between launches.
- **libdivecomputer again.** JNA is the JVM's, so on iOS the library is built in statically and
  called through Kotlin/Native's C interop: a second `Library.kt` and `Structs.kt`.
- **The platform's hooks** that Android answers in `Phone.kt`: pickers, keeping awake, sharing a
  file, the date, and posting to the network for the tides.
- **No USB.** A computer is read over Bluetooth or not at all.
- **A Mac to build on.** Kotlin/Native compiles for iOS only on macOS; a macOS machine in CI could
  build it, but nothing could be tried without a Mac and an iPhone.

## Open questions

- **IOS-1 — App Store review** is the main risk: Bluetooth use, background behaviour, and
   any data export need to be justifiable.
- **IOS-2 — Apple Developer Program membership** is required to run on real hardware at all.
- **IOS-3 — Background execution limits** may make a long dive computer download impossible
   unless the app stays in the foreground. Needs early investigation.
- **IOS-4 — Whether development hardware, a Mac and an iPhone, is available.**
