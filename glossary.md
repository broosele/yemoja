# Glossary

Every term this project has given a specific meaning, and where that meaning is written
down.

**This document defines almost nothing.** Each entry says only enough to tell one term
from another, next to a link to the document that owns it. Each fact keeps one home; this
is an index to those homes, not a second copy. Where an entry and its source disagree,
the source is right and the entry is a bug.

The exception is a term used across several layers and belonging to none of them, which
would otherwise be defined wherever it happened to be needed first. Those are marked
*defined here*, and here is their one home — everywhere else uses them without
restating.

Words are listed under the layer that owns them. A term used everywhere is listed where
it is defined, not where it is used.

## Items and their fields

Owned by [data/doc.md](data/doc.md).

| Term | Meaning |
|---|---|
| **item** | one thing in a logbook — a dive, a person, a region. The class is `Item`; the word is the same in prose, in code and in the manual |
| **`ItemSet`** | everything loaded, from logbook and libraries, with shadowing already applied |
| **`ItemDescription`** | what one item type is: its name and its fields |
| **`FieldDescription`** | what one field is, in enough detail to parse, check and show it. One kind per description — `NumberDescription`, `TextDescription`, `ReferenceDescription` — and each describes a field rather than being one |
| **primary** | a field whose value is recorded and nothing else |
| **derived** | a field worked out from other values, never stored |
| **overrideable** | derived, but a stored value *may* be present and corrects it when it is — a correction, never a cache |
| **referenceable item** | an item other items can point at, having an id of its own |
| **owned item** | a group of fields belonging to one item, with no id of its own |
| **key** | what an owned item sits under within its owner's collection, never a field it carries. Local: it means nothing outside that collection, and pointing at one is written `*p1`, not `@` |
| **parent** | the item an owned item belongs to; a link, never stored |
| **one-off value** | a plain name written where a reference could go, asserting no id |
| **id** | what names an item's file or key, and what `@` points at — not a field |
| **id proposal** | the id an item suggests from its own data, before clashes are resolved |
| **`#<index>`** | what distinguishes items whose id proposals collide |
| **name** | a field saying what a thing is called, and never the id. Primary for some types and derived for others — a person's name follows from their first, middle and last |
| **usable** | a field or derivation gave a value, and it can be used |
| **absent** | nothing was recorded and nothing could be worked out. There is one absent state, not several |
| **unusable** | something is recorded but cannot be used — a word where a depth belongs, a value outside a fixed set, a reference to the wrong kind of item. Kept as written, and carries why |
| **unrecognised** | a field name in the data that the description does not define — a newer version's, or a misspelling. Kept whole so a round trip survives, and never judged |
| **raw** | a value exactly as it was stored, carried by *unusable* so an interface can show what it found |
| **fixed set** | a closed list of values, where nothing outside it means anything |
| **suggested vocabulary** | an open list offered as help, where anything else is still accepted |
| **dimension** | what a numeric field measures — length, mass, time, pressure, and so on |
| **cylinder** *(defined here)* | a portable high-pressure vessel, taken along and swapped when empty. Never a *tank*, which is a fixed bulk vessel refilled where it stands — the two are different objects and this project uses only the first, however common the other is in speech |
| **units declaration** | a `units` block saying what the numbers in its own file are written in |

## Storage

Owned by [data/json/doc.md](data/json/doc.md), except where noted.

| Term | Meaning |
|---|---|
| **logbook** | one diver's data: a folder, and everything under it |
| **`yemoja.json`** | the file at the top of a logbook, saying whose it is and which libraries it uses. It holds no paths: the rest is found by convention |
| **`settings.json`** | preferences that travel with the logbook — the middle layer of the three. App-owned, outside the data model; see [manual/settings.md](manual/settings.md) |
| **`settings.local.json`** | preferences for one installation, never synced or backed up; see [manual/settings.md](manual/settings.md) |
| **journal** | Yemoja's own history of a logbook, kept in full and never compacted |
| **changeset** | one unit of change, reversible whole; a rename and its rewrites are one |
| **action** | one reversible step inside a changeset |
| **library** | reference data shipped with the application — see [data/libraries.md](data/libraries.md) |
| **shadowing** | a logbook item replacing a library one of the same id — see [data/libraries.md](data/libraries.md) |

## Behaviour

Owned by [logic/doc.md](logic/doc.md), except where noted.

| Term | Meaning |
|---|---|
| **Universe** | the single door every front end goes through; holds one or more item sets |
| **domain rule** | a check that needs to know the file is about diving |
| **finding** | what a domain rule produces — aimed at an item, carrying a severity, never a refusal |
| **reconciliation** | folding items that arrive from elsewhere into the logbook — see [logic/reconciliation.md](logic/reconciliation.md) |
| **repository** | somewhere a logbook *lives*: read, written, holding history — see [logic/reconciliation.md](logic/reconciliation.md) |
| **importer** | somewhere items *come from*, one way, with no history — not a repository. See [logic/reconciliation.md](logic/reconciliation.md) |
| **ancestry** | whether a common earlier version exists; sync has one, import does not — see [logic/reconciliation.md](logic/reconciliation.md) |
| **authority** | what a source is entitled to overwrite, and what it must leave alone — see [logic/reconciliation.md](logic/reconciliation.md) |

## Interface

Owned by [ui/gui/doc.md](ui/gui/doc.md), except where noted.

| Term | Meaning |
|---|---|
| **front end** | one of several interfaces over the logic layer — see [ui/doc.md](ui/doc.md) |
| **form factor** *(defined here)* | the two shapes the application takes: a large screen with keyboard and mouse, and a small touch screen. Which one applies follows from what the screen affords, not from the operating system — Windows and macOS are one form factor, Android and iPhone the other |
| **tab** | one of the eight top-level divisions of the application |
| **selector** | narrows a collection down to one item |
| **item view** | shows one item, arranged for reading, and changes nothing |
| **edit view** | shows every field of one item, arranged to be filled in |
| **description** | the shared data driving both views, on top of the field descriptions above |
| **progressive disclosure** | how much of an item is open at once, which differs by form factor |

## Kinds of value

The names in brackets after each field in the manual. Owned by
[manual/data-format.md](manual/data-format.md), which is written for divers rather than
for whoever builds this.

| Term | Meaning |
|---|---|
| **text** | one line, no leading `@`, no line breaks, tabs or control characters |
| **multiline text** | line breaks and a leading `@` allowed, tabs still not |
| **whole number** | no decimal point |
| **number** | with or without one |
| **true or false** | written `true` or `false`, and nothing else |
| **date** | always `"2026-02-23"` |
| **time** | always `"09:15:00"` |
| **reference** | another item's id, with `@` in front |
| **key reference** | one entry inside the item you are reading, with `*` in front |
| **gas** | a breathing mix as divers write it — `AIR`, `EAN32`, `TMX18/35` — parsed for its fractions |
| **series** | a measurement through a dive: pairs of time and value, time always in seconds |
| **keyed series** | several series together, each under a key |
| **keyed owned items** | several owned items together, each under a key |
| **fixed set** | a value from the short closed list given with the field — the term above, as the manual writes it |
| **list of** | several values together, in an array |
| **owned item** | not a value: a set of fields kept together, described above |
