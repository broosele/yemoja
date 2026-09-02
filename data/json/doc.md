# Data layer — JSON source

Implements the contracts in [../doc.md](../doc.md) by keeping the logbook as plain
JSON files on disk.

## Why this source exists

Two project goals meet here:

- **Human readable and hand maintainable.** Someone with a text editor must be able
  to read a dive, correct a typo, and have the app accept the result.
- **Works offline.**

On top of those, the storage system is expected to provide versioning, syncing,
backup and conflict resolution. What each of those means is specified in
[requirements.md](requirements.md); this document covers how they are satisfied.

## Layout

A logbook is a **folder**, versioned as a whole — except `settings.local.json`, which
belongs to one installation and is excluded from syncing, backup and history alike. At
its root sits **`yemoja.json`**, saying whose logbook it is and which
[libraries](../libraries.md) it uses. Settings sit beside it in their own files; see
`JSON-3`.

**Nothing in a logbook is a path.** Libraries are named and the application resolves
them; a logbook's own items live under fixed names — `dives/` or `dives.json` — and are
found by looking rather than by being declared. A path is the one thing that can point
outside the folder, and a logbook that does not contain itself cannot be copied, synced,
or turned into a repository: `../../../persons.json` survives on the machine that wrote
it and nowhere else.

[Referenceable items](../doc.md) are stored one of two ways, chosen per logbook:

- **Grouped by type** — one file holding every dive, one holding every buddy.
- **One file per item** — a directory per type, a file per item inside it.

`yemoja.json` declares which is in use and where each type lives, so a logbook
describes its own layout rather than the application assuming one. That also allows a
logbook to be reorganised without the application changing, and different types to be
stored differently — many large dives one per file, a handful of buddies grouped.

[Owned items](../doc.md) belong to their owner. Whether that means physically
inside the owner's file is a separate question: a dive profile is owned by its dive
but is far larger than the rest of it, and grouping every dive into one file would
make that file unusable by hand — which is the point of the format.

## Versioning

Yemoja keeps its own history, inside the logbook folder, in the same readable JSON as
everything else. There is no version control system underneath and no native library on
any platform.

That is not the obvious choice, so the reasoning is worth keeping. Git was the
candidate, and three of the four things it is normally chosen for do not apply here:

- **Its merge is excluded** by [requirement 4](requirements.md) — conflicts are resolved
  with rules that understand what a dive is, and with the user, not by a textual merge.
- **Its attribution is the wrong shape.** Requirement 1 wants to know which installation
  and which kind of operation produced a change. Git offers an author, an address and a
  message, so that metadata would have to be encoded by hand anyway.
- **Its transport only helps with git remotes.** Sync goes through whatever location the
  user provides, and a `.git` directory carried by a file-sync service is a well-known
  way to corrupt a repository.

What remains is genuine — content-addressed integrity, parent-linked history, and files
readable by tools that will certainly outlive this project. It was not enough to justify
a native dependency on five platforms resting on a single maintained binding whose
predecessor has been untouched since 2023.

Scale is what makes writing it reasonable. A logbook is small: twenty dives is about
forty kilobytes. Keeping every superseded version of every item for a lifetime of
diving stays in single-digit megabytes, so the compression and packing that a version
control system exists to provide buy nothing here.

### The journal

The journal is a list of **changesets**. A changeset is one thing the user did, and it
holds the **actions** that carried it out:

```json
[
  {
    "when": "2026-02-23T19:40:00+01:00",
    "installation": "desktop",
    "operation": "download",
    "actions": [
      {
        "dive": "@2026-02-23#0",
        "add": { }
      }
    ]
  },
  {
    "when": "2026-02-24T08:12:00+01:00",
    "installation": "phone",
    "operation": "edit",
    "actions": [
      {
        "person": "@jacques_cousteau",
        "edit": {"birthday": ["1920-12-03", "1923-11-07"]}
      }
    ]
  }
]
```

A changeset items three things about its origin, which is what makes *where did this
come from* answerable: **when** it happened, **which installation** did it, and **what
kind of operation** it was — a manual edit, a dive computer download, an import, a sync.
All three are known at the moment of the change and none has to be asked for.

It also carries a **sequence number of its own installation's making**, which is what
orders the journal. Within one installation that order is certain. Merging two journals
interleaves them by recorded time, which is only as good as the clocks were — and that
turns out not to matter much. A wrong interleaving changes the outcome only for two
changes to the *same field*, and those are conflicts, already found and resolved by rule
or by asking. Changes to different things commute, so the order they end up in is
immaterial.

Nothing more elaborate is used. A logical clock would order them correctly whatever the
clocks said, at the cost of putting a number in every changeset that means nothing to
anyone reading the file — which is a poor trade in a format whose point is being legible
by hand.

Every action can be applied in either direction. An `edit` carries only the fields that
changed, each as the value before and the value after. An `add` and a `delete` carry the
whole item, because that is what reversing them needs. Undo and redo are therefore the
same machinery run in opposite directions, and nothing outside the journal has to know
how to invert anything. Reversing a changeset is reversing its actions, backwards.

**Every change to the logbook belongs to exactly one changeset**, including a single
edit of a single field, which becomes a changeset holding one action. The uniformity is
worth the small overhead: the unit the user undoes is the unit the file is made of.

Two things follow from grouping this way, and both were required anyway. A download or
an import is *one* labelled thing to review and revert rather than four hundred, which
is what [reconciliation](../../logic/reconciliation.md) depends on. And attribution —
when, on which installation, by which kind of operation — is written once per changeset
instead of on every action, which is what `REQ-1` asks for and where it now lives.

This also settles what the journal stores and how conflicts are compared: both work in
fields, so two installations editing different fields of one dive is not a conflict at
all.

**An action can reach inside an item.** A field on the item is named plainly; a field
within a singular owned item is named by a path; an entry in a keyed collection is named
by the key it sits under, which is a path segment like any other:

```json
[
  {
    "when": "2026-03-01T10:00:00+01:00",
    "installation": "desktop",
    "operation": "edit",
    "actions": [
      {
        "person": "@anna_devries",
        "edit": {
          "medical.body_mass": [64, 63],
          "courses.k3.date": ["2022-08-20", "2022-08-21"]
        }
      }
    ]
  }
]
```

Addressing by key rather than by position is what lets actions be reordered and dropped
at all: the second course is only the second course until something moves.

**Actions do not generally commute**, which matters because rearranging and dropping
them is wanted. Two actions may be reordered safely when they touch different items,
or different fields of the same item. Everything else has a dependency: an `edit`
after an `add` of the same item needs that `add`; an `edit` before a `delete` is
undone by it. Removing or moving an action outside the safe set has to be refused or
made to cascade, and which of those is `JSON-13`.

What has to be built, and is not free:

- An append-only journal of changes, recording what changed, when, on which
  installation, and by which kind of operation.
- Superseded content, kept so that a value can actually be restored rather than merely
  described.
- Ordering that survives two installations editing while apart, since wall-clock time
  from two devices cannot be trusted to agree.
- Merging two journals on sync. The journal travels with the items — see `REQ-4` — so
  an installation can account for a change made anywhere, including one that arrived
  from elsewhere. That is the point of keeping history at all, and it is also the hardest
  part of this design: two journals that diverged have to become one, and the order they
  end up in has to be defensible.
The journal is never compacted and nothing is ever discarded from it. The arithmetic
allows that comfortably: a changeset is a few hundred bytes, so even a user making
twenty changes a week accumulates well under a megabyte a year — less, over twenty years,
than a single dive's profile. An application whose purpose is answering *what happened*
should not be quietly forgetting on a schedule.

## Ids and references

An item's id is its **key**, and the key comes from where the item is
stored: the file name when each item has its own file, or the key it sits under
when several share one.

```json
// persons.json
{
  "anna_devries": { }
}
```

```json
// dives/2026-04-28#0.json
{
  "buddies": ["@anna_devries"]
}
```

A **reference** is the key prefixed with `@`. A value in the same position *without*
`@` is a [one-off](../doc.md) — a plain value asserting no id:

```json
{
  "buddies": ["john"]
}
```

### What follows from id being the key

Keys are readable and hand-editable, which is the point of the format. Two
consequences come with that:

**Renaming is a data change — but not to the item.** The item itself contains no
mention of its key, so renaming rewrites the container and every reference to it, and
leaves the item's own content untouched. That is workable, since changes group into
a single revertible unit, see [requirements.md](requirements.md) — but it has to be an
operation the application performs, not something a user does in a text editor and
hopes.

**A key is a handle, not a value.** Where a key encodes content — a dive keyed
`2026-04-28#0` — the item itself still holds the authoritative version of that
content, at full precision and with a time and offset the key cannot carry. Correcting
a dive's date changes the item and leaves the key alone. A key may therefore drift
from what it appears to say, which is the price of references that do not break.

For the same reason the index within a day is *assignment order* — the first dive
logged that day — and is never re-sorted. Otherwise discovering a forgotten earlier
dive would renumber its neighbours and break every reference to them.

## Scope

- The on-disk layout: which item goes in which file, and how files are named.
- The canonical serialised form — the exact formatting rules that make a file
  deterministic, so that two writes of the same data are byte-identical and diffs
  stay minimal.
- How a schema version is recorded in a file and how an older or newer file is
  handled on read.
- Versioning, syncing, backup and conflict resolution, as specified in
  [requirements.md](requirements.md).
- Tolerating hand edits: reformatting, unknown fields, and changes made while the
  app was not running.

## Not in scope

Anything another source would also need — that belongs one level up.

The format as a *user* encounters it — what to type, and where it goes — is documented
in the user manual, `manual/data-format.md`, which owns it; what each field *means* is
`manual/data-fields.md`. This document covers why the format is as it is and what
follows from it.

## Open questions

To settle when we discuss architecture:


   **This is `REQ-14` in another guise** — whether a conflict is per item or per
   field. Storage that keeps whole items and merging that compares fields can coexist,
   but the two questions have to be answered together or the answers will disagree.
- **JSON-13 — When an action or a changeset may be moved or dropped.** Two actions commute
   if they touch different items, or different fields of one item; outside that they
   depend on each other. Either the journal refuses a change that would break a
   dependency, or it cascades to whatever depended on it. This is the ground operational
   transforms and CRDTs occupy, and the rule is worth borrowing rather than inventing.
- **JSON-16 — How an absent field is written in a before-and-after pair.**
   Nulls are never stored and absent means absent, so `null` is not available to mean
   "this field did not exist". Adding a field and changing one must still be told
   apart.

## Settled

- **JSON-7 — Whether a one-off can be richer than a string.** *Settled:* it cannot. A
  one-off is a name and nothing else. Anything with properties is an item — **generic**
  where it must still assert no identity, which is the mechanism that already exists for
  exactly that.

  The two halves of what a one-off is for both argue against widening it. Something with
  coordinates is known well enough to record, which is the first case gone; and once two
  dives carry the same coordinates a reader will take them for the same place, which is
  the inference the second case exists to prevent. A richer one-off would quietly become
  the weaker reference that *References and one-off values* says it is not.

  It also keeps a reference field to two shapes rather than three — `"@id"` or a plain
  name — so no reader has to branch on a third. And an inline object would be an item
  with no description behind it: nothing would say which fields are legal, so the
  usable-absent-unusable of `DATA-50` would have nothing to check against.

  **What it costs falls on import.** A foreign file carrying a site with coordinates that
  matches nothing here must either become an item — with the duplicate risk that
  [../../logic/reconciliation.md](../../logic/reconciliation.md) records for fuzzy
  matching — or keep the name and lose the position. That is a reconciliation decision,
  and better made there with the whole file in view than by widening the format for every
  logbook.

- **JSON-18 — What a local key looks like.** *Settled:* **proposed from the entry's own
  data, with `#<index>` where that clashes** — the same machinery as an id, one level
  down. A service keyed by its date reads as something; `k1` reads as nothing, and a key
  is now visible structure rather than a field among others.

  The proposal rule is a property of each collection and **is not yet defined for any of
  them**, exactly as an id's proposal is a property of each type. One collection is
  exempt: a profile's `pressures` is keyed by the `gas_sources` entry it measured, so its
  keys are borrowed rather than proposed, and one series per cylinder follows from that
  rather than needing a rule. What is settled here is
  that a key is proposed rather than counted or drawn at random.

  **A hand editor may write anything** not already present. Edits by hand exist to keep
  data lean, and someone doing that deliberately is not to be second-guessed. Uniqueness
  is structural, since a collection is a keyed object; nothing else is enforced.

  **A key is not reused once its entry is gone**, and that binds the application rather
  than the user. Only history can say which keys were ever used — the items show what is
  in use, not what was — and history is also the only thing reuse can harm: the damage is
  a recorded action naming `k1` reaching an entry it was not written against, which
  requires such an action to exist. So a key freed before any history existed is safe to
  reuse by construction, and one freed afterwards is recorded as deleted and can be known.
  `JSON-12` keeps history in full, so that record does not age out. The rule is
  enforceable exactly when it matters.

  **Not for immediate implementation.** Keys themselves are core — a collection cannot be
  written without them — but avoiding reuse waits on `FEAT-4`, which is *Planned*. Until
  a journal exists there is nothing to protect and reuse costs nothing; the obligation
  arrives with the means to meet it.

  One consequence for the journal rather than for keys: minting a key will need to consult
  history, and `JSON-9` settles only that the *logbook* is read on opening. Whether
  history is read then too, or read when a key is minted, is part of the parked journal
  work — a journal kept in full never gets smaller, so neither answer is free.

- **JSON-21 — How a logbook's own files are found.** *Settled:* by **convention, not by
  declaration.** Each kind of item lives under a fixed name — `dives`, `persons`,
  `regions`, `dive_sites`, `gear`, `certifications`, `operators`, `dive_trips` — as
  either a folder of one file per item or a single file of them all, and the application
  uses whichever is there. Both at once is the one case it cannot resolve, and it says so
  rather than choosing.

  `yemoja.json` therefore has no `logbook` section and no paths of any kind. The
  flexibility removed was never used: every entry in `fixtures/cousteau` was the
  predictable one.

  The reason is that a path is the only thing in a logbook that can point outside it. An
  absolute path stops working the moment the folder is copied; `../shared/persons.json`
  means the folder no longer *is* the logbook, so making it a repository or syncing it
  quietly leaves data behind. And a path carries a case-sensitivity trap across devices —
  `./Persons.json` against a file named `persons.json` works on Windows and fails on Linux
  and Android, revealing nothing on the machine that wrote it.

  What the section used to say survives without it. Which layout a type uses is visible in
  the folder. Which type a file holds comes from its name. And which types a logbook
  contains is simply which files exist, so a type added in a later version is readable in
  an older logbook rather than absent from its declaration.

- **JSON-20 — How a collection of owned items is written.** *Settled:* as an **object
  keyed by the entry's key**, not as a list with the key inside each entry.

  ```json
  {
    "maintenances": {
      "k1": {"type": "visual inspection", "date": "2025-06-02"},
      "k2": {"type": "pressure test", "date": "2026-06-10"}
    }
  }
  ```

  The old shape contradicted a rule the project already had. *An item does not own its
  id* puts an id in the file name or the key an item sits under, and says nothing inside
  the item records it — yet a `key` field sat inside every owned entry, which is the same
  thing one level down. Keying the collection makes both levels read alike: a file of
  persons is an object keyed by id, a person's courses an object keyed by key, and in
  neither does the thing carry its own name.

  Three problems stop existing. Two entries cannot share a key in a file that parses. No
  entry can lack one, so the journal always has an address and there is no question of
  assigning keys lazily. And adding an entry shifts nothing, so a diff shows the entry
  added and not everything after it — which is what per-field history in `REQ-14` wants
  from the file.

  **What it costs** is that a hand editor must invent a key rather than leaving it out for
  Yemoja to fill in. That is the same act the format already asks for one level up, where
  adding a dive by hand means choosing its file name, which is choosing its id.

  **Order carries no meaning** and is dealt with in [../doc.md](../doc.md): worked out
  from contents where it matters, and written in a settled order regardless so that saving
  an unchanged file produces no diff.

- **JSON-19 — How a field points at an entry by key.** *Settled:* with a marker of its
  own, **`*`**, as an id reference carries `@`. A dive names its primary profile as
  `"*p1"`; a gas switch names its gas source the same way.

  The marker is what an id reference gets for the same reason: a value that points at
  something should not be mistakable for one that says something. A bare key would be
  indistinguishable from text, and unlike an id — which is unique across the whole logbook
  and so interpretable on its own — a key means nothing outside its list. Bare, it would
  be readable only through the field's description, with nothing to fall back on if that
  were wrong.

  `*` is taken from the alias syntax of YAML, where `*name` is the entry labelled *name*.
  It is rare at the start of anything a user writes, and it does not collide with `#`,
  which is already structural inside an id.

  **The sigil marks the reference, never the key itself** — exactly as with ids, where a
  file name or JSON key is bare and only a reference to it carries `@`. The entry sits
  under a bare `p1`; everything pointing at it writes `*p1`.

  **Where the list is rooted comes from the description**, as the referenced type does in
  `JSON-5`. That matters more than it first appears: a pressure series inside a profile
  names a gas source belonging to the *dive*, not to the profile, so a key reference does
  not always address a list beside it. The description says which list and where it hangs;
  the file says which entry.

  Two things follow. Text may not begin with `*` any more than with `@`. And multiline
  text is unaffected, since nothing in it is read as a reference of either kind.

  **The two markers compose, should reaching into another item ever be wanted.**
  `@<id>*<key>` reads as the entry keyed *key* inside the item *id* — `@anna_devries*c1`
  for one of her courses. Nothing needs it yet: every key reference so far points inside
  the item doing the pointing. A dive naming a leg of a trip nearly did, until trips were
  given a `parent` instead and the reference stayed a plain id. It is written down because
  the syntax falls out of the two sigils already chosen, and knowing the shape is
  available stops a different one being invented later for the same job.

  That form only parses if **`*` is excluded from a proposed id**, as `#` already is —
  otherwise `@a*b*c` has no single reading. Names may contain `*` even though text may
  not begin with one, so the exclusion belongs where `#`'s does: see *An item does not
  own its id* in [../doc.md](../doc.md). Free to impose now and breaking to impose
  later, once ids exist that contain one.

- **JSON-8 — Where the logbook lives, and whether a user can keep several.** *Settled:*
  **the user chooses, from a per-platform default**, and **one logbook is open at a time
  while any number may exist on disk.**

  Choosing matters because a logbook is a folder — copy it and you have copied
  everything. A location the user cannot reach is a format that is readable in principle
  and not in practice, and it forecloses putting a logbook on an external disk or inside
  whatever the user already syncs. So Yemoja proposes a place and does not insist on it.

  Where that leaves a phone is a per-platform question rather than this one: Android's
  scoped storage and iOS's sandbox may not offer an arbitrary path, in which case the
  default is the only choice there. That is a narrowing of this rule on those platforms,
  not an exception to it — see the storage entries in
  [../../ui/gui/phone/android/doc.md](../../ui/gui/phone/android/doc.md) and
  [../../ui/gui/phone/iphone/doc.md](../../ui/gui/phone/iphone/doc.md).

  **One open at a time** keeps every mechanism here concerned with exactly one logbook,
  which is what they already assume: the journal is a logbook's history, sync is between
  two copies of *the same* logbook, and an `ItemSet` resolves ids within one. Several on
  disk costs nothing — a logbook is a folder, and Yemoja need only remember which was last
  opened. This is what `FEAT-15` asked for, so it is answered rather than merely unblocked.

  A consequence for the local settings: `settings.local.json` lives in the logbook folder
  and belongs to one installation, so a user who moves or copies a folder carries it
  along. `JSON-3` records that cost; choosing the location makes it likelier to arise,
  since copying a folder is now an ordinary thing to do rather than a rare one.

- **JSON-6 — Key collisions.** *Settled:* the local half already was — two people with
  the same name become `john_smith` and `john_smith#1` by `DATA-15`. The distributed half
  is answered from the journal.

  Two installations offline, each creating a different John Smith, both propose
  `john_smith`, both find it free, and both take it. `#<index>` cannot help: an index
  resolves a clash within a collection someone can see, and neither side can see the
  other. **The journal can.** `JSON-10` has each installation numbering its own
  changesets, so the history already says which installation created an item and that
  neither creation descends from the other. Two items sharing an id with no common
  ancestor are a collision, not a match.

  Sync therefore asks, as `REQ-15` requires of any genuine collision: one person or two?
  If two, one is renamed and every reference to it moves in the same changeset, which is
  `JSON-17` and needs nothing new.

  **This corrects something stated in [../../logic/reconciliation.md](../../logic/reconciliation.md).**
  That document rates sync matching as exact and names file import as the only source
  that can mis-identify. Sync can too, in exactly this case, and unlike import it would
  fuse two people silently — the ids match perfectly, so nothing looks wrong. What makes
  it recoverable rather than dangerous is that the journal has the evidence; what makes it
  worth writing down is that the obvious implementation, matching on id alone, is wrong.

  The alternatives were rejected for the same reason: both cure the collision by making
  ids carry something other than meaning. An id minted with an installation marker, or a
  hidden creation token matched on instead of the id, would each work — and each gives up
  that a proposal produces `anna_devries` and never `p_00417`, or that two equal ids mean
  the same item. The collision is rare; the readability is on every screen and in every
  file.

- **JSON-5 — How the referenced type is known.** *Settled:* from the field's description.
  A reference is written as a bare id — `"@anna_devries"` — and what it points at is a
  property of the field holding it, not of the value.

  Nothing is lost by leaving it out, because an id already names an item in the whole
  logbook: `@anna_devries` identifies exactly one thing whether or not anything says it is
  a person. A type in the reference would therefore be a restatement, and a restatement
  can disagree — a file could say `@person:...` of something that is not one, and then
  either the type or the id has to be believed.

  It also keeps resolution to one moving part. An `ItemSet` resolves an id by first match
  across the logbook and its libraries, which is what makes shadowing work; a type
  participating in that match would give the same id two possible answers.

  And the description has to carry the type regardless. An interface offering "add a
  buddy" must know which items to offer, and a derivation reading a gas source's
  `cylinder` must know it is looking at gear. Writing it into the file as well would put
  the same fact in two places, one of which nobody reads.

  **What it costs:** a file is no longer self-describing. Reading `"@northshore_diving"`
  by hand does not say whether that is an operator or a dive site — the manual does. For a
  format meant to be legible without the application, that is a real loss, accepted
  because a good id is recognisable on sight and the alternative pays on every reference
  in every file to serve a rare reading.

- **JSON-3 — What `yemoja.json` holds.** *Settled:* the structure of the logbook and
  nothing else — who owns it, which libraries it uses, and where each type's files live.
  Settings move out, into two files of their own:

  | File | Holds | Travels |
  |---|---|---|
  | `yemoja.json` | what the logbook *is* | with the logbook |
  | `settings.json` | the logbook layer of `DATA-9` | with the logbook |
  | `settings.local.json` | the local layer of `DATA-9` | never |

  **The settings files are not part of the data model.** They sit in the logbook and sync
  with it, and there the resemblance stops: no compatibility promise, no schema version,
  no place in `manual/data-fields.md`, and no obligation on a future release to read what
  an older one wrote. An unrecognised setting is ignored and the default is used, which is
  a licence the data itself will never have. They are documented for users in
  `manual/settings.md`, deliberately apart from the format chapters.

  That is the sharper reason for the split, beyond the fact that two things change for
  different reasons and by different hands. The structure is written once when a logbook is made and rarely touched; settings
  are edited whenever a user changes their mind. Keeping them apart also means the file
  a user is most likely to open by hand is the small one.

  **`settings.local.json` sits in the logbook folder and is excluded from everything.**
  Not synced, not backed up, and not in the journal — it is not logbook data, and a
  history of window sizes is worth nothing. Its absence is harmless: a reader falls
  through to `settings.json` and then to the built-in defaults, which is the whole point
  of the chain having three layers.

  The cost, recorded rather than glossed: a logbook folder copied to another machine by
  hand carries the local file with it, and those settings were meant to stay behind. The
  name is the mitigation — a file saying `local` in the middle of it is one a user can
  delete knowing what it was — and nothing breaks if they do.

Kept with their identifiers so earlier discussion still resolves.

- **JSON-1 — Versioning and sync mechanism.** *Settled:* Yemoja keeps its own history. See
  *Versioning*.
- **JSON-15 — How actions are grouped.** *Settled:* The journal is a list of changesets, each
  holding its actions and carrying its own attribution.
- **JSON-14 — How an action addresses something nested.** *Settled:* By path, with a list entry
  named by its local key rather than its position.
- **JSON-4 — Escaping.** *Settled:* nothing is escaped. Text may not begin with `@` or
  `*`, so a value starting with one is a reference to an item, a value starting with the
  other is a reference to a key, and a value starting with neither is text. *Amended by
  `JSON-19`,* which added the second marker; the rule is unchanged, it now excludes two
  characters rather than one.
- **JSON-2 — Where profiles go.** *Settled:* inside the dive, as series of time-value
  pairs, read and parsed with it.
- **JSON-9 — Index and startup cost.** *Settled:* no index. The whole logbook is read
  and constructed on opening. If that becomes slow enough to matter, the answer is lazy
  loading and caching of the computation — not writing derived values into the files.
- **JSON-17 — Whether renaming is one action.** *Settled:* it is one *changeset*. Every
  change belongs to exactly one, so a rename and the reference rewrites it forces are
  undone together and a half-undone rename cannot arise.
- **JSON-10 — How the journal is ordered.** *Settled:* each installation numbers its own
  changesets, and merging interleaves the two by recorded time.
- **JSON-12 — When history is compacted.** *Settled:* never. It is kept in full, see
  `REQ-3`.
- **JSON-11 — What the journal holds against what the items hold.** *Settled:* fields.
  An `edit` carries the fields that changed, each as before and after; an `add` or
  `delete` carries the whole item. Conflicts are therefore per field too, which is
  `REQ-14`. A profile is one value and is never split, see `REQ-18`.
