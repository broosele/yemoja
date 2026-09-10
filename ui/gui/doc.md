# UI layer — GUI

The application most people will use: polished, and aimed at a broad audience
rather than at users who are comfortable with a terminal.

One application, two form factors: [desktop](desktop/doc.md) and
[phone](phone/doc.md). They share their structure and differ only in how that
structure is placed on screen.

## Priority

1. **Windows desktop** — first
2. **Android phone** — second
3. macOS, Linux, iPhone — later

Earlier targets must not be designed in a way that blocks the later ones.

## Structure

### Tabs

The application is divided into **tabs**. One is visible at a time, and the means of
choosing between them is always *accessible* — whatever else is happening, another tab
is reachable without unwinding what you are doing.

Accessible, not necessarily visible. Eight labels fit comfortably down the side of a
desktop window and will not fit across the foot of a phone, so the phone is free to keep
the switcher one gesture away rather than permanently on screen.

There are eight:

- **Home** — a greeting, anything needing attention, and a few figures worth seeing
  without asking.
- **Dive** — dives and dive trips.
- **Gear** — equipment.
- **Community** — people, operators and certifications.
- **Location** — regions, dive sites and wrecks.
- **Statistics** — everything counted and summarised.
- **System** — settings, syncing and the rest of the machinery.
- **Manuals** — the documentation, read inside the application.

### Selector, item view, edit view

Most tabs are built the same way. A **selector** narrows a collection down to one
item, and an **item view** shows that item.

The two have different jobs and are written differently:

- The **item view presents**. It is arranged for reading — the things worth seeing
  first, seen first, and the rest available without being in the way. It never changes
  anything.
- The **edit view is complete**. It is reached from the item view, and it is dry and
  functional by design: every field the item has, laid out to be filled in rather
  than admired.

That split is deliberate. A view that both reads well and edits everything does
neither, and the compromise usually costs the reading.

### What is shared

One definition, used by both form factors. None of this may be decided per
platform:

- **Which main tabs exist**, and what belongs in each.
- **Which fields an item shows**, in the item view and the edit view alike.
- **How fields are grouped and ordered**, and what each group is called.
- **Which fields sit directly on an item and which are grouped into an owned
  item** — see [../../data/doc.md](../../data/doc.md). The interface reads that shape
  as its starting point for prominence; the data layer has no say in it.
- **Which actions exist** on each view, what they are called, and when they are
  available.
- **What counts as valid input**, and how a rejection is explained.
- **Navigation intent**: "show the list of dives", "open this dive", "edit this
  dive". The intent is shared; satisfying it is not.

If a change to any of the above is wanted on one form factor only, that is a signal
the definition is wrong, not that an exception is needed.

### What differs

Only placement and density:

- **How an intent becomes screen space.** Opening an item is a pushed screen on a
  phone and an item view filling a pane on desktop. Editing may be a separate screen, an
  inline panel or a dialog. The intent is the same; the mechanism is not.
- **How a group is laid out** — one column or several, how much is visible at once.
- **How much is shown before collapsing** — see progressive disclosure.
- **Input affordances**: keyboard shortcuts, context menus and drag-and-drop on
  desktop; long-press, swipe and gesture on phone.
- **Where the application's own controls sit**: menu bar and navigation rail versus a
  bottom bar.

### Progressive disclosure

Field *membership* is shared; the *number shown at once* is not. A dive has far more
fields than a phone screen can show without becoming unusable, and the answer is not to
give the phone a different field set — that guarantees the two drift apart and that
some data becomes uneditable on one platform.

Prominence is not annotated anywhere in the data. The item has a *structure* —
described in [../../data/doc.md](../../data/doc.md) — and the interface reads it as its
starting point, treating the levels this way unless a particular item type calls for
something else:

- **Name** — titles the item, and is what a link to it reads as.
- **Fields directly on the item** — always shown.
- **A singular owned item** — a group of detail. Expanded by default on desktop,
  collapsed on a phone. This is the only line where the two form factors differ.
- **Keyed collections of owned items** — collapsed to a count or a short summary, opened on
  demand, on both.

Selectors show the name, plus a small chosen set of the item's own fields.

Every field stays reachable everywhere; only how much is open at once changes.

### How the split is expressed

The shared part is a **description** — data — and each form factor has a renderer
that consumes it. It is not a base class with abstract methods overridden per
platform.

It sits on top of a description that already exists. The logic layer describes what a
field *is* — its type, its unit, whether it is a reference, whether it offers a
vocabulary — see [../../logic/doc.md](../../logic/doc.md). This layer describes what is
*done* with it: which fields appear, how they group and order, what each group is
called, and what a chooser offers. `GUI-16` settles that the split falls exactly
there, so the two are layered rather than merged, and the lower one never grows an
opinion about presentation.

The separation is the one described above either way, but description-plus-renderer
is preferable because:

- A description can be tested without constructing any interface at all.
- The [tui](../tui/doc.md) can consume the same description rather than
  reimplementing which fields exist and how they group.
- The toolkit is composition all the way down — an interface is functions calling
  functions, with no hierarchy to subclass. A design that wanted a platform-specific
  subclass would have nothing to subclass, so description-plus-renderer is not merely
  preferable here but the only one of the two the toolkit expresses.

### Choosing among known values

Two kinds of field offer the user a set to pick from, and they are not rendered the
same way.

A **fixed set** — a dive site's water type — is exactly its listed values. The chooser
offers those and no way to type anything else, because anything else would be
unusable.

A **suggested vocabulary** — a region's kind, a dive site's facilities, a dive's tags —
takes any string at all. The data layer refuses nothing, so the help belongs here
rather than there.

For those, the shared description marks the field and each form factor renders a
chooser offering, in one place:

- the values already used elsewhere in this logbook,
- the values supplied with the application,
- and the option of typing something new.

Presenting them together is the point: a user who has written `slipway` once should
not have to remember whether they wrote `slip way` the second time, and should still
be free to write whatever they like. How the chooser looks — a dropdown beside the
field on desktop, a full-height sheet on a phone — is a form-factor decision like any
other.

The [tui](../tui/doc.md) offers no such help; a plain string is accepted as typed.

### An id is never shown

An item's id — what names its file and what references point at — is not
surfaced. It is assigned when the item is created and never presented, chosen or
edited. Users work with items by their name and date, not by their id.

Because the id is worked out from the item's own data, the interface has to
make sure that data is there when an item is made: a new dive needs its date before
there is anything to propose from. Nothing below this layer requires it — storage
insists on no field at all — so this is the only place the issue can be forced, and
forcing it once at creation is cheaper than every later screen coping with a dive that
has no date.

Editing ids is a plausible later feature for advanced users, and would carry
the renaming cost described in [../../data/json/doc.md](../../data/json/doc.md): every
reference has to move with it, as one revertible action.

### Where the split does not apply

The dive profile chart differs in more than layout — pointer hover and tooltips
versus pinch and tap are different interactions, not different arrangements. Expect
a genuinely separate implementation per form factor rather than a shared
description.

## Scope

- The feature set as the user encounters it: logging dives, gear, sites, buddies,
  planning, and importing from a dive computer.
- The four view kinds above, and the shared definitions that drive them.
- The visual and interaction language: shared components, typography, colour,
  spacing, iconography, empty and error states.
- Settings the user controls, including units.
- Accessibility and localisation.

## Not in scope

Anything computed rather than shown — see [../doc.md](../doc.md) for the rules that
bind every front end.

## Cross-cutting requirements

- **Offline is the normal case**, not an error state. Nothing may block on a
  network, and the UI must not nag about being offline.
- **Long operations** — dive computer downloads, imports, sync — need progress,
  cancellation, and a way to recover from failure partway through.
- **Decompression output is a planning aid.** Wording and presentation must not
  imply otherwise. This needs review before any release.
- **Large logbooks** stay responsive: decades of dives, each with a profile.

## Toolkit

The intended toolkit is Compose Multiplatform, which covers all five targets from one
codebase and has adaptive navigation primitives that suit the structure above. Android is
where it is strongest, which is where this project wants it strongest; iPhone is the
youngest of the five and also the last in line.

Two things to plan around: it provides no adaptive layout automatically — both
layouts are still designed and written, the toolkit only prevents them being two
applications — and its desktop support is less mature than its mobile support.
Keyboard shortcuts, text selection, context menus and window management all work but
cost more effort than their mobile equivalents, which is the friction a data-dense
application feels most.

## What is built

**A window that reads a logbook, and nothing more.** The tabs down the side, a selector listing
what a tab holds by type, and an item view showing the fields of the one chosen. Owned items are
counted rather than spelt out, which is the progressive disclosure above meeting its first real
item. Three tabs hold no items yet and say what they will hold rather than showing an empty box.

**Nothing changes anything.** There is no edit view, no action and no key that writes. That is
the scope rather than an oversight: a first version proves the toolkit, the layout and the door
into the logic layer, and each of those is easier to judge when nothing can go wrong.

It opens as `yemoja gui <logbook folder>`, beside the terminal front end, and unlike that one it
can be run from the build, a window needing no console.

Four things it does badly, each of them an open question above rather than a bug:

- **A selector row is the item's title and nothing else.** A dive has no name, so three hundred
  rows read `2026-08-28#2`. `GUI-10` is which fields a row shows, and this is the case that
  makes it urgent rather than tidy.
- **The second type in a tab is unreachable**, sitting below every item of the first. A tab
  holding several types needs more than one list after another.
- **A reference reads as it is written**, `@shaab_el_erg_-_dolphin_house` rather than the name
  of the thing it points at, and a key reference likewise. That is the *an id is never shown*
  rule broken by the back door: nothing shows an id as an id, and a reference spells one out.
- **A worked-out value is greyed and an unreadable one reddened**, which is a placeholder for
  `GUI-8` rather than an answer to it.

`GUI-5` stays open. What the smallest *usable* version contains is not answered by a version
that cannot edit.

## Open questions

- **GUI-17 — Where the sync indicator lives.** Syncing is explicit, and the application
  must show at all times whether anything is owed in either direction — see
  [../../data/json/requirements.md](../../data/json/requirements.md). Putting it in the
  system tab is tidy and means nobody sees it; putting it among the application's own
  controls makes it permanent furniture for something that is usually saying "nothing to do".
- **GUI-18 — Correcting a recording's water type.** A profile's `water_type` says what
   the computer was set to, and the depths it wrote down were computed with it — see
   `DATA-59` in [../../data/doc.md](../../data/doc.md). A user who corrects it has
   changed what every depth in that recording means, not the depths themselves.

   Two things follow that are this layer's to do. It must **say so before the change is
   made**, because nothing about the screen suggests that editing one field silently
   reinterprets a thousand others. And it should **offer to convert** — recomputing the
   depths so the dive stays the same dive — which is a different act from correcting a
   mistyped setting and must be distinguishable from it. Open: how the two are put to a
   user without the wording implying that one is the safe choice.
- **GUI-3 — How much visual identity to define up front** versus adopting the platform's
   defaults and refining later.
- **GUI-4 — Dive profile rendering** is the most demanding piece of the interface and, per
   the exception above, the least shareable. Worth designing early.
- **GUI-5 — What the first usable version contains** — the smallest set of screens that
   makes the app worth opening.
- **GUI-6 — Whether shape is enough on its own**, or whether a field also needs a
   layout hint for its size. A free-text `remarks` is bulky but not unimportant; a
   rating is tiny but not trivial. How much room a field needs is independent of how
   prominent it is.
- **GUI-10 — Which of an item's own fields appear in a selector row**, and whether
   that is fixed per type or chosen by the user.
- **GUI-11 — Whether a "show everything" preference overrides collapsing**, for people
   who would rather see a wall of fields than open groups one at a time.
- **GUI-12 — Whether grouped detail is editable on a phone** or read-only there.
- **GUI-8 — How an unusable value is surfaced.** The data layer's half is settled: a
  read gives back usable, absent or unusable, and an unusable value carries which field,
  which value and what was expected — `DATA-50` and the derived-value outcomes in
  [../../data/doc.md](../../data/doc.md). What is open is entirely presentational. Both look like an
  empty cell. The interface has to make the difference visible and lead to the field at
  fault, without decorating every incomplete item as though it were broken.
- **GUI-7 — Which ancestry to show for a place.** A region can sit inside several
  larger ones, so there are several paths from a dive site up to the top of the world,
  and a caption such as "Zeelandbrug, Netherlands, Europe" needs exactly one of them.
  Either a rule picks the path, or something marks a parent as the primary one.
  Relocated from `DATA-18`.

## Settled

Kept with their identifiers so earlier discussion still resolves. `GUI-1` and `GUI-2`
are not among them: they were never questions, only the priority list above, mislabelled
once and corrected. The numbers stay unused rather than being given to something else.

- **GUI-9 — Whether a statistic says what it left out.** *Settled:* a statistic is
  shown with the items behind it, both the number used and the number there were —
  "total time underwater: 210 hours (248/253 dives)". The reader sees the sample and
  anything excluded from it in one place, without every exclusion being itemised.
- **GUI-13 — The tab switcher on a phone.** *Settled:* it must be accessible, not
  permanently visible. Eight tabs do not fit across the foot of a phone, and requiring
  them to would force the same compromise on the desktop, where there is ample room.
- **GUI-14 — Tabs holding more than one kind of item.** *Settled:* the shape of a
  selector is decided per tab, not once for all of them. Dive, community and location
  each answer it their own way.
- **GUI-15 — Whether the manuals tab is the same pattern.** *Settled:* it is. The
  selector lists chapters and the item view shows the text of one.
- **GUI-16 — What the item view shows for a given item.** *Settled:* the interface
  decides, per item type. The data layer says which fields exist; it has no say in
  which of them are shown, or how.
