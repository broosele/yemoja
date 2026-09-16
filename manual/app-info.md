# App info

## Version

You are running Yemoja {version}.

Yemoja is young software and still changing. Keep a backup of your logbook
folder — and remember that it is plain text you can always read and copy
yourself, whatever becomes of the app.

## Known bugs and limitations

- **There is no undo.** Every change is written to your files the moment it is saved.
- **A plan cannot be started from nothing.** What the model works out is shown under each
  recording and each plan, and a plan can be given its ascent — but making one means adding a
  profile in the edit form and marking it `planned` by hand. The rate it rises at and the depth
  of its shallowest stop are fixed at 9 m a minute and 3 m until the settings files are read.
  See [decompression.md](decompression.md).
- **The settings files are not read yet**; see [settings.md](settings.md).
- **Some things are not written to a UDDF file yet**; [uddf.md](uddf.md) lists them.
- **Locations cannot add or delete a site** from the window.

## No warranty, and no liability

Yemoja is provided as it is, with no warranty of any kind. There is no promise
that it is correct, that it will keep working, or that it is fit for any
particular purpose. Everything you do with it, you do at your own risk, and the
author accepts no liability for any loss or damage that follows — including the
loss or corruption of your logbook.

This matters more than usual for a diving application, so plainly:

Yemoja is a logbook, with an aid to understanding dives you have already made and
to planning dives you have not. It is not a dive computer, and it has been neither
certified nor validated as one, nor as planning software.

Its decompression figures are of two kinds, and both may be wrong. What your
computer recorded is what that device said at the time. What Yemoja works out is
one model's estimate, computed from a recording after the fact or from the
assumptions a plan was given, and it will differ from what a dive computer says.

You use them at your own risk, and the author accepts no responsibility for any
dive planned, made or judged with their help. Check a plan against your training
and your tables. Never carry one into the water as the thing you follow, and never
let any of it override your training, your computer, your tables, or your own
judgement.

Diving is dangerous. Responsibility for your dives is yours alone.

## Licence

Two parts of Yemoja are given away outright, under CC0 — a public domain dedication
with no conditions attached:

- **The manuals**, this chapter among them.
- **The data the app ships with**: the regions, the certifications, the gear
  catalogue.

Use them for anything, without asking and without crediting anyone. That is
deliberate. A logbook you could not read without this application would not really
be yours, and a format nobody else is free to implement is not really a format.

The format itself — the fields, how they nest, the way a reference is written — is
disclaimed too. No copyright is asserted over it and no patent. Write your own
software to read and write these files, and you owe nobody anything.

**The application itself is another matter.** It is not open source, and no licence
grants you any rights to it: all rights are reserved by the author. You may not use,
copy, modify or distribute it, in whole or in part, without explicit permission, and
where permission has been given it covers only what was agreed.

Whether that changes has not been decided.

## Names that belong to others

Yemoja is not affiliated with, endorsed by, or connected to any diving agency,
manufacturer or operator named in this application or in the data it ships. Those
names appear because the items are about them: a certification cannot be recorded
without naming who issued it. All trademarks belong to their owners.

## Credits

Yemoja is written by Bram Rooseleer.

Claude, an assistant made by Anthropic, is used throughout the work: to plan the design
and to be argued with about it, to review and give feedback, to write the documentation —
this manual and the notes behind it — to compile the reference data that comes with
Yemoja, to build the test data and the scripts that check it, to read published
standards, to draw the icon, and to help with debugging and testing.

The atlas of regions was written for Yemoja, following the conventions of Natural
Earth — a public domain map dataset that asks for no credit and gets this one anyway.
The map itself — coastlines, lakes, borders, rivers and cities — is Natural Earth's data.

The decompression chapter explains the published Bühlmann ZHL-16C model. With thanks to
Erik C. Baker, whose writing on gradient factors made it comprehensible to a generation of
divers.

## The name

Yemọja is a water deity — an orisha — of the Yoruba people of West Africa. Her
name comes from Yèyé omo ẹjá, "mother whose children are fish". She was first
honoured as the spirit of the Ogun river in what is now Nigeria; carried across
the Atlantic, she became a goddess of the sea, and is known as Iemanjá in Brazil
and Yemayá in Cuba.

She is the protector of those who travel on water — of fishermen, of sailors, of
people who go out and hope to come back. That seemed the right patron for a dive
log, which is after all a record of going into the water and returning to write
it down.

The dot beneath the letter in Yemọja is Yoruba spelling: it marks an open vowel.
The name is said roughly "yeh-MAW-jah".

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
