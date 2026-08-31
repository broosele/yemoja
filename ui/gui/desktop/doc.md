# GUI — desktop

The desktop form factor: large screen, keyboard and mouse, resizable window,
multiple things visible at once.

Shared rules live in [../doc.md](../doc.md), including the view structure and field
definitions that both form factors share — this document covers only how that
structure is placed on a desktop screen. Platform specifics live one level down:
[windows](windows/doc.md) (first priority), [mac](mac/doc.md), [linux](linux/doc.md).

## Scope

- Layouts that use the available width: lists beside details, side-by-side
  comparison, dense tables where they help.
- Keyboard as a first-class input: shortcuts, tab order, type-ahead, multi-select.
- Window behaviour: resizing, minimum size, remembering size and position.
- Desktop-native affordances: menu bar, context menus, drag and drop, copy/paste,
  file dialogs for import and export.
- Long-running work that continues while the user does something else.

## Not in scope

Anything that only applies to one operating system — that goes in that platform's
own doc.

## Open questions

- **DESK-1 — Window model:** a single window with in-app navigation, or several windows —
   e.g. a dive open beside the list?
- **DESK-2 — Menu bar:** how much lives there versus in the application's own controls,
   given macOS and Windows differ in expectation.
- **DESK-3 — Density:** whether to offer a compact mode for people with thousands of dives.
- **DESK-4 — Whether an edit view is ever a separate window** on desktop, or always an
   inline panel or dialog within the main window.
