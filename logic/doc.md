# Logic layer

Everything the application *does*, independent of how it is stored and how it is
shown. There is exactly one implementation — no alternatives, no platform variants.

Every user interface talks to this layer and only to this layer, through the single
access point described below. A front end may *name* the item types, which this layer
describes and which are the vocabulary the whole application shares; reaching past this
layer for anything it *does* is a bug in the design.

## Scope

- **The item model.** The description of every item type — its fields, their kinds
  and units, and how each derived value is computed. The machinery that reads, writes
  and resolves against those descriptions is in [data](../data/doc.md); what a dive
  *is*, is here.
- **Logbook operations:** create, edit, delete dives, sites, buddies and gear, and
  linking them together. Not numbering: a dive's number is the user's own and nothing
  renumbers it.
- **Dive planning and decompression.** Given gases, depths and times, produce a
  schedule with ascent, stops and gas use.
- **Gear tracking:** service intervals, what is due, what was used on which dive.
- **Statistics and summaries** over the logbook.
- **Import from dive computers**, and import of other logbook formats — folded into
  the logbook by the shared machinery in [reconciliation.md](reconciliation.md).
- **Search and filtering** — the definitions of "recent", "deep", "with this buddy".
- Unit handling for *display and entry*: what a user is shown and what they type. A file
  declares what its own numbers are written in and the data layer converts on the way in,
  so a value held here is in the model's own unit — see [../data/doc.md](../data/doc.md).
  What a user is shown is a separate choice, and it is this layer's.

## Not in scope

- Presentation of any kind: formatting, layout, wording, navigation, colour.
- Storage details.

## Depends on

The data layer, for items and the machinery that reads and resolves them — see
[../data/doc.md](../data/doc.md). An `ItemSet` is constructed with the descriptions
this layer supplies, so the dependency runs one way and the layer below knows nothing
about diving. Never on a specific source either: it must be possible to run and test this
layer against items assembled in memory, with no files anywhere.

Two things sit here rather than below, and both for the same reason — they need to know
what diving is:

- **The descriptions**, and with them every derived value, from a region's `children` to
  `deco`. Structure and arithmetic were once reason enough to compute a derivation
  below, which made `deco` an exception needing a rule of its own; now the computation
  travels with the description and there is no exception to police.
- **Domain rules.** Two dives that overlap in time, a course made of somebody else's
  dives, a cylinder whose end pressure exceeds its start, a dive deeper than the user
  was certified for. The test is whether a thing can be checked without knowing the file
  is about diving; if it cannot, it belongs here. Structural checks — a date that parses,
  a reference that resolves, text without a line break — stay in the data layer.

  Because validation is permissive and nothing is ever refused, these rules do not reject
  anything. They produce findings, each aimed at an item and carrying a severity, which
  is what the home tab shows. A dive beyond your certification is not invalid data: it
  happened, and it is worth remarking on.

## Structure

```
logic/
  doc.md          this file
  build.gradle.kts  the module, which depends on data and on nothing else, and which
                    packages the supplied libraries into the application
  reconciliation.md  merging an import into the logbook
  uddf.md         UDDF against this model, field by field
  divecomputer.md  a dive computer against this model, the same way
  src/commonMain/kotlin/yemoja/logic/
                  Universe.kt   what is open, and the one door a front end reaches
                                  anything through
                  Change.kt     one thing a user did, and the parts it was done in
                  Naming.kt     what a new item should be called, which is a proposal
                  Types.kt      every type gathered, and nothing else
                  Dive.kt       one file per stored type, holding what it owns: a dive's
                  Person.kt       details, environment, gear, profiles and gas sources are
                  Gear.kt         here, a person's medical, insurance and courses here, a
                  DiveSite.kt     piece of gear's buoyancy and maintenances here
                  DiveTrip.kt   ... nine of them, and every field worked out from each
                  Certification.kt
                  Operator.kt
                  Wreck.kt
                  Region.kt
                  Vocabularies.kt  what more than one type is described with
                  References.kt    the far side of a reference, for children and parts
                  Recordings.kt    the walk into a profile that a dive's fields share
                  Expiry.kt        what has run out, for an insurance and a maintenance
                  Today.kt         what day it is, which four fields count against
  src/jvmMain/kotlin/yemoja/logic/
                  Today.kt      the machine's own date, a day needing a zone
  src/commonTest/kotlin/yemoja/logic/
                  the tests, beside what they cover
```

**One file per stored type, and a derivation sits with the type it belongs to.** A dive's
`average_depth` is written under the dive rather than in a file of derivations, so what a type
is and what is worked out from it are read together. An owned item goes with its owner: a
profile is only ever reached through a dive, and reading the two apart was reading half a
question.

**An owned item is declared above the type that owns it**, a property being unusable in a file
above its own declaration. So `Dive.kt` builds from the inside out — the tolerances, then the
profile that holds them, then the dive — and the dive is the last of its descriptions rather
than the first.

That order is what the file's shape costs, and what it buys is visibility. Nearly every
derivation is `private` now: `divesAverageDepth` is used by the description four lines above it
and by nothing else, which a reader can see without searching. Only the nine stored descriptions
and what genuinely crosses a file are `internal`.

`Types.kt` keeps `object Types`, which is the vocabulary a front end names. Each of its nine
members is the description written in that type's own file; the eleven owned types are reached
through the types that own them and are not named there.

The **Universe** exists, holding the part of what the section below describes that there is
anything to hold: the open logbook, and whoever it belongs to. `Universe.open` is the one place
in this layer that names a source, which is what makes `ui/doc.md`'s rule true rather than
aspirational — a front end names a folder and never opens one.

It was called `Logbook` while that was all it did. The rename is not a new capability: it is the
point at which a front end stops naming a stand-in, so that everything added to the Universe
later arrives where front ends are already looking.

**Every item type the manual names, and every shape a field can take.** A dive brings the
last of them: its profiles and its gas sources under keys, a profile's depth and temperature
against time, and its pressures one series per gas source.

**Every field the manual defines is described**, all two hundred of them, and every one it works
out is worked out.

A person's `name` is assembled from the parts; a dive's is its id, which the set it belongs to
answers for. `buddy_count` counts the list. A region's `children` and a trip's
`parts` are the far side of a reference, gathered by asking every item of that type what it
names — one walk serves both, since a region has many `parents` and a trip has one `parent`,
and each is asked the same question. A gas source's `volume` comes from the `capacity` of the
cylinder it names.

**A derivation that cannot answer says so.** `volume` pointed at a regulator, a dive whose
recording cannot be chosen, a trip that is its own ancestor: each is *unusable* rather than
absent, because a blank looks like a field nobody wrote and each of these is a mistake worth
seeing. `DATA-50`. Absent is kept for what is not a mistake at all — a rented cylinder nobody
has an item for, a dive with no recording, a trip nothing has been logged against yet.

**A dive's `end_date` and `duration` fall back to the dive's own times.** Everything else taken
from a recording has no other source, but these two do: a dive that starts on a date at a time
and ends at a time has ended on a day, and lasted from one to the other. The day is the day it
began, or the next one where the end time is earlier than the start — no dive runs for
twenty-four hours, so an end before a start is the following morning and nothing else. The
manual has said this under `end_date` since the field was defined; the code only caught up
once seventeen of the twenty fixture dives turned out to be logged by hand and to have neither.

Two limits keep it from guessing. **A recording still wins**, including one that cannot be
chosen — a dive holding several profiles and naming none is a question asked twice and settled
neither time, and answering it from elsewhere would hide that. And **a dive with no start time
gets nothing**: there is then nothing for the end time to be earlier than, so whether the day
turned is unknown rather than unlikely.

`DiveGear.weight` counts every item in the `weights` category and nothing else. A
weight-integrated harness is not one: its own mass is the pockets, and the lead that went in
them is a `weights` item of its own, so the category counts each block once and no harness
twice.

`Profile.density` was the last, and it moved a field rather than needing a table. A computer
never measures depth: it measures pressure and divides by an assumed density, so the maker's
figure is baked into every depth it wrote. Fresh is 1000 and `en13319` exactly 1020, both
fixed. Salt is whatever that computer was set to, which the gear item now records as
`salt_density`, falling back to 1030 where there is no computer, no item for it, or no figure
on it. Recording it on the item beats keying a table on `brand`: two models from one maker can
differ, `brand` is open text nothing constrains, and a user who looks their computer up can
simply write the number.

It is absent where the recording does not say what it was set to, which is every profile
imported from UDDF. `DATA-59` calls that the sharpest gap in the mapping: UDDF keeps the
converted depth and discards the conversion, and its own `density` is a fact about the site
rather than about the device.

**One derivation reaches outside the logbook, and only one.** Every other is a pure function of
the items: the same logbook answers the same way forever. `days_left` and `expired`, on an
insurance and on a maintenance, are counted against today, so they change overnight with
nothing edited. That is survivable because `DATA-6` already has nothing announced and whatever
shows a worked-out value asking again. `LOGIC-9`.

They ask `today()`, which reads the machine's own calendar date and needs nothing from
anybody. It is per platform because a day needs a zone and the standard library carries none:
its clock answers with an instant, which is the same moment everywhere and so a day nowhere.

[manual/data-fields.md](../manual/data-fields.md) is the source of truth for every field,
and where it and the descriptions disagree the manual is right. `tool/checkdata.py` holds the
two together — on which fields exist, what kind each is, the order they are declared in, and
the order of the types themselves. See [testing.md](../testing.md).

## The Universe

Everything above this layer talks to one thing: the **Universe**. It is the application's
working state and the single door through which a front end reaches anything at all.

It holds one or more sets of [items](../data/doc.md) — normally the open logbook, and
during an import the candidates as well, each complete and resolvable in its own right.
Reconciliation is then a comparison between two sets rather than a special mode, and an
item is never without the items it belongs to.

Over that it offers what the data layer deliberately does not: statistics, decompression,
and the domain rules below.

Two things to hold it to, or it will quietly become the whole application:

- **It delegates and implements nothing.** A statistic is computed by a statistics
  service and handed on. The moment one is written inside the Universe itself, everything
  else follows it there.
- **It does not re-expose the data layer method by method.** An `ItemSet` is reachable
  *through* it rather than wrapped by it. Hand-written forwarding is boilerplate that
  drifts apart the first time somebody adds to one side and forgets the other.

**What is built is the state, and two things over it**: `logbook`, `user`, `open`, `change` and
`suggested`.
The rest of what the section describes — statistics, decompression, the domain rules — is a
service beside the Universe rather than a method on it, and `LOGIC-1` decides how those are
arranged. It is untouched by `change`, which is not one of them.

**`change` is the one way anything in a logbook changes**, and it is shaped as the changeset
`data/json/doc.md` describes: an operation, and the actions that carried it out. That is the whole
reason it exists now rather than when something needed it. When `FEAT-4` arrives a changeset has
to be recorded for every change, and a front end reaching past this to an item's own `write` would
leave nothing to record it from. One choke point costs nothing today and is the difference between
adding history later and rewriting everything that edits.

**`suggested` is the other half of `DATA-25`.** A field carries the words the application ships
with; the Universe joins them with the words this logbook already uses, so a category typed once
is offered from then on and a second spelling of it is visible beside the first rather than
hidden. Only the Universe can do it, being the only thing that knows what is loaded.

It is one walk of the logbook, kept against the revision, gathering every field at once rather
than one per question. That is not an optimisation of a rare call: most fields that suggest
anything sit on an owned item — a profile's model, a maintenance's type — so answering for one
costs the same walk as answering for all, and a front end asks on every repaint.

Values pool by field rather than by vocabulary. `type` and `follow_up_type` on a maintenance ship
with the same words and are still two fields, so a word typed into one is not offered for the
other. Pooling them would mean matching sets by their contents, which is a coincidence rather
than a statement.

The presets keep the order they were declared in and what the logbook adds follows,
alphabetically. `none, light, moderate, strong` is a scale somebody chose and sorting it says
`light, moderate, none, strong`; the words in use have no order of their own to keep, so they
take the one a reader can predict.

**An import is a second logbook, and `Import` is the review of one.** Items arriving from
somewhere else are written to a folder of their own and read back from it, so reviewing them
uses what already reads, edits and saves a logbook rather than a preview path of its own. The
whole of it goes in through one `change`. [reconciliation.md](reconciliation.md) holds the
reasoning; `RECON-1` and `RECON-2` are what it settled.

Three things `change` guarantees, and one it does not.

- **It lands whole or not at all.** Every part is judged before any is applied — an added item is
  built and its fields read, an id is minted — so a refusal leaves the logbook as it was. `REQ-2`
  restores a changeset whatever it holds, and something that could half-happen would not be one.
- **Saving is immediate.** There is no unsaved state and no save to forget, which is what makes
  every change the journal's unit rather than only the ones somebody remembered. It also keeps
  `RECON-1` about reviewing an import, which is the only place staging earns its machinery.
- **An owned item needs no address.** A change names the item it touches, and one inside another
  is reached by whoever walked there; the file written is the referenceable item that owns it.
  The dotted path the journal uses — `medical.body_mass` — is how an address is *written down*,
  and it arrives with the thing that writes it.
- **A deletion leaves references dangling**, and says so rather than hiding it: a reference naming
  nothing is a state the model carries and an interface shows, and it is the same state as a
  person not entered yet. `Delete` takes `alsoReferences` for the case where the deletion is meant
  to leave no trace, and even then it reaches only references — a mention in free text is prose,
  `JSON-23` gives it no fixed meaning, and clearing one would be editing what somebody wrote.
- **The files are not atomic together.** A change touching two of them can be interrupted between
  them. `JSON-25`.

Two of the things named above are absent and each waits on something. **An import's candidate
set** is the second thing the Universe is meant to hold, and waits on `RECON-2`. **The units a
user wants shown** belong here by `UI-2` and wait on somewhere for settings to be read from;
until then a front end formats in the model's own units, and a change is judged in them.

**A new item is named here, not by whoever asked for it.** `ItemDescription` carries a
`proposedId`, and the caller of `change` gives what the item holds rather than what it is called:
a front end may name a type and may not describe one. The proposal runs against the built item,
which works because an item holds no id — one is made, asked what it should be called, and added
under the answer.

Seven types propose from `name`, a dive from the day it began, and an item with nothing to go on
falls back to `unknown_person`, which the manual promises. The Universe then takes the first id
free from the proposal: index zero is left off unless the type proposes one, so a second Anna is
`anna#1` and a second dive that day is `2026-04-28#1`. **The lowest free, so a deleted item's id
comes back** — deleting a dive entered wrongly and entering it again heals what pointed at it,
which is what dropping the rule against reuse was for, and what it costs is that a reference to
something deleted can come to name something else.

`DATA-84` puts this cost here on purpose: a name in an alphabet an id cannot carry falls back
rather than being mangled, and the `name` field keeps the real spelling.

## Decompression

Decompression calculation is the one part of this project where a mistake can hurt
someone. It gets treated differently from everything else:

- Isolated behind its own boundary, with no dependency on storage or UI, so it can
  be tested exhaustively and compared against reference schedules.
- No change without tests.
- Whatever the app shows is a planning aid, never a substitute for a dive computer
  or for training. That framing is a requirement on the UI layer too.

## Open questions

To settle when we discuss architecture and features:

- **LOGIC-1 — Shape of the interface.** One service per subject area, a single facade, or
   use-case objects? This determines what the API and TUI front ends look like.
- **LOGIC-2 — Where the device-facing half of a download lives.** *Settled:* **a port the
   logic layer declares and each target implements, carrying a neutral recording.**

   Reaching a device needs a native library and platform Bluetooth, which this layer should not
   have. What crosses the boundary is a `Recording`: a set of readings about the dive as a whole
   and a walk of what was recorded through it. That is what a dive computer *is* rather than what
   one library calls it, so the half below is only *get the bytes off the device* and everything
   above is written once.

   **Three things follow, and the third is why.** The mapping — the part with all of
   [divecomputer.md](divecomputer.md)'s decided detail in it — is written once rather than once
   per target. A recording writes to a file, so every stage above the port is testable with no
   device and no Bluetooth, which is the difference between a feature that can be regression-
   tested and one that can only be tried. And it is buildable without a C toolchain, an Android
   SDK or Developer Mode, none of which this machine has.

   The alternative considered was putting the port in the data layer beside `FileStore`, which
   `DATA-86` makes a precedent for. What decided against it is `reconciliation.md`'s own
   distinction: a **repository** is where the logbook lives and an **importer** is where items
   come from, and the two were named apart precisely to stop a device being treated as the first.

   **Values cross in the model's default units** — metres, bar, Celsius, litres, seconds — and
   converting into them is the implementation's, so no arithmetic is written three times.

   **Two decisions do cross the port and are written per target.** `LOGIC-13`'s four deco types
   and `LOGIC-16`'s twenty-six events are translated in the library's own vocabulary, which the
   port deliberately keeps out, so each implementation carries those two tables. The cost is
   accepted for what it buys — a port that names no library — and it is bounded: a wrong word
   lands in a fixed set and reads back *unusable*, not as a plausible lie.
- **LOGIC-21 — Where a download's drop report goes.** `LOGIC-10` settled that a value this
   model has no field for is dropped *and that the download says what it dropped*. The first
   half is built and the second is not: nothing collects the saying.

   What is dropped is known as it happens — a device field with no home, a sample type this
   model does not keep — so the question is what carries it and who reads it. A value the
   download answers with, beside the items it made; something the review shows before anything
   is taken in; or a line in the journal `FEAT-4` will keep, which is where *what happened* is
   meant to live. The three are not exclusive and the cheapest is the first.

   Whichever it is, it bears on `TUI-8`: a download that says nothing while it runs and nothing
   when it finishes is the same silence twice.

- **LOGIC-22 — How the characteristics a Bluetooth LE device talks through are found.**
   *Settled:* **by looking, not by a table.** libdivecomputer knows which advertised names are
   which model and nothing about the connection behind them; the application hands it a stream
   and has to know where the bytes go. The two ways of knowing are a table — this vendor, this
   service, this characteristic — or a rule applied to what the device offers once connected.

   The rule: take the vendor's own service, never one the SIG defined, that has a characteristic
   which is written to and one which notifies — one characteristic doing both first, then two
   within one service. That is the shape nearly every dive computer has, because nearly every
   one is a serial line dressed as a GATT service.

   A table would be better where the rule is wrong, and there is no honest way to write one. No
   vendor publishes its UUIDs, and the one implementation that has collected them is GPL and off
   limits (*Provenance*, in the working agreement). So the rule stands, and where a device
   defeats it — two vendor services, or a service that wants a PIN before it talks — the answer
   is an entry learnt from that device itself, read off it with a Bluetooth scanner, and recorded
   here with the device it was learnt from. None exists yet.

   Proven on one. A Shearwater Perdix 2 offers two SIG services and one of its own with a single
   characteristic that both writes and notifies, and the rule picks it. Every other vendor is
   still the rule's guess, and what is tested is that it picks what it says it picks.

- **LOGIC-23 — How a profile names its computer.** *Settled:* **by serial, worked out rather
   than written.** A download writes the device's serial on the profile and nothing else about
   which computer it was. `dive_computer` is derived from it, as the gear item carrying the same
   serial, and a written value overrides that the way a dive's written start date overrides its
   recording's: for a borrowed computer, or to say the derivation is wrong.

   **The key follows the same rule.** A profile is filed under the gear item its serial named,
   so reading one computer twice writes one profile rather than two, and a dive worn with two
   computers keeps one profile apiece under names that say which. Where the logbook keeps no
   item for it, the device's own name, which at least says which one it was.

   Two things follow. A logbook whose profiles name a gear item by hand keeps working and
   resumes, because the fingerprint to resume from is found through the serial either way — a
   profile carrying it, or one naming the gear item that does. And a download from a computer
   nobody keeps an item for shows no computer on its profile, only the serial and the key the
   recording was filed under. That is the honest answer: a brand and a model are not which
   computer, which is `LOGIC-20`'s reason for letting nothing but a serial name one.

   The library has the serial as a number and a maker prints it as they please, so the download
   writes the number in decimal and the comparison meets the maker halfway: case and punctuation
   do not count, and a spelling with a hexadecimal letter in it is read as the number it is. What
   a user copies off the device's screen then matches what the device said, bar a hexadecimal
   serial that happens to hold only digits.

- **LOGIC-24 — How a computer that guards itself is read.** *Settled:* **the code is asked
   for while the device shows it, and what the device hands back is kept on the gear item.**
   Some computers — the Aqualung i330R and its Apeks twin are the first the library knows —
   show a code on their own screen when an application connects and talk only once it has been
   typed. The library's driver asks the application for three things through the stream: an
   access code kept from last time, the code being shown where there is none, and, once the
   code has been accepted, an access code to keep. So the download is handed a *session* to
   ask rather than answers to use: where to stop, which needs the serial; the kept access code,
   which needs the device; the typed code, which needs a user at a keyboard; and somewhere to
   put the new one.

   The access code lives on the gear item beside the serial, because it is the same kind of
   fact — this logbook's standing with this one device — and a file on one machine would leave
   the next machine asking again. Written as hexadecimal, like the fingerprint, and not for
   reading. A download writes it, once the device has said its serial and a gear item carries
   that serial: before then there is nowhere to put it, and the code is asked once more next
   time. So a new computer is set up in two downloads — one to learn its serial and put it on
   the gear item, one to be given the access code — which is the cost of `LOGIC-23` naming a
   computer by nothing but its serial.

   The driver asks for the kept access code before the device has said its serial, so the
   lookup then goes by the advertised name: the gear item whose serial the name's digits
   spell. That is how a Pelagic computer advertises itself, as far as one scan has shown, and
   it is unproven on the device that needs it.

   A front end supplies the asking. A terminal reads a line; a screen without a keyboard, or a
   test, supplies nothing, and such a computer is not read there rather than read wrongly. The
   download is then given up rather than failed, which is the difference between a user who
   pressed escape and a device that would not talk.

- **LOGIC-7 — What counts as due soon.** The data layer records `valid_until`,
   `days_left` and `expired`, and stops there — `expired` is a fact, "needs renewing
   shortly" is a judgement. This layer decides the judgement, and `FEAT-9` is what wants
   it: one list answering what needs renewing.

   What makes it more than a threshold is that one number is wrong everywhere. A month's
   notice suits a regulator service, is derisory for a five-yearly pressure test, and
   means nothing for a medical without knowing whether the user is diving next week or
   next season. Candidates: a share of the original interval, which scales itself since
   the interval is known from `date` to `valid_until`; a setting the user chooses; or a
   per-obligation figure. Relocated from `DATA-36`.
- **LOGIC-8 — What a walk does when it meets a cycle.** Three fields point at their own
   type — a region's `parents`, a dive trip's `parent`, a certification's `supersedes` —
   and nothing prevents a chain that returns to its start. It is not refused on write:
   `DATA-17` in [../data/doc.md](../data/doc.md) records why, and that the data layer
   never walks, so this is entirely here.

   **Answered per derivation rather than once.** What a cycle means depends on the field:
   an ancestry that will not terminate is a caption that cannot be written, a trip whose
   `dives` gathers from its descendants would count some twice or never stop, and a
   qualification that supersedes itself is a different absurdity again. Each derivation
   knows what it is for and decides.

   What is common to all of them is that a walk must terminate whatever the data says, and
   that `DATA-50` gives the result somewhere to go: a derived value that cannot be worked
   out is *unusable*, not absent, and a cycle is always a mistake worth reporting rather
   than quietly surviving. Relocated from `DATA-17`.
- **LOGIC-3 — Which decompression model(s)** to support, and whether the model is pluggable.
- **LOGIC-4 — State and lifetime.** Is this layer a stateless set of operations over the data
   layer, or does it hold a live in-memory logbook that UIs observe? Which layer holds
   the loaded items is settled — the data layer does, as an `ItemSet`, see `DATA-20`
   in [../data/doc.md](../data/doc.md) — but whether the Universe above them is live
   is not.

   *Observe* needs care now: the toolkit has a state model of its own, and an interface
   that rebuilds when what it read changes. `DATA-6` settled that nothing below announces
   anything and a view that may be stale asks again — which fits that model rather than
   fighting it, and is worth checking before anything here grows a subscription.

   `LOGIC-5` narrows this considerably: with one operation at a time there is nothing to
   race, so *live* costs only what it costs to hold, not what it costs to protect.

## Settled

- **LOGIC-34 — How a figure is taken over items somebody else chose.** *Settled:* **over ids, a
  field and an operation, and the answer says what it was based on.** The operations are a count,
  a sum, a minimum, a maximum and a mean. The answer gives the value, how many values went into
  it, and each id left out with the reason. A dive with no `sac` is left out rather than counted
  as nothing, which is `GUI-9`'s rule applied to a caller that is not a screen.

  The first caller is an agent's `aggregate` tool, `API-4`, which chooses the items by reading
  them and hands this the arithmetic. It is a service beside the Universe, as every statistic
  is, and the first piece of `FEAT-8`.

  **The mean is named rather than chosen for the caller.** Within one dive `LOGIC-33` weights by
  time. Across dives a plain mean counts a twenty-minute dive like a ninety-minute one, and a
  weighted one counts the long dive more. Both are fair answers to *what is my average SAC*, so
  the answer says which mean it is, and a user who wanted the other asks for it. Which weighted
  means are offered beside the plain one is not settled.

- **LOGIC-33 — How a SAC is worked out.** *Settled:* **a series on each recording, and a figure
  on each gas source over the time it was breathed, in litres a minute.**

  A recording's `sac` is worked out between each two pressure readings of a source, where that
  source was being breathed the whole way: the drop in bar times the source's `volume`, over the
  minutes between, over the ambient pressure in bar at the depth averaged along the line between
  samples. The switches say what was breathed; with none, the only source is. A stretch holding a
  switch is left out rather than shared, since the drop in it is not all this cylinder's doing.

  A gas source's `sac` is the gas it gave over all the stretches it was breathed, divided by those
  stretches' minutes each weighted by its ambient pressure. That is the series averaged over time
  rather than a mean of its points, which would weigh a thirty-second stretch like a ten-minute
  one. It comes from the primary recording, and is overrideable, since a dive with no pressures
  logged still has a SAC somebody worked out by hand.

  **Litres a minute rather than bar a minute**, because a figure in bar belongs to one cylinder
  size and cannot be compared between a twin set and a stage; litres can. That needed a unit the
  model had no dimension for, and `flow` was added to `DATA-8`.

  **What it assumes.** An ideal gas, which undercounts what a full cylinder holds by a few
  percent, as every logbook's SAC does. Water of the recording's `density`, and of 1020 where it
  gives none, the nominal figure most computers convert with. Air of the dive's
  `atmospheric_pressure`, and sea level where it gives none. None of these is written anywhere:
  change one and the figure follows.
- **LOGIC-32 — What a dive's time means, and where the clocks' differences are kept.**
  *Settled:* **a dive's times are local, the dive says how far local time was ahead of GMT, and
  each recording says how far its clock was out.** It replaces `LOGIC-11`.

  **Two offsets, because two different things go wrong.** A dive has a `time_zone_offset`: how
  far local time was ahead of GMT where it was made. A recording has a `recorded_time_offset`:
  how far the computer's clock read ahead of that local time, which a user sets for a clock that
  drifted, one left on home time abroad, or one that missed summer time. The single
  `gmt_offset` before them was both at once, and the two do not belong to the same thing: a zone
  is a fact about where the dive was, and a clock's error is a fact about one computer, so a dive
  on two computers has one zone and two errors.

  **The times shown are local**, which is what a diver logs and what every logbook already
  held. A recording's start less its `recorded_time_offset` is the dive's start, and nothing
  about the zone moves it. **One clock is needed only to compare two dives**: a surface interval,
  and whether a downloaded dive overlaps one already held. There each dive's local time has its
  own `time_zone_offset` taken off.

  **A dive that says nothing about its zone is taken to be on GMT.** No computer read so far
  reports a zone, so every dive a real logbook holds says nothing, and two such dives compare as
  their local times do, which is right unless a zone was crossed between them. The alternatives
  were to leave everything needing one clock unworked until an offset is written, which empties
  every surface interval a logbook has, and to inherit the previous dive's, which is a guess that
  is wrong on the first dive home.

  **A download writes the zone where the device reports one, and never the start.** The device's
  `timezone` is its clock against GMT, which is local time against GMT where its clock was set,
  so it lands on the dive. Its unit and sign are still the unverified pair `LOGIC-11` named. The
  start is left for the recording to give: written on the dive it was a stored value, and a
  `recorded_time_offset` set later could not move it. Nobody is asked anything during a download.

  UDDF writes a zone after a `datetime`, `+02:00`, and it is read as the dive's offset and
  written back from it. A screen reads and takes either offset as hours and minutes with a sign,
  `GUI-16`. **Both are housekeeping**, `DATA-115`: the times shown already have them applied, so
  a card leaves them off, and the form is where they are set.
- **LOGIC-31 — What a download writes about the gas a dive began on.** *Settled:* **the
   switch the computer reports on the first sample, kept whatever it names. What is dropped is a
   reading naming the gas already being breathed.**

   A Shearwater reports the gas on the first sample of every dive. It looks redundant — the
   dive plainly began on something, and the gas list holds it — and the first attempt at this
   dropped it as a switch that changed nothing, reading the first gas source as the one begun
   on.

   **That was wrong, and the data layer had already settled why.** A collection has no inherent
   order; where an order matters it is worked out from the contents, and
   [../data/doc.md](../data/doc.md) names this very case: gas sources are ordered *by when they
   were first breathed, which comes from the profile rather than from the entry*. Nothing may
   lean on the order entries happen to sit in. So the gas list cannot say which came first, the
   profile has to, and the switch on the first sample is the only thing that does. Dropping it
   threw away the one record of it and left the fact resting on a position the model promises
   nothing about.

   It also mattered beyond the reading. A key is minted from what a source was for or from the
   slot a computer counted it in; a user adding a stage to a dive gets an entry wherever it
   lands, and under the dropped rule that could silently change which gas the dive began on.

   **A repeat is still dropped.** A reading naming the gas already in use says nothing: it is
   not a change, and the gas in use is known because the reading before it said so. That is what
   a dive cut in two brings along — `LOGIC-25` glues the stretches, and the second stretch
   opens by reporting the gas again. One dive in the logbook this was measured against carries
   exactly that, a switch onto `tank_1` five minutes in with `tank_1` already being breathed.

   **A computer reporting no switch at all leaves the series out**, and then which gas the dive
   began on is not recorded. `LOGIC-29` still takes the first slot as used for the purpose of
   deciding which slots to write down, which is a different question: it asks whether a cylinder
   was dived, not which one was breathed first, and it is an assumption stated as one.

- **LOGIC-30 — What a reading does about the surface a recording ends with.** *Settled:* **it
   is cut off at the surfacing, in both readers.**

   A computer does not end a dive the moment a diver reaches the surface. It waits, in case the
   diver goes back down, and how long it waits is a setting. The logbook this was measured
   against holds both halves of that: one computer wrote about eight minutes of floating onto
   the end of every dive until the setting was changed, and about two onto every dive after.
   Those minutes are in the profile, so a graph of an hour's dive spends a seventh of its width
   on a flat line at nothing, and what is read off the recording — when it ended, how long it
   ran — describes the wait rather than the dive.

   **A recording ends at the surfacing**: the last sample below a metre, and the one after it,
   which is kept so that the depth series still runs to the surface rather than stopping on the
   way up. Everything past that goes. Whole samples go, which takes every series with them: a
   pressure read on the boat and an alarm that sounded afterwards are as much the tail as the
   depth is.

   **A metre, and the evidence is good** — unlike `LOGIC-25`'s ten minutes, which had nothing to
   be fitted to. That logbook holds 428 profiles carrying a depth series, 178 of which also
   carry the device's own figure for how long the dive was. The surfacing found this way and
   that figure agree within a minute in every one of them, within thirty seconds in all but one,
   and exactly at the median. They are worked out from different things — one from the depths,
   one from whatever the computer was counting — so agreeing that closely is the rule landing
   where the computer says the dive ended. Half a metre picks nearly the same sample; a metre
   and a half starts eating the ascent.

   **Nothing is cut unless it is known to be at the surface.** A depth nobody recorded, or one a
   file writes in a way that will not read as a number, is not evidence of floating and stops
   the cut where it stands. A recording that never went below a metre is left whole, nothing in
   it saying where a dive ended.

   **What the device said about the dive as a whole is untouched.** Its duration, its greatest
   depth and its average are the device's own figures and `LOGIC-19` already prefers them to the
   recording; the measurement above is what says they need no correcting, the device's duration
   ending at the surfacing already.

   **The cut comes before the stretches are joined**, so that what `LOGIC-25` measures between
   two of them is the surface a diver spent rather than the wait a computer was set to. Until
   this, a computer set to wait eight minutes could have a pair glued whose real surface was
   eighteen.

   **Only the tail is cut.** A dive that surfaced in the middle keeps that, it being diving
   either side; and there is nothing to cut off the front, a computer beginning a recording when
   the diver goes down.

   Both readers do it, a file being as free as a device to keep recording once the diving
   stopped: a download, and a UDDF document.

- **LOGIC-29 — Which of a computer's gas slots a download writes down.** *Settled:* **the ones
   something used, unless there is only one.**

   A computer reports every slot it has. A Perdix asked for three on a dive made on one
   cylinder: the two switched off look exactly like the one that was on, because nothing in the
   library says which is which. A gas carries its mix and a usage that is about rebreathers, and
   a tank its volume and its pressures; neither has a word for *enabled*. A slot that is off and
   a stage carried and never breathed are the same handful of fields.

   So what is written is what was used: a slot a pressure was read from, or one the diver
   switched to. Volume would have told them apart and does not, since a computer is not normally
   told how big a cylinder is.

   **A dive begins on a gas, and that gas was used.** A Perdix reports a switch on the first
   sample, which says which one and is counted like any other. An i330R reports no switch at
   all, so nothing says which, and the first slot is taken to be the one it began on: a gas list
   begins with the gas dived. Without that, every recording from a computer that says nothing
   about gas would arrive with none, which is 49 of the 353 in the logbook this was measured
   against.

   **Where there is only one slot it is kept whatever it says**, there being nothing to choose
   between.
- **LOGIC-28 — What the application says when it cannot read a dive computer.** *Settled:*
   **the port says whether reading is possible at all, and the two cases are said apart.**

   Nothing within reach and no library to look with read the same from above: `found()` answers
   with an empty list either way. So a front end said *no dive computer is within reach* to a
   reader whose computers were on the desk and whose library was missing, which is a message
   that misleads exactly half the time and sends its reader to look at the wrong thing.

   `Devices` now says whether a dive computer can be read here at all, which every
   implementation knows and none was asked. It is true unless something says otherwise, because
   anything that can list dive computers can read them; only an implementation leaning on a
   library that may be absent says no. That keeps the port naming no library, which is
   `LOGIC-2`'s whole point: *can this read one* is a question about the port rather than about
   what is behind it.

   A front end then has two things to say. One names a computer to switch on; the other names
   a library to install. Neither guesses.
- **LOGIC-27 — Where libdivecomputer is when the application ships.** *Settled:* **beside the
   application, in a folder of its own, found by looking rather than by being told.**

   An installation is `bin` beside `lib` beside `native`, and the application asks where its own
   jar was read from and looks one folder over. Nothing is set where something already has
   been, so a user who keeps the library elsewhere says so once with `jna.library.path` and is
   obeyed, and a run from the build, where there is no installation to look beside, is told by
   the build instead.

   **It stays a file a user can see and replace**, which is what the licence wants of it: the
   library is LGPL and this project's own terms depend on it staying separate and replaceable.
   README says so under *Licensing*. That is what ruled out the tempting answer, which was to
   put it inside the jar and let JNA unpack it: one artefact ships, no path is set, and the
   library is buried where nobody can swap it. It would also have needed unpacking the two
   libraries this one depends on, USB and HID, which JNA would not have done by itself.

   Also declined: **expecting it on the machine**, which is ordinary on Linux and on Windows
   means asking a diver to build a C library; and **a platform installer**, which is the better
   answer eventually and is a great deal of build machinery for a thing that is not yet
   released. An installer places the same file in the same place, so it grows out of this
   rather than replacing it.

   **A build on a machine that has no library writes none**, and such an installation reads no
   dive computer. That is the honest outcome and the reason `LOGIC-28` matters: it should say so
   rather than claim nothing is within reach.

   The phones are a separate question. Android packages a shared object per processor type
   inside the package, and iPhone has no ordinary place for a third-party dynamic library at
   all, which is where the licence position and the five targets meet.
- **LOGIC-26 — What a new logbook is made of.** *Settled:* **a folder with a manifest in it,
   declaring every library the application ships and naming no owner.**

   A logbook is a folder, and what makes one a logbook is the manifest at its top; everything
   else a logbook holds it gains by being used. So making one writes that file and nothing
   else, and the folder comes into being by the writing of it.

   **It declares every shipped library** so that a new logbook knows the world's regions and
   the agencies' certifications from the first minute, without anybody finding out that a file
   has to be edited to get a map. The list is an order of preference rather than a permission,
   as [../data/libraries.md](../data/libraries.md) has it, so declaring all of them costs a
   reader nothing and one who wants fewer prunes the list.

   Nothing here lists what ships. A folder under the library root named after an item type
   holds libraries of that type and each file in it is one, which is the whole of the
   convention and is enough to enumerate them. A library file sitting loose rather than in
   such a folder cannot say what type it holds, so it is not declared; `generic_gear.json` is
   the one there is, and a logbook that wants it names it by hand. Making the shipped set
   describe itself would settle that, and is not done here.

   **It names no owner.** Which of a logbook's people is the user is something to say once
   there are people, and a new logbook has none. The greeting says as much, `GUI-30` in
   [../ui/gui/doc.md](../ui/gui/doc.md).

   **A folder that already holds a manifest is refused** rather than written over: that is a
   logbook, and opening one is a different act from making one. Whatever else is in the folder
   is left where it is, a logbook being a folder somebody may keep other things in.
- **LOGIC-25 — What a download does about a dive a computer cut in two.** *Settled:* **it
   joins the stretches into one recording, where less than ten minutes of surface separates
   them.**

   A computer ends a dive when a diver reaches the surface and stays there. A diver who
   surfaces for a minute to sort a mask, to find the boat, or to cross a shallow sill comes
   home with two recordings of one dive, and a logbook that takes them at face value has two
   dives, two numbers, and every figure describing half of what happened.

   **The stretches are joined before any of it is a dive.** The join is on `Recording`, so
   everything above it sees a whole dive and no derivation, no graph and no statistic needs to
   know that a join happened. The alternative — one dive holding a recording per stretch —
   was declined: the model's several recordings are parallel views of one dive, a second
   computer's opinion, and two stretches are not that. It would also put two tabs reading
   *Perdix 2* and *Perdix 2 again* over two half graphs, where the point of the format is
   being legible.

   **Ten minutes, and the evidence under it is thin.** `LOGIC-11`'s 500 metres sits in an
   empty band because there were real pairs either side of it to measure. There are none here.
   The logbook the question has been asked of holds no dive a computer cut up at all, across
   more than four hundred recordings from two devices, so the rule has nothing to be fitted to.

   What that logbook does hold is a counter-example, and it is close. The shortest surface
   interval in it that is certainly two dives is eleven minutes; the pair differ in their gas,
   in how it was carried and in what each was for. Every other interval is fifty-one minutes or
   longer. So ten minutes has a minute of margin above it and nothing at all below it, which is
   the opposite of an empty band: the figure is safe against what has been seen and untested
   against what it exists for. The first logbook holding a real split settles it properly, and
   a shorter figure is the safer guess until then.

   **The surface between them is the surface, since the tails are cut first.** A computer waits
   before it ends a dive, so what lies between two stretches as they arrive is the wait and the
   surface together; `LOGIC-30` takes the wait out before any pair is measured, and what this
   rule sees is what the diver actually spent up there.

   **What the pair says about the dive as a whole is taken from the pair**: the deeper of the
   two depths, the colder of the two temperatures, and a length running from the first
   stretch's start to the second's end, surface and all. The average depth is weighted by how
   long each stretch lasted, the surface between them being no depth anybody recorded. The
   second stretch's samples move along by the time between the two starts.

   **The gas sources are laid over each other by position**, the two stretches being one dive on
   one computer whose list of cylinders is the same list both times, and the cylinder keeping
   what it began the dive at and what it came up with. Matching them by what they say instead
   made one cylinder into two, each stretch reporting its own pressures for it, which is what a
   real download did; matching by gas would make twins into one.

   **The surface between them stays a hole.** `DATA-58` in [../data/doc.md](../data/doc.md)
   has a gap saying nothing, read as a straight line throughout, and for depth that reads
   exactly right: both ends are at the surface, so the line between them is the surface. For
   what the computer worked out it is an approximation, which is the same licence every other
   thinned stretch already has.

   **A recording keeps one token per stretch**, in the order they were recorded, which is why
   `fingerprint` holds a list. A later download hands back the last, being the one the device
   reaches last, and every stretch stays recognisable to a download that starts again from
   nothing. `DATA-90` put the token on the profile; this only makes it plural. A lone token
   in a logbook written before this reads as a list of one, so nothing has to be rewritten.

   The order a device reports in is its own, so a stretch is measured against the one beside
   it whichever way round the two arrive, and a dive cut into three joins by the same rule.
- **LOGIC-20 — What a download is allowed to create.** *Settled:* **dives, and a dive site
   where the user asks for one. Nothing else.**

   The rule falls out of the decisions above rather than constraining them, and is written
   down so it need not be re-derived. A download creates dives and, with them, the owned items
   a dive holds — its environment, its gear, its profiles, its gas sources. Beyond that it
   creates one thing, a `dive_site`, and only after `LOGIC-18` has asked.

   **No gear, no person, no operator, no trip, no region, no wreck, no certification.** What
   separates the site from the rest is that a fix *proposes* a place even though it is not one.
   Nothing in a download proposes a cylinder — a tank has no identity at all — and nothing in
   it mentions a buddy, an operator or a trip.

   **A downloaded dive is therefore a skeleton**, and deliberately. It has its times, its
   depths, its gas and what the computer thought; it has no site until asked, no buddies, no
   rating, no trip, no tags, and nothing about the conditions but temperature and pressure.
   That is not a shortfall in the mapping — a computer does not know those things — and the
   review is where a user adds them.

   **Filling a reference is not creating one.** `profile.dive_computer` is the case, and a
   download no longer fills it either: it writes the serial and the reference is worked out
   from that, which is `LOGIC-23`.

   **Only a serial names a gear item.** `DC_EVENT_DEVINFO` carries one, once per download
   rather than per dive, and it is the only thing that tells two identical computers apart. A
   brand and a model do not: a user diving their own Halo 2 and a club's are the same two
   strings, and a guess that is a coin flip is worse than none, on the same reasoning that
   refuses to read a density of 1020 as `en13319`.

   **Proposing is not choosing.** Nothing here stops a user picking their own gear item at
   review — the dives are live before they are saved, and `dive_computer` is a field like any
   other. What a serial buys is Yemoja being right without being asked; what serial-only costs
   is that it usually will not be, and the user links their computer themselves the first time.

   And usually it will not fire at all: the device gives an `unsigned int`, while a serial a
   user types is the string on the case — `TW-H2-77120`. That is accepted rather than worked
   around. A match nobody can trust is not worth the code that makes it.

   `model` and `firmware` come with the serial and are dropped. The descriptor already gives
   the product by name, which is what a user reads, and a firmware revision belongs to a device
   rather than to a dive.

   That is exactly what a cylinder cannot have. A dive computer has an identity to match on; a
   tank has a volume and two pressures, which describe thousands of cylinders equally well.
- **LOGIC-19 — Two small ones: surface temperature, and dive time.** *Settled:* **a
   `surface_temperature` on the environment, and `DIVETIME` written as an override.**

   `DC_FIELD_TEMPERATURE_SURFACE` had no home and would have been dropped. On nearly every
   computer it is the *water* at the surface rather than the air, so putting it in
   `air_temperature` would say something the device did not — and the manual is explicit that
   nothing works that field out for you. It is a fact worth keeping and a fact of its own, so
   it gets a field beside the other two temperatures, on the environment where the conditions
   of a dive live.

   `DC_FIELD_TEMPERATURE_MAXIMUM` is still dropped. The warmest water at any point in a dive is
   neither the surface nor the bottom, and nothing here asks for it.

   **`DIVETIME` is written as an override**, the way `MAXDEPTH` is. `duration` derives from the
   last sample, and the manual makes both correctable for the same reason: a device reports
   better than the profile it kept. A computer that stopped sampling before the diver surfaced
   still knows how long the dive was, and the TUI already shows an overridden value in bold, so
   the difference is visible rather than silent.
- **LOGIC-18 — What a download's coordinates become.** *Settled:* **a proposal at review, with
   three answers: an existing site, a new one, or nothing. The per-sample track is dropped.**

   A dive has no position — only a `dive_site` does, and a dive names one by reference. So this
   is the one row of the mapping that is not a field going into a field.

   **The source is the sample, not the field.** This was written the other way round, and both
   halves were wrong. No parser in the library answers `DC_FIELD_LOCATION`, for any device, so
   the field this entry was built on supplies nothing. `DC_SAMPLE_LOCATION` does, and it was
   dismissed here as a track through the dive, which it is not: twenty dives read from a Perdix
   2 gave one position on nineteen of them, always on the first sample, and on four of those a
   second on the last. That is an entry fix and sometimes an exit fix, which is exactly the
   thing this entry wanted. A dive's position is its **first** fix: every dive that reports one
   reports that one, where only some report a second.

   **A fix is not a site.** A site is a place returned to: ten dives on one reef are one site
   with ten dives naming it, while ten downloads give ten positions differing by tens of metres,
   which is the boat and the satellites rather than ten places. And a site has a name, a water
   type, an environment type and its regions, none of which a fix supplies — one minted from
   coordinates alone would be `unknown_dive_site` at a decimal position.

   So the download carries the fix into the review and the user answers it. **Ignoring is one of
   the three answers**, not a failure to choose: a drifting boat, a site not worth recording,
   or a position the user simply does not want. Nothing is written that they have not seen,
   which is what keeps a logbook free of items nobody asked for.

   **The nearest sites are offered in order of distance, and no threshold is chosen.** A radius
   would be a number nobody can pick well — too small and a site is missed, too large and the
   list is noise — while a handful sorted by distance, each shown with its distance, lets the
   user judge what a threshold would have judged for them.

   **Two arriving fixes propose one new site when they are within 500 metres.** That is a
   different question from the one above, and it does need a number: a download of a week's
   diving arrives with no site to offer, and something has to say whether Tuesday's dive and
   Friday's dive are one new site or two. The figure comes from the only reading there has been
   rather than from taste. Pairs the user confirmed as one place ran from 21 to 247 metres
   apart; the closest two places they confirmed as different were 1,384 metres apart; and every
   threshold from 400 metres to 1,300 metres gave the same answer, so the number sits in an
   empty band rather than on a boundary.

   **It compares dives, not fixes.** One dive is one site whatever its own two fixes say, and
   they said up to 711 metres on that reading — further than the rule joins. A rule applied to
   fixes would split a dive from itself.

   Where sites are dense this will join two that a diver names apart, and that is the same
   over-eagerness the name matching accepts: a proposal costs a keystroke to refuse, and this
   one is refused by naming two sites instead of one at review.

   A fix never overwrites a site a dive already names. That case arises when reconciliation
   matches a downloaded dive to one already in the logbook, and there the dive's own answer
   stands.

   A fix past the first is dropped under `LOGIC-10`, an exit being a second position for a dive
   that holds one. A device that really does report a track would be dropped down to its first
   fix by the same rule; none has been met.

   **The altitude fills in a proposed site's `elevation`, and nothing where the device reports
   none.** It is the height of the water above sea level and what a fix at the surface measures,
   so it is the right quantity; the question was whether it is trustworthy enough to write.

   It follows `LOGIC-11` rather than `LOGIC-10`. A vertical fix is the worst of the three
   coordinates — satellites all sit above the horizon, so the vertical error is the large one —
   and `elevation` is not a decoration: the manual says diving at altitude changes how a dive is
   worked out. But the answer to a poor figure is not to drop it, because the user is already
   looking at this one. A site is proposed and accepted, so an elevation filled into that
   proposal is a default somebody sees and can correct, which is what `LOGIC-11` means by a
   stated assumption rather than a silent one. Where the device reports no altitude, nothing is
   offered: an empty field is honestly empty.

   The middle answer is ruled out by this question's own reasoning. *Use it only above the
   altitude where it starts to matter* is a threshold, and a threshold here is a number nobody
   can pick well, which is why no radius is used to choose among sites that already exist. The
   500 metres above is not that kind of number: it decides nothing a user cannot see and undo,
   and it was measured rather than picked.

   **The stakes are lower than they look, and for a reason worth stating.** `DC_FIELD_ATMOSPHERIC`
   is already carried to `environment.atmospheric_pressure`. Ambient pressure is what
   decompression wants and elevation is a proxy for it, so a downloaded dive records the
   meaningful quantity by a better route. It is a typed or planned dive that leans on
   `elevation`, and neither has a fix to take one from.

   One thing this does not establish: whether devices report that altitude from satellites or
   from their own pressure sensor. A barometric figure is derived from the quantity that matters
   rather than from satellite geometry and would deserve more trust than the above gives it. It
   does not change what is done with it — offered, shown, correctable — so it is worth knowing
   and did not need to be known first.

   This gives [reconciliation.md](reconciliation.md) a kind of candidate that is not an item
   being imported, which bears on `RECON-2`.
- **LOGIC-17 — What a download's decompression model becomes.** *Settled:* **four fields on a
   profile — `deco_model`, `gradient_factor_low`, `gradient_factor_high` and `conservatism`.**

   `DC_FIELD_DECOMODEL` gives a model, a conservatism and, for Bühlmann, a pair of gradient
   factors. Nothing here held any of it.

   **This is the one thing a download offers that `LOGIC-10` should not drop**, and the reason
   is the distinction that decision rests on. A ppO2 series or a heart rate is data this model
   does not keep. This is *metadata about data it does keep*: the manual already says
   `decostop`, `no_deco_time`, `cns` and the rest are what the computer said, "calculated with
   that device's own model, its settings and the diving you had done before". Two of those three
   clauses arrive with the recording, and a `decostop` read years later means little without
   knowing whether the computer was on 30/70 or on 85/85.

   It does not let anything be recomputed, and is not meant to. `LOGIC-6` settles that `deco` is
   read off the recording rather than worked out, and the third clause — the diving done before
   — is not here and never will be.

   **The format had already decided how a gradient factor is written.** `data-format.md` says a
   proportion runs from 0 to 1 and gives a gradient factor of 20 as `0.2`, so a computer set to
   30/70 records `0.3` and `0.7`. Two numbers rather than one piece of text, because they are
   two numbers.

   `deco_model` is a suggested set rather than a fixed one — `buhlmann`, `vpm`, `rgbm`, `dciem`,
   which is what libdivecomputer names. A maker may run something else, and unlike `water_type`
   or `alarms` nothing exports this to a closed list, so a name outside the four is not a
   problem to refuse.

   `conservatism` gets no range. It is a dial position whose meaning is the device's own: `2` is
   one thing on one make and something else on another, so it is worth recording and not worth
   comparing.
- **LOGIC-16 — What a download's events become.** *Settled:* **five map to alarms, a gas
   change is a gas switch, and the rest are dropped. An interval is kept at its start.**

   `parser_sample_event_t` holds twenty-six kinds and `alarms` is nine closed words. The nine
   were taken from UDDF so that mapping stays exact — `DATA-45` took its six steps rather than
   inventing a scale, and the same reasoning applies here — so the question is which of the
   twenty-six have an honest home, not how to widen the nine.

   Five do: ascent, remaining bottom time, surface, transmitter to `link`, and RGBM to
   `microbubbles`. Everything else is dropped under `LOGIC-10`.

   **Stretching the mapping was the alternative and is refused.** A ceiling is not a
   decompression alarm and a high partial pressure is not an error, so reading them as `deco`
   and `error` would fill our words at the cost of what they mean: a user reading *error* could
   no longer tell which of two things the computer said. Four of the nine — `breath`, `deco`,
   `error`, `skincooling` — therefore go unfed by a download, and are fed by UDDF and by the
   user. A word with no source is better than a word with the wrong one.

   **A gas change is not an alarm.** `GASCHANGE` and `GASCHANGE2` carry the mix, so on many
   devices a switch arrives as an event rather than as `DC_SAMPLE_GASMIX`. Where it lands is
   `LOGIC-12`'s; what this settles is only that events are a second source for the same thing.

   **A bookmark is dropped deliberately**, on the reasoning already recorded against UDDF's
   `setmarker`: a marker says something was interesting and nothing about what, which the dive's
   remarks do better.

   **An event's `value` is dropped with the ones that carry it.** What it means is per event and
   per device — an ascent rate on one, something else on another — and `alarms` is a series of
   words. The five that survive are kept for having happened, not for a magnitude.

   **An interval is recorded at its start.** `SAMPLE_FLAGS_BEGIN` and `SAMPLE_FLAGS_END` make an
   event a state starting or stopping, and `alarms` is a series of instants. Keeping both ends
   would put two identical words in the series with nothing to say which was which, so an ascent
   beginning and an ascent ending would read as two ascents. Keeping the beginning says the
   thing happened and when, which is what an alarm is for. Saying how long it lasted would want
   a field of its own, and nothing reads this stream yet.
- **LOGIC-15 — What an import does about a recording's density.** *Settled:* **it thins, and
   writes what the thinning cost into `tolerances`.**

   A computer sampling every two seconds gives eighteen hundred depths in an hour, and a profile
   drawn from those is no better than one drawn from a couple of hundred. Points lying on a line
   the others already describe carry nothing.

   This is not about disk. A logbook is small either way, and `../data/json/doc.md` says so —
   twenty dives is about forty kilobytes. It is about a recording being legible by hand, which
   is what the format exists for.

   **The figures themselves are provisional and still not chosen.** What is built thins by
   dropping any point within a tolerance of the line the points either side of it describe, so
   what survives differs from the recording by no more than that tolerance anywhere — which is
   exactly what the written figure then claims. A tenth of a metre, a fifth of a degree and half
   a bar are what a first import uses; they are the numbers to argue about rather than an answer.

   The error measured is vertical rather than perpendicular, because the two axes are not the
   same quantity: one is seconds and the other is metres, and the distance between them is not a
   length. What is being asked is how wrong the value would be if the point were dropped.

   **The figure is written because a missing one claims nothing.** The manual is explicit: an
   absent tolerance does not mean the series was left alone, it means nobody recorded what was
   done to it. Thinning without recording would produce a recording that lies by omission about
   its own precision, which is worse than either alternative.

   **Only a series with a tolerance figure is thinned**, and the model says which by holding
   the figures. Three are positive — depth, temperature and pressure — and that is not a
   coincidence to work around: a straight line between two samples is a claim about a quantity
   that varies continuously. `alarms` and `gas_switches` have none at all, being events, where
   dropping one that lies "between" two others would drop the event itself.

   **Three more are zero, and zero loses nothing.** A tolerance of zero drops only the points
   that cost exactly nothing to drop: the middle of three equal readings, or of any run lying on
   one straight line. `no_deco_time`, `cns` and `decostop` are values the computer works out
   rather than measures, and a worked-out value repeats while the depth holds and runs straight
   while the depth changes evenly, so a third of the points survive and the series reads back
   identically. What was written here before — that zero keeps everything, and that a stepped
   series must not be thinned — was two mistakes in one. Smoothing a step is what a *positive*
   tolerance would do, and it is still refused for these three; at zero nothing is smoothed,
   because both ends of every run are kept and the step between them stands. **A negative
   tolerance is what a user sets who wants the recording untouched.**

   That matters more than a third of the points suggests, because these series were the largest
   thing in a recording: a Perdix dive that keeps 226 depths after thinning was carrying 375
   no-decompression readings and 383 CNS readings, and now carries three and sixteen.

   The three positive figures are a setting rather than a constant, and are **not chosen
   here**: they are stated in the default units — metres, degrees Celsius and bar — so that
   whoever picks them is not picking a number in the wrong scale.
- **LOGIC-14 — What a download's salinity becomes.** *Settled:* **`water_type` as reported,
   and the reported density written beside it.**

   `DC_FIELD_SALINITY` gives a type and a density together, and the type is one of two: fresh
   or salt. This is the field that closes what `DATA-59` calls the sharpest gap in the UDDF
   mapping. UDDF stores a converted depth and discards the conversion; a download hands the
   conversion over, so a downloaded profile knows what its depths were made with and the
   `salt_density` a gear item carries is never consulted for one.

   The density is written whatever it is. `density` is overrideable, so a written figure sits
   on top of whatever the water type would have given and wins, which is exactly the case the
   field was made overrideable for.

   **`en13319` can never come out of a download**, and is not inferred. A computer set to the
   standard's nominal figure reports salt at 1020, and so does a computer genuinely set to salt
   water at 1020 — nothing distinguishes them. Reading 1020 as the standard would be right most
   of the time and unfalsifiable when it was not: the label would change under the user with
   nothing to show a choice had been made. So the third value stays what a user picks, and a
   download only ever writes the two it was told.

   Nothing is lost by that. The label reads a little oddly on a computer using the standard —
   salt, at 1020 — while the depths are right either way, since they follow the density and the
   density is the reported one.
- **LOGIC-13 — What a download's deco samples become.** *Settled:* **`NDL` fills
   `no_deco_time` and `DECOSTOP` fills `decostop`. A safety stop and a deep stop are dropped.**

   `DC_SAMPLE_DECO` carries a type, a time, a depth and a time to surface, and the type is one
   of four: no-decompression limit, decompression stop, safety stop, deep stop. This model has
   two series and neither is a home for the last two.

   **`decostop` is a required stop**, which the manual is explicit about — a rounded depth,
   three metres or six or nine — and neither of the others is required. Folding them in would
   be the simplest mapping and wrong in the way that matters: `deco` derives true from any
   `decostop` above zero, so every recreational dive that held an ordinary three-minute safety
   stop would come back labelled a decompression dive. Dropping is the only choice under which
   `deco` stays honest.

   What is lost is that the diver stopped at all, which the depth profile still shows as a flat
   stretch at five metres. A field of their own was the alternative and was refused for now: a
   fifth series that no other format carries and UDDF cannot export, bought by widening the
   model for something the profile already implies. `LOGIC-10` covers the dropping, and it can
   be modelled later on its own merits.

   `tts` is dropped with them. Time to surface is what the computer predicted rather than what
   happened, and nothing here holds a prediction.

   **The two series are sparse and interleaved, not parallel.** A sample is one of the four at
   a time, so `no_deco_time` says nothing during a stop and `decostop` says nothing before one.
   That is what a series already is — values against time, with gaps meaning no sample rather
   than no value — so it needs no accommodating.
- **LOGIC-12 — What a download's gas mixes and tanks become.** *Settled:* **one gas source per
   tank, plus a gas source for any mix that was breathed without one. A mix that was neither is
   dropped.**

   A download gives two arrays. `DC_FIELD_GASMIX` holds fractions; `DC_FIELD_TANK` holds a
   cylinder's pressures and volume and an index into the first. This model holds one keyed
   collection where a gas and the cylinder it came out of sit together, so two become one.

   A tank is a gas source, taking its fractions from the mix it names. Two tanks on one mix stay
   two, which is what a diver who logged twins or a pair of stages gets back.

   **A mix with no tank counts only if it was used**, and a switch naming it is what says so.
   Most recreational computers report no tanks at all, so without this the gas a diver actually
   breathed would be dropped for want of a transmitter. Against that, a computer commonly holds
   gases programmed and never breathed — a plan rather than a record — and those are dropped
   under `LOGIC-10`.

   **What counts as used at the start of a dive is an assumption**, not something the header
   settles: a dive on one gas that never switches names nothing, and reading *used* strictly
   would give it no gas source at all. So the mix in effect before any switch counts as used,
   which for a dive that never switches is the one gas it was breathed on.

   Two mappings follow and need no decision of their own. `DC_SAMPLE_PRESSURE` is indexed by
   tank, so it lands on the gas source that tank became. `DC_SAMPLE_GASMIX` is indexed by mix,
   so it lands on a gas source carrying that mix — **the first, where two tanks share one.**
   That is imprecise and cannot be otherwise: a computer records which gas was switched to and
   has no idea which cylinder the diver reached for.

   **A `usage` sits on both a mix and a tank**, and is one of four: none, oxygen, diluent,
   sidemount. Only one of those means anything here, and it is not a usage: `sidemount` is what
   this model calls a **configuration**, so it lands in `gas_source.configuration`. Oxygen and
   diluent are rebreather terms that `FEAT-21` rules out, and are dropped with the rest of
   `DIVEMODE`'s closed and semi-closed values. Where a mix and its tank disagree the tank wins,
   it being the cylinder that was actually carried a particular way.

   `gas_source.usage` is left unfed by a download. `bottom`, `stage`, `deco` and `travel` are a
   diver's words for what a cylinder was *for*, and no computer records that.

   **A download creates no gear**, and cannot. A tank has no identity — no serial, no name,
   nothing but a volume and two pressures — so there is nothing to match an existing cylinder
   on and nothing to name a new one. A dive site is the contrast: coordinates propose a place
   even though they are not one, which is what makes `LOGIC-18` possible. A tank proposes
   nothing.

   So `gas_source.cylinder` is left unset and `volume` is written directly. That is not a
   workaround but the case the field was made overrideable for, and the manual describes it:
   a rented or borrowed cylinder the user has no item for.

   **A tank's `volume` is already water capacity**, in litres, for metric and imperial alike.
   The library converts an imperial tank's air capacity before handing it over and says so:
   *"The volume has been converted from air capacity to water capacity."* An earlier draft of
   this question had us doing that conversion, which was wrong — it was read off the enumerator
   names rather than the header.

   `tank.type` is consumed rather than kept, and it is the only thing here that is: `NONE`
   means the volume and the work pressure are both zero, so it says whether there is a volume
   to write at all.

   **`workpressure` is dropped**, because there is nowhere for it to go. It is the one route
   back to how an imperial cylinder is named — *Vair = Vwater × Pwork / Patm* — so an eighty
   arrives as eleven litres and cannot be shown as an eighty again. That is a real loss and is
   recorded as one rather than reasoned away. It follows from creating no gear: a work pressure
   is a property of the cylinder and not of the dive, and there is no cylinder to put it on.
   Whether a hand-entered gear item should gain a `working_pressure` of its own is a separate
   want, and would serve the same purpose for cylinders a user does record.
- **LOGIC-11 — Where a downloaded dive's `gmt_offset` comes from.** *Replaced by `LOGIC-32`*,
   which split the one offset in two and asks nothing during a download. It said: **asked once
   for the download, and changeable per dive afterwards.**

   **The device is asked first, and the user where it has nothing to say.** `dc_datetime_t`
   carries a `timezone` beside the date and the time, and `DC_TIMEZONE_NONE` exists to mean
   the device did not report one — so some do and some do not. An earlier draft of this
   question said the offset was the one field a download could not fill from what it was
   given, which was written before that struct was looked at and is wrong.

   Where nothing is reported, the download asks and offers the machine's own current offset as
   the answer. That guess is right whenever the two were set together, which is the ordinary
   case, and the asking is what makes it a stated assumption rather than a silent one. A user
   who leaves a computer on home time in another country — the case the field exists for —
   corrects one number.

   **The asking survives either way**, since a reported zone is as correctable as a guessed
   one: a computer set to the wrong zone reports the wrong zone confidently. What a device
   gives is a better default, not an answer beyond question.

   **Two things about that member are unverified**, and both matter enough not to assume. Its
   unit — seconds, minutes or hours — and its sign. The header carries no comment on either;
   the sign in particular is what this project's own `gmt_offset` got backwards once, so it
   wants the library's documentation or a trial against a device rather than a reading of the
   enumerator's neighbours.

   `dc_event_clock_t` pairs a `devtime` with a `systime` and would give the same figure by
   measuring the device's clock against the machine's. That is a second route with the same
   two unknowns, and it is worth less now that a device may simply say.

   **Per dive costs nothing, because of how a review already works.** An import applies to the
   live data without saving it and the user keeps or discards the result, see
   [reconciliation.md](reconciliation.md), so a downloaded dive is an ordinary item in an
   ordinary logbook before anything is written. `gmt_offset` is a plain recorded field on a
   profile, so changing one during that review is editing a field, not a special path through
   the importer. A download that crossed a zone, or a clock reset halfway through a trip, is
   handled by the machinery already there.

   **The offset is always written, even when it is zero.** Otherwise a download would leave the
   field absent, and absent reads as zero — a dive whose zone nobody knows would be
   indistinguishable from one taken on a computer set to GMT. Writing it says what was assumed.
- **LOGIC-10 — What a download does with a value the model has no field for.** *Settled:*
   **it is dropped, and the download says what it dropped.**

   A dive computer offers more than this model keeps: dive mode, ppO2, a rebreather setpoint,
   remaining bottom time, heart rate, a compass bearing, and whatever a maker puts in its own
   strings. None of those has a field, and inventing one for each in order to lose nothing
   would be letting the devices decide what a dive is.

   Average depth was on that list until the model gained a field for it, and is now an
   override like `MAXDEPTH`. That is the question working rather than failing: a thing is
   dropped *because* nothing models it, so modelling it is the way off the list.

   **Keeping them unrecognised was the tempting answer and is the wrong one.** The format
   already keeps a field it does not know, so that a newer version's data survives a round
   trip through an older one. That works because such a field came from Yemoja and will mean
   something again. A vendor string never will, and a ppO2 series that no version of this
   application reads is not waiting to be understood — it is being stored. Putting the two in
   one place would say they are the same kind of thing.

   Dropping is only honest if it is visible, which is why the second half is not decoration.
   A download reports what it took and what it left, the way [reconciliation](reconciliation.md)
   reports everything else, so a user who wanted a figure knows it was seen and refused rather
   than never noticed.

   Each of the eight can still be modelled later, on its own merits and as a feature. `divemode`
   is the precedent: it was looked at for UDDF and deliberately left out. What this settles is
   that they are not modelled by accident, in a bag, because the alternative was work.
- **LOGIC-9 — Where *today* comes from.** *Settled:* **the local calendar date, from a
   `today()` this layer implements per platform.**

   Four fields want it: `days_left` and `expired` on an insurance and on a maintenance.
   Nothing else in the project asks what day it is, and until these are written nothing
   does.

   **Local, and the zone is not worth deciding.** A dive's times are local too — `LOGIC-32` —
   but a moment in GMT is already tomorrow in Auckland, so a day judged by one clock and a day
   judged by another disagree for a third of every day. That gap does not matter here: these fields are hints, and no renewal turns on
   which side of midnight it is judged from. A cover that ran out this morning and one that
   runs out tonight are the same news.

   **Asked, not handed in.** *Today* needs no context: it is the same answer for every item
   in every set, so nothing has to carry it and nothing has to be told it. A derivation calls
   `today()` where it needs one, and no signature anywhere mentions a day.

   It was built the other way first — the date threaded from the front end through
   `Universe.open`, the reader and `ItemSet`, which a derivation then read off `item.set`. That
   arrangement was forced by a real constraint, since the data layer calls a derivation holding
   only the item and `item.set` is its one route outward, so context that genuinely varies
   would have to arrive that way. This does not vary. Threading it cost `ItemSet` the claim its
   own document makes — *two questions, and no more* — put a parameter on three signatures,
   and made forgetting it silent, since it had to default to absent or every test would carry
   a day it did not care about.

   **The argument for handing it in was testability, and it was weaker than it looked.** A
   derivation reading a clock does make a test time-dependent — but only one that names a day.
   Written as an offset from `today()`, a test pins the arithmetic and both boundaries without
   naming one: cover ending twenty-seven days out has twenty-seven left; ending today has none
   left and has not expired; ending yesterday has minus one and has. `Date.daysUntil` is
   covered where it lives.

   **Per platform, because a calendar day needs a zone.** `kotlin.time.Clock.System.now()` is
   in the standard library and needs no dependency, but it answers with an instant. Using it
   as a day would mean UTC, which tells somebody in New Zealand it is yesterday for thirteen
   hours out of every twenty-four — small, and exactly the off-by-one a reader notices on
   `days_left`. So `expect fun today()` here and an `actual` per target, of which the JVM's is
   the only one built.

   This says nothing about context that does vary. Should a derivation ever need something
   that differs between one set and another, `item.set` is still the only channel and the
   question reopens then. `LOGIC-7`'s judgement sits above the field, `UI-2`'s units are
   applied when a value is read, and `LOGIC-6`'s gradient factors belong to a service
   computing a fresh answer rather than to a stored one, so nothing is waiting.

   **No dependency follows.** `kotlin.time.Clock.System.now()` is in the standard library
   and needs nothing added, but it answers with an instant, and turning an instant into a
   calendar day needs a zone the standard library does not carry. Each front end knows its
   own local date without help, so the date arrives from above and the question of a
   date-and-time library stays unopened. It would reopen if something wanted a zone
   properly — a reminder that fires at a particular hour, say — and that is `LOGIC-7`'s
   neighbourhood rather than this one's.

   This does not make a medical's validity a computation. The manual is deliberate that
   Yemoja does not judge one, because how long a check counts for depends on who is
   asking. What is settled here is only where the day comes from for the fields that
   already have an end date written on them.
- **LOGIC-5 — Concurrency.** *Settled:* **there is none.** One thread, one operation at a
  time, and a long operation takes the application over until it finishes.

  Downloading a dive computer, importing a file, running a plan: each is exclusive. The
  user is not browsing dives while forty come off their computer, because nobody needs to
  and pretending otherwise buys a class of bugs for a convenience nobody asked for.

  **What that removes is the whole question.** Two things reaching one logbook at once is
  the only reason any of the alternatives existed — copying it so readers see a consistent
  version, queueing writes to one owner, locking it. None of them is needed if there is
  never a second thing. `ItemSet` stays a plain mutable structure, an edit changes an item
  in place, and no part of this layer has to be written twice for a case that cannot arise.

  It is also the one place where the change of language would otherwise have cost
  something: coroutines make a background download cheap to *start* and make the shared
  logbook expensive to get right. Declining the first declines the second.

  **One implementation constraint, and it is not a softening of this.** Android kills an
  application whose interface stops answering for a few seconds, so *exclusive* cannot mean
  a frozen thread — the interface has to keep drawing to show that something is happening
  and to offer a way to stop it. The work therefore runs off the interface's own thread
  while the interface refuses everything else: one screen, a progress indication, a cancel,
  and nothing reachable behind it. The rule stands as written — one operation at a time,
  the application belongs to it — and only the mechanism has to respect the platform.

  Two things follow elsewhere. `LOGIC-4` gets easier: a live in-memory logbook has no
  observers racing it. And a large import is a single stretch of work rather than something
  reconciled while the user carries on around it — see
  [reconciliation.md](reconciliation.md).

- **LOGIC-6 — Which gradient factors a logged dive's `deco` is computed against.**
  *Settled:* **none, because nothing computes it.** `deco` is read off the primary
  profile, from either of two things the computer recorded:

  - a `decostop` above zero, at any point — the dive went into decompression, and a `decostop`
    recorded but never above zero — it did not;
  - failing a `decostop`, a `no_deco_time` that never reached zero — it did not — or one that
    did, after a positive reading — it did.

  Where the profile has neither, or there is no profile at all, `deco` is absent and the
  user answers it.

  The second rule exists because computers generally write stops only when there are
  stops, so a `decostop`-only rule would leave `deco` unanswered on most recreational
  dives that came off a computer. A device saying you always had time left is the same
  device's answer as the stops it did not set, and just as much a fact of the recording —
  reading it is not inference.

  The question assumed `deco` could not be answered without gradient factors. For a dive
  that came off a computer it can, and better: the device decided at the time, with the
  user in the water, using the model and settings they were actually following. Nothing
  here can reproduce that, and a second opinion computed afterwards would not be a
  correction — it would be a different dive's answer printed over this one.

  That is the rule the manual already applies to `decostop`, `no_deco_time`, `cns` and
  `otu`: kept as recorded rather than recalculated. `deco` is the same kind of claim and
  now follows the same rule, which removes the exception rather than deciding it.

  **Gradient factors therefore play no part in a logged dive at all.** They belong to
  planning, where `default_gf_low` and `default_gf_high` are what a new plan starts from,
  and changing them cannot alter what the application says about a dive done years ago —
  which is what this question was worried about, now unable to happen.

  A dive from a depth gauge gets no computed answer, and that is the point. Absent means
  nobody has said; it does not mean no. See `DATA-50` in
  [../data/doc.md](../data/doc.md).
