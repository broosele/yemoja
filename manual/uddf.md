# UDDF

UDDF is the one open file format for dive logs — a published standard, not one
manufacturer's export. Yemoja reads and writes **version 3.2.3**.

This chapter is about one question: **if you move a logbook through UDDF, what comes out
the other side?** Nothing here is a surprise waiting to happen. Everything that is lost is
listed, and where a value changes shape on the way, that is said too.

Two things worth knowing before the detail.

**Your own logbook is never the copy.** Importing a UDDF file adds to your logbook and
does not consume the file, so what a file holds that Yemoja cannot keep stays in that
file. Nothing in this chapter describes anything being destroyed.

**Exporting is the lossy direction.** UDDF is a good format and an old one, and it
describes a slightly different idea of a dive from this one. Most of what you lose, you
lose going out rather than coming in.

## The short answer

Almost everything about a dive survives in both directions: when it was, how deep, how
long, how cold, who with, where, what you breathed, and the whole recording your computer
made — every sample of depth, temperature, cylinder pressure, stops, alarms and oxygen
loading.

Four things do not, and they are the ones to remember:

- **A second dive computer.** UDDF holds one recording per dive.
- **What your computer was set to** — salt, fresh or the standard density. UDDF keeps the
  depths and throws away what made them.
- **`waves`**, the state of the surface. UDDF has nothing for it.
- **Your own labels** on a dive.

Several more come back subtly changed rather than missing — a site's kind, an address,
where a site sits. Each of those is explained under its own subject below, with everything
else.

## Not written yet

How to export is in [computers-and-importing.md](computers-and-importing.md).

**A dive you have planned and not yet made is not written at all.** An intention in a file handed
to another application is a claim that the dive happened, and nothing in the file lets whoever
reads it tell the difference. UDDF has an element of its own for a dive plan, which is the only
honest way to send one, and Yemoja does not write it yet. The export says how many plans it left
behind.

Some other things this chapter says survive are **not written yet**, and will come back empty if
you export and import again:

- a person's insurance,
- a piece of gear's service records,
- where a trip went,
- which regions a site is in,
- a site's water type.

Regions and qualifications themselves are not written either. They come with the application
rather than from your logbook, and a UDDF file has nowhere to put them: a course goes out with
the name of its qualification in words.

## Units, which are not a problem

UDDF is written in strict SI throughout: metres, kilograms, seconds, and then **kelvin**
for temperature, **pascals** for pressure and **cubic metres** for volume. Your logbook is
in degrees Celsius, bar and litres.

Every one of those conversions is exact. Nothing is rounded away, nothing drifts, and a
value that goes out and comes back is the same value. A UDDF file will look odd if you
open it in a text editor — 200 bar reads as 20000000 — but that is all it is.

## Your dives

**Kept, unchanged, both ways.** The dive number, the date and time, the deepest point, the
coldest water, visibility, air temperature, how the current was, your rating out of ten,
your notes, which site it was, and how you fared for warmth. A time zone written after the
time becomes the dive's time zone offset, and goes back out the same way.

Two of those deserve a note. **Current** uses the same six steps in both formats, so it
crosses over exactly rather than being squeezed into a different scale. **Rating** is 1 to
10 in both.

**Repetitive dives are kept**, in a different shape. UDDF groups dives that follow each
other closely and marks where a group starts; your logbook instead has each dive name the
one before it, where the gas from it still mattered. The two say the same thing, and
converting between them is arithmetic. Your surface intervals come out right.

**Lost on export.**

- **The state of the surface.** UDDF has no element for `waves` at all, so this one
  simply goes.
- **Your tags.** The same.
- **Whether it was a decompression dive**, but only when the dive has no recording. With a
  computer's recording the stops themselves are exported, so it survives. Without one, it
  is your own answer and UDDF has nowhere to write it, so it comes back unanswered.

**Lost on import.** UDDF can carry a few things this logbook does not keep: how hard you
were working, what went wrong, equipment that failed, the kind of platform you dived from,
and the purpose of the dive. Where the file has them, they are folded into the dive's
notes rather than dropped, so you can read them even though nothing sorts or searches on
them.

## The recording

This is the largest part of most dives and it survives well.

**Kept, both ways.** Depth, temperature, cylinder pressures, the stops the computer set,
no-decompression time, CNS and OTU, gas switches, and the no-fly and desaturation times
at the end. Alarms too: UDDF names the same nine kinds this logbook
does, so they cross over exactly.

**Three losses, and one of them matters a great deal.**

**What the computer was set to.** A dive computer does not measure depth. It measures the
pressure around it and converts, using a water density it was set for — salt, fresh, or
the standard figure many computers use. Your logbook records that setting and the density
behind it. **UDDF records neither.** It keeps the converted depths and discards the
conversion.

So an imported recording has depths but nothing to say what they were made with. Yemoja
will not guess: it says so, and asks. Until you answer, the depths can be read and drawn
but nothing can be calculated from them. Where a file came from your own export, set it
back to what it was.

**A second computer.** If you dive two computers, your logbook keeps both recordings and
one is marked as the one to work from. **UDDF holds one.** Exporting writes the one you
marked, and the other is gone. That is the single worst incompatibility here, and there is
no way round it that does not lie about the number of dives.

**Alarm severity.** UDDF can say how serious an alarm was. That is not kept, because
"serious" is not comparable between makes.

**Heart rate.** Some computers record it and UDDF carries it. Yemoja does not keep it, so
it is lost coming in and never written going out.

**A shape difference, which is not a loss but is worth knowing.** Your logbook keeps each
measurement as its own series with its own times, because a computer samples temperature
far less often than depth. UDDF instead stores one list of instants, each carrying every
measurement at once. Exporting therefore has to fill in the gaps, and it does so along a
straight line between the samples either side.

**An exported file will contain depth figures your computer never measured.** They are
honest interpolations and they are what the format requires; nothing is invented that
disagrees with what was recorded. Reading such a file back does not degrade it further.

## Gas and cylinders

**Kept, both ways.** What was in the cylinder, the pressures in and out, its size, and
which of your cylinders it was.

Your logbook writes a mix the way divers do — `AIR`, `EAN32`, `TMX18/35` — and UDDF writes
the fractions out. Those are the same thing said differently, and your logbook reads the
fractions out of the name rather than treating it as a label, so the two convert.

**To whole percentages.** A file claiming 31.8% oxygen comes in as 32 and goes back out as
32, because whole percentages are all a mix is written in here — which is how mixes are
named, analysed and labelled on a cylinder.

**Lost on export.** Argon and hydrogen. UDDF can describe them and this logbook cannot, so
the question does not arise in that direction.

**And one that is easy to miss.** When you switch gas, your logbook records which
*cylinder* you moved to. UDDF records which *mix*. If you dived two cylinders of the same
mix — a pair of stages, or twins you switched between — the file cannot say which one you
went to, and reading it back cannot recover it.

**Lost on import.** Gas consumption rate, which Yemoja calculates for itself from the
pressures, and the figures a file may carry that follow from the mix anyway — equivalent air
depth, maximum operating depth, maximum partial pressure. Nothing is lost that cannot be worked
out again.

## Dive sites

**Kept, both ways.** The name, other names it goes by, its position, its height above sea
level, how deep it gets, what the bottom is made of, your rating and your notes.

**Lost on export.** What is there — parking, a filling station, showers. UDDF has no equivalent.

**Lost on import.** The shallowest the site gets, which this logbook deliberately does not
record.

**Three that come back changed.**

**What kind of place it is** goes out coarser than it came in. UDDF joins pairs this
logbook keeps apart — ocean with sea, lake with quarry, cave with cavern. Every value can
be written out; reading it back cannot tell which half you chose, so a sea may return as
an ocean.

**The water type** of the site is not recoverable. Your logbook says salt, fresh or the
standard figure; UDDF writes a density instead. Each of the three has a density so going
out is exact, but an arbitrary density cannot say which of three a diver picked, and most
real ones match none of them.

**Where the site is.** Your logbook places a site in regions, and a region can sit inside
several larger ones — a French bay is in France and in the Mediterranean at once. UDDF
gives three lines of text: country, province, location. Going out, one region has to be
chosen as the country and one as the province, out of a shape that does not label them.
Coming back, three strings have to be matched against the regions you already have, or new
ones made. This is the least reliable part of the round trip, and it is worth checking
after an import.

## Wrecks

**Kept, both ways.** Her name and former names, what she was, her flag, her yard and
launch, when she sank, her dimensions and displacement, and your notes.

**Two structural differences.** UDDF allows **one wreck per site**, so a debris field of
three vessels cannot be written out as three. And a wreck there **cannot be shared**: a
large ship reachable from two moorings becomes two copies of the same vessel, and reading
that back gives you two wrecks where you had one.

If the sites you dive have more than one wreck on them, that is the thing to watch.

## People

**Kept, both ways.** Names, birthday, contact details, insurance, and notes.

**Address is lossy on the return leg only.** Your logbook keeps an address as you wrote
it; UDDF splits it into street, town, postcode and country. Going out, it cannot be taken
apart again reliably, and coming back it is joined into one line. Import then export then
import will leave it looking slightly different from how you typed it.

**Medical details are a snapshot here and a history there.** Your logbook records the
state — when you were last checked, blood group, height, weight. UDDF records
*examinations*, each with a date, a doctor and a result, and may hold many. Importing
takes the date of the most recent one and loses the rest. Exporting produces a single
examination with no doctor and no result. Blood group, height and weight have nowhere to
go there at all.

**Qualifications lose their certificate number on import, and their instructor and dives
on export.** Your logbook records a course as something you did — with an instructor, and
the dives that earned it — pointing at a qualification that a supplied library describes.
UDDF puts the qualification and your holding of it in one place, with a certificate
number. Neither side has room for all of the other's.

**Lost on export.** Emergency contacts. UDDF has nowhere to put them.

## Gear

This is where the two formats disagree most, and the result is best effort in both
directions.

UDDF has **twenty-one kinds of thing** — mask, fins, suit, regulator, tank, computer,
light, and so on — and a piece of equipment *is* one of them. Your logbook says what a
thing is in two open fields, a category and a kind, and both accept anything you write.

**Coming in**, each UDDF element sets both from a fixed table: a `mask` becomes ABC with
kind `mask`, a `tank` becomes a cylinder, a `divecomputer` becomes an instrument. Where
nothing sensible fits, the category is left empty — which is honest, since it means
nobody knows.

**Going out**, anything that maps back to one of the twenty-one is written as that
element. Anything that does not — an ABC item you called a `flipper` — is written as
`variouspieces` with your own word for it in the notes, so a person reading the file still
learns what it was. No program will sort on it, but nothing is silently dropped.

**Kept, both ways.** Make, model, serial number, and service records.

**Lost on import.** What you paid, and rebreather equipment.

**One oddity on export.** In UDDF, equipment belongs to a person. Here it does not — which
is what lets you record a club cylinder or a borrowed weight belt. Exporting has to
attribute everything to you, whether or not it is yours.

## Trips and operators

**Kept, both ways.** A trip's name, dates, where it went, who ran it, and the dives on it.
Trips with legs survive too: your logbook makes each leg a trip naming the larger one, and
UDDF has the same two levels.

**Watch out for the same dive centre appearing twice.** UDDF can describe an operator in
two ways: as a thing in its own right with an identity, or written out inside a trip with
no identity at all. Where a file uses the second, two trips with the same centre are two
descriptions with nothing saying they are the same place, and importing them gives you two
operators. Where a file offers both, Yemoja takes the one with an identity.

**Lost on export.** What sort of operation it is — dive centre, club, resort, boat,
liveaboard. UDDF separates a dive base from a boat structurally instead, which does not
fit a liveaboard, so this is dropped rather than guessed.

**Lost on import.** Staff, accommodation, and the boat's own details. And prices, which
this logbook does not record anywhere.

## What is not carried at all

Four whole subjects in UDDF have no counterpart here, in either direction. If your file is
mostly one of these, this is not the application for it:

- **Rebreathers.** Closed and semi-closed circuit and everything that follows from them —
  setpoints, measured and calculated partial pressures, and the rebreather diving modes.
  None of it is read and none of it is written.
- **Marine life.** UDDF carries a whole vocabulary for species, abundance and sightings.
  Not kept.
- **Programming a dive computer.** UDDF can be used to send tables and schedules *to* a
  device. Yemoja reads computers; it does not program them.
- **Other decompression models.** Files carrying parameters for models this application
  does not use.

## Getting a file back unchanged

If what you want is a logbook that survives a round trip exactly, the honest answer is
that UDDF is not that, and no interchange format is. Export it, read this chapter, and
know which four or five things you will have to put back.

What is safe: one computer per dive, no wrecks shared between sites, and a note of what
your computer's water setting was.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
