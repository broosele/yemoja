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

| Key | Does |
|---|---|
| left, right | change tab, round the ring |
| up, down | move in the list, stopping at its ends |
| tab, shift-tab | move between the chosen item's fields, round the ring |
| space | open the item the chosen field names |
| enter | open the chosen field on its own |
| escape | close what is open, or leave where nothing is |
| `q`, ctrl-C | leave |

A list stops at its ends because one that wraps loses the user's place on a long one; the
tabs and the fields wrap, being short enough to see whole. Leaving takes two keys of its own
because raw mode swallows the usual one, and escape is a third where nothing is open. Up and
down move in whatever is in front of the user: the list, or an open field too long to see at
once.

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

**A value is cut at thirty-two characters** and marked where it was cut, since a column wide
enough for the longest remark anybody writes would be a column of mostly nothing. What was cut
is not lost: the field opened on its own shows all of it.

## One field on its own

Enter opens the chosen field alone. It says where it came from — type, item and field — so
that a reader who followed a reference into it knows where they are, then what the field *is*,
then the whole of what it holds.

What a field is, is read off its description: its name and label, its kind in the words the
manual uses for the same thing, what it holds, whether it is recorded or worked out, and then
whatever only that kind has to say — a number's dimension and the unit it is held in, a range,
a vocabulary, what a reference points at. The `when` over the kinds is exhaustive, so a kind
added to the layer below is a compiler error here rather than a field this view has nothing to
say about.

What it holds is the value uncut, wrapped over as many rows as it takes and scrolled with up
and down. A value that could not be read shows both the reason and what was written, since the
two together are what a reader needs in order to fix it.

**Editing is not here.** Enter opens; it does not yet change anything, and neither does
anything else.

**Nothing shown today is a reference.** No field of a person, a region or a piece of gear is
one — the only single references the manual defines sit inside owned items, which are neither
described nor shown — so underlining and space are correct and unreachable until those
arrive. They are tested against invented types.

**A painted line holds no line break.** `remarks` is the one multiline field and a terminal row
is not multiline, so a break is shown as `\n`, the escape a file writes it with. Taken instead,
one remark would occupy three rows while measuring as one, and in raw mode leave the cursor
wherever the last of them ended. A remark longer than the column is cut there; showing it whole
would mean a field taking several rows, which is a layout question nobody has asked yet.

The supplied libraries load, so a logbook declaring them shows them under its own items —
they travel inside the application and need no second argument, `LIB-6`.

One thing is absent for now. **Fields holding more than one value are left out** until it is
settled how a list, a keyed collection, an owned item and a series are each shown; only
[`Types`](../../logic/doc.md) knows the difference, and only single values are described so
far anyway.

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
