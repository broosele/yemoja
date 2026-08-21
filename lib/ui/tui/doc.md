# UI layer — TUI

A deliberately raw terminal interface: tables of items with new, edit and delete.
No polish, no visual design.

Not planned for now. This file records the intent.

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

## Open questions

- **TUI-1 — Full-screen interactive or a command-driven REPL?** The first is nicer to use,
   the second is far less code and scripts more naturally.
- **TUI-2 — Whether it is shipped to users or stays a development tool.** Affects how much
   input validation and error recovery it needs.
- **TUI-3 — Whether it can edit fields the GUI cannot**, and if so, how it avoids letting
   someone write data the GUI then cannot display.
