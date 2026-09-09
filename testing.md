# Tests

The test suite, in Kotlin, one module at a time. Every feature has tests, and a feature is
not finished without them.

**The documentation checks are a separate thing** and stay in Python — they keep the
documents and the data honest with each other rather than exercising code. See `TEST-2`.

## Running them

```
./gradlew build              the whole build, tests included
./gradlew :data:jvmTest      one layer's tests
python tool/checkdata.py     the documents against the data and the descriptions
python tool/checklinks.py    the links, the examples and the glossary
```

Two runners, on purpose. Both are run before a commit.

The Kotlin task is `jvmTest` rather than `test`, because a multiplatform module names its
test task after the target. When there is more than one, `allTests` runs the lot.

## Why tests sit with the module they cover

There was once one test tree at the root, mirroring one source tree, because `dart test`
looked in `test/` and nowhere else. Neither of those trees exists now: each layer is its
own module, and a module carries its own tests.

```
data/
  doc.md                      the layer
  src/commonMain/kotlin/      what it is
  src/commonTest/kotlin/      what proves it
```

The same for `logic/` and `ui/`. This file is what remains of the old tree: the
conventions that hold across all three, in one place, rather than restated per module.

A test is named after the thing it covers and sits at the matching place inside its
module's test source set, so given a source file there is exactly one place its tests can
be. Anything shared between tests of one layer lives in that module; anything shared
across layers has nowhere to live yet, and `TEST-4` is where that is decided.

**Tests moving into the modules is the point of the modules.** A test for the data layer
cannot accidentally reach the logic layer, because its module does not depend on one that
does not exist below it. The layering rule stops being a convention and becomes something
the build refuses.

## What belongs here

- **Unit tests** for the [data](data/doc.md) and [logic](logic/doc.md)
  layers. These are the bulk of it, and they need no interface and no files on disk —
  the data layer is designed so items can be assembled in memory. What each layer's tests
  may know about is `TEST-4`: the data layer's invent their own type and never mention
  diving, the logic layer's use the real descriptions and never open a file.
- **Tests of the front ends** that do not need a running application: that a
  description names the fields it should, that an intent maps to the right operation.

## What does not

- **Fixture logbooks.** They are in [fixtures/doc.md](fixtures/doc.md), at the root, by
  `TEST-1`.
- **Checks on the documentation and the fixtures.** `tool/checklinks.py` and
  `tool/checkdata.py` verify that links resolve, that JSON examples parse *and name only
  fields the manual still defines*, that every
  field in the fixtures and libraries is one the manual defines, that every reference
  and key reference finds its target, that no id names two items, and that fixed sets
  and numeric ranges hold. The last two read their vocabularies out of the manual's
  prose, so the manual stays the only place a value is written down.

  `tool/checkdata.py` also reads the item types out of the logic layer's sources and holds
  them to the same manual: a field described in code that the manual does not define is a
  fault, and so is a kind the two disagree on. It reads the source rather than running it,
  which keeps the checkers one Python script with no Kotlin behind them. A field the manual
  defines and nothing describes yet is counted rather than complained about, because that
  is work not done and not a disagreement.

  It reads the whole folder rather than one file, and **it refuses to pass having found no
  types at all**. It once read `Types.kt` alone, which was every type until they were split
  one to a file; after that it reported no problems against nothing, which is the one answer
  a checker must not give quietly.

  It also holds the manual's **order** to the code: each type's fields in the order the
  description declares them, and the chapters themselves in the order `Types.ALL` gives.
  The chapters went unchecked while the fields were checked, and were two reorderings behind
  by the time anyone looked. The list introducing them is checked with them, having been
  missing `Wreck` outright.

  **A suggested vocabulary is checked too**, against the values the manual names for that
  field. A fixed set is checked both ways already — the data is held to it and the manual
  states it — but a suggested set is open, so no value can be wrong and nothing was comparing
  the two lists. They had diverged: the supplied regions use `world` and `area`, the
  description offered both, and the manual named neither.

  **They are Python, and stay that way** — and they are not tests. They check documents
  against data rather than code against expectations, which is why they sit outside the
  suite and run on their own. `TEST-2` records why.
- **Anything requiring a device.** Reading a real dive computer over Bluetooth is not
  something a suite can do unattended; see `LOGIC-2` in
  [logic/doc.md](logic/doc.md).

## Open questions

- **TEST-5 — Where a test that drives the whole application lives.** Opening a logbook,
   editing a dive, checking it was written: it needs all three layers at once, so it
   belongs to none of them. `ui/`'s common tests would hold it, at the cost of a module's
   tests reaching past what that module is; a module of its own depending on all three is
   the alternative, and buys a fourth thing to build for a handful of tests.

   Gradle has no convention here worth deferring to, which is why this is the part
   `TEST-3` could not settle. It waits on there being an application to drive.

## Settled

- **TEST-4 — How much of the logic layer can be tested without the data layer, and
  whether that argues for a shared fixture builder.** *Settled:* **the question was the
  wrong way round, and yes.**

  *None* of the logic layer can be tested without the data layer, and nothing is wrong
  with that: `Item` and `ItemSet` are the data layer, and a description with no items
  describes nothing. What [logic/doc.md](logic/doc.md) promises is narrower and is the part
  that matters — this layer can be tested *with no files anywhere*. So a logic test may
  reach for items freely and must never reach for `data/json`.

  **Three kinds of test data follow, and keeping them apart is the whole discipline.**

  - **Data-layer tests invent their own type.** A description with a text field, a number
    and a reference — never a `Dive`. Building a dive to test the machinery would smuggle
    diving into the one layer whose entire claim is that it knows nothing about it, and the
    smuggling would not show up as a failure, only as a layer that quietly knew too much.
  - **Logic-layer tests use the real descriptions**, with items assembled in memory: a dive
    carrying a profile, a region graph closing on itself, a certification that supersedes
    itself.
  - **`data/json` tests, `logic`'s JVM tests and end-to-end tests read the fixtures on disk**,
    because the real
    format is what they are testing. See [fixtures/doc.md](fixtures/doc.md).

  **A shared builder serves the middle one, under one rule: it may not know a field name.**
  It is a thin helper over a description and a map — terse enough that a dive with a
  profile does not bury the test that uses it — and every name still comes from the
  description. The moment the builder spells `max_depth` itself it becomes a second
  definition of what a dive is, competing with the real one and drifting from it, which is
  precisely what the description model exists to prevent.

  Loading `fixtures/cousteau` into memory was the tempting alternative: 515 items of
  realistic, already-checked data, unused by the layer that would most benefit from
  realistic data. It was refused because it would make every logic test depend on the JSON
  reader — breaking the no-files promise, and turning one reader bug into a whole suite
  failing for a reason unrelated to what it was testing.

- **TEST-3 — Where GUI tests live.** *Settled:* **in `ui/src/commonTest/kotlin`, with the
  rest of that module's tests.** No separate tree, and nothing new at the repository root.

  Compose Multiplatform decides most of this. `runComposeUiTest { }` replaces the JUnit
  rules that Android used, and it is written in common code — so one test drives the
  interface on Android, on the desktop three and on iOS, and it is an ordinary test file
  beside the ones that exercise a description or an intent.

  **Separation is by source set, not by tree.** Where a test genuinely needs a device it
  goes in `androidInstrumentedTest` or `iosTest`; everything else is common. That is the
  whole of the distinction this question was asking about, and it is expressed in the build
  rather than in the directory layout.

  One thing to check against the version actually pinned rather than take on trust: running
  common UI tests on desktop and iOS without experimental workarounds arrived in Compose
  Multiplatform 1.11, and Android needs
  `instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)` or `commonTest`
  never links to the instrumented variant — which fails only once the tests reach a device.

  What this does not settle is the end-to-end case, which is `TEST-5`.

- **TEST-2 — Whether the documentation checks are rewritten in Kotlin.** *Settled:*
  **no. They stay Python, and they are not part of the suite.**

  The two are different jobs. A unit test asks whether code does what it should; these ask
  whether the documentation and the data still agree — that every field written in a
  fixture is one the manual defines, that no link has rotted, that a worked example names
  nothing that has been renamed. Nothing they check is code, and none of them would fail
  because a function broke.

  So needing a second runner is not the defect it looked like. It follows from there being
  two kinds of checking, and merging them would hide that rather than fix it: a red
  build would stop distinguishing "the application is wrong" from "the manual and the
  fixtures have drifted apart", which are answered by different people doing different
  work.

  Python also happens to suit them. They read vocabularies out of the manual's prose —
  fixed sets, numeric ranges, field lists — and that is text work, which would be more code
  in a typed language for no gain. Rewriting 481 working lines would buy one command and
  cost the thing that makes them easy to extend.

  **Accepted with it:** Python on the machine of anyone who wants to check their own work,
  and a commit that is not finished until both runners are clean.

- **TEST-1 — Where fixture logbooks live.** *Settled:* **`fixtures/`, at the repository
   root.**

   It was `testdata/`, which collided first with `test/` and then, once the layers became
   modules, with `data/`. The rename settles more than the collision: the documentation
   said *fixture* forty-five times and *testdata* never once except as a path, so the
   directory was the only place carrying the second word for a thing that already had one.

   They stay at the root rather than moving inside a module, because they belong to no
   single one — every layer's tests read them — and because they double as a readable
   example of the format for anyone who never runs a test.
