# Libraries

The library files themselves.

What a library *is* — read-only, shadowed by the logbook, never deleted, named rather
than pathed, and permanent once published — is
[data/libraries.md](../data/libraries.md). That is the design; this is the folder.
Nothing here argues for a rule, and a rule stated here has been restated by mistake.

## Layout

A name maps to a file here by appending `.json`:

```
region/world        →  libraries/region/world.json
generic_gear        →  libraries/generic_gear.json
certification/mda   →  libraries/certification/mda.json
```

Names may nest where that groups them usefully, and need not where it does not. The
folders are for whoever maintains these files; the application only resolves names.

**These files are packaged into the application**, under the same names, and read from
there rather than from any directory on the machine — `LIB-6` in
[data/libraries.md](../data/libraries.md). `LICENSE` goes with them, since its terms travel
with the data. So this directory is both where they are maintained and, unchanged, what
ships.

## Format

A library file is a logbook file: an object keyed by item id, and nothing else.

```json
{
  "north_sea": { },
  "baltic_sea": { }
}
```

Everything inside is an item as `manual/data-fields.md` describes it — same field names,
same types, same references, written in the syntax `manual/data-format.md` describes.

**Units are not the same**, and every library file must say what its own are:

```json
{
  "units": {"mass": "kg", "volume": "m3"},
  "generic_drysuit": { }
}
```

A library sits outside the logbook's unit scoping — see [data/doc.md](../data/doc.md)
— so without a declaration the values fall back to SI, and a logbook writing its volumes
in litres would read a five-litre suit as five thousand.

`units` is a reserved key here: no item may be called that.

Nothing in the file says what type its items are. That comes from the logbook that
uses it, which names its libraries by type:

```json
{
  "libraries": {
    "region": ["region/world", "region/europe"],
    "certification": ["certification/padi"]
  }
}
```

So one file holds one type, and a library of regions and a library of the dive sites in
them are two files.

**The map is the one library that is not a logbook file.** `map/` holds geometry rather
than items: coastlines, lakes, borders, rivers and cities, at three scales, for drawing a
region. Nothing in a logbook names it and nothing resolves into it; the application reads
it whole. Its layout is three folders, `110m/`, `50m/` and `10m/`, the source's scales from
coarsest to finest, each holding the same five files:

```
land.txt      coastlines, as closed shapes
lakes.txt     lakes, as closed shapes
borders.txt   land borders between countries, as open shapes
rivers.txt    rivers and lake centre lines, as open shapes
cities.txt    populated places
```

A shape is one line. It is one or more rings separated by ` / `, the first the outside
and any after it a hole in it — the Caspian in Eurasia, an island in a lake; a border or
a river has one ring, open. A ring is longitude and latitude alternating, separated by
spaces, rounded to one decimal at `110m`, two at `50m` and three at `10m`, which is
finer than the source draws at that scale. No shape crosses the date line; the source
splits them there. A city is one line of five tab-separated cells: name, latitude,
longitude, the source's rank from 0 for the most important, and population.


One consequence: a library and a logbook share an id namespace, because resolution
looks in the logbook first and then here. An id chosen carelessly will be shadowed
by, or will shadow, something it has nothing to do with.

## What belongs here

Data that is true for everyone, and that a user should not have to type:

- Geography — regions, and their relationships.
- Certifications as the awarding bodies define them.
- Gear as manufacturers make it, rather than the item in someone's garage.

What does not belong: anything personal, anything one user would want changed, and
anything that varies by who is asking. A user's *correction* to a library item is
stored in their own logbook and shadows this copy; it never comes back here.

## Where the data came from

**Item the source of everything, in this section, at the time it is added.** Provenance
is trivial to note and impossible to recover afterwards, and these files are released
under CC0 — a promise nobody can make about data they cannot account for.

Ids published here are fixed: correct an item's contents freely, but never
rename or remove one. The reason is in [data/libraries.md](../data/libraries.md).

- **Regions** — written for this project. The coordinates are approximate bounding boxes
  of well-known geography, which is fact rather than anyone's work. The shape of the
  data follows the conventions of [Natural Earth](https://www.naturalearthdata.com/),
  which is itself in the public domain and could have been used directly; nothing was
  taken from it.
- **The map** — [Natural Earth](https://www.naturalearthdata.com/), which is in the public
  domain; its `land`, `lakes`, `admin_0_boundary_lines_land`, `rivers_lake_centerlines` and
  `populated_places` themes at 1:110m, 1:50m and 1:10m, taken in September 2026 from the
  GeoJSON in its maintainers' repository. Only geometry, names, ranks and populations were
  kept; every other attribute was dropped, and coordinates were rounded as the layout above
  says.
- **Certifications** — the qualifications agencies publish, recorded as fact. See the
  note on names below.
- **Generic gear** — written for this project.

An earlier set of regions was discarded rather than kept, because its source could not be
established. That is the standard: data of unknown provenance does not stay.

## Names that belong to someone else

Agencies, manufacturers and operators are named here because the items are about
them. Nothing in this folder implies affiliation or endorsement, and the notice in
README under *Trademarks* says so.

Two rules follow. Use the plain name and nothing more — no logos, no stylised marks, no
slogans. And describe what a body actually awards or makes, rather than characterising
it: this is reference data, not a review.

## Status

Populated: world regions split by continent, PADI and CMAS certifications, a catalogue
of generic gear, and the map at three scales.

No count is given here on purpose — it went stale the first time anything was added.
`tool/checkdata.py` reports the current figure and verifies that every reference
resolves.
