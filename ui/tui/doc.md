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

| Key | Does |
|---|---|
| left, right | change tab, round the ring |
| up, down | move through whatever is in front of you, stopping at its ends |
| tab, shift-tab | move between the chosen item's fields, round the ring |
| space | open the item the chosen field names |
| enter | open the chosen field on its own |
| escape | close what is open, or leave where nothing is |
| `q`, ctrl-C | leave |

A list stops at its ends because one that wraps loses the user's place on a long one; the
tabs and the fields wrap, being short enough to see whole. Leaving takes two keys of its own
because raw mode swallows the usual one, and escape is a third where nothing is open.

**Up and down move through whatever is in front of you**: the list of items, or the values of
an open field that holds several, or the rows themselves where an open field holds one value
longer than the screen. A field holds one or the other and never both, so one key does all
three without ever being ambiguous — and a list of 75 regions, which `children` will bring,
needs a cursor rather than a page of bullets nobody can point at.

**The layout is the descriptions and nothing else.** Nothing in this front end names a type
or a field, so a type added to the logic layer appears here without this changing. `UI-3`.

Three things it shows that a prettier interface would hide. A field with nothing in it is
still listed, because what a type *can* hold is half of what this is for. A value that could
not be read shows why — `! north should be within -90.0..90.0 deg, but was 91.0 deg` — rather
than a blank, which is what an absent field looks like and is not the same thing. And a value
is shown in the form a file writes, `DATA-76`, so what is on screen is what is on disk.

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

## One field on its own

Enter opens the chosen field alone. It says where it came from — type, item and field — so
that a reader who followed a reference into it knows where they are, then what the field *is*,
then the whole of what it holds.

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

What it holds is the value uncut, wrapped over as many rows as it takes. **How it came to hold
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

Opened, a list is a bullet apiece and nothing is left out, which is what makes it possible to
see where one value ends and the next begins. One bullet is set apart, the same way the chosen
field is on the row, and up and down move between them.

**Underlining is finer here than on the row.** The row has one piece of text for the whole
field, so it underlines a field that names items. Open, each value stands alone, so what is
underlined is each value that names one — a plain name asserting no id is not, and neither is
an entry that would not read, because neither opens anything and the underline is the promise
that something will.

**Space opens the item a field names.** On the row that is the first one that can be opened,
which is the one the row is showing — so a region with one parent needs no drilling in. In an
open field it is the value the cursor is on, which is how one of several is chosen. Following
closes the open field, the reader having arrived somewhere else.

A plain name asserting no id is not followed, there being nothing to open, and neither is a
value that would not read. Both still count as a value the cursor moves over: it moves over
what a reader sees, not over what happens to be followable.

**A painted line holds no line break.** `remarks` is the one multiline field and a terminal row
is not multiline, so a break is shown as `\n`, the escape a file writes it with. Taken instead,
one remark would occupy three rows while measuring as one, and in raw mode leave the cursor
wherever the last of them ended. A remark longer than the column is cut there; showing it whole
would mean a field taking several rows, which is a layout question nobody has asked yet.

The supplied libraries load, so a logbook declaring them shows them under its own items —
they travel inside the application and need no second argument, `LIB-6`.

**A keyed collection, an owned item and a series are still left out** until it is settled how
each is shown. Only [`Types`](../../logic/doc.md) knows the difference, and none of the three
is described there yet anyway.

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
- **TUI-2 — Whether it is shipped to users or stays a development tool.** Affects how much
   input validation and error recovery it needs.
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
