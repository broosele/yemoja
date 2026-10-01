# Dive computers and importing

Dives arrive in two ways besides being typed in: read off a dive computer, or taken from
another logbook. Both end the same way, with a list of what arrived for you to look over before
anything is added. What survives a UDDF file is in [uddf.md](uddf.md); this chapter is about
doing it.

## Downloading from a dive computer

Press **Download from dive computer** on the home screen. Yemoja looks for a computer plugged in
over USB or a serial cable, and for one advertising over Bluetooth.

- **Nothing found:** switch the computer on and put it into its Bluetooth or download mode,
  then press the button again.
- **One found:** it is read straight away.
- **Several found:** you are asked which, with a button for each.

Reading a full computer takes several minutes. Home shows how far it has got, and **Cancel**
gives it up, keeping nothing. You can go to any other tab and carry on meanwhile. Home's tab shows
a spinning circle in place of its icon while the computer is read, and a tick once it has
finished.

**Only dives this logbook has not seen come across.** Yemoja remembers where the last download
from each computer stopped, so the next one brings only what you dived since.

**A computer that shows a code before it talks** asks you to type the code it is showing. The
answer is kept on the gear item for that computer, found by its serial number, so you are not
asked again. Add the computer as a piece of gear, category `instruments`, with its serial, and
the code is remembered there.

## Looking over what arrived

A download's list is shown when you come to Home after it has finished, and it is made afresh
each time you come back. Leave it half done to add a dive site, and on your return the site is
there to choose; a dive you took in last time is not offered again. A choice you made and did
not apply is not kept when you leave.

Every dive that arrived is a box, oldest first, headed by when it started, how long it was, how
deep, and which computer recorded it. Each has three choices, one of them already chosen:

- **Merge** where the dive overlaps one you already have. Nobody is on two dives at once, so the
  two are recordings of the same dive, and the recording is added to the one you have. It is
  chosen to begin with wherever it applies, and greyed where it does not.
- **Import as dive N** takes it in as a new dive. The number follows from the boxes above: a
  merged dive takes none, a skipped one takes none, so change one and the numbers below move.
- **Skip** leaves it out. A skipped dive is offered again each time you come back to Home, until
  the download is closed. After that, a later download does not bring it back.

A box also says when a dive was pieced together from several recordings, because the computer
cut it in two, and when it is a dive this logbook already holds.

**Where a dive recorded a position**, the box asks where it was, with a chooser: the three nearest
of your dive sites with how far off each is, **No site**, or **New site**, named in the box beside
it. Typing a name chooses *New site* by itself. The new site takes its name as its id and keeps the
position the computer recorded. A dive left at *No site* goes in with none.

**Nothing happens until you press Apply.** Every choice can be changed until then, and the line
beside the button counts what it will do — *1 merged, 2 imported, 1 left staged*. Apply works
down the list and stops at the first thing it cannot do, saying why under that box; what is
above has landed and what is below is still waiting. **Close** leaves everything waiting.

What has arrived but not been decided is kept in a folder beside your logbook, with `.import`
after its name. It is not part of your logbook.

## Importing another logbook

Press **Import** on the home screen and choose either:

- **a folder**, which is read as another Yemoja logbook; or
- **a file**, which is read as UDDF, the open format most dive logs can write.

The dives are listed for you to look over, exactly as a download is. Everything else that came
with them — people, sites, gear, trips — is counted in a sentence above the list, such as *Also
arriving: 2 people, 3 dive sites (1 already held)*, and goes in with the dives.

**A Yemoja logbook is matched by id.** An item your logbook already holds under the same id is
the same item, and what arrives is laid over it field by field: a field the incoming item says
nothing about is left as it was. Importing the same logbook twice therefore changes nothing the
second time.

**A UDDF file is matched by nothing.** The names inside a UDDF file mean something only within
that file, so every item from one arrives as new, and a dive overlapping one you already have is
offered **Merge**. Importing the same file twice, and taking everything as new both times, gives
you everything twice.

A dive that arrives with its own dive number keeps it.

## Exporting

Press **Export to UDDF** on the home screen and name a file. The whole logbook goes: every dive
you have made, and the sites, wrecks, people, gear, trips and operators they refer to. Nothing in
your logbook changes. When it is written, Yemoja says how many dives went, how many were recorded
on more than one computer and so went with one recording only, and how many planned dives were
left behind.

What a UDDF file can and cannot carry, and what is not written yet, is in [uddf.md](uddf.md).

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
