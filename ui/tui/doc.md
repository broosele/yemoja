# UI layer — TUI

A deliberately raw terminal interface: tables of items with new, edit and delete.
No polish, no visual design.

**Reading is built. Nothing writes.** It opens a logbook, shows what is in it, and that is
all — which is not a choice this front end made, since nothing anywhere in the project
writes a logbook yet.

## Purpose

- Get at everything in the logbook without waiting for the GUI to grow a screen
  for it — including fields the GUI chooses not to show.
- A fast way to inspect and repair data during development.
- Like the [API](../api/doc.md), it keeps the logic layer honest.

## Scope

- Navigating between item types.
- Listing items as tables; creating, editing and deleting them.
- Presenting errors from the layer below.

## Not in scope

Visual refinement, discoverability for newcomers, and anything approaching the
GUI's feature set. If the TUI needs to be pretty, that is what the GUI is for.

## What it does

`yemoja tui <logbook folder>`. A tab per item type, the ids of that type down the left, and
the chosen item's fields on the right.

**The bottom row says what the keys do**, and says what they do *here*: the list and an open
field answer to different things, so a reader is told the ones in front of them rather than
all of them. Where the screen is too narrow it stops at the first that will not fit, rather
than passing over it for a shorter one — a bar that keeps its order is one whose front a
reader learns.

**The bar draws the arrow keys and spells the rest** — `[<,>]`, `[^,v]`, against `[esc]`,
`[enter]`, `[space]` and `[(shift)-tab]`. Each is what its own key cap shows. Drawn in ASCII
because no arrow character survives a console on code page 437, which is what this one opens
on: `←` shows as `?` there, and so does `◀`. That code page does hold arrow glyphs, at
0x18 to 0x1b, but those are the control-code positions and nothing encodes to them. The two
together also keep `[enter] open` on the bar at eighty columns, which spelling them out
would push off the end.

| Key | Does |
|---|---|
| tab, shift-tab | change tab, round the ring — the types, or the keys of an open field |
| left, right | move through the list of items, stopping at its ends |
| up, down | move through whatever fields are in front of you |
| space | open the item the chosen field names |
| enter | go one step further in |
| escape | come one step back out, and nothing where there is nothing to |
| `q`, ctrl-C | leave, from wherever you are |

**Each key does one job and does it everywhere.** Tab is for tabs, whether those are the
types or the keys of an open field; the arrows are for what sits under them. That is what
decided the map: the alternative gave a key two meanings depending on which half of the
screen was live, which meant the screen had to say which half that was, and a reader had to
read it before every press.

**Escape is held to that too, which is why it does not leave.** It used to, where nothing was
open, and that put ending the session one key past the mildest thing a reader does. Leaving
belongs to `q` and ctrl-C, which work from four levels into a keyed field as readily as from
the list, so nothing is gained by lending escape a second meaning. `TUI-6`.

The cost is that the item list answers to left and right rather than to up and down, which
reads oddly against a column of ids. It was taken knowingly: an odd direction is learnt once,
and a key whose meaning moves is read every time.

A list stops at its ends because one that wraps loses the user's place on a long one; the
tabs and the fields wrap, being short enough to see whole. Leaving takes two keys of its own
because raw mode swallows the usual one, and `[q] quit` leads both bars: the key a reader
wants without hunting for it is the one that gets them out, so it is the one thing a screen
too narrow for anything else still says.

**Input ending is leaving too.** A closed terminal or a pipe running out is a session over
rather than a fault, so the loop asks Mordant for a key *or null* and stops on the null. Its
other reader throws instead, which would have put a stack trace where a clean exit belongs.

**Up and down move through whatever fields are in front of you**: the chosen item's, or the
fields of an item inside an open field, or the values of an open field that holds several, or
the rows themselves where an open field holds one value longer than the screen. A field holds
one or the other and never both, so one key does all of them without ever being ambiguous —
and a list of 75 regions, which `children` will bring, needs a cursor rather than a page of
bullets nobody can point at.

**The layout is the descriptions and nothing else.** Nothing in this front end names a type
or a field, so a type added to the logic layer appears here without this changing. `UI-3`.

Three things it shows that a prettier interface would hide. A field with nothing in it is
still listed, because what a type *can* hold is half of what this is for. A value that could
not be read shows why — `! north should be within -90.0..90.0 deg, but was 91.0 deg` — rather
than a blank, which is what an absent field looks like and is not the same thing. And a value
is shown in the form a file writes it, `DATA-76`: a date as `2026-02-23`, a reference with its
`@`, a mix as `EAN32`.

**That is the written form, not the written file.** Two things on screen were never on disk
and are not meant to be. A worked-out value is shown like any other, in italic, because what
a type holds is the point here and half of it is worked out. And a measurement is shown in
the default unit whatever its file declared, reading having converted it — a dive written in
feet is read as metres and shown as metres. Whether a front end should show a value back in
the unit its file used is `UI-2`'s to answer, units being a setting rather than a fact about
the item.

**What a value is, is shown rather than told.** A reference is underlined, so what can be
followed is visible before it is tried. A value written over one that would have been worked
out is bold, and one that was worked out is italic; a value simply written is plain, which is
most of them. All three come from the description and from the origin the layer below answers
with, so nothing here decides which is which.

The chosen field is set apart by reversing its row to the edge of the screen. That was not
specified and is the one mark here that is a choice rather than a rule: it has to differ from
the three above, which say what a value *is*, and from the list's `>`, since both cursors are
live at once.

**A value is cut at thirty-two characters** and marked with `...` where it was cut, since a
column wide enough for the longest remark anybody writes would be a column of mostly nothing.
Three dots rather than the one character that means them: a Windows console on a code page
that is not UTF-8 shows that character as a question mark, which reads as a value nobody could
make sense of rather than as a value that was cut. What was cut is not lost — the field opened
on its own shows all of it.

## Going into an item

Enter opens the chosen field alone, and goes on opening from there. The top line is the way in
— `dive / 2025-05-30#2 / profiles / p1 / pressures / g1` — so a reader who has gone four steps
down knows where they are, and escape brings them back up one at a time.

**A field holding several is a tab apiece**, the open one in brackets, with what is under it
below — the same row of names the types get, and the same key moving along it, because it is
the same thing: several of a kind, one of them open. Tab is left alone where an open field has
no keys, there being nothing for it to move between. Opening such a field arrives at the first
key rather than at a list of them, and **a key is not a stop of its own**: coming back out of
one goes to the field that held it.

**Up and down move over whatever is in front of you, and the bar names it.** Inside an item
that is its fields; at a list it is the values; and at a single value there is nothing to move
between, so they scroll instead.

The cursor lands on what was just left rather than at the top, the way each tab keeps the row
it was on: coming out of something is not the same as arriving somewhere.

**Only one level is shown at a time here.** A reader at an item is choosing which field to go
into, and the rows of everything inside would be rows they cannot choose. Where the whole of an
item is wanted at once, that is the column beside the list.

A field holding values says where it came from, then what the field *is*, then the whole of
what it holds.

What a field is, is read off its description: its name and label, its kind in the words the
manual uses for the same thing, what it holds, whether it is recorded or worked out, and then
whatever only that kind has to say — a number's dimension and the unit it is held in, a range,
a vocabulary, what a reference names.

Only what a field *permits* is said, not what it refuses. A reference that accepts a plain
name says so where it says what it names — `names a  person, or a plain name where there is
no item` — and one that does not says nothing, refusing being what every other reference does.
A row answering *yes* or *no* to a question said the same amount either way.

The `when` over the kinds is exhaustive, so a kind added to the layer below is a compiler error
here rather than a field this view has nothing to say about.

What it holds is the value uncut, wrapped over as many rows as it takes. A field holding an
item shows what is in it rather than the value it has none of, without naming the field a
second time — the heading above has done that.

 **How it came to hold
it goes in brackets beside that heading** — `What it holds (worked out)` — rather than on a row
of its own, since it is one word about the whole of what follows and a reader would otherwise
count it among the values. A field holding nothing says so there and adds nothing under it. A
value that could not be read shows both the reason and what was written, the two together being
what a reader needs in order to fix it.

**Editing is not here.** Enter opens; it does not yet change anything, and neither does
anything else.

**A field holding several values** shows them on the row separated by commas, and where they
do not fit, as many as do followed by how many were left out — `@world ... (8 others)`. Saying
how many is what a plain cut cannot: three dots at the end of a list of regions could mean one
more or forty, and which it is decides whether opening the field is worth it. A list somebody
wrote with nothing in it says `(empty)`, since that is not the same as a field nobody wrote.

**A field holding a series** says how many samples it has and no more — `1000 samples`. A
profile holds thousands, and the first two of them say nothing a reader wants from a row.
Opened, it is one row per sample: when it was taken and what was read, the times lined up on
the right so the values stand in a column, a profile being read down rather than across. The
time is seconds from the start of the recording, which is what the file holds whatever else it
declares — `DATA-58`.

There is no cursor over a series and up and down scroll it, there being nothing in one to
follow.

Opened, a list is a bullet apiece and nothing is left out, which is what makes it possible to
see where one value ends and the next begins. One bullet is set apart, the same way the chosen
field is on the row, and up and down move between them.

**Underlining is finer here than on the row.** The row has one piece of text for the whole
field, so it underlines a field that names items. Open, each value stands alone, so what is
underlined is each value that names one — a plain name asserting no id is not, and neither is
an entry that would not read, because neither opens anything and the underline is the promise
that something will.

**Space opens the item a field names, and does not come back.** On the row that is the first
one that can be opened,
which is the one the row is showing — so a region with one parent needs no drilling in. In an
open field it is the value the cursor is on, which is how one of several is chosen. Following
closes the open field, the reader having arrived somewhere else.

**A mention written in a remark is followed the same way.** `JSON-23` makes `@willy` in free
text name an item by convention, and this is the first interface to act on one. Opened, a
remark's mentions are what up and down move over — the same cursor a list's values get,
because they are the same thing: several followable things in one open field — and space
follows the one it is on.

**Only what resolves is marked.** `JSON-23` asks that of a reader and gives the reason: an
interface that underlined every candidate would light up each address and each `@media` in the
logbook. So `@example.invalid` inside an address stays the text it is, is not underlined, and is
not a stop the cursor moves over. A remark with nothing to follow has nothing to move between,
so up and down scroll it as they always did.

The mention is marked where it sits in the sentence rather than listed away from it, which is
what makes it obvious which `@willy` is meant when a remark holds two. Underlined for what it
is, and reversed for the one the cursor is on, exactly as a chosen bullet is.

Not on the row beside the list. That row shows a cut, escaped line, and there is nowhere in it
to put a cursor.

Nothing records where a reader came from, and nothing will: every item is a tab and a few rows
away, and each tab remembers the row it was on. `TUI-5`.

A plain name asserting no id is not followed, there being nothing to open, and neither is a
value that would not read. Both still count as a value the cursor moves over: it moves over
what a reader sees, not over what happens to be followable.

**A painted line holds no line break.** `remarks` is the one multiline field and a terminal row
is not multiline, so on the row beside the list a break is shown as `\n`, the escape a file
writes it with. Taken instead, one remark would occupy three rows while measuring as one, and in
raw mode leave the cursor wherever the last of them ended. A remark longer than the column is cut
there.

**Opened, the breaks are taken**, one row per line, each set in by the same two spaces so the
value stays in one column. That does not contradict the rule above, which is about a painted
line: three written lines become three painted lines rather than one holding a break. The open
field is where a value is shown whole, and a remark written as three lines that arrives as one
run of text with `\n` in it has been shown in part.

A line still too wide for the screen carries on below, and the rows after the first are set in as
far as the line itself is, so a value that wraps stays in its own column rather than running back
to the edge.

**A row is cut span by span**, so a mention or a reference straddling the break keeps its
underline on both halves. Painting each row with the first span's styles was easier and lost the
mark off anything long enough to need a second row — and the first span of a value row is the
indent, which is styled with nothing at all.

Both go through one function, and it flattens whatever it is given whether or not the text fits.
Measuring the escaped form and painting the unescaped one is exactly the bug that was there: a
break short enough to fit was painted as a break, the terminal took it, and the rest of the value
landed at column 0 outside the rectangle. Nothing reaches that function holding a break now — a
remark is broken into rows before it, and every other text is refused one by the layer below —
but the flattening is what makes a painted line one row, and that should not rest on who calls
it.

The supplied libraries load, so a logbook declaring them shows them under its own items —
they travel inside the application and need no second argument, `LIB-6`.

**An item inside an item is indented under the name of the field holding it**, and each of its
fields is a row that can be chosen like any other — up and down walk all of them, at whatever
depth they sit. Values line up in one column however deep their names sit, which is what lets an eye
run down them, and only the chosen row is set apart, not the rows under it: a whole item
reversed is a wall rather than a cursor.

**A field holding several says its keys and no more** — `Profiles  p1, p2`, through the same
cut and count a list gets. What is under them is unbounded: a dive with three profiles of
twenty fields would be sixty rows of somebody else's business, so they are shown whole where a
reader asks for that field rather than in front of one who did not.

That is the line between the two: **what a field holds is expanded where it is bounded and
summarised where it is not.** One item is a handful of fields and always the same handful; a
list, a series and a set of keys are however many somebody wrote.

The column scrolls, since an item holding items is easily taller than a screen. Up and down
move it, keeping the chosen row in view.

Every shape the model has is reachable now that a dive is described: its profiles under keys,
a profile's depth against time, and its pressures one series per gas source, which is the only
keyed series there is.

Two rows above the body and two below: where the reader is, a rule, and then a rule and the
keys. A screen too small to hold one row of body between them is refused rather than drawn.

It must be started from a real terminal, which is why it cannot be run through Gradle: Gradle
gives a child process no terminal, so there is no run task and `./gradlew :ui:installDist`
writes the start scripts instead.

`Screen` holds no terminal. It answers a key and paints rows of styled text, so the whole
interface is tested without one; the terminal lives in one file beside it and does nothing
else. A row is spans rather than text with escapes in it, so its width is the number of
characters a reader sees and what a style becomes is known only where a terminal is. That is
also why `Screen` is deliberately mutable, which `ui/doc.md` allows a front end to be about
where the user is in it.

## Open questions

- **TUI-1 — Full-screen interactive or a command-driven REPL?** *Settled:* full-screen
  interactive. The tabs, the list and the cursor keys are what was asked for, and a REPL
  would be less code but would not show a whole item at once, which is the thing this exists
  to do.
- **TUI-5 — Whether following a reference can be undone.** *Settled:* **no**, and nothing
  records where a reader came from.

  A stack would be state kept for a journey nobody has to retrace. There is no hierarchy to be
  lost in: every item in the logbook is a tab and a few rows away, and the row a reader was on
  is still chosen when they come back to its tab, because each tab remembers its own. What a
  back key would buy over that is one keystroke, against a second meaning for escape, which
  `TUI-6` has just finished reducing to one.

  It also keeps the interface honest about what it is. A reader following references is
  reading, not navigating a history, and the thing they were looking at has not gone anywhere.

- **TUI-6 — Whether escape leaves the interface.** *Settled:* **no.** Escape comes one step
  back out and does nothing where there is nothing to come out of. Leaving is `q` and ctrl-C,
  and both work from wherever the reader is.

  It used to leave where nothing was open, which put ending the session one key past the
  mildest thing a reader does: escape out of one level too many and the application is gone.
  A step back should not be a thing to be careful with.

  Nothing is lost, because nothing was gained. Quitting was already available at any depth —
  what was missing was the bar saying so, which is why escape looked like the only way out and
  so had to be given a second job. `[q] quit` now leads both bars and the problem dissolves.

  The alternatives were a confirmation at the top level, and a double press. Both rehabilitate
  a meaning worth dropping, and both add state to do it: one a prompt, the other a timeout or
  a nag line.

  Editing will test this again rather than settle differently. Once enter opens an editor, `q`
  is a character somebody is typing and escape means *cancel this edit*; ctrl-C is the only one
  that survives intact. `TUI-3`.

- **TUI-2 — Whether it is shipped to users or stays a development tool.** *Settled:*
  **shipped**, as a power tool. Not yet, since nothing writes and a tool that only reads is
  not one anybody needs, but it is built as something a user will meet rather than as a
  workbench that happens to run.

  That decides arguments rather than features. Input validation and error recovery are held
  to the standard the rest of the application is, not to what a developer will put up with,
  and a message reaching the screen is a message a user reads.

  The raw look stays. Raw is what a power tool is for, and *no polish* was never the same
  claim as *no care*. Nor does this make the TUI the GUI in a terminal: what is out of scope
  above stays out, and `TUI-3` is still open about what it may edit.
- **TUI-3 — Whether it can edit fields the GUI cannot**, and if so, how it avoids letting
   someone write data the GUI then cannot display.
- **TUI-4 — What reads the keyboard.** *Settled:* **Mordant**, with its JNA module.

  Raw keys and the terminal's size are the two things the JDK offers no way to ask for, and
  on Windows — the first desktop target — there is no `stty` to fall back on either: switching
  the console to raw input needs a native call. So a library was needed for those two things
  and nothing else. Output is plain escape sequences written here, which keeps the borrowed
  surface to `enterRawMode`, `readKey` and `size`.

  **Mordant is Apache-2.0 and Kotlin Multiplatform**, so it does not tie this to the JVM the
  way the alternatives would. Its terminal interop is a separate module and there are two:
  `mordant-jvm-ffm` needs JDK 22 and this project is on 21, so `mordant-jvm-jna` is the one,
  at the cost of a bundled native library in the jar. That cost is nothing here — this is an
  inspection tool, not the shipped application.

  JNA is dual-licensed, LGPL or Apache-2.0 from version 4.0, and is taken under Apache-2.0.
  **Lanterna was refused** on its licence: it is LGPL only, and its widgets are not worth
  carrying copyleft into this. JLine is the fallback if Mordant disappoints — BSD, mature,
  and JVM-only, which is why it is the fallback rather than the choice.
