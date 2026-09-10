# UI layer

Everything a user or another program interacts with. Several front ends exist side
by side; they share nothing but the [logic layer](../logic/doc.md) underneath them.

## The front ends

| Front end | Purpose | Priority |
|---|---|---|
| [api](api/doc.md) | Programmatic access to the logic layer, for scripting and automation | Later |
| [tui](tui/doc.md) | Raw terminal interface: tables with new/edit/delete | **Started** |
| [gui](gui/doc.md) | The real application, for a broad audience | **Started** |

The API and TUI are not throwaways. They keep the logic layer honest: anything that
can only be done through the GUI has leaked presentation into business logic.

**One command, one front end.** The application starts in one place, `yemoja`, which names a
command per front end — `yemoja tui <logbook folder>` — so a user does not have to know which
executable each of them became. Nothing else reads a command line.

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
- **UI-4 — Which language a front end shows.** Split out of `UI-2`, which bundled it with
   units on the assumption that both were the same question. They are not, and the reason is
   that **user-facing text is produced below this layer.** `north should be within
   -90.0..90.0 deg, but was 91.0 deg` is a validation message from the data layer, and the
   project's own code style requires such a message to say what was expected and what arrived
   precisely because it reaches a user: reading a field splices it into the reason on an
   unusable result. A field's `label` has the same problem one layer up, in the logic layer's
   descriptions.

   So translating is not a matter of a front end choosing words. Either the layers below learn
   about languages, which neither should, or what they produce stops being text — a message
   becomes a named fault with values, and a front end says it. That is a larger change than
   picking an owner, and nothing waits on it: an English-only application is a working one.
- **UI-2 — Which layer owns the units a user wants shown.** *Settled:* **the Universe holds
  the setting, and a front end asks it.**

  Two kinds of unit already exist and neither is this one. A file declares what *it* is
  written in, which `DATA-8` settles and which the data layer reads because it is a fact about
  the file. Reading converts that into the defaults, so a value in memory is in metres and bar
  whatever its file said. What a user wants *shown* is a third thing, and it is in no file the
  format owns — `manual/settings.md` puts settings beside the logbook and says outright that
  they belong to the application rather than to the format.

  The machinery for it was already complete. `format(value, units)` takes a `Units` on every
  kind of field, and it takes one precisely so that a value can be written in a file's units
  and shown in the user's — two answers from one value. Nothing new is needed below; what was
  missing was somewhere for the choice to live.

  **The Universe is where application state lives**, so the choice is read once from the
  settings and every front end asks the same question of the same object. The alternative was
  each front end reading the settings itself, which is less machinery and lets a TUI and a GUI
  disagree about what the user chose. Putting it in the data layer was refused for the reason
  the third kind exists at all: a source-neutral layer holding a user's preference would make
  `format` answer one way when there are two right answers.

  The manual has promised this since it was written — *"your preferences: the units you want to
  be shown"* — and listed only gradient factors. `FEAT-19` is the neighbouring want, units
  declared per item, and is separate: that is about what a file may say, not about what a
  screen shows.

- **UI-3 — How much the TUI reuses.** *Settled:* **neither.** Which fields exist and what
   they are was already shared — both read the same description of the type, which the logic
   layer holds, see [../logic/doc.md](../logic/doc.md). The presentation half was the open
   part, and the TUI takes none of it: a tab per type in the order the item set was built
   with, every single-valued field in the order the type declares it, the items of a type in
   the order that type asks for, and no grouping, ordering or labelling of its own. So the
   GUI's shared description of fields and grouping (see [gui/doc.md](gui/doc.md)) stays the
   GUI's.

   The item order came later and was made to obey this rather than to work around it: it is
   `orderedBy` on the type, applied by a front end that never learns what it names. `DATA-89`.

   What that buys is that a type added to the logic layer appears in the terminal without the
   terminal changing, which is what makes this front end a check on the layer below rather
   than a second place to describe the same items.

Settled: desktop and phone are **one application sharing its structure**, differing
only in layout. See [gui/doc.md](gui/doc.md).
