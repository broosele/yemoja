# Diving Log against this model

Diving Log is a Windows logbook, and this is how its database is read: what its tables hold, what
of it maps onto the items in `manual/data-fields.md`, and what is left behind. It is `FEAT-7`'s
second source after UDDF, [uddf.md](uddf.md), and arrives the same way: as a set of items that is
reviewed before anything is taken in.

**Nothing here comes from another program's reading of this format.** Diving Log does not publish
its database's layout, and the one reader that is widely known is GPL and off limits. What is
written below was worked out from a Diving Log 4.2 database, by reading its tables and checking
what they held against what its owner remembered of the dives, and from SQLite's own published
file format, `DATA-130`.

## How the database holds a dive

What follows is what a Diving Log 4.2 database was found to hold, column by column where it
matters. A column not named here was empty in that database, or is named under the table it
belongs to with its meaning unknown.

### The tables

| Table | Holds | One row is |
|---|---|---|
| `Logbook` | the dives | a dive, with its main cylinder and its profile |
| `Tank` | cylinders beyond the main one | a cylinder of a dive, by `LogID` |
| `Place` | dive sites | a site, by `PlaceID` from a dive |
| `Country`, `City` | where a site is | a country, a town |
| `Buddy` | the people dived with | a person, by the ids in a dive's `BuddyIDs` |
| `Shop` | dive centres and clubs | an operator, by `ShopID` from a dive or a trip |
| `Trip` | trips | a trip, by `TripID` from a dive |
| `Divetype` | the kinds a dive may be marked | a kind, by the ids in a dive's `Divetype` |
| `Brevets` | the user's certifications | a certification held |
| `Personal` | the user | one row |
| `Equipment` | gear | an item |
| `Userdefined` | the fields a user named themselves | a dive's values, by `LogID` |
| `Fish`, `FishRel` | species, and which dive saw which | a species; a sighting |
| `Pictures`, `Signature`, `Bookmarks` | images and marks on a dive | one each, by `LogID` |
| `DBInfo` | the database itself | one row, naming the version in `DBVersion` |
| `DeletedRecords` | what Diving Log's own sync removed | an id, and nothing to import |

Rows point at each other by their integer `ID`. A list of them, `BuddyIDs` or `Divetype`, is the
ids written as text with commas between: `3,17`.

### Units and forms

**Metric throughout, whatever the program showed.** Depths are metres, temperatures degrees
Celsius, pressures bar, cylinder sizes litres of water. A dive's `Divetime` is minutes, as a
number that may have a fraction. Dates are text, `yyyy-mm-dd`, and a dive's `Entrytime` is
`hh:mm` in local time; `UtcOffset` would say where local stood, and was empty throughout.

A site's position is text in degrees, minutes and seconds with a hemisphere letter, as
`12°34'56.78"N`. Its `Altitude` is a band written as words, such as `0 m - 300 m`, not a height.

### Codes

Several columns hold a small number for a choice made from a list in the program. The list is not
stored, so each meaning was found from where the dives were.

| Column | Codes |
|---|---|
| `Water` on a dive | `1` salt, `2` fresh |
| `Visibility` | `1` good, `2` medium, `3` poor, `0` not given. A scale, not metres: dives in clear tropical water are mostly `1`, and those in murky inland and coastal water mostly `2` and `3` |
| `Entry` | `0` from the shore, `2` from a boat; `1` and `5` are unknown |
| `Tanktype` | `0`, `1` and `2`, unknown, and probably a cylinder's material |
| `Deco` | `1` where the dive owed a stop |

### The profile

A dive's profile is five text columns of digits, each a run of fixed-width samples taken every
`ProfileInt` seconds from the start. Sample *n* of each column is the same moment, *n* times the
interval after the start. A column is empty where the computer did not report it.

| Column | Width | Positions | Holds |
|---|---|---|---|
| `Profile` | 12 | 0–4 | depth in centimetres |
| | | 5 | a flag set over long stretches of two dives, meaning unknown |
| `Profile2` | 11 | 0–2 | temperature in tenths of a degree |
| | | 3–6 | cylinder pressure in tenths of a bar, nought where none was read |
| | | 7 | the cylinder breathed: `0` the dive's main one, `1` its first in `Tank` |
| | | 8–10 | a figure falling through the dive and resting at 239 near the surface, probably the computer's remaining gas time in minutes |
| `Profile3` | | | empty throughout |
| `Profile4` | 9 | 0–2 | the no-decompression limit in minutes |
| | | 3–5 | the depth of the stop owed, in metres |
| | | 6–8 | the stop's time in minutes |
| `Profile5` | 19 | 13–15 | the CNS clock in hundredths of a percent |

Three things are not what they look like.

**The no-decompression limit is held at 99 minutes when it is longer**, and at one minute
throughout a stop.

**A stop's depth stays in its column after the stop is done.** It is a stop only while the limit
is below its ceiling of 99; otherwise every dive that owed a stop would claim it to the surface.

**The CNS clock stops at 9.99 percent**, written `999`, and a reading there is the ceiling of the
column rather than a measurement.

## Settled

- **DLOG-1 — Whether Diving Log's file is read directly.** *Settled:* **yes, for other people
  moving from it.** Decided on 2026-10-02, at the author's word.

  Diving Log can write UDDF, which is read already, and a user moving once could go that way. It
  leaves things behind that its own database holds, among them series of a profile that UDDF
  carried no further, and a user should not have to know which route loses less. So its file is
  read as it is, recognised by the signature every SQLite file begins with, through the same
  import a UDDF file or another logbook goes through.

- **DLOG-2 — Which versions are read.** *Settled:* **any file holding the tables a dive needs.**
  Decided on 2026-10-02, at the author's word.

  Only a 4.2 database has been seen. Every column is read by its name, so a file from another
  version that adds a column is read without it, and one that lacks a column has no value for
  the field it would have filled. A file without the dive table itself is refused, saying so. What
  the import reports names the version the file says it is, and that 4.2 is the one checked.

- **DLOG-3 — What happens to what this model has no field for.** *Settled:* **what was typed goes
  to the remarks; pictures, signatures and stamps are left.** Decided on 2026-10-02, at the
  author's word.

  A dive's boat, its divemaster, the weather, the fish seen, its pressure groups and the
  user-defined fields Diving Log offers were all typed by somebody, and each is added to the
  dive's remarks as a line of its own, labelled, so nothing typed is lost. Pictures, signatures
  and stamps are images, or paths to files on the computer Diving Log ran on, and mean nothing
  in another logbook; they are left, and the import counts them.

- **DLOG-4 — Whether an import remembers what it took in.** *Settled:* **no; a second import is
  reviewed as the first was.** Decided on 2026-10-02, at the author's word.

  Every record in the file carries an id of its own, and keeping it would let a second import
  of an updated file offer only what is new. That is a field on every type for a move most
  users make once. So nothing is kept, and a dive imported twice is left to the review to catch, as
  one from a UDDF file is.

## Open questions

None yet.
