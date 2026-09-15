# Your logbook on disk

Yemoja keeps your logbook as ordinary text files that you can open, read and change
with any text editor. Nothing is hidden in a database. If you ever stop using Yemoja,
your dives are still there and still readable.

This page describes how those files are arranged, so you can edit them safely by hand.
It covers the shape of the files — where they sit, how items are named and referred
to, and how values are written. What each kind of item *contains* is a chapter of its
own: see [data-fields.md](data-fields.md).

Only the parts of the format that are fixed are described here. Sections will be added
as more of the application is finished.

## A logbook is a folder

Everything belonging to one logbook lives in a single folder. Copy that folder and
you have copied your whole logbook.

You choose where it goes. Yemoja suggests a place the first time, and you can put it
somewhere else — an external disk, or a folder you already sync yourself. On a phone the
choice may be narrower, because phones are stricter about where an application may write.

You can keep as many logbooks as you like; Yemoja has one open at a time.

At the top of it sits a file called `yemoja.json`. It says what the logbook is: whose it is
and which of the supplied libraries it uses. It holds no file paths — where the rest of your
data is kept is settled by the names below, not by anything you write here.

```json
{
  "user": "@jacques_cousteau",
  "libraries": {
    "region": ["region/world"]
  }
}
```

**`user` says whose logbook it is**, naming a person in it the way any other reference does.
It is optional: a logbook with nobody named is a logbook, and so is one naming a person you
have not written yet — Yemoja simply does not know whose it is until you do. There is no
field on a person saying *this is me*; the logbook says it, once, here.

Two more files sit beside it, `settings.json` and `settings.local.json`. They hold your
preferences rather than your data, they belong to the application rather than to this
format, and they are described in [settings.md](settings.md). Nothing in this chapter or
in [data-fields.md](data-fields.md) applies to them.

## Where your data is kept

Each kind of item has a name, and everything of that kind lives under it:

| Kind | Lives in |
|---|---|
| Dive | `dive` |
| Person | `person` |
| Region | `region` |
| Dive site | `dive_site` |
| Wreck | `wreck` |
| Gear | `gear` |
| Certification | `certification` |
| Operator | `operator` |
| Dive trip | `dive_trip` |

Each may be either:

- **A single file** — `person.json` — holding everything of that kind together. Good
  for things you have few of, such as the people you dive with.
- **A folder** — `dive/` — holding one file per item. Good for things you have many
  of, or that are individually large, such as dives.

Yemoja looks for both and uses whichever it finds, so you can choose differently for
each kind and change your mind later by moving the files. Having both at once — a
`dive` folder *and* a `dive.json` — is the one thing it cannot make sense of, and it
will tell you rather than guess.

Nothing lists these anywhere. There are no paths in a logbook, which is deliberate: a
path can point outside the folder, and then copying the folder no longer copies the
logbook. What you can see in the folder is what there is.

What a file holds depends on which of the two you chose:

- **A folder of one file per item** — each file *is* the item. Its fields sit at the
  top level, with no wrapper around them.
- **A single file for everything of a kind** — an object whose keys are the items'
  ids, each holding one item.

An owned item, such as a dive's `environment`, is written as an object inside the item
that holds it. Where there can be several — a person's `courses` — they are written as an
object too, each entry under a key of its own.

**A single owned item with nothing in it is not written.** `"environment": {}` says exactly
what leaving `environment` out says, so Yemoja drops it: write one by hand and the next save
takes it away again, and nothing about the dive reads differently either way. A collection of
them is left alone — `"courses": {}` stays, an empty list being something you may have meant.

## How an item is identified

Two different things are easily confused, so it is worth separating them at the start:

- An item's **name** is a field inside it, saying what the thing is called: `Anna De
  Vries`, `Zeelandbrug`, `Red Sea`. You read it, you can change it, and it is what
  Yemoja shows you.
- An item's **id** is the text other items use to point at it:
  `anna_de_vries`. It is not inside the item at all.

Where an id comes from depends on how you store the item:

- **One file per item:** the id is the file name, without `.json`. A dive in
  `dive/2026-02-23#0.json` has the id `2026-02-23#0`.
- **Several items in one file:** the id is the key it is stored under.

```json
{
  "anna_de_vries": { },
  "john_smith": { }
}
```

Yemoja works an id out from the item's `name` when the item is created, and
then leaves it alone. The two drift apart quite normally: correcting a misspelled
`name` does not change the id, because everything pointing at that item would
otherwise stop working. An id that no longer quite matches the name is not a
fault.

Nothing inside an item mentions its id, and changing one does not change
anything the item says about itself.

**What an id may contain** is narrow, because it is also a file name: lowercase
letters `a` to `z`, digits, and `_`, `-` and `.`. Nothing else, and no accented or
non-Latin letters. It may not start or end with `_`, `-` or `.`.

That looks mean, and it is deliberate. An id is a file name on Windows, macOS, Linux,
Android and iPhone, and those disagree: two of them treat `Anna` and `anna` as the same
file while Yemoja would treat them as two items, and one of them rewrites accented
letters into a different form on disk, so the same logbook synced between two machines
would grow a second copy of the same dive. Letters that look identical and are not —
a Cyrillic `a` beside a Latin one — would give you two people you could not tell apart.

**Your own language belongs in `name`, not in the id.** The id is an address; the name
is what you read, what Yemoja shows you, and what you correct when it is wrong. They are
free to differ, and for most people they will.

### When two items would share an id

Yemoja adds a number. A second Anna De Vries becomes `anna_de_vries#1`, leaving the
first as it was.

Dives always carry such a number, because several dives in one day is perfectly normal.
Two dives on 23 February 2026 are `2026-02-23#0` and `2026-02-23#1`.

**The number does not say which dive of the day it was.** It is only there to tell two
dives apart, handed out as they are created — so if you log the afternoon dive first, it
gets `#0` and the morning one gets `#1`. Which came first is what the times are for.

Deleting a dive does not renumber the others: they keep the ids they have, so anything
referring to them still points where you expect. The number you freed can be handed out
again, which is what makes deleting a dive you entered wrongly and entering it again put
everything back — anything that pointed at it points at it once more.

### Changing an id

Do not change one by editing the file yourself. Everything referring to that item
would still be pointing at the old id. Use the application, which moves the
item and everything referring to it together, in one step you can undo.

## Referring to another item

To point at another item, write its id with an `@` in front:

```json
{
  "buddies": ["@anna_de_vries"]
}
```

This means *the person stored under `anna_de_vries`* — the same person every time,
wherever they appear.

### When you do not want to name someone

You can also write a plain value, without the `@`:

```json
{
  "buddies": ["john"]
}
```

This means that you dived with someone called John without claiming to know who.
Two dives that each list `john` are **not** treated as the same person, which is what
you want when you simply cannot remember, or when two different Johns are involved.

Yemoja will never quietly turn one of these into a real reference.

## Units

Unless the file says otherwise, measurements are in these units:

| What is measured | Written in | Or one of |
|---|---|---|
| Depth, distance, length | metres, `m` | feet, `ft` |
| Mass | kilograms, `kg` | pounds, `lb` |
| Time | seconds, `s` | minutes `min`, hours `h` |
| Temperature | degrees Celsius, `C` | Fahrenheit `F`, kelvin `K` |
| Volume | litres, `l` | cubic metres, `m3` |
| Pressure | bar, `bar` | psi, `psi`, pascal `Pa` |
| Longitude and latitude | degrees, `deg` | |
| Water density | kilograms per cubic metre, `kg/m3` | |

Case matters: `C` is Celsius and `K` is kelvin, and `Pa` is the pascal. Nothing is
written with a superscript, so cubic metres are `m3`, and a compound divides with a
slash: `kg/m3`.

If a file uses something else, it says so at the top:

```json
{
  "units": {
    "length": "ft",
    "pressure": "psi"
  }
}
```

Everything in that file is then read in those units. Anything the block does not
mention keeps the unit from the table.

**Write the name exactly as it appears above**, capitals included: `C` is Celsius and
`K` is kelvin. Those are the only names Yemoja knows. A name it does not recognise means
Yemoja cannot tell what the measurements of that one kind mean, so it shows you none of
them and tells you which name it did not understand. Everything else in the file opens as
usual: an unreadable `length` costs you the depths and the distances, and leaves the
temperatures and the pressures alone.

Both halves of that are deliberate. Reading a depth in feet as though it were metres
would be silently wrong by a factor of three, so a unit that cannot be understood is
never guessed at. But the damage stops at the kind of measurement it applies to, because
a later version of Yemoja may know a name this one does not, and a file written with it
should cost you the one kind of number it affects rather than everything in the file.

**A `units` block applies to its own file and to nothing else.** There is no setting
elsewhere that changes how a file is read — not in `yemoja.json`, not in another file,
and not in a single item inside a file. To know what a number means, look at the top
of the file it is in. Nothing else can change it.

This does mean everything in one file shares the same units. If you keep both metric
and imperial cylinders, and your gear is all in one `gear.json`, they have to be
written the same way. You can put each piece of gear in its own file instead — see
*Where your data is kept* above — and then each says what it likes.

In a file holding several items, `units` sits alongside them; in a file holding one, it
sits alongside that item's own fields. So `units` is a name nothing else may take: no
item is called that, and neither is any field.

If you set units by hand, Yemoja keeps your choice. It will not rewrite your file into
different units.

**Each unit is written to a set number of decimals**, and no zeros are added on the end —
`108000`, not `108000.0`. The same figure decides what Yemoja shows you, so a number on the
screen and the number in your file are the same number.

The figure is three decimals for nearly every unit — metres, feet, kilograms, bar, degrees
Celsius. Hours and positions get six, since a thousandth of an hour is nearly four seconds
and a thousandth of a degree is a hundred metres. Cubic metres get nine, being a very large
unit for the volumes a logbook holds. Seconds and pascal get none, being small enough
already.

Each unit's figure is the finest thing anyone writes in it rather than the finest anyone
reads. Litres get three decimals because a small weight displaces `0.04` of one, even
though no depth needs that many.

Two things follow. A value finer than its unit allows is rounded when it is saved, so
Yemoja cannot keep precision your units cannot express — a depth written in feet is kept
to a tenth of a foot. And converting into a unit that does not divide evenly can move the
last digit once, the first time Yemoja writes a value it read from somewhere else; after
that the file stays as it is.

The ready-made data described below says what its own units are, so your choices never
change what it means. Your choice of litres does not change what the supplied equipment
weighs.

## What the values look like

Every field holds one of a small number of kinds of value. Each is named in brackets
after the field.

- **text** — a single line: `"Zeelandbrug"`. It may not begin with `@` or `*`, which
  mark the two kinds of pointer, and may not contain a line break, a tab, or any other
  character you would not see. Characters that reorder what is shown are refused as well,
  so that what a file says is what you read.
- **multiline text** — line breaks are allowed. Tabs are not, nor is anything else you
  would not see.

  **`@` points at things here too**, as it does in a field: writing `@willy` in
  a remark names the item with that id. It is a convention and nothing more — a remark is
  free text, so nothing has to act on a pointer and a name matching nothing is simply what
  you wrote. What an interface makes of one is up to it: a link, the item's name in its
  place, or nothing at all.

  A pointer is an `@` and the name that follows it, ending where the name does. A trailing
  `.`, `-`, `_` or `#` is punctuation rather than part of it, since no id ends with one, so
  `We met @willy, who was on his @padi_wreck course.` points at two items and the comma
  and the full stop are punctuation. A dot or a `#` inside a name is kept —
  `@generic_0.5_kg_lead_weight` is one pointer, and so is `@2026-02-23#0`. Capitals do not matter: `@Willy` at the
  start of a sentence finds the same item as `@willy`.

  **Nothing else about an `@` is examined**, because nothing could be. You may write an
  address, a handle from somewhere else, or simply mean *at* — `max depth @ 22m` — and no
  rule can tell those from a name. It does not matter: a pointer means something only
  when an item of that name is really there, and one that finds nothing is the text you
  wrote. `tom@example.invalid` is your address and stays your address.

  **Renaming an item carries your pointers with it.** Rename *willy* and every remark saying
  `@willy` says the new name afterwards, in the same step, which you can undo like any other.
  Only pointers that actually found that item are changed — an address is never touched, and
  neither is a pointer at anything else.

  The one thing that does not survive is a capital. `@Willy` finds the same person as
  `@willy`, and what it becomes is the new name as Yemoja writes it, in lower case.
- **whole number** — no decimal point: `12`.
- **number** — with or without one: `31.4`.

  A number that is a proportion is written **from 0 to 1**: `compressible_fraction` is
  `0.29`, and a gradient factor of 20 is written `0.2`. One runs from 0 to 100 instead:
  `cns`, which is quoted that way everywhere in diving.
- **true or false** — written `true` or `false`. Reading is forgiving, as it is for a
  mix: case is ignored, and `yes`, `no`, `t`, `f`, `y` and `n` are understood too, and
  written back as `true` or `false`. A number is not — `1` is a count, not a yes.
- **date** — always `"2026-02-23"`.
- **time** — always `"09:15:00"`.
- **reference** — another item's id with `@` in front: `"@anna_de_vries"`.
- **key reference** — a part of this same item, with `*` in front: `"*p1"`. Where an `@`
  points at another item anywhere in the logbook, a `*` points at one entry of one list
  inside the item you are already reading — a dive saying which of its profiles to work
  from.
- **fixed set** — a value from a short closed list, which is given with the field.
  Nothing outside the list means anything.
- **gas** — a breathing mix, written the way divers write it: `"AIR"`, `"EAN32"` for
  nitrox with 32% oxygen, `"TMX18/35"` for trimix with 18% oxygen and 35% helium. Yemoja
  reads the fractions out of it, which is what decompression needs — a name alone would
  not do. No `units` setting affects it.

  Those are the forms to write, but the reading is deliberately forgiving: near-misses
  and the other spellings divers use are understood where the meaning is clear. A mix
  Yemoja understands is written back in the standard form the next time it saves that
  dive, so `nx 32` becomes `EAN32`. One it cannot make sense of is kept exactly as you
  typed it and reported rather than dropped.
- **list of** — several values together: `["@anna_de_vries", "john"]`.
- **keyed owned items** — several owned items together, each under a key: `{"k1": {…},
  "k2": {…}}`. The key names the entry and is not written inside it.
- **series** — a measurement through a dive, as a list of pairs: a time and a value,
  `[[0, 0], [30, 8.4]]`. The time is always seconds from the start of the recording,
  whatever else the file says about units. Between two pairs, assume a straight line.
- **keyed series** — several series together, each under a key, the same way owned items
  are keyed.

An **owned item** is not a value but a set of fields kept together, described with the
item that owns it.

### Dates and times are always written the same way

Measurements change form to suit a file; dates and times do not. They are always
`yyyy-mm-dd` and `hh:mm:ss`, everywhere, and no `units` setting affects them.

**Whole numbers take no unit either.** Every one of them counts something rather than
measuring it — your own dive numbering, how many buddies, a rating out of ten, how many
days until a service falls due — and a count has nothing to be converted into. Only
numbers with a decimal point allowed are measurements, and only those follow the table
above.

That is deliberate. `03/04/2026` is the third of April to some readers and the fourth of
March to others, and a file that had it the wrong way round would look perfectly
correct — the mistake would only show up as dives on the wrong days, long after anyone
could tell which reading was meant. There is no such doubt about `2026-04-03`.

It also sorts. A list of dates in this form is in date order as plain text, which is why
a folder of dives reads chronologically.

## Ready-made data

Yemoja comes with reference information you do not have to type yourself: parts of the
world and their dive sites, commonly available equipment, and certification schemes.
You refer to these exactly as you refer to your own items.

`yemoja.json` lists which of these you use, grouped by the kind of item they hold:

```json
{
  "libraries": {
    "region": ["region/world", "region/europe"],
    "certification": ["certification/padi"]
  }
}
```

These are names, not file paths — Yemoja knows where to find them, on whichever device
you are using.

Three things to know about them:

1. **They are read-only.** Anything you add is stored in your own logbook, never in
   the supplied data.
2. **You can change them anyway.** If you correct a dive site that came with Yemoja,
   your corrected version is saved in your logbook and is the one that gets used from
   then on. The original stays untouched. This is the only way one of your items comes
   to stand in for a supplied one: adding a site of your own never replaces one, even
   if you give it the same name.
3. **They cannot be deleted** — only replaced by your own version. Delete your version
   and the supplied one comes back, as it stands today: Yemoja will not tell you that a
   site you corrected has since been corrected at its end, so if you want what it ships
   with, take your copy away.

If an id exists both in your logbook and in the supplied data, yours is always
the one that counts.

## Editing by hand — what to watch for

1. **Close the logbook in Yemoja first**, or your changes may be overwritten.
2. **Keep the file valid JSON.** A missing comma or bracket will stop the file being
   read. Most editors will point these out.
3. **Do not invent fields.** Anything Yemoja does not recognise is kept but ignored.
   The fields each item may hold are listed in [data-fields.md](data-fields.md).
4. **Do not store anything the application works out for itself**, such as totals or
   averages. These are recalculated, and a value you write is kept in the file but never
   used. Which calculated values may be corrected is said in
   [data-fields.md](data-fields.md).
5. **Renaming items is for the application**, as described above.

## Two complete examples

### A dive, one file per item

Stored as `dive/2026-02-23#0.json`. The file is the dive itself.

```json
{
  "units": {
    "length": "m",
    "temperature": "C"
  },
  "start_date": "2026-02-23",
  "start_time": "09:15:00",
  "end_time": "09:58:00",
  "dive_number": 143,
  "dive_site": "@blue_quarry",
  "buddies": ["@anna_de_vries", "john"],
  "rating": 8,
  "max_depth": 31.4,
  "details": {
    "tags": ["training"],
    "dive_trip": "@spring_weekend",
    "operator": "@northshore_diving"
  },
  "environment": {
    "current": "none",
    "waves": "none",
    "visibility": 4,
    "air_temperature": 11,
    "bottom_temperature": 7
  },
  "gear": {
    "items": ["@my_drysuit", "@my_computer"],
    "mass": 34,
    "weight": 8,
    "temperature_evaluation": "cold",
    "buoyancy_evaluation": "good"
  },
  "remarks": "Silty in the shallows.\nEntry easier from the north end."
}
```

Notice what is **not** in the file. There is no `name`, no `end_date`, no `duration` and
no `buddy_count`: Yemoja works all of those out. Writing them in would only be worth doing
to correct one of them.

`max_depth` is here because this dive has no profile. With one, it would be worked out
too, and worth writing only if the computer's own figure were better.

`deco` is missing for a different reason. Without a profile there is nothing to work it
out from, so it is not absent by choice — it is simply unanswered, and writing `true` or
`false` here is the only way it gets an answer.

### People, all in one file

Stored as `person.json`. Each key is a person's id.

```json
{
  "anna_de_vries": {
    "first_name": "Anna",
    "last_name": "De Vries",
    "email": "anna@example.invalid",
    "medical": {
      "last_medical_check": "2026-01-14",
      "blood_group": "O+",
      "height": 1.72,
      "body_mass": 64
    },
    "insurance": {
      "name": "Aqua Cover",
      "policy": "AC-88213",
      "start_date": "2026-01-01",
      "end_date": "2026-12-31"
    },
    "courses": {
      "k1": {
        "certification": "@open_water_diver",
        "instructor": "@tom_janssen",
        "date": "2019-06-02",
        "dives": ["@2019-06-01#0", "@2019-06-01#1"]
      }
    },
    "remarks": "Prefers a shore entry."
  },
  "tom_janssen": {
    "first_name": "Tom",
    "last_name": "Janssen"
  }
}
```

Both people are in the same file, so both are as complete or as sparse as you like:
`tom_janssen` has a name and nothing else, which is enough to be referred to.

`medical` and `insurance` are single owned items, written as objects. `courses` may hold
several, so each sits under a key of its own. A course's `dives` are references like any other, `@` and
all.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
