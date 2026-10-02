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
