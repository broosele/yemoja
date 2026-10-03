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

## What is built

**It builds, it starts, and a phone is laid out as one.** `./gradlew :android:assembleDebug`
writes a debug app. Tried on the SDK's emulator, Android 16, and on no phone yet.

**A logbook lives in a folder the user picks**, `AND-5`. New and Open are one question on a phone,
which folder, asked through Android's own picker; a folder holding a logbook is opened, and one that
holds none is made into one. The grant is kept and the folder remembered, so the app opens on it
again. It is reached through the storage access framework, by `GrantedFileStore`, whose answers
about a folder's children are kept until something in it changes. **A folder is listed whole**: a
cloud provider such as Google Drive's answers in parts, marking the list as still loading or giving
one page of a longer one, and the first answer taken as the whole once read a logbook of 347 dives
as 200. So the list is asked for page after page, while the provider counts more than it gave and
otherwise until a page adds nothing, Drive giving pages of 200 and no count; and while the list is
loading its answer is held open until the provider says it changed, then asked for again, the old
answer closed only once the new one is open, as Android's own file browser does. A folder still
loading after two minutes is refused rather than read in part. Asking afresh every quarter second,
as 0.1.3 to 0.1.5 did, and closing the answer before asking again, as 0.1.6 and 0.1.7 did, each
timed out on a phone: Drive said the list had changed every three seconds and gave the same 200
each time, as a provider would that starts its fetch over for every new question. A refusal, since
0.1.7, **says what the listing saw**: which folder,
how often it was asked for, how many entries the provider gave and how many it counted, how often
it said the list had changed, and whatever else its answer carried, with the app's version after
it. A phone's log is out of most users' reach, and the refusal is the one message they can send
back. **Unchanged files come from a copy** in the app's own storage, the listing's date and size
saying which, `JSON-28`. **What the copy lacks is fetched eight files at a time** before the reading
asks for each in turn, the screen counting them as they come: a first opening from Google Drive is
some hundreds of requests to Drive's own app, and one after another they took minutes. **A logbook is opened off the screen's thread**, the screen saying so
meanwhile, since reading one from a cloud drive can take a while and a blank screen reads as a
broken app. The libraries the app ships are read from inside it, and since a folder inside an app
cannot be listed the build writes an index of them, which is how a new logbook learns what to
declare. A review and an agent's changes are staged in the app's own storage, the grant reaching
nothing beside the folder. No lock is taken, `JSON-27`.

**On a phone, one page at a time**, `PHONE-2`. The tab row is the open tab with a menu of the
others, back, and add, edit and delete; the agent's button is not there, `PHONE-1`. A tab shows
its list, and choosing opens the item full-screen. Locations goes from the regions to a region's
own page, its map, what is at it and the region itself, and from there to a site. Back,
the arrow or the phone's own, steps out one page at a time, cancelling a form on the way. Fields
stand one to a row. Calculations is chosen from a list above the form, the planner's settings,
gases, runtime and contingency stand one under another with the gases' table scrolling sideways,
and the model's warning scrolls with the form it heads. A tablet keeps the desktop's layout, `PHONE-3`,
told apart by the shorter side of its screen, 600 or more.

The app is one activity in `android/`, which hands the screen to `Yemoja` in `ui/src/androidMain`;
everything the platform supplies is there. The manual and the map travel inside the app as they do
in the desktop's jar.

**A dive computer is read over Bluetooth**, `AND-6`. libdivecomputer is built for Android by
`tool/libdivecomputer-android.py`, which compiles its sources with the NDK's compiler for a phone's
processor and the emulator's, USB, IrDA and classic Bluetooth left as the library's own stubs; the
app carries it as a file of its own, as the desktop does. The code reaching it is the desktop's,
shared through the logic layer's `javaMain`, JNA taken as its Android archive. Bluetooth is asked
for when Download is pressed, with notifications beside it, and a refusal says where to allow it,
`AND-2`. A computer that wants the code it is showing typed is given a dialog for it, `LOGIC-24`:
the read waits on its own thread while the screen's asks, and leaving the dialog answers nothing.
Tried on the emulator with a question put from another thread, there being no computer there to
put one. While a read runs, a foreground service keeps the app alive with a notification showing
its progress and a Cancel, `AND-3`. Tried on the emulator as far as an emulator goes: the
permissions are asked, the library loads, and the scan finds nothing, there being no dive
computer to find. No read has been made on a phone yet.

**Released signed, as 0.1.0**, `AND-1`. A release build signs with `yemoja.jks` from the folder
the property `yemoja.signing` names, kept outside the repository with its password beside it; its
certificate names Yemoja and nobody else, since anyone can read it off the app. An update installs
only over an app signed with the same key, so that folder is kept and backed up. A debug build
keeps Android's own debug key, and the two cannot be installed over each other.

**The launcher icon is the window's drawing**, in Android's adaptive form: the tile's blue behind,
the sea and the fish in front, and the same shapes alone for a phone that tints its icons.
`tool/icons.py` writes it from `yemoja.svg`, drawn at a little over the part a launcher shows, so
the sea reaches the mask's edges and the fish stays clear of them.

**Import and export go through Android's pickers for a file**, `AND-9`. Tried on the emulator with
a logbook of 347 dives: the export was written to a file named in the picker, ten megabytes of
UDDF, and picking that file again opened the review with each dive matched to the one it came from.

What a tooltip says over a greyed button is said on a long press, the toolkit's own answer on a
touch screen.

## Settled

Decided on 2026-10-01, before anything Android is built. Nothing below is built yet.

- **AND-5 — Where a logbook lives.** *Settled:* **in a folder the user picks.** A logbook is a
  folder of readable files, and that is the point of the format: the user can see it, copy it
  and sync it with whatever they already use. Android gives an app no free path to a shared
  folder, so the folder is reached through the storage access framework, as a tree the user
  grants once. That needs a second file store beside the desktop's. App-private storage, reached
  by import and export, was the cheaper answer and hides the logbook from the user, which is the
  one thing the format exists not to do.
- **AND-1 — Distribution.** *Settled:* **an installable file published directly, first.** The
  same footing as the Windows installer, `WIN-1`: built here, published with the release, and
  signed with a key of the author's own, which Android requires of every app and which costs
  nothing. The key is kept outside the repository. The Play Store and F-Droid can follow once
  the app has been tried.
- **AND-2 — Minimum Android version.** *Settled:* **Android 12.** From 12 on, scanning for and
  connecting to a Bluetooth device have permissions of their own; before it a scan needs the
  location permission, which is hard to explain to a user and asks for more than is used.
  Phones from before about 2021 are left out.
- **AND-3 — Background downloads.** *Settled:* **a download carries on, with a notification.**
  A slow Bluetooth computer takes a quarter of an hour, which nobody watches with the screen on.
  The read runs in a foreground service whose notification shows its progress and a Cancel, as
  the desktop shows them on Home, `GUI-52`.
- **AND-6 — Which connections are read.** *Settled:* **Bluetooth only.** Every computer met so
  far talks Bluetooth LE, and the Bluetooth library already in use carries it on Android. A USB
  or serial cable on a phone needs an adapter, a USB-serial library and testing per chip, for
  few users.
- **AND-7 — What builds it.** *Settled:* **the Android Gradle Plugin and the Android SDK.**
  Google's plugin, Apache 2.0, is the only way Gradle builds an Android app, and it was approved
  as a dependency on that ground. The SDK is a build tool installed on the machine that builds,
  as WiX is for the installer, and carries nothing into the app. README lists the plugin among
  the dependencies once the build takes it.
- **AND-8 — Whether the app may reach a network.** *Settled:* **it may, for a tide.** Decided on
  2026-10-03, at the author's word. The Tides calculation fetches a day's readings from
  Rijkswaterstaat, `LOGIC-44`, and on Android nothing opens a connection without the `INTERNET`
  permission. It is one the system grants at installation and never asks the user about, so it
  costs no prompt. What it costs is that the app could not reach a network at all before, and
  now can. What is sent is a station's code and a date; nothing of the logbook is.
- **AND-9 — How a file is imported and exported.** *Settled:* **through Android's pickers for a
  document, with a copy in the app's own storage in between.** Decided on 2026-10-03, when the
  author found both deeds greyed on a phone.

  **A picked document has no path.** It is reached through whichever app provides it, a file
  manager or a cloud drive, and the logic layer reads and writes by path. So an import copies what
  was picked into the app's cache and reads the copy, and an export is written into the cache and
  copied out to the document the user named. The copy is under the document's own name, and what
  the home screen says names that and not the path of the copy. The cache is the system's to
  clear, and nothing is read from it twice: what an import staged is kept in the app's files.

  **An import is a file, not a folder.** Android has one picker for a document and another for a
  folder, and no picker for either. A UDDF file and a Diving Log database are files, and those are
  what arrives on a phone. Another Yemoja logbook is a folder and is not offered here: it is
  imported on a desktop, or opened as the logbook it is.

  **Any file is offered, none filtered out.** Neither a UDDF document nor a Diving Log database
  has a type Android knows, so a filter by type would hide exactly the files wanted. What is
  picked says what it is when it is read, as on a desktop, `GUI-33`.

  **An export is made untyped, as `logbook.uddf`** until the user names it otherwise. Made as XML,
  some providers would put `.xml` after the name.

  **Both run off the screen's thread.** Android stops an app that keeps its screen waiting five
  seconds, and reading ten megabytes of UDDF takes longer than that. The home screen says
  *Reading logbook.uddf…* until the review opens, and the Import deed is greyed meanwhile.
- **AND-4 — Test devices.** *Settled:* **the author's phone and the SDK's emulator.** The
  emulator for the screens and the storage picker during development; the phone, on Android 12
  or later, for every download, since Bluetooth is proven only on real hardware.
