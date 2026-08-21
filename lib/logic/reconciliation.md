# Reconciliation

Merging items that arrive from somewhere else into the logbook.

Three situations produce items the application must fold into what it already has:

| Situation | Direction | Frequency |
|---|---|---|
| **Sync** with another Yemoja installation | Both ways, repeatedly | Often, small |
| **Download** from a dive computer | Inbound | Every trip, heavily overlapping |
| **Import** from another application's file | Inbound | Rare, potentially the whole logbook at once |

They look different but pose the same question, and it is the same question
[storage requirements, section 4, *Conflict resolution*](../data/json/requirements.md)
asks: *for each incoming
item, is it new, is it one I already have, or is it one I already have but
different?* — and if different, which version wins, decided by rules where possible
and by the user where not.

Building that machinery once is worth doing. What follows is where it genuinely is
one problem, and where it is not.

## What is shared

The decision itself, and everything after it:

```
per source:   read → convert to the item model → match against existing items
shared:       compare → classify → resolve → apply
                              │        └── rules where unambiguous, ask the user where not
                              └── new / identical / differing
```

The shared half is the conflict resolution described in the storage requirements.
The per-source half is not shareable, for the reasons below.

## What differs

### Id — how an incoming item is matched to an existing one

This is where the three diverge most, and it is the part each source must supply.

| Source | Matched by | Reliable? |
|---|---|---|
| Sync | The item's own id, shared by both sides | Exact, with one exception below |
| Dive computer | Device serial plus the device's own per-dive fingerprint | Exact and stable |
| File import | Heuristics — start time within a tolerance, duration, maximum depth | **Fuzzy; can be wrong in both directions** |

File import can genuinely mis-identify: it can fail to recognise a dive it already has
and duplicate it, or wrongly fuse two distinct dives. That risk is heuristic and belongs
to import alone, and should not be allowed to leak into the shared machinery.

**Sync has one failure of its own, and it is not heuristic.** Two installations offline
can each create a different item that proposes the same id — two John Smiths, both
`john_smith` — and matching on the id alone then fuses two people with nothing looking
wrong. `JSON-6` in [../data/json/doc.md](../data/json/doc.md) settles it: the journal
records which installation created each, so two items sharing an id with no common
ancestor are a collision to be asked about rather than a match. Sync is exact once that
check is in place, and quietly wrong without it.

### Ancestry — whether a common starting point exists

**The sharpest difference, and the reason these cannot be fully unified.**

- **Sync has a common ancestor.** Both installations diverged from a state both once
  had, so for any differing field it is knowable *which side changed it*. If one side
  changed a value and the other did not, there is no conflict at all — take the
  change. Only fields both sides edited need a decision.
- **Download and import have no ancestor.** Only two versions exist. A differing
  value is just a difference; there is no way to tell who changed what. Far more
  cases need either a rule or the user.

So the resolver must accept an *optional* common ancestor and behave differently with
and without it. Forcing sync through an ancestor-less path would discard the
information that makes sync merges mostly automatic.

### Authority — what a source is entitled to overwrite

A source has opinions about some fields and none about others, and silence must not
be read as an instruction to erase.

- **Dive computer**: authoritative for what it measured — profile, times,
  temperatures, depths. Knows nothing about buddies, sites, gear or notes.
  Re-downloading a dive must refresh the profile and leave everything the diver
  typed untouched.

  It also reports figures that can be derived from what it already gave us: a maximum
  depth alongside the profile that maximum came from. Those are kept only where they
  *differ* — see [../data/doc.md](../data/doc.md). A device that agrees with its own
  recording has added nothing, and writing it down twice creates two things that can
  later disagree.
- **File import**: mixed. May carry both measured and user-entered data, and its
  user-entered data may be better or worse than what is already held. Whatever it carries
  that this model cannot read is not authority but ballast: an opaque payload is the
  other application's derived data, and taking it in would store a derivation whose
  inputs can change here and whose dependencies cannot be seen. See *Application data* in
  [uddf.md](uddf.md).
- **Sync**: peers. Neither side has standing over the other.

## Repositories and importers

"Data source" would otherwise mean two different things, and the collision would cause
confusion, so the two are named apart:

- **Repository** — a place the logbook *lives*. Read and written, holds history.
  Only [JSON files](../data/json/doc.md) planned. Sync is between two repositories
  of the same logbook.
- **Importer** — a place items *come from*, one way, with no ancestry and no
  history: a dive computer, another application's export.

A dive computer is a source of data but not somewhere the logbook lives. Keeping one
word for both invites treating them as interchangeable, which the ancestry difference
above shows they are not.

This also answers the open question of where dive computer support belongs: an
**importer**, feeding the shared reconciler — not a repository, and not a layer of
its own. Reading the device is a per-source concern; everything after conversion to
the item model is shared.

Note that *dive computer* names a role, not an item type. The device is stored as an
ordinary piece of gear, indistinguishable from a regulator; what makes it a dive
computer is that dives can be downloaded from it. Whatever downloading needs to
remember between sessions — which dives have already been fetched, how to reach the
device again — attaches to that gear item rather than to a type of its own.

## How much the user is asked

Rules resolve most conflicts automatically. The user is asked only where the rules
cannot decide, and where a single answer can settle many items at once, it is
offered in bulk rather than repeated.

Which cases are automatic, which are asked, and which admit a bulk answer is not yet
decided. The constraint that shapes it: importing another application's logbook can
produce hundreds of decisions at once, and an interface that asks four hundred
questions has failed regardless of how correct each one is.

## Reviewing before committing

**There is no separate dry-run mechanism.** An import applies to the live data
without saving it; the user reviews the result and either keeps it or discards it.
Once saved, version history can still take it back.

This reuses machinery the project needs anyway rather than building a parallel
preview path, but it only works if three things hold:

- **A changeset is one unit.** An import touching four hundred items must be
  revertible as a single labelled action, not four hundred separate undos. The same
  applies to a dive computer download. The journal is built this way — a list of
  changesets, each holding its actions, see
  [../data/json/doc.md](../data/json/doc.md) — so an import is one entry in it.
- **The user can see what changed.** "It already happened, check it" is only
  reviewable if the change is legible: how many items were added, merged and
  skipped, and which. A large import is not reviewable by scrolling the logbook. The
  summary needed here is the same one versioning needs to answer *where did this come
  from*.
- **Unsaved state is not visible to sync.** Syncing must act on saved data only, or
  be blocked while a review is outstanding. Otherwise an unreviewed import propagates
  to another installation.

## Two levels of support

Not every format deserves the same effort, and the difference is worth naming because it
decides something concrete: **only an actively supported format constrains the item
model.**

- **Actively supported.** Round-trip fidelity is a goal. `DATA-55` in
  [../data/doc.md](../data/doc.md) applies — where this model and that format record the
  same thing, they should record it the same way, so nothing is collapsed on the way out
  and back. Fixtures exist, and a break is a bug.
- **Best effort.** Read what can be read, drop what cannot, tell the diver what was
  dropped. No claim of fidelity and no vote on how a field is shaped. Failing on an
  unusual file is acceptable; failing silently is not.

The second is not a lesser version of the first, it is a different promise. A format with
no specification cannot be actively supported however much effort goes in, because there
is nothing to be correct against.

### Where the candidates fall

A field-by-field comparison of UDDF against this model is in [uddf.md](uddf.md).

**UDDF is the one open, published, general interchange format**, and it behaves as a hub
rather than as one format among many: several logbook applications read and write it, so
supporting it well reaches more of them than supporting their native formats badly. It is
the only candidate for active support, and the only one where `DATA-55` can be satisfied
at all.

One complication to plan for: **UDDF versions are not interchangeable in practice.**
Files in the wild are written to 2.x and to 3.2, and at least one application accepts only
the newer. So "which formats" has a second half — which versions are read, and which one
is written. This document works from 3.2.3.

**Dive computers are already handled**, and not as a format. `FEAT-3` reads the devices
themselves through a library, so a vendor's desktop-application file is only needed for a
diver's existing archive, never for ongoing use. That makes vendor formats best-effort by
their nature. Some are one-way for other reasons — a manufacturer's cloud service may
accept only logs its own computers produced, so it can be a source but not a destination.

**Subsurface is deliberately absent.** It is the most used open-source logbook and its
format would be the obvious second candidate, but as far as is known it has no
specification apart from its implementation — so working the format out means reading GPL
code, which the project does not do. It stays off the list unless a specification exists
independently of the source.

**CSV is the floor.** No standard, a different shape from every application, and a diver
with a spreadsheet is a real case. Best effort by definition, and probably needs the
columns mapped by the diver rather than guessed.

## Open questions

- **RECON-1 — Where unsaved state lives.** Held in memory, or staged on disk as a pending
   changeset? In memory is simpler, but a phone can be killed at any moment and the
   review work is lost — which argues for staging, at the cost of more machinery.
- **RECON-2 — Whether an import can be accepted in part** — keeping some items and rejecting
   others — or only as a whole. Partial acceptance is closer to per-item conflict
   resolution and may be the same interaction.
- **RECON-4 — Whether importers are also exporters.** Interoperating with other applications
   is likely wanted in both directions, but export raises none of these questions and
   may not belong here at all.
- **RECON-5 — Which formats to import**, at which level of support, and in which
   versions — see *Two levels of support* above. Also whether a common intermediate form
   is worth having, or each format converts directly to the item model.

## Settled

- **RECON-3 — Whether an unresolved item can be parked.** *Settled:* it can, and is
  parked whole rather than field by field. See `REQ-17` in
  [../data/json/requirements.md](../data/json/requirements.md).
