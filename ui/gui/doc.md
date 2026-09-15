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

Accessible, not necessarily visible. Six labels fit comfortably across the top of a
desktop window and will not fit across the foot of a phone, so the phone is free to keep
the switcher one gesture away rather than permanently on screen.

There are six:

- **Home** — a greeting, what the application can be asked to do to a logbook as a whole,
  and everything counted. `GUI-30`.
- **Dive** — dives and dive trips.
- **Gear** — equipment.
- **Community** — people, operators and certifications.
- **Location** — regions, dive sites and wrecks.
- **Manuals** — the documentation, read inside the application.

**The application opens on Home**, which is first in the list and is what a user arrives at
rather than what they last left. A first screen that says how much diving is in here and
offers to open another logbook is a truer start than a list of dives pretending to be the
point.

**Home holds the statistics** rather than a tab of their own. A few figures worth
seeing without asking and every figure there is are the same subject read at two
depths, and a tab a user visits to see one number beside a tab that greets them was
two doors onto one room. What is owed by this is a Home that goes somewhere: the
figures shown without asking have to lead to the rest rather than being all there is.

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

### What each selector is

`GUI-14` settled that the shape of a selector is decided per tab rather than once for all of
them. These are those decisions. Three of the six are not a list at all, which is why the
question had to be asked per tab.

- **Dive** — a table of four columns: the trip, the dive's own number, the date, and the site,
  divided by year, every year but the last folded. The trip column has one cell per trip,
  spanning the consecutive dives on it within a year with the trip's name in the middle of
  the cell, and a dive on no trip stands alone. Which
  column is clicked decides what is selected: the trip cell selects the trip, anywhere else
  selects the dive; with control held a click adds a dive to the selection or takes it out, with
  shift held it selects every dive between the one last chosen and it, and a click on a year's
  divider selects the year's dives together. The tab opens on the last dive.
- **Gear** — a tree of categories and the kinds within them, and beside it the items in
  whichever branch is chosen.
- **Community** — a subtab per type: people, operators, certifications. The user, whoever the
  logbook names as its own, is marked among the people and is what the tab opens on.
- **Location** — a tree of regions, and beside it the dive sites in whichever region is chosen
  or in any region inside it, with the wrecks at those sites in the same list and marked apart
  from them. A region and a site are chosen at once, and the item view shows a map of the
  region above the two of them. `GUI-25`. A box, *Hide unused*, on by default, takes out what
  no dive touches. `GUI-26`.
- **Manuals** — a tree of two levels: the chapters, and the sections of each. A chapter is
  shown whole, and choosing a section scrolls to its place in it. `GUI-15`.
**Home has no selector.** It is not a collection, which is why the pattern above says *most*
tabs rather than all of them.

Four things follow that the shapes above do not answer, and each is a way for an item type to
become unreachable rather than a matter of taste. All four are settled: `GUI-19` to `GUI-22`.

### Selecting more than one

A selector selects one item or several, and the item view answers both.

**Several items are shown as their statistics.** Field by field, what a set of items has to say
about that field: a range, an average, how many of them answered it. So the view is the same
view — the same fields in the same order — reading a set instead of one, and a user who knows
where a dive's maximum depth sits knows where twenty dives' deepest sits.

**A trip is both.** Selecting one shows the trip's own fields and, beside them, the statistics of
the dives on it. That is what a trip is: an item with data of its own, and a set of dives.

This is the second place statistics appear, and the two answer different questions. Home says
*how much diving have I done*, over everything, without being asked. A selection says *what
about these*, and is asked by choosing them. Neither is a tab called Statistics, and that is why
there is no longer one.

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

**A window that reads a logbook, and nothing more.** The tabs across the top, a selector
listing what a tab holds by type, and an item view showing the fields of the one chosen. On a
desktop the plain fields flow into two columns, an owned item is set into a box of its own and
shown in full, and a keyed one is such a box with a tab per entry. The manual is read in its
tab, a chapter at a time. Two tabs hold nothing yet and say what they will hold rather than
showing an empty box.

**An item's fields can be edited.** A pencil on the item card turns it over into the edit form,
which `GUI-29` describes, and an entry of a keyed collection can be taken out or added there;
nothing else writes yet. No item is added or deleted from here, no owned item is added, and
there is no journal to undo with.

It opens as `yemoja gui [<logbook folder>]`, beside the terminal front end, and unlike that one
it can be run from the build, a window needing no console. The folder is optional: with none it
opens on the welcome and offers Home and Manuals. `GUI-30`.

**The look is the platform's, in the application's colours.** Material as it comes, light or
dark as the system is set, with one thing of ours in it: the colours are a marine palette rather
than the default violet, and nothing else is ours yet. `GUI-3`. Within that, the tabs are a row
across the top, each with the platform's glyph for its subject beside its name, the dive table
is headed and its trip cell is drawn as one cell down the run it spans, whatever is chosen is
tinted rather than emboldened, a tree unfolds branch by branch, and the item view sits on a card
with its labels ranged against its values.

Three things it does badly, each of them an open question above rather than a bug:

- **A selector row outside the dive table is the item's title and nothing else.** `GUI-10` is
  which fields a row shows, and the dive table answers it for dives alone.
- **A key reference reads as its key, read** — *Perdix 2* for `*perdix_2`, *Tank 1* for
  `*tank_1` — and leads nowhere. An ordinary reference reads as the name of what it points at
  and is a link to it, which is the *an id is never shown* rule holding where it was broken by
  the back door; a key is neither a name nor an id, and reading it the way a label is read is
  as far from the spelling as it honestly gets.
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
- **GUI-24 — What a merged trip cell does when the order changes.** The trip column spans
   consecutive dives, which is only ever true while the table is in date order and no two trips
   overlap in time. Sorted by site, or by depth, a trip's dives scatter and there is nothing to
   span. Either the merging is a property of the date ordering and goes when the order changes,
   or the table refuses to be sorted another way, or grouping survives sorting within a group.
- **GUI-5 — What the first usable version contains** — the smallest set of screens that
   makes the app worth opening.
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
  permanently visible. Seven tabs do not fit across the foot of a phone, and requiring
  them to would force the same compromise on the desktop, where there is ample room.
- **GUI-3 — How much visual identity to define up front.** *Settled in part:* **the colours and
  the icon, and nothing else yet.** The platform's scheme is violet and a logbook of the sea is
  not, so the same roles are filled from a marine hue, navy where the default is purple and a
  cool grey where it is a warm one, as quiet as the default was. Type, shapes, glyphs and every
  component stay the platform's. Two constraints hold it in place: every colour on a screen is a
  *role* from the scheme rather than a value, so the palette lives in one file; and every
  pairing of a colour with the text on it is tested for contrast, because a hand-filled scheme
  can pair a dark on a dark and nothing else would say so. The icon is a white fish above three
  waves on the palette's navy — Yemọja is the mother whose children are fish — drawn as a
  vector, and given to the window at whatever size the platform asks for. What is left of the
  question is whether the application ever wants type or glyphs of its own, which nothing yet
  asks for.
- **GUI-25 — What a region shows.** *Settled:* **a map, then the region, then what was chosen at
  it.** Choosing a region and choosing a site are two acts and stay two selections: the site is
  read against where it is, so the item view holds both, one below the other, and the map above
  them is the region's frame with a dot per site and the chosen one marked and named. A region
  lists the sites anywhere inside it, since a reader who opens Europe is asking what there is to
  dive there. The dots are drawn in the text colour and the mark in the error colour, which are
  black and red in daylight and stay visible in the dark.

  **The map is drawn from Natural Earth**, bundled as a library at three scales: land, lakes,
  borders, rivers and cities. A frame is drawn from the coarsest scale that still looks like a
  map at its width, so the world is a few thousand points and a bay is its real coastline, and
  the cities named narrow the same way, from capitals on the world to every place the source
  knows in a bay. The geometry is read once, off the interface's thread, and a region drawn
  before it arrives shows its sites on an empty frame until it does.
- **GUI-23 — Which statistic each kind of field gets.** *Settled:* **one per kind, and a field
  nobody answered is left out.** A number is its range and its average, written with the sign ⌀
  rather than the word, unless it names rather than measures, as a dive's number does, when the
  range is all; a date or a time its range; a yes-or-no how many yeses of how many; and anything
  named — a site, a buddy, a word from a list — every different one and how many that is, each
  of them, each leading to its item. Where not every item answered, how many did is added. The
  shape is the item view's own: the same fields in the same order, two columns, titled by how
  many items and headed by the items themselves, each a link, which is why their names are no
  statistic of their own. It is shown for several dives chosen in the table, and under a trip's
  own fields for the dives on it, which is what a trip is — the same box either way, however
  the dives came to be chosen. A field a set has nothing to say about is left out rather than
  shown empty, since a set of twenty dives has no remarks and saying so is noise.
- **GUI-33 — How a logbook is imported from the window.** *Settled:* **one path, the dives
   reviewed a line apiece, and everything else counted and taken in with them.**

   The logic layer already had all of it: `importFrom` takes a path, reads a folder as another
   Yemoja logbook and a file as a UDDF document, and stages what it finds beside the logbook as
   a logbook of its own. What was missing was the window.

   **One dialog, folders and files both.** What is there says how it is read, so asking *which
   kind* first would be asking a reader something they answered by knowing what they were
   pointing at. The picker is the platform's, like the two that open and make a logbook, and the
   deed is greyed where a platform has none.

   **The dives are reviewed with the machinery a download already has**, `GUI-31`: a line
   apiece, what each appears to be a second copy of, and a button to put it together with that
   one or take it as a dive of its own. The question is the same question — *which of these am I
   already holding* — and the answer machinery does not care what put them there. It asks only
   where the source carried no ids of its own, which today means UDDF.

   **Everything else is counted, not listed.** A download makes dives and sites and nothing
   else, `LOGIC-20`, while another logbook can bring every type there is. Those arrive matched by
   the id they came with or as new, so there is nothing to ask and nothing to choose, and a line
   saying *2 people, 3 dive sites (1 already held)* answers the question a reader has. That
   count is what `reconciliation.md` asks for and says is not built.

   A type that takes no `s` is not given one: people, and gear, which is what one piece of it is
   called and what any number of it is called.

   **A dive arriving with a number of its own keeps it.** A download hands numbers out because a
   computer has none, and doing that to an imported dive would renumber a logbook that already
   knew what its dives were called. The same goes for the recording it is worked from. So both
   are written only where the arriving dive says nothing.

   **The rest goes in when the dives are decided.** Taking a dive in leaves it staged no longer,
   so what is left in the folder is what has not been decided; once the dives are gone the rest
   is applied in one call. That is two changesets rather than the one `RECON-1` wants, which the
   download review already had and which the journal will have to settle when it is built.

   Open: nothing lists what arrived of the other types item by item, so an import bringing a
   person under an id already taken by a different person is taken in on the model's rule rather
   than the reader's answer. `RECON-6` settles what that rule is.
- **GUI-32 — What else the home plot can draw.** *Settled:* **a gathering, chosen beside the
   two axes: one dot per dive, how many, a total, an average, the two extremes, or a running
   total.**

   `GUI-30` drew a dot per dive and nothing else. That answers *how do these two things go
   together* and refuses every question of the form *how much diving did I do*: three hundred
   dots say less about a diver's year than four bars would.

   **The gathering is a third box beside the two axes**, and it decides what the others mean.
   Counting needs nothing up the side, so that box goes and the axis is titled *Dives*.
   Everything else reads the variable up the side and gathers the dives in each bar: their sum,
   their mean, the greatest and the least.

   **It opens on how many dives a month**, which is the figure a diver looks for first and the
   only one that needs no variable chosen to be worth drawing. The axes keep `GUI-30`'s opening
   pair underneath, so a reader who switches to a total is already plotting against the date.

   **A date is cut by the calendar.** A month is a month and no fraction of a year is one:
   cutting at a twelfth of an average year begins March two days late and files the first
   weekend of it under February. So a variable that reads a date carries the day as well as the
   number it plots, and the calendar cuts on that. Everything else is cut at a round width —
   one, two or five times a power of ten — chosen over the range the dives actually cover.

   **How wide is chosen to fit and can be overridden.** The narrowest cut drawing no more than
   a hundred and twenty bars, which is ten years of months; past that the fit steps up to
   quarters and a fourth box steps it back down. Narrowest rather than roundest, because the
   detailed question is the one asked first and the only reason to widen is that the bars stop
   being legible. The width is forgotten when the bottom axis changes, a cut chosen for a
   calendar meaning nothing on an axis of metres.

   **A bar nothing fell in is nought for a count and a total, and absent for the rest.** No
   dives in February is none made and no hours spent, which are figures; it is not an average
   depth of nought, which is a claim about diving that did not happen. That is `DATA-50`'s
   distinction between absent and zero, applied to a bar.

   **A running total is not cut into bars at all.** It is a line through the dives in the order
   they were made, each point carrying what the logbook came to once that dive was in it, so it
   only climbs and its last point is the figure the greeting gives.

   **Bars stand on nought whatever they reach**, which the dots do not. A bar read against an
   axis beginning elsewhere lies about how big it is, and being read by size is the whole of
   what a bar is for.

   Open: clicking a bar to see the dives in it, which `GUI-30` already owes for a dot.
- **GUI-31 — How a dive computer is read from the window.** *Settled:* **the screens drive it
  and the platform only asks; every dive that comes across is taken in together or left staged.**

  Everything a download needs is on the universe — what is within reach, and the reading of one
  — so the flow is this layer's rather than a platform's. What a platform adds is one thing:
  putting a question and *waiting* for the answer. A device that guards itself asks for a code
  part way through, from whatever thread the download runs on, and the answer has to come back
  to that thread before the next byte is read. `LOGIC-24`.

  **Where it has got to is a thing in its own right.** A download is the one act here that takes
  minutes, so it has stages — looking, choosing between what was found, reading, done — and the
  section under the greeting says which. The window stays alive throughout, both the looking and
  the reading happening off the drawing thread, which is what the terminal front end says it
  owes and does not yet have.

  **What arrives is read before it is taken in**, a dive to a line: when it began, how long it
  ran, how deep it went, and what recorded it. Nothing lands until it is asked for.

  **A dive that overlaps one already held is that dive**, seen by another computer. Nobody is on
  two dives at once, so two recordings that overlap in time are two recordings of one dive,
  which is `Import`'s own rule and wanted only somewhere to be asked. The row says which dive it
  appears to be and offers to put them together; the dive then carries both recordings, keyed by
  the computer that made each. Put together is offered, never taken: over-eagerness costs a
  keystroke and the other button is always there.

  **A dive of its own is offered under the number it would take**, one past the highest the
  logbook holds. Nothing else knows what a diver counts and the one certain thing is that this
  dive follows the last, so it is a proposal like the rest of a downloaded dive. It is also
  given the recording it arrived with as the one it is worked from, there being only one to
  choose; a second computer's comes later and does not displace it. Merged onto a dive already
  held it keeps that dive's number and its primary, and is given neither.

  **A dive glued from more than one recording says so**, since a computer that ended a dive part
  way through is a thing a reader should know happened rather than discover. `LOGIC-25` does the
  gluing before any of it is a dive; this only reports it.

  *All as proposed* does every row as its own button would, for a reader who has read the list
  and agrees. *Leave them* keeps them staged, where they are found again and where the terminal
  front end can review them. Taking in stops at the first refusal and leaves the rest: what is in
  the folder is what has not been decided, `RECON-1`.

  **Where a dive was made is asked, not guessed.** A device says where it was and nothing more,
  and a fix is not a site: a site has a name, a water type and its regions, none of which is in
  a pair of coordinates. So the row shows the position and offers the sites already held that
  are nearest it, each with its distance, in order. No radius is chosen, because one would be a
  number nobody can pick well and a handful in order lets the reader judge what a threshold
  would have judged for them. Beside them, a box to name a new site, and *nowhere* for a dive
  whose place is not worth an item. `LOGIC-18`.

  A fix nobody claims is dropped rather than kept: it was a question, not an item. One that is
  named becomes a site and lands with the dive that asked about it, and two fixes within five
  hundred metres are one question rather than two, so a week on one reef proposes one site.

  Open: undoing a gluing, which would mean keeping the stretches apart until asked.
- **GUI-30 — What the home screen is.** *Settled:* **a greeting, what can be done to a
  logbook as a whole, and one plot the reader chooses both axes of.**

  Three things, in that order, because they answer what a reader has not asked yet.

  **The greeting is two lines.** Who is being greeted, large; and beneath it, in the size the
  rest of the application reads at, what there is to say to them. Splitting it is what lets the
  second line change without the first one growing: the name is the constant and the sentence
  under it is not.

  Who is greeted has three answers. Whoever the logbook names as its own, by name. A reader
  whose logbook names nobody, as *diver*, and told that naming one of its people as themselves
  is what fixes that. And a reader with no logbook open at all, welcomed, and pointed at the
  two buttons that would get them one.

  **The window opens without a logbook**, which is what makes the third reachable: the folder
  the command takes is optional, and named with none the window opens on the welcome. A folder
  that is named and will not read is still an error, that being what was asked for. Only the
  tabs that are not about what a logbook holds are offered then, which is Home and Manuals; a
  tab is about a logbook exactly when it lists item types, so the model answers that rather
  than a list kept beside it.

  **Making a logbook and opening one both work.** Each asks for a folder and puts the window
  on what comes back, so the welcome leads somewhere. What a new logbook is made of is the
  logic layer's, `LOGIC-26` in [../../logic/doc.md](../../logic/doc.md); what this layer adds
  is the asking, and the window swapping the logbook it shows. A folder that will not read, or
  will not be made into a logbook, is said in a box over the window rather than on the screen
  behind it: it answers something the reader just asked for, and the window under it has not
  changed. Reading a dive computer works too, and is `GUI-31`; so does importing a logbook,
  which is `GUI-33`. All four of the deeds are built.

  **A folder holding no manifest opens**, as a logbook that declares nothing, which is what the
  command line does with the same folder and what the model calls one. Refusing it would be
  this layer disagreeing with the layer under it about what a logbook is, and a greeting saying
  *no logged dives* is a truer answer than a box saying no.

  **What a tab was left on belongs to the logbook that was open.** `GUI-27` keeps a tab's place
  when it is left and returned to; another logbook does not hold the item it was left on, so
  the whole of that memory is made afresh when the logbook changes.

  Told what, where there is a logbook and a reader: how many dives, how many places they were
  made, and how long they ran altogether. The places are the ones dived rather than the ones
  known, a site nobody has been to saying nothing about the diving that was done. A length
  runs to hundreds of hours and is written in hours and minutes rather than in the clock a
  dive's own times use, `306:12` being a number nobody reads as a length.

  **The day is remarked on where it is worth remarking on**, woven into the first line rather
  than set beside it: *Hello Anna Devries, and happy World Oceans Day.* The reader's own
  birthday first, then the new year, then a day the sea has been given, then the turn of a
  season. Most days are plain and the line is just hello, which is what keeps the rest worth
  reading. Two of the sea's days are the United Nations'; the others are observances divers
  keep among themselves, and the one date among them that is a plain fact is Jacques
  Cousteau's.

  **A remark leads out to a page about the day**, styled and opened the way the manual's own
  outward links are. A day with an article of its own leads to it and a day without leads to
  what it is about, a shark's day to the sharks. Two remarks lead nowhere: a reader's birthday
  is theirs rather than an article, and a welcome is not a day at all.

  Seasons are the meteorological ones, beginning on the first of a month, rather than the
  astronomical ones, whose equinox wanders over three days and would need an almanac. Which
  way round they run is the mean latitude of the logbook's sites, so that a northern diver's
  week in the tropics does not move their winter, and a logbook whose sites say nothing is
  taken to be northern.

  **What day it is comes from the platform**, beside the manual and the map. Nothing below the
  screens has a clock, and the alternative was a dependency for a date the window already
  knows.

  **What the application does to a logbook as a whole is not a subject**, so it is not a tab.
  Opening another logbook, making one, importing one and downloading from a computer are four
  things done *to* a logbook rather than things in one, and they belong where a reader is
  before they have chosen anything. That is what removed the System tab: it held nothing, and
  what it was owed turned out to belong here. The four are named whether or not a platform can
  do any of them yet, greyed where it cannot, for the same reason the tab list is the whole of
  what the application offers rather than the part that happens to be built.

  **The statistics are one plot rather than a page of figures.** A dive answers for a dozen
  numbers and a date, and every pair of them is a question somebody might have; a screen that
  chose for the reader would answer one of them and hide the rest. So both axes are chosen,
  each from every number a dive answers for, its own and those of the items it owns, and how
  deep against when is the pair they hold, that being the shape of a diver's own diving. What
  is drawn with them is a third choice, `GUI-32`.

  A time reads in minutes, an axis marked every 600 being unreadable, and a date reads as a
  year and a fraction of one, so the marks fall on years and the axis needs no calendar. A
  dive missing either reading is left out rather than drawn at nought, which would be read as
  a reading. Clicking a dot does not yet lead to its dive, and should.
- **GUI-4 — Dive profile rendering.** *Settled in part:* **one graph per recording, under
  its tab.** Depth over time up the left, the recording drawn thick and filled underneath so a
  dive reads as water, its deco stops stepped across it and the water over them shaded red,
  that being where the diver may not ascend to; and up the right one other thing the
  computer wrote, chosen from a box of what it wrote — the temperature, a cylinder's pressure
  named as its gas source is, the no-decompression limit, the CNS. The limit is titled *NDL*,
  as a diver writes it and as the CNS and the OTU beside it already are. It is plotted capped
  at 99 minutes and without the zeros a computer reads before it has calculated: computers
  mark *no limit* with a number, 99 minutes on one and 598 on another, and plotted as written
  the marker is the whole axis. It is broken where a stop stood: a computer shows either how
  much longer a diver may stay or how deep they may not come above, never both, and writes
  nothing for the other, so a line drawn across that stretch climbs from nothing back to the
  limit and is a reading nobody took. On the depth line itself, every gas switch is a dot named for
  the source switched to and every alarm a triangle named as the computer gave it, so the
  events of a dive sit where they happened. Each recording of a dive has its own
  graph and shows only its own data, which is what a tab is for. Marks fall on values a reader
  would choose, steps of one, two or five, and the unit sits in each axis's title. An axis
  leaves room beyond the readings it covers, since a reading that hardly changes drawn on the
  plot's edge reads as a border rather than as a line: a dive in water of six degrees
  throughout would otherwise draw its temperature as a second axis under the graph. What is
  worked out is worked out without a screen and tested; the drawing is paths built once per
  size. Open: what a phone shows of this.
- **GUI-6 — Whether shape is enough on its own.** *Settled in part:* **a field may be marked
  as not for reading, and the model marks it.** A type says which of its fields are kept for
  the machinery — a download's bookmark, a pairing key, which recording is worked from — and
  which are solely a source for others that say it better, a recording's own start beside the
  dive's; `DATA-115`. An item view leaves both out and the edit form shows them, since they
  are written and kept, not read. The manual already said of each that it is not for reading,
  which is why the mark is the model's rather than a table here. Still open: whether a field
  needs a hint for how much room it takes.
- **GUI-29 — How a field is edited.** *Settled:* **the item view turned over.** A pencil on
  the card turns it into the edit form: the same fields in the same places, two columns and
  the insets and their tabs, with *Save* and *Cancel* on the title line, so a reader who knows
  where a field sits when reading knows where it sits when editing. The title line, with its
  pencil or its Save and Cancel, stays put while the fields scroll under it, so what is being
  looked at and the way out of it are never scrolled away. Cancel puts the card back
  as it was; Save hands every field changed to the model as one change, and a refusal comes
  back to the field it was about.

  **One widget per kind of field**, chosen from its description: a text field, taller for
  multiline text; a number field with its unit after it, a time as `m:ss`; a date as text; a
  yes-or-no as a three-state box, the third being *not said*; a fixed set as a drop-down, and a
  suggested set as a text field with the suggestions offered from an arrow beside it and not
  enforced; a rating as five stars to click; a reference as a drop-down over the type it points
  at that typing narrows, the item taken written as its id and anything typed and not taken
  standing as a plain name where the field allows one; a key reference as a drop-down over the
  collection's keys; a list as one such widget per entry, with add and take out. A series is not
  edited: the graph is its place.

  **What is worked out is shown, not edited.** A derived field is read-only and greyed. One the
  model works out unless told otherwise shows what it worked out with an *override* beside it —
  not *correct*, which beside a number reads as saying the number is; overridden, it is edited
  like any other, and *revert* clears the override so the worked-out value returns. **Validation
  stays in the model**: every drafted value is judged by the field's own description as it is
  typed and the reason for a refusal sits under the field in red.

  **Every value travels as the text a file holds it as**, which is what the model reads and what
  the terminal front end already writes, so the two front ends cannot disagree about what `12.3`
  or `@anna` means. **An entry of a keyed collection is taken out or added at once**, from an ×
  on its tab and a + after the tabs, rather than on Save: either is the collection rewritten
  whole, which makes every entry a new object, so a draft on one would be a draft on nothing.
  Neither asks, as the terminal front end has it: an entry is put back by typing it. A new entry
  is keyed as its type proposes for one holding nothing, the first free taken. Open: adding and
  deleting items, adding an owned item, and the phone's form. The text fields are the height of
  their text rather than the platform's fifty-six pixels: a form of twenty fields in tall boxes
  is one nobody scrolls to the end of.
- **GUI-28 — What a reference is on a screen.** *Settled:* **a link.** Wherever a field names
  another item, the name is clickable and opens that item where it lives: the tab holding its
  type, on the right subtab, with the item chosen — or, for a region, with the map on it — and
  the list scrolled to it where the tab has a list, the year holding a dive unfolded first,
  since a line in a folded year is not there to scroll to. A site sets the region as well, since the
  map would otherwise show wherever Location was left: a site names several regions and none
  of them is *the* one, so the map goes to the most specific, which is the one with the
  smallest frame — Egypt rather than the Red Sea, Zeeland rather than the Netherlands. A wreck
  goes through the first site it lies at. That holds in every item view, so a site's
  dives lead to the dives and each dive's site leads back; the back-links the model works out
  are what make the second direction exist. The dive table's site column is not a link, since
  `GUI-19` gave a click there to the dive.
- **GUI-27 — What a tab keeps when it is left.** *Settled:* **everything.** The item chosen,
  the region, the box, which branches are unfolded, which subtab, where each list is scrolled
  to: a tab returned to is where it was left, because switching tabs to look something up and
  coming back to find the dive gone is the kind of small loss that makes an application feel
  like it is working against you. What each tab keeps lives above the screens, which come and
  go with the tab, and is one object per tab for as long as the application runs. The one
  thing not kept is a transient: where a section click is scrolling to, which is over by the
  time anyone could leave.
- **GUI-26 — What Location shows of an atlas.** *Settled:* **what the dives touch, unless
  asked otherwise.** The atlas holds the world, and a logbook touches a few corners of it; a
  tree of every region to reach three of them is a tree of noise. With *Hide unused* on, which
  it is by default, a site no dive names is left out, a region with no such site at it or
  inside it is left out, and a region left with one child and no site of its own is cut out
  so the child takes its place — Europe with only the Netherlands left in it is not a level
  worth a click. Off, the whole atlas is there, which is where a new site is filed.
- **GUI-22 — Which parent a region tree uses.** *Settled:* **all of them.** A region has
  `parents`, plural, so regions form a graph rather than a tree, and a region with two parents
  appears under both. Nothing is hidden and no rule has to be invented for which path is the
  real one, at the cost of a region being reachable by more than one route — which is what
  being in two larger places means rather than a flaw in the display.

  **This does not answer `GUI-7`**, and I said it would. A tree can show every path; a caption
  reading *Zeelandbrug, Netherlands, Europe* has room for one and still needs a rule. The two
  looked like one question and are not.

  **The tree must survive a cycle**, which `LOGIC-8` says nothing prevents and which would
  otherwise expand for ever. A branch that reaches a region already above it in the same path
  stops there, and says so rather than ending silently: a region inside itself is a mistake
  worth reporting, which is the answer `LOGIC-8` asks each walk to give for itself.
- **GUI-21 — How gear is narrowed down.** *Settled:* **a tree, not subtabs.** Categories at
  the top and the kinds within them below — `kind` is what an item is more finely than its
  category, a wing within BCD, gloves within suit — with the items in the chosen branch listed
  beside it.

  A tree answers what subtabs could not. Both fields take any word, so the branches come from
  the logbook rather than from a list here, and a tree grows a branch where a row of tabs would
  have to grow a tab. Gear with no category sits at the top of the tree rather than in a bucket
  called *other*, which is what it is: not filed yet.

  It is also the shape `Location` uses, a tree beside a list, so the application has one way of
  narrowing something down that is used twice rather than two ways used once each.
- **GUI-20 — How a wreck is reached.** *Settled:* **in the site list, marked apart from the
  sites.** A wreck is a ship rather than a place: it has no region and no position of its own,
  and a site names the wrecks that lie at it. So the list under a region is the sites in that
  region and the wrecks at those sites, which is one place to look for *what is there to dive*
  and lets a wreck be opened without first opening the site it lies at.

  Two things follow from a wreck having no place of its own. One at no site appears under no
  region, and one named by two sites appears under both, which is right where the sites are two
  moorings on one hull and misleading where they are not.
- **GUI-19 — How a dive trip is reached.** *Settled:* **by the column that names it.** The
  trip column holds one cell per trip, spanning the consecutive dives on it, and clicking that
  cell selects the trip where clicking anywhere else in the row selects the dive. So a trip is
  reached from any dive on it and needs no list of its own, and the column earns its width
  twice: it says which trip a dive was on, and it is the way in.
- **GUI-14 — Tabs holding more than one kind of item.** *Settled:* the shape of a
  selector is decided per tab, not once for all of them. Dive, community and location
  each answer it their own way.
- **GUI-15 — Whether the manuals tab is the same pattern.** *Settled:* it is. The
  selector is a tree of chapters and the sections in them, and the item view shows a chapter
  whole, scrolled to the section chosen. Whole rather than a section at a time, because a
  chapter is written to be read through and a section is a place in it, not a page.

  The chapters are bundled with the application and read from it, in the subset of markdown
  the manual's own conventions allow and no more. That subset is small enough to read here
  rather than through a library, and reading it here is what lets a link to another chapter be
  followed inside the tab: the manual is closed under its own links, and a test holds it to
  that.
- **GUI-16 — What the item view shows for a given item.** *Settled:* the interface
  decides, per item type. The data layer says which fields exist; it has no say in
  which of them are shown, or how. The first uses of that: a region's `children` are not
  shown, since the tree beside the item is exactly that list, and a trip's `dives` are not,
  since the box of their statistics under it is; a dive's primary recording is marked with a
  star on its tab and put first among them, being the one every figure on the dive comes from,
  while what is stored keeps the order it was written in — putting one first on a screen must
  not rewrite a file; and a rating out of ten reads as five stars, two points to a star and an
  odd rating ending in a half, and the number is not shown.

  **A word from a vocabulary is a word rather than a name.** A file writes one in lower case
  with an underscore where a reader puts a space, and a screen reads back what it says, so
  `back_mounted` is *Back mounted*. Only a field with a set of words to draw on has words in
  it, so free text is left exactly as it was typed and a gas is `EAN32` rather than `Ean32`.

  **How a desktop lays an item out.** The plain fields flow into two columns, in the type's
  order. An owned item is set into a box of its own, titled, and shown in full the same way,
  boxes nesting where an owned item owns one. A keyed owned item is such a box with a tab per
  entry, the tabs small buttons on the title's own line, each as wide as its name, the first
  open; a tab is called
  by the entry's name where it has one, else by what the first reference on it points at — a
  recording by the computer that made it, a course by its certification — and by its key only
  where nothing on it says anything. A series is not laid out at all: the graph is where it is read.

  **What a number reads as.** A file keeps three decimals of a metre so nothing measured is
  lost; a reader wants one. A depth, a temperature, a mass and a volume read to one decimal,
  a pressure and a density to none, and an angle as the file writes it, a coordinate being
  nothing to round. A time is seconds in the model and reads as minutes and seconds, `61:16`,
  however long, since a dive is quoted in minutes. Every other number carries its unit after
  it, once after a range and once after an average.
