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

Accessible, not necessarily visible. Seven labels fit comfortably across the top of a
desktop window and will not fit across the foot of a phone, so the phone is free to keep
the switcher one gesture away rather than permanently on screen.

There are seven:

- **Home** — a greeting, anything needing attention, and everything counted and
  summarised.
- **Dive** — dives and dive trips.
- **Gear** — equipment.
- **Community** — people, operators and certifications.
- **Location** — regions, dive sites and wrecks.
- **System** — settings, syncing and the rest of the machinery.
- **Manuals** — the documentation, read inside the application.

**The application opens on Home**, which is first in the list and is what a user arrives at
rather than what they last left. That holds while Home is still a placeholder: a first screen
that says what it will hold is a truer start than a list of dives pretending to be the point.

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

- **Dive** — a table of four columns: the trip, the dive's own number, the date, and the site.
  The trip column has one cell per trip, spanning the consecutive dives on it, and a dive on no
  trip stands alone. Which column is clicked decides what is selected: the trip cell selects the
  trip, anywhere else selects the dive.
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
- **System** — not decided.

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

**A window that reads a logbook, and nothing more.** The tabs across the top, a selector listing
what a tab holds by type, and an item view showing the fields of the one chosen. Owned items are
counted rather than spelt out, which is the progressive disclosure above meeting its first real
item. The manual is read in its tab, a chapter at a time. Two tabs hold nothing yet and say what
they will hold rather than showing an empty box.

**Nothing changes anything.** There is no edit view, no action and no key that writes. That is
the scope rather than an oversight: a first version proves the toolkit, the layout and the door
into the logic layer, and each of those is easier to judge when nothing can go wrong.

It opens as `yemoja gui <logbook folder>`, beside the terminal front end, and unlike that one it
can be run from the build, a window needing no console.

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
- **A key reference reads as it is written**, `*perdix_2` rather than the recording it names,
  and leads nowhere. An ordinary reference reads as the name of what it points at and is a link
  to it, which is the *an id is never shown* rule holding where it was broken by the back door;
  a key reference still breaks it.
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
- **GUI-23 — Which statistic each kind of field gets.** A set of items shown field by field
   needs one answer per kind, and they are not the same answer: a depth has a range and an
   average, a date has a range and no average, a site has neither and has *how many distinct*, a
   boolean has *how many true*. Some have none worth showing. What is settled is that the shape
   is the item view's own; what is open is the table of kind to statistic, and whether a field
   with nothing worth saying is shown empty or left out.
- **GUI-24 — What a merged trip cell does when the order changes.** The trip column spans
   consecutive dives, which is only ever true while the table is in date order and no two trips
   overlap in time. Sorted by site, or by depth, a trip's dives scatter and there is nothing to
   span. Either the merging is a property of the date ordering and goes when the order changes,
   or the table refuses to be sorted another way, or grouping survives sorting within a group.
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
- **GUI-4 — Dive profile rendering.** *Settled in part:* **a first drawing, on the item view.**
  Depth over time is the graph: the primary recording drawn thick and filled underneath, so a
  dive reads as water, and any other recording of the same dive laid over it thinly, with the
  deco stops stepped across it. Everything else the primary recording holds — temperature,
  cylinder pressures, no-deco time, CNS — is a small graph of its own under it, on the same
  axis of minutes. Marks fall on values a reader would choose, steps of one, two or five, and
  the unit sits in the title. What is worked out is worked out without a screen and tested;
  the drawing is paths built once per size. Open: what a phone shows of this, and whether the
  small graphs share one axis or each get their own.
- **GUI-28 — What a reference is on a screen.** *Settled:* **a link.** Wherever a field names
  another item, the name is clickable and opens that item where it lives: the tab holding its
  type, on the right subtab, with the item chosen — or, for a region, with the map on it — and
  the list scrolled to it where the tab has a list. A site sets the region as well, since the
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
  shown, since the tree beside the item is exactly that list; and a rating out of ten reads
  as five stars, two points to a star and an odd rating ending in a half, and the number
  is not shown.
