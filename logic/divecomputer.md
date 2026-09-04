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

Filling a reference is not creating one. `profile.dive_computer` takes the device's name,
and a gear item only where the serial identifies one exactly. A brand and a model are the same
two strings on a user's computer and on a club's, so they propose nothing — though the user may
always pick an item themselves, the dives being live before they are saved.

## The dive as a whole

| libdivecomputer | ours | |
|---|---|---|
| `datetime` | `profile.start_date`, `start_time` | as the computer's clock read |
| the descriptor | `profile.dive_computer` | the device by name, no gear proposed |
| `devinfo.serial` | — | the only thing that proposes a gear item |
| `devinfo.model`, `.firmware` | — | dropped: the product name is what a user reads |
| `clock.devtime`, `.systime` | — | may bear on `gmt_offset`; unverified, `LOGIC-11` |
| — | `profile.gmt_offset` | nothing gives it: `LOGIC-11` |
| `DIVETIME` | `profile.duration` | written as an override: `LOGIC-19` |
| `MAXDEPTH` | `dive.max_depth` | written as an override |
| `AVGDEPTH` | — | dropped: `LOGIC-10` |
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
| `location.altitude` | a proposed site's `elevation` | available, not settled |
| `sample.pressure.tank` | which gas source | `LOGIC-12` |
| `sample.event.type`, `.flags` | `profile.alarms` | five of twenty-six, beginnings only |
| `sample.event.value` | — | dropped: per event and per device |
| `sample.deco.type`, `.time`, `.depth` | `decostop`, `no_deco_time` | `LOGIC-13` |
| `sample.deco.tts` | — | dropped: a prediction, and nothing holds one |
| `sample.ppo2.sensor` | — | moot, the sample being dropped |
| `sample.vendor.type`, `.size`, `.data` | — | dropped: a maker's own bytes |

## What has no source at all

Five things this model holds that no download supplies, and each has somewhere else to come
from:

- **`profile.gmt_offset`** — the one field a download cannot fill from what it was given.
  `LOGIC-11` asks for it once and lets the user change it per dive.
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

## Open questions

None of its own. What this document waits on is elsewhere:

- **`LOGIC-2`** — where the device-facing half lives, which needs platform capabilities the
  logic layer should not have.
- **`RECON-2`** — whether an import can be accepted in part, which `LOGIC-18` gives a new
  kind of candidate to.
- **`LOGIC-15`'s three figures** — the tolerances themselves are a setting and are not
  chosen.
