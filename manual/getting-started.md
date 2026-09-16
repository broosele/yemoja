# Getting started

Yemoja keeps your logbook as a folder of plain files you can read yourself. This chapter is
about the window: opening a logbook, finding your way round it, and changing what it holds.
What the files look like is in [data-format.md](data-format.md), and what each field means is
in [data-fields.md](data-fields.md).

## Opening a logbook

With no logbook open, the window shows only **Home** and **Manuals**, and Home offers two
buttons.

- **New logbook** asks for a folder, and one that does not exist yet may be typed. The new
  logbook comes with what Yemoja ships already in it — the regions of the world, the agencies'
  certifications and a catalogue of generic gear — and nothing of yours. A folder that already holds a logbook is refused rather than written over.
- **Open a logbook** asks for a folder holding one. A folder with no logbook files in it opens
  as an empty logbook.

The folder can also be named when Yemoja is started, and it then opens straight away.

**Yemoja greets you by name once it knows which person you are.** A new logbook names nobody.
Add yourself on the Community tab, then write your id as the `user` in `yemoja.json`, as
[data-format.md](data-format.md) shows. Until then the greeting says so.

## Home

**The greeting** says hello, and remarks on the day when there is something to remark on: your
birthday, the new year, a day given to the sea, or the first day of a season. The seasons turn
round when your dive sites lie, on average, south of the equator. The line under it counts your
dives, the places you dived and the time you spent underwater.

**Warnings** come next, when something is due. A red box holds what has already lapsed, and a
yellow one what falls due within a month:

- a piece of gear whose maintenance is due, from the `valid_until` of each entry;
- your own medical, taken to run a year from your last check;
- your own insurance, from its end date.

Nobody else's medical or insurance is warned about, and generic gear owes nothing. When nothing
is due, nothing is shown.

**System** holds what can be done to a logbook as a whole: making one, opening one, importing,
exporting to UDDF, and downloading from a dive computer. The last three need a logbook open. The
last two have chapters of their own: [computers-and-importing.md](computers-and-importing.md)
and [uddf.md](uddf.md).

**Statistics** plots your dives. The first box chooses what is drawn:

- **Each dive** is a dot per dive, one figure against another.
- **How many**, **Total**, **Average**, **Largest** and **Smallest** cut the bottom axis into
  bars and bring the dives in each to one figure.
- **Running total** adds the dives up in the order you made them.

The next boxes choose the figures, and **by** chooses how wide a bar is: a month, a quarter, a
year or five years for a date, and a round number for anything else. It opens on how many dives
you made each month, widening the bars where there would be too many to read.

## The tabs

- **Dives** lists your dives in a table, newest first, grouped by year. A year folds away with
  its arrow, and clicking the year chooses all its dives. A trip is one cell down the dives made
  on it, and clicking it chooses the trip.
- **Gear** lists your equipment by category and then by kind. Anything with no category sits at
  the top.
- **Community** holds people, operators and certifications, each on a small tab of its own. You
  are marked among the people, and the tab opens on you.
- **Locations** holds the regions of the world as a tree, the sites and wrecks in the one chosen,
  and a map of it with your sites marked. **Hide unused**, which is on to begin with, leaves out
  the sites none of your dives were at, and the regions left with nothing in them.
- **Manuals** is this manual.

Each tab keeps what you chose and where you had scrolled to while you look at another.

**Several dives can be chosen at once.** Hold Ctrl and click to add a dive or take one out, or
hold Shift and click to take every dive between the last one chosen and this one. What they
come to together is shown instead of a single dive: the range and average of each figure, how
many there were of each value, and how many said yes.

## Reading an item

What you choose is shown on a card, titled with its name.

- A value that points at something else is a **link**. Following it goes to that item, on
  whichever tab holds it.
- A value **in grey** was worked out by Yemoja rather than written by you.
- A value **in red** could not be read, and says why in its place.
- A rating shows as **stars**, two points to a star.
- A person, a piece of gear, a dive site or an operator lists its **dives** in a box at the
  bottom.

Something an item holds several of, such as a dive's recordings or a person's courses, is a box
with a small tab for each. The recording a dive is worked from comes first, marked with a star.

**A recording is drawn as a graph**: the depth down the left, the minutes along the bottom. A
stepped line is the stop your computer set, with the water above it shaded red while the stop
stood. The right-hand axis is chosen from its title: the temperature, a cylinder's pressure,
the no-decompression time as **NDL**, and oxygen loading as CNS and OTU. Gas switches are marked
with the gas moved to, and alarms with red triangles.

## Changing things

**The pencil** on a card turns it into a form. **Save** writes what you changed, and is greyed
until you change something; **Cancel** leaves the item as it was. Something that cannot be
saved says why, in red, above the fields, and a field that will not read says so beneath it as
you type.

- A worked-out value can be **overridden** with a value of your own, and **reverted** to what
  is worked out.
- A field with usual answers offers them from its arrow, and anything else may still be typed.
  A field with a fixed list offers only that list.
- A field pointing at another item finds it as you type its name. A plain name is kept where
  one is allowed: a buddy, an emergency contact, or a dive computer you keep no item for.
- A list has **+ add** and a cross beside each entry.
- A time is typed as minutes and seconds, `42:30`, or as minutes alone.

On gear, the fields that only mean something for one kind of item are folded away on the rest:
a serial number and an access code on anything that is not an instrument, a capacity on
anything that is not a cylinder. They are there under **more fields** when you need them.

**Adding a tab** to something an item holds several of, or taking one away with its cross,
happens at once rather than waiting for Save.

**The + on a card** makes another item of the same kind, and opens it as a form. Nothing is
made until you save, and the item's id is worked out then, from what you typed. Where nothing
is chosen, the middle of the screen offers to add one.

On the Gear tab you can also click a category or a kind in the tree, which chooses it — the
arrow beside it still folds it away. A new piece of gear added while one is chosen starts out
filed there, with the category, and the kind where you chose one, already filled in. Change them
in the form like anything else.

**The bin** deletes the item, after asking. Anything the item holds goes with it. Where other
items point at it, the question says how many references will be left pointing at nothing, and
names where they are when there are only a few; ticking **Clear the references too** takes them
out as well. With several dives chosen, the bin deletes them all.

Locations has no + and no bin yet. A new dive site arrives with a download, which offers to name
one where a dive was, or with an import.

**Everything is written to your files the moment it is saved**, and there is no undo yet. Keep
a backup of your logbook folder.

## Copying text

Anything the window shows can be selected with a drag and copied. A selection stays within the
part of the window it began in, so dragging down a card does not pick up the list beside it.
The tabs along the top and the boxes of a form are not part of it.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
