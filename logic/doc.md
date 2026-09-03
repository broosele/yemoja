# Logic layer

Everything the application *does*, independent of how it is stored and how it is
shown. There is exactly one implementation — no alternatives, no platform variants.

Every user interface talks to this layer and only to this layer, through the single
access point described below. A front end may *name* the item types, which this layer
describes and which are the vocabulary the whole application shares; reaching past this
layer for anything it *does* is a bug in the design.

## Scope

- **The item model.** The description of every item type — its fields, their kinds
  and units, and how each derived value is computed. The machinery that reads, writes
  and resolves against those descriptions is in [data](../data/doc.md); what a dive
  *is*, is here.
- **Logbook operations:** create, edit, delete dives, sites, buddies and gear, and
  linking them together. Not numbering: a dive's number is the user's own and nothing
  renumbers it.
- **Dive planning and decompression.** Given gases, depths and times, produce a
  schedule with ascent, stops and gas use.
- **Gear tracking:** service intervals, what is due, what was used on which dive.
- **Statistics and summaries** over the logbook.
- **Import from dive computers**, and import of other logbook formats — folded into
  the logbook by the shared machinery in [reconciliation.md](reconciliation.md).
- **Search and filtering** — the definitions of "recent", "deep", "with this buddy".
- Unit handling for *display and entry*: what a user is shown and what they type. A file
  declares what its own numbers are written in and the data layer converts on the way in,
  so a value held here is in the model's own unit — see [../data/doc.md](../data/doc.md).
  What a user is shown is a separate choice, and it is this layer's.

## Not in scope

- Presentation of any kind: formatting, layout, wording, navigation, colour.
- Storage details.

## Depends on

The data layer, for items and the machinery that reads and resolves them — see
[../data/doc.md](../data/doc.md). An `ItemSet` is constructed with the descriptions
this layer supplies, so the dependency runs one way and the layer below knows nothing
about diving. Never on a specific source either: it must be possible to run and test this
layer against items assembled in memory, with no files anywhere.

Two things sit here rather than below, and both for the same reason — they need to know
what diving is:

- **The descriptions**, and with them every derived value, from a region's `children` to
  `deco`. Structure and arithmetic were once reason enough to compute a derivation
  below, which made `deco` an exception needing a rule of its own; now the computation
  travels with the description and there is no exception to police.
- **Domain rules.** Two dives that overlap in time, a course made of somebody else's
  dives, a cylinder whose end pressure exceeds its start, a dive deeper than the user
  was certified for. The test is whether a thing can be checked without knowing the file
  is about diving; if it cannot, it belongs here. Structural checks — a date that parses,
  a reference that resolves, text without a line break — stay in the data layer.

  Because validation is permissive and nothing is ever refused, these rules do not reject
  anything. They produce findings, each aimed at an item and carrying a severity, which
  is what the home tab shows. A dive beyond your certification is not invalid data: it
  happened, and it is worth remarking on.

## Structure

```
logic/
  doc.md          this file
  build.gradle.kts  the module, which depends on data and on nothing else
  reconciliation.md  merging an import into the logbook
  uddf.md         UDDF against this model, field by field
  src/commonMain/kotlin/yemoja/logic/
                  Types.kt      what a person, a region and a piece of gear are
  src/commonTest/kotlin/yemoja/logic/
                  the tests, beside what they cover
```

**Three types so far, and only the fields holding one value.** Lists, keyed collections
and owned items are absent, so a person has no `courses` and a region no `parents`; the
six other item types are absent entirely. [manual/data-fields.md](../manual/data-fields.md)
is the source of truth for every field, and where it and `Types.kt` disagree the manual is
right. Nothing checks that automatically yet.

## The Universe

Everything above this layer talks to one thing: the **Universe**. It is the application's
working state and the single door through which a front end reaches anything at all.

It holds one or more sets of [items](../data/doc.md) — normally the open logbook, and
during an import the candidates as well, each complete and resolvable in its own right.
Reconciliation is then a comparison between two sets rather than a special mode, and an
item is never without the items it belongs to.

Over that it offers what the data layer deliberately does not: statistics, decompression,
and the domain rules below.

Two things to hold it to, or it will quietly become the whole application:

- **It delegates and implements nothing.** A statistic is computed by a statistics
  service and handed on. The moment one is written inside the Universe itself, everything
  else follows it there.
- **It does not re-expose the data layer method by method.** An `ItemSet` is reachable
  *through* it rather than wrapped by it. Hand-written forwarding is boilerplate that
  drifts apart the first time somebody adds to one side and forgets the other.

## Decompression

Decompression calculation is the one part of this project where a mistake can hurt
someone. It gets treated differently from everything else:

- Isolated behind its own boundary, with no dependency on storage or UI, so it can
  be tested exhaustively and compared against reference schedules.
- No change without tests.
- Whatever the app shows is a planning aid, never a substitute for a dive computer
  or for training. That framing is a requirement on the UI layer too.

## Open questions

To settle when we discuss architecture and features:

- **LOGIC-1 — Shape of the interface.** One service per subject area, a single facade, or
   use-case objects? This determines what the API and TUI front ends look like.
- **LOGIC-2 — Reading a dive computer** needs a native library and platform Bluetooth access.
   Where the *reconciliation* belongs is settled — see
   [reconciliation.md](reconciliation.md) — but where the device-facing half lives,
   given it needs platform capabilities the logic layer should not have, is not.

   The language change moved this. The platform that matters most is now the one where
   Bluetooth and a native library are least awkward, and the three routes to
   libdivecomputer — JVM, Android, iOS — are three source sets rather than one binding.
   That argues for the boundary sitting at an interface the logic layer declares and each
   target implements, but it is an argument, not the answer.
- **LOGIC-7 — What counts as due soon.** The data layer records `valid_until`,
   `days_left` and `expired`, and stops there — `expired` is a fact, "needs renewing
   shortly" is a judgement. This layer decides the judgement, and `FEAT-9` is what wants
   it: one list answering what needs renewing.

   What makes it more than a threshold is that one number is wrong everywhere. A month's
   notice suits a regulator service, is derisory for a five-yearly pressure test, and
   means nothing for a medical without knowing whether the user is diving next week or
   next season. Candidates: a share of the original interval, which scales itself since
   the interval is known from `date` to `valid_until`; a setting the user chooses; or a
   per-obligation figure. Relocated from `DATA-36`.
- **LOGIC-8 — What a walk does when it meets a cycle.** Three fields point at their own
   type — a region's `parents`, a dive trip's `parent`, a certification's `supersedes` —
   and nothing prevents a chain that returns to its start. It is not refused on write:
   `DATA-17` in [../data/doc.md](../data/doc.md) records why, and that the data layer
   never walks, so this is entirely here.

   **Answered per derivation rather than once.** What a cycle means depends on the field:
   an ancestry that will not terminate is a caption that cannot be written, a trip whose
   `dives` gathers from its descendants would count some twice or never stop, and a
   qualification that supersedes itself is a different absurdity again. Each derivation
   knows what it is for and decides.

   What is common to all of them is that a walk must terminate whatever the data says, and
   that `DATA-50` gives the result somewhere to go: a derived value that cannot be worked
   out is *unusable*, not absent, and a cycle is always a mistake worth reporting rather
   than quietly surviving. Relocated from `DATA-17`.
- **LOGIC-3 — Which decompression model(s)** to support, and whether the model is pluggable.
- **LOGIC-4 — State and lifetime.** Is this layer a stateless set of operations over the data
   layer, or does it hold a live in-memory logbook that UIs observe? Which layer holds
   the loaded items is settled — the data layer does, as an `ItemSet`, see `DATA-20`
   in [../data/doc.md](../data/doc.md) — but whether the Universe above them is live
   is not.

   *Observe* needs care now: the toolkit has a state model of its own, and an interface
   that rebuilds when what it read changes. `DATA-6` settled that nothing below announces
   anything and a view that may be stale asks again — which fits that model rather than
   fighting it, and is worth checking before anything here grows a subscription.

   `LOGIC-5` narrows this considerably: with one operation at a time there is nothing to
   race, so *live* costs only what it costs to hold, not what it costs to protect.

## Settled

- **LOGIC-5 — Concurrency.** *Settled:* **there is none.** One thread, one operation at a
  time, and a long operation takes the application over until it finishes.

  Downloading a dive computer, importing a file, running a plan: each is exclusive. The
  user is not browsing dives while forty come off their computer, because nobody needs to
  and pretending otherwise buys a class of bugs for a convenience nobody asked for.

  **What that removes is the whole question.** Two things reaching one logbook at once is
  the only reason any of the alternatives existed — copying it so readers see a consistent
  version, queueing writes to one owner, locking it. None of them is needed if there is
  never a second thing. `ItemSet` stays a plain mutable structure, an edit changes an item
  in place, and no part of this layer has to be written twice for a case that cannot arise.

  It is also the one place where the change of language would otherwise have cost
  something: coroutines make a background download cheap to *start* and make the shared
  logbook expensive to get right. Declining the first declines the second.

  **One implementation constraint, and it is not a softening of this.** Android kills an
  application whose interface stops answering for a few seconds, so *exclusive* cannot mean
  a frozen thread — the interface has to keep drawing to show that something is happening
  and to offer a way to stop it. The work therefore runs off the interface's own thread
  while the interface refuses everything else: one screen, a progress indication, a cancel,
  and nothing reachable behind it. The rule stands as written — one operation at a time,
  the application belongs to it — and only the mechanism has to respect the platform.

  Two things follow elsewhere. `LOGIC-4` gets easier: a live in-memory logbook has no
  observers racing it. And a large import is a single stretch of work rather than something
  reconciled while the user carries on around it — see
  [reconciliation.md](reconciliation.md).

- **LOGIC-6 — Which gradient factors a logged dive's `deco` is computed against.**
  *Settled:* **none, because nothing computes it.** `deco` is read off the primary
  profile, from either of two things the computer recorded:

  - a `decostop` above zero, at any point — the dive went into decompression;
  - failing that, a `no_deco_time` that never reached zero — it did not.

  Where the profile has neither, or there is no profile at all, `deco` is absent and the
  user answers it.

  The second rule exists because computers generally write stops only when there are
  stops, so a `decostop`-only rule would leave `deco` unanswered on most recreational
  dives that came off a computer. A device saying you always had time left is the same
  device's answer as the stops it did not set, and just as much a fact of the recording —
  reading it is not inference.

  The question assumed `deco` could not be answered without gradient factors. For a dive
  that came off a computer it can, and better: the device decided at the time, with the
  user in the water, using the model and settings they were actually following. Nothing
  here can reproduce that, and a second opinion computed afterwards would not be a
  correction — it would be a different dive's answer printed over this one.

  That is the rule the manual already applies to `decostop`, `no_deco_time`, `cns` and
  `otu`: kept as recorded rather than recalculated. `deco` is the same kind of claim and
  now follows the same rule, which removes the exception rather than deciding it.

  **Gradient factors therefore play no part in a logged dive at all.** They belong to
  planning, where `default_gf_low` and `default_gf_high` are what a new plan starts from,
  and changing them cannot alter what the application says about a dive done years ago —
  which is what this question was worried about, now unable to happen.

  A dive from a depth gauge gets no computed answer, and that is the point. Absent means
  nobody has said; it does not mean no. See `DATA-50` in
  [../data/doc.md](../data/doc.md).
