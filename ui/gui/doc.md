# UI layer — GUI

The application most people will use: polished, and aimed at a broad audience
rather than at users who are comfortable with a terminal.

One application, two form factors: [desktop](desktop/doc.md) and
[phone](phone/doc.md). They share their structure and differ only in how that
structure is placed on screen.

**What is decided here and what is decided there.** A `GUI` decision says what the window shows
and how it behaves, whatever the screen. A `DESK` or a `PHONE` decision says where that goes on
one: the columns, the boxes, the pointer, the sizes. Where a decision has both halves, each is
written where it belongs and names the other.

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

- **Home** — a greeting, what the application can be asked to do to a logbook as a whole,
  and everything counted. `GUI-30`.
- **Dives** — dives and dive trips.
- **Gear** — equipment.
- **Community** — people, operators and certifications.
- **Locations** — regions, dive sites and wrecks.
- **Calculations** — what a diver works out on a slate, with no dive to read it from. `GUI-43`.
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
them. These are those decisions. Four of the six are not a list at all, which is why the
question had to be asked per tab.

- **Dives** — a table of four columns: the trip, the dive's own number, the date, and the site,
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
- **Locations** — a tree of regions, and beside it the dive sites in whichever region is chosen
  or in any region inside it, with the wrecks at those sites in the same list and marked apart
  from them. A region and a site are chosen at once, and the item view shows a map of the
  region above the two of them. `GUI-25`. A box, *Hide unused*, on by default, takes out what
  no dive touches. `GUI-26`.
- **Manuals** — a tree of two levels: the chapters, and the sections of each. A chapter is
  shown whole, and choosing a section scrolls to its place in it. `GUI-15`.
- **Calculations** — a list of what can be worked out, narrower than the other selectors because
  its names are a few letters long. The dive plan heads it and is chosen on the first visit.
  `GUI-43`.
**Home has no selector.** It is not a collection, which is why the pattern above says *most*
tabs rather than all of them.

Four things follow that the shapes above do not answer, and each is a way for an item type to
become unreachable rather than a matter of taste. All four are settled: `DESK-7`, `GUI-20`,
`GUI-21` and `GUI-22`.

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
tab, a chapter at a time. Every tab has a screen behind it; none is a placeholder.

**An item's fields can be edited, and an item made and deleted.** A pencil on the item card
turns it over into the edit form, which `GUI-29` describes, and an entry of a keyed collection
can be taken out or added there. A **+** beside it makes another of the same type and a bin
deletes, `GUI-35`. There is no journal to undo with.

It opens as `yemoja gui [<logbook folder>]`, beside the terminal front end, and unlike that one
it can be run from the build, a window needing no console. The folder is optional: with none it
opens on the welcome and offers Home, Calculations and Manuals. `GUI-30`.

**The look is the platform's, in the application's colours.** Material as it comes, light or
dark as the system is set, with one thing of ours in it: the colours are a marine palette rather
than the default violet, and nothing else is ours yet. `GUI-3`. Within that, the tabs are a row
across the top, each with the platform's glyph for its subject beside its name, the dive table
is headed and its trip cell is drawn as one cell down the run it spans, whatever is chosen is
tinted rather than emboldened, a tree unfolds branch by branch, and the item view sits on a card
with its labels ranged against its values — the form beneath the pencil being the same card with
boxes where the values were.

Three things it does badly, each of them an open question above rather than a bug:

- **A selector row outside the dive table is the item's title and nothing else.** `GUI-10` is
  which fields a row shows, and the dive table answers it for dives alone.
- **A key reference reads as its key, read** — *Perdix 2* for `*perdix_2`, *Tank 1* for
  `*tank_1` — and leads nowhere. An ordinary reference reads as the name of what it points at
  and is a link to it, which is the *an id is never shown* rule holding where it was broken by
  the back door; a key is neither a name nor an id, and reading it the way a label is read is
  as far from the spelling as it honestly gets.
- **An unreadable value is reddened**, which is a placeholder for `GUI-8` rather than an answer
  to it. Where a value came from is settled and is not this: `GUI-49`.

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

- **GUI-47 — What the item view does with a start and an end.** *Settled:* **the pair is one
  line, a range, labelled by what they bracket.**

  A dive holds `start_time` and `end_time`, and under each other they read as two facts a reader
  has to put together. On one line they read as the one fact they are: *Time 09:15 – 09:58*. The
  same goes for the dates of a dive, a profile, a trip and an insurance, and for a gas source's
  pressures, *Pressure 200 bar – 60 bar*.

  **Both ends, and one kind.** A pair is joined only where both are written and both are described
  the same way, because a lone date beside *Date* would not say which end it was, and a pair of
  different kinds is not a range. The gradient factors are a low and a high rather than a start and
  an end, and are left alone: neither brackets the other.

  **The edit form keeps them apart.** Two boxes are what a typist needs, and a range in one box
  would have to be parsed back into two fields. `GUI-29`.

- **GUI-45 — How the Calculations tab words what it says.** *Settled:* **the subject first, then
  what is correct.**

  A reader who gets something wrong wants to know what right looks like, not why the application
  cannot proceed. So an error says what the value should be and what it is: *GF low should not be
  higher than GF high*, *Line 2 duration should be m:ss or minutes, such as 2:13 or 25, not
  "soon"*. A value left empty is *missing* rather than wrong. The model's warnings put the time
  first and what they are about after it, then the value against its limit: *25:00 Gas 2 (EAN50):
  pO₂ 2.52 bar should be at most 1.60 bar*. What cannot be worked out says so and lists every input
  missing, for every cylinder at once, so one fix is not followed by the next complaint: *Cannot be
  calculated (missing for Gas 1: SAC, volume, start pressure)*. Explanations are short facts, *Salt
  water, sea level, rounded up to 0.1 m*, and the reasons behind them belong in the manual.

  The model's warnings are the same sentences under a recording in the Dives tab, so they read this
  way there too. An error here says *not "x"* where a field's assertion elsewhere says *but was x*;
  both give what was expected and what arrived.

- **GUI-44 — How a plan is kept.** *Settled:* **the Calculations tab's planner saves a plan, as a
  new dive or on to one already there, and the Dives tab only opens it there.**

  The planner and the form that started plans on the Dives tab wrote the same thing: a dive holding
  a profile marked `planned`, `DATA-57`. So the planner saves, and the form, its *Plan dive* button
  and a plan's *Add the ascent* are gone. One place makes a plan rather than two that would drift
  apart. A dive's profiles offer *Add plan*, and a plan among them offers *Edit plan*. Each opens the
  planner bound to that dive, blank to add a plan and holding the saved one to change it, and
  saving goes back there. *Save as new dive* is always offered as well, and so is *Attach to
  existing dive*, a menu of the logbook's dives, newest first. Attaching always adds a plan: it
  takes the name typed where that dive holds no plan of the name, and the next free letter where it
  does, so it never saves over one. *Open plan* is a menu of every saved plan, named by its dive's id
  and its own name, as `2026-03-01#1: Plan A`, and opens the chosen one as *Edit plan* would, so a
  plan can be found without first finding its dive.

  **A plan saves its start and what it follows.** The start goes on the plan itself, as a
  recording's does, so a dive holding only plans has a start and an end, and a new dive is filed
  under its day. The run followed goes on the plan as `previous_profile`, since a dive's own
  `previous_dive` speaks for its primary run alone. A new dive also names that dive as
  `previous_dive` and takes its time zone, so the surface interval the logbook works out is the one
  the planner showed. A dive already there takes `previous_dive` only where it names none: a dive
  follows one dive, so a plan following another than the one it names is refused there, with a
  sentence saying which, and may still be saved as a new dive. Opening a saved plan brings its
  start and its choice back.

  *Forget the dive* unbinds a plan opened from one, so the next save makes a new dive instead of
  saving over the one it came from.

  **One way.** What is saved is the run with its way up, as points, `LOGIC-35`; its cylinders, each
  keyed by what it is for, with its role written as `usage`; its gradient factors and its water.
  The planner's other settings, the oxygen limits, the safety stop, the rates, the reserve and which
  lines were typed, have no field and are not kept. A plan opened again is its points as typed
  lines under the settings the planner starts from, and a stop the model wrote a point a minute is
  one line.

  **A plan is named, and its name is its key.** The first letter free on the dive: *Plan A*, then
  *Plan B*, typed over freely. A key holds no spaces, so *Plan A* is kept as `Plan_A` and shown back
  as its name, a key being shown with its underscores as spaces. `JSON-18`. A plan saved over keeps
  its key, and anything else written on it by hand.

  A dive with no profile before takes the plan as its primary one; a dive with a recording keeps
  the recording, and the plan sits beside it for comparing.

- **GUI-43 — Calculations, as a tab.** *Settled:* **a list of what can be worked out on the left,
  the form for the one chosen on the right: a dive plan, SAC, NDL, MOD, EAD and END.**

  A calculation reads no dive: a diver on a boat wants a number from figures they have in their
  head, and the logbook is neither here nor there. So it is a tab of its own rather than a box
  under a dive, it needs no logbook to be open, and its shape is the shape a subject with a short
  list has — the list on the left, as Manuals lists its chapters, and the one chosen on the right.
  What is typed and which calculation is chosen are kept between visits, as anything a tab holds
  is. `GUI-27`. The dive plan heads the list and is the one chosen on the first visit, being what
  the tab is most often opened for.

  **SAC is six boxes, one of them worked out.** SAC, average depth, duration, cylinder size, start
  pressure and end pressure sit under each other, each with a bullet before it; the bullet chooses
  the one worked out from the other five, and its box shows the answer instead of taking one. The
  six are one equation — gas used is size times the drop in pressure, and the rate is that spread
  over the minutes at the pressure of the average depth — turned round six ways, and the pressure
  is the model's own `ambientAt`, so the tab and a recording's worked-out SAC cannot disagree. An
  empty box means the form waits; a box that will not read says which one and why; a set of five
  that gives no number, a duration of nought for a rate, says so rather than showing infinity. An
  end pressure below nought is shown as it is: it is the honest answer to a dive that would have
  run the cylinder dry, and a reader can see why.

  **NDL is the high gradient factor, a gas and a depth, and gives minutes.** The factor is
  prefilled from the settings the first time the form opens, and typed over freely after. The
  limit is the model's, `LOGIC-39`, which decides what a form must not: only the high factor is
  passed, and the descent is counted. **The low factor is not asked for**, since it says how deep
  a first stop is taken and nothing about whether one is owed: a box for it would ask a reader
  for a number that changes no answer, which is worse than leaving it out and explaining why.
  Salt water at sea level and the settings' descent rate are assumed, and a line says so. A depth
  the model finds no limit for is said in words rather than left blank.

  **MOD, EAD and END are a mix and one more figure each, and give a depth.** MOD takes an oxygen
  limit, prefilled from *pO₂ max bottom* the first time it opens, and is the model's own
  `maximumOperatingDepth`, so it is the figure the plan's gas table shows beside a cylinder. It
  gives the minimum depth too, from a least oxygen prefilled from *pO₂ min*, since a hypoxic mix is
  as dangerous too shallow as a rich one is too deep; a range with its minimum below its maximum
  says the mix is breathable nowhere rather than showing two depths that contradict. EAD and
  END take a depth, `LOGIC-41`, and END has a tick for whether oxygen is narcotic, on unless the
  reader says otherwise: agencies differ, the tick is where the difference is chosen, and a line
  under it says which way is the more cautious. The three share one mix box, since a reader asking
  one of them about a mix usually asks the others about the same one. **Each is rounded the cautious
  way**, MOD down and the minimum depth and the two equivalents up, to a tenth of a metre: a depth
  rounded the other way is one the mix is too rich, too lean or too narcotic for. A mix too rich for its limit at the surface
  already says so rather than giving a MOD of nought.

  **A waiver heads the tab**, above the list and the form both, so it is met before any figure is
  and whichever calculation is chosen. It is the one place in the window where a reader asks for a
  number to act on with no dive and no computer behind it, and a warning they would have to go
  looking for is one they will not find. Its words are the manual's and the licence's rather than
  its own: not a dive computer, certified and validated as neither, may be wrong, used entirely at
  the user's own risk, nobody accepting responsibility, checked against training and tables. One
  wording in three places cannot drift into a softer claim in one of them, which is why the
  sentence is a constant a test holds against those four claims. `manual/decompression.md`.

  **A dive plan belongs to no dive.** It is a runtime, the settings it is worked out under, the
  cylinders it is breathed from, what the whole dive costs, what the model objects to, and the
  graph a recording is drawn with. Where those go on a screen is `DESK-8` in
  [desktop/doc.md](desktop/doc.md).

  **Each line of the runtime is one segment, a change of depth or a stay.** A line is typed as a
  depth and either a duration or a rate. The other is worked out and shown in italics in its own
  box, and typing into that box makes it the typed one. Both blank takes the rate from the
  settings. A line at the depth before it stays, and has a duration and no rate. The arrow and the
  runtime are worked out, the runtime being the whole minute the line ends in, counted up. A
  duration is `m:ss` or whole minutes, read as every clock in the application is read.

  This was not the shape of the form plans were once started from on a dive, `GUI-41`, which asked
  for a depth and a bottom time counting the descent. That form asked a question with one answer. This one is a runtime
  table, and a line that is either a rise or a stay is what lets a reader type a way up at a rate
  of their own.

  **A line breathes the gas of the line directly above until one is chosen**, and says so in
  italics. That line need not be filled in yet: a gas chosen before a depth is already what the
  lines below it follow. A
  cylinder is named there by its number and its mix, as `2: EAN50`, so a reader choosing one need
  not look across at the gases. The mix is written as the application writes a gas, whatever case
  it was typed in, and the gas box itself takes that case as it is typed where only the case
  differs. The first line follows the first cylinder. Choosing the gas the line above breathes follows it again,
  so there is no separate deed for going back. The switches are written where a line changes
  cylinder, and the first is always at nought: a run that says nothing about what it went in on is
  refused, and rightly. `LOGIC-37`.

  **The way up is always there, in italics and without boxes.** The model completes a valid ascent
  from the last typed line to the surface, and it is shown beneath, changing as anything above it
  is typed. A stop the model writes as a point a minute is one line. A rise through several depths
  is one line too, unless a gas is switched on the way. A reader who types part of the way up
  shortens what is added, and a dive typed to the surface has nothing added. Nothing turns those
  lines into typed ones. The deed that did, *Add ascent*, is gone: a reader who wants to shape the
  way up types it, and the italic part fills in only what they left.

  **A plan has a start, and may follow an earlier run.** Under the heading, a date and a time, and
  *After*, a choice of the runs in the logbook that ended in the two days before that start, the
  latest first, each named by its dive and its own name. The choice starts at *None* and is never
  made for the reader: following a run is a claim about what the user did, and one made for them
  would carry a dive they did not mean. Where it stays at *None* and a dive ended in the day
  before, a note says so and how long before, as *2026-10-03#0 ended 1 hour and 30 minutes before
  this start: choose it under After if this dive follows it*. Once a run is chosen the same place
  says what the plan starts with, as *Surface interval 1 hour and 30 minutes after 2026-10-03#0,
  Plan A; CNS 18 % at the start*, in plain words rather than the model's: the words are about the
  user's dive, not the arithmetic behind it. The whole plan is then worked out from what that run
  left, `LOGIC-37`. A start that will not read, or one before the chosen run's dive ended, is the
  plan's fault as any setting that will not read is. A start alone, following nothing, changes
  nothing but what is saved.

  **The settings are the plan's own.** They sit in titled sections, each a single column, the
  sections stacked in two columns with space between them. On the left, *General* holds descent
  and ascent rate and water, and *Gas* pO₂ max bottom, pO₂ max deco and pO₂ min. On the right,
  *Algorithm* names the model, Bühlmann ZH-L16C, as a value that is shown rather than chosen until
  there is a second model, above GF low and high, and *Stops* holds the last stop and the safety
  stop's depth and duration. The gas reserve's settings are not among them: they have a box of their
  own beside what they decide, below. Each starts
  from what the settings hold, `GUI-42`, and is changed here for this plan alone. A safety stop of nought minutes is none,
  and its depth is greyed. The safety stop is a minimum the ascent holds, with a longer deco stop
  there counting towards it, and a dive that does not hold it is warned of, typed or not. The water
  is weighed as a recording made in it would be.

  **A cylinder has a role: bottom, deco or bailout.** The role decides two things. Bottom and
  bailout are held to *pO₂ max bottom* and deco to *pO₂ max deco*. The way up may switch to a bottom
  or deco cylinder by itself, and never to a bailout, which is breathed only where a line names it.
  Any line may name any cylinder. A bailout is held to the bottom limit because it is breathed in
  trouble, at the effort that brought the trouble on. A cylinder added is a deco cylinder.

  How deep each may go is the model's `maximumOperatingDepth` at that limit, so the table, the
  ascent's choice and the warning are one figure. What the plan takes from each cylinder and leaves
  in it are worked out beside it.

  **The gas reserve has a box of its own, between the settings and the gases.** *Contingency* holds
  the reserve's two settings on the left, the panic stress factor and the problem-solving time,
  and beside them a line for each scenario, `LOGIC-40`, each beginning with its own switch. The settings and what they decide sit
  together, so a reader changing one sees the other move; the user moved them out of the settings
  and from under the clocks for that. What each cylinder must still hold is also a column of the
  gases, *Minimum*: the most any scenario switched on asks of it, red on a cylinder that falls short
  in any of them. Each line gives the scenario's name, *Lost* or *Buddy out of gas*, then its
  switch, then what it came to. The lost-gas line's switch is the choice of gas lost, below, and
  the sharing line's a tick that switches it off, both shown before the plan can be worked out as
  well. The name and its switch sit close together, as wide as the widest pair needs, in a slot of
  one width for both lines: the results line up, and the width a fixed slot for each part would take
  is left to them. Each line says what
  it asks each cylinder
  to hold, its own worst moment, and what it assumes, in one sentence: *Gas 1 needs 28 bar at 25:00
  (40 m), two divers sharing to 21.6 m, each at 2 × SAC*: *each* is there because a bare *2 × SAC*
  read as both divers together, and the multiple is given as it is rather than by the setting's
  name. A scenario that asks nothing says why, as *No
  sharing needed: each diver switches to 2: EAN50 at once*, rather than a worst moment at the
  surface that would mean nothing. The lines are where a reader sees which scenario set a minimum, and the
  phrase is the rule said once, where it applies, rather than in a paragraph they would have to go
  looking for. Both start on, since a reserve nobody asked for protects nobody.

  A cylinder that falls short is also a warning in the list beneath, saying which scenario it falls
  short in and in the names the model's own warnings use, so *Gas 1 runs out* and *Gas 1 is empty*
  read as one cylinder. Which cylinder the lost-gas scenario loses is a choice of one cylinder on
  that scenario's line, where a tick would be. One, because losing two at once is
  not a scenario anybody plans for; the user replaced a tick on each cylinder with it for that
  reason. It is the first deco cylinder until another is chosen, follows its cylinder when others
  are added or taken out, and goes back to the first deco cylinder when its own is taken out. **Its
  first choice is *None*, which leaves the lost-gas scenario out**: the choice of what is lost and
  whether anything is lost are one decision, so the user merged the scenario's tick into it. A plan
  with no deco gas and nothing chosen shows *None* too, rather than asking for a choice. It is
  a setting rather than a fourth role, since losing a bottom gas is a scenario a reader may want to
  try without changing what the cylinder is for. The panic stress factor is read apart from the
  other settings, so a factor typed wrong leaves the sharing scenario unsaid and everything else
  answered. The problem-solving time is read the same way, and being where both scenarios begin, a
  time typed wrong leaves both unsaid. The scenario's sentence names the hold, as *1:00 at depth,
  then …*, so the figure beside a cylinder says what it allows for.

  **A cylinder a line breathes stays.** Its × is greyed, and a tooltip says which line breathes it.
  Taking it out would change the dive behind the reader's back, so they change the line first. A
  cylinder only the way up breathes can go, and the way up is worked out again without it. The
  first cylinder can therefore go once the first line names another.

  **What the model objects to is a list under the clocks**, one sentence each. Some warnings have no
  line to blame, such as a cylinder running out, and one list holds them all.

  **Two faults are also marked on the line itself, in the error colour.** A gas is red on a line
  that goes deeper than its MOD at the limit its role gives, or shallower than a hypoxic mix's
  minimum depth. A depth is red on a line where the dive
  is above the ceiling, judged at the run's own points as the model judges it when it warns, so the
  mark and the warning are one judgement. A worked-out line can be red too: a way up that begins
  above the ceiling stays there until the ceiling clears, and saying otherwise would hide it.

  **It reaches the model through a `Run` rather than an item**, `LOGIC-37`, so a plan typed here and
  a plan on a dive are answered by one walk and cannot disagree; and the figures are the same list
  `GUI-40` shows under a recording, and the graph is the one a recording is drawn with, the
  cylinders named by whatever knows their names. **Nothing is stored until it is saved**, `GUI-44`.
  Until then a plan typed here is kept while the window is open, as anything a tab holds is.
  `GUI-27`.

  **The units are the model's own** — litres a minute, metres, minutes, litres, bar — until the
  units a user wants shown are read. `UI-2`.

- **GUI-42 — Where settings are seen and changed.** *Settled:* **a deed on the home screen, and a
  form of every setting saying where each value came from.**

  Fourteen settings are still a form rather than a place, so they open in the System box like a
  download, from a deed of their own, rather than taking a tab. A tab is the answer once there are
  enough of them to want sections; nothing about this one stands in its way.

  **Each setting says where its value came from**: *set on this device*, *set in this logbook*,
  *the default*, or *not set* for one with no default. `DATA-9` has three layers and a reader
  cannot see them otherwise, and a choice kept on one device is one somebody will look for in vain
  on another.

  **Save writes what changed, and all or nothing.** Every box is read before anything is written,
  so one that will not do leaves the rest as they were rather than half a form saved; what will not
  do is said in the setting's own terms, with its range and unit. An emptied box takes the choice
  away, and what answers is the next layer. Where a save goes is the Universe's rule, not the
  form's: to the logbook's file unless this device's file already holds that setting.

  **The water is chosen from a menu**, being one of a fixed set: `salt` or `fresh`, the words
  `water_type` uses, so a settings file and a recording say it alike. A word outside the set is
  ignored when read and refused when chosen, as a number outside its range is.

  **The gradient factors are typed as percentages**, as the plan form takes them and for the same
  reason, `GUI-41`, and held as the proportions the file writes. They have no default, and say *not
  set* until somebody sets them.

  **The agent's command is in the form, with how to set it up under its box.** It is text rather
  than a number and belongs to one device, which the form says beside it as it says of any setting.
  The explanation is there because the box is where a reader is looking when they get it wrong: the
  command is the agent's adapter for editors, not the agent's own program, and the adapters need
  Node.js. It uses the same words as the failures the panel gives — *adapter*, the `npx` commands,
  Node.js — so what a reader is told beforehand and what they are told afterwards agree. `GUI-38`.

  **A setting reaches the making of something and nothing after.** The planner reads them when a
  plan is started; nothing that works out what a plan or a
  dive comes to reads one. `LOGIC-35`.

- **GUI-41 — How a plan is started.** *Replaced by `GUI-44`*, which makes a plan in the
  Calculations tab's planner and saves it on to a dive. It said: **a button on the Dives tab and a
  form of eight questions, making a dive at one depth with its ascent already added.**

  A plan is a dive that has not happened, so it is started where dives are read: *Plan dive* sits
  above the table, and the form takes the place of the dive shown, titled *New plan* as a new
  item's form is titled. What it makes is chosen in the table, so the reader lands on the plan.
  Everything a plan holds beyond its first shape — the site, the cylinder, a second gas — is
  changed there like any dive, so the form asks only what a profile cannot be drawn without. It was
  a deed on home first, and moved because a plan belongs with the dives rather than with what is
  done to a logbook as a whole. A half-typed form survives a visit to another tab, as anything a
  tab holds does. `GUI-27`.

  **Eight questions**: the day, a time if one is known, the depth of the bottom, the minutes from
  leaving the surface to leaving the bottom, the gas, salt or fresh water, and the two gradient
  factors. Typed as text and read when the plan is asked for, so a half-typed depth is not refused
  keystroke by keystroke, and what will not read is said in the reader's terms: *03/10/2026 is not a
  date, which is written 2026-10-03*. The bottom time counts the descent, which is how a diver means
  one, and one too short to reach the bottom in is refused with the arithmetic.

  **The gradient factors are asked for and never assumed.** They say how conservative the plan is,
  and a number the application chose for the reader is a safety decision nobody made. They are typed
  as percentages, the way divers say 30/70, and a value below one is refused rather than read as a
  fraction of a percent: `0.7` is what somebody who knows the file's proportions types for seventy.
  Where the user chose `default_gf_low` and `default_gf_high` in the settings, those start the two
  boxes; where they chose nothing, the boxes start empty. `GUI-42`.

  **What it makes is written as a file writes a plan**: a dive holding one profile marked `planned`,
  its depths running down at the descent rate and holding the bottom, one gas source switched to at
  the start, and the model named. The ascent is then worked out and added, so what opens is a whole
  plan with its stops rather than a bottom waiting for a button. A plan the model will not answer for
  is still made and opened, and its own view says why. `GUI-40`.

  **What it assumes is written beside the deed**: the descent rate, the ascent rate and the
  shallowest stop, as the settings hold them, and a pointer to where they are changed. Said rather
  than hidden for the reason `GUI-40` gives.

  A plan of several levels is not started here. It is still written by hand, and the form would need
  a table of rows to take one.

- **GUI-40 — Where what the model says appears, and how a plan is written.** *Settled:*
  **under the run it is about, in the box that already holds the run.** Its second half, *a plan
  carries a button that works its ascent out*, is *replaced by `GUI-44`*: a plan is saved with its
  way up already in it, and changed in the planner. What is said below about the button is kept as
  the record.

  A run is a recording or a plan and the window draws either, so the model's answer belongs where
  the run is rather than in a screen of its own. `LOGIC-37` answers for both alike.

  **The ceiling goes on the depth axis**, not the right one: it is a depth, and what a reader
  looks at is the gap between it and the line that was swum. `Chart` already draws a second line
  there with the water above it shaded, which is what a deco stop uses, so a ceiling took no new
  drawing. It is left out where the run owes no stop, a flat line along the surface saying only
  that the graph has another line in it.

  **Everything else the model works out is a right-axis overlay, and each says it was worked
  out.** A recording may carry its computer's own beside it, and the two disagree on purpose:
  `manual/decompression.md` tells a reader the device decided at the time with settings this
  cannot reproduce. *NDL calculated* beside *NDL* is the difference stated where it is read.

  **The figures and the findings read as fields.** What a run costs, the clocks and the two waits
  are shown as worked-out values under the run's own fields, and each finding is a line labelled
  with the minute it happened at, in the error colour. A finding is a value that would not read,
  in the sense `GUI-8` already gives the phrase: something here is wrong and the reason is the
  useful part.

  **A recording the model cannot answer for says nothing; a plan says why.** Most recordings
  carry no gradient factors, so a refusal under each would put a red line under nearly every dive
  in the logbook meaning only *the model was not asked* — and a reader who learns to skip that
  line skips the one that matters. `LOGIC-37`'s refusals carry which kind they are, so a fault in
  what somebody wrote is shown wherever it is found and an absence is shown only on a plan, where
  something its writer meant to supply is missing.

  **Worked out once per run per edition**, held by `remember` against the Changer's count. That is
  `LOGIC-37`'s cache, in the window because the logic layer holds no state and `LOGIC-4` has not
  settled whether it should. The cost was measured rather than assumed: a four-thousand-sample
  recording costs about 10 ms, and the same profile at ten metres cost 84 ms until the search
  behind it was made to leap, which it now does — 23.5 ms. A dive opens in a frame and a half,
  once.

  **What the ascent assumes is on the button, not behind it**: the rate it rises at and the depth
  of the shallowest stop, which `LOGIC-35` takes as arguments rather than storing. They are read
  from the settings when the row is drawn, `GUI-42`, and the row says what they are, so nothing
  about the ascent is worked out from something a reader cannot see.

  **The run a plan carries gas from is typed, not chosen**, as `@2026-09-20#0*b`. Every other key a
  form offers is an entry of the item being edited, so it lists them; this one is an entry of
  another dive, and there is no dive chosen yet to list from. Offering a list anyway asked the
  profile for a collection profiles do not have, which threw, and opening the form on any dive with
  a recording closed the window. The field says what it takes, and the description refuses a bare
  `*b` with a sentence.

  **The button says what pressing it does**: *Add the ascent*, in the word a diver uses for that
  part of a dive. It read *Write the way up* first, which is this document's phrase for the thing
  rather than anybody else's, and a button borrowing a document's prose asks a reader to have read
  the document.

- **GUI-9 — Whether a statistic says what it left out.** *Settled:* a statistic is
  shown with the items behind it, both the number used and the number there were —
  "total time underwater: 210 hours (248/253 dives)". The reader sees the sample and
  anything excluded from it in one place, without every exclusion being itemised.
- **GUI-13 — The tab switcher on a phone.** *Relocated to [phone/doc.md](phone/doc.md) as
  `PHONE-5`.*
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
- **GUI-39 — What the window does with a dive that has only been planned.** *Settled:* **shown
  and marked, counted nowhere.** `LOGIC-36` settles the same question for the model; this is the
  half a reader sees.

  A logbook that claims a dive nobody made is wrong, and one that hides what is planned is
  useless. So a plan is a row in the dive table like any other, and the column that would carry
  its number says `plan` instead, greyed: a plan has no place in the reader's own numbering, and
  that column is the only one whose value a plan does not have. Its recording is drawn as a
  dashed depth line, dashes being what a line that has not happened yet is drawn with
  everywhere. The `planned` field itself is housekeeping, `DATA-115`, so it is not among the
  fields on a card: the row, the dashes and the planner say it, and a line reading *Planned: no*
  on every dive ever made says nothing.

  **Counted nowhere** is the greeting and the places it counts, the statistics in all seven
  gatherings, the year rows of the table, and what a year chooses when it is clicked. A year
  still lists its plans and counts only the dives made in it, and clicking it chooses those: what
  a set of dives comes to is a figure over diving.

  **What a reader chose is answered as chosen.** Ticking a plan by hand and asking what the
  selection comes to gives an answer including it, which is `LOGIC-34`'s rule — the judgement
  sits with whoever chose. Only the counts the application makes on its own skip plans.

  **An export leaves plans behind**, `Writing.kt`, and the sentence says how many were left. A
  plan written into a file handed to another application is a claim that the dive happened, and
  nothing in the file lets the reader tell otherwise. UDDF's own `diveplan` is the honest route
  one day.

- **GUI-38 — How an agent is reached from the window.** *Settled:* **a side panel hosting the
  user's own agent over the Agent Client Protocol, on desktop only.**

  **The window starts the agent the user picked and is its interface.** Any agent that speaks
  the protocol is offered, and the manual names the ones tested. The agent logs in by itself, so
  whether it runs on a subscription, an API key or a local model is between the user and its
  provider. Yemoja makes no call to a model and stores no key. Providers refuse a subscription
  to an application calling their models directly, and allow it to their own agent hosted by
  another program. That is the position at the time of writing, and it has changed before.

  **Desktop only**, because an agent is a program installed on a computer.

  **When a conversation starts the agent is handed the tool server**, `API-4`. Its own requests
  to read or write a file or to run a command are refused, every time, so the tools are the only
  way to the logbook — unless the user ticks *Allow raw file access*, when a read or write inside
  the
  logbook or the agent's own folder is served and its own tools of the file-handling kinds are
  allowed there, and the window reads the logbook again when the turn ends. A command is never
  run. `API-5`.

  **An agent asks permission for the tools it was given as well as for its own, and only the
  second kind is refused.** The protocol makes no distinction between the two, so an agent that is
  turned down for everything is turned down for `describe` and answers nothing at all. A refusal a
  reader sees is therefore always the agent reaching past the logbook for the machine, which is
  what makes it worth saying aloud.

  **The panel stays open while the user moves between tabs**, so a dive the agent names can be
  opened while the conversation carries on. Where it sits and what it looks like is `DESK-9` in
  [desktop/doc.md](desktop/doc.md). It holds the
  choice of agent, the conversation, and two boxes that are off at the start of every conversation:
  *Allow logbook edits*, which lets the agent stage, and *Allow raw file access*, which lets it
  at the files
  directly. `API-5`. A box that held back a person's private details is gone with the machinery
  behind it: an agent is sent whatever it reads, and the manual says so where its chapter begins.

  **An item is cited as a mention**, `@2026-04-28#1`, the convention `JSON-23` settles for a
  remark. A mention that resolves is shown as the item's name and leads to it, which keeps *an id
  is never shown* the way an ordinary reference keeps it. One that resolves to nothing is shown
  as written.

  **Staged changes are reviewed where an import is**, in the home screen's System box, and the
  panel says how many items wait there with a deed that brings the reader to it from any tab. One
  place to review a pending change whatever proposed it, and a table of before and after wants the
  window's width: an agent that corrects forty dives fills a panel's column with a scroll nobody
  reads. The panel keeps the conversation; the review keeps the table. `RECON-8`.

  **Every row shows both values, and a stale one says so before anything is pressed.** An item is
  called what the logbook calls it, or *a new dive site* where it is being added, since an id is
  never shown and the one it is staged under is not the one it will have. Each field is its path
  read as words — *Environment · Visibility* — then what it holds and what it would hold; one being
  added or deleted shows its one side. A field the logbook has moved under since it was staged says
  what it is now, in the error colour, beside the change it spoils. Items are counted by what would
  happen to them, because *1 item to delete* among nine edits is what a reader most needs to see.
  Each item can be discarded, and the whole applied or discarded.

  **The values are what a file would write**: `19.7 → 21.4`, and a list as its brackets. A unit
  beside them would need the review to know each field's description, which a staging does not
  carry, and a before and after that read like the user's own files are at least read the same way
  throughout.

  **What a review came to is said where it was done and in the conversation.** Under the review,
  so that applying changes none of which could be applied visibly does something; and as a turn of the window's
  own in the panel, so the conversation records what became of what the agent staged. It is shown
  to the reader and never put to the agent: a line sent to an agent is a prompt it answers, which
  spends the user's plan on a turn they did not ask for, and the agent learns the logbook moved
  from the revision on its next reply. `API-4`.

  **Nothing of a conversation is kept.** It lasts as long as the panel holds it, and whatever the
  agent keeps on its own side is between the user and its provider. What was asked, and whose
  names were in it, is never written beside the logbook, where syncing would carry it.

  **The command that starts an agent is a setting, and on this device only.** It is
  `desktop_agent_command`, set in the settings form beside an explanation of what to type, and the
  panel reads it when it starts an agent: the panel holds no box for it. Only in
  `settings.local.json`, and a copy found in the logbook's file is ignored: a command names a
  program where one machine keeps it, often under a folder named for the user, and the logbook's
  file carries whatever is in it to every machine. `GUI-42`, `DATA-9`.

  **It opens from a button on the tab row**, at the right of the tabs and so on every tab: a
  question comes up wherever the reader is, and a deed on home would send them there first. The
  button shows a sparkle, the mark readers have come to know for an AI feature, and says *Ask an
  agent* over itself while the pointer rests there: an icon keeps the row the tabs' own, and the
  name is one hover away rather than lost. It is greyed where there is no agent to ask — no
  logbook open, a platform that hosts none, or no command set — and then says why instead, naming
  the settings where the command is set, so a reader who cannot press it learns where to go
  without leaving their tab. The icon is Material's own, from the set the interface already draws
  on. Once opened the panel keeps a fixed width beside whichever tab is showing, and the same
  button shuts it: one press opens, the next closes, and what the press would do is what the
  pointer is told. A *Close* of its own was taken out once the button became a toggle — two ways
  to shut one panel, one of them where the panel is and one where the button is, is a choice a
  reader should not have to make.

  **Opening the panel starts the agent.** The button that opens it is greyed until a command is
  set, so by the time the panel is open there is nothing a *Start* of its own would wait for, and
  a click that only ever followed another click was a step for nothing. *Start* is offered only
  after *Stop*, or after a start that failed, where it is the way to try again once the command has
  been mended.

  **What it holds, top to bottom**: a title naming the agent the command names, *Ask Claude*,
  with *Stop* beside it — *Start* after a stop or a failed start; the conversation, a
  view of its own for copying; what the stance says while an agent is starting or thinking; how
  much is staged and the deed to review it, where anything is; and what to ask, with the two boxes
  under each other to the left of *Ask*, a box to a line: the panel is a column beside a tab, and
  three of them across it left each one a word wide. *Ask* is live only while an agent waits to be asked, and Enter presses it,
  Shift and Enter making a new line, as in any chat. While the agent answers, *Ask* gives way to
  *Interrupt*, which is the protocol's own cancel: the answer ends where it has got to and stays on
  the screen, and the agent stays running to be asked again. *Stop* is the other thing, and ends
  the agent itself. The first box lets an agent stage, not change: nothing it stages lands until
  the reader takes it in.

  **A turn that was stopped says nothing.** Starting an agent and asking one both answer on a
  coroutine, and a reader who presses *Stop* meanwhile has closed the conversation already, so what
  those answer is about a conversation that has gone: letting a start finish its work would leave
  the panel saying an agent is ready while its tools are shut and its process is stopped. Each
  counts the conversation it began in and says nothing where that has moved.

  **A turn ending redraws what is staged.** An agent stages while it answers, and nothing below
  this layer announces a change, `DATA-6`. So the panel says when a turn has ended, the staged
  count and the review are read again, and the review applies only the edition it was showing —
  `RECON-8` refuses the rest, so a list that moved under a reader cannot be taken in by pressing
  *Apply all*.

  **The window speaks in the conversation too.** An agent that will not start, one that stops part
  way through an answer, and each request of its own that was refused are turns in the same
  column, unattributed and in the outline colour. A refusal is shown once: what an agent was
  refused only ever grows while it runs, so the panel remembers how many it has said. They are
  rare, for the reason above.

  All of it is built.
- **GUI-37 — How a logbook is exported from the window.** *Settled:* **a deed on the home
   screen, a file named, and one sentence about what went.**

   The logic layer does the writing, `Universe.exportTo`, and what UDDF can and cannot carry is
   `logic/uddf.md`'s. What the window adds is where to put the file and what to tell the reader.

   **A deed beside the import, and the whole logbook.** Export changes nothing in the logbook,
   so there is nothing to review and nothing to choose among: the question a reader has is
   *where*, and the platform's save dialog asks it. A name typed bare is given `.uddf`, and a
   file already there is asked about before it is written over, since the dialog does not.

   **One sentence afterwards, under the deeds**, saying how many dives went and where. The one
   loss said aloud is a dive recorded on more than one computer, which goes out with its primary
   recording only: nothing in the file shows it, and a reader would otherwise find out in the
   other application. Every other loss is the manual's to list rather than the screen's to
   repeat on each export.

   It runs off the interface's thread, as a download does, since a logbook of recorded dives is
   megabytes of samples.
- **GUI-36 — Whether what the window shows can be copied.** *Settled:* **all of it, a view at
   a time.**

   A logbook is full of things worth taking somewhere else: a site's coordinates into a map, a
   serial into a shop's form, a computer's own words into a search, a remark into a message. A
   window that shows them and will not let them be copied makes a reader type them out with the
   answer in front of them.

   **A selection stays inside the view it began in.** The list on the left, the item on the
   right, the regions and the sites at one, the manual's contents and its chapter: each is a
   view of its own. One container over the whole window would let a drag over two lines take
   both panes with it, and what came out would be a site's remarks spliced with the names of
   the sites listed beside it.

   Every view is one, so the rule is still *every word*, and no list of which words may be
   copied has to be kept true as screens are added. The tabs along the top are in none, being
   what is pressed to move about rather than what is read. A new view is wrapped in
   `Selectable`, in `Screens.kt`.

   **A field being typed into is outside it.** A text field has a selection of its own, which is
   what a caret is, and two selections over one run of text fight: a drag would paint the
   view's selection across the box rather than moving the caret. Those are the one exception,
   and nothing is lost by it, a text field already copying what it holds.

   **A menu's words are outside it too.** A dropdown opens in a layer of its own and inherits the
   selection of the view it opened from all the same, and pressing on one of its words began a
   selection the view could not measure against: choosing *quarter* in the statistics threw
   rather than choosing. Every menu is `Menu` in `Screens.kt`, which switches selection off.

   **So are a tooltip's.** A tooltip opens in a layer of its own the same way, and while one showed,
   a press on the words under it threw for the same reason: choosing a gas lost in the planner,
   whose dropdown carries a tooltip, closed the application. `Explained` switches selection off in
   what it shows, and a test presses words under their own tooltip to keep it so.

   **What a pointer does with all this is `DESK-10`**, a drag and a click being a large screen's
   inputs.
- **GUI-35 — How an item is made and unmade.** *Settled:* **two buttons beside the pencil, and
   a question before anything is deleted.**

   Reading and editing were reachable and the two ends were not: a logbook could be read and
   corrected from the window but not grown or pruned, and a new one could hold nothing at all.

   **Plus makes another of what you are looking at.** A tab may hold three types and the one
   worth adding is the one already in front of the reader, so the chosen item answers it: a
   person chosen makes a person, a trip chosen on the dive tab makes a trip rather than a dive.
   Where nothing is chosen the tab's first type answers instead — the dives the table is built
   around, the regions the sites hang from — and the empty middle offers it in words, *Add a
   dive*, which is the only way a logbook with nothing in it grows a first item.

   *Amended:* **on a tab holding more than one type, plus opens a list of them**, the one being
   looked at first. *Another of this* answers the common case and cannot answer the first one: a
   logbook with no trip in it has no trip to press plus on, so a trip could not be made at all.
   The list is the tab's own types and nothing else, so the Dives tab offers *Add a dive* and
   *Add a dive trip*, and a tab holding one type presses straight through as before.

   *Amended:* **Locations has both buttons too**, having had neither. It shows two cards, a region
   and what is at it, so the bin asks about the card it was pressed on rather than about whatever
   the tab has chosen. Its list is *Add a region*, *Add a dive site* and *Add a wreck*, which is
   the only way to make a first wreck: a wreck is reached through the site that names it, and a
   logbook with none had none to reach. Deleting a region the supplied library holds is refused
   where it is written, the libraries being read-only, and the reason is what the card says.

   **A branch of the gear tree chosen fills the form's first fields.** A reader looking at the
   cylinders who presses plus is adding a cylinder, and the category and the kind are answers the
   screen watched them give. So a chosen branch puts its own words into `category`, and into
   `kind` where a kind is what was chosen. They are typed into the form rather than written
   behind it: a reader sees them and changes them where the branch was not what they meant.

   That gives the gear tree a chosen branch, which it had none of: a click folded a category and
   nothing more. A click now chooses it as a click on Location's tree chooses a region, the arrow
   still folds, and choosing an item lets the branch go — a reader looking at one item is no
   longer at a category. Only gear has such a tree, so nothing else fills anything.

   **Plus makes nothing. Save makes it.** An id is minted once, at creation, from what the item
   says at that moment, and nothing renames it afterwards: there is no `Change.Rename` and the
   Universe has none, `data/doc.md` recording only that a rename belongs there. So an item made
   before its form is filled in is minted `unknown_person`, and stays that way after a name is
   typed into it; a second is `unknown_person#1`.

   The first attempt at this did exactly that. What the same page anticipates is the fix — *an
   interface that saves a filled form rather than a half-typed one will not produce it* — so
   plus opens an empty form over an item nobody holds, and Save hands the typed fields to
   `Change.Add`, which mints `anna_devries` from them. Cancel leaves no trace, there being
   nothing to undo. A block begun inside the new item folds into those fields rather than
   becoming a write of its own, there being no owner yet to write it onto.

   **It asks, because it cannot be taken back.** There is no journal yet, `RECON-1`, so a
   deletion is the one thing in the window with no way back. The question names one item and
   counts several — a reader deleting a dive wants to see which, and one deleting twenty wants
   to know it is twenty rather than reading a list they cannot check.

   **And it counts the references it would leave dangling, naming where they are while they
   are few.** A reference to something deleted is not hunted down, `DATA-17`, so deleting a
   person on forty dives leaves forty references pointing at nothing. That is a thing to know
   before rather than after.

   What is counted is references rather than the items holding them — one dive naming a person
   twice is two references and one item, and *reference* is the word the format uses for the
   thing being left dangling. A derived one is not counted at all: a site's `dives` works itself
   out, so deleting the last dive leaves nothing dangling and the question says so by saying
   nothing. `DATA-117`. What is *named* is the items, up to three of them: a reader can
   check three names against what they meant to delete and cannot check forty, and a dialog
   that tries turns a question into a wall. Past three they are counted and not named, which is
   the same handful `LOGIC-18` offers of the sites nearest a fix, for the same reason.

   **Several at once where several can be chosen**, which today is dives: the box of statistics
   over a selection carries the same delete, asking about the lot as one question. Plus is not
   offered there — what would be added is not in doubt, but the card is about a set rather than
   about an item, and one is not made by looking at twenty.

   **And it offers to clear them.** A box in the question, off until it is ticked, turns the
   deletion into `Change.Delete`'s `alsoReferences`. Off, because rewriting other items is a
   second thing being asked for rather than a tidier way of doing the first: `DATA-17` settles
   that a dangling reference is a state the model carries and an interface shows, the same state
   as a buddy not entered yet. The box is only there where something points at what is going.

   **What the item owns goes with it**, and needs no rule: an owned item lives inside its
   owner's file and is written and read as part of it, so deleting the owner deletes the file
   and everything in it. Nothing outside can name an owned item either — a key names an entry
   inside one owner and is meaningless the moment it leaves — so there is nothing to dangle.
- **GUI-34 — What the home screen says about what has fallen due.** *Settled:* **two boxes
   under the greeting, red for what has lapsed and amber for what falls due within a month.**

   A logbook knows when a cylinder is out of test and when a medical has run out, and a reader
   who has to go looking for that will find out when the shop turns them away. So it is said
   where they arrive, above everything they might have come for.

   **Three things fall due**, which is all the model has a date for: a piece of gear owing work,
   the user's medical, and their insurance. Nothing else carries a day it runs out on.

   **Only the user's own medical and insurance.** A logbook holds other people, and when their
   certificates run out is their business rather than a banner on somebody else's screen. Gear
   is not qualified that way, a logbook's gear being the logbook's; a `generic` item is left out,
   describing a kind rather than one that is owned.

   **Two boxes rather than one**, because a box is one colour and a service due next week put
   inside a red one says it is already a problem. What lapsed is read as an error, in the role
   Material has for one; what is merely near is amber, which is a colour Material's scheme has
   no role for and which the palette therefore carries itself. Amber rather than a paler red:
   the two sit next to each other and have to be told apart at a glance rather than by reading.

   **A month's notice**, which `LOGIC-7` in [../../logic/doc.md](../../logic/doc.md) leaves open
   and which this does not close: that question asks what notice each kind of obligation wants,
   and a service and a five-yearly pressure test do not want the same. One month for everything
   is what a first version says rather than the answer.

   **The latest work for each thing owed is the one that counts.** An entry sets the clock for
   what its `follow_up_type` names, or for its own `type`, and a later entry for the same thing
   replaces it. Warning about every dated entry put a regulator on the screen twice, once for an
   inspection already redone. Latest is by `date`, and by `valid_until` where entries give none.

   **What lapsed is never dropped, however long ago.** A cylinder two years out of test is more
   of a problem than one due next week, not less, so only the far future is left out.

   **A medical is taken to run a year from the last check.** The model records when a person was
   examined and nothing about how long that lasts, and a year is the common answer rather than
   the rule: age, the authority and an employer all change it. So the warning says the check
   *falls due* rather than claiming a certificate expired, and it is the one line on this screen
   resting on something the logbook did not say. Gear and insurance rest on dates that were
   written down.

   Nothing at all is shown where nothing is due, which is the ordinary case and stays silent.

   Open: a warning leads nowhere. Clicking one should open the gear item or the person it is
   about, which is the same thing `GUI-30` owes for a dot on the plot.
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
  the computer that made each. *Merge* is offered, never taken: over-eagerness costs a
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

  *Import all* does every row as its own button would, for a reader who has read the list and
  agrees. *Close* keeps them staged, where they are found again and where the terminal
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
  tabs that are not about what a logbook holds are offered then, which is Home, Calculations and
  Manuals; a
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
  graph and shows only its own data, which is what a tab is for, **but all of a dive's graphs
  share one scale**: time and depth reach as far as the longest and deepest of them, and a
  reading two recordings both hold, such as the temperature, spans both. Moving between tabs then
  shows how the recordings differ rather than redrawing each to fill the box, where a shorter
  or shallower one would look the same size. What the model works out is worked out for the
  tab on show only, so its axis is that recording's own. Marks fall on values a reader
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
  the card, between the buttons that make one and unmake one, `GUI-35`, turns it into the edit
  form: the same fields in the same places, so a reader who knows where a field sits when reading
  knows where it sits when editing. How a desktop frames that is `DESK-11`. Cancel puts the card back
  as it was; Save hands every field changed to the model as one change, and a refusal comes
  back to the field it was about.

  **A field that belongs to one kind of gear is folded away on the rest.** Gear is one type
  whatever the item is — [../../data/doc.md](../../data/doc.md) settles that under *Types are
  not subdivided*, a variant simply omitting what does not apply — so a drysuit's form offered
  an access code and a salt density, both of which are a dive computer's. They now sit behind
  a fold that opens, counted on its own line. Four fields need it, all on gear: `serial`,
  `access_code` and `salt_density` belong to `instruments`, and `capacity` to `cylinder`.

  **Folded, never hidden**, and the distinction is the whole of why this is allowed. What
  decides is the item's own `category`, which is free text a reader typed; a cylinder filed
  under the wrong word would lose its capacity for good if the form took the field away rather
  than tucking it out of the way. An item that says nothing about what it is keeps everything
  folded, a reader who has not said what it is not having said the field applies. And the data
  layer's refusal was about validation — it will not insist on or forbid a field because of
  another's value — which says nothing about what a screen offers, that being `GUI-16`'s.

  **A singular owned item that is absent is offered anyway.** Until this the form drew an inset
  only where the item already had one, so a gear item with no buoyancy block could not be given
  one: the fields existed, the manual described them, and the window could not reach them. The
  inset is now always drawn, with its fields empty and ready, and no button in front of them —
  *Add* before eight fields nobody can see asks about storage while pretending to ask about
  diving.

  **A block nobody had is the form's until Save.** It is not written into the logbook when the
  form opens: the draft holds it, the fields are edited into it as into any other, and on Save it
  lands as one write of the whole block onto its owner — or not at all, where nothing was typed.
  `DATA-116` makes the same rule everywhere, so a block emptied by any route stops being
  written.

  **A field is a row: its label, its widget, then the button that unlocks it.** The label is
  ranged against the widget as a card ranges a label against a value, so a field sits in the same
  place read and written, and every widget in a column begins and ends at the same two positions.
  Before this the form stacked a small label over each box and the card put the label beside the
  value, so the two views of one item shared nothing but their order. Fields go two to a row, and
  **a list or a paragraph takes a row to itself**, `GUI-16`: a stack of buddy boxes beside a
  one-line field left a hole where four fields would have been.

  **A field the model fills in is drawn the same whether or not it had an answer today.** It is
  its own box, locked and faded, holding what was calculated, the reason it would not read, or a
  dash for nothing, with *override* in a slot of its own beside it. What the box holds is `GUI-49`.
  Before this a calculation that succeeded gave a line of text and a button while one that came to
  nothing gave an ordinary empty box, so one field was two different controls and the shape of a
  row said what the data happened to be rather than what kind of field it was. The slot is kept on
  every row of a type that has one field to override and costs nothing on a type that has none.

  **Reverting locks the box again and shows the calculation at once.** A correction is still in the
  logbook until Save, so a box reading the item after a revert would show the very value the reader
  had just dropped, and the button beside it would go on offering to revert something that was
  already gone. The form therefore shows what the field *will* say: the computation, run against
  the item as it stands. What the draft carries is separate — clearing a correction the logbook
  holds is a change Save must make, while clearing one typed into this form is the draft forgetting
  it, so Save goes back to being offered only where something really changed. `offeredOf` decides
  all of this in one place, away from the drawing.

  **A chooser is the same box as a text field.** A fixed set, a key and a gas were drawn as a word
  with a small arrow after it, thirty pixels wide beside a box that filled its column. They now sit
  in the box everything else sits in, the arrow at its right, and `Modifier.boxed` is the one place
  that border and ground are decided — so a planner's settings, a table cell, a calculator's input
  and a form's field cannot come apart. Nothing is typed into a chooser; the menu does the work.

  **The machinery is folded away with what does not apply.** A housekeeping field — a dive's
  `time_zone_offset`, `planned` and `primary_profile` — is reachable and out of the way, behind the
  same *N more fields* line that holds the fields of another kind of gear. Both are things a reader
  is not looking for and must still be able to reach.

  **A section is a box when editing too**, as it is when reading, so *Details* does not dissolve
  into loose fields the moment the pencil is pressed.

  **One widget per kind of field**, chosen from its description: a text field, taller for
  multiline text; a number field with its unit after it, a time as `m:ss`; a date as text; a
  yes-or-no as a three-state box, the third being *not said*; a fixed set as a drop-down, and a
  suggested set as a text field with the suggestions offered from an arrow beside it and not
  enforced; a rating as five stars to click; a reference as a drop-down over the type it points
  at that typing narrows, the item taken written as its id and anything typed and not taken
  standing as a plain name where the field allows one; a key reference as a drop-down over the
  collection's keys; a list as one such widget per entry, with add and take out. A series is not
  edited: the graph is its place.

  **What is worked out is shown, not edited.** A derived field is read-only, and how it is drawn
  is `GUI-49`. One the model works out unless told otherwise shows what it worked out with an
  *override* beside it —
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
  is keyed as its type proposes for one holding nothing, the first free taken. **A reference
  offers the items it may name**: those of its type, and where a field wants one category of
  them, that category alone, so a gas source's `cylinder` offers the cylinders and not the
  regulators its volume could never be taken from, and it offers to make one where nothing
  answers to what was typed, `GUI-48`. Open: the phone's form.
- **GUI-50 — What a card does with a name and with a field too wide for a column.** *Settled:*
  **the title is the name, so the body never repeats it; a paragraph spans both columns.**

  Every card is titled with its item's `name`, and every card then listed `name` again as its first
  field. A dive's read *Name 2026-06-21#0* under a heading saying 2026-06-21#0. So the body leaves
  it out. **A form keeps it where it can be written**: a site, a person and a piece of gear are
  renamed by typing in that box, and only a name the model calculates — a dive's, which is its date
  and its number within the day — is dropped from the form as well.

  **A field wider than a column takes the row.** Fields flow two to a row, and a paragraph of
  remarks in half of one is a ribbon four lines tall with a hole beside it. A card gives the row to
  long text; a form gives it to long text and to lists as well, a list there being a box to a line
  with an *add* under them rather than the one line of names a card reads it as. That is the same
  question `AT_FOOT` answers for the hundreds of dives on a site, at the other end of the scale:
  that list gets a box under everything, this one gets its row where it stands.

- **GUI-49 — How a reader is told where a value came from.** *Settled, for now:* **calculated is
  grey italics, corrected is bold, written is neither.**

  A field holds one of four things, and the four were drawn six ways across two tabs. A card greyed
  what the model calculated and the edit form did the same; the planner's runtime put a calculated
  duration in italics in a darker ink; the SAC calculator drew its answer in the ink of a number
  somebody had typed. Nothing distinguished a value corrected by hand from one written on a field
  that is never calculated, though the two are entirely different claims — *the model was wrong
  here* against *nobody calculates this*.

  So one rule, in one place. **Calculated is italic in `onSurfaceVariant`.** Italic carries it
  because it survives being greyed and a reader takes a slanted number in a box as an answer rather
  than a prompt; the ink is not the `outline` that labels are drawn in, a card otherwise naming a
  field and saying its value in one colour. **Corrected is bold in the ordinary ink**, which is the
  loudest of the four and belongs to the rarest: a number a user went out of their way to write
  over the model's. **Written is upright in the ordinary ink**, and is what everything else looks
  like. **Unreadable is the error colour**, upright, the reason standing where the value would be,
  because a sentence is not a figure the model arrived at.

  `calculatedOf` and `styleOf` in `Form.kt` are the whole of it, so the item view, the edit form,
  the runtime table and the four calculators cannot drift apart again.

  **A value being typed is not styled.** This says where a value a reader is *told* came from, and
  a box is for writing in: bolding the characters under a caret would be styling the act rather
  than the fact. A corrected field in a form is known by the *revert* beside it, which says the
  same thing and offers to undo it.

  **Statistics are left alone.** Every figure on that tab is calculated, and a page in italics
  throughout distinguishes nothing from nothing.

  **The settings form answers it the same way.** A setting nobody has chosen was typed into its box
  as though somebody had, and only the note beside it said *the default*. The box is now left empty
  and the default shows through it as a hint, italic, so the settings a reader has actually made
  are the ones in ordinary type. That also takes an ambiguity out of Save, which no longer has to
  work out which boxes are answers and which are the application repeating itself. Typing over the
  default chooses it and emptying the box gives it back, which is `GUI-41`'s rule already —
  *an empty box takes the choice away* — made visible.

  **The planner's own settings are not this.** Its boxes are a working copy of a plan being shaped
  rather than a reading of what is stored, and it carries no notion of chosen against defaulted to
  draw on. `GUI-43` seeds them and lets a reader type over them freely.

  *For now:* grey and italic together are two markers for one fact, kept while `GUI-8` is still a
  placeholder. Dropping one is cheap once that is answered.

- **GUI-48 — How an item that does not exist yet is named in a reference.** *Settled:* **the
  drop-down offers to make one, called by what was typed.**

  A buddy met today and a site dived for the first time are the ordinary way a logbook grows, and
  before this the only way in was to leave the form, make the item on its own tab, and come back.
  So every reference's list ends with one more line — *New dive site: "Blue Hole"* — which makes
  the item with that name and points the reference at it.

  **A site made this way names no region**, and the Locations tree lists a site under the
  regions it names, so it hung nowhere and a reader could not find what they had just made. The
  tree ends with *No region* where any such site exists, and that branch shows them whether or not
  a dive names one — *Hide unused* is for the supplied sites nobody has dived, not for the one
  made a moment ago. `GUI-25`, `GUI-26`.

  **The list does not take the keyboard.** A menu opens in a layer of its own and the platform
  hands the keys to whatever opened last, so a list that narrows as a name is typed swallowed the
  rest of the word: one letter opened it and the next went nowhere, which left the new-item line
  greyed and the box half filled. The reference's menu is opened unfocused, and every other menu
  in the window keeps the keyboard as before.

  **The line is there whether or not anything has been typed**, because a reader who has to know
  it is there has not been told. With nothing to call the new item there is nothing to make, so it
  reads *New dive site: type a name* and is greyed until the box holds a name nothing answers to.
  Offering it only once a name was typed was the first shape, and it hid the feature from whoever
  opened the list to look.

  **Keeping a name and making an item are two lines, where both are allowed.** A buddy, an
  emergency contact and a dive computer may be a written name and nothing else — a *one-off*, as
  `data/doc.md` has it — and a
  name typed into such a field used to stay one silently — so making an item and writing a word
  looked identical while they were being done. The list now says both: *Keep "John" as a name
  only* above *New person: "John"*. A field that takes no written name, a dive site among them,
  shows only the second, which is what makes the difference visible.

  **It is offered for every type but the dive.** Eight of the nine propose their id from a name,
  so a name is the whole of what making one needs, `LOGIC-23`. A dive is named for the day it was
  made on and has no name to give, so nothing offers to make one from a box.

  **The item is made at once, not with the form.** A reference has to point at something that
  exists, and the form's draft is not a place an item can live. Cancelling the form therefore
  leaves the new item behind, which is the same as pressing **+** on its own tab and is worth
  knowing rather than hiding.

- **GUI-28 — What a reference is on a screen.** *Settled:* **a link.** Wherever a field names
  another item, the name is clickable and opens that item where it lives: the tab holding its
  type, on the right subtab, with the item chosen — or, for a region, with the map on it — and
  the list scrolled to it where the tab has a list, the year holding a dive unfolded first,
  since a line in a folded year is not there to scroll to. A site sets the region as well, since the
  map would otherwise show wherever Locations was left: a site names several regions and none
  of them is *the* one, so the map goes to the most specific, which is the one with the
  smallest frame — Egypt rather than the Red Sea, Zeeland rather than the Netherlands. A wreck
  goes through the first site it lies at. That holds in every item view, so a site's
  dives lead to the dives and each dive's site leads back; the back-links the model works out
  are what make the second direction exist. The dive table's site column is not a link, since
  `DESK-7` gave a click there to the dive.
- **GUI-27 — What a tab keeps when it is left.** *Settled:* **everything.** The item chosen,
  the region, the box, which branches are unfolded, which subtab, where each list is scrolled
  to: a tab returned to is where it was left, because switching tabs to look something up and
  coming back to find the dive gone is the kind of small loss that makes an application feel
  like it is working against you. What each tab keeps lives above the screens, which come and
  go with the tab, and is one object per tab for as long as the application runs. The one
  thing not kept is a transient: where a section click is scrolling to, which is over by the
  time anyone could leave.
- **GUI-26 — What Locations shows of an atlas.** *Settled:* **what the dives touch, unless
  asked otherwise.** The atlas holds the world, and a logbook touches a few corners of it; a
  tree of every region to reach three of them is a tree of noise. With *Hide unused* on, which
  it is by default, a site no dive names is left out, a region with no such site at it or
  inside it is left out, and a region left with one child and no site of its own is cut out
  so the child takes its place — Europe with only the Netherlands left in it is not a level
  worth a click. Off, the whole atlas is there, which is where a new site is filed.

  *Extended:* **following a link unfolds the tree to what it chose, and scrolls to it.** The tree
  keeps which branches are open, and choosing a place left that alone — so a link to a site in
  Catalonia set the region, changed the card on the right, and left the tree beside it exactly as
  it was, with the answer folded four levels down. Every ancestor of the chosen branch is opened
  now, and the tree scrolls to the line: a tree unfolded towards something off the bottom of the
  list is the same failure one step later.

  **Its ancestors, and not the branch itself.** A reader who asks where a site is wants the chain
  down to it, not every region beneath it. And **the first path, not every one**: `GUI-22` puts a
  region under both its parents, so there are two chains to a Red Sea, and answering *where is
  this* twice is worse than answering once.

  ***Hide unused* is turned off where it would hide the answer.** A site nobody has dived is in no
  region the filtered tree shows, so unfolding towards a branch that is not there would leave the
  reader looking at the same tree again and wondering what the link did.
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

  It is also the shape Locations uses, a tree beside a list, so the application has one way of
  narrowing something down that is used twice rather than two ways used once each.
- **GUI-20 — How a wreck is reached.** *Settled:* **in the site list, marked apart from the
  sites.** A wreck is a ship rather than a place: it has no region and no position of its own,
  and a site names the wrecks that lie at it. So the list under a region is the sites in that
  region and the wrecks at those sites, which is one place to look for *what is there to dive*
  and lets a wreck be opened without first opening the site it lies at.

  Two things follow from a wreck having no place of its own. One at no site appears under no
  region, and one named by two sites appears under both, which is right where the sites are two
  moorings on one hull and misleading where they are not.
- **GUI-19 — How a dive trip is reached.** *Relocated to [desktop/doc.md](desktop/doc.md) as
  `DESK-7`.* A table with a spanning cell is a large screen's answer, and a phone will need
  another.
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

  **A tab takes the short form of what it names.** A keyed entry with no name of its own is
  labelled by what it points at, and where that carries an `abbreviation` it is the
  abbreviation: a person's courses read *OW*, *AOW*, *EFR* rather than a row of tabs whose ends
  nobody can see. The field exists for exactly this — *the short form it is usually known by* —
  and a tab is the shortest place an interface has. Its first letter is raised and the rest is
  left alone, a label beginning with a capital and the rest of an abbreviation being none of a
  label's business: a specialty written *nitrox* reads *Nitrox*, and `OW` stays `OW`.

  **A word from a vocabulary is a word rather than a name.** A file writes one in lower case
  with an underscore where a reader puts a space, and a screen reads back what it says, so
  `back_mounted` is *Back mounted*. Only a field with a set of words to draw on has words in
  it, so free text is left exactly as it was typed and a gas is `EAN32` rather than `Ean32`.

  **Where all of that goes on a screen is the form factor's**, and a desktop's answer is `DESK-5`
  in [desktop/doc.md](desktop/doc.md): the columns, the boxes, the tabs and the foot.

  **A list with nothing in it is not shown, wherever it sits.** A worked-out list always answers,
  so a region no site names has an empty list of sites rather than none, and it read *(empty)*
  on every such card where an absent field says nothing. An empty list says nothing either, and
  in the form a read-only one shows the dash an absent one does.

  **What a number reads as.** A file keeps three decimals of a metre so nothing measured is
  lost; a reader wants one. A depth, a temperature, a mass and a volume read to one decimal,
  a pressure and a density to none, and an angle as the file writes it, a coordinate being
  nothing to round. A time is seconds in the model and reads as minutes and seconds, `61:16`,
  however long, since a dive is quoted in minutes. An offset between two clocks is seconds too
  and reads as hours and minutes with a sign, `+2:00`, which is how a zone is written everywhere;
  it is typed the same way, or as a bare number of hours. `LOGIC-32`. Every other number carries its unit after
  it, once after a range and once after an average.
