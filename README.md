# Yemoja

A dive logbook for desktop and phone.

## Goal

Users accumulate a lifetime of dives, and that item usually ends up locked inside
one vendor's application or one vendor's cloud. Yemoja aims to be the logbook that
outlives the app that wrote it.

- **Your data stays yours, and stays readable.** The logbook is stored in a plain
  text format you can open, read and correct by hand, without Yemoja and without a
  database.
- **Versioned.** Changes are recoverable, and the history is inspectable.
- **Offline first.** Everything works with no network. Backup and sync across your
  machines happen when a network is there, and never get in the way when it is not.
  The tide calculation is the exception: it fetches a day's tide when asked, and says
  so when it cannot.
- **Reads your dive computer**, including over Bluetooth.
- **Cross-platform.** Windows, Android, macOS, Linux and iPhone — in that order of
  priority.

What it covers: logging dives, dive sites, buddies and gear, plus dive planning with
decompression calculations.

Decompression output is a planning aid. It is never a substitute for a dive computer
or for proper training.

## Status

Early, and building. The documentation structure and the layer boundaries were settled
first, and the three layers are now written against them: the item model and its files,
the logbook operations, decompression and dive planning, and two front ends over them.
Only the JVM target is built.

The first version aims at a **local logbook**: dives and the items they refer to, in
readable files on one machine. Reading a dive computer and importing UDDF are built as
far as [features.md](features.md) records; history and syncing are designed and
specified but not written. The file format was settled with those in mind, so adding
them later is building, not rewriting.

What a reader should not expect yet: nothing is installable, the history and the syncing
the format was designed for are not written, and none of the four other platforms has been
built. Each is registered in features.md or in the layer document that owns it.

## Installing and running

**Releases are on [GitHub](https://github.com/broosele/yemoja/releases)**: a Windows installer,
for Windows 10 and 11, and an Android app, for Android 12 and later, both attached to each release.
The Mac, Linux and the iPhone come later. Building either, and building and running from source,
are below.

**A release is made as a draft first**, the installer and the app attached to it, and published
after. The repository's releases are immutable, so nothing can be attached once one is published,
and a tag a deleted release used cannot be used again. Tags are the bare version, `0.1.0`.
`tool/release.py` does all of it from the build's own version, and checks the attached files'
names before it publishes.

**The installer** is built with `./gradlew :ui:packageMsi`, which writes
`ui/build/compose/binaries/main/msi/Yemoja-<version>.msi`. It carries its own Java runtime and
the dive computer library, so nothing else need be installed first. It is not signed, so Windows
warns on first run that the publisher is unknown; *More info* then *Run anyway* starts it.
`WIN-1`, `WIN-2`. The installed `Yemoja.exe` opens the window, and given a command it runs that
command instead — `Yemoja.exe plan <file>` — except the terminal front end, which needs a
console the installed launcher does not have: use `installDist` for that.

**Building what exists** needs a JDK 21 and nothing else. The Gradle wrapper fetches its
own Gradle, and Gradle fetches the Kotlin compiler, so `./gradlew build` from the root is
the whole of it. Running the tests is in [testing.md](testing.md).

**Two front ends run**: the terminal one, over a logbook folder, and the window, which may be
given one or opened without.

```
./gradlew :ui:installDist             writes ui/build/install/yemoja
yemoja tui <logbook folder>           with that bin directory on the path
yemoja gui [<logbook folder>]         the window, the folder being optional
yemoja coverage <logbook folder>      how often each worked-out field has a value, testing.md

./gradlew :ui:gui --args="<folder>"   the window straight from the build, spaces and all
```

**Reading a dive computer needs libdivecomputer**, which is not built here. Tell the build
where it is, by a `LIBDIVECOMPUTER` variable or `-Plibdivecomputer=<folder>`, and an
installation carries it in `native/` beside the jars, where the application looks for it. A
build told nothing writes none, and that installation reads no dive computer. It stays a
separate file on purpose; see *Licensing* below and `LOGIC-27`.

`yemoja` on its own says which commands there are. The terminal front end must be started
from a real terminal, so a pipe or a redirect gets a message rather than a screen — which is
also why it cannot be run through Gradle, since Gradle gives a child process no terminal. The
window has no such need and can be. Both read and show; the window also makes items, edits
their fields, saves a dive plan, and writes what a download or an import brings in. See
[ui/tui/doc.md](ui/tui/doc.md) and
[ui/gui/doc.md](ui/gui/doc.md).

**Android is built too**: the app keeps a logbook in a folder the user picks, shows the
desktop's screens laid out for a phone, and reads a dive computer over Bluetooth. It needs the Android SDK, which
installs into any folder without elevation; tell the build where in a `local.properties` at the
root, `sdk.dir=<folder>`, which git ignores. Reading a dive computer needs libdivecomputer built
for Android with the SDK's NDK, which `tool/libdivecomputer-android.py` does from the library's
source; tell the build where it put it with `-Plibdivecomputer.android=<folder>`. A release build
is signed with the key in the folder `-Pyemoja.signing=<folder>` names, `AND-1`. Then

```
./gradlew :android:assembleDebug      writes android/build/outputs/apk/debug/android-debug.apk
```

What it does and does not do yet is in [ui/gui/phone/android/doc.md](ui/gui/phone/android/doc.md).
The native targets need a C++ toolchain and Developer Mode, and join when the platform they serve
is worked on.

Development prerequisites are per platform and are documented with each target — for
the current first priority, see [ui/gui/desktop/windows/doc.md](ui/gui/desktop/windows/doc.md).

## Architecture

Three layers, each depending only on the one below it.

```
ui        one of several front ends
   |
logic     everything the application does — a single implementation
   |
data      what the data is, and one or more places to keep it
```

- **[data](data/doc.md)** — items and the machinery that reads, writes, resolves
  and checks them, plus the contract a data source must meet, separate from any
  particular storage. It is told what an item type is rather than knowing; the
  descriptions come from the layer above. Several sources are possible; the only one
  planned is **[json](data/json/doc.md)**, plain files on disk.
- **[logic](logic/doc.md)** — the item model, logbook operations, dive planning
  and decompression, statistics, import. One implementation, no variants. Decompression is isolated and
  treated as safety-relevant.
- **[ui](ui/doc.md)** — several front ends over the same logic: a programmatic
  **[api](ui/api/doc.md)**, a raw **[tui](ui/tui/doc.md)**, and the
  **[gui](ui/gui/doc.md)** that is the real application, in a
  [desktop](ui/gui/desktop/doc.md) and a [phone](ui/gui/phone/doc.md) form factor.

The rule that makes this worth having: **a layer never reaches past the one directly
below it.** The logic layer offers a single access point, the *Universe*, and every front
end goes through it; none of them opens a file or knows where anything is kept.

### Repository layout

**One module per layer**, each with its own source and its own tests:

```
data/         the data layer — see data/doc.md
logic/        the logic layer — see logic/doc.md
ui/           the front ends — see ui/doc.md
android/      the Android app, an activity hosting what ui draws
tool/         development scripts that check the documentation and the fixtures
libraries/    reference data shipped with the application
manual/       the user manual, bundled and shown in the application
fixtures/     fixture logbooks — see fixtures/doc.md
```

Inside a module, source sits under `src/commonMain/kotlin` and tests under
`src/commonTest/kotlin`, with a further source set per target where a layer needs one —
in practice the interface, and the logic layer's `javaMain`, shared by the JVM and Android, where
libdivecomputer is reached. A layer's `doc.md` sits at the root of its module, above the source
rather than inside it.

**Modules are how the layering rule is kept.** A module declares what it depends on, so
the data layer cannot reach the logic layer by accident: there is nothing to reach, and
the build says so rather than a reviewer. The rule above stops being a convention.

The four directories that are not modules hold no source. `libraries/` and `manual/` are
shipped with the application and are dedicated to the public domain; everything else is
not — see *Licensing* below.

### Main dependencies

The language is **Kotlin**, and the interface is **Compose Multiplatform**. The data layer
takes **Okio** for file access, which common Kotlin has none of, behind an interface of its
own so that one file names it — see `DATA-86` in [data/doc.md](data/doc.md). The terminal
front end takes **Mordant** for raw keys and the terminal's size, which the JDK offers no way
to ask for — see `TUI-4` in [ui/tui/doc.md](ui/tui/doc.md). The logic layer takes **xmlutil**
for reading UDDF, common Kotlin having no XML reader — Apache 2.0, and there rather than in the
data layer, whose promise of no dependencies stands because a foreign format is not its business.
The terminal front end also takes JNA, through Mordant's JVM side, and the window takes
Compose's core icon set, with twelve icons copied from the extended one rather than shipping it
whole. The logic layer's JVM and Android side takes **libdivecomputer** for reading dive
computers — LGPL-2.1,
linked as a shared library so the rest of the application stays its own, which *Licensing* below
turns on — and **JNA** to call it, Apache 2.0 under its dual licence. libdivecomputer leaves
Bluetooth LE to the application, and that is **Kable**, Apache 2.0: Kotlin Multiplatform, so the
same library serves the JVM, Android and iOS, and on the desktop it reaches Windows and Linux
through the Rust library btleplug that it carries. `LOGIC-2` in [logic/doc.md](logic/doc.md)
puts all three below a port the layer declares, so each target answers the same port its own
way. The API front end takes the **MCP Kotlin SDK** to serve an agent the logbook's tools, MIT
for its earlier code and Apache 2.0 for what is added since, on the JVM side only; it brings
Ktor's server core and kotlinx serialization and coroutines with it, all Apache 2.0, and no Ktor
engine is declared since nothing is served over HTTP. Beside it, the **ACP Kotlin SDK** hosts the
agent the user installed, which is how their own account answers rather than one of ours: Apache
2.0 in what it publishes, while the repository it comes from says MIT, and either suits. See
`API-4` in [ui/api/doc.md](ui/api/doc.md) and `GUI-38` in [ui/gui/doc.md](ui/gui/doc.md).

**Building the installer** uses Compose's own packaging, which calls the JDK's jpackage, and
jpackage on Windows needs the **WiX toolset**: version 3, under the Microsoft Reciprocal License.
It is a build tool, fetched by the Compose plugin into the build's own cache the first time an
installer is made, and installed on nothing. Its reciprocal terms reach changes to WiX's own
source files, which this project makes none of; the installer it writes compiles in WiX's standard
dialogs and carries no WiX code to run. See `WIN-1` in
[ui/gui/desktop/windows/doc.md](ui/gui/desktop/windows/doc.md).

**Building for Android** takes Google's **Android Gradle Plugin**, Apache 2.0, the only way
Gradle builds an Android app, and the app takes **androidx activity-compose**, Apache 2.0, which
gives an activity a Compose window to draw in. `AND-7` in
[ui/gui/phone/android/doc.md](ui/gui/phone/android/doc.md).

**Why, and against what.** The five targets are not equal — Android matters more than
iPhone here — and Kotlin is Android's own language rather than a target it compiles to.
The desktop three run on the JVM, and iPhone, which is last in line, is also the youngest
part of the toolkit; that ordering is the point rather than a compromise.

Two costs are accepted. Desktop builds carry a runtime, so installers are larger than a
compiled binary's. And reaching a C library takes more plumbing than a language with
first-class foreign bindings — which matters because libdivecomputer must be linked
dynamically for the licence reasons below, on the JVM, on Android and on iOS by three
different routes.

Two things it buys beyond the language. The terminal front end has real libraries to build
on. And nothing about the item model changed when the language did — see *Why a
description rather than a class* in [data/doc.md](data/doc.md), where the argument
was rewritten to stop resting on Dart's lack of reflection, which was never the load
it carried.

Storage needs nothing: versioning is Yemoja's own, so no version control library is
involved — see [data/json/doc.md](data/json/doc.md).

## Code style

- **Documentation lives with what it describes.** Each layer and each platform has a
  `doc.md` covering that level and nothing more. A fact has exactly one home;
  everywhere else links to it.

  *With* means at the level it describes rather than in the folder the source sits in.
  The documents are a tree of their own for two reasons. A subject's code is not in one
  place — a front end is split across a source set per target, so the terminal interface
  is under both `commonMain` and `jvmMain` — and there is no one directory to be beside.
  And a level is documented before it is built:
  [ui/gui/phone/iphone/doc.md](ui/gui/phone/iphone/doc.md) describes an application
  nobody has started.
- **User documentation is a separate set**, in `manual/`, written for users. It owns
  the description of the file format and of every data field; internal documents
  reference it rather than restating them.
- **Features are registered, not remembered.** Anything deferred goes in
  [features.md](features.md) with a status, so setting an idea aside does not lose it.
- **Open questions carry stable identifiers** — `DATA-3`, `GUI-3`, `RECON-1` — so they
  can be referred to in discussion. An identifier is never reused and never
  renumbered; a settled question keeps its number and records the answer.
- **Detail belongs at the narrowest level it applies to.** General GUI structure goes
  in `ui/gui/doc.md`, phone layout in `ui/gui/phone/doc.md`, Android specifics in
  `ui/gui/phone/android/doc.md`.
- **Every feature has tests.** The full suite passes before a commit.
- **One commit per feature or fix.** Unrelated changes are not bundled together.
- **Documentation is updated in the same commit as the change that affects it.**
- **No private data in the repository** — no real logs, names, addresses, credentials
  or device identifiers. Test data is invented.
- **No formatter or linter is configured.** The language is settled and this is not, so the
  conventions are what the code already does and a reviewer is what enforces them.

## Documentation

| Document | Covers |
|---|---|
| [features.md](features.md) | What the application is meant to do, and what is deferred |
| [glossary.md](glossary.md) | Every term with a specific meaning, and which document owns it |
| [data/doc.md](data/doc.md) | Item model, data source contracts |
| [data/json/doc.md](data/json/doc.md) | The JSON file source |
| [data/libraries.md](data/libraries.md) | Read-only reference data shipped with the application |
| [libraries/doc.md](libraries/doc.md) | The library files themselves: layout, format, what belongs |
| [data/json/requirements.md](data/json/requirements.md) | What storage must do: versioning, sync, backup, conflicts |
| [logic/doc.md](logic/doc.md) | Application behaviour, planning, decompression |
| [logic/reconciliation.md](logic/reconciliation.md) | Merging sync, dive computer and file import into the logbook |
| [logic/uddf.md](logic/uddf.md) | UDDF field by field against this model: what maps, what is missing, what is declined |
| [logic/divinglog.md](logic/divinglog.md) | Diving Log's database against this model: its tables, what maps, what is left |
| [ui/doc.md](ui/doc.md) | Rules common to all front ends |
| [ui/api/doc.md](ui/api/doc.md) · [ui/tui/doc.md](ui/tui/doc.md) · [ui/gui/doc.md](ui/gui/doc.md) | The individual front ends |
| [manual/doc.md](manual/doc.md) | User manual — conventions and what is written |
| [manual/data-fields.md](manual/data-fields.md) | **The definition of every data field**, written for users |
| [manual/settings.md](manual/settings.md) | The settings files: app-owned, not part of the data format |
| [manual/data-format.md](manual/data-format.md) | How a logbook is written to disk, written for users |
| [manual/getting-started.md](manual/getting-started.md) · [manual/decompression.md](manual/decompression.md) · [manual/computers-and-importing.md](manual/computers-and-importing.md) · [manual/uddf.md](manual/uddf.md) · [manual/app-info.md](manual/app-info.md) | The rest of the manual's chapters |
| [fixtures/doc.md](fixtures/doc.md) | Fixture logbooks used by tests |
| [testing.md](testing.md) | The test suite: the conventions that hold across all three modules |
| [ui/gui/desktop/doc.md](ui/gui/desktop/doc.md) | Desktop form factor — [windows](ui/gui/desktop/windows/doc.md), [mac](ui/gui/desktop/mac/doc.md), [linux](ui/gui/desktop/linux/doc.md) |
| [ui/gui/phone/doc.md](ui/gui/phone/doc.md) | Phone form factor — [android](ui/gui/phone/android/doc.md), [iphone](ui/gui/phone/iphone/doc.md) |

Each `doc.md` ends with the questions still open at that level.

`manual/` is separate: user documentation, bundled with the application and rendered
in its information tab. It is written for users rather than for whoever builds this,
and it never links back into the documents above. Its conventions are in
[manual/doc.md](manual/doc.md).

## Licensing

Four files carry the actual terms; what follows explains them. Nothing here has been
checked by a lawyer.

- [LICENSE](LICENSE) — the repository, all rights reserved.
- [manual/LICENSE](manual/LICENSE) — the manuals, CC0.
- [libraries/LICENSE](libraries/LICENSE) — the shipped data, CC0.
- [ui/src/commonMain/kotlin/yemoja/ui/icons/LICENSE](ui/src/commonMain/kotlin/yemoja/ui/icons/LICENSE)
  — eleven Material icons copied from Compose, Apache 2.0, as they came. `DESK-11`.

Contributions are not accepted for the time being, so no terms are offered for them.

### Free, so that a logbook outlives the application

A logbook is only yours if someone else can read it. Two things are therefore released
under **CC0**, a public domain dedication carrying no conditions at all:

- **The user manuals** — everything in [manual/](manual/doc.md) that ships to a reader,
  the data model's documentation among them.
- **The libraries** — [libraries/](libraries/doc.md). Without them a logbook that refers
  to a region or a certification is incomplete, so withholding them would withhold part
  of the data itself.

Anyone may use these for anything, including a competing product, without asking and
without crediting anyone. That is the point: a promise that costs nothing to make is not
a promise.

### The format itself

The specification is a document, and documents are plainly covered by copyright. Whether
the *format* is — the field names, how they are arranged, the syntax that holds them —
is a different question, and an unsettled one. It looks far more like a method than a
work, and where there is only one sensible name for a thing, the name and the thing tend
to merge. Courts have not entirely agreed with each other about where that leaves an
interface.

Rather than rely on that, plainly: **Yemoja asserts no right over the format and no
patent over it.** Read it, implement it, extend it, build something that competes.
Nobody needs permission and nobody needs to ask for it.

The reason to say so out loud is practical. An implementer who cannot tell whether they
are allowed will not usually pay a lawyer to find out — they will simply do something
else, and a format nobody else implements is not really a format. Silence reads as
reserved.

Everything else goes with the source, including the test fixtures in `fixtures/` and the
internal documents — the per-layer `doc.md` files, and `manual/doc.md`, which is about
writing the manual rather than part of it.

### Source-available, for now

Everything else — the application and its supporting code — is published to be read,
not to be used, copied or changed without permission.

This is **not** open source, and the project should not call it that. Open source
grants everyone, in advance and without asking, the right to use the software for any
purpose, to modify it and to redistribute it. Requiring permission is precisely what
disqualifies it. *Source available* describes the arrangement accurately and costs
nothing.

Published source with no licence at all is worse than either: it reserves every right,
so a reader may not legally compile or run it. Whatever terms are chosen have to say
what a reader *may* do, or the invitation to read is empty.

No date is set for relaxing this, and nothing binds the project to relaxing it at all.
The question is deliberately left until the first public release: until then there is no
reader relying on the answer, and no contributor to whom it would be a promise. A reader
in the meantime should assume the terms are what they say and nothing more.

### What the dependencies would force

The terms are chosen — all rights reserved, with CC0 for `manual/` and `libraries/` — and the
 constraints below are what the dependencies force on them. The first is answered already:
 libdivecomputer is shipped as a shared library beside the jars rather than linked in.

- **libdivecomputer is LGPL-2.1.** Linked as a shared library, the rest of the
  application stays proprietary. Linked statically, relinking obligations follow.
  Either way a user must be able to replace it, and changes to it stay LGPL.
- **Decompression implementations are the real hazard.** The Bühlmann algorithm and
  its coefficients are published science and not copyrightable; the widely available
  *implementations* are GPL. An engine written from the published tables is clean;
  one derived from existing source is not, and would take the whole application
  with it.
- **Kotlin, Compose Multiplatform and most of the ecosystem** are Apache 2.0, and
  impose nothing beyond attribution.
- **The library data is in the public domain**, so nothing attaches to it. Any data
  added later has to be checked the same way: a share-alike source would carry its
  obligation into the libraries and collide with releasing them freely.

### Trademarks

Yemoja is not affiliated with, endorsed by, or connected to any diving agency,
manufacturer or operator named anywhere in this project or in the data it ships.

Agency and product names appear because the data is about those things: a user's Open
Water card cannot be recorded without naming who issued it. They are used to refer to
the real qualifications and equipment and for no other purpose. All trademarks belong to
their owners. No logos or stylised marks are used, and none should be added.

### Settled

All six kept with their identifiers so earlier discussion still resolves.

- **LIC-1** — CC0 for the manuals and the libraries. No attribution required. The
  fixtures are not included.
- **LIC-2** — No change date, and no commitment to one. Revisit before the first
  public release.
- **LIC-3** — The library data carries no obligations. Its provenance is recorded per
  library in [libraries/doc.md](libraries/doc.md), and anything whose source cannot be
  established is discarded rather than kept.
- **LIC-4** — Only the manuals are free. Internal documents go with the source.
- **LIC-5** — Agency names stay, with a non-affiliation notice and no logos.
- **LIC-6** — The format itself is disclaimed explicitly, rather than left to the
  argument about whether it could have been owned. No copyright asserted, no patent.
