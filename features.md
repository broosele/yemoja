# Features

What Yemoja is meant to do, and what it is meant to do *later*. This register exists so
that an idea can be set aside without being lost, and so that something already decided
against is not proposed again a year on.

It is not a plan and carries no dates. It says what is intended, not when.

## How to read it

Every entry has an identifier — `FEAT-3` — which is never reused and never renumbered,
so it can be referred to in discussion. Entries move between statuses; they are not
deleted.

| Status | Meaning |
|---|---|
| **Core** | Part of what the application is for. Without these there is no product. |
| **Planned** | Intended, and will be built. |
| **Future** | Wanted, not scheduled. Nothing depends on it. |
| **Low priority** | Wanted in principle, and may never happen. |
| **Rejected** | Decided against. The reason stays, so the decision is not relitigated. |

Design questions belong in the `doc.md` file for the layer they affect, not here. Where
a feature waits on such a question, it names it.

## Core

- **FEAT-1 — Log dives.** Recording a dive and everything about it.
- **FEAT-2 — Dive sites, people, gear, trips, operators.** The items a dive refers to.

The first version is a **local logbook**: dives and the items they refer to, in readable
files on one machine. Reading a dive computer and importing another application's file are
built on top of that. What moves data between installations — history, syncing, merging one
logbook with another — is intended and designed for, and none of it is what makes this a
logbook.

That is a decision about order, not about ambition. The design work behind those features
is done and recorded, and the file format must not foreclose them: see
[data/json/requirements.md](data/json/requirements.md), whose eighteen
requirements are settled and remain binding on the format even while nothing implements
them.

## Planned

- **FEAT-4 — Versioned storage in readable files.** Yemoja's own history, not a
  version control system underneath. See [data/json/doc.md](data/json/doc.md).
  Moved from *Core*: a logbook that cannot undo is still a logbook, and the journal is
  the largest single piece of machinery in the storage layer.
- **FEAT-5 — Sync between installations, and backup.** Through a location the user
  provides; see [data/json/requirements.md](data/json/requirements.md). Moved
  from *Core* with `FEAT-4`, which it builds on — merging is merging journals — and
  carrying conflict resolution with it.
- **FEAT-3 — Download from a dive computer**, over Bluetooth among other transports.
  Moved from *Core*: it is how most dives will arrive in practice, but a dive typed in
  by hand is a dive, and this brings a native library and per-platform Bluetooth with it.
  What a download carries and what it drops is decided, field by field, in
  [logic/divecomputer.md](logic/divecomputer.md), and reads on the JVM: a computer plugged in
  or advertising over Bluetooth LE is found, read from where the last download stopped, and
  reviewed like any other import. One computer has been read, over Bluetooth on Windows; USB
  and serial have met none, and the other four targets are untouched. What is owed is listed in
  that document rather than here.

- **FEAT-6 — Dive planning with decompression.** Carries the safety obligation recorded
  in [logic/doc.md](logic/doc.md). Moved here from *Core*: logging what you did
  and planning what you will do are separate jobs, and a logbook that cannot plan is
  still a logbook.

  It brings no type of its own: `DATA-57` in [data/doc.md](data/doc.md) settles that a plan is a
  profile marked `planned`, which is the same shape as a recording and compares against one.

  *Built:* Bühlmann ZH-L16C (`LOGIC-3`), the profile fields a plan needs, and two operations over
  them. `evaluate` says what a run costs — the ceiling, the time left, the gas each cylinder gives
  up, the oxygen clocks, how long before flying, and what it objects to (`LOGIC-37`, `LOGIC-38`).
  `completeAscent` writes the way out of one (`LOGIC-35`). `lostGasReserve` and `sharedGasReserve` say
  what gas a plan must still hold at its end, for gas lost or a buddy sharing it at the worst moment
  (`LOGIC-40`). In the window, both sit under the run
  they are about: the ceiling over its graph, and the figures and the findings under its fields.
  `GUI-40`.

  A plan is made in the Calculations tab, of any number of segments and cylinders, with the way
  up worked out as it is typed, a role for each cylinder, a safety stop and a gas reserve
  (`GUI-43`). It is saved as a new dive or on to one already there, and a saved plan is opened
  back into the planner from the Dives tab or from *Open plan*, which lists every plan in the
  logbook (`GUI-44`). A plan has a start and may follow an earlier dive or plan, and saves both,
  and it keeps every setting the planner had and the lines as they were typed (`DATA-129`).

  *Also built:* **the planner without a window.** `yemoja plan` reads a file of plans and writes
  what they come to, as a table or in full, opening no logbook — which is how a schedule is put
  beside a published table or another program's. `API-8`, and
  [manual/planning-from-a-file.md](manual/planning-from-a-file.md) for a user. An agent reaches
  the same calculation through `plan`, and stages one through `create_plan`. `API-9`.
- **FEAT-7 — Import from other applications' logbooks.** The reconciliation machinery is
  built; see [logic/reconciliation.md](logic/reconciliation.md). UDDF is read — the dive, its
  recording, and the sites, people, gear, trips and operators it names — and how far it
  goes is under *What is built* in [logic/uddf.md](logic/uddf.md).

  *Also built:* **Diving Log's own file**, for a user moving from it, read through a SQLite reader
  of our own (`DATA-130`). What it maps to, and what it leaves, is
  [logic/divinglog.md](logic/divinglog.md).
- **FEAT-8 — Statistics over the logbook.** Totals, counts and summaries, each reported
  with what it was based on.

  *Built:* any two things a dive answers for, plotted against each other in the Statistics tab —
  each dive, a count, a total, an average, the largest, the smallest, or a running total, grouped
  by whatever the user picks. `Figures.kt` counts and the Statistics tab draws it.
- **FEAT-9 — Renewal tracking.** Saying what needs renewing, across insurance, the medical and
  gear maintenance. The reason the validity work in [data/doc.md](data/doc.md) exists. What
  counts as *soon* is `LOGIC-7`, not a stored value: the data layer records when a thing falls
  due and this feature decides when that is worth saying.

  *Built:* the home screen warns of gear maintenance that has fallen due, and of the user's own
  medical and insurance, a month ahead (`LOGIC-7`). That is the whole feature: a separate list
  of every renewal however far off was offered and declined, since each item's own page already
  shows its dates.
- **FEAT-12 — A programmatic interface.** Another program driving the logbook without a person
  present. See [ui/api/doc.md](ui/api/doc.md). Moved from *Future* because `FEAT-18` is built on
  it. The tools an agent reads through were the part built first.

  *Built since:* the shape, `API-2` — functions that answer and functions that change, third
  parties and the agent alike, the agent's boxes deciding which it may use — and the planner as
  the first of them: `calculated` and `saved`, `yemoja plan` over a file of cases, and the agent's
  `plan` and `create_plan`. `API-7`, `API-8`, `API-9`. What is owed is a way for something outside
  the application to *write* — `saved` is built and only the agent reaches it. `API-3` now says how
  a command may open a logbook, and nothing blocks it but the work.
- **FEAT-18 — An AI agent over the logbook.** Asking for something in ordinary language
  and having it answered or done, where doing it by hand would be many reads or many edits:
  how often a stage richer than 36% was breathed, a clock error set across a trip, one dive's
  gear given to the others on it. The user brings their own agent and whatever account it
  runs on. Yemoja supplies no model, pays for none and holds no key. Desktop only.
  Moved from *Future* once its design was settled.

  Built on `FEAT-12`, so an agent reaches the data through tools over the Universe and gets
  no privileged path of its own. What an agent is given and what it is not is `API-4` and
  `API-5` in [ui/api/doc.md](ui/api/doc.md). How it is hosted and talked to is `GUI-38` in
  [ui/gui/doc.md](ui/gui/doc.md). Its changes are staged and reviewed like an import, which is
  `RECON-8` in [logic/reconciliation.md](logic/reconciliation.md), and its arithmetic is
  `LOGIC-34` in [logic/doc.md](logic/doc.md).

  *Built:* a panel beside the tabs starts the agent the user names, hands it the tools, and
  refuses a request of its own unless a box allows it: *Allow raw file access* for one that
  stays at the logbook's files, *Allow internet access* for one that fetches. A command it asks
  to run is always refused. It reads, and it stages a change where the user allows one. What it
  staged is reviewed in the System tab field by field, with anything the logbook has moved under
  marked before it is taken in.
- **FEAT-30 — Gas mixing.** What a cylinder holds after a top-up, and which gases to add to make
  a mix in a cylinder that is not empty: a half-used nitrox topped up with air, or a trimix made
  from oxygen, helium and air on top of what the last dive left. Worked out with real gases, since
  helium at 200 bar is nearly a tenth less gas than its pressure says and a blend filled by ideal
  pressures is off by several bar.

  *Built:* a Gas mix calculation in the window, on the desktop and on a phone. `LOGIC-45`,
  `GUI-56`. Not built: a temperature other than twenty degrees, a gas with argon in it, and the
  same question from the command line or an agent.

## Future

- **FEAT-11 — A terminal interface.** Deliberately raw; see [ui/tui/doc.md](ui/tui/doc.md).
  *Built, less one shape:* it reads a logbook, shows it, and changes it — a value typed
  over, an item or an entry made or deleted — saving each change as it is made. A series is
  what it cannot edit, nobody typing thousands of samples into a terminal.
- **FEAT-23 — Following a mention written in a remark.** `@willy` in free text names an
  item by convention, settled in `JSON-23`. *Built in the TUI:* opened, a remark's mentions are
  what up and down move over and space follows the one the cursor is on, and only those that
  resolve are marked. A rename carries them with it, which is `JSON-24` and waits on there
  being a rename at all.
- **FEAT-13 — Export to other applications' formats.** UDDF is written: the whole logbook,
  from the System tab, and read back by the importer. What goes and what is not written yet is
  under *What is built* in [logic/uddf.md](logic/uddf.md). No other format is written.
- **FEAT-31 — Plugins.** Adding tide sources, site libraries and tables without a new release,
  as files with a manifest, and making a new kind of extension one class to write. Built-in
  extension points and data plugins only, never code from others. How a plugin is got and
  installed, and the open question of where it lives, is `LOGIC-47` in
  [logic/doc.md](logic/doc.md).
- **FEAT-14 — Editing ids.** For advanced users, carrying the rename cost
  described in [data/json/doc.md](data/json/doc.md).
- **FEAT-29 — Navigating to a dive site.** A link on a dive site that opens it in a map
  application, Google Maps or the like, so the way there is one press from the logbook. A site
  already carries its `latitude` and `longitude`, so nothing new need be stored for the simplest
  form. Open: which service the link goes to, or whether the platform's own `geo:` link is used and
  the phone chooses; and whether a site wants a second position of its own, the car park or the
  entry point, since where the water is and where to drive to are often not the same place.
- **FEAT-28 — Getting a skipped dive back.** A downloaded dive the review was told to skip, or
  left undecided when the review was closed, is at present gone for good. The next download
  resumes after the newest dive the logbook holds, `DATA-90`, so a dive older than one taken in
  never comes across again, and nothing reopens what was staged in the `.import` folder. Only a
  full download from an emptied resume point would bring it back. Wanted: a way to see what was
  passed over and take it in after all. Open: whether the staging folder is kept and offered
  again, or the download resumes from the oldest dive not taken in rather than the newest one held.
  Within one session a finished download is offered again on each visit to Home, `GUI-52`; this
  is what lies past that.
- **FEAT-10 — A buoyancy calculator.** How much lead a dive needs, worked out from the kit taken
  and the water it is taken into: the suit, the cylinders full and nearly empty, everything else
  carried, in salt water or fresh. A calculator beside the others in the Calculations tab, and the
  same figure offered where a dive's `gear` is filled in. Moved from *Low priority*, where it was
  *weighting from gear buoyancy*. Gear already carries the `buoyancy` figures it would read and a
  dive already sums its `weight` from them, so nothing waits on it; what is not settled is which
  moment of a dive the answer is for — a diver is weighted to be neutral at the end, with the
  cylinders nearly empty, and that is the figure a table of ballast gives.
- **FEAT-27 — Gear sets.** A named set of gear — *drysuit kit*, *tropical kit*, *twinset* —
  given to a dive in one step rather than an item at a time, and kept as a set so a dive says
  which it was taken with. A logbook that dives two or three ways has the same eight items on most
  of its dives and fills them in by hand each time. Open: whether a set is an item of its own that
  a dive's `gear` names, or only a convenience that fills in the list and is forgotten; the first
  lets a set change and every dive follow it, which is wrong for dives already made.
- **FEAT-26 — Tides at a dive site.** When the water is high and low, and how hard it is running,
  for the day a dive is planned or was made at a site on the coast. Slack water is when a drift
  site can be dived at all, and a logbook that knows a site's tides could say whether a planned
  dive falls in it. *Built, in part:* a Tides calculation gives the high and low waters of a day
  and the curve between, at the tide station nearest a dive site, from Rijkswaterstaat's measured,
  forecast and astronomical series, which are CC0. `LOGIC-44`, `GUI-55`. That covers the
  Oosterschelde and the Dutch North Sea within fifty kilometres of a station, on the desktop and on
  Android, `AND-8`, with a network. A site on the Grevelingen or on any other lake is not offered
  one, having no tide.

  A dive site says nothing about tides: a calculator decides from the site's position whether it
  covers it, so the question of a field on a site for its station is closed by not having one.

  **The current at a site in the Oosterschelde** is built too, from Rijkswaterstaat's Scaldis-Oost
  model at fifty-eight places, most of them dive locations: how fast it runs, flood and ebb told
  apart, and when it is slack, from two weeks back to two days ahead. `LOGIC-44`.

  **Slack from the dive club CVD's table** is built for the seventeen of its forty-two sites that
  have a position, used with the club's permission: Kats' predicted high and low water moved by
  the minutes the club gives for the site, for any day. `LOGIC-44`.

  What is not built: **the other twenty-five rows of that table**, which wait on a position each;
  **the current anywhere outside
  the Oosterschelde**; **the Westerschelde and the Wadden Sea**, which are tidal and
  not outlined yet; **any water outside the Netherlands**, which waits on a worldwide or a
  regional calculator; and **a tide
  without a network**.
- **FEAT-25 — Scans of the papers a logbook stands on.** A photograph or a scan attached to a
  `person`'s `courses`, `medical` and `insurance`, and to a piece of gear's `maintenances`: the
  certification card, the doctor's certificate, the insurance card, the service receipt. Each of
  those already records what a paper says and a date it runs to; none of them records the paper.
  A diver asked to show a card on a boat has it on their phone rather than in a drawer at home.

  **It is the first thing in a logbook that is not text**, which is what makes it more than a
  field. Everything in [manual/data-format.md](manual/data-format.md) is readable and diffable,
  and an image is neither, so the files go beside the logbook rather than into it and a field
  holds the name of one. Where exactly, and whether a name is a path or an id, is the data
  layer's to settle.

  Three things it drags in, none of them hard but none of them free:

  - **Size.** `FEAT-5` moves a logbook between installations, and a folder of scans is the first
    thing that makes one large. Whether they travel with it, or are left behind like a cache, is
    a decision that belongs with that feature rather than after it.
  - **Privacy.** A scanned medical certificate is the most sensitive thing a logbook would hold,
    and `API-5` sends an agent everything it reads. What an agent does about a file it cannot
    read as text — and what it may do with one while *Allow raw file access* is ticked — needs an
    answer before the first scan lands, not after.
  - **Export.** UDDF has nowhere to put one, so an exported logbook loses them silently unless
    [logic/uddf.md](logic/uddf.md) says otherwise.

  Future rather than planned: nothing depends on it, and the three questions above are worth
  answering deliberately rather than in a hurry.

## Low priority

- **FEAT-16 — Libraries a user did not get from Yemoja**, so that a club's dive sites or
  its own words for things can be added without a new release. `DATA-25` settles that
  suggested values come from the field's description joined with what a logbook already
  uses, so a library would be a third source rather than a replacement for the first.

  What makes this more than a convenience is what `LIB-5` settles it *cannot* be. A user
  can already copy items into their logbook, which is how a supplied item is frozen — but
  absorbing a club's list that way loses which items came from it and any hope of
  replacing it wholesale when the club issues a new one. A third-party library therefore
  needs somewhere of its own to live, and the logbook folder is the only place that
  reaches every device: a setting cannot carry files to a phone. A layer of that kind was
  sketched and set aside under `LIB-5`; this is the want that would bring it back, and it
  would have to answer the question that sank it — where an edit to an item in your own
  library goes.
- **FEAT-17 — Maintenance measured in dives** rather than elapsed time, so that a
  regulator due every hundred dives says so instead of needing a date worked out by hand.
  `DATA-35` settles that validity is dates only for now, and that this remains additive:
  a maintenance with no dive limit simply has none. What it would need is a count of dives
  using a given item since a date, which is the first derived value on gear that reads the
  whole logbook rather than the item.
- **FEAT-22 — Argon and hydrogen in a gas mix.** UDDF's `mix` carries `ar` and `h2`
  alongside oxygen, helium and nitrogen; this model's gas notation names oxygen and
  helium only, so a mix containing either cannot be represented and is lost on import.
  Argon is a drysuit inflation gas more often than a breathing one, and hydrogen is
  experimental — neither is common enough to shape the notation now, and both are worth
  recording as known gaps rather than being discovered twice. See
  [logic/uddf.md](logic/uddf.md).
- **FEAT-19 — Units declared per item.** A `units` block inside a single item in a
  file holding several, so a metric and an imperial cylinder can sit in the same
  `gear.json`. Removed deliberately rather than never considered: a declaration now
  reaches only the file it is written in, because a resolution order the reader cannot
  see is a source of errors in a format meant to be read by hand — see
  [data/doc.md](data/doc.md). Low priority because the case is already
  expressible: any type may be stored as a directory of one file per item, and each
  file then says what it likes. The cost of reinstating it is that the rule stops being
  statable in one sentence, not that the code is hard.

## Rejected

Entries here keep their reason, so that a decision already taken is not taken again.

- **FEAT-15 — More than one logbook to hand.** Remembering recent logbooks and switching between
  them without hunting for a folder. Rejected: a user keeps one logbook, and the convenience is
  for a way of working the application does not set out to serve. What `JSON-8` settles stays
  true and costs nothing — any number of logbooks may exist on disk, one is open at a time, and
  *Open logbook* reaches any of them — but nothing is built around having several.

- **FEAT-24 — A state on a piece of gear.** Whether an item is new, in use, retired or sold.
  Considered against a real logbook that records one, and declined: a logbook is not an
  inventory, and neither reading of the word earns a field. Condition overlaps what
  `maintenances` already records, and standing answers a question nobody asked.

  What raised it is worth keeping, because it does not go away with the field. **Gear cannot
  be deleted once a dive names it** — a wetsuit named by forty dives would take them with it —
  so a kit list only ever grows. Whoever wants to shorten one will meet this again, and
  `remarks` is where it goes until then.

- **FEAT-21 — Closed and semi-closed circuit diving.** Rebreather support, and the data
  that comes with it: set, measured and calculated oxygen partial pressures through a
  dive, and the dive modes that distinguish a rebreather from open circuit. *Planning on a
  closed circuit is being built for plan files, and so for the web planner, in two steps*:
  the schedule on the loop first, `LOGIC-46`, then its gas, scrubber and bailout. The window
  offers none of it. Logging rebreather dives remains ruled out for the foreseeable future
  rather than for ever — the reason is scope, not principle. It is a different kind of diving
  with its own safety surface, and supporting it badly would be worse than not supporting it.
  UDDF carries all of it, so a rebreather dive imported from elsewhere will lose that data
  rather than be refused; `DATA-54` should record it among the fields deliberately not
  modelled.
- **FEAT-20 — Recording what things cost.** A purchase price on gear, a price on each
  service, and totals across them. Dropped from the data model rather than deferred:
  money is the one quantity that does not behave like the others. Every dimension the
  model handles converts by a fixed factor, which is what lets a value be kept as written
  and converted only when used; a currency has no base and no constant rate, so any total
  across currencies needs exchange rates and dates from outside — live external data an
  offline logbook has nowhere to get and no reason to hold. See `DATA-34` in
  [data/doc.md](data/doc.md). Tracking the cost of diving is a fair thing to
  want and belongs in something that is not a logbook.
