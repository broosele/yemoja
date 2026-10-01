# UDDF against this model

A field-by-field comparison between [UDDF](https://www.streit.cc/resources/UDDF/v3.2.3/en/index.html)
version 3.2.3 and the items described in `manual/data-fields.md`. It answers `DATA-54` —
what the interchange formats carry that this model does not — and feeds `DATA-55`, which
asks whether what this model records can be written back out.

This is analysis first. What is settled is the correspondence between two data models, which is
a fact about both; what of it is read and written is under *What is built*, and `RECON-4` settled
that the one package does both.

**What a user keeps or loses is stated in [../manual/uddf.md](../manual/uddf.md),
and that chapter owns it.** This document holds what the manual deliberately does not: the
element names, the quotations from the specification, and the reasoning behind each
decision — why a thing is lost, what the alternatives were, and which question settled it.
Where a consequence appears here, it is because a decision rests on it. Anything that
changes what survives changes the manual in the same breath, and the manual is the version
to trust.

## Scope

UDDF has upwards of 450 elements, from `abundance` to `wreck`. Most of them describe
things this application is not for. Excluded wholesale, and not listed again below:

- **Rebreathers.** Closed and semi-closed circuit, and everything that follows —
  `setpo2`, `measuredpo2`, `calculatedpo2`, the rebreather values of `divemode`. Ruled
  out by `FEAT-21`.
- **Fauna, flora and observations.** UDDF carries a biological survey vocabulary —
  species, abundance, sightings. Not modelled, and not intended to be.
- **Dive computer control.** UDDF can drive a device: setting tables, transferring
  schedules. Yemoja reads computers, it does not program them.
- **Other decompression models.** `vpm`, `rgbm` and their parameters. `LOGIC-3` settled that
  Bühlmann ZH-L16C with gradient factors is the only model, so these have nowhere to land.

What remains is the logbook: the dive, its profile, its gas, the site, the person and the
equipment. That is what this document covers.

## A note on units

**UDDF is strict SI and says so outright:** *"All values are given in the SI system
(Systeme International d'unites)."* The base units are named — metre, kilogram, **cubic
metre**, kilogram per cubic metre, **pascal**, **kelvin**, second — and there is no unit
attribute anywhere to say otherwise. A quantity's unit is fixed by the specification, not
by the file.

This model defaults to litres, bar and degrees Celsius, so three of the four dimensions
where `DATA-8` departs from SI collide head-on:

| Quantity | Ours | UDDF | |
|---|---|---|---|
| temperature | `C` | K | add 273.15 — the one conversion that is an offset rather than a factor |
| volume | `l` | m³ | divide by 1000 |
| pressure | `bar` | Pa | multiply by 100000 |
| angle | `deg` | decimal degrees | agree; the units page names no unit for angle, and `latitude` and `longitude` are plain real numbers |
| density | `kg/m3` | kg/m³ | agree |

Every one of those is exact, so this is arithmetic rather than a modelling difference and
nothing is lost in either direction. `DATA-8` fixed the names it converts into.

A file's own unit declarations therefore have no counterpart on import: there is nothing
to read, only a constant to apply. Going out, every value is converted and no unit is
written, because writing one would not be UDDF.

The set has `m3` and `Pa` in it (`DATA-61`), so a converted file *may* be written in the
units it arrived in and skip the arithmetic entirely. That is not what the importer will
do — a logbook in cubic metres would be unreadable to the user who owns it — but it is
available, and it means nothing UDDF can say lacks a name here.

*An oddity, not yet confirmed against the schema:* the `latitude` page describes its sign
convention in east/west terms, which reads as carried over from `longitude`. Positive is
north; nothing turns on it here, but an implementer reading that page could be misled.

## The dive

| Ours | UDDF | |
|---|---|---|
| `dive_number` | `divenumber` | |
| `start_date`, `start_time` | `datetime` | split |
| `max_depth` | `greatestdepth` | |
| `bottom_temperature` | `lowesttemperature` | |
| `visibility` | `visibility` | |
| `air_temperature` | `airtemperature` | |
| `current` | `current` | one-to-one |
| `rating` | `rating` | 1 to 10 both sides |
| `remarks` | `notes` | |
| `dive_site` | `link` | |
| `temperature_evaluation` | `thermalcomfort` | |
| `previous_dive`, `surface_interval` | `repetitiongroup`, `surfaceintervalbeforedive` | see *Repetitive dives* |

`current` is the one that already matches exactly: `DATA-45` took UDDF's six steps
rather than inventing a scale, so the mapping needs no table.

**Ours with no UDDF counterpart.** `waves` — UDDF has no wave element at all, so it
arrives empty from every UDDF file and is dropped on the way out. Temperature and
visibility used to lose a pair each to UDDF's single value; this model records one of each
too, so nothing is given up there now.

`duration`, `buddy_count` and `name` are derived here and would be
computed either side rather than carried across. **`deco` is the awkward one.** Where a
profile recorded stops it is derived from them and survives, because the stops themselves
export as `decostop` samples. Where there is no profile it is the user's own answer
(`LOGIC-6`), and a UDDF dive has no field for it: a dive from a depth gauge that the user
marked as a decompression dive goes out saying nothing about that, and comes back
unanswered.

`tags` has no counterpart anywhere. `dive_trip` and `operator` are compared under *Dive
trips* and *Operators*.

**UDDF's, with no counterpart here.** Each is a candidate for the model or a deliberate
omission, and none is yet decided:

- `altitude` — the site's elevation, which we hold on the dive site rather than the dive.
- `workload` — physical exertion. `manual/decompression.md` names exertion among the
  things the model cannot know, so recording it would be honest even unused.
- `problems`, `equipmentmalfunction` — what went wrong. Currently only `remarks`.
- `apparatus`, `purpose`, `program`, `stateofrestbeforedive`, `diveplan`, `pressuredrop`,
  `internaldivenumber`, `applicationdata`.

**`platform` has a counterpart, on the site rather than the dive.** `DATA-123` put `entry` on
the dive site, where it does not change from one dive to the next, and UDDF's `platform` sits on
a dive. What is not yet compared is the two vocabularies: ours is a suggested set of six and
UDDF's is whatever its own page says, which nobody has read against it. An export also has to
decide whether a site's entry is worth writing on to every dive made there.

## The profile

UDDF stores samples as `waypoint` elements — one time-ordered list, every waypoint
carrying `depth` and `divetime` and whatever else was measured at that instant. This
model keeps a series per quantity, each with its own times.

Ours is the more expressive shape: a computer samples temperature far less often than
depth, and a shared time base forces padding or interpolation. It stays convertible
because every series is read as piecewise linear, so exporting means emitting waypoints
at the union of the timestamps and reading depth off the line for those that lack one.
What that costs, and why it is acceptable, is weighed under *Where the two disagree*.

The waypoints after the surfacing are dropped as the file is read, a file being as free as a
device to keep recording once the diving stopped. A depth that will not read as a number stops
the cut where it stands, so nothing is lost to a file this cannot parse. `LOGIC-30`.

| Ours | UDDF waypoint child |
|---|---|
| `depth` | `depth` |
| `temperature` | `temperature` |
| `pressures` | `tankpressure` |
| `alarms` | `alarm` |
| `gas_switches` | `switchmix` |
| `decostop` | `decostop` |
| `no_deco_time` | `nodecotime` |
| `cns` | `cns` |
| `otu` | `otu` |
| `no_flight_time` | `noflighttime` | on the dive there |
| `desaturation_time` | `desaturationtime` | on the dive there |
| `water_type`, `density` | — | see below |

`alarm` is a closed list of nine — `ascent`, `breath`, `deco`, `error`, `link`,
`microbubbles`, `rbt`, `skincooling`, `surface` — and this model took the same nine, so
that mapping is exact. UDDF's optional `level` is not kept: severity is not comparable
between makes.

**Ours with no counterpart.** `water_type` and `density` on a profile — what the computer
was set to, and the constant it turned pressure into depth with. UDDF records neither, so
an imported profile has no density and nothing can be worked out from it until a user
supplies one. That is `DATA-59`, and it is the sharpest gap in this document: UDDF stores
the converted depth and discards the conversion.

**UDDF's, not modelled.** `setmarker`, deliberately — a marker says something was
interesting and nothing about what, which the dive's remarks do better. `divemode`,
which reduces to open circuit against apnea once rebreathers are out, and a breath-hold
dive is arguably a different kind of dive rather than a mode within one. `bodytemperature`,
`heartrate` and `pulserate`, dropped by `DATA-72`; `batterychargecondition`, `heading`,
`remainingbottomtime`,
`remainingo2time`, `gradientfactor`, `divetime` as a value in its own right.

## Repetitive dives

UDDF wraps its dives: `profiledata` holds `repetitiongroup`s, each with `dive`
*"compulsory, multiple"*, and a group is a run of dives close enough together that the gas
from one is still in the diver during the next — a diving holiday. Each `dive` may carry a
`surfaceintervalbeforedive`, which holds either `<infinity/>` — the diver started fully
desaturated — or a `passedtime` in seconds.

This model does not group. A dive names `previous_dive`, or names nothing, and the chains
that makes carry the same information (`DATA-60`). `surface_interval` derives from it.

| Ours | UDDF | |
|---|---|---|
| `previous_dive` unset | `<infinity/>`, and a group opens | |
| `previous_dive` set | membership of the group already open | |
| `surface_interval` | `passedtime` | seconds both sides |

**Importing** walks each group in order: the first dive takes no `previous_dive`, and each
one after it takes the dive before. A `passedtime` is then redundant and is dropped,
because the two dives it sits between are both present and their clocks say the same
thing. It is kept in one case — on the *first* dive of a group, where it describes a dive
that is not in the file. That is what `surface_interval` being correctable is for.

**Exporting** reverses it. An unset `previous_dive` opens a `repetitiongroup` and writes
`<infinity/>`; a set one continues the group already open; `passedtime` is computed from
the two dives. A dive whose `previous_dive` points outside the exported set opens a group
and writes its `surface_interval` as `passedtime`, which is honest — the reader is told
there was an earlier dive without being shown one.

**`exposuretoaltitude` and `wayaltitude`** have no home here and are dropped. They record
travel *during* a surface interval — driving over a pass between two dives — which is not
derivable from anything and is not the same as a site's `elevation`, the altitude the dive
itself was at. It matters only to a decompression calculation across an interval, and
planning is `FEAT-6` and undecided, so this is recorded as a gap rather than closed with a
field that would be empty on every dive anyone logs. See `DATA-54`.

## Gas and cylinders

UDDF splits what this model keeps together. A `mix` defines a gas once for the whole
file and is referred to by id; a `tankdata` block holds one cylinder's pressures for one
dive. Our `gas_sources` entry is both at once.

| Ours | UDDF | |
|---|---|---|
| `gas_type` | `mix` | fractions |
| `start_pressure` | `tankpressurebegin` | |
| `end_pressure` | `tankpressureend` | |
| `volume` | `tankvolume` | m³ to litres |
| `cylinder` | `link` | to `tank` |

`gas_type` is the case `DATA-55` exists for. UDDF holds a mix as fractions — `o2`, `he`,
`n2`, and `ar` and `h2` besides — while this model writes `EAN32` and parses the
fractions out. The two are convertible because ours is parsed rather than a label, which
is why it is a value kind and not text. Argon and hydrogen have no place to go.

**Convertible to whole percentages**, which is as fine as this format goes: `EAN32` and
nothing between. A UDDF `o2` of 0.318 arrives as 32 and leaves as 0.32. That is a loss and
a small one — a mix is named and analysed in whole percentages, and rounding oxygen up is
the conservative direction — but it is a loss, and it is in the field `DATA-55` names as
its own example.

**A counterpart now, and not yet read or written.** `breathingconsumptionvolume` — a gas
consumption rate, in `m3/s`, which is a gas source's `sac` here, worked out from the pressures and
the volume or written by hand. `LOGIC-33`.

**UDDF's, not modelled.** `equivalentairdepth`,
`maximumoperationdepth`, `maximumpo2` — all derivable from the mix. `priceperlitre`,
which `FEAT-20` rules out along with everything else about money. `aliasname`.

## The dive site

| Ours | UDDF | |
|---|---|---|
| `name` | `name` | |
| `alternative_names` | `aliasname` | |
| `longitude`, `latitude` | `geography` | |
| `elevation` | `altitude` | in `geography` |
| `environment_type` | `environment` | many-to-one |
| `water_type` | `density` | see below |
| `max_depth` | `maximumdepth` | in `sitedata` |
| `substrate` | `bottom` | free text both sides; ours renamed to keep clear of a gas source's `bottom` |
| `rating` | `rating` | 1 to 10 both sides |
| `remarks` | `notes` | |

**Ours with no counterpart.** `facilities` — what is there: parking, a filling
station, showers. UDDF has no equivalent.

**UDDF's, with no counterpart here.**

- `minimumdepth` — the shallowest the site gets. Deliberately not modelled: a site's
  depth is what it reaches, and how shallow it also is says little.


## The person

UDDF splits `owner` from `buddy` structurally. This model has one Person type and names
the owner once, in `yemoja.json`, which is flatter and loses nothing.

| Ours | UDDF | |
|---|---|---|
| `first_name` | `firstname` | in `personal` |
| `middle_names` | `middlename` | |
| `last_name` | `lastname` | |
| `birthday` | `birthdate` | |
| `address` | `address` | see below |
| `email`, `phone` | `contact` | |
| `insurance` | `diveinsurances` | |
| `medical` | `medical` | see below |
| `courses` | `education` | see below |
| `remarks` | `notes` | |

**Ours with no counterpart.** `emergency_contacts`, which UDDF has nowhere to put.

**UDDF's, not modelled.** `honorific` and `sex` in `personal`; `divepermissions`.

**Three that correspond loosely rather than field for field:**

- **Address.** Ours is one text field; UDDF's is `street`, `city`, `postcode`, `country`.
  Ours holds theirs as prose, so import concatenates and export cannot take it apart
  again. Lossy on the return leg, and only there.
- **Medical.** Ours records the state — `last_medical_check`, `blood_group`, `height`,
  `body_mass`. UDDF records *examinations*, each with a date, a doctor, a result and
  notes, and may hold several. Theirs is a history where ours is a snapshot; `DATA-37`
  settled that deliberately. Import can take the latest examination's date into
  `last_medical_check` and loses the rest; export produces one examination with no doctor
  and no result. Nothing else in ours has a place there: blood group, height and mass are
  not examination data.
- **Certifications.** Ours is a `Course` owned by a person, pointing at a `Certification`
  item that a library supplies, and carrying the instructor and the dives that earned it.
  UDDF's `certification` sits under `education` with `level`, `certificatenumber`,
  `organisation` and `issuedate` — the qualification and the holding of it in one place,
  with no separate item and no library. `certificatenumber` has no home here; `instructor`
  and `dives` have none there.

## Equipment

The largest structural difference in the format. UDDF has **twenty-one element types** —
`boots`, `buoyancycontroldevice`, `camera`, `compass`, `compressor`, `divecomputer`,
`equipmentconfiguration`, `fins`, `gloves`, `knife`, `lead`, `light`, `mask`,
`rebreather`, `regulator`, `scooter`, `suit`, `tank`, `variouspieces`, `videocamera`,
`watch` — each a different element. This model has one `Gear` type and says what a thing
is in `category` and `kind`.

Each of theirs carries manufacturer, model, serial number, purchase information and
service intervals, which line up with `brand`, `model`, `serial` and `maintenances`.
Purchase information does not: `FEAT-20` rules money out.

The two shapes are not the same in kind. Theirs is **one closed axis**: a thing *is* a
`mask`, and there is no way to say what sort of suit a `suit` is except in prose inside
it. Ours is **two open axes**, `category` and `kind`, and both accept anything.

### Importing

Each element sets both, from a fixed table. Where nothing sensible fits a category, it is
left unset — absent means unknown, which is exactly what it is:

| UDDF | `category` | `kind` |
|---|---|---|
| `mask` | ABC | mask |
| `fins` | ABC | fins |
| `boots` | suit | boots |
| `gloves` | suit | gloves |
| `suit` | suit | — |
| `buoyancycontroldevice` | BCD | — |
| `regulator` | regulator | — |
| `tank` | cylinder | — |
| `lead` | weights | lead weight |
| `divecomputer` | instruments | dive computer |
| `light` | lighting | light |
| `camera` | photography | camera |
| `videocamera` | photography | video camera |
| `knife` | accessory | knife |
| `compass` | instruments | compass |
| `watch` | instruments | watch |
| `compressor` | — | compressor |
| `scooter` | — | scooter |
| `variouspieces` | — | — |

`rebreather` is dropped by `FEAT-21`. `equipmentconfiguration` is not a piece of
equipment at all but a description of how a set is put together, and has no home here.

UDDF has no element for a depth or pressure gauge — its `divecomputer` covers the modern
case and the older instruments are presumably `variouspieces`. So `instruments` is wider
than anything UDDF names, and three of its elements collapse into one category of ours.

### Exporting

Best effort, and nothing silently lost. Where the pair maps back to an element, it is
written as that element: ABC with kind `mask` becomes `<mask>`. Where it does not — ABC
with kind `flipper` — the element becomes `variouspieces` and the kind is written into its
notes, so a person reading the file still learns what it was.

**That much is recoverable by a reader, not by a program.** UDDF's `applicationdata` can
carry the rest — see *Application data* below.

**And equipment belongs to a person there, to nobody here.** UDDF nests `equipment`
inside `owner` or `buddy`. In this model Gear is a top-level item that dives refer to,
which is why a club's shared cylinder or a borrowed weight belt needs no owner. Exporting
must attribute every item to the file's owner, whether or not it is theirs.

## Operators

UDDF has two, and only one of them is an item.

**`divebase` is the match.** It is top-level, its `id` is *compulsory* — "a unique
identifier of this divebase" — and everything else about it is optional. That is our
`Operator`: one thing, named once, pointed at from wherever it is used.

**`operator` is not.** It sits inside a `trippart`, optional and single, carries no `id`,
and holds its details inline: *"Inside `operator` information about the operator of a
diving trip is put into brackets."* It cannot be shared and cannot be linked to. It is a
description of who ran the trip, not a reference to them.

| Ours | UDDF | |
|---|---|---|
| `name` | `name` | |
| `alternative_names` | `aliasname` | |
| `address` | `address` | |
| `phone`, `email`, `website` | `contact` | three fields into one element |
| `rating` | `rating` | |
| `region` | — | a reference here, and UDDF's address is text |
| `category` | — | no counterpart; see below |

**Ours with no counterpart.** `category` — dive centre, club, resort, boat, liveaboard.
UDDF distinguishes a `divebase` from a `vessel` structurally instead, which is a coarser
cut than ours and does not survive being flattened into it: a liveaboard is both.

**UDDF's, not modelled.** `guide`, the staff, each a `link` to a person — a real thing
this model has no field for, and an unusual one to want. `pricedivepackage` and
`priceperdive`, out with `FEAT-20`.

**The asymmetry that matters is on import.** Writing out is easy: every `Operator` becomes
a `divebase` with its id, a dive points at one with a `link`, and a trippart that has an
operator can carry the inline `operator` as well without harm. Reading back is where
identity is lost — an inline `operator` has no id, so two tripparts naming the same dive
centre are two anonymous descriptions, and nothing in the file says they are the same
place. An importer either creates two items or matches them by name, and matching by name
is a guess. Preferring `divebase` and a `link` wherever the file offers both is the only
lossless reading.

## Wrecks

UDDF puts a `wreck` inside `sitedata`, optional and single. This model keeps no wreck: a ship is
what a site is dived for, and what is known of it is said in the site's `remarks`. Reading writes
a paragraph after the site's own notes, beginning *Wreck:* and her name, then each part UDDF
gives in its own words and with the value as written there: `aliasname`, `shiptype`,
`nationality`, the `built` yard and launch, `sunk`, the four of `shipdimension`, `tonnage`, and
the wreck's own `notes`. A figure goes with UDDF's unit for it, metres or kilograms, and
`tonnage` with none, since gross tonnage in the world is a volume and UDDF defines it as a mass;
a reader judges it. UDDF's `sunk` carries a time as well as a date, and only the date is said.

Nothing is written back as a `wreck`. What a site's remarks say of a ship leaves in its `notes`.

## Dive trips

Everything of ours lands in a `trippart` rather than in the `trip` above it, because a
trip here carries the same fields whether or not it has a parent:

| Ours | UDDF | |
|---|---|---|
| `name` | `name` | present at both levels |
| `dives` | `relateddives` | in `trippart`, and pointing the other way |
| `start_date`, `end_date` | `dateoftrip` | in `trippart` |
| `region` | `geography` | in `trippart` |
| `operator` | `operator` | in `trippart` |
| `remarks` | `notes` | in `trippart` |

**Ours with no counterpart.** None. Every field of a `Dive trip` has somewhere to go.

**`relateddives` points the opposite way**, and that survives the round trip without a
decision. UDDF stores the membership on the trip — *"Inside `relateddives` all dives made
during a dive trip can be listed"* — while here a dive names its `dive_trip` and the trip
derives `dives` from that, gathering its own and those of everything beneath it. Reading
one and writing the other is bookkeeping. The one thing that does not survive is that a
`relateddives` may link a whole `repetitiongroup` rather than single dives; since this
model has no groups (`DATA-60`), such a link flattens to the dives inside it, which says
the same thing in more words.

**UDDF's, not modelled.** `accommodation`, which names where you stayed with its own
category, contact and rating; `vessel`, the boat with its name, type, marina and
dimensions; `aliasname`; `pricedivepackage` and `priceperdive`, both ruled out
with the rest of money by `FEAT-20`; and `rating`, which UDDF carries at *both* levels and
which this model does not have on a trip at all.

**The scale matches, and it is ours that moved.** `ratingvalue` is a required integer and
the specification fixes its range: *"The scale ranges from '1' (lowest quality) to '10'
(highest quality)."* This model ran 0 to 10 and now runs 1 to 10, on all three of dive,
dive site and operator, so every rating survives a round trip in either direction. There
was no scale attribute to read — the earlier note guessing at one was wrong. The precedent
is `DATA-45`, which took UDDF's six steps for `current` rather than inventing a scale; the
cost is the bottom value, which nobody could reliably tell from 1 anyway.

**Rating more than once is still asymmetric.** A UDDF `rating` carries an optional
`datetime` beside its value and may repeat, so a site visited twice can hold both verdicts
and their dates. Ours is a single number and holds the current one. Importing takes the
latest by `datetime` and drops the rest, which loses history without misstating anything;
exporting writes one `rating`, and the `datetime` is left off rather than invented, since
this model does not record when a rating was formed.

### One trip against many legs

**No longer a mismatch, and this model is now the wider of the two.** A trip may name a
`parent`, so a fortnight is a trip with a leg for each island beneath it. UDDF's `trip`
and `trippart` are exactly two levels; a `parent` reference is any number, and a simple
weekend stays one item with no container above it and no part below.

The two differ only in direction, as elsewhere: UDDF's `trippart` lists its
`relateddives`, while here each dive names its trip and the list follows — the rule that
a relationship is stored once, on the side that owns it.

Exporting flattens: a trip with legs becomes a `trip` with a `trippart` for each, and any
level deeper than two has nowhere to go.

## How references work on both sides

Since UDDF 3.0.0 there is one cross-reference element. `link` is empty, carries a
compulsory `ref` — *"unique identifier of the data object to be cross-referenced"* — and
is optional and multiple under eighteen parents. It replaced the situation-specific
elements earlier versions had.

Those eighteen, and what each means here:

| Parent | Here |
|---|---|
| `equipmentused` | a dive's `items` |
| `tankdata` | a gas source's `cylinder` |
| `informationbeforedive` | a dive's `dive_site` |
| `relateddives` | a trip's `dives`, though the direction is reversed — see *Dive trips* |
| `trippart` | a bare cross-reference; the page does not say what it points at, and a `divebase` is the obvious candidate |
| `certification` | a certification's agency |
| `divebase` | an operator — see *Operators* |
| `examination` | who did a medical, on the `Medical` owned item |
| `profile`, `divecomputerdump` | the gear item that recorded it |
| `notes` | nothing — see below |
| `equipmentconfiguration` | nothing; a named set of kit is not modelled |
| `purchase` | nothing; money is out with `FEAT-20` and `DATA-34` |
| `generator` | nothing; the writing application is file metadata |
| `bottomtimetable`, `table`, `inputprofile` | nothing yet; dive tables wait on `FEAT-6` |
| `hyperbaricfacilitytreatment` | nothing; chamber treatment is not logged here |

Two of the empty rows are worth more than a dash. **`equipmentconfiguration`** names a set
of gear that goes together — a user's usual kit — which this model has no item for: gear
is listed per dive and a configuration would be a saving of typing rather than a fact
about a dive, so it is a feature question rather than a data one. And **`notes`** may hold
a `link`, so UDDF's free text can point at a site or a buddy inside a sentence; `remarks`
here is plain text throughout, so an imported link becomes the target's name and the
pointing is lost.

**It carries no type, and it does not need to**, because a `ref` is unique across the
whole document: resolve it and the element you land on says what it is. What the *parent*
supplies is the role — a `link` under `equipmentused` is gear being used, one under
`informationbeforedive` is where the dive was.

This model splits the same two jobs the same way, which is why the mapping is direct
rather than merely possible:

| | UDDF | Ours |
|---|---|---|
| what it points at | `ref`, unique document-wide | `@id`, unique across the whole item set |
| what the pointing means | the parent element | the field name |

So `link` under `informationbeforedive` is our `dive_site`, `link` under `equipmentused`
is a member of `items`, and neither side needs a type written into the reference itself.
An importer resolves first and classifies from what it found, which is what a UDDF reader
must do anyway.

**One id, one item, across every type.** That follows from a reference carrying no type,
and it is the same rule UDDF's globally unique `ref` implies. It is stated in
[../data/doc.md](../data/doc.md), and `tool/checkdata.py` now enforces it — allowing the
one legitimate way an id appears twice, which is shadowing: same type, laid over rather
than beside.

## Application data

UDDF has an escape hatch. `applicationdata` may sit on a `dive` or a `profile` and holds
one element per application; a parser is required not to interpret it, but to pass it
through untouched. Several vendors coexist in one file without colliding.

**Coming in, it is dropped.** Another application's `applicationdata` is that
application's *derived* data — the specification describes it as tissue saturation
figures and internal parameters — computed from the dive and unreadable here. Keeping it
would store a derivation whose inputs we can change and whose dependencies we cannot see,
which is the thing this model refuses everywhere else: correct a water type and convert
the depths, and a saturation figure inside the blob is quietly wrong with nothing to
notice.

Discarding it only when something relevant changes is not available either, because
knowing what is relevant means reading it. The rule would have to be *any* edit, and then
correcting a buddy's name silently destroys saturation data — an absurdity that argues
the blob should never have been taken in.

So a file imported here becomes a logbook of this model's own, and what another program
kept about that dive stays in that program's file, which importing does not consume.

**Going out, it is available and used reluctantly.** Written by us it cannot go stale,
since it is regenerated from current data every time — the same reason derived values are
recomputed rather than stored. It is what could carry `waves`, a profile's `water_type`
and `density`, and a gear `kind` that matched none of the twenty-one elements — small
things with nowhere else to go. A second profile is explicitly not among them: see
*Several profiles per dive*, where the bulk decides it.

**But it is a private format inside a public one**, and every use of it is a thing only
this application can read. The pressure to match UDDF's representation where both formats
model the same thing — `DATA-55` — exists precisely because a bucket makes it easy not to.
So: nothing goes there that UDDF can express, and what does go there is a last resort
rather than a convenience.

The same reasoning settles any other format's equivalent without asking again. An opaque
payload is someone else's derived value, and this model does not store those.

## Where the two disagree

`DATA-55` asks not what is missing but what is modelled **incompatibly** — both formats
recording the same thing in shapes that do not convert cleanly. A field UDDF lacks is
merely absent from an export; a field both hold differently degrades on every round trip
and no converter can repair it.

### Confirmed

**The shape of a profile — but it largely undoes itself.** Ours is a series per
quantity, each with its own times; UDDF's is one list of waypoints, each carrying
`depth`. Exporting emits a waypoint at the union of all timestamps and reads depth off
the piecewise-linear line for those that lack one, so the file contains depths this
application interpolated rather than the computer measured. UDDF is being told something
slightly untrue.

Reading it back is better than it sounds. Those invented points sit *exactly* on the line
between real samples, so thinning on import drops them again at any tolerance — they add
nothing their neighbours do not already say. Temperature comes back exactly, since a
waypoint carries it only where it was measured. So a round trip of our own data is very
nearly lossless, and what loss there is comes from the tolerance rather than from the
shape.

The other direction is not so kind: a real UDDF file thinned on import loses detail for
good. That is `tolerances` doing its job, not a disagreement between the formats.

**Which cylinder a gas switch moved to.** Our `gas_switches` names a `gas_sources` entry,
which is a cylinder and its gas together. UDDF's `switchmix` names a `mix`, which is the
gas alone. A user switching between two cylinders carrying the same mix is expressible
here and not there, so that distinction is lost on export and cannot be recovered.

**Exotic gases.** UDDF's `mix` carries `ar` and `h2` alongside oxygen, helium and
nitrogen. This model's gas notation names oxygen and helium only, so argon and hydrogen
have nowhere to go and a mix containing them cannot be represented.

**A closed list that maps one way only.** A dive site's `environment_type` is a fixed set
of eleven, closed precisely so that export is possible, and every value does map. But
ours splits pairs UDDF joins — `ocean` and `sea` against `ocean-sea`, `lake` and `quarry`
against `lake-quarry`, `cave` and `cavern` against `cave-cavern` — so export is
many-to-one and a round trip cannot tell which half it started from. Lossy rather than
impossible, and only on the return leg.

**Several profiles per dive.** This model allows a profile per computer and names one
primary. UDDF's `samples` is *"optional, single"* — one per dive, or none. A dive recorded
on two computers therefore cannot be written out whole.

*Settled:* **export writes the primary and drops the rest.** `primary_profile` already
names which one to work from, so there is no choosing to do. The second recording is lost,
and a user should be told so once rather than discovering it.

`applicationdata` could have carried it and deliberately does not. A profile is thousands
of numbers, by far the largest thing in a dive, so this would be the heaviest possible use
of a bucket meant for last resorts — and it would turn a file shared with another
application into one where most of the bytes are unreadable to whoever received it. An
export should be what UDDF can say, not a Yemoja file wearing UDDF's clothes.

Exporting one dive as two, one per profile, was the other option: everything survives in
readable form, and any reader then counts two dives that never happened.

**Water type against density.** Ours is a fixed set of three, one of them the `en13319`
nominal figure; UDDF's `sitedata` holds `density` as a number. Ours converts outward —
each of the three has a density — but not back, since an arbitrary density cannot say
which of three a user chose, and most real densities match none of them.

**Where a site is.** UDDF's `geography` gives `country`, `province` and `location` as
three levels of text. This model gives `regions`, a list of references into a graph with
its own `parents`, where a site may sit in several regions at once and a region in
several larger ones — the fixture has `provence` inside both a country and a sea. Neither
shape reduces to the other: exporting must pick which region is the country and which the
province out of a graph that does not label them, and importing three strings means
matching or minting Region items that may already exist under other names.

**Gear as one type against twenty-one.** UDDF names each kind of equipment with its own
element; this model has one `Gear` item and says what it is in two fields of open text.
Import is a table lookup. Export cannot be, since no table can anticipate what a user
typed — see *Exporting* above, where what will not fit goes into the notes rather than
being dropped. Structure is lost; content need not be.

**Gear has an owner there and none here.** UDDF nests `equipment` inside `owner` or
`buddy`. Here Gear is a top-level item that dives point at, which is what lets a club
cylinder or a borrowed belt belong to nobody. Export must attribute every item to the
file's owner whether or not it is theirs, and import cannot tell shared kit from personal.

**A medical snapshot against a history of examinations.** Ours records the current state;
UDDF records each examination with its date, doctor and result, and may hold many.
`DATA-37` settled the snapshot deliberately. Import keeps the latest date and discards the
rest; export invents a single examination with no doctor and no result, and has nowhere to
put blood group, height or mass.

**An address as prose against four fields.** Ours is one text field; UDDF has `street`,
`city`, `postcode` and `country`. Import concatenates and export cannot take it apart, so
a round trip through this model flattens an address for good.

### A stop, now that both pages are read

**`decostop` is a stop there and a stop standing here.** UDDF's carries a `kind`, a `decodepth`
and a `duration`, all three compulsory, on the waypoint where the stop begins. Ours is a series:
the depth of the stop standing at each moment, nought where none is. The two convert as steps.
Going out, a sample above nought writes a mandatory stop lasting until the series next says
anything, and a nought writes nothing. Coming in, a mandatory stop is its depth at its waypoint,
and where it runs out before another begins a nought is put where it ran out. A safety stop is
not read, since this series is what says a dive was a decompression dive.

What does not survive is a nought before the first stop, which says nothing a series of steps did
not already say, and how long the last stop of all lasted, there being nothing after it to say.

### Not incompatible, only asymmetric

Worth separating, because these lose data without misrepresenting it. `waves` has no UDDF
counterpart, so it is dropped rather than mangled. An alarm's `level` is discarded on
import by choice, since severity is not comparable between makes. A second profile is lost
on export, for reasons set out above. None of these needs a decision beyond the one
already taken.

## What is built

**Every type in this document is read, and written.** `logic/uddf/` holds an XML document read into a tree,
one file of shared reading, the equipment table, and a mapping per type. Dives, their recordings
and their gas; sites, a wreck in one said in its remarks; people, whether owner or buddy;
equipment; trips;
and operators.

**Items are read in the order they point in**, so a reference resolves by the time something
needs it: a site before the dive at it, an operator before the trip, and dives last,
since a dive points at almost everything. A `link`'s `ref` is looked up in what has been read so
far and becomes this model's own reference — which is the direct mapping this document predicted,
the parent element supplying the role and the `ref` only saying which item.

What is not read is what this document already says has nowhere to go, and one thing that has:
the `operator` inline in a `trippart` is not made an item, since it carries no id and two of them
naming one dive centre would be two items. A `divebase` and a `link` is the lossless reading and
the only one taken. It is reached the way another logbook is:
the import screen takes a path, and a file at that path is read as UDDF where a folder is read as
a logbook. A document holding no dives is refused rather than opening a review with nothing in
it.

**No arithmetic was written.** UDDF is strict SI and `DATA-61` already has `K`, `m3` and `Pa`
among the names a file may declare, so the reader hands `ItemReader` those units and the
conversion is the one the model already does. What is left in the mapping is names and shape,
which is the whole of it: a value goes in as the text the document held and the field it lands on
reads it, exactly as a value out of a JSON file does.

**Ids are minted rather than carried.** A UDDF `id` is a name for one document's own use — it
says nothing outside the file — so a dive is named for the day it was made on, as this model
names one.

**So nothing is matched by id, and a UDDF import matches nothing at all.** A minted id says what
this model would have called such an item, not which item it is: two dives on one day are both
`2024-06-15#0` whether or not they are the same dive. Matching on that folded a stranger's dive
into the user's and wrote over what was there, which is what `Matching.NONE` exists to stop. An
arriving item whose id is already taken is minted afresh and lands beside what was there.

The cost is that **importing the same file twice, answered as new each time, puts everything in
twice**. That is visible and can be deleted, where the alternative was quietly overwriting dives
the user already had. What stands in the way is a proposal rather than a rule: taking a dive in
asks whether it is new or one already held, and starts on the one it overlaps in time. The
three-number heuristic `reconciliation.md` once asked for — start time within a tolerance,
duration, maximum depth — is not built and no longer wanted, a tolerance being a number nobody
can pick well.

One thing that follows and is not handled: a reference to an item whose id was minted afresh is
not rewritten. Nothing read from UDDF points at anything else read from it yet, so there is
nothing to follow; a source that did would need this.

### Writing

**The whole logbook goes out, and what goes out is what reading takes in**, so a logbook exported
and imported again keeps whatever the importer reads. A test runs the populated fixture through
both and compares dive by dive.

- **The user is the `owner` and holds every piece of gear**; everybody else is a `buddy`. A
  logbook naming nobody writes an owner with nothing but the equipment.
- **A dive's links name what it points at** — its site, its buddies, its operator — all under
  `informationbeforedive`, and reading classifies each by what it resolves to rather than by where
  it sits. That is the rule under *How references work on both sides*, and reading had taken the
  first link as the site.
- **An id is made legal.** A dive's id holds a `#` and begins with a digit, and an XML id may do
  neither, so what is not a letter, digit, point, hyphen or underscore becomes an underscore and
  one beginning with a digit gains one in front.
- **Dives go out earliest first, in the groups `previous_dive` makes**, as *Repetitive dives*
  sets out, and a trip with legs is a `trip` with a `trippart` apiece.
- **A gas is a `mix` defined once**, and reading now takes the mix back as the gas source's
  `gas_type` rather than leaving it out.
- **The primary recording goes out as waypoints** at every second anything was measured, the
  depth read off the line where only something else was: temperature, stops, no-deco time, CNS,
  OTU, alarms, switches and each cylinder's pressure by `tankref`. A dive's other recordings are
  counted and the user told once.
- **A closed word crosses by table**: a current, how warm the diver was, and what kind of place a
  site is. Reading had copied UDDF's words in, which the fields then refused.
- **A computer a recording names is a `divecomputer`** whatever its kind says, since the table
  cannot tell one from a kind of `wrist`.

**Not written yet**, though this document maps each of them: a person's insurance, gear's
maintenance, where a trip went, a site's regions as `geography`, and a site's water type as a
density. Nor is anything a library supplies, a region or a certification, which UDDF has no item
for; a course goes out naming its certification's title as its `level`.

The document follows the element order of the specification's examples where it shows one. It
has not been checked against the schema, which the specification's pages do not link.

Which of a dive's two halves a value sits in is not relied on, since the reader looks for the
name in either. A zone after a `datetime` is the dive's `time_zone_offset`, read and written;
the time before it is local, which is what a dive's own time is. `LOGIC-32`.

## Where this stands

**The comparison is complete for UDDF 3.2.3.** Every section of the logbook has been read
against it — the dive, its profile, its gas, the dive site, wrecks, the user, equipment,
trips, operators, repetitive dives and how references work — and the claims that once
rested on unread pages have been checked against the pages. Three of them were wrong and
are corrected in place: `operator` does have a counterpart, `rating` has no scale
attribute, and `link` has eighteen parents rather than nineteen.

**Declined deliberately**, which is `DATA-54`'s middle pile — not modelled, as against a
gap to close:

- `workload`, `problems`, `equipmentmalfunction` — all three are things a user observed
  and would write in prose. `remarks` takes them, and three fields that are usually empty
  cost more than they carry. Imported values are folded into remarks rather than dropped.
- `setmarker`, rebreather data with `FEAT-21`, `minimumdepth` on a site, the taxonomy, and
  `exposuretoaltitude` with `wayaltitude`, each recorded where it arose.

**`DATA-55`.** *Where the two disagree* lists eleven confirmed incompatibilities, and the
one that was suspected, `decostop`, has been read and settled as steps. One entry has been closed rather than answered: trips were a mismatch
until this model took UDDF's shape and gave a trip its parts. The worst is settled and is bad: a UDDF dive holds one `samples` block,
so a dive recorded on two computers cannot be written out whole.
