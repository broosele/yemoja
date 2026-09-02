# Storage requirements

What the storage system must do, stated before deciding how. These are requirements,
not a design — the mechanism that satisfies them is an open question in
[doc.md](doc.md).

Four capabilities are expected: versioning, syncing, backup, and conflict
resolution.

**None of this is in the first version.** `FEAT-4` and `FEAT-5` are *Planned*, not *Core*
— see [../../../features.md](../../features.md) — so nothing here is implemented at
first. The requirements are settled all the same, and they bind the file format now: a
format that cannot carry a journal, or that cannot tell where an item came from, could not
gain either later without rewriting every logbook already written. Read this as a
constraint on what may be built first, not as a description of it.

## 1 — Versioning

**Purpose: correct mistakes, and understand how a wrong value got there.**

Two separate needs, and the second is the demanding one:

- **Fix errors.** A previous state of an item can be restored.
- **See where an error came from.** Given a value that is wrong, it must be possible
  to find out when it changed, and what caused the change.

The second means every change carries metadata, and that metadata has to be good
enough to answer "what caused this" in terms a person recognises — a manual edit, a
dive computer import, a bulk change, a sync from another device.

This does **not** require branching, distributed development, or automatic merging.
It requires a recorded, attributed, inspectable history per item.

**Changes group into units.** A dive computer download or an import from another
application changes many items at once, and must be revertible as one labelled
action rather than item by item. This also makes undo the mechanism by which an
import is reviewed — see
[../../logic/reconciliation.md](../../logic/reconciliation.md) — so a group needs a
description legible enough to review: what was added, changed and left alone.

### Open


## 2 — Syncing

**Purpose: several installations of Yemoja converge on the same logbook.**

Assumed to mean installations of this application — desktop and phone — rather than
exchange with third-party dive software, which is import/export and a different
feature. *Flagged as an interpretation; correct it if it is wrong.*

**Sync goes through a location the user provides** — a git remote, a folder on a cloud
drive, a share on a network disk. Not device to device, and not through anything this
project runs.

Three things follow, and the third is the reason:

- **Backup is very nearly free.** That copy is already off the device and already
  current, so requirement 3 is largely answered by requirement 2. There is no separate
  backup mechanism, no second schedule and no second status: the sync indicator says
  whether anything is owed upward, and when it says nothing is, the copy at the location
  is the backup and it is current.
- **Neither installation need be awake at once.** A phone that syncs on the boat and a
  desktop that syncs a week later never meet, and do not have to.
- **The project never holds anyone's data.** A person item can carry a birthday, an
  address, a blood group and the date of a medical, and most such items describe other
  people. Keeping that out of any infrastructure this project runs is worth more than
  the convenience of putting it in.

The cost is that the user must have somewhere and set it up. That is a real barrier,
and making it painless is the interface's problem.

**Syncing is explicit.** It happens when the user asks and at no other time, so nothing
surprising ever runs and there is never a question about which version went where.

What makes that safe rather than merely predictable is that the application always shows
whether a sync is *owed*, in either direction: changes made here that have not gone up,
changes made elsewhere that have not come down, or both at once. Without it, the
commonest way to lose a trip's dives is forgetting to press a button — which is the
situation sync exists to prevent.

Two different things are therefore needed. Knowing what is owed upward is free: the
journal knows which changesets have been synced. Knowing what is owed downward means
asking the location, which has to be far cheaper than a sync — checking a marker rather
than reading a logbook.

### Open


## 3 — Backup

**Purpose: the logbook survives losing the device it was on.**

### Open


## 4 — Conflict resolution

**Purpose: reconcile the same item changed in two places, using the application's
own rules and the user's judgement.**

Explicitly **not** delegated to a generic text or version-control merge. Conflicts
are resolved with logic that understands what a dive is, and with user input where
the logic cannot decide alone.

**Most of what would be a conflict elsewhere is not one here.** The journal works in
fields, so two installations that touched different fields of the same dive have not
collided at all, and neither have two that touched different items. What is left is
narrow: the same field of the same item, set to two different values, in two places.

**A collision never has to be dealt with now.** Everything that merged cleanly is
applied, and any item with a collision is set aside — keeping its local version whole
and consistent — until the user chooses to answer. Not the contested fields but the
whole item, so nothing is ever half-merged and no item exists in a state its own
rules would reject.

The price is that clean changes to a deferred item are held with it: a note edited on
the phone waits behind a depth that two installations disagree about. That is accepted
for a plainer outcome — an item is either merged or waiting, never partly both. It also
means a sync is not finished while anything is deferred, and the indicator that says
whether a sync is owed has to count those too.

**What the user is shown is the item, twice.** Both versions in the item view they
already read items in, with the fields that disagree picked out and everything else
left as context — because which dive it is, and what else changed alongside, is usually
what makes the answer obvious. Values are taken from either side, field by field, and
what comes out is one item.

**A profile is one value, not a thousand.** It is compared whole and replaced whole, and
never merged: two recordings of the same dive that differ are two recordings, and half of
each is not a third. Identical profiles are not a conflict at all. Different ones are, and
the choice is presented by what actually distinguishes them — which computer, how long,
how deep, how many samples — rather than by any attempt to show the samples themselves.

That follows from what a profile is. Every other field is something a person decided, and
two people deciding differently can sensibly be reconciled value by value. A profile is
one continuous recording made by one device, and it is only coherent entire.

Two things are deliberately avoided. A bare question — *31.4 or 32.1?* — is easy to
present and strips away exactly the context that would answer it. And choosing a whole
side is one decision that discards good edits from the other, which is the outcome the
field-level design exists to prevent.

**Every collision is put to the user.** Both values were typed deliberately and
neither is more right; a rule picking between them — most recent, longest, fullest —
would be guessing, and its cost when wrong is silently throwing away something somebody
wrote. Asking is only bearable because it is rare, and it is rare because everything
else already merges without a word.

This is the requirement that most constrains the mechanism choice, because it
removes the main thing a version control system would otherwise have contributed.

The same machinery serves dive computer downloads and imports from other
applications, which pose the same question without a common ancestor to help. See
[../../logic/reconciliation.md](../../logic/reconciliation.md).

### Open


## Consequence for the mechanism choice

Section 4 above rules out using a version control system's merge. Anything of that
kind would therefore be providing history, attribution and transport only — not
conflict handling.

That materially narrows what such a dependency would buy, and the mechanism question
in [doc.md](doc.md) should be re-asked in that light rather than assuming the earlier
framing still holds.

## Settled

Kept with their identifiers so earlier discussion still resolves.

- **REQ-5 — Topology.** *Settled:* Sync goes through a location the user provides. Not
  device-to-device, and not through infrastructure this project runs.
- **REQ-6 — Who provides the location.** *Settled:* The user does.
- **REQ-10 — Whether backup is a by-product of syncing.** *Settled:* Largely yes, given REQ-5. The
  synced copy is off-device and current; what is left is being able to restore from it.
- **REQ-8 — Whether history travels with the data.** *Merged into `REQ-4`*, which asks
  the same thing from the versioning side.
- **REQ-13 — Whether a backup is usable without Yemoja.** *Settled:* yes, necessarily.
  A backup is a copy of the logbook folder, which is readable JSON with its format
  documented and given away. Nothing else was ever on offer.
- **REQ-1 — What "where it came from" must identify.** *Settled:* three things — when it
  happened, which installation did it, and what kind of operation it was: a manual edit,
  a dive computer download, an import, a sync. All known at the time, none needing to be
  asked for. Recorded on the changeset, see `JSON-15`.
- **REQ-4 — Whether history itself syncs.** *Settled:* it does, along with everything
  else. Any installation can then say where a change came from, including one that
  arrived from the other — which is the case the requirement exists for. The price is
  that two journals must merge on sync, which is the hardest part of the design and is
  `JSON-10` and `JSON-13`.
- **REQ-2 — Restore granularity, from history.** *Settled:* a changeset, whatever it
  touched — one field or four hundred items. It is the unit the journal is built from,
  so it is the unit that comes back.
- **REQ-11 — Restore granularity, from a backup.** *Settled:* the whole logbook, or a
  single item lifted out of it. A backup is a folder of readable files, so picking one
  out needs no machinery.
- **REQ-3 — How far back.** *Settled:* everything, for ever. A changeset is a few hundred
  bytes; twenty years of an unusually busy logbook is smaller than one dive's profile.
  Nothing is discarded on a schedule.
- **REQ-7 — Automatic or explicit.** *Settled:* explicit. Nothing syncs on its own. The
  application does, however, show at all times whether anything is waiting — changes here
  that have not gone up, changes there that have not come down, or both.
- **REQ-9 — Long absences.** *Settled:* nothing special happens. An installation away for
  eight months has changesets the location lacks and lacks changesets the location has,
  which is the ordinary case with larger numbers. It works only because history is never
  discarded — see `REQ-3` — so the common ancestor is always still there. There is no
  threshold, no second code path, and no notion of a device having been away too long.
- **REQ-12 — Automatic or manual, and how the user knows a backup is current.**
  *Settled:* there is no separate backup mechanism and no second status. The sync
  indicator already says whether anything here has yet to go up; when it says nothing is
  owed, the copy at the location is current, and that copy is the backup.
- **REQ-15 — What resolves automatically and what asks.** *Settled:* every genuine
  collision is asked about. A rule choosing between two deliberate values would be
  guessing, and the one outcome worth avoiding is quietly discarding something a user
  typed.
- **REQ-17 — What happens to an unresolved conflict.** *Settled:* the item is deferred
  whole. Everything that merged cleanly is applied; an item with a collision keeps its
  local version, entire and consistent, until the user answers.
- **REQ-16 — What the user is shown.** *Settled:* the item twice, in the ordinary item
  view, with the disagreeing fields picked out and the rest as context. Values are taken
  from either side field by field, and the result is one item.
- **REQ-18 — Profile and sample data.** *Settled:* a profile is one indivisible value.
  It is compared whole and replaced whole, never merged, and where two differ the user
  picks a side.
- **REQ-14 — Granularity.** *Settled:* per field, since the journal records changes per
  field — see `JSON-11`. A profile is the exception and is one value entire, see
  `REQ-18`.
