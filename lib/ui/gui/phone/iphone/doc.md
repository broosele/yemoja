# GUI — phone — iPhone

Planned, not started. The last of the five targets.

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

## Open questions

- **IOS-1 — App Store review** is the main risk: Bluetooth use, background behaviour, and
   any data export need to be justifiable.
- **IOS-2 — Apple Developer Program membership** is required to run on real hardware at all.
- **IOS-3 — Background execution limits** may make a long dive computer download impossible
   unless the app stays in the foreground. Needs early investigation.
- **IOS-4 — Whether development hardware, a Mac and an iPhone, is available.**
