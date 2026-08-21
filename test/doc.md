# Tests

The Dart test suite. Every feature has tests, and a feature is not finished without
them.

## Why it sits here and not beside the code

Dart decides this rather than the project. `dart test` and `flutter test` look in
`test/` and nowhere else, and a `*_test.dart` file left under `lib/` is published with
the package. Co-locating tests with the code they cover is therefore not available, so
the suite is one tree at the root of the package, mirroring `lib/`.

## Structure

```
test/
  doc.md          this file
  data/           mirrors lib/data
  logic/          mirrors lib/logic
  ui/             mirrors lib/ui
```

A test file is named after the file it covers, with `_test.dart` in place of `.dart`,
and sits at the matching place in the mirror. Anything shared between tests of one
layer lives with that layer; anything shared across layers lives directly here.

The mirror is the point: given a source file, there is exactly one place its tests can
be, and a directory in `lib/` with no counterpart here is visible at a glance.

## What belongs here

- **Unit tests** for the [data](../lib/data/doc.md) and [logic](../lib/logic/doc.md)
  layers. These are the bulk of it, and they need no interface and no files on disk —
  the data layer is designed so items can be assembled in memory.
- **Tests of the front ends** that do not need a running application: that a
  description names the fields it should, that an intent maps to the right operation.

## What does not

- **Fixture logbooks.** They are in [../testdata/doc.md](../testdata/doc.md) — see
  `TEST-1` below, which asks whether that is right now this directory exists.
- **Checks on the documentation and the fixtures.** `tool/checklinks.py` and
  `tool/checkdata.py` verify that links resolve, that JSON examples parse *and name only
  fields the manual still defines*, that every
  field in the fixtures and libraries is one the manual defines, that every reference
  and key reference finds its target, that no id names two items, and that fixed sets
  and numeric ranges hold. The last two read their vocabularies out of the manual's
  prose, so the manual stays the only place a value is written down. They are Python,
  so `dart test` cannot run them — see `TEST-2`.
- **Anything requiring a device.** Reading a real dive computer over Bluetooth is not
  something a suite can do unattended; see `LOGIC-2` in
  [../lib/logic/doc.md](../lib/logic/doc.md).

## Open questions

- **TEST-1 — Where fixture logbooks live.** `testdata/` sits at the repository root and
   predates this directory. Two names as close as `test/` and `testdata/`, holding
   different things, is the kind of collision that costs more later than now. Moving it
   to `test/fixtures/` is the conventional answer; keeping it at the root suits it also
   being an example of the format, readable by anyone without running anything.
- **TEST-2 — Whether the documentation checks become Dart.** Rewriting them would make
   "run the complete suite" a single command, at the cost of redoing work that already
   functions. Leaving them Python means every commit needs two runners.
- **TEST-3 — Where GUI tests live.** Flutter's convention is a separate
   `integration_test/` at the root for tests that drive a real application, distinct
   from widget tests under `test/`. Whether this project wants both is unanswerable
   until there is an interface to drive.
- **TEST-4 — How much of the logic layer can be tested without the data layer**, and
   whether that argues for a fixture builder shared across the suite rather than each
   test assembling items by hand.
