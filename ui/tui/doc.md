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

One argument, the logbook's folder. A tab per item type, the ids of that type down the
left, and the chosen item's fields on the right. Left and right change tab and wrap round;
up and down move within the list and stop at its ends, because a list that wraps loses the
user's place on a long one. `q`, escape and ctrl-C all leave, since raw mode swallows the
usual one.

**The layout is the descriptions and nothing else.** Nothing in this front end names a type
or a field, so a type added to the logic layer appears here without this changing. `UI-3`.

Three things it shows that a prettier interface would hide. A field with nothing in it is
still listed, because what a type *can* hold is half of what this is for. A value that could
not be read shows why — `! north should be within -90.0..90.0 deg, but was 91.0 deg` — rather
than a blank, which is what an absent field looks like and is not the same thing. And a value
is shown in the form a file writes, `DATA-76`, so what is on screen is what is on disk.

Two things are absent for now. **Fields holding more than one value are left out** until it
is settled how a list, a keyed collection, an owned item and a series are each shown; only
[`Types`](../../logic/doc.md) knows the difference and only single values are described so
far anyway. And **libraries do not load**: the one argument names a logbook, nothing tells
this where the installation keeps its supplied files, so a logbook using them shows only its
own items.

`Screen` holds no terminal. It answers a key and paints a rectangle of text, so the whole
interface is tested without one; the terminal lives in one file beside it and does nothing
else. That is why `Screen` is deliberately mutable, which `ui/doc.md` allows a front end to
be about where the user is in it.

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
