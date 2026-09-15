# Dive computers and importing

Dives arrive in two ways besides being typed in: read off a dive computer, or taken from
another logbook. Both end the same way, with a list of what arrived for you to look over before
anything is added. What survives a UDDF file is in [uddf.md](uddf.md); this chapter is about
doing it.

## Downloading from a dive computer

Press **Download from a computer** on the home screen. Yemoja looks for a computer plugged in
over USB or a serial cable, and for one advertising over Bluetooth.

- **Nothing found:** switch the computer on and put it into its Bluetooth or download mode,
  then press the button again.
- **One found:** it is read straight away.
- **Several found:** you are asked which, with a button for each.

Reading a full computer takes minutes, and the window says so while it works.

**Only dives this logbook has not seen come across.** Yemoja remembers where the last download
from each computer stopped, so the next one brings only what you dived since.

**A computer that shows a code before it talks** asks you to type the code it is showing. The
answer is kept on the gear item for that computer, found by its serial number, so you are not
asked again. Add the computer as a piece of gear, category `instruments`, with its serial, and
the code is remembered there.

## Looking over what arrived

Every dive that came across is a line, oldest first: when it started, how long it was, how deep,
and which computer recorded it.

- **As dive N** takes it in as a new dive, numbered one after the highest number in your
  logbook.
- **Put together** is offered where the dive overlaps one you already have. Nobody is on two
  dives at once, so the two are recordings of the same dive, and the recording is added to the
  one you have.

A line also says when a dive was glued together from several recordings, because the computer
cut it in two, and when it is a dive this logbook already holds.

**Where a dive recorded a position**, the line asks where it was: the three nearest of your dive
sites, **nowhere**, or a new site you name there and then. A dive left unanswered goes in with no
site.

**All as proposed** takes every line as it stands. **Leave them** closes the list without taking
in what is left.

What has arrived but not been decided is kept in a folder beside your logbook, with `.import`
after its name. It is not part of your logbook.

## Importing another logbook

Press **Import a logbook** on the home screen and choose either:

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
offered to be put together with it. Importing the same file twice, and taking everything as new
both times, gives you everything twice.

A dive that arrives with its own dive number keeps it.

## Exporting

Press **Export to UDDF** on the home screen and name a file. The whole logbook goes: every dive,
and the sites, wrecks, people, gear, trips and operators they refer to. Nothing in your logbook
changes. When it is written, Yemoja says how many dives went, and how many were recorded on more
than one computer and so went with one recording only.

What a UDDF file can and cannot carry, and what is not written yet, is in [uddf.md](uddf.md).

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
