# UI layer

Everything a user or another program interacts with. Several front ends exist side
by side; they share nothing but the [logic layer](../logic/doc.md) underneath them.

## The front ends

| Front end | Purpose | Priority |
|---|---|---|
| [api](api/doc.md) | Programmatic access to the logic layer, for scripting and automation | Later |
| [tui](tui/doc.md) | Raw terminal interface: tables with new/edit/delete | Later |
| [gui](gui/doc.md) | The real application, for a broad audience | **Now** |

The API and TUI are not throwaways. They keep the logic layer honest: anything that
can only be done through the GUI has leaked presentation into business logic.

## Rules that apply to every front end

- **Talk to the Universe.** The logic layer offers one access point — see
  [../logic/doc.md](../logic/doc.md) — and everything a front end does goes through it.
  Item types may be *named*, since they are the vocabulary the whole application
  shares, but no front end opens a file, reaches a repository, or works around the
  Universe to get at what is behind it.
- **Hold no domain rules.** If a front end needs to decide what is valid, what a
  number means, or how something is computed, that decision belongs below it.
  Front ends may hold presentation state (what is selected, what is expanded).
- **Own no data.** The logbook is not a UI concern.
- **Never present decompression output as authoritative.** It is a planning aid,
  never a replacement for a dive computer or training.
- Anything two front ends both need, and that is not presentation, is a sign it
  belongs in the logic layer.

## Structure

```
ui/
  doc.md              this file — rules common to all front ends
  api/                programmatic interface
  tui/                terminal interface
  gui/                the application
    desktop/            Windows (first), macOS, Linux
    phone/              Android (second), iPhone
```

## Open questions

- **UI-1 — Presentation logic ownership.** Formatting a depth, naming a gas mix, ordering
   a dive list — shared across front ends, but presentation. Where does it live?
- **UI-2 — Units and language** are per-user settings that every front end needs. Which
   layer owns them?
- **UI-3 — How much the TUI reuses.** Which fields exist and what they are is settled:
   both read the same description of the type, which the logic layer holds — see
   [../logic/doc.md](../logic/doc.md). What is open is the presentation half — the GUI's
   shared description of fields and grouping
   (see [gui/doc.md](gui/doc.md)) is not inherently GUI-specific. Decide whether the
   TUI consumes it or defines its own, far simpler, view of the same items.

Settled: desktop and phone are **one application sharing its structure**, differing
only in layout. See [gui/doc.md](gui/doc.md).
