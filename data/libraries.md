# Libraries

Reference data shipped with the application rather than owned by the user: regions
and their dive sites, a catalogue of commonly available gear, certification schemes.

A library is **not** part of a logbook. It is not versioned with it, not synced with
it, and not the user's to change.

## Rules

- **Read-only.** Nothing writes to a library.
- **Adding always goes to the logbook.** A new dive site is the user's, even if it
  sits in a region a library describes. Its id is worked out against the libraries as
  well as the logbook, so a new item can never land on a supplied id by accident — it
  takes an index instead.
- **Editing a library item copies it.** The edited version is written to the logbook and
  the library keeps its own, untouched. **This is the only way an item comes to shadow a
  supplied one**, which makes shadowing always deliberate: adding cannot cause it, and
  editing cannot fail to.
- **The logbook wins.** An item in the logbook shadows a library item with the same id,
  because the logbook is looked in first.
- **Library items cannot be deleted.** They can only be shadowed — and deleting the
  shadow brings the supplied one back, which is how a user undoes a copy and takes up
  whatever the library says now.

## Reference resolution

A reference names an id, not a place. Think of it as a search, in one order:

```
the logbook  →  first library  →  … →  last library
```

**The first to define an id wins**, and later definitions of it are passed over. The
logbook is looked in first, so it shadows every library; among libraries the one listed
first is preferred. One rule in one direction, rather than a search order and a separate
statement that the logbook takes precedence.

That makes the list in `yemoja.json` an order of preference, which is what a reader would
guess it is. It also means nothing already read is ever replaced: reading only ever adds,
so an item is final the moment it is in.

This is what makes shadowing work, and it means a reference never has to say *where* its
target lives — `@padi_owd` and `@jacques_cousteau` look identical and need no
distinguishing syntax.

## How a library is named

`yemoja.json` sits inside the logbook, which may be anywhere. Libraries live with the
application, whose location differs by platform and installation. A path relative to
the logbook cannot reach the application, and an absolute path stops working the
moment the logbook is synced to a phone.

**Libraries are therefore referenced by name, not by path:**

```json
{
  "libraries": {
    "region": ["region/world", "region/europe"],
    "certification": ["certification/padi"]
  }
}
```

The logbook declares *which* libraries it uses, grouped by the kind of item they
hold. Where they live is the application's business — they travel inside it, `LIB-6` — and
what a device has may differ on every device the logbook is opened on.

A name is an identifier, not a file name: no directory prefix and no extension. The
application supplies both, and the type comes from the declaration rather than from
anything inside the file.

This is now the whole of it: **`yemoja.json` contains no paths at all.** Where a
logbook's own items live is fixed by convention rather than declared — `dive/` or
`dive.json`, whichever is there — so the file names libraries and says who the logbook
belongs to, and nothing else. A path could point outside the folder, and a logbook that
does not contain itself cannot be copied, synced, or made into a repository.

Where these files live in this repository, and how one is written, is
[libraries/doc.md](../libraries/doc.md).

If user-supplied libraries are ever wanted, the resolution gains a second directory
and names resolve against both; nothing in the logbook changes. If libraries ever
genuinely need to live in arbitrary places, the fallback is a scheme naming a root —
`app:region/world`, `logbook:dive/` — but that is more machinery than the current
requirement justifies.

## Publishing is close to permanent

A library item cannot be deleted at runtime, and any logbook anywhere may hold
references to it. Removing an entry therefore breaks every reference written against it,
in logbooks this project will never see. Renaming an id does the same thing by
another route.

**Until the first release, none of this has bitten yet.** Nothing has been published, so
no logbook anywhere refers to a supplied id and any of them may still be corrected. That
window closes the day something ships, and it is the only chance to make them right for
free — a name that reads badly is worth fixing now rather than being carried for ever.

Correcting an item's *contents* is expected and safe. Changing what it is called, or
taking it away, is not. `LIB-5` settles that a logbook does not pin the edition it was
written against: a user who wants a supplied item to stop changing copies it in, where
it becomes theirs.

## Open questions

## Settled

- **LIB-1 — Whether a superseded copy is surfaced.** *Settled:* it is not. Once an item
  is copied into the logbook it is the user's, and the supplied version is no longer
  consulted for it or compared against it. A dive site frozen in 2026 keeps the
  coordinates it was frozen with, and nothing says they were later corrected.

  That is what copying is *for*, and the reason to leave it alone is that the application
  cannot honestly say much anyway. It holds the library's current version and the user's
  copy, and they differ — but they always differ, because differing is why the copy
  exists. Telling a library correction from the user's own edit needs what the library
  said at the moment of copying, and `LIB-5` settles that a logbook pins no edition.

  There was a way to have it. Copying is editing a supplied item, and `JSON-11` has an
  `edit` carry each changed field as before and after; if the *before* were the library's
  value, the journal would hold exactly what was superseded. That is a real option and it
  is declined, not overlooked — it would make a copy quietly dependent on history for its
  meaning, and `FEAT-4` is *Planned*, so it would do nothing at all for the first version.

  A user who wants the current supplied version has a plain way to get it: delete their
  copy. The library item is untouched underneath and reappears, since **library items
  cannot be deleted, only shadowed**.

- **LIB-4 — Version skew across installations.** *Settled:* accepted, and not a defect.
  Libraries ship with the application and do not sync, so two installations on different
  versions can resolve one reference to different content. That is what shipping
  corrections means.

  What bounds it is already settled elsewhere. **References never break**: *Publishing is
  close to permanent* forbids removing or renaming a published id, so every version
  resolves every reference and the skew is in content alone. **Correcting content is
  expected and safe** — the same section says so. And **the logbook is untouched**: a
  user's own items sync and agree, and only the reference data behind them differs.

  So the older installation is not wrong about the logbook, only behind on the atlas.
  A user who wants a supplied item to stop moving copies it in, where it becomes theirs
  and syncs like anything else — `LIB-5`.

  The alternatives were both worse for the same reason: they buy stability by making the
  library less useful. Showing which edition is in use needs libraries to carry a version,
  which is the pinning `LIB-5` declined, wearing a different hat. Copying every referenced
  item into the logbook removes the skew and the corrections together, and grows a logbook
  that quietly duplicates whatever it touches.

- **LIB-2 — Accidental shadowing.** *Settled:* it cannot happen by accident. **An id
  proposal is checked against the libraries as well as the logbook**, so a user adding
  their own Blue Hole where a library already has one gets `blue_hole#1` and the supplied
  item is untouched.

  Shadowing therefore only ever happens on purpose, and there is exactly one way to do it:
  **edit the supplied item.** The edited copy is written to the logbook, where it shadows
  what it came from — which is the same act as freezing it, settled in `LIB-5`. One
  gesture, one meaning.

  Nothing changes at read time. `DATA-15` already resolves clashes by appending an index;
  it was only ever consulting a smaller set than it should have. And it matters more here
  than elsewhere because the user never sees an id: a silent replacement would leave
  nothing on screen to notice, and every reference to the supplied item would quietly
  change target.

- **LIB-3 — Ordering between libraries.** *Settled:* allowed, and the first listed wins —
  as reference resolution already has it, the list being the order they are looked in.

  *Amended:* this said the last listed wins, when each library was laid over the one before.
  Reversing that rule left this sentence behind, saying the opposite of what the code does.

  Two libraries defining one id is not necessarily a fault. A club correcting a supplied
  dive site is `FEAT-16`'s whole purpose, and refusing the collision would force it to
  ship a replacement for the entire set instead of the one item it disagrees with. The
  user controls which wins by the order they are listed in, which is a thing they can see
  and change: the correction goes above the set it corrects.

  This is the one place where the collision rules differ by side, and deliberately: a
  *user* cannot shadow a library item by accident, because `LIB-2` gives their new item
  an index, while a *library* may shadow another on purpose, because that is what
  publishing a correction means.

- **LIB-6 — Where the supplied libraries are, at runtime.** *Settled:* **inside the
  application**, on its class path, read as resources. Nothing looks for a directory.

  The question this avoids is *where was the application installed*, which has no good
  answer. On the JVM the code source location is null under some class loaders and points
  into a build directory when running from source, so development and a release would differ
  exactly where a fault would hide. A property or an environment variable set by a launcher
  works until something starts the application another way, and then needs a fallback that is
  one of the other answers anyway. Per-platform conventions are five implementations. And on
  a phone the question does not arise: there is no installation directory to name.

  **The shape already fitted.** Libraries are named and never listed — see *How a library is
  named* — and a resource can be opened by name and cannot be listed. The two operations a
  library needs are the two a class path offers.

  What it costs is that the supplied set cannot be edited, which is already the rule: a
  library item is shadowed rather than changed, `LIB-2`, and never deleted. Replacing one
  means a release, which `LIB-5` and *Publishing is close to permanent* already say it does.

  This does not settle user-supplied libraries. If they are ever wanted, resolution gains a
  directory beside the resources and nothing about the logbook changes.

- **LIB-5 — Whether a library can be versioned or pinned.** *Settled:* no. Libraries
  carry no edition and a logbook records none. A user who wants a supplied item to stop
  changing **copies it into the logbook**, where it shadows the supplied one and is
  thereafter theirs.

  Nothing had to be built for that: it is ordinary shadowing, and `fixtures/cousteau`
  already does it — `netherlands` sits in the logbook's own `region.json`, the same
  content with a remark of the user's own. Copy twenty regions and twenty regions are
  frozen. The freeze is per item, so later additions to a library still arrive; a user
  who does not want a library at all leaves it out of the list.

  **Two alternatives were considered and set aside.** *Pinning* — recording which edition
  a logbook was written against — needs versioned libraries, a way to obtain old editions,
  and a rule for what happens when one is missing, all to serve a want that copying
  already meets. *A second static layer* holding the user's own library files was
  designed and dropped: it has no clean boundary against the logbook, because an edit to
  an item in your own library has nowhere to go that is not either the static layer, which
  then is not static, or a third layer above it.

  What copying does not serve is a **third-party** library — a club's dive sites, a
  shared vocabulary — where the point is to add a set you did not have, keep it
  identifiable, and replace it wholesale later. Absorbing its items loses all three. That
  is a different want and belongs with `FEAT-16` rather than here.
