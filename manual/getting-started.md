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
- **Open logbook** asks for a folder holding one. A folder with no logbook files in it opens
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
exporting to UDDF, downloading from a dive computer, and settings. All but the first two need a
logbook open. Most have more written about them: importing and downloading in
[computers-and-importing.md](computers-and-importing.md), exporting in [uddf.md](uddf.md), and
settings in [settings.md](settings.md).

**Statistics** plots your dives. The first box chooses what is drawn:

- **Each dive** is a dot per dive, one figure against another.
- **Count**, **Total**, **Average**, **Largest** and **Smallest** cut the bottom axis into
  bars and bring the dives in each to one figure.
- **Running total** adds the dives up in the order you made them.

The next boxes choose the figures, and **by** chooses how wide a bar is: a month, a quarter, a
year or five years for a date, and a round number for anything else. It opens on how many dives
you made each month, widening the bars where there would be too many to read.

## The tabs

- **Dives** lists your dives in a table, newest first, grouped by year. A dive you have planned
  and not yet made is listed too, with **plan** where its number would be. A year folds away with
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

A planned dive counts nowhere: not in the greeting, not in the statistics, and not in the
number a year carries. Clicking a year chooses the dives you made in it. If you tick a plan
yourself and ask what your selection comes to, the answer includes it — what you chose is what
is counted.

**A recording is drawn as a graph**: the depth down the left, the minutes along the bottom. A
stepped line is the stop your computer set, with the water above it shaded red while the stop
stood. A plan is drawn with a dashed line, since it has not happened. The right-hand axis is
chosen from its title: the temperature, a cylinder's pressure,
the no-decompression time as **NDL**, and oxygen loading as CNS and OTU. Gas switches are marked
with the gas moved to, and alarms with red triangles.

**What Yemoja's own model makes of it sits under the graph**, where it can be worked out: how deep
the stops would start, what each cylinder gives up and ends at, the oxygen clocks, how long before
you may fly, and how long before it is out of you. Anything it objects to — going above the
ceiling, a cylinder that runs dry, a mix too rich for the depth it is breathed at — is listed in
red with the minute it happened at, and the ceiling itself is drawn over the graph with the water
above it shaded. Its own NDL and clocks can be chosen on the right-hand axis, each marked *worked
out* so you can tell them from what your computer recorded.

Most recordings say nothing about it, and then nothing is shown: a computer has to have written
down which gradient factors it was running before the model can say anything at all.

**A plan carries a button that works its ascent out.** Fill the depths in as far as the bottom,
and *Add the ascent* writes the rest of the dive into the plan: coming up at 9 metres a minute,
the decompression stops it owes with the shallowest at 3 metres, and the switch to a richer gas
wherever the depth allows one. What it wrote is then part of the plan like anything you typed, so
changing a gas afterwards does not move the stops — ask the model again and it will tell you they
no longer hold. A plan that cannot be answered for says why, which is
usually that nobody has written its gradient factors in.

**Start a plan with *Plan dive*** above the table on the Dives tab. Give it a day and, if you like, a time; the
depth of the bottom and how long you mean to be there, counted from leaving the surface; what you
breathe, written as `air`, `EAN32` or `TMX 21/35`; whether the water is salt or fresh; and the
gradient factors, as percentages — 30 and 70 for 30/70. **Create plan** makes the dive, descending
at 18 metres a minute, and adds its ascent at once, so what opens among your dives is a whole plan
with its stops. Everything else about it — the site, the cylinder, a second gas — is added the way
you change any dive, and *Add the ascent* is there again once you have.

The gradient factors are filled in only if you have chosen defaults for them in **Settings**, and
so are the rates the form says beside the button. They decide how conservative the plan is, and
that is a decision for you to make rather than a number for Yemoja to assume.

A plan with more than one level, or one you want to shape sample by sample, is still written into
the dive's own file by hand: a profile with `planned` set, the water type, the gradient factors,
and a `depth` reaching the bottom. [data-fields.md](data-fields.md) lists what a profile holds, and
[decompression.md](decompression.md) explains what the model does, what it assumes, and what it
cannot know.

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
names where they are when there are only a few; ticking **Also remove references to it** takes them
out as well. With several dives chosen, the bin deletes them all.

Locations has no + and no bin yet. A new dive site arrives with a download, which offers to name
one where a dive was, or with an import.

**Everything is written to your files the moment it is saved**, and there is no undo yet. Keep
a backup of your logbook folder.

## Asking an agent

Yemoja can put your logbook to an AI agent you have installed yourself. It supplies none and pays
for none: the agent is yours, it signs itself in, and what it costs is between you and whoever
runs it.

**Everything the agent reads leaves your machine.** Whatever it looks at to answer you is sent to
whoever runs its model, and that includes your people's addresses, telephone numbers, e-mail
addresses and medical notes, because a question about who you dived with is answered from the
items that hold them. Yemoja holds nothing back, so the only judgement about what a provider sees
is yours: ask an agent about a logbook you are willing to send, or run a model on your own machine,
where nothing leaves it.

**First tell Yemoja how to start your agent**, in **Settings** on the home screen: the *Agent
command* box, which [settings.md](settings.md#the-agent-command) explains. Until it is set, the
sparkle button is greyed, and resting the pointer on it says so.

**Press the sparkle button** at the right of the tabs, on whichever tab you are on; it says *Ask
an agent* when the pointer rests on it. A panel opens beside it and starts your agent, which takes
a few seconds, and stays there as you move between tabs, so a dive the agent names can be opened
and read while you carry on talking. Once the agent is running, ask. **Stop** ends the
conversation and leaves it to read; **Start** then begins another.

**It reaches your logbook through Yemoja, unless you say otherwise.** Every request it makes to
read a file, write one or run a command is refused, and the panel says so each time one is. What
Yemoja can refuse is only what the agent asks it, though: the agent is a program of your own,
running with your rights, and an agent that goes looking for files on its own is a matter between
you and it. Yemoja never tells it where your logbook is, and asks it not to look.

**It can propose changes, and you decide.** Tick *Allow changes* and an agent can stage
changes — correct a clock error across a trip, give one dive's gear to the others. Nothing it
stages touches your logbook. The panel says how many items are waiting, and **Review** takes you to
them on the home screen, under the other deeds. Each item shows every field it would change, what
the field holds and what it would hold. If you have edited one of those fields since the agent
staged it, the row says what it holds now, in red, and that change is left alone when you apply the
rest. It stays waiting afterwards, still in red, until you **Discard** it or the agent stages it
again against what the field holds now. **Discard** any item you do not want, then press
**Apply all** or **Discard all**. The box starts empty for every conversation, like the other one.

What is waiting is kept in a folder beside your logbook, with `.proposed` after its name, so it
survives the window being closed. It is not part of your logbook.

**It can be let at the files themselves, as a last resort.** Tick *Allow file access* and the
agent is told where your logbook is, may read its files, and may edit them — directly, with
nothing staged and nothing to review. It is asked to do that only for what the tools cannot, to
make the smallest change, and to say which file it changed and why; Yemoja reads your logbook again
when its turn ends, so the window shows what it did. A file it leaves unreadable stops the whole
logbook opening, and the panel says so if that happens. Take a backup first, as you would before
editing by hand. Running a command is never allowed, whatever is ticked. This box, too, starts
empty for every conversation.

**It works beside your logbook, not in it.** An agent writes files of its own as it goes — a note
of which tools it has been allowed, and whatever else it keeps. Those go in a folder next to your
logbook, named after it with `.agent` on the end, so your logbook stays your dives and nothing
else. Deleting that folder costs you nothing but what the agent remembered about itself.

**Check what it tells you.** An agent reads your logbook and answers in ordinary language, and it
can be confidently wrong: about which dives it counted, or about what you meant. It is asked to
say what an answer was based on and to name the dives it used, and each one it names can be
opened from the panel — that is what makes an answer checkable. Arithmetic is Yemoja's rather
than the agent's, so a total is a total; which dives went into it is the part worth reading.

**It is not a dive planner.** An agent is asked to give no advice about planning a dive or about
decompression, for the reasons the [app-info](app-info.md) chapter gives, and a model that
offers some anyway is not speaking for Yemoja.

**Nothing of a conversation is kept.** Closing the panel stops the agent and the conversation is
gone; it is never written beside your logbook. What the agent remembers on its own side is between
you and its provider.

## Copying text

Anything the window shows can be selected with a drag and copied. A selection stays within the
part of the window it began in, so dragging down a card does not pick up the list beside it.
The tabs along the top and the boxes of a form are not part of it.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
