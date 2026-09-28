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
  Re-downloading a dive must refresh the profile and leave everything the user
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

**The incoming items are a logbook of their own.** They are written to a folder of their
own and read back from it, and the review happens there: nothing touches the real
logbook until the whole import is applied, as one change.

That is what makes the review cost nothing to build. Everything that reads, edits and
saves a logbook reads, edits and saves these — `LogbookReader`, `LogbookWriter`, the
Universe's `change`, and every front end. An incoming dive is corrected with the same
keys as any other dive, and the correction is on disk the moment it is made.

An earlier answer here had the import apply to the live data without saving it, and the
user keep or discard the result. Staging is better on every count that mattered to it.
There is no unsaved state to lose, no window in which the logbook holds something nobody
has agreed to, and nothing for sync to be kept away from — the staged set is not the
logbook, so the question does not arise.

Three things it still turns on, two of them unchanged:

- **A changeset is one unit.** An import touching four hundred items must be
  revertible as a single labelled action, not four hundred separate undos. The same
  applies to a dive computer download. The journal is built this way — a list of
  changesets, each holding its actions, see
  [../data/json/doc.md](../data/json/doc.md) — so an import is one entry in it. Applying
  goes through one call to `change`, which already lands whole or not at all.
- **The user can see what is coming.** Reviewing four hundred items is only possible if
  what arrived is legible: how many are new, how many answer to something already held,
  and which. The window has it, `GUI-33`: the dives a line apiece with what each appears to be,
  and a count per type of everything else with how many of them are already held.
- **Applying twice does nothing the second time.** What was added answers to its id
  afterwards, so a second run meets it as the same item and writes what it already
  holds. An interrupted apply is safe to repeat.

### What applying does

**Whether ids may be matched at all is the source's to say.** Another Yemoja logbook carries
ids that mean the same on both sides. A source with none of its own has them minted on the way
in, and a minted id names what this model would have called such an item rather than which item
it is — so nothing is matched, and an arriving item whose id is already taken is minted afresh.
Getting that wrong loses data rather than merely confusing it.

**What cannot be worked out is asked.** Where nothing is matched, taking an item in puts the
question: is this new, or is it one already held? An item is proposed as the answer and the
reader says yes or otherwise, so being over-eager costs a keystroke and being wrong costs
nothing. That is what stands in for the heuristics this document asks for — a start time within
a tolerance, a duration, a maximum depth — which are three numbers nobody can pick well.

**Two rules propose, and neither has a number in it.** One that says when it was is proposed by
**overlapping in time**: an impossibility rather than a tolerance, nobody being on two dives at
once, so two recordings that overlap are two recordings of one dive. Everything else is proposed
by **the name its type would give it** — eight of the nine types propose an id from the item's own
`name`, so two logbooks each holding a site called Blue Hole both propose `blue_hole`, and the
proposal is the name match a reader would make by eye.

Two things that rule deliberately does not do. The index a proposal may carry is left off, since
it says where an item sat rather than what it is, which is what made an id unusable for matching
in the first place. And an item nobody named proposes nothing: two of those are not one, and
`unknown_person` says as much about either.

Two people both called John Smith are proposed as one, which is `JSON-6`'s collision — met here
where somebody can answer it rather than resolved by a rule.

Where ids are carried, an incoming item is met in one of three ways:

| Meeting | What it is | What applying does |
|---|---|---|
| **Nothing** | no item answers to its id | added under the id it came with |
| **The same** | an item of its own type under its own id | each field it holds is written onto that item |
| **Something else** | its id is taken by another type | nothing; it cannot go in |

**The id comes across rather than being minted.** An id is what matches an item between
two logbooks and it is what the references in the same import point at: mint a new one
and the dive that names the person no longer finds them.

**Fields the incoming item does not hold are left alone.** A source has opinions about
some fields and none about others, and silence is not an instruction to erase — a dive
computer knows nothing about buddies, and re-downloading a dive must not take them out.
That is why applying writes field by field rather than replacing the item, and why a field
that is itself a collection — the profiles, the gas sources, the environment — is laid over
the one held member by member rather than written in its place. A second computer's profile
lands beside the first; the visibility a computer never knew survives it. `RECON-6`.

**Something else** is rare and real: two logbooks can each mint `north_sea`, one for a
region and one for a person. Nothing is written for one, since giving it another id would
break every reference to it in the same import, and rewriting those is the work a
matching rule does rather than something applying should improvise.

### What is built

The shared half, and both sources over it: a dive computer read through `Universe.downloadFrom`
(`FEAT-3`) and another application's file read through `Universe.importFrom` (`FEAT-7`), each
bringing its own matching rule. Both front ends review one.

**Deciding is editing.** An item taken in leaves the staged logbook and so does one turned
down, so what is left in the folder is exactly what has not been decided. There is no list of
decisions kept beside the items and nothing to write when a review is put down: the folder is
the state. An earlier draft had a file of accepted and declined ids, which this replaced.

**An item goes in one at a time**, through a `change` of its own. That is what the front end
asks for and it is what makes a refusal legible — the item that could not go in is still on
the screen with the reason beside it. The cost is that an import is not one changeset but one
per item, which `FEAT-4` will want the other way round: taking the whole of what is left across
in one go is `apply`, and it is what a *take it all* key would call.

The staging folder is the logbook's own path with `.import` after it, which puts it outside the
logbook. What is being reviewed is not part of it and must not be read as though it were.

Three gaps worth naming rather than discovering:

- **A field the running version does not recognise is dropped when applying.** `DATA-65`
  keeps such a field through a read and a write of the same file; carrying one into
  another logbook is a different thing and needs a way to say it, which `Change` has not
  got.
- **A finished staging folder is left where it is.** Nothing cleans it up.
- **Collisions are not detected.** Every accepted item is applied; two versions of one
  field are not noticed, let alone put to the user. That is the interaction the storage
  requirements describe and it is not built.

## Two levels of support

Not every format deserves the same effort, and the difference is worth naming because it
decides something concrete: **only an actively supported format constrains the item
model.**

- **Actively supported.** Round-trip fidelity is a goal. `DATA-55` in
  [../data/doc.md](../data/doc.md) applies — where this model and that format record the
  same thing, they should record it the same way, so nothing is collapsed on the way out
  and back. Fixtures exist, and a break is a bug.
- **Best effort.** Read what can be read, drop what cannot, tell the user what was
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
user's existing archive, never for ongoing use. That makes vendor formats best-effort by
their nature. Some are one-way for other reasons — a manufacturer's cloud service may
accept only logs its own computers produced, so it can be a source but not a destination.

**Subsurface is deliberately absent.** It is the most used open-source logbook and its
format would be the obvious second candidate, but as far as is known it has no
specification apart from its implementation — so working the format out means reading GPL
code, which the project does not do. It stays off the list unless a specification exists
independently of the source.

**CSV is the floor.** No standard, a different shape from every application, and a user
with a spreadsheet is a real case. Best effort by definition, and probably needs the
columns mapped by the user rather than guessed.

## Open questions

- **RECON-2 — Whether an import can be accepted field by field** — taking some of an
   incoming item and not the rest. Accepting or declining a whole item is built; below the
   item it becomes the collision interaction the storage requirements describe, and waits
   on that.
- **RECON-7 — What lands when a second computer's dive meets one already logged.** Taking an
  arriving dive onto a held one writes every field the arrival holds, and for a second computer
  worn on the same dive that is wrong twice over. Its times and its maximum depth are written
  over the first computer's, though neither is more right than the other; and the dive ends up
  holding two profiles and naming no primary, which makes its date, its duration and its depth
  read back *unusable* rather than wrong. `primaryProfile` refuses to guess, correctly.

  The naming half is now settled and built: a change that gives a dive its second profile names
  the first as primary, `DATA-120`, so a merged recording no longer leaves a dive unusable. What
  is still open is the writing half. A second computer's recording should bring its profile and
  leave the times and the maximum depth alone, and whether that is a rule the review applies by
  itself — a recording from a computer the dive has no profile from is a profile and nothing
  else — or a question put to the user, is the decision. It is the half of `RECON-2` that was
  left open, met in a concrete case.

  Found by doing it: a hundred and two i330R recordings laid onto dives a Perdix had already
  recorded, beside the application rather than through it, for exactly this reason.

- **RECON-5 — Which formats to import**, at which level of support, and in which
   versions — see *Two levels of support* above. Also whether a common intermediate form
   is worth having, or each format converts directly to the item model.

## Settled

- **RECON-8 — What an agent's staged changes may hold, and how they land.** *Settled:*
  **additions, edits and deletions, and each edit remembers the value it replaced.** An agent
  allowed to change data, `API-5`, writes into a staging logbook of its own, as an import does
  by `RECON-1`, and the user reviews it and applies it. Nothing an agent does reaches the
  logbook without that.

  **A deletion is staged explicitly.** An import cannot say one, because a field or item absent
  from the staged set is left alone, which is the rule under *What applying does*. An agent's
  staging marks an item or an entry for deletion instead. The review shows it with the
  references it would leave naming nothing, and applying deletes through `Delete`.

  **An edit remembers what it replaced**, and the review shows each field before and after,
  which the review of an import does not. On applying, a field whose value in the logbook is no
  longer the one remembered is not written. The review says why, and every other field still
  lands.

  **An agent's staging and an import may both be waiting**, each reviewed and applied on its
  own. The protection runs one way. An agent's edit applied after an import is refused where the
  import changed that field. An import applied after an agent's edits remembers nothing and
  writes over them, which is the undetected collision named under *What is built*.

  **Built, less the review.** `Staging` in the logic layer holds it, reached as the Universe's
  `staging` and kept in a folder beside the logbook with `.proposed` after it. It is two copies of
  every item it touches — as it was when the change was staged, and as the agent would have it —
  both ordinary logbooks, so nothing new reads or writes them. Which of the three a change is
  follows from the pair rather than from anything marked: no *before* is an addition, no *after* is
  a deletion, and both is an edit whose fields are the difference between the copies.

  A field that has moved says so *before* anything is applied rather than after: each `Changed`
  carries what the logbook holds now beside what it held when the change was staged, so a review
  shows it and `apply` leaves it alone.

  **What landed leaves the staging, and what was refused stays in it**, still marked, until it is
  dropped or staged again. That holds field by field and whatever happened beside it. The first
  version emptied the staging whenever anything landed and kept everything when nothing did, so a
  stale field survived or vanished depending on its neighbours; the review found that on a copy of
  the fixture, a rating staged against 6 while the logbook held 7.

  **A review applies what it was shown.** A staging carries an edition that moves whenever anything
  is staged, dropped or applied, and `apply` takes the edition the reader was shown and refuses the
  lot where it has moved since. An agent goes on working while somebody reads, so without it an
  *apply all* could take in an item that appeared after the list was drawn — a deletion, say — which
  is applying what nobody agreed to.

  What shows all this is the window's, `GUI-38`.

- **RECON-4 — Whether importers are also exporters.** *Settled:* **for UDDF, yes, and export is
  not reconciliation.** One package reads and writes the format, `logic/uddf`, so the mapping is
  one table read in both directions and a vocabulary crossing it is one list of pairs. Export
  raises none of this document's questions — nothing arrives, nothing is matched, nothing is
  staged — so it does not pass through the machinery here: the Universe writes the file and
  says what went. Whether another format gets a writer is that format's question under
  `RECON-5`.
- **RECON-1 — Where unsaved state lives.** *Settled:* **on disk, as a logbook of its
  own.** The question was written as a trade-off — in memory is simpler, staging is safer
  and costs more machinery — and the trade-off turned out not to exist. Incoming items
  are a logbook, so staging them costs nothing that is not already built, and every edit
  made while reviewing is saved as it is made. There is no unsaved state anywhere.
- **RECON-2 — Whether an import can be accepted in part.** *Settled:* **it can, item by
  item.** Everything arrives accepted and declining is the decision, because what arrives
  cleanly is meant to land and the user vetoes rather than approves. What is declined is
  kept beside the items, so an interrupted review is not lost. Accepting *within* an item
  is the remaining half and is still open above.
- **RECON-3 — Whether an unresolved item can be parked.** *Settled:* it can, and is
  parked whole rather than field by field. See `REQ-17` in
  [../data/json/requirements.md](../data/json/requirements.md).
- **RECON-6 — How an arriving item lands on one already held.** *Settled:* **laid over it
  member by member.** Applying writes field by field, and a field that is itself a collection
  — the profiles, the gas sources, the environment — is laid over the held one member by
  member rather than written in its place, all the way down: a profile arriving under a new
  key lands beside the held ones, one arriving under a held key has its fields laid over that
  profile's, and a series or a list is replaced whole, half of one not being a thing. What it
  does not do is tell two gas sources apart, so a re-download puts the computer's beside the
  user's rather than onto them; that is the collision above, still not built.
