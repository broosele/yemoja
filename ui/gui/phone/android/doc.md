# GUI — phone — Android

**Second priority target**, after Windows desktop.

Only Android-specific matters belong here; see [../doc.md](../doc.md) for the phone
form factor.

## Scope

- Build, packaging, signing and distribution.
- The permission model, which is the most intrusive part of this target: Bluetooth
  scanning and connection permissions, how they changed across Android versions,
  and how to ask for them without alarming the user.
- Storage: where a logbook lives, and what scoped storage allows.
- Android conventions: back navigation, notifications for long downloads, staying
  alive during a dive computer download.
- Minimum and target API levels.

## Open questions

- **AND-1 — Distribution:** Play Store, direct APK, or F-Droid. The Play Store requires
   justifying Bluetooth and any location permission, which can be onerous.
- **AND-2 — Minimum Android version.** Older versions require a location permission for
   Bluetooth scanning, which is hard to explain to users and may be worth dropping.
- **AND-3 — Background downloads:** whether a download survives the app being backgrounded,
   and what that costs in complexity.
- **AND-4 — Test devices** — Bluetooth behaviour varies enough between manufacturers that
   emulator testing is not sufficient.
