# Yemoja

A dive logbook for desktop and phone.

## Goal

Divers accumulate a lifetime of dives, and that item usually ends up locked inside
one vendor's application or one vendor's cloud. Yemoja aims to be the logbook that
outlives the app that wrote it.

- **Your data stays yours, and stays readable.** The logbook is stored in a plain
  text format you can open, read and correct by hand, without Yemoja and without a
  database.
- **Versioned.** Changes are recoverable, and the history is inspectable.
- **Offline first.** Everything works with no network. Backup and sync across your
  machines happen when a network is there, and never get in the way when it is not.
- **Reads your dive computer**, including over Bluetooth.
- **Cross-platform.** Windows, Android, macOS, Linux and iPhone — in that order of
  priority.

What it covers: logging dives, dive sites, buddies and gear, plus dive planning with
decompression calculations.

Decompression output is a planning aid. It is never a substitute for a dive computer
or for proper training.

## Status

Early, and deliberately so. The documentation structure and the layer boundaries are
being settled before any implementation starts, so the repository holds documentation,
the layer skeleton, test fixtures and the tooling that checks the documentation —
and no application code at all.

The first version aims at a **local logbook**: dives and the items they refer to, in
readable files on one machine. History, syncing, dive computer downloads and importing
are designed and specified but not in it — see [features.md](features.md). The file
format is settled with those in mind, so adding them later is building, not rewriting.

An earlier attempt at scaffolding was removed rather than kept: it was written before
these decisions and would only have misled anyone reading it.

## Installing and running

Nothing is installable yet. This section will cover, per platform, how to install a
release and how to build and run from source, once there is something to run.

Development prerequisites are per platform and are documented with each target — for
the current first priority, see [lib/ui/gui/desktop/windows/doc.md](lib/ui/gui/desktop/windows/doc.md).

## Architecture

Three layers, each depending only on the one below it.

```
ui        one of several front ends
   |
logic     everything the application does — a single implementation
   |
data      what the data is, and one or more places to keep it
```

- **[data](lib/data/doc.md)** — items and the machinery that reads, writes, resolves
  and checks them, plus the contract a data source must meet, separate from any
  particular storage. It is told what an item type is rather than knowing; the
  descriptions come from the layer above. Several sources are possible; the only one
  planned is **[json](lib/data/json/doc.md)**, plain files on disk.
- **[logic](lib/logic/doc.md)** — the item model, logbook operations, dive planning
  and decompression, statistics, import. One implementation, no variants. Decompression is isolated and
  treated as safety-relevant.
- **[ui](lib/ui/doc.md)** — several front ends over the same logic: a programmatic
  **[api](lib/ui/api/doc.md)**, a raw **[tui](lib/ui/tui/doc.md)**, and the
  **[gui](lib/ui/gui/doc.md)** that is the real application, in a
  [desktop](lib/ui/gui/desktop/doc.md) and a [phone](lib/ui/gui/phone/doc.md) form factor.

The rule that makes this worth having: **a layer never reaches past the one directly
below it.** The logic layer offers a single access point, the *Universe*, and every front
end goes through it; none of them opens a file or knows where anything is kept.

### Repository layout

One Dart package. The three layers are directories under `lib/`, which is where Dart
expects a package's source, and the test suite mirrors them under `test/` — Dart offers
no way to keep tests beside the code they cover.

```
lib/          the three layers above, one directory each
test/         the suite, mirroring lib/ — see test/doc.md
tool/         development scripts that check the documentation and the fixtures
libraries/    reference data shipped with the application
manual/       the user manual, bundled and shown in the application
testdata/     fixture logbooks
```

The four directories outside `lib/` and `test/` are not Dart source. `libraries/` and
`manual/` are shipped with the application and are dedicated to the public domain;
everything else is not — see *Licensing* below.

### Main dependencies

The language is Dart, which is what the repository layout above assumes. The rest is
not settled: a cross-platform UI toolkit for the GUI, a library for reading dive
computers, and a Bluetooth LE library per platform. Each will be recorded with the layer
that needs it, once chosen.

Storage needs nothing: versioning is Yemoja's own, so no version control library is
involved — see [lib/data/json/doc.md](lib/data/json/doc.md).

## Code style

- **Documentation lives with what it describes.** Each layer and each platform has a
  `doc.md` covering that level and nothing more. A fact has exactly one home;
  everywhere else links to it.
- **User documentation is a separate set**, in `manual/`, written for divers. It owns
  the description of the file format and of every data field; internal documents
  reference it rather than restating them.
- **Features are registered, not remembered.** Anything deferred goes in
  [features.md](features.md) with a status, so setting an idea aside does not lose it.
- **Open questions carry stable identifiers** — `DATA-3`, `GUI-2`, `RECON-1` — so they
  can be referred to in discussion. An identifier is never reused and never
  renumbered; a settled question keeps its number and records the answer.
- **Detail belongs at the narrowest level it applies to.** General GUI structure goes
  in `lib/ui/gui/doc.md`, phone layout in `lib/ui/gui/phone/doc.md`, Android specifics in
  `lib/ui/gui/phone/android/doc.md`.
- **Every feature has tests.** The full suite passes before a commit.
- **One commit per feature or fix.** Unrelated changes are not bundled together.
- **Documentation is updated in the same commit as the change that affects it.**
- **No private data in the repository** — no real logs, names, addresses, credentials
  or device identifiers. Test data is invented.
- Formatting and language-level conventions will be fixed, and enforced
  automatically, when the implementation language is settled.

## Documentation

| Document | Covers |
|---|---|
| [features.md](features.md) | What the application is meant to do, and what is deferred |
| [glossary.md](glossary.md) | Every term with a specific meaning, and which document owns it |
| [lib/data/doc.md](lib/data/doc.md) | Item model, data source contracts |
| [lib/data/json/doc.md](lib/data/json/doc.md) | The JSON file source |
| [lib/data/libraries.md](lib/data/libraries.md) | Read-only reference data shipped with the application |
| [libraries/doc.md](libraries/doc.md) | The library files themselves: layout, format, what belongs |
| [lib/data/json/requirements.md](lib/data/json/requirements.md) | What storage must do: versioning, sync, backup, conflicts |
| [lib/logic/doc.md](lib/logic/doc.md) | Application behaviour, planning, decompression |
| [lib/logic/reconciliation.md](lib/logic/reconciliation.md) | Merging sync, dive computer and file import into the logbook |
| [lib/logic/uddf.md](lib/logic/uddf.md) | UDDF field by field against this model: what maps, what is missing, what is declined |
| [lib/ui/doc.md](lib/ui/doc.md) | Rules common to all front ends |
| [lib/ui/api/doc.md](lib/ui/api/doc.md) · [lib/ui/tui/doc.md](lib/ui/tui/doc.md) · [lib/ui/gui/doc.md](lib/ui/gui/doc.md) | The individual front ends |
| [manual/doc.md](manual/doc.md) | User manual — conventions and what is written |
| [manual/data-fields.md](manual/data-fields.md) | **The definition of every data field**, written for users |
| [manual/settings.md](manual/settings.md) | The settings files: app-owned, not part of the data format |
| [manual/data-format.md](manual/data-format.md) | How a logbook is written to disk, written for users |
| [testdata/doc.md](testdata/doc.md) | Fixture logbooks used by tests |
| [test/doc.md](test/doc.md) | The test suite: how it mirrors `lib/`, and what belongs in it |
| [lib/ui/gui/desktop/doc.md](lib/ui/gui/desktop/doc.md) | Desktop form factor — [windows](lib/ui/gui/desktop/windows/doc.md), [mac](lib/ui/gui/desktop/mac/doc.md), [linux](lib/ui/gui/desktop/linux/doc.md) |
| [lib/ui/gui/phone/doc.md](lib/ui/gui/phone/doc.md) | Phone form factor — [android](lib/ui/gui/phone/android/doc.md), [iphone](lib/ui/gui/phone/iphone/doc.md) |

Each `doc.md` ends with the questions still open at that level.

`manual/` is separate: user documentation, bundled with the application and rendered
in its information tab. It is written for divers rather than for whoever builds this,
and it never links back into the documents above. Its conventions are in
[manual/doc.md](manual/doc.md).

## Licensing

Three files carry the actual terms; what follows explains them. Nothing here has been
checked by a lawyer.

- [LICENSE](LICENSE) — the repository, all rights reserved.
- [manual/LICENSE](manual/LICENSE) — the manuals, CC0.
- [libraries/LICENSE](libraries/LICENSE) — the shipped data, CC0.

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

Everything else goes with the source, including the test fixtures in `testdata/` and the
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

Nothing is chosen yet, so nothing is binding yet. The constraints to plan around:

- **libdivecomputer is LGPL-2.1.** Linked as a shared library, the rest of the
  application stays proprietary. Linked statically, relinking obligations follow.
  Either way a user must be able to replace it, and changes to it stay LGPL.
- **Decompression implementations are the real hazard.** The Bühlmann algorithm and
  its coefficients are published science and not copyrightable; the widely available
  *implementations* are GPL. An engine written from the published tables is clean;
  one derived from existing source is not, and would take the whole application
  with it.
- **Flutter, Dart and most of the package ecosystem** are BSD, MIT or Apache, and
  impose nothing.
- **The library data is in the public domain**, so nothing attaches to it. Any data
  added later has to be checked the same way: a share-alike source would carry its
  obligation into the libraries and collide with releasing them freely.

### Trademarks

Yemoja is not affiliated with, endorsed by, or connected to any diving agency,
manufacturer or operator named anywhere in this project or in the data it ships.

Agency and product names appear because the data is about those things: a diver's Open
Water card cannot be recorded without naming who issued it. They are used to refer to
the real qualifications and equipment and for no other purpose. All trademarks belong to
their owners. No logos or stylised marks are used, and none should be added.

### Settled

All five kept with their identifiers so earlier discussion still resolves.

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
