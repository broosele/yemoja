# A dive computer against this model

A field-by-field comparison between what
[libdivecomputer](https://libdivecomputer.org/) hands over and the items described in
`manual/data-fields.md`. It is the companion to [uddf.md](uddf.md), which does the same for
a file format, and it feeds `FEAT-3` — downloading from a dive computer.

**This is analysis and decisions, not behaviour.** Nothing is built: `FEAT-3` is *Planned*,
and `LOGIC-2` has not settled where the device-facing half of it lives. What is settled is
the correspondence between two data models and what a download does with each part of it,
which is a fact about both and does not wait on either.

Each row cites the question that settled it. The reasoning lives with the question in
[doc.md](doc.md); this document is the map.

## Where the shape came from

Two things are read: **fields**, asked for one at a time and describing the dive as a
whole, and **samples**, walked in order through it. The names below are the enumerators of
`dc_field_type_t` and `dc_sample_type_t`, taken from the published header.

Reading that header is not the contested ground the project's provenance rule is about. It
is the interface a program links against, and using it is what the LGPL contemplates. The
implementation behind it was not read and is not needed.

**One asymmetry worth stating up front.** UDDF is a file, so a bad mapping can be redone
from the same file tomorrow. A dive computer's log is a ring buffer: it overwrites itself,
and a dive dropped on the way in is usually gone for good. That is why `LOGIC-10` insists a
download report what it dropped, and why `LOGIC-17` modelled rather than dropped.

## What a download creates

**Dives, and a dive site where the user asks for one.** With a dive come the owned items it
holds — its environment, its gear, its profiles, its gas sources — and nothing else is made at
all: no gear, no person, no operator, no trip, no region. `LOGIC-20`.

So a downloaded dive is a skeleton, deliberately. It has its times, its depths, its gas and
what the computer thought; it has no site until asked, no buddies, no rating, no trip, no
tags, and nothing about the conditions but temperature and pressure. A computer does not know
those things, and the review is where a user adds them.

Filling a reference is not creating one, and `profile.dive_computer` is not even filled: the
serial is written, and the reference is worked out from it as the gear item carrying the same
serial. `LOGIC-23`. A brand and a model are the same two strings on a user's computer and on a
club's, so they name nothing — though the user may always pick an item themselves, the dives
being live before they are saved.

## The dive as a whole

| libdivecomputer | ours | |
|---|---|---|
| `datetime` | `profile.start_date`, `start_time` | as the computer's clock read |
| the descriptor | the profile's key | the gear item the serial named, else the device's name |
| `devinfo.serial` | `profile.serial` | what `dive_computer` is worked out from: `LOGIC-23` |
| `devinfo.model`, `.firmware` | — | dropped: the product name is what a user reads |
| `clock.devtime`, `.systime` | — | a second route to the offset, unverified |
| `datetime.timezone` | `profile.gmt_offset` | where reported; else asked: `LOGIC-11` |
| `DIVETIME` | `profile.duration` | written as an override: `LOGIC-19` |
| `MAXDEPTH` | `dive.max_depth` | written as an override |
| `AVGDEPTH` | `dive.average_depth` | written as an override |
| `GASMIX`, `GASMIX_COUNT` | `dive.gas_sources` | with the tanks: `LOGIC-12` |
| `TANK`, `TANK_COUNT` | `dive.gas_sources` | `LOGIC-12` |
| `SALINITY` | `profile.water_type`, `density` | type and density both: `LOGIC-14` |
| `ATMOSPHERIC` | `environment.atmospheric_pressure` | absolute in both |
| `TEMPERATURE_MINIMUM` | `environment.bottom_temperature` | the coldest water |
| `TEMPERATURE_SURFACE` | `environment.surface_temperature` | water, not air: `LOGIC-19` |
| `TEMPERATURE_MAXIMUM` | — | dropped: neither surface nor bottom |
| `DIVEMODE` | — | dropped: `LOGIC-10`, as UDDF's is |
| `DECOMODEL` | `profile.deco_model` and three more | `LOGIC-17` |
| `LOCATION` | proposes a `dive_site` | not a field: `LOGIC-18` |

## The samples

| libdivecomputer | ours | |
|---|---|---|
| `TIME`, `DEPTH` | `profile.depth` | thinned: `LOGIC-15` |
| `TEMPERATURE` | `profile.temperature` | thinned |
| `PRESSURE` | `profile.pressures` | keyed by tank there, by gas source here |
| `GASMIX` | `profile.gas_switches` | the first source on that mix: `LOGIC-12` |
| `DECO` | `profile.decostop`, `no_deco_time` | two of four types: `LOGIC-13` |
| `EVENT` | `profile.alarms` | five of twenty-six: `LOGIC-16` |
| `CNS` | `profile.cns` | a fraction there, a percentage here |
| `RBT` | — | dropped, though its alarm is kept |
| `HEARTBEAT` | — | dropped |
| `BEARING` | — | dropped |
| `SETPOINT`, `PPO2` | — | dropped: rebreather, and `FEAT-21` rules those out |
| `VENDOR` | — | dropped: a maker's own bytes |
| `LOCATION` | — | dropped: a track, not a position |

## What their structs carry

The enumerators above say what can be asked for. Several answer with a struct, and its
members needed deciding too — the first audit of this document checked the enums and missed
them.

| member | ours | |
|---|---|---|
| `gasmix.oxygen`, `.helium`, `.nitrogen` | `gas_source.gas_type` | the mix as divers write it |
| `gasmix.usage`, `tank.usage` | `gas_source.configuration`, for `SIDEMOUNT` only | `LOGIC-12` |
| `tank.gasmix` | which mix a gas source carries | `LOGIC-12` |
| `tank.type` | — | consumed: `NONE` means there is no volume to write |
| `tank.volume` | `gas_source.volume` | already water capacity, both kinds |
| `tank.workpressure` | — | dropped: no gear is created, so nothing holds it |
| `tank.beginpressure`, `.endpressure` | `start_pressure`, `end_pressure` | gauge in both |
| `salinity.type`, `.density` | `profile.water_type`, `density` | `LOGIC-14` |
| `decomodel.type` | `profile.deco_model` | `LOGIC-17` |
| `decomodel.conservatism` | `profile.conservatism` | a dial position, no range |
| `decomodel.params.gf` | `gradient_factor_low`, `_high` | 0 to 1, not 0 to 100 |
| `location.latitude`, `.longitude` | a proposed site's | `LOGIC-18` |
| `location.altitude` | a proposed site's `elevation` | filled in and shown: `LOGIC-18` |
| `sample.pressure.tank` | which gas source | `LOGIC-12` |
| `sample.event.type`, `.flags` | `profile.alarms` | five of twenty-six, beginnings only |
| `sample.event.value` | — | dropped: per event and per device |
| `sample.deco.type`, `.time`, `.depth` | `decostop`, `no_deco_time` | `LOGIC-13` |
| `sample.deco.tts` | — | dropped: a prediction, and nothing holds one |
| `sample.ppo2.sensor` | — | moot, the sample being dropped |
| `sample.vendor.type`, `.size`, `.data` | — | dropped: a maker's own bytes |

## What has no source at all

Six things this model holds that a download may not supply, and each has somewhere else to
come from:

- **`profile.gmt_offset`** — where the device reports no zone, which some do not.
  `LOGIC-11` asks for it once then and lets the user change it per dive either way.
- **`profile.tolerances`** — nothing gives it because nothing else does the thinning.
  `LOGIC-15` makes the import owe the figures.
- **`otu`, `no_flight_time`, `desaturation_time`** — computers display them; the library
  does not report them. They come from UDDF, or from the user.
- **`gas_source.usage`** — `bottom`, `stage`, `deco` and `travel` are a diver's words for what
  a cylinder was *for*, and no computer records that. `LOGIC-12`.
- **`gas_source.cylinder`** — a download creates no gear and a tank has no identity to match one
  by, so nothing points at a gear item. `volume` is written directly instead. `LOGIC-12`.
- **`alarms`: `breath`, `deco`, `error`, `skincooling`** — four of our nine words that no
  event maps onto. `LOGIC-16` refuses to stretch a near-miss into them.
- **A fix**, for now. The library declares a location *field* and nothing in it fills one, for
  any device. It also has a location *sample*, a position at an instant, and the Shearwater
  driver fills that — which this walk drops, along with every other sample type it does not
  name. So `LOGIC-18`'s proposal does have a source, and it has been looked at: reading twenty
  Perdix dives gave a position on nineteen of them, always on the first sample, and on four of
  the nineteen a second one on the last. That is an entry fix and sometimes an exit fix, which
  is a better answer than the field would have been and settles what a dive's one position is
  made of. Nothing reads it yet, so a site is still the user's to name. `LOGIC-18` is what turns
  it into a proposal.

## Where the two models disagree in shape

Four places where the difference is structural rather than a name.

**Two arrays against one collection, and no cylinder in either.** Gas mixes and tanks are
separate lists there, the second indexing into the first; here a gas and the cylinder it came
out of are one thing. But a tank is not a cylinder: it has no identity, so a download creates
no gear and names none.
`LOGIC-12` makes a tank a gas source and adds one for any mix that was breathed without a
tank. What follows is that a gas switch names a mix, and where two tanks share one, only the
first can be pointed at — a computer records which gas was switched to and has no idea which
cylinder the diver reached for.

**A stop is not a stop.** Their `DECO` sample carries four types; our `decostop` is a
*required* stop and nothing else. Folding a safety stop in would be the simplest mapping and
would make `deco` derive true for every recreational dive that held three minutes at five
metres. `LOGIC-13`.

**An event may be an interval.** `SAMPLE_FLAGS_BEGIN` and `SAMPLE_FLAGS_END` make an event a
state starting or stopping; `alarms` is a series of instants. `LOGIC-16` keeps the beginning,
because two identical words in the series could not say which was which.

**A position is not a place.** A dive has no coordinates and a site does, so a fix cannot be
written anywhere. `LOGIC-18` makes it a proposal at review with three answers, one of which
is nothing.

## What this is better at than a file

Worth recording, because it runs the other way from every other row.

`DATA-59` calls one thing the sharpest gap in the UDDF mapping: *UDDF stores the converted
depth and discards the conversion.* A depth is a pressure divided by an assumed density, and
without that density nothing can be worked back.

**A download hands the conversion over.** `SALINITY` gives a type and a density together, so
a downloaded profile knows what its depths were made with and the `salt_density` a gear item
carries is never consulted for one. The same is true of `DECOMODEL`: the manual already said
`decostop` and `no_deco_time` were computed with the device's own model and settings, and a
download is where those settings arrive.

## What is built

**Everything above the port.** `logic/divecomputer/` holds the recording a device hands over,
the port it hands it through, the thinning, and the mapping in the tables above. A download makes
dives with their environment, their gas sources and their profile, and nothing else at all —
`LOGIC-20`.

**The port's other side, for the JVM.** libdivecomputer is called through JNA and linked as a
shared library, which is what the licence position in README turns on. Looking finds what is
attached over USB HID and serial and what is advertising over Bluetooth LE; opening one walks its
dives and parses each into a recording.

**Bluetooth LE is the application's to do.** The library owns serial and USB and hands over
nothing for Bluetooth LE but a stream of fifteen functions to fill and a name filter: it knows
which advertised names are which model, and nothing about scanning, connecting, or which of a
device's characteristics carry the bytes. Kable does the radio, on every target it will be built
for. A scan listens for four seconds and matches what it heard by name; opening connects, picks
the pair of characteristics by the rule `LOGIC-22` gives, subscribes before the first write, and
hands the library a stream whose reads answer one notification apiece — over Bluetooth LE the
library reads packets, not counts, and offers a buffer bigger than any of them. The stream answers
the two requests the drivers that speak Bluetooth make — the advertised name, and a
characteristic read by UUID — and calls a PIN or an access code unsupported until a device is
met that wants one.

**What is attached is enumerated once and matched in memory.** The obvious reading of the API is
to ask each of the three hundred and fifty odd models whether it is there, and that is what this
did first: twenty-six seconds, because every question enumerates the bus again. An iterator may be
given no descriptor at all, so one enumeration per transport and a few thousand comparisons by
`dc_descriptor_filter` give the same answer in about a fifth of a second.

**A second download transfers only what is new.** Each dive comes with a token the device knows
it by, and handing the last one back before the next download tells the device where to stop.
`DATA-90` keeps it on the profile, so it travels with a synced logbook, survives a restore, and
recognises a dive exactly rather than by proposing one. Where to resume from is a question the
logbook answers: the newest recording that computer made and that carries one.

**One dive computer has been met.** A Shearwater Perdix 2 over Bluetooth LE on Windows gave up
a hundred dives in twenty minutes, each with its samples, tank pressures, gradient factors,
water and fingerprint. USB and serial have met nothing. What is tested without a device is the
sample walk — the offsets a reading is taken at, and the constants that decide which stop is a
required one and which event is an alarm — driven with memory laid out by hand. Three of those
constants were wrong when first written, and one of them mapped `decostop` onto a safety stop,
which is exactly what `LOGIC-13` refuses.

Three more gaps, each of them a decision rather than typing:

- **The drop report.** `LOGIC-10` requires a download to say what it dropped, and nothing
  collects it. What is dropped is decided; where the list goes is not.
- **The site proposal.** `LOGIC-18` makes a fix a question at review with three answers, and a
  recording carries one that nothing yet asks about.
- **`LOGIC-15`'s tolerances.** The thinning is built and the figures it uses are provisional.

## What is owed

Decided and not built, or built and not proven. Each is here rather than in somebody's head.

- **Two computers, over one transport.** A Shearwater Perdix 2 and an Aqualung i330R, both over
  Bluetooth LE. Every other model, and USB and serial altogether, are tested on recordings
  written by hand above the port and memory laid out by hand below it, which catches a wrong
  offset and not a wrong reading of what a device means. Three constants were wrong when first
  written and all three were found by re-reading the header. Each device then found an error no
  test had: a read that waited to fill its buffer where the library wanted one packet, a write
  split at twenty bytes where the driver meant one packet of twenty-one, a whole family of
  models answering to one another's names, and a density of zero written as though it were a
  measurement. That is the class still waiting for every model not yet met.
- **A tank and its mix arrive apart.** The Perdix reports a tank from its transmitter with
  pressures and no mix, and the mix on its own, so a dive on one cylinder of EAN30 becomes two
  sources: one with pressures and no gas, one with gas and no pressures. That is what `LOGIC-12`
  says to do with a tank whose mix is unknown, and it is the wrong picture of that dive. Whether
  one tank and one mix are joined when there is exactly one of each is a decision not yet put.
- **A second reading has not used the access code.** The i330R was paired once, the code was
  typed, and the sixteen bytes it handed back are kept. Nothing has yet handed them back to it,
  so whether a kept code opens the device without asking is the one half of `LOGIC-24` still
  unproven. The rest is: the driver asks for the code by an advertised name that spells the
  serial, and *unsupported* is what makes it ask the user instead.
- **A Shearwater takes twenty minutes where an Aqualung takes eight, and neither is this
  side's doing.** Timing a Perdix read: a write costs 1.6 milliseconds and the whole minute
  goes on waiting for the next packet, 684 of 731 waits landing between 100 and 140
  milliseconds. Not one packet arrived within five milliseconds of the one before, so the
  device sends exactly one twenty-byte packet per connection event and the connection interval
  is around 120 — about 170 bytes a second. The i330R moves 71 packets a second over the same
  radio on the same machine, so nothing here is a fixed ceiling.

  Windows can be asked for a throughput-optimised connection interval, and neither Kable nor
  the btleplug it is built on exposes that, so there is nothing to call. Other applications
  being quicker is consistent with their asking. Whether it is worth reaching past Kable to the
  platform for this is not decided; `TUI-8` owns the silence while it runs either way.
- **A second computer's dive is merged beside the application, not through it.** `RECON-7`.
- **A link that drops keeps what it got.** The second session lost the connection after fifteen
  minutes and seventy-two dives, for no reason either side reported; the seventy-two were handed
  over. What was not got cannot be fetched by resuming, because a device counts from its newest
  dive and the fingerprint says where to stop, not where to start. The older dives take another
  full download that lasts.
- **The drop report is not collected.** `LOGIC-10` settled that a value with no field is dropped
  *and that the download says what it dropped*. What is dropped is decided field by field above;
  where the saying goes is `LOGIC-21`.
- **The fix does not cross the port, though the device sends one.** `LOGIC-18` settled that a
  download's coordinates become a proposal at review, and no review question exists, so the
  recording does not carry one: a field every implementation must fill and nothing reads is a
  lie waiting to be believed. What was written here before was that nothing in the library
  supplies a position, and that was wrong: no parser answers the location *field*, but the
  Shearwater driver fills a location *sample*, and a Perdix sends one on nearly every dive. The
  walk drops it. When `LOGIC-18` is built the position is there to be taken.
- **A sample type the walk does not name is dropped without being counted.** Twenty Perdix
  dives went past carrying twenty-three positions and six and a half thousand
  remaining-bottom-time readings, and nothing said so. `LOGIC-10` requires a download to report
  what it dropped, and `LOGIC-21` asks where that report goes; until then the model is being
  told less than the device offers and cannot tell how much.
- **The serial has not been read off a device.** The JVM listens for the device-info event and
  hands the serial over as a decimal number, and nothing has yet compared that number with what
  a Perdix prints on its screen. The comparison reads a hexadecimal spelling as its number, so
  either way round should match; whether it does is the next download's to say.
- **A re-download overwrites a correction.** Applying lays the arriving dive over the held one
  member by member, so buddies, site, rating and notes survive, a second computer's profile
  lands beside the first, and the visibility survives the environment. `RECON-6`. A field the
  computer reports *and* the user has corrected does not survive: a max depth fixed by hand is
  written over without a word, and a profile arriving under the key already held has its
  series replaced. That is the collision `data/json/requirements.md` describes and
  [reconciliation.md](reconciliation.md) records as not built. And the gas sources land beside
  the held ones under their own keys rather than onto them, since nothing says which is which,
  so a re-download doubles them.
- **A logbook resumes only through a serial its gear items carry.** A profile that names a gear
  item by hand is found through that item's serial, so the item must have one. Until it does,
  the resume lookup finds no chain for the device and the next download fetches everything
  again. `LOGIC-23`.

## Open questions

- **LOGIC-21 — Where a download's drop report goes.** `LOGIC-10` requires one and nothing
  collects it. What is dropped is known as it happens — a device field with no home, a sample
  type this model does not keep — so the question is what carries it and who reads it: a value
  the download answers with beside the items, something the review shows, or a line in the
  journal `FEAT-4` will keep. It bears on `TUI-8`, both being what a download has to say for
  itself while and after it runs.

What this document also waits on is elsewhere:

- **`LOGIC-22`** — how the characteristics a device talks through are found, which is decided
  and not yet proven on a device.
- **`RECON-2`** — whether an import can be accepted in part, which `LOGIC-18` gives a new
  kind of candidate to.
- **`TUI-8`** — what a job that takes minutes looks like, which is what a download is.
