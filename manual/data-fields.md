# What Yemoja records

This page lists every kind of item Yemoja keeps and every field it may hold. It is
the definition of your data: if a field is not here, Yemoja does not know about it.

How these items are written to disk — the files, the naming, the way a value is
spelled — is a chapter of its own: see [data-format.md](data-format.md).

## The items and their fields

Yemoja stores these kinds of item:

- **Dive** — a single dive.
- **Dive trip** — diving done on one occasion or in one place.
- **Gear** — a piece of equipment. A dive computer is gear like anything else you own.
- **Region** — a part of the world.
- **Dive site** — a place you dive.
- **Person** — someone who appears in your logbook, including yourself.
- **Operator** — a dive school or dive centre.
- **Certification** — a diving qualification, such as an agency's open water award.

More may be added later.

A kind of item is never subdivided. Gear covers a regulator, a cylinder and a
camera alike, and each simply leaves out the fields that do not apply to it. Yemoja
does not insist that a particular field be present.

**Nothing is required, including a name.** An item with nothing in it is a valid item;
Yemoja will call it something — `unknown_person`, and `unknown_person#1` for the next —
and let you fill it in later. What it is called does not change when you do, because other
things point at that name; renaming is its own operation and Yemoja does it across the
whole logbook at once.

Nearly every kind of item has a `remarks` field: multiline text of any length, for
whatever you want to note that has no field of its own. It is never required. It is a
convention rather than a rule, so each kind lists its own below and a kind may be
defined without one.

It is the one field where a line break is allowed. Everywhere else a piece of text is a
single line — a dive site called `Blue Quarry`, not a dive site with a paragraph in its
name.

It is also where you can write `@willy` and mean the person: pointing at an item inside a
remark is a convention Yemoja understands and never acts on by itself. See *What the
values look like* in [data-format.md](data-format.md).

JSON has no way of writing a string across several lines, so a line break inside
`remarks` is written as `\n`:

```json
{
  "remarks": "Strong current on the ebb.\nEntry easier from the north end."
}
```

Where an item can hold **several** parts of one sort — a person's courses, a gear item's
maintenance history — each sits under a **key**: a short label, unique within that
collection and meaningless outside it, which Yemoja uses to keep track of which entry is
which.

```json
{
  "courses": {
    "k1": {"certification": "@padi_open_water_diver"},
    "k2": {"certification": "@padi_rescue_diver"}
  }
}
```

The key is where the entry sits, not something written inside it — the same as an item's
id, which is its file name or the key it sits under and never a field. Adding one by hand
means choosing a key, exactly as adding a dive by hand means choosing a file name. Use
anything short that is not already there.

One rule: **a key belonging to an entry you have deleted should never be given to a new
one.** Reusing a key makes Yemoja's record of past changes point at the wrong thing. You
cannot accidentally use one twice at the same time — two entries cannot share a key in a
file that reads at all.

Other parts of the same item can point at an entry by its key — a dive with two
profiles says which of them is the one to work from, by naming its key.

A key is not an id. It means nothing outside the item holding it, and no other item can
use it. Pointing at one is written with a `*` rather than the `@` that marks an id:
`"*p1"` means the entry sitting under `p1`.

**The order they appear in means nothing.** Yemoja writes them in a settled order so that
a saved file does not churn, and reads them in any order at all. Where an order matters
to you — courses by when you took them, services by when they were done — it comes from
the dates inside them, never from where they sit in the file.

Each kind lists its fields in one run below, in the order an interface shows them. A field
you record and a field Yemoja calculates sit together where they belong together — a count
beside the list it counts, a depth beside the profile it came from.

**A field marked *derived* is one Yemoja calculates**, and it is not stored in your files.
Some of them can be corrected: if you know better than the calculation, write the field in
yourself and your value is kept and used from then on. The rest are recalculated every time
and writing them has no effect. Each field says which of the two it is.

Some fields hold an **owned item** rather than a value — a dive's conditions, a
person's health details, a gear item's service history. An owned item is written as an
object inside the item that holds it, and a list of them as an array. They have no id of
their own, nothing refers to them, and they go when the item goes.

Each owned item is described immediately after the item that owns it, so everything you
need to write one item is in one place.

Where an item's own fields are what you see at a glance, its owned items hold the detail
behind them. That is why they are separate: an interface shows the item's own fields
plainly and lets you open the owned items when you want them.

The individual field descriptions follow.

### Dive

The item everything else exists to describe.

**Where the times come from.** A dive can have more than one profile, and one of them
is the primary. If there is a primary profile, the start and the duration are both taken
from it — the computer was there and you were busy.

With no primary profile you write the start date, the start time and the duration
yourself. **There is no end.** A dive says when it began and how long it ran, and where
it finished follows without anybody having to say whether the day turned: a dive in at
23:20 for 45 minutes came out at 00:05 the next morning, and nothing about that needs
recording.

All three can be corrected where the calculation is wrong.

- `start_date` (date, derived) — the day the dive began, in the local time where it was made.
  From the primary profile, corrected by its `recorded_time_offset`, or from you.
- `start_time` (time, derived) — when you went in, in local time. The dive's times are all
  local, and shown as they are.
- `duration` (number, derived) — how long the dive lasted, in seconds unless the file says
  otherwise. From the primary profile, or written by you where there is no recording. It is what
  says when the dive ended, there being no field for that.
- `dive_number` (whole number) — your own numbering, if you keep one. Not every diver
  numbers every dive, and Yemoja does not require it. This is unrelated to the number at
  the end of the item's id.
- `max_depth` (number, derived) — the deepest point reached, from the primary profile. Correct
  it for a dive you kept no recording of; for one you did, the figure belongs on the recording.
- `average_depth` (number, derived) — how deep the dive was on average, weighted by time
  rather than by sample. A computer records unevenly, so counting samples would let a slow
  ascent drag the figure down for no reason. Correct it for the same reason as `max_depth`.
- `dive_site` (reference or text) — where the dive was. A plain name is allowed for a place
  you keep no site for: nothing is worked out from it, so a dive named this way has no water
  type, no elevation and no place on the map.
- `deco` (true or false, derived) — whether the dive went past the no-decompression limit,
  so that stops were required on the way up. Taken from the primary profile: a `decostop` above
  zero at any point means yes, and one recorded but never above zero means no. Where there is
  no `decostop`, `no_deco_time` decides: reaching zero means yes, and never reaching it means
  no. Most computers write stops only when there are stops, which is why the second reading
  matters. A zero before the first positive value is ignored: some computers read zero at the
  surface before they have calculated anything, and a dive cannot begin in deco.

  Where the recording has neither, or there is no profile at all, nothing is calculated
  and the field is empty for you to answer. Yemoja will not decide this one for you — your
  computer decided it at the time, with you in the water and with settings this
  application cannot reproduce, and a second opinion arrived at years later would be
  answering a different question.
- `buddies` (list of references or text) — who you dived with. Plain names are allowed
  for people you have no item for.
- `buddy_count` (whole number, derived) — from the list. Correct it when you remember how
  many people were there but not all their names.
- `rating` (whole number) — what you made of it, from 1 to 10.
- `previous_dive` (reference) — the dive you were still carrying gas from when you went
  back in. Leave it out for a dive you started clean, which is most of them. Yemoja does
  not calculate this from the clock: whether a surface interval was long enough to ignore
  is a judgement, and any threshold that decided it for you would be wrong for somebody.
  It must name a dive in this logbook that ended before this one began.
- `surface_interval` (number, derived) — how long you were out of the water before this
  dive, from `previous_dive`'s end time to this dive's start. Nothing is calculated when
  `previous_dive` is unset, and where it names a dive that is not in this logbook or that
  ended after this one began, the interval is shown as something that cannot be calculated. Write it yourself for a dive whose predecessor is not in this
  logbook — an imported dive often knows the interval without knowing the dive.
- `dive_trip` (reference) — the trip this dive was part of. Where a trip has legs, name
  the leg: a trip's list of dives gathers its own and those of everything beneath it.
- `operator` (reference) — who you dived with.
- `tags` (list of text) — your own labels. Anything you like; the usual ones are `wreck`,
  `night`, `drift`, `course`, `teaching`, `training`, `cave`, `cavern` and `ice`, and what you
  have written on other dives is offered too.
- `environment` (owned item) — the conditions you found.
- `gear` (owned item) — what you took, and how it performed. Described under *Dive gear*
  below.
- `profiles` (keyed owned items) — the depth records through the dive, one of them the
  primary.
- `gas_sources` (keyed owned items) — what you breathed from.
- `name` (text, derived) — the dive's date and its number within that day, as
  `2026-02-23#0`. This is what a dive is listed and linked as.
- `planned` (true or false, derived) — whether this dive is still ahead of you. True where the
  dive holds profiles and every one of them is a plan; false the moment a recording arrives beside
  them, and false for a dive with no profile at all, which is how a dive typed out of a paper
  logbook reads.

  **A planned dive is shown, marked and counted nowhere.** It sits in the dive table like any
  other dive, marked as a plan, and you can open and edit it — but it is left out of your dive
  count, your hours underwater, the places you have dived and every other total. It is left out of
  what Yemoja exports as well, since a file handed to another application has no way of saying a
  dive was only intended.

  Two things it is not left out of. A figure over dives you picked yourself counts what you
  picked, because you did the picking. And a recording coming off your computer is matched against
  planned dives like any other, which is how the dive you planned and the dive you made end up in
  one place.
- `time_zone_offset` (number) — how far local time was ahead of GMT where the dive was made, in
  seconds unless the file says otherwise, and shown as hours and minutes: summer in Western
  Europe is `7200`, shown `+2:00`, and New York in winter is `-18000`.

  The dive's times stay as they are; this is what puts two dives on one clock. It is what makes
  a surface interval right when two dives sit in different zones, and what tells a downloaded
  dive apart from one you already have when you have crossed a zone since. A dive that says
  nothing is taken to be on GMT, which compares correctly with any other dive that says nothing.
  A download fills it in where the computer reports its zone.
- `primary_profile` (key reference) — which of them to work from: `"*p1"`. Leave it out
  when there is only one profile, since there is nothing to choose between. With several
  and none named, Yemoja cannot tell which to believe, and everything calculated from a
  profile — the times, the depths, the temperatures — is reported as something it cannot
  calculate rather than guessed at. That happens only to a file edited by hand: when Yemoja
  adds a second profile to a dive, a plan or another computer's recording, it names the one
  the dive already had as primary, so the dive goes on saying what it said.
- `remarks` (multiline text) — how the dive went. The seal that flooded, the shoal
  that came past, why you turned round early.

#### Environment

One per dive. What the conditions were.

- `current` (fixed set) — how much there was: `none`, `very mild`, `mild`, `moderate`,
  `hard` or `very hard`. The steps are about what you can do, not about speed: `mild` is
  no trouble to swim against, `hard` means even a trained diver manages it only briefly,
  and `very hard` means you cannot swim against it at all.
- `waves` (fixed set) — the same six steps, for the state of the surface.
- `visibility` (number) — how far you could see. One figure, not a range: UDDF records
  one and a range cannot be converted into it without either picking a half or inventing
  a middle.
- `air_temperature` (number) — what it was like on the surface. Nothing calculates it for
  you; write it if you want it.
- `surface_temperature` (number, derived) — the water at the surface, which is what you felt
  getting in, taken from the primary recording. Not the air: a computer that reports a
  *surface* temperature is nearly always reporting water, which is why this is a field of its
  own rather than a second source for `air_temperature`.
- `bottom_temperature` (number, derived) — the coldest water you were in, taken from the
  primary recording. Write either yourself for a dive you kept no recording of.
- `remarks` (multiline text) — the conditions in words, where six steps and a
  handful of numbers do not tell it.

#### Dive gear

One per dive. Not to be confused with a gear item, which is a piece of equipment you
own; this is what you took on one particular dive and how it served you.

- `items` (list of references) — the equipment used.
- `mass` (number) — the total mass of what you carried.
- `weight` (number, derived) — how much lead you carried: the buoyancy `mass` of every item
  in the `weights` category, added up. An item with no mass written on it adds nothing. Correct
  it when the items do not tell the whole story.
- `temperature_evaluation` (text) — how you fared for warmth. The usual answers are
  `very cold`, `cold`, `good`, `warm` and `too warm`.
- `buoyancy_evaluation` (text) — how the weighting felt. The usual answers are `way too
  heavy`, `too heavy`, `good`, `too light` and `way too light`.
- `remarks` (multiline text) — how the kit served you, beyond the two evaluations.

#### Profile

One run through the dive: what a dive computer recorded, or what you intend to do. A dive
with two computers has two profiles, and `primary_profile` on the dive says which to work
from.

**A plan is a profile with `planned` set.** It is written the same way and read the same
way, because it is the same thing said in advance: depths against time, the gas you will
breathe, and how conservative you want to be. What a decompression model makes of either is
calculated when you ask and never stored, so a plan and the recording of the dive you made
from it sit side by side and can be compared.

Every series below is a list of pairs — a time and a value — and the time is **seconds
since the profile started**, always, whatever the file says about other units. Between
two samples Yemoja assumes a straight line, which is an approximation and the more so
the wider the gap.

A gap says nothing about why it is there. A computer sampling every thirty seconds and one
that briefly lost its pressure transmitter both leave a long stretch with a straight line
across it. Where the computer noticed the loss it will have raised a `link` alarm, and
that is the only record of it.

```json
{
  "depth": [[0, 0], [30, 8.4], [60, 14.2]]
}
```

- `planned` (true or false) — whether this is a dive you intend rather than one you made.
  Leave it out for a recording, which is what every profile without it is.
- `dive_computer` (reference, derived) — the gear item that recorded it. From `serial`:
  the gear item carrying the same serial is the computer. Write it yourself to say otherwise,
  or to name one you borrowed and keep no item for, which a plain name does.
- `serial` (text) — the computer's serial number, as a download reads it off the device. It is
  what tells two computers of one model apart, and what `dive_computer` is calculated from:
  put the same serial on the gear item and every recording from that computer finds it.
  Spelling does not matter — case, dashes and spaces are ignored, and a serial the maker prints
  in hexadecimal matches the number the device reports.
- `fingerprint` (list of text) — what the computer knows this recording by, written as
  hexadecimal. It is put there by a download and is not for reading: its whole use is that
  the next download can hand it back and be given only the dives made since. Nothing else
  depends on it, so a recording that lost it is a recording, and clearing it means the next
  download fetches everything again.

  More than one means your computer ended the dive part way through and started again, which
  they do when you spend a few minutes on the surface. Yemoja put the two stretches back
  together as one dive and kept a token for each. It always writes the list, `["a1b2"]` even
  for one; a lone token written plainly, as an older logbook has it, is read as a list of one
  and needs no changing.
- `start_date` (date) — the day the recording began, as the computer had it, or the day a plan
  is to begin.
- `start_time` (time) — the moment it began, as the computer had it, or the moment a plan is to
  begin.

- `recorded_time_offset` (number) — how far the computer's clock read ahead of the local time,
  which is what has to come off the two above. Seconds, unless the file says otherwise, and
  shown as hours and minutes. Yours to write: a download never does.

  It covers three things at once, because they are one thing to arithmetic: a clock that has
  drifted, a computer left on home time in another country, and one that missed the change to
  summer time. A computer still on Belgian winter time in Egypt reads an hour behind, so it
  writes `-3600`, and a recording that says `09:00` began at `10:00` local time. One whose clock
  gained two minutes writes `120`.

  A recording keeps what the computer said, so correcting a clock you find was wrong means
  changing this one number and nothing else.

  **Everything calculated from a recording is local time**, this having been applied — a dive's
  own date and time among them. Where local time stood against GMT is the dive's own
  `time_zone_offset`, which this does not touch.

  The correction moves the date as well as the time where it has to: two minutes past
  midnight, with two hours coming off, is late the previous evening.

- `duration` (number, derived) — how long it ran, from its last sample. Correct it where the
  recording stopped before you surfaced.
- `max_depth` (number, derived) — the deepest sample this recording took. Worth correcting: a
  dive computer usually reports a better figure than its own samples, which it takes only every
  few seconds, and a download writes what the computer said.
- `average_depth` (number, derived) — how deep this recording was on average, weighted by time
  rather than by sample, and corrected the same way.
- `water_type` (fixed set) — what the computer was **set to** while it recorded: `salt`,
  `fresh` or `en13319`. Not what the water actually was — you can dive the sea with a
  computer set to fresh, and the depths it wrote down will say so.
- `density` (number, derived) — how heavy the water was taken to be, in kilograms per cubic
  metre unless the file says otherwise. Fresh is 1000 and `en13319` is exactly 1020, both of
  them fixed. Salt is whatever the computer was set to, so it comes from the `salt_density` of
  the gear item in `dive_computer`, or 1030 where there is no computer, no item for it, or no
  figure on it. Nothing is calculated where `water_type` is not written, which is the case for
  a recording imported from UDDF. Write it in yourself if you know better.
- `atmospheric_pressure` (number) — the air above this run, and **absolute**: about 1 bar at
  sea level, less up a mountain. Unlike a cylinder's pressure this is not what any gauge reads
  against; it is the pressure itself. A download writes what the computer measured, and a plan
  writes what it assumes. Each recording carries its own, so two computers on one dive keep
  their own readings.
- `bottom_temperature` (number, derived) — the coldest water this recording sampled, from its
  own samples where the computer reported no figure of its own.
- `surface_temperature` (number) — the water at the surface, as this computer reported it.
- `deco_model` (text) — which decompression model the computer was running: `buhlmann`,
  `vpm`, `rgbm` or `dciem`. Anything you like, since a maker may use something else.
- `gradient_factor_low`, `gradient_factor_high` (number) — how conservative a Bühlmann
  computer was set to be. Written from 0 to 1 like any other proportion, so a computer set
  to 30/70 records `0.3` and `0.7`. Only Bühlmann has them.

- `conservatism` (whole number) — the setting a computer offers instead of, or alongside,
  gradient factors. It is the dial position and nothing more: `2` means one thing on one
  make and something else on another, so it is worth recording and not worth comparing.

  These three say what the numbers above were calculated with. **They do not let Yemoja
  recompute anything** — the device also knew the diving you had done before, which is not
  here — but a `decostop` read years later means little without knowing whether the computer
  was set to 30/70 or to 85/85.

- `no_flight_time` (number) — how long the computer said to wait before flying, at the
  end of the dive.
- `desaturation_time` (number) — how long it reckoned you would take to offgas.
- `tolerances` (owned item) — how much detail was dropped when the recording was taken
  in, where that is known. Described below.
- `depth` (series) — how deep, throughout. It runs to the moment you surfaced and stops there.

  A computer does not end a dive the moment you reach the surface — it waits a while in case you
  go back down, and how long it waits is a setting. Whatever it recorded during that wait is not
  diving, so a download or an import cuts it off: the last sample not known to be within a metre
  of the surface, the one after it so that the line still reaches the surface, and nothing more. What the computer itself said
  about the dive is left alone, since it had already stopped counting when you surfaced.

  Surfacing part way through a dive keeps everything, that being diving either side of it. And a
  recording that never went below a metre is left whole: nothing in it says where the dive ended.
- `temperature` (series) — how cold, throughout. Often sampled far less often than
  depth, which is why it is a series of its own rather than a column beside it.
- `decostop` (series) — the stop it was holding you to, throughout. A stop is a rounded
  depth rather than a continuous ceiling — three metres, six, nine — and a computer may
  skip the shallowest depending on how it is set.
- `no_deco_time` (series) — how much longer it said you could stay, the no-decompression
  limit, which a graph titles `NDL` as divers write it. A computer writes this
  or a `decostop`, never both: once it is holding you to a stop there is no such time, and the
  series simply stops until the stop clears. A graph leaves that stretch blank rather than
  drawing a line through it.
- `cns` (series) — the central nervous system oxygen clock the computer was keeping, as a
  percentage. It runs past 100 on a long or deep dive, and the computer decides when to
  say so.
- `otu` (series) — oxygen tolerance units accumulated, which is a count and not a
  percentage. A different measure of a different risk, on its own scale.
- `alarms` (series) — what the computer warned about, and when. Each is one of `ascent`,
  `breath`, `deco`, `error`, `link`, `microbubbles`, `rbt`, `skincooling` or `surface`.
- `gas_switches` (series) — which gas you were on and when, each naming the `gas_sources`
  entry: `[[0, "*g1"], [1260, "*g2"]]`. **An entry at the start says the gas you went in on**,
  and the rest are the changes. Before the first entry nothing says what you were breathing.
  Most Shearwaters report the starting gas on the first sample of the dive and that is what is
  written. A computer that reports only its changes writes just the switch to a deco gas, and a
  computer that reports nothing leaves the series out; either way the gas you started on is not
  recorded, and it is worth writing in, since without it Yemoja's model will not work the dive
  out.

  A reading naming the gas you were already on is dropped, being a repeat rather than a change.
  A dive your computer cut in two brings one along at the second stretch's start.
- `pressures` (keyed series) — gauge pressure left in each cylinder, throughout: one series
  under each `gas_sources` key it measured, so a dive on twins with a stage has three,
  and two computers watching one cylinder keep their readings apart.
- `sac` (series, derived) — how much gas you were breathing, as litres a minute at the
  surface's pressure, throughout: SAC, for surface air consumption. Calculated from the pressure
  of whichever cylinder you were breathing, between each two readings of it, from its `volume`
  and the depth you were at. A stretch in which you switched to another cylinder is left out,
  and so is one whose cylinder has no `volume`. Nothing is calculated without pressures, and
  nothing is stored.
- `previous_profile` (key reference, derived) — the run whose gas you were still carrying when
  this one began, written `"@2026-09-20#0*p1"`: the dive, and which of its profiles. Calculated
  from the dive's own `previous_dive` and that dive's primary profile.

  **Write it on a plan to say which earlier plan it assumes.** Two plans for the morning are two
  things that might happen, so a plan for the afternoon says which of them it follows, and a chain
  of plans sits beside the chain of dives you actually made.
- `gas_sources` (keyed owned items) — the cylinders this run uses, where they are its own rather
  than the dive's.

  **A plan keeps its own, and a recording never does.** One dive was breathed once however many
  computers watched it, so every recording shares the dive's; but two plans for the same dive
  are free to assume different mixes, different fills and different cylinders, and each keeps
  what it assumes.

  A profile that keeps none names the dive's, which is what `gas_switches` and `pressures` do on
  every recording. A profile that keeps its own names those instead, through the same fields.

**What the planner was set to.** A plan saved from the planner keeps the settings it was made
under and the lines you typed, so opening it again gives back the plan you saved rather than one
under today's settings. A recording has none of these. They are shown when you edit a plan and
not otherwise, being the planner's rather than yours to read. Each number has a setting of the same name
with `default_` in front, which is what a new plan starts from; [settings.md](settings.md) says
what each does.

- `po2_max_bottom`, `po2_max_deco`, `po2_min` (number) — the most oxygen a bottom or bailout
  gas is breathed at, the most a deco gas is, and the least any gas is, in bar.
- `descent_rate`, `ascent_rate` (number) — in metres a minute, for a line that gives neither a
  duration nor a rate, and for the way up.
- `last_stop` (number) — the depth the way up takes its shallowest stop at.
- `gas_switch_stops` (true or false) — whether the way up stops to switch to a richer gas where
  no deco stop is owed, rather than waiting for a stop or the surface. A new plan starts with it
  off.
- `safety_stop_depth`, `safety_stop_duration` (number) — the safety stop. A duration of 0 means
  none.
- `panic_factor` (number) — how many times their usual SAC each of two divers sharing gas
  breathes at, in the gas reserve.
- `problem_solving_time` (number) — how long the gas reserve spends at the depth trouble starts
  before the way up begins.
- `lost_gas_reserve` (true or false) — whether the gas reserve tries losing a cylinder.
- `lost_gas` (key reference) — which cylinder it loses, naming a `gas_sources` entry. Left out,
  it loses the first deco cylinder.
- `shared_gas_reserve` (true or false) — whether the gas reserve tries a buddy out of gas,
  sharing yours.
- `runtime` (keyed owned items) — the lines you typed, keyed by their place: `1`, `2`, `3`.
  The way up the planner added is not among them, being worked out again when the plan is
  opened. Described below.

  **The points are the plan, and these only make it again.** If `depth` or `gas_switches` is
  changed by hand afterwards so that the lines no longer lead to it, the plan is opened from its
  points, as a plan saved before these fields existed is.
- `remarks` (multiline text) — anything about the recording itself: a computer you
  do not trust, a transmitter that dropped out.

**Depth is not a measurement.** A computer measures the pressure around it and turns
that into a depth using the density it was set for, so every depth here has a water type
baked into it already. That is why `water_type` sits on the recording rather than only on
the site: it says what the numbers were made with, and without it Yemoja cannot work back
to pressure — which is what decompression actually needs. A profile with no `density` can
still be read and drawn; nothing can be calculated from it.

Changing `water_type` afterwards does not change the depths that were written down. It
changes what they mean, and Yemoja will say so and offer to convert them.

**`decostop`, `no_deco_time`, `cns`, `otu`, `no_flight_time` and `desaturation_time` are
what the computer said**, not what Yemoja calculates. They were calculated with that device's own model, its settings and
the diving you had done before, none of which can be reproduced here — so they are kept
as recorded rather than recalculated, and they may disagree with what Yemoja tells you
about the same dive. Both figures are honest; they are answers to different questions.

##### Tolerances

A dive computer samples far more often than is worth keeping. Yemoja thins a recording
as it comes in, dropping points that lie on a line the others already describe, and
records here how far it was willing to stray.

- `depth`, `temperature`, `pressure` (number) — the most any kept point may differ from
  what was thrown away, in that measurement's own units.
- `remarks` (multiline text) — what was known about the thinning, where the figures
  do not say it.

All of these are optional, and a missing one claims nothing. It does not mean the series
was left alone: it means nobody recorded what was done to it — a recording that arrived
from elsewhere, or one thinned by something that did not say by how much. Where a figure
is here you know what was given up; where it is not, you do not know.

The figures are not a promise about the computer's accuracy either — only about what was
discarded from what it reported.

##### Runtime line

One line of a plan, as you typed it in the planner.

- `depth` (number) — the depth the line goes to, or stays at.
- `duration` (number) — how long the line takes.
- `rate` (number) — how fast it goes, in metres a minute, where it was given instead of a
  duration. A line with neither moves at the plan's `descent_rate` or `ascent_rate`.
- `gas_source` (key reference) — the cylinder the line switches to, naming one of the plan's
  `gas_sources`. Left out, the line breathes what the line above it breathes.
- `remarks` (multiline text) — anything about the line: why it is there, what it is for.

#### Gas source

One entry for each cylinder you breathed from on the dive, so a dive on several gases
keeps them apart. They are not in any order of their own: which you went in on is in the
profile's `gas_switches`, not in where the entry happens to sit.

A plan keeps entries of the same shape for the cylinders it assumes, under its own
`gas_sources`. Everything below is written the same way there.

- `gas_type` (gas) — what was in it: `AIR`, `EAN32`, `TMX18/35`.
- `start_pressure` (number) — what the gauge read as you went in.

- `end_pressure` (number) — what it read as you came out.

  Both are **gauge** pressure: what the needle showed, which is zero for an empty
  cylinder at the surface. That is what you read and what you write, and Yemoja adds the
  atmosphere itself wherever a calculation needs the absolute figure.

- `usage` (text) — what it was for. Anything you like; the usual ones are `bottom`,
  `stage`, `deco`, `travel` and `bailout`.
- `configuration` (text) — how it was carried. Anything you like; the usual ones are
  `back mounted`, `sidemount`, `pony` and `staged`.
- `cylinder` (reference) — the gear item it was, where it is one you own. Leave it out
  for a rented or borrowed cylinder you have no item for.

- `volume` (number, derived) — how much the cylinder holds, taken from the `capacity` of the
  gear item named in `cylinder`. Write it yourself where no cylinder is named, or where the one
  you had was not the one recorded.

  Calculating it needs a `cylinder` that really is one — a gear item in the `cylinder`
  category, with a `capacity` written on it. Where it is not, Yemoja reports that it
  cannot work the volume out rather than leaving the field looking empty: pointing at a
  regulator, or at a cylinder whose capacity was never filled in, is a mistake worth
  seeing. Writing a volume yourself settles it either way.
- `sac` (number, derived) — how much gas you breathed from this source, as litres a minute at
  the surface's pressure, over the time you were breathing it. Taken from the primary profile's
  `sac`, and counting a long stretch for more than a short one. Write it yourself where there is
  no recording with pressures to give one. On a plan it is always yours to write, and it is what
  the gas a plan needs is calculated from.

  **What went in is an ideal gas.** A cylinder's drop in bar times its volume is taken as the
  gas it gave; a real gas at 200 bar holds a few percent more, which is not corrected for. Where
  a recording says nothing of the water, it is taken to be at the standard 1020 kilograms per
  cubic metre most computers assume, and where the dive says nothing of the air, sea level.

- `remarks` (multiline text) — anything about the cylinder or the fill.

### Dive trip

Diving done on one occasion: a week on a boat, a weekend at a lake, a fortnight moving
between three islands.

Most trips are one item and nothing more. Where a trip has legs — different places, or
different people to dive with — each leg is a trip of its own naming the larger one as
its `parent`. A dive always belongs to the trip it was actually on, which is the leg
rather than the fortnight.

- `name` (text) — the item's id is derived from it.
- `start_date`, `end_date` (date, derived) — when the trip ran: the day its first dive
  started and the day its last dive started.
  Correct them where the trip was longer than the diving — a travelling day at either end — or
  where you have set it up before logging anything.
- `region` (reference) — where it went.
- `operator` (reference) — who ran it.
- `parent` (reference) — the larger trip this one is part of, where there is one.
- `parts` (list of references, derived) — the trips naming this one as their parent. This
  follows from their `parent` in the same way.
- `dives` (list of references, derived) — the dives made on this trip, and on any trip
  beneath it. You never list them here: each dive says which trip it belongs to, and this
  follows from that, so the two can never disagree.
- `remarks` (multiline text) — how the trip went as a whole, which is not the same
  as how any one dive on it went.

### Gear

A piece of equipment. A dive computer is gear like anything else you own.

- `name` (text) — the item's id is derived from it.
- `brand` (text)
- `model` (text)
- `serial` (text) — the serial number, where it has one. For a dive computer, put the serial
  from its own information screen here: every recording it makes finds the item by it.
- `access_code` (text) — for a dive computer that shows a code you must type before it talks,
  the key it hands back once you have. A download puts it here, as hexadecimal, and hands it
  back next time so the code is not asked again. Not for reading; clear it and the computer
  asks once more.
- `generic` (true or false, derived) — whether this describes a *kind* of item rather than
  one you own. Leave it out and it is `false`: your own gear is your own. Write it to say
  otherwise. See below.
- `category` (text) — the broad group it belongs to. Anything you like; the usual ones
  are `ABC`, `BCD`, `regulator`, `cylinder`, `suit`, `weights`, `instruments`,
  `lighting`, `photography` and `accessory`. `instruments` covers anything you read: a
  dive computer, a compass, a depth or pressure gauge, a watch.
- `kind` (text) — what the item actually is, more finely than the category: a `wing` or
  a `jacket` within `BCD`, a `drysuit` or `gloves` within `suit`.
- `description` (text)
- `capacity` (number) — for a cylinder, how much water it would hold. This is the figure
  a metric cylinder is named by: a twelve-litre has a capacity of 12. An American
  cylinder named for the gas it delivers — a forty, an eighty — is not named by this, so
  write the water capacity rather than the number in the name.

- `salt_density` (number) — for a dive computer, how heavy it takes salt water to be, in
  kilograms per cubic metre unless the file says otherwise. Look it up in the computer's own
  manual and write it once.

  It matters because **a computer never measures depth**. It measures the pressure around it
  and divides by an assumed density, so the figure the maker chose is baked into every depth
  it wrote. Two computers on one dive, both set to salt, disagree by the better part of a
  metre at forty because one assumed 1025 and the other 1035.

  Leave it out and Yemoja uses 1030, which is the usual figure. Fresh water and `en13319` do
  not need it: those are 1000 and exactly 1020 whatever the computer is.

- `buoyancy` (owned item) — what the item does in the water.
- `maintenances` (keyed owned items) — what has been done to it and when, described
  under *Maintenance* below.
- `dives` (list of references, derived) — the dives it was taken on, which follows from
  the `items` of each dive's gear and, for a dive computer, from the `dive_computer` of each
  profile. For a generic item this is every dive that listed the
  kind, which is not the same thing, as the note below says.
- `remarks` (multiline text) — anything about the item: how it fits, what it came
  with, where you bought it.

A **generic** item is a description of equipment in general — a five millimetre wetsuit,
a two kilogram weight — rather than a particular thing sitting in your garage. Most of
the gear supplied with Yemoja is generic.

Referring to a generic item twice makes no claim that it was the same item both times.
Two dives that both list a generic weight belt were not necessarily done with the same
belt, and Yemoja will not tell you how many dives that belt has done, because there is
no *that belt*. This is the same idea as writing a buddy's name without an item for them
— except that a generic item still carries properties, so weighting and buoyancy can
still be calculated.

Anything identifying a single item belongs on an item that is not generic: a serial
number, a service history, the dives it has been on.

#### Buoyancy

One per gear item. What the item does to your
buoyancy, so that weighting can be calculated from the kit you took.

- `mass` (number) — how much the item weighs.
- `displaced_volume` (number) — the volume of water it pushes aside: the item itself,
  and any gas sealed inside it. Not the same as a cylinder's `capacity`, which is what
  fits inside it: a twelve-litre cylinder holds 12 and displaces rather more, because its
  walls take up room too.

- `compressible_fraction` (number) — how much of `displaced_volume` is gas rather than
  solid, from 0 to 1, so that a suit losing buoyancy with depth is accounted for.

  A wetsuit is mostly sealed bubbles in rubber. Pressure squeezes the gas and leaves the
  rubber alone, so what an item displaces at depth is

  > the solid part, plus the gas part divided by the **absolute** pressure in bar — one
  > at the surface, two at ten metres, four at thirty.

  At ten metres the gas is halved, at thirty it is a quarter. A steel backplate is nearly
  all solid and displaces the same at forty metres as at the surface; a 7 mm one-piece is
  mostly gas and loses most of its lift. One number places an item between the two.

  **It is not the foam's real gas content**, which is higher. Neoprene's cell walls carry
  some of the load, so the bubbles do not squeeze quite as freely as loose gas would. What
  belongs here is the fraction that *behaves* as gas — the figure that reproduces the
  buoyancy you actually lose. If your 5 mm suit loses about half its lift by ten metres,
  the number that says so is right, whatever the foam is made of.

- `lift_volume` (number) — the gas the item can take on, over and above
  `displaced_volume`. A wing, a lift bag and a drysuit are all this: something with a
  volume of its own that a valve makes larger.

  The two figures split at **minimum gas**. A drysuit's `displaced_volume` is the suit
  and its undersuit with just enough in it not to squeeze — the least it can ever
  displace, which is the figure weighting has to work from — and `lift_volume` is what
  can be added beyond that. A wing splits the same way and happens to start near nothing.

  **Depth does not shrink `lift_volume`.** It is gas you put in and let out, so at thirty
  metres a wing holds whatever you have put there — not a quarter of what it held at the
  surface. Only `displaced_volume` is squeezed, and only its gas part. That is the whole of
  the difference between the two figures: one is what the item is, the other is what you
  are doing with it. A drysuit is where it matters most, since its suit compresses and its
  inflation is you answering that.

- `remarks` (multiline text) — how the figures were arrived at: weighed, measured in
  a pool, or taken from the maker.

  Water that floods in and out is not part of the item and is not counted. **A soaked
  wetsuit is heavier on the boat and behaves exactly as it did before in the water**,
  because the water it took on displaces its own weight; there is nothing to record and no
  figure that changes. A suit that has grown genuinely less buoyant over the years has
  lost gas from the neoprene itself — that is a smaller `displaced_volume`, corrected on
  the item.

Nothing here records the gas in a cylinder. Its weight follows from the cylinder's
`capacity`, the pressure at the time and what is in it, so recording it on the gear item
would be storing an answer that changes through every dive.

#### Maintenance

A list on a gear item, one entry for each time work was done, so the history stays
intact.

- `type` (text) — what was done. Anything you like; the usual ones are `visual
  inspection`, `repair`, `service` and `cleaning`.
- `date` (date) — when it was done.
- `valid_until` (date) — when the next one falls due. A date, always: where a service
  interval is counted in dives rather than months, calculate roughly when that will fall
  and write it.
- `days_left` (whole number, derived) — how long until `valid_until`.
- `expired` (true or false, derived) — whether it has passed.
- `follow_up_type` (text) — what falls due then, where it is not the same as `type`. A
  repair that resets the service clock says `service` here; a repair is something that
  happened, not something owed.
- `operator` (reference) — who did the work.
- `remarks` (multiline text) — what was found, which is often worth more than the
  fact that the work happened.

**An item can owe more than one thing at once**, and this list holds them all. A cylinder
needs a visual inspection every year and a pressure test every five: those are not
alternatives but two obligations running side by side. Each entry sets the clock for one
of them — the one named in `follow_up_type`, or its own `type` where that is not given —
so the inspection falling due does not hide the test, and having the test done does not
make the inspection look current.

**The latest entry for each counts.** Doing an inspection again replaces the one before it,
which owes nothing any more, so keeping the old entry as history is safe. Latest is by `date`;
between entries with no date, by `valid_until`.

Entries with no `valid_until` owe nothing and start no clock. A cleaning is worth
recording and is not a due date.

### Region

A part of the world: a continent, an ocean, a country, a sea. Many regions come with
Yemoja, so you normally only record one it does not already know.

- `name` (text) — what the region is called. The item's id is derived from
  it.
- `category` (text) — what sort of region it is. Anything you like; `world`, `continent`,
  `ocean`, `sea`, `country` and `area` are the usual ones, and are what the supplied
  regions use — `area` for anything inside a country, from a coastline to a marine park.
- `parents` (list of references) — the larger regions this one belongs to. There can be
  more than one, since a region often sits inside several at once.
- `children` (list of references, derived) — the regions that name this one as a parent. You
  never write this: it follows from the `parents` of every other region, including the ones
  supplied with Yemoja. Adding a country to your own logbook makes it appear among the children
  of its continent, without that continent being touched.

- `west`, `east`, `south`, `north` (number) — the four edges of a box containing the
  region, used to place it on a map. All four are in degrees: `west` and `east` are
  longitudes, `south` and `north` latitudes.

  `east` is the edge you reach travelling **east** from `west` — which is what makes the
  date line unremarkable. The Pacific runs from `west: 120` to `east: -70`, and that is
  simply where it starts and where it ends, not a mistake and not something Yemoja will
  correct. Latitude does not wrap, so `north` is always above `south`.

- `dive_sites` (list of references, derived) — the sites naming this region in their
  `regions`. Only those: a site in a region inside this one is that region's.
- `remarks` (multiline text) — what you know about the region that the box and the
  category do not say.

### Dive site

A place you dive.

- `name` (text) — what the site is called. The item's id is derived from it.
- `alternative_names` (list of text) — other names the site goes by. Useful where it is
  called one thing locally and another on the chart.
- `regions` (list of references) — the regions the site lies in. There can be more than
  one.
- `environment_type` (fixed set) — what kind of place it is: `ocean`, `sea`, `lake`,
  `quarry`, `river`, `spring`, `cave`, `cavern`, `pool`, `under ice` or
  `hyperbaric chamber`, and nothing else. A closed list because it has to be written out
  to other formats, which have closed lists of their own; anything outside it could not be
  exported. This says what the site *is*, not how dense the water is — that is
  `water_type`, and the two do not follow from each other: a cave can be salt or fresh,
  and a salt lake is neither an ocean nor fresh water.
- `water_type` (fixed set) — `salt`, `fresh` or `en13319`, and nothing else. What the
  water at this site is actually like. It is not what your depths were computed with —
  that is the same field on a recording, which says what your computer was set to, and
  the two can disagree. `en13319` is the nominal density laid down by the
  European standard for depth gauges, which is what many dive computers use in place of
  either real value.
- `entry` (text) — how you get into the water here. Anything you like; the usual ones are
  `shore`, `jetty`, `steps`, `rope`, `boat` and `poolside`.

  It belongs to the site because that is where it stays the same: a quarry with a jetty has
  one every time you dive it. Where a dive was different — a zodiac drop on a house reef you
  normally walk into — the dive's `remarks` say so.
- `max_depth` (number) — how deep the site goes. The site's own depth, not how deep you
  went: a dive there may have turned round anywhere above it.
- `rating` (whole number) — what you make of the site, from 1 to 10. Your view of the
  place itself, which is not the same as your view of a dive you did there.
- `substrate` (text) — what the ground is made of, in whatever words suit: `sand`,
  `silt over rock`, `broken shale and weed`. No list to choose from; it is a description,
  not a classification, so write what you saw rather than looking for the nearest word.
  The word covers a quarry floor and a lake bed as readily as a seabed, and keeps clear of
  `bottom` on a gas source, which is the gas you breathed at depth.
- `facilities` (list of text) — what is there. Anything you like; the usual ones are
  `parking`, `filling station`, `nitrox`, `trimix`, `300bar`, `toilets`, `showers`,
  `changing rooms`, `rinse tanks`, `gear rental` and `food`, and what you have written on other
  sites is offered too.
- `elevation` (number) — the height of the water above sea level. It matters for more
  than the map: diving at altitude changes how a dive is calculated.
- `longitude`, `latitude` (number) — where it is, in degrees.
- `dives` (list of references, derived) — the dives made here. You never list them: each
  dive names its site, and this follows from that.
- `remarks` (multiline text) — how to dive the place: entries, hazards, where to
  park, what the tide does. At a wreck, what is known of the ship goes here too: what she
  was, when she sank, how big she is. Where a book quotes a tonnage that will not say which
  kind it is, give it in the book's own words rather than guessing a unit.

### Person

Anyone who appears in your logbook: the people you dive with, your instructors, your
emergency contacts, and yourself. They need not be divers.

- `name` (text, derived) — the person's full name, assembled from `first_name`, `middle_names`
  and `last_name`, and what
  Yemoja works the item's id out from. Correct it whenever the assembly is wrong: names do not
  all follow the same pattern, and yours is the one that counts.
- `first_name` (text)
- `middle_names` (text) — all of them together, if there are several.
- `last_name` (text)
- `birthday` (date)
- `email` (text)
- `phone` (text)
- `address` (text)
- `instructor_number` (text) — their number as an instructor, where they are one. Text
  rather than a number: it may carry letters and leading zeros, and nothing is ever added
  up. Someone who instructs for two agencies has two, so record the one that matters to you
  and put the other in `remarks`.
- `emergency_contacts` (list of references or text) — who to contact about this person
  in an emergency. A plain name works if you do not want a full item for them.
- `medical` (owned item) — this person's health details.
- `insurance` (owned item) — cover this person holds.
- `courses` (keyed owned items) — the qualifications this person has earned, described
  under *Course* below.
- `dives` (list of references, derived) — the dives this person was a buddy on, which
  follows from each dive's `buddies`. On the person the logbook belongs to, this is every
  dive in it.
- `remarks` (multiline text) — whatever you want to keep about them that has no
  field of its own.

#### Medical

One per person. Health details, kept together rather
than scattered through the item.

- `last_medical_check` (date) — when this person was last examined.

  **Home takes your own medical to run a year from this**, and warns you a month before it falls
  due and once it has. Nobody else's medical is warned about. That is a guess: nothing here records how long your certificate runs, and a
  year is the common answer rather than the rule — age, the authority and your employer all
  change it. The warning says the check falls due rather than claiming your certificate
  expired. Where the year is wrong for you, what fixes it is writing the check date the
  certificate's own interval implies.
- `blood_group` (text)
- `height` (number)
- `body_mass` (number)
- `remarks` (multiline text) — anything worth keeping: an allergy, a medication,
  what the examiner said.

#### Insurance

One per person. The policy they hold.

When you renew, change the dates on the policy that is there rather than adding
another. It is the same cover continuing.

- `name` (text) — the insurer, or what the cover is called.
- `policy` (text) — the policy number.
- `start_date` (date) — when the cover begins.
- `end_date` (date) — when it runs out.
- `days_left` (whole number, derived) — how much longer the cover runs.
- `expired` (true or false, derived) — whether it has run out.
- `remarks` (multiline text) — what the cover includes, and what it does not.

#### Course

A list on a person, one entry for each qualification earned.

- `certification` (reference) — which qualification it was.
- `number` (text) — the number on the card, which identifies this award rather than the
  qualification. Text, for the same reason as `instructor_number`.
- `date` (date) — when it was granted.
- `instructor` (reference) — who taught it. Their own number is on them, not here: a course
  points at the person and the person carries it.
- `dives` (list of references) — the dives that formed part of it.
- `remarks` (multiline text) — how it went, and what it covered.

### Operator

Anyone who takes you diving or looks after your gear while you are there: a dive
centre, a club, a resort, a boat.

- `name` (text) — the item's id is derived from it.
- `alternative_names` (list of text) — what they were called before. Dive centres are
  bought and rebranded, and the dives you did there were with the old name; keeping it
  here means searching for either one finds the place.
- `region` (reference) — where the operator is.
- `address` (text)
- `phone` (text)
- `email` (text)
- `website` (text)
- `category` (text) — what sort of operation it is. Anything you like; the usual ones
  are `dive center`, `hotel`, `dive resort`, `boat operator`, `dive club` and
  `liveaboard operator`.
- `rating` (whole number) — what you make of them, as a whole number from 1 to 10.
- `dives` (list of references, derived) — the dives made with them, which follows from
  the `operator` on each dive.
- `dive_trips` (list of references, derived) — the trips they ran, which follows from
  each trip's `operator`.
- `remarks` (multiline text) — what they were like to dive with.

### Certification

A qualification as an agency awards it — not one person's award of it. Your own
qualifications are recorded as courses on your person item.

- `name` (text) — the item's id is derived from it.
- `abbreviation` (text) — the short form it is usually known by.
- `organisation` (text) — who awards it, written as a plain name.
- `category` (text) — what sort of qualification it is. Anything you like; the usual
  ones are `progression`, `technical`, `specialisation` and `professional`.
- `max_depth` (number) — the depth the qualification is granted for.
- `supersedes` (list of references) — the qualifications this one replaces. An advanced
  qualification supersedes the one before it.
- `remarks` (multiline text) — what the qualification covers, and what it required.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
