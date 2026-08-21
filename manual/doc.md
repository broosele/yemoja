# User manual — conventions

This file is **internal** and is not part of the manual. Every *other* file in this
folder is a **chapter**: user documentation, bundled with the application and rendered
in its information tab.

## Audience and purpose

Written for divers, not for whoever builds this. No implementation detail, no
technology names, no internal structure. A reader wants to know what the application
does and how to work with their own data — including editing it by hand, which is why
the file format is documented here rather than internally.

## What belongs here, and what owns what

The manual and the internal `doc.md` files are separate sets with different readers,
so the same rule may legitimately be stated in both — briefly here in terms of what to
type, and internally in terms of what follows from it.

One thing must **not** be stated twice:

- **The data types, their fields, and how each is derived.** `data-fields.md` is the
  only source of truth for these. Internal documents reference it and never restate a
  field list.

## Rules

- **No links out of this folder.** The manual is closed under its own links. It never
  refers to an internal `doc.md`, which would put open questions and undecided design
  in front of a user.
- **Only what is settled.** A page states what is true, not what is being considered.
  An incomplete page is fine; an uncertain one is not.
- **No localisation.** English only; out of scope.

## Markdown that renders

The information tab supports a deliberately small subset, chosen so that pages read
well on a narrow phone screen:

- Headings and paragraphs
- Bullet lists
- Numbered lists
- Fenced code blocks, for showing what a file looks like
- Tables, kept narrow — see below

Not supported, and not to be used:

- Images
- Inline HTML

### Tables

A table earns its place where the content really is a grid — every row answering the
same questions, every cell a label rather than a sentence. Where it is a list of things
with something to say about each, a list says it better and survives a narrow screen.

Three constraints, which come from a phone in portrait holding roughly forty characters
at a readable size, and fewer for a diver who has set larger text:

- **Two or three columns.** Three only where all of them are short.
- **Cells under about twenty-five characters.** A cell that needs a comma is usually
  prose in disguise.
- **No sentences.** If a column is explaining something, it is a paragraph.

A table that fails these does not degrade gracefully — it wraps into something harder to
read than the prose it replaced, and it does so only on the screens where reading is
already hardest.

## Maintenance

Updating a chapter may be deferred until the feature it describes is complete and
tested, but it is part of that feature's work and is not optional. The exception is
`data-fields.md`, whose field lists are the definition of those fields and are kept
current as they are decided.

## Chapters

- `data-format.md` — the logbook on disk: how the files are arranged, named and
  written, and how to edit them by hand. **Written.**
- `data-fields.md` — every kind of item and every field it may hold. **Written**,
  and kept current as fields are decided.
- `decompression.md` — the model, what it assumes and what it cannot know.
  **Written.** It carries a safety obligation the others do not, and the wording is
  reviewed before any release.
- `settings.md` — the two settings files, which of them wins, and why they are not part
  of the data format. **Written**, and grows as settings are added.
- `app-info.md` — version, credits, licence, and the warranty and safety notices.
  **Written.**
- `uddf.md` — what survives a UDDF file, in both directions, and what does not.
  **Written**, and kept current as the comparison is. It states what the two formats can
  and cannot say about a dive, which is settled; it does not describe a button, and gains
  a paragraph about how importing is *driven* when that is.
- Getting started — not written.
- Dives, gear, sites and buddies — not written; waits on the data model.
- Dive computers and importing — not written. `uddf.md` covers what an imported file
  keeps; this one covers doing it.
- Sync and backup — not written. The behaviour is settled, so this is writable whenever
  the feature is.
