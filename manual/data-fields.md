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
- **Wreck** — a ship on the bottom, which is not the same thing as the site it lies at.
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
you record and a field Yemoja works out sit together where they belong together — a count
beside the list it counts, a depth beside the profile it came from.

**A field marked *worked out* is one Yemoja calculates**, and it is not stored in your files.
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
is the primary. If there is a primary profile, the start, the end and the duration are
all taken from it — the computer was there and you were busy.

With no primary profile you write the start date and the times yourself, and the rest
follow: an end time earlier than a start time means the dive ran past midnight, and the
duration follows from the two.

All five can be corrected where the working out is wrong.

- `name` (text, worked out) — the dive's date and its number within that day, as
  `2026-02-23#0`. This is what a dive is listed and linked as.
- `dive_number` (whole number) — your own numbering, if you keep one. Not every diver
  numbers every dive, and Yemoja does not require it. This is unrelated to the number at
  the end of the item's id.
- `start_date` (date, worked out) — the day the dive began, in GMT. From the primary profile,
  corrected by its `gmt_offset`, or from you.
- `start_time` (time, worked out) — when you went in, in GMT.
- `end_date` (date, worked out) — the day the dive finished. From the primary profile, or from
  your own times: an end time earlier than the start means it ran past midnight. Correct it for
  the rare dive spanning more than one night, or where the clocks moved underneath you. A dive
  with no start time leaves this blank, there being nothing to tell the two days apart.
- `end_time` (time, worked out) — when you came out.
- `duration` (number, worked out) — how long the dive lasted, in seconds unless the file says
  otherwise. From the primary profile, or from your start and end times where there is no
  recording.
- `max_depth` (number, worked out) — the deepest point reached, from the primary profile. Worth
  correcting: a dive computer usually reports a better figure than its own recorded profile,
  which is only sampled every few seconds.
- `average_depth` (number, worked out) — how deep the dive was on average, weighted by time
  rather than by sample. A computer records unevenly, so counting samples would let a slow
  ascent drag the figure down for no reason. Correct it for the same reason as `max_depth`.

- `deco` (true or false, worked out) — whether the dive went past the no-decompression limit,
  so that stops were required on the way up. Taken from the primary profile: a `decostop` above
  zero at any point means yes, and failing that a `no_deco_time` that never reached zero means
  no. Most computers write stops only when there are stops, which is why the second reading
  matters.

  Where the recording has neither, or there is no profile at all, nothing is worked out
  and the field is empty for you to answer. Yemoja will not decide this one for you — your
  computer decided it at the time, with you in the water and with settings this
  application cannot reproduce, and a second opinion arrived at years later would be
  answering a different question.

- `dive_site` (reference) — where the dive was.

- `entry`, `exit` (text) — how you got in, and how you got out. Anything you like; the
  usual ones are `shore`, `pier`, `boat`, `hard boat`, `rib`, `liveaboard`, `platform`
  and `pool`.

  Two fields rather than one because they differ more often than you would think: a drift
  dive goes in off a boat and comes out on a beach, and a shore dive in a swell sometimes
  comes out up a ladder. Where they are the same, write the same word twice — Yemoja does
  not assume one from the other, because assuming would make the interesting case invisible.

  This is a property of the *dive*, not of the site. The same quarry is a shore dive one
  day and a boat dive the next.

- `buddies` (list of references or text) — who you dived with. Plain names are allowed
  for people you have no item for.
- `buddy_count` (whole number, worked out) — from the list. Correct it when you remember how
  many people were there but not all their names.
- `rating` (whole number) — what you made of it, from 1 to 10.
- `previous_dive` (reference) — the dive you were still carrying gas from when you went
  back in. Leave it out for a dive you started clean, which is most of them. Yemoja does
  not work this out from the clock: whether a surface interval was long enough to ignore
  is a judgement, and any threshold that decided it for you would be wrong for somebody.
  It must name a dive that started earlier than this one.
- `surface_interval` (number, worked out) — how long you were out of the water before this
  dive, from `previous_dive`'s end time to this dive's start. Nothing is worked out when
  `previous_dive` is unset. Write it yourself for a dive whose predecessor is not in this
  logbook — an imported dive often knows the interval without knowing the dive.
- `primary_profile` (key reference) — which of them to work from: `"*p1"`. Leave it out
  when there is only one profile, since there is nothing to choose between. With several
  and none named, Yemoja cannot tell which to believe, and everything worked out from a
  profile — the times, the depths, the temperatures — is reported as something it cannot
  work out rather than guessed at.
- `details` (owned item) — tags, and which trip and operator the dive belonged to.
  Described under *Details* below.
- `environment` (owned item) — the conditions you found.
- `gear` (owned item) — what you took, and how it worked out. Described under *Dive gear*
  below.
- `profiles` (keyed owned items) — the depth records through the dive, one of them the
  primary.
- `gas_sources` (keyed owned items) — what you breathed from.
- `remarks` (multiline text) — how the dive went. The seal that flooded, the shoal
  that came past, why you turned round early.

#### Details

One per dive.

- `dive_trip` (reference) — the trip this dive was part of. Where a trip has legs, name
  the leg: a trip's list of dives gathers its own and those of everything beneath it.
- `operator` (reference) — who you dived with.
- `tags` (list of text) — your own labels. Anything you like; `solo` and `wreck` are
  examples.
- `remarks` (multiline text) — anything about how the dive was arranged that the
  tags and the trip do not carry.

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
- `air_temperature` (number) — what it was like on the surface. Nothing works it out for
  you; write it if you want it.
- `surface_temperature` (number) — the water at the surface, which is what you felt
  getting in. Not the air: a computer that reports a *surface* temperature is nearly
  always reporting water, which is why this is a field of its own rather than a second
  source for `air_temperature`.
- `bottom_temperature` (number) — the coldest water you were in, from the primary
  profile. Correct it where there is no profile, or where it did not measure
  temperature.
- `atmospheric_pressure` (number) — from where the dive was, and **absolute**: about 1 bar
  at sea level, less up a mountain. Unlike a cylinder's pressure this is not what any gauge
  reads against; it is the pressure itself. Correct it when you know better; weather moves
  it about, and altitude matters more than most divers expect.
- `remarks` (multiline text) — the conditions in words, where six steps and a
  handful of numbers do not tell it.

#### Dive gear

One per dive. Not to be confused with a gear item, which is a piece of equipment you
own; this is what you took on one particular dive and how it served you.

- `items` (list of references) — the equipment used.
- `mass` (number) — the total mass of what you carried.
- `weight` (number, worked out) — how much lead you carried, added up from the items. Correct
  it when the items do not tell the whole story.
- `temperature_evaluation` (text) — how you fared for warmth. The usual answers are
  `very cold`, `cold`, `good`, `warm` and `too warm`.
- `buoyancy_evaluation` (text) — how the weighting felt. The usual answers are `way too
  heavy`, `too heavy`, `good`, `too light` and `way too light`.
- `remarks` (multiline text) — how the kit served you, beyond the two evaluations.

#### Profile

What a dive computer recorded, one entry for each computer you dived with. A dive with
two computers has two profiles, and `primary_profile` on the dive says which to work
from.

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

- `dive_computer` (reference) — the gear item that recorded it. A plain name works for
  one you borrowed and keep no item for.
- `start_date` (date) — the day the recording began, as the computer had it.
- `start_time` (time) — the moment it began, as the computer had it.

- `gmt_offset` (number) — how far ahead of GMT the two above read, which is what has to come
  off them. Seconds, unless the file says otherwise, the same as any other length of time.

  A computer set to Egyptian time writes `7200`, Egypt being two hours ahead, so a recording
  that says `09:00` began at `07:00` GMT. One reading two hours behind writes `-7200`.

  It covers three things at once, because they are one thing to arithmetic: a computer set
  to the wrong time, a clock that has drifted, and a computer set to a different zone from
  the water you were in. Whatever the reason, this is the correction.

  A recording keeps what the computer said, so correcting a clock you find was wrong means
  changing this one number and nothing else.

  **Everything worked out from a recording is GMT**, this having been applied — a dive's
  own date and time among them. That is what makes a surface interval right when two dives
  sit in different countries, and what makes dives sort into the order you did them. It
  also means a dive begun just after midnight in an eastern zone is dated the day before,
  because in GMT it was.

  The correction moves the date as well as the time where it has to: two minutes past
  midnight, with two hours coming off, is late the previous evening.

- `end_date` (date, worked out) — the day the recording ended, from the last sample.
- `end_time` (time, worked out) — the moment it ended.
- `duration` (number, worked out) — how long it ran. Correct any of these where the recording
  stopped before you surfaced.
- `water_type` (fixed set) — what the computer was **set to** while it recorded: `salt`,
  `fresh` or `en13319`. Not what the water actually was — you can dive the sea with a
  computer set to fresh, and the depths it wrote down will say so.
- `density` (number, worked out) — how heavy the water was taken to be, in kilograms per cubic
  metre unless the file says otherwise. Fresh is 1000 and `en13319` is exactly 1020, both of
  them fixed. Salt is whatever the computer was set to, so it comes from the `salt_density` of
  the gear item in `dive_computer`, or 1030 where there is no computer, no item for it, or no
  figure on it. Write it in yourself if you know better.
- `deco_model` (text) — which decompression model the computer was running: `buhlmann`,
  `vpm`, `rgbm` or `dciem`. Anything you like, since a maker may use something else.
- `gradient_factor_low`, `gradient_factor_high` (number) — how conservative a Bühlmann
  computer was set to be. Written from 0 to 1 like any other proportion, so a computer set
  to 30/70 records `0.3` and `0.7`. Only Bühlmann has them.

- `conservatism` (whole number) — the setting a computer offers instead of, or alongside,
  gradient factors. It is the dial position and nothing more: `2` means one thing on one
  make and something else on another, so it is worth recording and not worth comparing.

  These three say what the numbers above were worked out with. **They do not let Yemoja
  recompute anything** — the device also knew the diving you had done before, which is not
  here — but a `decostop` read years later means little without knowing whether the computer
  was set to 30/70 or to 85/85.

- `no_flight_time` (number) — how long the computer said to wait before flying, at the
  end of the dive.
- `desaturation_time` (number) — how long it reckoned you would take to offgas.
- `tolerances` (owned item) — how much detail was dropped when the recording was taken
  in, where that is known. Described below.
- `depth` (series) — how deep, throughout.
- `temperature` (series) — how cold, throughout. Often sampled far less often than
  depth, which is why it is a series of its own rather than a column beside it.
- `decostop` (series) — the stop it was holding you to, throughout. A stop is a rounded
  depth rather than a continuous ceiling — three metres, six, nine — and a computer may
  skip the shallowest depending on how it is set.
- `no_deco_time` (series) — how much longer it said you could stay.
- `cns` (series) — the central nervous system oxygen clock the computer was keeping, as a
  percentage. It runs past 100 on a long or deep dive, and the computer decides when to
  say so.
- `otu` (series) — oxygen tolerance units accumulated, which is a count and not a
  percentage. A different measure of a different risk, on its own scale.
- `alarms` (series) — what the computer warned about, and when. Each is one of `ascent`,
  `breath`, `deco`, `error`, `link`, `microbubbles`, `rbt`, `skincooling` or `surface`.
- `gas_switches` (series) — when you changed gas, each naming the `gas_sources` entry you
  moved to: `[[1260, "*g2"]]`.
- `pressures` (keyed series) — gauge pressure left in each cylinder, throughout: one series
  under each `gas_sources` key it measured, so a dive on twins with a stage has three,
  and two computers watching one cylinder keep their readings apart.
- `remarks` (multiline text) — anything about the recording itself: a computer you
  do not trust, a transmitter that dropped out.

**Depth is not a measurement.** A computer measures the pressure around it and turns
that into a depth using the density it was set for, so every depth here has a water type
baked into it already. That is why `water_type` sits on the recording rather than only on
the site: it says what the numbers were made with, and without it Yemoja cannot work back
to pressure — which is what decompression actually needs. A profile with no `density` can
still be read and drawn; nothing can be worked out from it.

Changing `water_type` afterwards does not change the depths that were written down. It
changes what they mean, and Yemoja will say so and offer to convert them.

**`decostop`, `no_deco_time`, `cns`, `otu`, `no_flight_time` and `desaturation_time` are
what the computer said**, not what Yemoja works out. They were calculated with that device's own model, its settings and
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

#### Gas source

One entry for each cylinder you breathed from on the dive, so a dive on several gases
keeps them apart.

- `gas_type` (gas) — what was in it: `AIR`, `EAN32`, `TMX18/35`.
- `start_pressure` (number) — what the gauge read as you went in.

- `end_pressure` (number) — what it read as you came out.

  Both are **gauge** pressure: what the needle showed, which is zero for an empty
  cylinder at the surface. That is what you read and what you write, and Yemoja adds the
  atmosphere itself wherever a calculation needs the absolute figure.

- `usage` (text) — what it was for. Anything you like; the usual ones are `bottom`,
  `stage`, `deco` and `travel`.
- `configuration` (text) — how it was carried. Anything you like; the usual ones are
  `back mounted`, `sidemount`, `pony` and `staged`.
- `cylinder` (reference) — the gear item it was, where it is one you own. Leave it out
  for a rented or borrowed cylinder you have no item for.

- `volume` (number, worked out) — how much the cylinder holds, taken from the `capacity` of the
  gear item named in `cylinder`. Write it yourself where no cylinder is named, or where the one
  you had was not the one recorded.

  Working it out needs a `cylinder` that really is one — a gear item in the `cylinder`
  category, with a `capacity` written on it. Where it is not, Yemoja reports that it
  cannot work the volume out rather than leaving the field looking empty: pointing at a
  regulator, or at a cylinder whose capacity was never filled in, is a mistake worth
  seeing. Writing a volume yourself settles it either way.

- `remarks` (multiline text) — anything about the cylinder or the fill.

### Dive trip

Diving done on one occasion: a week on a boat, a weekend at a lake, a fortnight moving
between three islands.

Most trips are one item and nothing more. Where a trip has legs — different places, or
different people to dive with — each leg is a trip of its own naming the larger one as
its `parent`. A dive always belongs to the trip it was actually on, which is the leg
rather than the fortnight.

- `name` (text) — the item's id is worked out from it.
- `start_date`, `end_date` (date, worked out) — when the trip ran, taken from the dives on it.
  Correct them where the trip was longer than the diving — a travelling day at either end — or
  where you have set it up before logging anything.
- `region` (reference) — where it went.
- `operator` (reference) — who ran it.
- `parent` (reference) — the larger trip this one is part of, where there is one.
- `parts` (list of references, worked out) — the trips naming this one as their parent. This
  follows from their `parent` in the same way.
- `dives` (list of references, worked out) — the dives made on this trip, and on any trip
  beneath it. You never list them here: each dive says which trip it belongs to, and this
  follows from that, so the two can never disagree.
- `remarks` (multiline text) — how the trip went as a whole, which is not the same
  as how any one dive on it went.

### Gear

A piece of equipment. A dive computer is gear like anything else you own.

- `name` (text) — the item's id is worked out from it.
- `brand` (text)
- `model` (text)
- `serial` (text) — the serial number, where it has one.
- `generic` (true or false) — whether this describes a *kind* of item rather than one you
  own. Leave it out and it is `false`: your own gear is your own. See below.
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
- `remarks` (multiline text) — anything about the item: how it fits, what it came
  with, where you bought it.

A **generic** item is a description of equipment in general — a five millimetre wetsuit,
a two kilogram weight — rather than a particular thing sitting in your garage. Most of
the gear supplied with Yemoja is generic.

Referring to a generic item twice makes no claim that it was the same item both times.
Two dives that both list a generic weight belt were not necessarily done with the same
belt, and Yemoja will not tell you how many dives that belt has done, because there is
no *that belt*. This is the same idea as writing a buddy's name without an item for them
— except that a generic item still carries properties, so weighting and buoyancy still
work out.

Anything identifying a single item belongs on an item that is not generic: a serial
number, a service history, the dives it has been on.

#### Buoyancy

One per gear item. What the item does to your
buoyancy, so that weighting can be worked out from the kit you took.

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
  interval is counted in dives rather than months, work out roughly when that will fall
  and write it.
- `days_left` (whole number, worked out) — how long until `valid_until`.
- `expired` (true or false, worked out) — whether it has passed.
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

Entries with no `valid_until` owe nothing and start no clock. A cleaning is worth
recording and is not a due date.

### Region

A part of the world: a continent, an ocean, a country, a sea. Many regions come with
Yemoja, so you normally only record one it does not already know.

- `name` (text) — what the region is called. The item's id is worked out from
  it.
- `category` (text) — what sort of region it is. Anything you like; `world`, `continent`,
  `ocean`, `sea`, `country` and `area` are the usual ones, and are what the supplied
  regions use — `area` for anything inside a country, from a coastline to a marine park.
- `parents` (list of references) — the larger regions this one belongs to. There can be
  more than one, since a region often sits inside several at once.
- `children` (list of references, worked out) — the regions that name this one as a parent. You
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

- `remarks` (multiline text) — what you know about the region that the box and the
  category do not say.

### Dive site

A place you dive.

- `name` (text) — what the site is called. The item's id is worked out from it.
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
- `max_depth` (number) — how deep the site goes. The site's own depth, not how deep you
  went: a dive there may have turned round anywhere above it.
- `rating` (whole number) — what you make of the site, from 1 to 10. Your view of the
  place itself, which is not the same as your view of a dive you did there.
- `substrate` (text) — what the ground is made of, in whatever words suit: `sand`,
  `silt over rock`, `broken shale and weed`. No list to choose from; it is a description,
  not a classification, so write what you saw rather than looking for the nearest word.
  The word covers a quarry floor and a lake bed as readily as a seabed, and keeps clear of
  `bottom` on a gas source, which is the gas you breathed at depth.
- `wrecks` (list of references) — the ships lying there, if any. A site may hold more
  than one; a wreck may be reachable from more than one site.
- `facilities` (list of text) — what is there: parking, air fills, toilets, a slipway,
  and so on.
- `elevation` (number) — the height of the water above sea level. It matters for more
  than the map: diving at altitude changes how a dive is worked out.
- `longitude`, `latitude` (number) — where it is, in degrees.
- `remarks` (multiline text) — how to dive the place: entries, hazards, where to
  park, what the tide does.

### Wreck

A ship on the bottom. Kept apart from the dive site because the two are not the same
thing: a site is a place with coordinates, a wreck is a vessel with a history, and one
site may hold several while one large wreck may be dived from more than one.

- `name` (text) — what she was called. The item's id is worked out from it.
- `alternative_names` (list of text) — other names she went by, before a rename or a
  change of owner.
- `ship_type` (text) — what she was: `freighter`, `tanker`, `warship`, `hospital ship`.
- `nationality` (text) — where she was registered.
- `shipyard` (text) — who built her.
- `launched` (date) — when she went into the water the first time.
- `sunk` (date) — and when she went under for good.
- `length`, `beam`, `draught` (number) — how big she was.

- `displacement` (number) — what she weighed, which for a ship means the weight of water
  she pushed aside. A mass like any other, so it is in kilograms unless the file says
  otherwise: a large ship runs to eight digits.

  Wreck books quote tonnages that will not say which kind they are — long tons, tonnes,
  or gross register tonnage, which is a volume and not a weight at all. Where you cannot
  tell, leave this empty and put what the book said in `remarks`. A figure you had to
  guess the units of is worse than no figure.

- `remarks` (multiline text) — her history, and whatever a book said that no field
  here can hold.

Most of this is not diving. Where she was built and what flag she flew make no
difference underwater, and they are here because a wreck diver wants to know — the ship
is half the reason for the dive.

### Person

Anyone who appears in your logbook: the people you dive with, your instructors, your
emergency contacts, and yourself. They need not be divers.

- `name` (text, worked out) — the person's full name, assembled from the parts above, and what
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
- `remarks` (multiline text) — whatever you want to keep about them that has no
  field of its own.

#### Medical

One per person. Health details, kept together rather
than scattered through the item.

- `last_medical_check` (date) — when this person was last examined.
- `blood_group` (text)
- `height` (number)
- `body_mass` (number)
- `remarks` (multiline text) — anything worth keeping: an allergy, a medication,
  what the examiner said.

Yemoja does not work out whether a medical is still valid, and does not remind you to
renew one. How long a check counts for depends on who is asking — the agency, the
operator, the country — not on the examination itself.

#### Insurance

One per person. The policy they hold.

When you renew, change the dates on the policy that is there rather than adding
another. It is the same cover continuing.

- `name` (text) — the insurer, or what the cover is called.
- `policy` (text) — the policy number.
- `start_date` (date) — when the cover begins.
- `end_date` (date) — when it runs out.
- `days_left` (whole number, worked out) — how much longer the cover runs.
- `expired` (true or false, worked out) — whether it has run out.
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

- `name` (text) — the item's id is worked out from it.
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
- `remarks` (multiline text) — what they were like to dive with.

### Certification

A qualification as an agency awards it — not one person's award of it. Your own
qualifications are recorded as courses on your person item.

- `name` (text) — the item's id is worked out from it.
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
