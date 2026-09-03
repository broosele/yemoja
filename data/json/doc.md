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
them; a logbook's own items live under fixed names — `dive/` or `dive.json` — and are
found by looking rather than by being declared. A path is the one thing that can point
outside the folder, and a logbook that does not contain itself cannot be copied, synced,
or turned into a repository: `../../../person.json` survives on the machine that wrote
it and nowhere else.

[Referenceable items](../doc.md) are stored one of two ways, chosen per logbook:

- **Grouped by type** — one file holding every dive, one holding every buddy.
- **One file per item** — a directory per type, a file per item inside it.

Neither is declared. Each type lives under a fixed name and the application uses whichever
of the two is there, which is `JSON-21` — an earlier draft of this section had `yemoja.json`
declaring the layout, and it does not. What that decision keeps is the part worth keeping: a
logbook can be reorganised without the application changing, and different types can be
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
// person.json
{
  "anna_devries": { }
}
```

```json
// dive/2026-04-28#0.json
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

## Reading

Everything here is in `src/commonMain/kotlin/yemoja/data/json/`, the two stores included.
`FileStore` and `DiskFileStore` are files rather than JSON, but what they are asked is this
format's question — which files hold a type, given that one is a `.json` file or a folder of
them — so they sit with it and not at the layer's root. `DATA-86` argues the dependency.

`LogbookReader` starts at `yemoja.json`, which names the libraries the logbook uses; a
logbook without one reads as a logbook declaring none, since whether the file is required has
never been settled and being strict would make a folder of dives unreadable for want of a file
saying only what it has none of. Nothing reads the owner it also declares.

Two readers, both ours. `LogbookReader` turns
a logbook's files into a set of items. *Which* files those are is the store's to answer, by
the convention `JSON-21` settles and with nothing declaring the layout; this turns each of
them into items, whether it is one item named by its file or many under their own ids. `Json`
turns one text into a tree, and everything below is about that one.

It builds a `Stored` tree — the data layer's own, not one of this source's — and knows
nothing about fields: `"max_depth": "deep"` reads happily as a string and is refused later
by the description, which is the division `DATA-64` draws. Building the layer's tree rather
than a JSON-shaped one is what lets the walk from a description to an item's fields be
written once instead of once per source.

**No library.** JSON is a small grammar and the reader is about three hundred lines, which
buys the bottom layer keeping its promise of no dependencies. The deciding argument was
not the reader, though — it was the writer. This format wants a settled field order so that
saving a file nobody edited produces no diff, and a general-purpose writer offers its own
formatting rather than ours, so the writer was always going to be ours. The reader is the
smaller half of a job already begun.

**A whole number is told from a fraction.** `7` becomes a 64-bit integer and `7.0` a
double, because a whole number field must refuse `7.0` while a number field accepts `7`,
and the fixtures are full of whole-looking numbers in number fields. Nothing is needed to
carry the distinction: the two are different types already. Holding the whole one as a
64-bit integer also means a value too large for the model is refused where fields are
judged rather than truncated in the reader.

**A null is carried, not refused**, as a leaf holding nothing. A reader does not know what
a field is, so what a null means — most likely that the field is absent — belongs to
whatever reads against a description. No file in the fixtures or the libraries contains
one.

**Nothing is rendered back into text on the way in.** A boolean and a number reach a
description as a boolean and a number, and only a date, a gas, a reference and the rest
arrive as strings, because JSON has no such thing. Rendering `false` back to `"false"` so
that a description could parse it would make `"deco": "false"` and `"deco": false` the same
value, which the format says they are not.

**Two forgivenesses, in the reading only, and the writer offers neither.** A trailing comma
after the last member or element, so that deleting a line does not break the line above it.
And a byte order mark at the start, because editors on Windows leave one, nobody can see it,
and refusing it would point at a brace that looks perfectly correct. Everywhere else a byte
order mark is an ordinary character and the text rules refuse it.

**What it refuses beyond the grammar.** A name given twice, since this format keys items by
id and two members of one name is a real mistake rather than an undefined one. And nesting
beyond sixty-four deep, so that a corrupt file of nothing but brackets is reported instead
of exhausting the stack.

All forty files in `fixtures/` and `libraries/` are strict JSON, so nothing yet depends on
either forgiveness.

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
- **JSON-24 — Whether a rename carries the mentions in free text.** `JSON-17` makes a rename
   one changeset rewriting every reference to the old id. A mention is not a reference, so
   nothing carries it, and `@willy` goes on naming an id that no longer exists.

   For carrying it: the user wrote the mention meaning the person, and leaving it behind is
   data quietly decaying under a rename the user asked for. The machinery exists — a rename
   is already a changeset touching many items, and one more kind of edit is reversible like
   the rest.

   Against: a remark is prose somebody typed, and rewriting the middle of a sentence is editing
   their words rather than repointing a link. Worse, `JSON-23` makes a mention *have no fixed
   meaning*, so nothing can know that `@willy` was meant as one rather than being text that
   looks like one — rewriting it asserts a reading the format declines to make. And a partial
   guarantee may be worse than a stated absence: `*<key>` and `@<id>*<key>` cannot be resolved
   without a description, so only the plain `@<id>` form could ever be carried.

   Two things bear on it. Whether a journal action can address part of a string, or only
   replace a field whole, which is `JSON-16`'s neighbourhood. And what happens to a mention in
   a library item, which a logbook may not rewrite at all.

   Until this is settled nothing is promised either way, and the manual says nothing about it.
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
  declaration.** Each kind of item lives under **its own name** — `dive`, `person`,
  `region`, `dive_site`, `wreck`, `gear`, `certification`, `operator`, `dive_trip` — as
  either a folder of one file per item or a single file of them all, and the application
  uses whichever is there. Both at once is the one case it cannot resolve, and it says so
  rather than choosing.

  **The name is the type's own, so nothing maps one to the other.** A source is handed
  descriptions and reads `dive` for the type *dive*; it holds no list of type names, which it
  could not, being a source that has to work for a subject other than diving.

  The names were plural until the reader was written, and writing it showed what that cost:
  something had to turn *dive* into `dives`, and it could not be a rule. `gear` is uncountable
  and takes nothing, and `persons` is a deliberate departure from English, so any derivation
  was a table with two exceptions — `tool/checkdata.py` had been carrying exactly that table.
  Singular also agrees with the rest of the format, since `yemoja.json` already groups its
  libraries under `region` and `certification`.

  **A folder is read in sorted order; a grouped file keeps its own.** What a folder lists
  first is the platform's business, and two machines reading one logbook should hold it the
  same way. A file's order is one somebody chose, and a writer emits it settled so that
  saving an unchanged logbook produces no diff.

  **A file that is not named `.json` is passed over.** A logbook is a folder that someone
  may also keep other things in.

  **One unreadable file stops the whole read**, for now. A file that is not JSON, or that
  does not hold a set of items, ends the reading rather than being reported and skipped.
  That is the strict answer and probably not the last one: carrying on would need somewhere
  to report the file to, and nothing has settled what that is. It is a different matter from
  a value that cannot be believed, which is kept and reported by `DATA-24`.

  `yemoja.json` therefore has no `logbook` section and no paths of any kind. The
  flexibility removed was never used: every entry in `fixtures/cousteau` was the
  predictable one.

  The reason is that a path is the only thing in a logbook that can point outside it. An
  absolute path stops working the moment the folder is copied; `../shared/person.json`
  means the folder no longer *is* the logbook, so making it a repository or syncing it
  quietly leaves data behind. And a path carries a case-sensitivity trap across devices —
  `./Person.json` against a file named `person.json` works on Windows and fails on Linux
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
  text is where both markers stop being structure and become a convention, which
  `JSON-23` settles.

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

- **JSON-23 — Whether `@` and `*` mean anything inside multiline text.** *Settled:* **yes,
  by convention, and nothing enforces it.**

  A mention in free text — `@willy`, `*p1`, `@anna_devries*c1` — names an item, an entry
  of this item, or an entry of another one, in the two markers `JSON-19` already gives a
  field. An interface may draw it as a link, or show the item's name in its place, or pass
  over it entirely.

  **The text is the value.** A remark holding `@willy` holds those six characters. Reading,
  writing and comparing never see anything else, nothing is resolved on load or rewritten
  on save, and a mention naming an item that is not there is not a fault — it is text that
  happens to look like a mention. That is what free text means, and it is why this costs
  the reader nothing: no kind changes, and `remarks` is parsed exactly as before.

  One consequence follows, and it is the price of the convention rather than a defect: **a
  mention cannot be broken**, having never been a link, so `tool/checkdata.py` counts
  references and passes over these.

  **What a rename does to a mention is not settled here.** `JSON-17` makes a rename one
  changeset rewriting every reference, and a reference is a field; a mention sits inside a
  string nobody parsed, so the machinery that carries the one does not reach the other by
  itself. Whether it should be made to is `JSON-24`.

  **What a mention looks like.** After an `@`, the longest run of ASCII letters, digits,
  `_`, `-`, `.` and `#` — the characters `DATA-84` allows in an id. A trailing `_`, `-`,
  `.` or `#` comes off, no id ending in one, so a full stop closing a sentence stays a
  full stop. An empty run is no candidate at all. The run is folded to lowercase, which
  `DATA-84` makes unambiguous — an id is lowercase ASCII exactly so that `Anna` and `anna`
  cannot be two of them — so `@Willy` opening a sentence finds *willy* rather than finding
  nothing in silence.

  So `@willy,` names *willy*, `@blue_hole.` ends a sentence and names *blue_hole*,
  `@generic_0.5_kg_lead_weight` keeps the dot carrying its decimal, `@2026-02-23#0` names
  a dive whole, and `@ 22m` names nothing, the run being empty.

  **Nothing says where a mention may begin**, and no rule should. An `@` in a remark may
  be an address, a handle from another system, `@media` in a snippet, or the word *at* —
  which in a dive log is the likeliest of all: `max depth @ 22m`, `gas switch @ 21min`.
  What separates those from a mention is intent, and this question declines to read it.

  A rule was tried and dropped: that a mention must follow a space or a line start. It
  refused `tom@example.invalid` for a reason that turns out to be luck, an email's local
  part happening to be built from characters an id may hold, and it refused `(@willy)` and
  `"@willy"` along with it, which is prose anybody writes. It aimed at intent through a
  correlation and hit ordinary punctuation instead.

  **What makes a candidate safe is resolution, not grammar.** `@example.invalid` inside an
  address is looked up, found to be nothing, and goes on being the text it always was.
  `@30m` likewise. The candidate that does resolve is the one that was meant: `dived
  @ravensgate_quarry today` links the site.

  **So an interface marks only what resolves.** One that helpfully highlighted unresolved
  candidates, to catch a typo the way a spell checker does, would light up every address
  and every `@media` in the logbook. That is the one thing this convention asks of a
  reader, and it is the whole of it.

  **A mention is still read more narrowly than a field.** `DATA-84` binds where an id is
  minted, and reading a reference refuses only whitespace and `*`, so a field holding
  `@André` resolves or dangles visibly. A mention cannot be that generous: it has to find
  where a word ends, which a field holding one value never has to, so it stops at the `é`
  and offers *andr* — a candidate like any other, and like most of them one that resolves
  to nothing.

  **A bare `*<key>` is the weakest of the three.** `JSON-19` has a field's description say
  which collection a key belongs to, and free text has no description, so `*p1` says only
  *some entry keyed p1 on the item this is written on* — and an interface facing two
  collections that both hold that key must choose or decline. `@<id>*<key>` is no better
  off. Neither is pinned down further, because nothing depends on it: an interface that
  cannot resolve a mention unambiguously leaves it as the text it already is.

- **JSON-22 — How a logbook says whose it is.** *Settled:* a `user` key holding a reference
  to a person, `"user": "@jacques_cousteau"`, and it is **optional**.

  One place, and it is this one. The alternative was a field on a person saying *this is me*,
  which puts a logbook-wide fact on an item and then needs a rule for what two of them mean.
  A reference has none of that: a logbook names one person or names nobody.

  **A plain name is refused where an id is wanted.** An owner is an item, because it is what a
  certification, an emergency contact and a medical hang off, and a bare name can carry none
  of them. This is the one reference in the format that may not be a one-off.

  **Naming somebody who is not there is not an error.** The owner is then absent, exactly as a
  dangling reference is elsewhere — the person may be written later, and refusing to open the
  logbook over it would be a heavy answer to an optional field. Naming an item that is not a
  person is the same case: not the owner, and not a refusal.

  What *is* refused is a `user` that is not text, or is text that is not a reference. Those are
  a malformed file rather than a missing person, and the sibling declaration in the same file
  refuses the same way.

- **JSON-3 — What `yemoja.json` holds.** *Settled:* the structure of the logbook and
  nothing else — who owns it and which libraries it uses. *Amended:* it said *and where each
  type's files live*, which `JSON-21` later took away by making the layout a convention. The
  file holds no paths at all.

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
