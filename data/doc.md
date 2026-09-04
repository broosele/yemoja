# Data layer

Owns *what* the data is and *how it is asked for* — never *where it is kept*.

Concrete storage lives in subdirectories, one per source. Only [json](json/doc.md)
is planned for now; the split exists so a second source (a server API, a database)
can be added without touching anything above this layer.

A source here means a **repository** — somewhere the logbook lives, read and written,
holding history. It does not mean a dive computer or another application's export
file: those are one-way *importers* and are covered in
[../logic/reconciliation.md](../logic/reconciliation.md). The two are easy to confuse
and behave differently.

Read-only reference data shipped with the application — regions, gear catalogues,
certification schemes — is a third thing again, described in
[libraries.md](libraries.md).

## Scope

- The machinery of the item model: what a description of a type can say, and what
  reading, writing, resolving and checking do with it. The types themselves are not
  here — see *Where the descriptions live* below. The authoritative list of item
  types, their fields and how each is derived lives in the user manual,
  `manual/data-fields.md`, and is not restated anywhere; this document covers the rules
  those fields obey.
- The contracts a data source must satisfy (read, write, list, query, delete).
- Rules that hold regardless of storage: id, required fields, valid ranges,
  how items reference each other, units.
- Schema versioning and migration. The version an item carries, the rule for what to
  do with an unknown one, and the migration itself, which runs **once when a logbook is
  opened**. Everything above sees current-shape items only, so no code outside the
  migration needs to know that more than one version has ever existed. An `ItemSet` is
  constructed in full at that moment anyway — see `DATA-5` — so it costs no extra pass
  over the data. How a version is physically recorded is the source's business.

## Not in scope

- File formats, directory layouts, serialisation, transactions, history, sync,
  network. All of that belongs to a specific source.
- Anything that interprets the data. Computing a statistic, validating a dive plan
  against a decompression model, deciding which dives are "recent" — that is the
  [logic layer](../logic/doc.md).

## Item model

### Three kinds of field

| Kind | Stored? |
|---|---|
| **Primary** | Yes. The value exists only because someone or something recorded it. |
| **Derived** | Never. Recomputed from other stored values whenever it is needed. |
| **Overrideable** | Only when set. Normally derived, but a correcting value may be stored when the derived answer is known to be wrong or incomplete. |

The buddies on a dive are primary. The *number* of buddies is derived from that list
— but overrideable: you may remember that five people were on the dive while only
knowing three of their names.

An override is stored only when it has been set. Absent means *derive it*. Clearly
derived and never stored under any circumstances: statistics and totals, averages,
surface intervals, gas consumption rates, anything aggregated across dives.

**A derived value is not necessarily a function of its own item.** The children of a
region are found by inspecting the `parents` of every other item; a dive's number
comes from counting the dives before it. Derivation needs the surrounding collection
at least as often as it needs the item, which bears on what a source has to be able
to answer — see `DATA-4` and `DATA-5`.

It is also what lets a read-only [library](libraries.md) take part. A region supplied
with the application gains a child when the user adds one beneath it, because the
child list was never stored in the library to begin with.

### What a derived value can be

A derivation does not always produce a value. There are three outcomes, and the last
two must not be collapsed into one:

- **Usable** — a value, and it can be used.
- **Absent** — the inputs are not there. A dive with no profile has no maximum depth
  to work out. Nothing is wrong; there is simply nothing to say.
- **Unusable** — the inputs are there and cannot be used. A dive site whose water type
  reads `brackish` cannot yield a depth from a pressure. The input need not be malformed:
  a gas source whose `cylinder` points at a regulator has a reference that resolves
  perfectly to an item that cannot supply a capacity, and a cylinder whose capacity was
  never filled in fails the same way.

Both absent and unusable show as nothing in a list, which is exactly why they need to be
distinguishable underneath. They call for opposite responses: absent may be fine, or may
mean *record more*; unusable means something already recorded is wrong and can be put
right. Treat them alike and a mistyped water type becomes a column of silent blanks with
nothing to point the user at.

**Unusable spreads, except in aggregates.** A value worked out from an unusable one is
unusable in turn — the safe default, because a figure built on something unusable is not
to be trusted. Statistics gathered across many items are the exception: a total time
underwater, a greatest depth, a count of buddies, skip the items they cannot use rather
than collapsing the whole figure. Beyond those two rules, individual cases are decided as
they arise.

An aggregate therefore reports **what it was based on** as well as its result: how
many items it used, and how many there were. A bare number cannot be checked, and a
total quietly short by five dives looks exactly like a correct one.

An unusable value therefore carries its reason — which field, which value, and what was
expected. "Unusable" on its own tells a user nothing they can act on, and the whole point
of separating it from absent is that it is actionable.

**An override repairs an unusable value.** Where a field is overrideable, a stored
correction is used and the failing derivation is never reached. Overrides are the general
escape from unusable inputs rather than a special case, which is worth knowing before
inventing a second repair mechanism.

### Derived values arriving from outside

An overrideable field is also the answer to what happens when a source supplies a
value that would normally be derived. Such a value is neither discarded nor promoted
to primary — it is stored as an override, **but only where it disagrees with what would
be derived**. A dive computer reporting a maximum depth identical to the deepest point of
the profile it also supplied has told us nothing, and writing it down would record one
fact in two places that could later disagree. The difference is the only part worth
keeping.

This matters more than it sounds. A dive computer's reported maximum depth is usually
*more* accurate than its own sampled profile, because sampling can miss the peak.
Discarding it as derived would throw away the better number; treating it as primary
would mean it never gets recomputed when the profile is corrected. As an override it
is kept, attributed, and can be cleared to fall back to the profile.

The same applies to a dive computer's own decompression state, which was calculated
with that device's model, settings and preceding dive history and cannot be
reproduced here.

### What primary data can be

- **Simple values** — number, true or false, date, time, a breathing mix, and two kinds
  of text. A date, a time and a mix are alike in having one written form everywhere,
  which no `units` declaration affects, and in being parsed rather than taken at face
  value: `EAN32` yields a fraction of oxygen, and a mix that cannot be read is unusable
  rather than absent.

  **Text** is a single line. It may not begin with `@` or `*`, may not carry line breaks
  or tabs, and may not carry other control characters. Those two restrictions are what
  make a one-off value unambiguous: a string beginning with `@` is a reference to an item
  and one beginning with `*` is a reference to a key, always, and a string that is neither
  cannot begin with either. Nothing needs escaping and nothing needs guessing at.

  **Multiline text** allows line breaks, and allows a leading `@` — a field of this kind
  is never read as a reference, so there is nothing to disambiguate. Tabs are excluded
  here too, for now; that is the restriction most likely to be relaxed, since a tab in
  prose is merely untidy rather than ambiguous. `remarks` is the only field of this kind.
- **A value from a fixed set** — a closed list, and nothing outside it means anything.
  A dive site's `water_type` is `salt`, `fresh` or `en13319`. These are the fields
  whose values the application acts on: water type decides how depth follows from
  pressure, so an unfamiliar value is not merely unusual, it is unusable.
- **A value from a suggested vocabulary** — a plain string with values the application
  knows about, where the set suggests rather than restricts: a region's `category`, a dive
  site's `facilities`, a dive's tags. Nothing is refused for being unfamiliar, because
  nothing depends on it beyond being shown back.

  The distinction is not stylistic. Ask whether an unrecognised value would stop the
  application doing something: if it would, the set is fixed; if it would not, it is a
  vocabulary.

  Three words are in use here and they are deliberately not interchangeable:

  - **`category`** is the broad group an item falls in — a region's, a gear item's, an
    operator's.
  - **`kind`** is a finer classification within that group, where one is worth having. A
    piece of gear is `BCD` by category and a `wing` or a `jacket` by kind. Only gear
    carries both, because only gear needs the distinction so far.
  - **`type`** is what was *done*, not what something is. Only a maintenance item uses
    it, and the item's sort was never in doubt.

  None of these is an oversight to be tidied into the others.
- **Lists** and **dictionaries** of them.
- **Owned items**, inline (see below).
- **References** to referenceable items.

### References and one-off values

A reference identifies a referenceable item. A field that normally holds references
may instead hold a plain value — a *one-off* — used in two situations:

- The item is not known well enough to record: a buddy whose name is remembered but
  who is not in the logbook.
- Naming something must **not** imply it is the same thing named elsewhere. Two dives
  each listing a one-off `john` make no claim that it was the same John.

The one-off is deliberately not a weaker reference. It asserts no id at all,
and must never be silently promoted to one — including during import or sync.

**It is a name and carries nothing else.** A one-off with properties would be a weaker
reference by another route: two dives naming a site at the same coordinates would be read
as the same place, which is the whole thing a one-off refuses to say. Anything with
properties is an item — see `JSON-7` in [json/doc.md](json/doc.md).

**A generic item is the same idea with properties attached.** A piece of gear marked
`generic` describes a kind of thing — a five millimetre wetsuit — rather than one item.
Referring to it twice claims no more than writing the same plain name twice would: not
that it was the same item, only that it was that sort of item. Unlike a one-off it is a
real item with real fields, so buoyancy and weighting still compute.

What follows is that anything true of *one item* is meaningless on a generic item: a
serial number, a service history, and any count of the dives it has been on. Those belong to the item in someone's garage, which is a separate item that is
not generic.

### A relationship is stored once

Where two items relate to each other, one side holds the reference and the other
derives its half. A dive names the trip it belongs to; the trip's list of dives follows
from that. A region names its parents; their `children` follow from that.

Storing both halves would be convenient and would eventually disagree with itself —
and there would be no way to tell which half was right. Deriving one of them makes that
impossible rather than merely discouraged. Which side stores it is decided per
relationship, and is normally the many side, so that adding an item touches one file
instead of two.

How references and ids are written down is a storage concern; see
[json/doc.md](json/doc.md).

### Units

**A number means one of two things, and never a third.** Either the default, or what
the file it sits in says. Nothing else is consulted.

The default is SI but for four dimensions, where strict SI is close to unreadable for
diving — 200 bar is 20000000 Pa, 14 °C is 287.15 K, a twelve-litre cylinder is
0.012 m³:

| Dimension | Default | |
|---|---|---|
| length | metre | SI |
| mass | kilogram | SI |
| time | second | SI |
| temperature | degree Celsius | not SI |
| volume | litre | not SI |
| pressure | bar | not SI |
| angle | degree | not SI |
| density | kilogram per cubic metre | SI |

Any file may carry a `units` declaration replacing any of them for that file, and that
is the whole of the rule. **A declaration reaches no further than the file it is
written in.** There is no logbook-wide setting, nothing inherited from `yemoja.json`,
and no per-item declaration inside a file holding several items. A name the build does
not know costs that dimension its measurements and leaves the rest of the file alone.
`DATA-87`.

That is a deliberate loss of expressiveness. Two cylinders in one `gear.json` cannot be
written in different volumes, and a user who owns both metric and imperial kit has to
convert one or split the file — the format already allows a directory of one file per
item for any type, not only dives, so the case remains expressible. What is bought is
that the units of a number can be determined by looking at the top of the file it is in,
with nothing else open. A resolution order the reader cannot see is a source of errors
in a format whose purpose is being readable by hand.

**Libraries stop being a special case.** A shipped library used to need exempting from
the chain, so that a logbook declaring bar could not change what the library meant. With
no chain, that follows from the rule. Library files declare their units explicitly all
the same: they are published and effectively permanent, and a file that depends on no
default cannot be broken by one changing.

Three things follow:

- **Every numeric field has a dimension** — length, mass, time, temperature, volume,
  pressure, angle — recorded in the schema. That is how a declaration knows which
  fields it governs.
- **Values are converted on the way in and back on the way out.** *Amended:* this said
  the opposite — kept as written, converted at the point of use — to avoid the drift that
  converting twice causes. The drift is real and was measured: 18.3 pounds returns as
  18.300000000000004, and a value changing in the last decimal on every save is noise in
  exactly the diffs versioning depends on.

  What decided it the other way is that a **range has to mean one thing**. A bound like
  `0.0..332.35` is metres, and a depth in feet must become metres before it is checked, or
  every bound needs a unit and every comparison a conversion. Converting early keeps that
  in one place, and nothing above a description ever meets a foot.

  The drift is then a writing problem, and `DATA-88` settles it: twelve significant digits,
  which is past where the tail lives and short of anything anyone writes.

  What a writer must still do is keep the declaration. A file's `units` block is data and
  has to survive a read-and-write cycle untouched, or the units someone chose by hand are
  silently replaced on the next save.

**Sample times inside a profile are always seconds**, and no declaration reaches them.
They are exempt for the reason dates are: a profile holds thousands of numbers, so a
declaration touching them would rescale a whole recording at a stroke, and the benefit —
writing 2 instead of 120 — is nil for figures no one reads by eye. Everything else in a
profile follows the file's declaration as usual; a device reporting in another unit is
converted once, at import.

Note that this is about **how a number on disk is to be read**, which is not the same
as what a person is shown. Display preference is a separate concern — see
[UI](../ui/doc.md).

**Instants and quantities are not the same thing.** A date or a time names a point and
is written one way everywhere, because a second notation buys nothing and `03/04/2026`
means two different days to two readers. A duration is a quantity like any other, with
the time dimension and the second as its base, so unit scoping applies to it exactly as
it does to length or pressure. A dive's `duration` is a number of seconds, and a file
may say it is written in minutes.

### A field's name says how many

A singular field name holds one thing; a plural one holds a list. `region` on an
operator is the one region it sits in; `regions` on a dive site is every region that
site belongs to. `profiles` on a dive is a collection, `medical` on a person is not.

The rule is worth stating because it is the only signal, and because breaking it is
invisible until someone reads the wrong shape out of a file they cannot see the schema
for.

One deliberate exception: a person's `middle_names` is a single piece of text holding
however many there are, not a list of them. The plural belongs to the English phrase
rather than to the field. It is the exception that proves the rule needs stating.

### A proportion runs from 0 to 1

`compressible_fraction` is `0.29`, and a gradient factor of 20 is `0.2`.

Two things run from 0 to 100 instead. A name containing *percentage* says so outright. And
a term of art already defined as a percentage keeps its own name and its own scale: `cns`
is the only one in the model, because the CNS oxygen clock is quoted against a limit of
100 wherever it appears, and `cns_percentage` would put a scale word on a term that
carries its scale already.

Nothing in a description says which. A proportion is dimensionless either way by
`DATA-68`, and a range cannot tell them apart, since a CNS clock passes 100 on purpose. So
[../manual/data-fields.md](../manual/data-fields.md) is where a field's scale is read.

### Fields common to every type

Some fields belong to every referenceable type rather than to one of them. `remarks` —
optional free-form multi-line text — is the first. Common fields are defined once, in
the manual's introduction to the item types, and are not repeated per type.

An **owned** item has `remarks` only where its own definition says so. Free-form text
is bulky wherever it is shown, and an interface offering a notes box beside every
cylinder and every service entry spends its space badly — so this one is granted
deliberately rather than by default.

### Types are not subdivided

An item type covers every variant of the thing it names. Gear is one type, whether
the item is a regulator, a cylinder or a camera; a variant simply omits the fields
that do not apply to it.

Validation is deliberately permissive. A field that is absent is absent, and the
application does not insist on a set of fields being present because of some other
field's value. The alternative — a type per variant, or required-field rules
conditioned on a `category` — buys strictness that a personal logbook does not need and
makes hand-editing hostile.

### How a type is described

There is no class per item type. A dive, a person and a region are all the same class —
`Item` — holding the description of its type, a map of field name to value, a second
mapping for the fields it does not recognise, and the items it belongs to.

The description is data. An `ItemDescription` names a type and lists its fields. A
`FieldDescription` says what one field is, in enough detail that the parser, the
structural checks and every front end can work from it and nothing has to name a field
in code. **Three** things need describing differently, so they are implementations rather
than one class with a flag: a **value**, a **reference**, and an **owned item** — a set of
fields kept inside its owner.

**Two things are orthogonal to that split**, and both are properties any of the three may
carry rather than types of their own:

- **Primary, derived and overrideable** — whether a field is recorded, worked out, or
  worked out and correctable.
- **Cardinality** — single, a list, keyed, a series through a dive, or a keyed series. See
  `DATA-67`.

**A value is described by kind rather than by a kind field.** There is a description per
kind of value — a number, text, a date, a series — so that what a kind needs belongs to it
and to nothing else: a dimension is on a number and on a series, a suggested vocabulary is
on text, and a date can no more be given a dimension than a boolean can be given a range.
That is a refinement of *value*, not a fourth thing beside reference and owned item.

**Being closed is one rule, written where it applies.** A `current` of six steps and a
`rating` from 1 to 10 make the same promise — a value outside is *unusable* and kept as
written, by `DATA-24`. That is what keeps a rating a number, with a number's dimension and
a number's ordering, rather than becoming a kind of its own.

But a closed *list* belongs to text and a *range* belongs to numbers, and neither means
anything on the other. So each kind carries its own — text has a fixed set, a number has a
range — rather than both sharing one thing that could be attached to either. One rule,
stated once here; two ways of writing it, each where it can be used and nowhere it cannot.

A range rather than a pair of bounds, so that a lower bound above an upper one cannot be
written. It costs the one-sided case, which nothing in the model has.

A *suggested* vocabulary is neither. It constrains nothing: a value outside it is an
ordinary value and always was. It sits beside the fixed set on text, which is where the
difference between them is easiest to state.

**Why a description rather than a class.** A declaration is the wrong place to keep this,
whatever the language offers. `Double? maxDepth` cannot say that the value is a length and
therefore obeys a `units` declaration, that it is derived with an override rather than
primary, that a string field is a reference to a dive site rather than free text, or that
it offers a suggested vocabulary rather than a fixed set. All of that would have to be
written alongside as annotations — at which point the annotations *are* the description,
and the class contributes nothing but a second place for the field list to live.

This argument was first written against Dart, which has no useful runtime reflection, and
it was tempting to read reflection as the reason. It was not. **The language changed and
the decision did not**: a language with full reflection and annotations still has to
declare every one of those properties somewhere, and the only question is whether that
somewhere is beside a field or instead of one. Reflection would have made a class *work*;
it would not have made it a better place for the field list.

Keeping it as data has a consequence worth having on its own: **a field the application
does not recognise survives a round trip.** A file written by a newer version is read,
held and written back with its unknown fields intact. Nothing has to be taught to
preserve them, which is what stops two installations at different versions quietly
eroding each other's data as they sync.

**What it costs.** Reading a field is not statically checked. Nothing is caught at
compile time, and renaming a field is a search rather than a refactoring the editor can
do. That is the price of the field list having one home.

It is not paid at full price, though. `DATA-51` settles that the logic layer reads
through an interface accepting only names the description carries, so a misspelt name is
reported as a fault in the code rather than becoming a silent absent — found on the first
run instead of never. The raw untyped mapping stays available beneath it for writing
back, which is the one job that has to reach fields the description has never heard of.

**On the name.** *Item* is the word everywhere — in prose, in the manual, and as the
class. *Record* would have been the natural choice and was used at first; it collided with
Dart's own `Record`, and by the time the language changed the whole project — glossary,
manual, fixtures, every document — spoke of items.

**The clash is history and the name still stays**, because the better reason was always
the second one. *Record* is this project's verb: a hundred and thirty uses of *record*,
*records* and *recorded*, against six hundred of *item*. And *recording* is already taken
— it is what a dive computer wrote, and the manual has a chapter section by that name. A
`Record` holding a `Recording` would be two unrelated ideas one letter apart. Carrying both
words — *record* in prose, `Item` in code — was tried and discarded for the same reason.

The name is also not quite free even now: `java.lang.Record` is in scope by default in
Kotlin's JVM and Android source sets, though not in common code.

**How it stays true.** [manual/data-fields.md](../manual/data-fields.md) is the
definition of every field, so a description and the manual can drift apart. They are
checked against each other rather than trusted: `tool/checkdata.py` already reads the
manual's field lists to validate the fixtures and the libraries, and the descriptions
become a third thing checked the same way — see `TEST-2` in
[testing.md](../testing.md).

### The set of items

Reading a logbook produces a set of **items**: everything loaded, from the logbook and
from every [library](libraries.md) it uses, with shadowing already applied so each
id resolves to exactly one of them.

**One id names one item across every type.** The namespace is not per type, because a
reference carries no type: `@anna_devries` has the id and nothing else to go on, so if a
person and a dive site could share one there would be no way to say which was meant. A
proposal is therefore checked against everything already loaded, not only against items of
its own kind, and a new person proposing an id a dive site already holds becomes
`#1` like any other clash.

The one legitimate second sighting is **shadowing**: the same id, of the same type, met
again after it has already been read — a logbook item before a supplied one, or a library
before a later library. What is read first wins and the second sighting is passed over, so
nothing already in the set is ever replaced. See [libraries.md](libraries.md).

**Within one source it is still a clash.** Two items of one id in one logbook, or in one
library, is the fault that `add` refuses. First-wins is about the order sources are read
in, not a licence to write an id twice in one place.

Every item holds access to the items it belongs to. That is how an item answers
questions it cannot answer alone — a region finds its `children` by asking which regions
name it as a parent — and it is where a reference such as `@anna_devries` becomes the
item it points at.

**Two things can be asked of a set of items:** resolve an id, and list
everything of a type. Nothing else, and what comes back is always a whole item of the
type asked for, constructed in full when the data is read. There are no summaries, no
projections and no half-built items — one kind of thing to ask for and one kind of
thing to get. A logbook is small enough to hold entirely, so
filtering, sorting and searching are done over ordinary collections rather than by the
set — `inOrder` is a function beside it and not a third question, `DATA-89` —
and items navigate themselves — a region asks for its children, a reference resolves,
a dive reaches its site. The interface stays small because there is nothing to add to
it, which is a better reason than restraint.

**Nothing is announced when an item changes.** There are no subscriptions and no
listeners here: whatever is showing something derived from an item asks again when it
may be out of date. At this scale that is cheap — rebuilding a list of a thousand dives
costs less than maintaining the machinery to avoid it — and it removes a whole class of
faults, since a view cannot go stale if it never cached anything, and a listener cannot
outlive what it was listening to.

It does not remove the need for *something* to say that a change happened. It removes
the need to say what.

More than one set can exist at a time. An import is read into its own, so that candidate
items are complete and resolvable before anything is merged, and the
[logic layer](../logic/doc.md) holds whichever are open.

#### Where the descriptions live

**The machinery is here; the types are not.** `Item`, `ItemDescription`,
`FieldDescription` and everything that acts on them — parsing, resolving `@`, applying
units to the right fields, proposing an id, checking that a journal action names a
real field — belong to this layer. The descriptions that say what a dive, a person or a
region *is* belong to the [logic layer](../logic/doc.md), and an `ItemSet` is
constructed with them.

The test is the one that layer already sets: can this be done without knowing the file is
about diving? Parsing a file against a description can. Writing the description cannot —
its whole content is diving. So the machinery stays and the instances go up.

That also settles where a derived value is computed. A description declares that a field
is derived and carries the computation with it, so `children`, `duration`, `buddy_count`
and `deco` alike sit with the type they belong to, rather than being split by how much
arithmetic each needs. This layer never computes a derived value; it holds the item
that knows how, and hands back whatever the description works out.

An earlier arrangement kept the types here, on the argument that too much of this layer
needs to know what a dive is. That argument was written when knowing meant *having a
class per type*. Once the description is data handed in at construction, the objection
goes with it.

**What this layer knows is the kinds of value the format has, not the fields any item
holds.** The two are easy to run together and are not the same. A `gas` is a kind — the
format writes one as `EAN32`, so something here reads and writes that, and `Gas` is a type
in this layer. That a dive has a `gas_type`, or that a gas source has one and a dive site
does not, is knowledge this layer never has and never needs.

So the line is not that nothing here has heard of diving. It is that nothing here names a
field, and nothing here would have to change to describe a different subject with the same
kinds of value.

Two things follow from items being connected rather than free-standing:

- **An item is not plain data in memory.** Writing one out must stop at its own
  fields and never follow its way back into the items around it.
- **Serialising and deriving are different operations.** What goes to disk is what was
  recorded; what an item can tell you includes everything it works out by asking
  around.

### An item does not own its id

A referenceable item's id lives outside it — in the file name, or in the key
it is stored under. Nothing inside the item records it. Changing an item's
id therefore does not touch the item at all: it changes where the item sits
and it changes everything that refers to it, and that is all.

**References are held as they are written.** A reference field holds the id, not
the item it names, and resolves on demand through the items the item belongs to.
Resolving at load into direct links was considered and rejected: it would make renaming
an id a silent in-memory operation, whereas `JSON-17` settles that a rename is one
changeset carrying every reference rewrite, so that a half-undone rename cannot arise.
Those rewrites have to be recorded whether or not the links would have survived without
them. Keeping the written form also means a reference to something deleted, which
permissive validation has to accept, survives a round trip unchanged instead of needing
a representation of its own.

The consequence is that an item cannot say what it is called. Nothing in this layer
needs to — writing a logbook iterates the items and takes each id from the key
it is stored under — but an interface offering "add this person as a buddy" holds an
item and must write a reference to it. So a set of items answers in both directions:
an id to an item, and an item to its id. Storing it on the item
instead would mean an item created but not yet accepted — an import candidate — either
carrying an id it does not have yet or having one written into it later.

**Nor does this layer invent one.** An id is the user's, arrived at with whatever help
the interface offers — proposing one from a person's name or a dive's date is an
assistance, not machinery, and it does not live here. An `ItemDescription` says nothing
about how a proposal is made, and `DATA-73` records why: a recipe per type in the
descriptions would be this layer knowing what a dive is.

An **id proposal** is a *base*, not a finished id. Whatever manages the collection resolves
clashes by appending `#<index>`, and that much *is* here, because only the collection can
see the neighbours.

**The clash is checked against the libraries too**, not only the logbook. A new dive site
whose proposal matches a supplied one becomes `blue_hole#1` rather than silently replacing
it — see `LIB-2` in [libraries.md](libraries.md). Shadowing a library item is then always
deliberate: you edit the supplied item, and the edited copy is written to the logbook.

**Nothing is required, and nothing is prevented.** An item read from a file already has an
id — its file name or its key. One created with too little to name it gets a proposal all
the same: whoever proposes falls back to the type's own name, so an unnamed person becomes
`unknown_person`, and a second becomes `unknown_person#1` like any other clash. No field
is mandatory, the data layer cannot prevent an empty item and should not try, and an
interface that wants to warn about one is welcome to. It is a safety net rather than a
path anybody takes: an interface that saves a filled form rather than a half-typed one
will not produce it.

**An id can be changed, but not by the item.** An item does not own its id, so it cannot
rename itself; the Universe can, and does it as one act — the id and every reference to it
together. That is why it sits there and nowhere lower: only something holding all the
items can find what points at one.

**What stays forbidden is reuse.** An index, once assigned, is never reissued and never
renumbered. Deleting `2026-02-23#0` does not renumber `2026-02-23#1`, and the freed index
is not handed to a later item — otherwise ids would silently start meaning something else,
and every reference to them would quietly change target. A deliberate rename does not do
that, because it carries the references with it; a reissued index does, because nothing
announces it.

**Whether the index is written down is a property of the item type.** For most
types a clash is rare, so index zero is left off and only a genuine second item
carries one: `anna_devries`, then `anna_devries#1`. Dives are the exception — several
dives in a day is ordinary rather than exceptional — and always carry their index,
starting at `#0`. This keeps the common case free of noise without letting an item
acquire an index retrospectively, which would mean renaming it.

**An index means nothing beyond telling two items apart**, and this is worth stating
because a dive invites the other reading. `2026-02-23#1` is not *the second dive of that
day*: it is a dive of that day which needed a distinguisher, assigned in the order dives
were created rather than the order they were made. Log the afternoon dive first and it
takes `#0`. Nothing derives an ordering from an index, and nothing should — a dive's
place in a day comes from its times, which is what they are for.

`#` is therefore structural in an id, and a proposed base may not contain one. Neither
may it contain `*`: a key reference is written `*key`, and the two markers compose as
`@<id>*<key>` for reaching into another item — a form nothing needs yet, but one that
stops parsing if an id may contain the character that separates its halves. See `JSON-19`
in [json/doc.md](json/doc.md). A name may contain `*` freely; only the id worked out from
it may not.

Assignment is hidden **in the graphical application, and nowhere else**. A user of
that application neither chooses nor sees an id; it is assigned when the item
is created and left alone thereafter. Editing ids may appear later as a feature
for advanced users.

In storage the id is fully visible, and deliberately so. It names the file and
appears in every reference inside every file, so anyone reading the logbook in a text
editor sees ids constantly — which is a goal of the format, not a leak. A
proposal must therefore produce something a person can recognise: `anna_devries`,
never `p_00417`.

### Referenceable and owned items

Two kinds of stored item:

- **Referenceable** — has its own id, can be referred to from elsewhere, and is
  stored in its own right: dives, buddies, dive sites, gear items.
- **Owned** — exists only inside one referenceable item, is never referred to from
  outside it, and is created and destroyed with its owner: a dive profile, a cylinder
  entry on a dive, a service item on a gear item.

An owned item in a collection sits under a **key**: an identifier, but a local one. It is
unique within the collection it belongs to and means nothing outside its owner. **It is
where the entry sits, not a field the entry carries** — the same rule as an item and its
id, applied one level down, and the reason a collection is written as a keyed object
rather than a list.

Two things need it. The journal has to be able to say which of three courses changed, and
counting from the top stops being true the moment anything is reordered. And the owner
itself has to be able to point at one of them, which is how a dive with several profiles
says which one to work from.

Keying by construction settles what would otherwise be rules to enforce: two entries
cannot share a key in a file that parses, and no entry can lack one. What remains a rule
is that a key must not be *reused* once its entry is gone, which no format can prevent.

**A collection has no inherent order.** Where an order matters it is worked out from the
contents — courses by date, services by date, gas sources by when they were first
breathed, which comes from the profile rather than from the entry. Which fields give the
order is a property of the type. Two consequences follow: nothing may lean on the order
entries happen to sit in, and a writer must nevertheless emit them in a settled order, or
every save produces a diff for a file that did not change.

The distinction to hold on to is scope. An **id** names an item in the whole
logbook, is written with `@`, and any item may use it. A **key** names one entry inside
one owner, is not written with `@`, and is meaningless the moment it leaves. Both are
identifiers; only one of them is an address.

A singular owned item has no key and needs none: its field name is its address, so
`environment.current` reaches inside one exactly as `courses.k1.date` reaches into a
collection. A key tells siblings apart, and a singular has no siblings.

An owned item may appear singly as well as in a collection. Most are collections — a
person's courses, a gear item's service history — but one of a kind is equally valid, and a
singular one is how a set of related fields is grouped.

One of a list may be **primary**. A dive can carry several profiles, and the primary one
is what its times, duration, depth and temperatures are derived from. The dive says which
by naming its key — not by a flag on the profile, which could end up set on two of them
at once, and not by position, which changes.

An owned item also knows its **parent**. That link is not stored — it would be
circular on disk and says nothing the file structure does not already — and is set
when the item is created. Like an item's access to the items around it, this makes
it connected rather than free-standing, and writing an item out must not follow it.

### Validity and renewal

Two things in the model expire: an insurance policy and the maintenance of a piece of
gear.

A medical examination does not. Whether a year-old check still counts depends on who is
asking — an agency, an operator, a country — so validity is a rule applied to the
examination from outside, not a property of it. Recording an expiry against the check
itself would be asserting something the check does not say.

These are unified in **how they are implemented, not in what they store.** Both happen
to state an end date outright — a policy because that is what the document says, a
maintenance item because the shop tells you when the item is next due — but neither
is obliged to, and a third expiring thing need not follow suit. What matters is that
each reads like the thing it describes.

What is common is the behaviour. Both answer the same questions — when does this run
out, how long is left, has it lapsed — and the same code answers them. Those values are
the `DATA-29` kind: they depend on today, are never stored, and two installations may
legitimately show different numbers for the same file.

**Extending is not replacing.** A policy that is renewed is still that policy, so its
dates move and it stays one item; only one is held at a time. Maintenance is the
opposite: each occasion is an event that happened on a day, a later one does not amend
it, and every one is kept.

**Maintenance is one sequence, not several.** The obligation is that maintenance happens
at all — an item falls due again once the last work's validity runs out, whatever was
done that time. So the latest item is the item's status, whatever kind of work it
was, and the history behind it stays for reference.

**What comes next is recorded, not derived.** Each item names the work that should
follow it. An alternating routine therefore needs no schedule and no rule: an
inspection that turned into a repair simply says that an inspection is due next, and the
alternation re-bases itself. A schedule held against the item would instead be a rule
that reality departs from — a year skipped, work brought forward, an inspection that
became something else — and it would need reconciling against the items for as long as
the item existed.

### Shape carries the hierarchy

An item has levels, and they follow from where a field sits rather than from a
separate marking kept alongside it:

- **`name`.** Every referenceable item has a field called exactly that — mandatory
  on some types, derived and overrideable on others, as a person's is from the parts of
  their name. It titles the item, it is what a link to it reads as, and it is what
  the id is proposed from.

  **`name` is not the id.** The name lives in the item and is meant to be read
  and corrected; the id lives in the file name or key, is what references point
  at, and is fixed once assigned. An id is proposed from a name at creation and
  the two are free to diverge afterwards — see *An item does not own its id*
  below. Keeping the two words apart matters, because almost every mistake in this area
  starts by conflating them.
- **Fields directly on the item** are its main fields: what the item is, at a
  glance.
- **A singular owned item** groups detail. A person's health details sit together
  because they are one item, not because something labelled them a group.
- **Keyed collections of owned items** are lower still: a person's courses, a gear
  item's service history.

This is a statement about **structure, not about presentation**. Nesting says that
fields belong together and that a group is subordinate to the item holding it. It
does not say how large anything should be drawn, or whether it is shown at all.

Interfaces are expected to read the structure — it is there precisely so they have
something to read — but they are not bound by it. What appears in a view, and how
prominently, is decided by the interface, per item type. The data layer supplies the
shape and no more; see [../ui/gui/doc.md](../ui/gui/doc.md).

The gain is that there is nothing to keep in step. The hierarchy cannot disagree with
where a field is stored, because it *is* where the field is stored, and someone reading
the file by hand sees the same structure an application works from.

The cost is worth stating plainly: **regrouping a field changes the file.** Moving a
main field into a group is a change of stored shape, not of an annotation, so it moves
data and affects anything already written by hand. That is a reason to be deliberate
about the grouping early, not a reason to avoid the model.

What follows from the distinction:

- Only referenceable items need stable identifiers.
- Deleting a referenceable item can leave references to it elsewhere; deleting an
  owner simply destroys what it owns.
- In [reconciliation](../logic/reconciliation.md), an owned item is matched and
  resolved as part of its owner, never on its own. A referenceable item is matched
  in its own right.

## Depends on

Nothing above it. This is the bottom of the stack, and it must stay free of any
dependency on logic, UI, or a specific source.

**One library**, and it is named in one file. Common Kotlin has no file access at all, so
reading a logbook needs something outside; `DATA-86` chose Okio and put it behind a small
interface, so `json/DiskFileStore` is the only place that knows which library it is. Both
live with the JSON source rather than at the root, because what they answer is shaped by
that format: `getPaths` knows a type is a `.json` file or a folder of them, which is the
convention `JSON-21` settles and not something every source would share.

## Structure

```
data/
  doc.md          this file — the item model, items, id, validation
  build.gradle.kts  the module
  libraries.md    reference data shipped with the application
  json/           the JSON file source (doc.md, requirements.md)
  src/commonMain/kotlin/yemoja/data/
                  Description.kt   what a type is, and what a field is
                  Gas.kt           a breathing mix, in whole percentages
                  Item.kt          one item of one type, referenceable or owned
                  ItemReader.kt    a description and a tree, walked into an item
                  ItemSet.kt       everything loaded, by id and by type
                  Moment.kt        a day, a time of day, and the two together
                  Reference.kt     naming another item, and what it points at
                  Result.kt        what reading a field gave, one member of a collection,
                                   and what a parser throws
                  Series.kt        values against time, and one sample of them
                  Stored.kt        what a source holds, before anything judges it
                  Units.kt         what the numbers of one file are written in
                  json/DiskFileStore.kt  the machine's own files, the one place Okio
                                   is named
                  json/FileStore.kt      which files hold a type, and one store in memory
                  json/Json.kt     a JSON text, read into that
                  json/LogbookReader.kt  a folder of them, read into a set of items
  src/commonTest/kotlin/yemoja/data/
                  the tests, beside what they cover
```

This layer is a module of its own, and its tests sit inside it rather than in a tree of
their own — see [testing.md](../testing.md).

## Open questions

To settle when we discuss architecture:

- **DATA-81 — Whether a profile is read with its dive.** A logbook is held entirely in
  memory, and this asks whether the profiles are part of that or are fetched when
  something asks for one. It is the only field where the answer could matter.

  `DATA-5` sharpens this. Entities are constructed in full when read, so whatever sits
  inside a dive is in memory for every dive at once. A profile is the only field large
  enough for that to matter: a thousand dives with a few thousand samples each is
  comfortably the largest thing in the application, and a phone is where it would be
  felt. Either profiles are not read with their dives, or the cost is accepted and
  measured.

  The language change made this sharper rather than softer. A runtime with a heap and
  object headers costs more per sample than the one this was first weighed against, and
  Android is now the target that matters most rather than one of five — so the phone is
  both where it hurts and where it is least acceptable to hurt.

  An argument for the small answer: most navigation happens *through items* rather
  than through queries. A region asks for its children, a reference resolves itself, a
  dive reaches its site. If that holds, `ItemSet` needs little beyond resolving an
  id and listing a type, and the interface stays small by consequence rather than
  by discipline.
- **DATA-54 — Which fields the common interchange formats carry that this model does
  not.** *In progress:* **UDDF is done.** [../logic/uddf.md](../logic/uddf.md) compares
  every section of the logbook against 3.2.3, and what remains is other formats — which
  ones is `RECON-5`, so this cannot close before that does.

  `DATA-45` was decided by looking at one element of one format and finding the
  model would have lost data on import. That was luck rather than method. The formats
  worth importing — `RECON-5` in
  [../logic/reconciliation.md](../logic/reconciliation.md) names the question — should be
  read through field by field, and each of their fields placed in one of three piles:
  modelled already, deliberately not modelled, or a gap to close before an importer
  exists. The middle pile matters as much as the last, since a field consciously declined
  is not a bug and should not be rediscovered every time somebody reads a specification.

  The middle pile now has entries: `workload`, `problems` and `equipmentmalfunction` are
  declined because `remarks` already takes what a user would write, and imported values
  are folded there rather than dropped. Markers a user sets on the computer mid-dive —
  UDDF's
  `setmarker` — are deliberately not modelled: a marker says something was interesting and
  nothing about what, which the dive's remarks do better. Rebreather data goes with
  `FEAT-21`. And UDDF's site-level `environment` — `ocean-sea`, `cave-cavern`,
  `under-ice` and the rest — describes the kind of place and is a different axis from
  `water_type`, which is about density; an importer cannot map one onto the other and
  should leave `water_type` unset rather than infer `salt` from `ocean-sea`. And UDDF has
  no wave element at all, so `waves` will always arrive empty from it.
- **DATA-55 — Whether what this model records can be written back out.** The companion
  to `DATA-54`, which asks what the interchange formats carry that this model does not.
  This asks the reverse: for every field both model, can ours be expressed in theirs
  without loss?

  The bad case is not a field they lack — that is merely absent from an export. It is
  **both modelling the same thing incompatibly**, because that cannot be repaired later
  by either side. A current recorded on an eight-step scale has no honest home in UDDF's
  six: exporting collapses steps, importing the result back gives a different dive, and
  every round trip degrades. Nothing in a converter can fix it; only the choice of
  representation can.

  Which is why this is a constraint while fields are still being defined, not a task for
  when an exporter is written. **Where both model the same thing, match their
  representation unless there is a reason not to.** `current` and `waves` follow it
  already — `DATA-45` took UDDF's six steps rather than inventing a scale, so the mapping
  is one-to-one in both directions.

  It does not wait on deciding to export at all — `RECON-4` and `FEAT-13` — since an
  incompatible representation is a permanent property of the model whether or not
  anything ever writes the file.

  It applies only to **actively supported** formats, and only to the version of them that
  is written: a best-effort format gets no say in how a field is shaped. See *Two levels
  of support* in [../logic/reconciliation.md](../logic/reconciliation.md), which also
  notes that UDDF's own versions differ enough for "match their representation" to need a
  version named.
- **DATA-57 — Whether a dive plan is an item, and what it holds.** A plan keeps the
  inputs it was made with — gases, depths, times, and the gradient factors fixed at the
  moment it was made — so that changing a preference later does not silently rewrite it.
  Nothing in the model holds those. There are nine item types and none of them is a plan.

  Open: whether a plan is a tenth type, or something owned by a dive, or not stored at all
  and merely printed. What settles it is whether a plan outlives the screen it was made
  on — a plan you keep to compare against what you actually did is an item; a plan you
  read off and forget is not. `FEAT-6` is *Planned* rather than *Core*, so nothing waits
  on this, but the gradient-factor defaults in `manual/settings.md` already assume a plan
  remembers its own.
## Settled and relocated

- **DATA-86 — What supplies files, and where it lands.** *Settled:* **Okio, behind a
  four-method interface**, so the library is named in one file and nothing above it knows
  which library it is.

  Common Kotlin has no files. Something outside has to supply them, and unlike a date — which
  `DATA-74` declined a library for and wrote instead — this cannot be written here at all.

  **Okio over kotlinx-io**, which was the close call. `kotlinx-io` is smaller and is Kotlin's
  own, but it is pre-1.0 and its shape can still move, which is the wrong thing to absorb in
  the layer that holds a user's logbook. The size between them is a couple of hundred
  kilobytes before shrinking, against an API that has carried other people's work for years.
  They are the same design either way: `kotlinx-io` is an adaptation of Okio's. If it settles,
  the interface is what makes changing cheap.

  **Writing it per platform** was the third option. It needs five implementations of
  something one library already does, on toolchains this machine does not have, and each
  platform's file rules differ in ways that are found late.

  **Four operations, because reading needs four**: whether a path is a file, whether it is a
  folder, what is directly inside a folder, and the whole of a file as text. `JSON-21` has to
  tell a `dive` folder from a `dive.json` file, and the files are small enough to read whole.
  Writing widens this when there is a writer; guessing at it now would be designing against
  no implementation.

  **A store spans two places**, the logbook's folder and whatever holds the supplied
  libraries. They are apart because a logbook contains itself and may be copied, while
  libraries belong to the application. The four operations run over one namespace spanning
  both: a path under `libraries` resolves against the supplied set and any other against the
  logbook. That is what lets a path be handed back to the store that gave it, and `libraries`
  cannot collide, because a type is stored under its own name and no type is called that.

  Where the supplied set is, is `LIB-6`: inside the application, read from its class path,
  because there is no reliable way to ask a JVM where it was installed and on a phone no such
  place exists.

  **Asking for a type gives the files that hold it, in reading order**: each file in the
  logbook's `dive` folder, or its single `dive.json`, and then the libraries declared for that
  type. That order is where shadowing is applied — the first to define an id wins, so a
  logbook item is met before a supplied one and an earlier library before a later. It is
  written once against the four operations rather than by each platform.

  **Which libraries those are is given to the store, not found by it.** They are declared in
  `yemoja.json` and the reader starts there, so the store parses no format and cannot fail to
  be built by a manifest that will not read. A library sitting in the directory that the
  logbook does not name is left alone. A named one the installation has not got is passed over
  rather than refused: a logbook carried to a device with an older installation names libraries
  that device may not have, and refusing to open it would be a harsh answer to a missing list
  of regions. The references into it go unresolved, which is a state the model already
  carries.

  **The interface earns itself twice over in tests.** A second implementation holds the files
  in memory, so nothing above needs a disk, and it needs no fake-file-system library to do it.
  Both are put to the same questions by the same tests, so the one standing in for a logbook
  cannot drift from the one that is a logbook.

  **A path is relative and written with `/`.** The store stands for the logbook's folder, so
  nothing above it handles a separator, a drive letter or a home directory.

  Still open, and a requirement rather than a library question: `requirements.md` says nothing
  about crash-safety — no atomic write, no temporary file, no partial write. That is what
  decides whether the writing half needs a rename as well as a write.

- **DATA-85 — How an owned item is built, when neither it nor its owner can exist first.**
  *Settled:* **the owner builds it**, by handing itself to whatever builds its fields.

  An owned item takes its parent at construction, and the parent's fields are what hold the
  owned item, so there is no order in which both can be whole. The same knot appeared with
  an item set and was cut by letting the set be filled afterwards, which is why it was worth
  refusing the same answer twice.

  An item's fields are therefore built by a function the constructor passes itself to. Both
  links stay fixed, no half-built owned item is ever visible, and construction is finished
  when the constructor returns, which is what `DATA-5` asks for. An item whose fields hold no
  owned item is built from a plain mapping and never sees the difference.

  **The rule that makes it safe: nothing outside the item is read while it is being built.**
  Not the item set, not a parent's fields, not another item. All of those are still being
  assembled, and a property read in that window has not been given its value yet — an owned
  item asking for the set during construction would walk up to an owner whose set is not
  there. Storing a reference is fine; following one is not.

  Two alternatives were refused. **Filling the owner's fields afterwards** would make the
  field mapping something that changes, and everything that reads a field rests on its not
  changing. **Giving an owned item its parent afterwards** leaves a window in which one
  exists without an owner, and nothing stops it being used in that state.

- **DATA-84 — What an id may contain.** *Settled:* **lowercase ASCII letters, digits, `_`,
  `-` and `.`**, with `#` reserved as the index separator and forbidden in a base. Not empty,
  and not starting or ending with `_`, `-` or `.`.

  All 523 ids in the fixtures and the libraries already fit, including
  `generic_0.5_kg_lead_weight`, whose dot carries a decimal, and `2026-06-21#0`, whose
  hyphens carry a date.

  **This is a rule about file names, which is why it is narrow.** An id names a file on five
  platforms, and they disagree in three ways that each break *one id names one item*:

  - Windows and macOS are case-insensitive by default, so `Anna` and `anna` would be one file
    and two ids. Lowercase-only removes it, and lowercase-only is a clean rule in ASCII and a
    locale-dependent one in Unicode: Turkish dotted and dotless i do not fold the way every
    other locale expects.
  - macOS stores file names decomposed and Linux stores what it is given, so the same Unicode
    id would be different bytes on disk depending on the machine, and a synced logbook would
    see two files.
  - Cyrillic and Latin have letters that look identical. Permissive reading means a
    confusable pair resolves to two items with no visible difference between them.

  **The cost falls where the proposal is made, above this layer.** Someone whose name is
  written in Greek, Japanese or Arabic gets an id that is a transliteration or a fallback like
  `unknown_person#1`. The `name` field keeps the real spelling, the two are free to diverge,
  and a user of the graphical application never sees an id at all.

  **Excluded regardless of the character set**, for the same file-name reason: a leading dot,
  which hides the file on Unix; a trailing dot or space, which Windows strips, turning two ids
  into one file; and the reserved device names `con`, `prn`, `aux`, `nul`, `com1` to `com9`
  and `lpt1` to `lpt9`, since `nul.json` cannot be created on Windows at all. A length cap
  around 200 characters follows from the id plus `.json` having to fit.

  **Reading is not held to this, and does not need to be.** An id arrives either as a file
  name, which the file system has already proved legal, or inside a reference — where it
  resolves to an item that came from a file, or dangles and never becomes a path at all. There
  is no case where reading has to check that an id could be a file name. The rule binds when an
  id is minted and when an item is written to a file of its own, and nowhere else.

  So a reference is validated permissively: a hand-written `@Andre` with an accent resolves
  and is reported rather than refused. Reading refuses only what stops the format parsing:
  whitespace, and `*`.

  **What `DATA-83` refuses in text is deliberately not refused here.** An invisible or
  confusable character in an id makes a reference that looks right and dangles, which is a
  confusing failure but a visible one — the interface says it points at nothing. In a `name`
  the same character gives two people who look identically named with no signal at all, which
  is why it is worth the code there and not here.

- **DATA-83 — What text may contain.** *Settled:* **nothing a reader would not see, and
  nothing that reorders what is shown.**

  The manual already promised *other invisible control characters* while the code refused
  three of them, so this makes good on a promise rather than adding a rule. Refused now: every
  Unicode control, so escape and backspace and the block that arrives from mis-decoded
  Windows-1252 as well as tab and the line breaks; every line separator, including the two
  that are neither carriage return nor newline and that a *single line of text* was quietly
  accepting; the nine bidirectional controls, which display text in an order other than the
  one it is stored in; and the zero-width space, word joiner and byte order mark, which make
  two names look identical while differing.

  **The zero-width joiner and non-joiner are kept.** An emoji sequence is joiner-built, and
  Persian and Indic writing needs the non-joiner. Refusing the whole of the format category
  would have been three lines shorter and would have turned away text a diver might type.

  Prose is the same rule with the newline excepted, so it refuses a carriage return too and
  the format has one line ending rather than two.

  It matters most in a `name`, because an id is proposed from one: two names that look alike
  and differ would become two people. `DATA-84` closes the other half by keeping the id itself
  to ASCII.

- **DATA-82 — Whether a proportion is written as a fraction or as a percentage.**
  *Settled:* **a fraction, from 0 to 1.** A name containing *percentage* is the exception,
  and so is a term of art already defined as one.

  The model had both and nothing said which. `compressible_fraction` held `0.29`, the
  gradient factors held `0.2`, and `cns` held `44` — three proportions on two scales, told
  apart only by prose in the manual.

  **`cns` keeps its name and its scale.** The CNS oxygen clock is the share of the
  single-exposure limit used up at the partial pressure breathed, and it is quoted against
  100 in the published tables, in training, and on every computer that shows it. That 100
  is the threshold the whole idea is stated against, so a fraction would match nothing a
  diver reads, and `cns_percentage` would put a scale word on a term of art that carries
  its scale already.

  **A description does not say which.** `DATA-68` leaves a proportion dimensionless either
  way, and a range cannot help, since a CNS clock passes 100 on purpose. So this is a rule
  about how a field is written and read rather than something a program asks. A front end
  needing it in code would want a property beside `range` on the number description; that
  is not written, because nothing needs it yet.

  `DATA-55` is why it could not wait. UDDF states gas fractions from 0 to 1, so an exporter
  converts, and matching a format's representation is a constraint while fields are being
  defined rather than a task for when an exporter is written.

- **DATA-79 — Whether a referenceable item and an owned item are one class or two.**
  *Settled:* **two, under a sealed `Item`.**

  Everything that differs between them is outside the item. A referenceable one is named
  by the file it sits in, an owned one by the key it sits under or by nothing at all, and
  an owned one has a parent. So one class with a nullable parent was the obvious
  alternative, and it would have expressed none of that.

  What the split buys is that the base can declare the link to the set and each kind
  answers it differently: a referenceable item holds one, an owned item answers its
  parent's. The set is then recorded in one place, and an owned item moved between owners
  cannot keep a stale one — which would have shown up as a reference resolving against the
  wrong logbook, quietly.

  It also lets a resolved reference name the referenceable kind, since resolution goes
  through the set and the set holds nothing else.

- **DATA-80 — What an item's field map holds.** *Settled:* **a result per stored field,
  and nothing else.**

  Only stored fields. A derived one is worked out on every read, because nothing announces
  that an item changed and a cached answer would be the thing that goes stale. `DATA-6`.

  **Absent is never a value in it**, because a field that is absent is not a key. That is a
  stated rule rather than one the type enforces, in the same spirit as immutability being
  declared and then honoured.

  `Element` was weighed for it and refused. `Element` exists for members inside a list or a
  keyed collection, where absence cannot arise and repeating the field's origin once per
  sample would cost something on a profile. At field scale neither applies — an item has
  twenty fields, not three thousand samples — and what reading a field gives is what a
  result is for.

  The map stays wider than the description: a field this version does not recognise lives
  here and nowhere else, which is what lets it survive a round trip. `DATA-51`.

- **DATA-76 — When a stored value is parsed.** *Settled:* **at construction.** An item is
  built with its fields already read, and reading one hands back what is there rather than
  parsing it again.

  Reads dominate. Everything an interface does is a read, and a profile is read again on
  every redraw, so the work belongs at the one place that happens once. `DATA-5` already
  says items are constructed in full; this is the same answer one level down.

  The cost is that writing back goes through the formatter rather than through the text
  that was read, so a hand-written `eanx32` returns as `EAN32`. That is a single
  normalising diff on a file nobody edited, and it makes formatting the true inverse of
  parsing, which is a property a test can hold us to rather than a promise.

  A field the description does not name is not parsed at all. It is held as it came and
  written back untouched, which is what stops two installations at different versions
  eroding each other's data.

- **DATA-77 — Where the three states sit on a field holding more than one value.**
  *Settled:* **`Result` on the field, `Element` on each member.** A field is absent, or
  usable with an origin, or unusable because what was stored is not a collection at all. A
  member is a value, or something that is not one.

  A result inside a result would carry a state that cannot arise. A member of a list is
  there, so it is never absent, and every caller would write a branch nothing reaches. It
  would also repeat the field's origin on every member, which is one reference per sample
  across the profiles that are the largest thing in the application.

  **A single value where a list belongs reads as a list of one.** `"buddies": "@anna_devries"`
  is the list with one name in it. It is a forgiveness for someone editing by hand, in the
  spirit of the ones the JSON reader already offers, and `DATA-76` writes it back as a list on
  the next save, so nothing stays in the odd shape. No file in the fixtures or the libraries
  uses it — thirteen list fields and every one of them written as a list — so it costs nothing
  today and exists for the hand a user has not yet made.

  It goes one way only. A list where a single value belongs is unusable, since there is no
  answer to which of two values was meant, and a keyed collection has no equivalent
  forgiveness: a name is not optional the way a bracket is.

  **One bad member does not spoil the field.** A misspelt buddy leaves the other three
  readable, and `DATA-24` keeps the bad one visible in place with what the source held. This was
  weighed for series too, where the samples are read as one curve, and rejected there for
  a concrete reason: `alarms` is closed to nine words, so one unrecognised word from a
  newer version would take out the whole series, including the `link` alarm that `DATA-58`
  leans on to explain a gap in the depth curve.

- **DATA-78 — Whether a series is a collection or a value.** *Settled:* **a value, with a
  type of its own.** `Series` holds the times in one array and the values beside them, and
  a field holding one is a result over a series rather than over a list of pairs.

  Where the time axis lives was the half that stayed open. A value description reads one
  value and cannot see the time beside it, so nothing owned the pairing. A series does,
  being the only thing that holds both halves.

  Pairs were the alternative and cost three objects per sample: the pair, a boxed time and
  the member. `DATA-5` notes that a thousand dives of a few thousand samples each is
  comfortably the largest thing in the application, and that Android is where it would be
  felt. That decides it.

  Samples run strictly forwards, and a file that breaks it makes the field unusable rather
  than throwing where a user would see it. An empty series is allowed. Negative seconds
  are not forbidden, since nothing says a sample cannot precede the start.

- **DATA-74 — Where a date and a time come from.** *Settled:* **ours, and no dependency.**
  `Date` and `Time` are written in this layer rather than taken from `kotlinx-datetime`.

  A library would be the obvious choice and brings more than a date. `LocalDate` and
  `LocalTime` are exactly these two, and their arithmetic is proven by everyone else using
  it — where ours is proven only by us. That is the real cost, and it is not nothing.

  Against it: this layer had no dependencies at the time, and the first one is the expensive
  one. `DATA-86` has since taken one, for files, which common Kotlin does not have at all —
  where a date and a time can be written and were.

  What we need is narrow — a calendar date, a time of day, and the number of days or
  seconds between two of them — where the library's reach is time zones, instants and a
  copy of the IANA database that goes stale on a phone. `DATA-41` already declined the
  zones. Taking the package for the two small types would be taking the rest as well.

  **The arithmetic was checked rather than believed.** Both directions of the day count
  were run against a known-good implementation across two and a half thousand years, and
  the leap-year rule against the centuries that catch people out — 1900 is not a leap year
  and 2000 is. That is the answer to "proven only by us": not a promise, a comparison.

  If a date ever needs a zone, an instant or a locale, this decision is wrong and the
  library is the answer. None of those is in the model.

- **DATA-75 — A moment, which is not a field.** *Settled:* a `Moment` pairs a `Date` with
  a `Time` **for arithmetic only, and is never stored**.

  A start is three fields — a date, a time and a `gmt_offset` — and they are not
  independent. Correcting the time alone can push it out of the day, which means the date
  was wrong too: two minutes past midnight less two hours is late the evening before. So
  the correction is an operation on a moment, not on a time.

  That argues for a moment in the *computation* and not in the *format*. Storing one
  combined field would make a date and a time a special case among the value kinds, and
  would stop a user correcting either by hand — which is why UDDF's single `datetime` is
  split on the way in. See [../logic/uddf.md](../logic/uddf.md).

  Three derivations carry a day and should not each say so: correcting a recording by its
  offset, an `end_date` from an end time earlier than the start, and the interval between
  two dives. The second of those was a written rule before it was a type.

- **DATA-66 — Which layer judges a value against its description.** *Settled:*
  **structural checks stay here, on the description, and domain rules stay above.** A
  description answers `validate`, returning valid or a reason a user can read.

  The interface decided it. A user typing into a form has to be told before saving that
  `brackish` is not one of the three water types, and the only thing that knows the three
  is the description. Putting the check above would mean the logic layer reaching down for
  a vocabulary the description already holds, or holding a second copy of it.

  **It is not the same question as reading**, which is why it is not the same type.
  `DATA-50`'s `Unusable` answers *what is in the file and why it cannot be used*, and
  carries what the source held so an interface can show it. Validity answers *does this value
  belong in this field* about something that may never have been in a file at all — typed
  into a form, or about to be written. There is no raw to keep, and a value that reaches
  validation is already one.

  For text entry a front end wants both at once — a value that will not read and a value out
  of range are the same kind of news to a user — so the per-kind reader of `DATA-64` does the
  reading and calls this for the rest. One implementation, used when a file is read and when
  a form is. A form handing over a value it has already made goes through the same door and
  is judged the same way: taking a value as it comes is not taking it on trust.

  **What it deliberately cannot judge is a reference.** Whether `@blue_hole` resolves, and
  whether `*p1` names an entry that exists, are questions about the items and not about the
  value; a description holds no items and should not. Those are checked where the items
  are, and `validate` is honest about answering less than the whole of structural validity.

  Nothing is refused by any of this. Validation informs an interface and produces
  `unusable` on the way back in; it never stops a file being written or read — `DATA-24`.

- **DATA-73 — Whether a description says what an id is proposed from, and what becomes of
  `required`.** *Settled:* **it does not, and `required` goes.**

  An id is the user's. Proposing one from a person's name or a dive's date is help the
  interface offers, not machinery this layer owns — so an `ItemDescription` says nothing
  about it, and no recipe per type appears in the descriptions. A recipe there would be
  this layer knowing what a dive is.

  **`required` was never about requirement.** Six fields carried the word and five gave the
  same reason beside it — *the item's id is worked out from it* — with a person's
  `first_name` the same one step removed. Nothing is refused on write, so it could only
  have meant *the proposal needs this*. The marking was inconsistent too, which is what
  gave it away: a dive site's `name` and a region's `name` say the id comes from them and
  were never marked.

  **Missing inputs are handled rather than prevented.** Whoever proposes falls back to the
  type's own name, so an unnamed person is `unknown_person` and a second is
  `unknown_person#1` by the ordinary clash rule. Nothing has to be present, nothing is
  refused, and an interface may warn. The cost is that an id is not rewritten when the name
  arrives — but ids can now be changed at the Universe, so that is a repair rather than a
  scar.

- **DATA-72 — Heart rate.** *Settled:* **dropped.** The field is gone from the profile,
  from the fixture and from the UDDF mapping.

  It was the last quantity in the model whose unit could not be declared. Beats per minute
  is a rate, there is no rate in `DATA-8`'s dimensions, and adding one for a single field
  would have bought an exemption of its own — the fourth after dates, gas and whole
  numbers, in a scheme whose worth is that it has few.

  Nothing else argued for keeping it. No feature reads it, decompression here does not use
  it, and only some computers record it. A logbook that showed it could do nothing with it
  but draw it.

  What is given up is real: a user whose computer records a pulse loses it on import and
  cannot export it. That is written where a user will meet it, in
  [../manual/uddf.md](../manual/uddf.md), rather than left to be discovered.

- **DATA-71 — Whether a pressure is gauge or absolute.** *Settled:* **a cylinder's is
  gauge, the atmosphere's is absolute, and anything computed uses absolute.**

  A cylinder pressure is what the needle showed — zero for an empty cylinder at the
  surface — because that is what a user reads and writes, and because this model keeps
  what was observed rather than a converted form. `atmospheric_pressure` is not a reading
  against anything; it is the pressure itself, and is absolute. `pressures` in a profile
  follows the cylinder.

  Anything that calculates adds the atmosphere at the point of use, which the dive already
  records. The distinction is negligible for gas in a cylinder — four parts in a thousand
  — and is the whole of the calculation when turning a depth back into a pressure.

  **It went unstated for a long time and was not harmless.** `DATA-70`'s compression law
  divides the gas part by the pressure in bar; read as gauge that is a division by zero at
  the surface, and read as absolute it is correct. Two fields with two conventions and
  nothing saying so is the kind of thing that produces an answer rather than an error.

  Three smaller gaps went with it, all of them missing words rather than decisions: `cns`
  is a percentage that runs past 100, `otu` is a count on its own scale — they were one
  line describing two different measures. A third, `heart_rate`, was answered by dropping
  the field — see `DATA-72`.

- **DATA-70 — What `compressibility` means.** *Settled:* it is renamed
  **`compressible_fraction`** and defined as *how much of `displaced_volume` is gas rather
  than solid*, from 0 to 1. What an item displaces at pressure `p` bar is
  `solid + gas / p`.

  The old field carried one clause — "how much that volume is squeezed by pressure" — with
  no unit, no range and no formula, while nine values already sat in the shipped library.
  Two implementers would have read it differently and neither could have been shown wrong.

  **The physics chose the shape.** A wetsuit is sealed gas cells in rubber; pressure
  squeezes the gas and leaves the rubber, so an item is mixed and one number says where
  between the two it sits. That makes it a ratio of two volumes — dimensionless, needing no
  unit and no exemption from unit scoping, which the alternative *fraction lost per bar*
  would have needed, being an inverse pressure.

  **It is an effective figure, not a measured one.** Real neoprene is 60 to 80 percent gas,
  but the cell walls carry load and the bubbles do not squeeze as freely as loose gas. What
  belongs in the field is the fraction that *behaves* as gas — fitted to the buoyancy a
  user actually loses, and so checkable by one.

  **`lift_volume` is not governed by it.** Gas the user adds is whatever they have added;
  a wing at thirty metres holds what was put there, not a quarter of it. Only
  `displaced_volume` is squeezed and only its gas part. A drysuit is where confusing the
  two would be invisible in the arithmetic and wrong in the water — its suit compresses,
  and its inflation is the user answering that.

  The name changed because "compressibility" in physics is a coefficient with units of
  inverse pressure, which is exactly what this stopped being.

- **DATA-69 — What a series carries.** *Settled:* **whatever a value may be.** A series
  field describes its value axis with a value description of its own, rather than with a
  dimension.

  The shape it replaced assumed a series is numbers, and two of the model's series are not:
  `alarms` carries text closed to nine words, `gas_switches` carries a key reference into
  `gas_sources`. Neither has a dimension and neither is a measurement, so a description
  built around one could not describe them at all.

  Describing the axis instead makes the rest fall out. The nine alarm values are a
  `Constraint.OneOf` like any other closed list, rather than something a series has to know
  about specially. A depth series is a series *of* a length. And a **keyed series** —
  `pressures`, one per gas source — is a series with `KEYED` cardinality rather than a kind
  of its own, which is the second thing `DATA-67` collapsed now falling out for free.

  The time axis is not described, because there is nothing to say about it: always seconds,
  never scoped by a `units` declaration, on every series there is or will be.

- **DATA-68 — Whether a whole number can have a dimension.** *Settled:* **no. It never
  does, and the description says so by being a kind of its own.**

  Every whole number in the model answers *how many*: `dive_number` is an ordinal,
  `buddy_count` is a count, `rating` is a bare 1 to 10, and `days_left` is a count of days.
  A measurement answers *how much*, and only a measurement has a unit to be written in. So
  a whole number carries no dimension, takes no `units` declaration, and a description that
  offered it one would be offering nonsense.

  **The rule runs one way only.** Every whole number is dimensionless; not every other
  number is dimensioned. `compressible_fraction` is a ratio, and `cns` and `otu` are a
  percentage and an accumulated count — numbers that are neither counts nor measurements,
  so a dimension stays optional.

  **`days_left` is the one that had to be decided rather than observed.** It looks like a
  duration, and a duration has a dimension. It is a *count of days*: derived for reading
  rather than measured, and answering how many days rather than how long. That settles it
  as dimensionless with the rest.

  Two things follow. `d` is not added to `DATA-8` — it was the only field that would have
  wanted it. And `days_left` sits deliberately apart from `no_flight_time`, which is a real
  duration in seconds and is unit-scoped like any other measurement; the two are different
  kinds of quantity that happen to be about time.

  The hole this closed was real: nothing had exempted `days_left` from unit scoping, so a
  logbook declaring `"time": "min"` was saying something about it that nobody intended, and
  its unit lived in its name — which `DATA-52` forbids for values.

- **DATA-67 — Whether a keyed collection of owned items is its own kind of field.**
  *Settled:* **no. Cardinality is a property, and there are three kinds of field rather
  than four.**

  A field description used to name four things, the last two being a singular owned item
  and a keyed collection of them, separate *"because the interface treats them
  separately"* — one a group expanded by default, the other collapsing to a count. That
  reason was sound and pointed at the wrong conclusion: what the interface is switching on
  is **how many**, and how many is a property. It can switch on a property.

  Following that turned up two more places saying the same thing in different words. A
  value or a reference expressed *several* as a flag on the field, while owned items
  expressed it as a second type. And `pressures` — several series, each under a
  `gas_sources` key — was a *kind of value* called keyed series, so "keyed" appeared both
  as a value kind and as a field kind, with nothing relating them.

  One `Cardinality` replaces all three. It means the same
  thing wherever it appears, and the shape of the model is visible in it: plural owned
  items are always keyed and never a list, because their entries are addressed —
  `@2026-06-21#0*p1` reaches one — while a list has no addressable elements. Nothing in
  the model is a list of owned items, and nothing is a keyed reference; the cardinality
  says which combinations exist without a type per combination.

  What a user sees is unchanged, and so is
  [manual/data-format.md](../manual/data-format.md), which lists *keyed owned items* and
  *keyed series* as kinds because that is how they are written in a file. The manual
  describes the syntax; this describes what the syntax is made of.

  **A series is the same argument, and was missed the first time.** Pairs of a time and a
  value are several values reached by time, which is a cardinality and not a kind — so
  `depth` is a *number* held as a series, `alarms` is closed *text* held as a series, and
  `pressures` is a *pressure* held as a keyed series. What had been a description of its
  own, carrying a nested description of its value axis, is now a property like the rest.
  The nested one had a name, a label and a role that meant nothing, which is how the
  mistake showed.

  So the cardinalities are **single, a list, keyed, a series, and a keyed series** — five,
  because the model nests exactly once and has no prospect of nesting twice. A structure
  that expressed nesting generally would also express keyed-keyed, which is nothing.

  **Some combinations are unused rather than impossible, and those stay sayable.** A series
  of references would say which buddy you were with at each moment: coherent, and nobody
  wants it. That is different in kind from a dimension on a count or bounds on text, which
  cannot be assigned a meaning at all. The rule this layer follows is to make the
  *meaningless* unsayable — with a type, which is why a whole number has no dimension — and
  to leave the merely useless alone. Generality is not nonsense, and only one of them is
  worth a type to prevent.

- **DATA-64 — Where a stored value is turned into a value.**
  *Settled in part:* **the source parses, and the description is what it parses against.**
  Where validation happens is `DATA-66`, and open.

  `FieldDescription` was doing both jobs, and only one of them is its own. The
  documentation had said as much without the code following: a description says what a
  field is *"in enough detail that **the parser**, the structural checks and every front
  end can work from it"* — the parser works *from* the description, it is not the
  description.

  **Parsing belongs to whatever the items came from.** JSON has no date, so a date arrives
  as `"2026-02-23"` and has to be read; a database column would hand one over already
  made; a fixture assembled in memory never had a string at all. That difference is the
  source's business and nothing above it should know which case it was in.

  The source cannot do it alone, which is what keeps the description in the picture. No
  store knows that a piece of text is a reference, a gas or a series — those kinds exist
  only in the description.

  **How the two meet was settled a second time, and the first answer was wrong.** It had
  been *a source is asked for a kind and answers or fails*, with JSON implementing `date`
  by parsing and a database by reading a column. That cannot be built: an interface phrased
  as *give me a date* forces every source to parse, which is the very difference between
  sources that this question says is theirs.

  **A source produces a tree instead.** `Stored` is a leaf, members under names, or elements
  in order, and a leaf holds anything — the text JSON has, or the date a database column
  already made. Reading a leaf against a kind takes whichever it finds, and that reading is
  written once, in the layer that owns the rules. The difference between sources then shows
  up in the tree rather than in ten methods each source implements, which serves this
  question's reason better than its first answer did.

  **The reading lives on the description**, one method per kind, taking whatever the leaf
  held. Each kind says which forms it accepts: a date takes a `Date` as it comes and parses
  a string, a number takes a double or a whole number and parses a string, a whole number
  refuses a fraction whichever way it arrives. Nothing switches on the source.

  **A form is a source too**, which is the second thing this buys. A date picker hands over
  a `Date`, a checkbox a boolean, and neither has to render its value to text so that a
  description can read it back. The caller gets a result with the origin already right,
  rather than assembling one and guessing — and a wrong origin is a correct-looking answer.

  **Shape is not the description's business.** It is handed one piece at a time, and whatever
  owns cardinality decides that a leaf belongs here and a group does not. A description given
  a group answers unusable rather than trying to make sense of it.

  **Where validation happens is *not* settled here** — see `DATA-66`. This question settles
  only that reading is the source's job and that the description is what the source is read
  against.

  **`raw` is reserved for what could not be made into a value**: the node as the source held
  it, carried into `Unusable` so an interface can say what it found rather than showing a
  blank. That is not a storage concern — any source can hold a word where a depth belongs,
  and every one of them needs it shown. `DATA-50`.

  A source-specific result type was considered and refused. The logic layer depends *"never
  on a specific source… it must be possible to run and test this layer against items
  assembled in memory, with no files anywhere"*, and a JSON-flavoured result would either
  be unbuildable in memory or force every derivation and every piece of interface to handle
  two shapes.

- **DATA-65 — What a field in the data but not in the description is called.** *Settled:*
  **unrecognised**, and it keeps its own mapping.

  `DATA-51` reserved a place for them without naming it — *"deliberately wider than the
  description: a field this version does not recognise lives here and nowhere else, which
  is what lets it survive a round trip."* The word was already in that sentence.

  It sits beside `unusable` on a different axis, and the pair is the whole of what this
  version cannot do with a file: **`unusable` is a value that cannot be believed,
  `unrecognised` is a name that means nothing here.** A newer version's field and a
  hand-typed misspelling are both unrecognised, and neither can be told from the other —
  which is why they are kept rather than judged.

  Unlike `raw`, this one *is* a storage concern. Keeping a name nobody asked for is
  something a file can do and a schema cannot, so a source that cannot preserve them says
  so rather than dropping them quietly.

  **They are held as [Stored]**, the tree every source produces and none of them owns, and
  nothing in this layer looks inside one. `DATA-64` kept a source's own types out of a
  *result* because a source-flavoured result *"would force every derivation and every piece
  of interface to handle two shapes"*. Neither applies here: an unrecognised field is never
  derived from, never shown, never checked and never read as a value. The only thing that
  touches one is a writer from the source that produced it, and an item assembled in memory
  has none, which is correct rather than a limitation.

  That tree was going to be built anyway, for `DATA-64`, so keeping unrecognised fields in
  it costs nothing and buys something: a writer for one source can put back what a reader
  for another handed over. It loses nothing JSON needs — a whole number tells itself from a
  fraction because a `Long` is not a `Double`, a null is a leaf holding nothing, and members
  keep the order they were written in.

- **DATA-63 — Which items carry `alternative_names`.** *Settled:* dive site, wreck and
  **operator**. Not person, not dive trip, not gas mix, whatever UDDF does.

  UDDF puts `aliasname` on nearly everything, which is a format's caution rather than a
  model. The test applied here is whether a thing is *known* by more than one name, as
  against merely having been called something else once. A site is: charts, local usage
  and a guidebook disagree, and all three are current. A wreck is: renamed before sinking,
  and both names appear in the literature. An operator is: dive centres are bought and
  rebranded, and the dives you logged there were with the old name, so searching either
  should find the place.

  A trip is named by the user and has no other name to know. A person has a name.
  Neither gets a field that would sit empty in every logbook, and an imported `aliasname`
  on one of them folds into `remarks` rather than being dropped silently.

- **DATA-62 — How a wreck's `displacement` is written.** *Settled:* a **number, in the
  file's mass unit**, and no tonne is added to `DATA-8`. A ship reads in kilograms, so a
  fifty-thousand-tonne liner is written 50000000.

  Two shorter routes were refused. **Text with the unit inside it** was the tempting one, on the grounds that nothing calculates with the field. It loses
  the one exact mapping the field has, since UDDF writes displacement as a real number in
  kilograms and a string can only be exported by parsing prose. And it inverts `DATA-52`:
  that ruled "40 cuft" is a *name* rather than a quantity and belongs in `description`, and
  the complement is that a quantity belongs in a numeric field. *Niche, with no arithmetic
  attached* is an exemption any field could claim.

  **A `t` name** was the other, and would have let a wrecks file declare `"mass": "t"` and
  read 48158. It buys readability in one field of one item type, against a unit set whose
  whole value is being short enough to hold in mind. Seven digits are ugly; a vocabulary
  that grows a name per awkward case is worse.

  What a wreck book actually says — "10,077 tons", of a kind it will not name, and as
  often as not gross register tonnage, which is a volume — is a provenance problem rather
  than a unit one. It goes in `remarks`, in the source's own words, which is where
  `DATA-52` sends "40 cuft" for the same reason.

- **DATA-61 — Whether `m3` and `Pa` join the unit set.** *Settled:* both are in, added to
  `DATA-8`'s table.

  They are the SI units for dimensions this model already carries, and the set says what a
  file may declare. What a user writes settles which name is the *default* and how litre
  is spelled; it was never a reason to refuse a valid unit. `DATA-8`'s own generosity
  argument points the same way — an unrecognised name makes every number in a file
  unusable, and a name added later does that to every version before it.

  The practical effect is small and worth having: UDDF is strict SI, so a file converted
  from one by hand can be written in the units it arrived in and needs no arithmetic at
  all. See [../logic/uddf.md](../logic/uddf.md).

- **DATA-60 — How a repetitive dive is tied to the one before it.** *Settled:* an
  optional **`previous_dive`** reference on the dive, and no computed grouping anywhere.

  UDDF wraps its dives in `repetitiongroup`s and hangs a `surfaceintervalbeforedive` off
  each one, holding either `<infinity/>` or a `passedtime` in seconds. Both are derived —
  an interval is this dive's start minus the last one's end, and a group is that
  arithmetic with a threshold laid over it — so by the rule that nothing derived is
  stored, both should simply be recomputed and neither should be a field.

  **The threshold is what breaks that.** The arithmetic is exact; deciding that six hours
  is clean and five is not is a judgement, and one this application would be making on the
  user's behalf every time it drew a group. Agencies disagree about the number, and a
  wrong one is not a rounding error but a claim about somebody's decompression. So the
  judgement is the user's and is recorded, and the arithmetic stays derived: what
  `previous_dive` says is *this one counted*, not how long the gap was.

  It sits on the **dive**, not the profile. Residual gas belongs to the user, and the
  profiles of one dive are the same user seen by two computers — letting them disagree
  about what preceded would be recording a fact about a device rather than about a dive.

  Validation is the ordinary permissive kind: the target must have started earlier, and a
  chain that closes on itself is a `LOGIC-8` question like every other self-reference, not
  something this layer refuses on write.

  Exporting draws UDDF's groups from the chain — an unset `previous_dive` opens a group
  with `<infinity/>`, a set one continues it, and `passedtime` is computed on the way out.
  Importing reverses it: a dive takes the one before it in its group, and the first of a
  group takes nothing. See [../logic/uddf.md](../logic/uddf.md).

- **DATA-17 — Cycles in self-referential lists.** *Relocated to the logic layer,* as
  `LOGIC-8`, and **not answered once for all fields.** A region's `parents`, a dive trip's
  `parent` and a certification's `supersedes` all point at their own type, and what a
  cycle *means* differs in each — so what a walk should do about one is decided per
  derivation, by whatever knows what the field is for.

  Nothing here needs to guard against it, because nothing here walks. An item set answers
  two questions — resolve an id, list a type — and every traversal is above it, alongside
  the derivations that live with the descriptions. This layer's part is only that a cycle
  is not refused on write: validation is permissive, and the check could not be made
  anyway against items that are not loaded.

- **DATA-59 — Which water type a recorded depth was computed with.** *Settled:* the
  profile records it. A profile carries a **`water_type`** — what the computer was set to,
  not what the water was — and a **`density`** derived from it, overrideable.

  The derivation needs two things because one is not enough. `fresh` is 1000 and
  `en13319` is exactly 1020, that figure chosen so ten metres is one bar. `salt` is not a
  number at all: makers use anywhere between 1025 and 1035, so density follows from the
  water type *and the make of computer*, read through the profile's `dive_computer`.
  Where the make is unknown, salt has no single answer.

  **Without a density, nothing can be worked out from the profile.** A dive computer
  measures ambient pressure and converts to depth on the device; only the depth is
  downloaded. Working back to pressure — which is what decompression needs — requires the
  constant the device used, so where it is missing the derived values are unusable rather
  than approximate, per `DATA-50`.

  This is why a site's `water_type` could never have served. A user may dive the sea with
  a computer set to fresh, and the depths will say fresh; the site is salt regardless. The
  two fields share a name and a vocabulary and answer different questions — one describes
  water, the other an instrument's setting.

  Correcting the setting does not touch the depths already written: it changes what they
  mean. Saying so, and offering to convert them, is the interface's work — `GUI-18`.

  Whether the setting arrives at all depends on the source. Some computers report it and
  some do not, and a download library may or may not know how to read it from a given
  model; where such a library supplies a nominal figure of its own rather than the
  device's, that is a derived value arriving from outside and is kept only where it
  disagrees.

- **DATA-58 — Whether a series can say that data is missing, as against merely sparse.**
  *Settled:* it cannot, and nothing is added to say so. A series is pairs, read as
  piecewise linear throughout, and a long gap means the same thing whether the device was
  sampling coarsely or had lost a transmitter.

  Where a device knows it lost signal it raises a `link` alarm, which is already recorded
  with its time, so the fact is in the profile even though it is not in the series. That
  is enough to explain a curve to someone looking at it, and it costs nothing.

  **What that does not do is worth writing down.** An alarm says the link went, not when
  it returned, and this model does not carry UDDF's `tankref`, so on a dive with twins and
  a stage the alarm does not say which transmitter dropped. A reader can tell that
  *something* was lost around a certain time; it cannot mark the affected stretch of the
  affected series.

  Both alternatives were declined for the same reason: they add structure to every profile
  to describe a case that is uncommon and already half-recorded. A sentinel would put a
  non-number in a series of numbers; a separate list of gaps would be a second structure
  to keep in step with the first.

  This is one of the few decisions that cannot be revisited cheaply. A logbook written
  without the distinction cannot be given it later, because nobody will know afterwards
  which gaps were which.

- **DATA-35 — Validity measured in use rather than time.** *Settled:* dates only.
  `valid_until` is a date and there is no counting of dives. A user whose regulator is
  due every hundred dives works out when that will be and writes the date.

  The model therefore says less than the label on a regulator does, and that is accepted.
  What it buys is that validity means one thing everywhere: a date, on a medical, an
  insurance and a service alike, with `days_left` a subtraction and `expired` a
  comparison. A usage limit would have made gear the exception — the only place validity
  needed a walk over the logbook to count which dives used which item, and the only place
  where "expired" meant *whichever of two came first*, which is a third derived value
  neither `days_left` nor a dive count answers.

  `FEAT-17` stays *Low priority* rather than rejected. Adding a limit later is additive,
  since a maintenance with no such field simply has no limit, so nothing written now
  forecloses it.

- **DATA-36 — What counts as due soon.** *Relocated to the logic layer,* as `LOGIC-7`.
  Not a data question: this layer records what is true — a `valid_until`, the `days_left`
  until it, whether it has `expired` — and "soon" is a judgement about those facts rather
  than another fact. Nothing is stored for it and no field carries it.

  Which is the same line the layers are drawn on everywhere else. A date subtraction is
  arithmetic; deciding that five weeks is worth mentioning and six is not needs to know
  what the thing is and how a user plans, which is knowing what diving is.

- **DATA-38 — Whether an item can have more than one maintenance sequence.** *Settled:*
  yes, and without a second list. Entries stay in one collection and the derived values
  are worked out **per obligation** rather than for the item as a whole, so a cylinder's
  yearly inspection and five-yearly test each answer for themselves.

  What identifies an obligation is what an entry says falls due next: `follow_up_type`,
  or the entry's own `type` where that is not given. So an inspection sets the inspection
  clock, a pressure test sets the test clock, and a repair that says
  `follow_up_type: service` resets the service clock rather than inventing a "repair"
  obligation — a repair is something that happened, not something owed. An entry with no
  `valid_until` starts no clock at all.

  That keeps `follow_up_type` doing the job it was added for, which was the *alternating*
  case, while making the *concurrent* case work too. The two turned out to be the same
  rule seen from either side: an entry always says when the next thing is due and what
  that thing is.

  A separate sequence item was the alternative and buys little. It would make a user
  choose a sequence before logging a service, and it puts a second level of nesting under
  gear to hold what `type` already distinguishes.

Kept with their identifiers so earlier discussion still resolves.

- **DATA-56 — Fields that have a default rather than being absent.** *Settled:* a field
  with a default never reads back absent — where nothing is written it gets one, and it is
  *usable*. **It is not a mechanism of its own.** A default is an **overrideable derivation
  whose computation is a constant**: nothing written and it works out the same answer every
  time, something written and that wins.

  The two were separate at first and turned out to be one thing described twice. Every
  clause said of a default is what overrideable already did, and holding both meant a rule
  about which fields could carry one — a default beside a derivation would have won always,
  since a derived value is never written — enforced by a check rather than by the shape.
  Now the question cannot be asked.

  Nothing is stored to make it happen. The file stays as it was — a user's gear does not
  gain a `generic: false` it never had — so writing back is unchanged and a default costs
  nothing on disk. `DATA-27` still holds: storage has exactly one absent state, and the
  value is worked out on the way out.

  `generic` on gear is the case that prompted it, and remains the only field in the model
  with a default. Almost nothing in a user's own logbook
  is generic and almost everything in the supplied library is, so the library states
  `true` and everything else says nothing and means `false`. Making every owner write
  `generic: false` on every item would be noise in service of a distinction they never
  think about.

  **A default is only right where absent has no meaning of its own**, which is why this
  is true of a few fields rather than a habit — and matters more now that writing one is a
  computation rather than a value, since a computation is easier to write carelessly. A flag is on or off, so absent is
  merely unwritten. A missing `max_depth` is not zero — it is unknown, and defaulting it
  would turn a gap into a false measurement, which is exactly the confusion `DATA-50`
  exists to prevent. Give a field a default only when the alternative is every reader
  guessing the same one.

- **DATA-19 — How a region's extent is stored.** *Settled:* four numbers, named for the
  edges they are — `west`, `east`, `south`, `north`. **`east` is the edge reached
  travelling east from `west`**, so a box crossing the antimeridian has an `east`
  numerically below its `west` and needs no explaining. Latitude does not wrap, so `north`
  is always above `south`.

  The names were `min_longitude` and friends first, and the rename is most of the answer.
  Everywhere else in this model a `max_` prefix marks an *observed extreme* — `max_depth`
  on a dive, and on a dive site — so a box edge borrowing it invited the same reading,
  under which `min` above `max` genuinely is broken. Naming the edges for their compass
  points says what they are and leaves nothing to correct.

  The alternatives each bought less than they cost. A list of boxes removes the special
  case from every consumer, but makes the common region — one box — a list of one, and
  turns something legible into something awkward to write by hand, which the format
  cannot afford. A west edge plus a width is unambiguous and arithmetic, but stops
  reading as a bounding box to anyone editing the file and departs from how extents are
  written everywhere else.

  What remains of the rule has somewhere to live: it is a property of the field, so the
  description carries it and the manual states it once, rather than each consumer
  rediscovering it. Arithmetic still has to cope — a width is not `east - west` when the
  box wraps — but that is now a calculation to get right rather than data that looks
  wrong. And the extent feeds nothing: it places a region on a map and bounds it, and nothing derives from it or
  references it, so a consumer that mishandles the wrap draws a wrong rectangle rather
  than producing a wrong number.

  The failure it guards against is worth naming, because it is the kind that passes
  review: treating a wrapped box as ordinary yields the *inverse* region — a box around
  the rest of the world — which looks like working code and is wrong only where the date
  line is crossed. The supplied regions carry three such cases, and `fixtures/cousteau`
  one more, deliberately.

- **DATA-25 — Where suggested values come from.** *Settled:* two sources, joined above
  this layer. The **`FieldDescription` carries the presets**, written with the field like
  its type and its unit. The **Universe gathers what is already in use** from the loaded
  items, and hands out the union of the two.

  Not a library. A library ships reference data that items point at, and its ids are close
  to permanent — a heavy promise for a list nothing references, where dropping a value
  breaks nothing. `FEAT-16` remains a way to *extend* the presets later rather than to
  replace where they live.

  **A suggested vocabulary constrains nothing.** This is the whole of the difference from
  a fixed set: a value outside a fixed set is unusable, by `DATA-24` and `DATA-32`, while
  a value outside a suggested vocabulary is an ordinary value and always was. The
  suggestion is help with typing, and belongs to the interface — see *Choosing among known
  values* in [../ui/gui/doc.md](../ui/gui/doc.md), which is also why the
  [tui](../ui/tui/doc.md) offers none of it and takes a plain string as typed.

  The presets are still written into the model rather than left to the interface, because
  a default that everyone is shown is what keeps a logbook self-consistent — the user who
  wrote `slipway` once should be offered it the second time instead of writing `slip way`.
  Recording them is encouragement, not enforcement.

- **DATA-45 — Whether conditions stay yes-or-no.** *Settled:* they become a six-step
  fixed set, the same one for `current` and for `waves`: `none`, `very mild`, `mild`,
  `moderate`, `hard`, `very hard`.

  What decided it was import, not richness. UDDF records current as a six-value
  enumeration — `no-current` through `very-hard-current` — so a flag would have collapsed
  five distinct values into `true` at the moment a dive arrived, and no later widening
  could recover them. A field that loses data on the way in is worse than one that is
  merely coarse.

  The steps are UDDF's, with the `-current` suffix dropped so the same scale serves waves,
  and written as words with spaces as every other value in this model is. The mapping is
  therefore one-to-one in both directions and needs no table. UDDF defines the steps by
  what a diver can do rather than by speed, which is what makes them recordable from
  memory after a dive; that reading is kept.

  `waves` gets the same scale despite having nothing to import from — UDDF has no wave
  element. One scale for both is easier to hold in mind than two, and `hard` reading a
  little oddly of a sea surface is a smaller cost than a second vocabulary.

- **DATA-52 — What a cylinder's volume means.** *Settled:* **water capacity, always.** A
  cylinder's volume is how much water it would hold, in the volume unit of its file — a
  twelve-litre is 12, and an American forty is a little under six.

  An imperial size is a **name, not a quantity.** "40 cuft" describes the free gas the
  cylinder delivers at its working pressure, which depends on that pressure and is a
  different measurement from the volume of the vessel. It is written where names are
  written — `model`, `description` — and never in a numeric field. That is why `cuft` is
  absent from the unit set in `DATA-8`, and why adding it would be wrong rather than
  merely unnecessary: it is not another way of writing a volume.

  The field is `capacity` on a gear item, and a gas source's `volume` derives from it.
  What a cylinder *displaces* is a separate figure and lives in `buoyancy` as
  `displaced_volume` — the outside of the cylinder against the inside. Both were once
  called volume, which is how they came to hold the same numbers. The name says its
  dimension on purpose: a ship's `displacement` is a *mass*, and the two words would
  otherwise collide across `Wreck` and `Buoyancy` with different dimensions behind them.
  A ship keeps `displacement` because that is the term of art and `tonnage` means
  something else — gross tonnage is a volume — so the field this project invented is the
  one that moved. See the note under `DATA-52`
  in the fixture and library data.

  **Displacement is the item's own volume**: the material, plus any gas sealed inside it.
  Water that floods freely in and out belongs to the sea, not to the item. This settles a
  case that looked like a missing field — a wetsuit weighs more once soaked, and nothing
  in `buoyancy` records that. Nothing needs to: taking on water adds mass and adds the
  displacement of that same water, and the two cancel exactly, because what it absorbed is
  the water it is floating in. The apparent gap was an accounting error, counting a
  flooded suit's outer envelope as displacement while leaving the water inside it out.

- **DATA-34 — Money as a unit.** *Settled:* there is no money. `price` is dropped from
  gear and from maintenance, and currency is not a dimension — the unit set in `DATA-8`
  holds only quantities with a fixed base.

  Currency was the one thing in the model that could not obey the rules the rest of it
  runs on. Every other dimension converts by a constant, which is what lets a value be
  kept as written and converted at the point of use, and lets figures from files in
  different units be added together at all. A hundred euro is not a fixed number of
  dollars, and what it is depends on the day — so a total across fifteen years and three
  currencies needs rates and dates from outside, which an offline logbook has nowhere to
  get and no business storing.

  Tracking what diving costs is a reasonable thing to want and a different application
  from this one. Registered as rejected in [../../features.md](../features.md) with
  the reason, so it is not proposed again.

- **DATA-32 — Whether a numeric range is enforced or advisory.** *Settled:* enforced, and
  by the same rule as `DATA-24`. A value outside its range is kept on disk exactly as
  written and read back **unusable** — a `rating` of 50 where the range is 1 to 10 is not
  a rating. Nothing is refused and nothing is silently corrected.

  The average that prompted the question then comes out right without anyone having to
  remember why: `DATA-26` already has statistics skip what they cannot use, so an
  out-of-range rating is excluded rather than dragging the mean upward. And because the
  written value survives, an interface can say what it found instead of showing a blank —
  which is the difference between a user finding the typo and never learning of it.

  One rule now covers both shapes of the same mistake. A range and a fixed set are the
  same thing said two ways — a closed description of what a field may hold — so a value
  outside either is unusable, and the reader needs no special case for numbers.

- **DATA-53 — Whether a prefixed setting overrides a plain one.** *Settled:* the question
  does not arise, because **granularity is fixed when a setting is defined** and a setting
  exists at exactly one. Either there is a `desktop_window_size`, or the window size is
  split per platform, never both — so a plain name and a prefixed one for the same
  setting cannot coexist and there is nothing to resolve between.

  A reader therefore always knows the whole name to ask for, and the three layers of
  `DATA-9` apply to that one name. No second resolution order, and no falling back from
  `linux_` to `desktop_` to plain.

  In practice most settings will be unprefixed or `desktop_`/`phone_`. A platform prefix
  is for something that only exists on that platform at all, not for a value that merely
  happens to differ there.

- **DATA-9 — Whether display preference lives in the logbook or the installation.**
  *Settled:* both, in three layers, the first that answers winning:

  1. **Local settings**, held by the installation and never written to the logbook.
  2. **Settings in the logbook**, which travel with it and reach every device.
  3. **Built-in defaults.**

  So a preference set once in the logbook follows a user to a new device, and an
  installation can still depart from it without that departure leaking back into shared
  data. A device that has never been told anything falls through to the logbook, and then
  to the default, so nothing has to be configured before a logbook is usable.

  Where a setting is *expected* to differ by device, the answer is not to rely on the
  chain diverging. It carries a prefix naming what it applies to, and a device reads only
  those addressed to it — so both can sit in the logbook and travel together without
  fighting.

  The prefixes are the [form factor](../glossary.md) and the platform, spelled as the
  directories under `ui/gui/` are: `desktop_` and `phone_`; `windows_`, `mac_`,
  `linux_`, `android_` and `iphone_`. A setting with no prefix applies anywhere.

  That keeps one axis out of the lookup chain. A phone does not have to know what a
  desktop would have chosen, and a setting meant for one platform cannot be reached by
  another through any amount of falling through.

  **This is a lookup chain, and units deliberately have none** — see *Units*, where a
  declaration reaches no further than its own file. The two are not in conflict because
  they govern different things. A unit decides what recorded data *means*, so an
  invisible resolution order can silently corrupt it: the same number becomes a different
  depth. A setting decides only what a user is shown, so getting it from the wrong layer
  is visible, harmless and immediately correctable. Ambiguity is cheap here and expensive
  there.

- **DATA-8 — The vocabulary of unit names.** *Settled:* short symbols, from a closed set,
  written exactly as listed. These are what a user writes and what the fixtures and
  every supplied library already use, so nothing needed renumbering.

  | Dimension | Names |
  |---|---|
  | length | `m` `ft` |
  | mass | `kg` `lb` |
  | time | `s` `min` `h` |
  | temperature | `C` `F` `K` |
  | volume | `l` `m3` |
  | pressure | `bar` `psi` `Pa` |
  | angle | `deg` |
  | density | `kg/m3` |

  The first of each is the default. Case is part of the name: `C` is Celsius and `K` is
  kelvin, `Pa` is the pascal, and none can be written the other way round. Cubic metres
  are `m3`, since nothing here carries a superscript, and a compound name divides with a
  slash: `kg/m3`.

  **Density is a dimension like any other**, keyed `density` in a `units` block and
  defaulting to `kg/m3`. It has one name because one is all anyone writes — water is 1000
  to 1035 and every source quotes it that way — but it is in the table rather than fixed
  in prose, so the unit of a density comes from the same place as the unit of a depth. A
  second spelling was rejected rather than forgotten: `g/cm3` writes the same water as
  1.025, and two spellings a thousand apart is exactly the confusion this set exists to
  prevent.

- **DATA-87 — What an unrecognised unit name spoils.** *Settled:* the measurements of
  that dimension, and nothing else. A file declaring `"length": "fathom"` gives up its
  depths and its distances, as unusable values carrying what was written and a reason
  naming the unit. Its temperatures and its pressures read normally, and the file opens.

  Guessing is not the alternative. A depth in feet read as metres is wrong by a factor of
  three and looks perfectly ordinary, so a unit that cannot be understood is never assumed
  away. The question is only how far the refusal reaches.

  **Forward compatibility decides it.** A later version may add a name to `DATA-8`, as
  `DATA-61` already added `m3` and `Pa`. A logbook written with it, opened by an older
  build, should cost the one dimension that name governs — not every measurement in the
  file. The wider rule would turn each addition to the set into a file that older builds
  read as blank, which is a heavy price for a compatible change, and it is paid by the
  user rather than by whoever added the unit.

  A misspelled dimension key is the same case and takes the same answer: `"lenght": "ft"`
  is a dimension this build does not know, so the lengths keep their default and nothing
  else is touched. There is no reading under which it should silence the temperatures.

  The manual states this to the user in *Units*.

- **DATA-88 — How many digits a measurement is written with.** *Settled:* **each unit carries
  its own precision, in decimal places, and the same figure governs showing a value and
  storing one.** The figure is 0, 3, 6 or 9. No trailing zeros, and never an exponent.

  Converting a number into a unit and back is exact only to about the sixteenth digit, so a
  value written straight from a double gains a tail on every save. Rounding at the unit's
  precision removes the tail at its source.

  **This replaces an earlier answer of twelve significant digits**, which was right about
  files and useless on a screen. A worked-out average depth came out as `22.1056451613`, and
  a depth of 92 feet read in metres as `28.0416`, because the interface had nothing to show a
  number with but the form a file is written in. Adding a second rule for display would have
  let the two drift; one figure per unit means the number a user reads and the number their
  file holds are the same number.

  **Decimal places rather than significant digits**, which is the opposite of what the earlier
  answer chose, and for the reason that answer could not use: a place means something definite
  once the unit is known, and here it always is. The old objection — that six places would
  write a 0.0456 litre item in cubic metres as `0.000046` — is answered by the cubic metre
  carrying nine places and the litre three, rather than by counting from the first digit.

  **A unit's precision is the finest thing written in it, not the finest a diver reads.** A
  depth wants one decimal; the litre carries three, because the supplied gear holds displaced
  volumes of `0.04` litres, and the bar three because an atmospheric pressure is `0.88`.
  Choosing by how a depth reads would have rounded fifty-three supplied items to nothing. The
  figures were taken from what the data actually holds.

  **Steps of three, and a unit takes the smallest that holds what is written in it.** Three
  decimals suits nearly everything. Six is for the hour, where three would be steps of 3.6
  seconds, and for the degree, where three would be a hundred metres of position — enough to
  lose which end of a wreck a site names. Nine is for the cubic metre alone, since a weight
  displacing 0.0456 litres is 0.0000456 of one. Nothing is for the second and the pascal,
  which are already finer than anything measured.

  The alternative was a figure per unit, chosen for each. That is eighteen small arguments to
  make and to keep, and the differences between them were not carrying their weight: a metre
  at two decimals and a bar at two were the same decision written twice, and the two that
  genuinely differ are the ones a step of three still separates.

  A consequence worth having: the metre gets three where a depth wants one, and that settles
  `medical.height` without an argument of its own. `1.85` survives, where a figure picked for
  depths would have made it `1.9`.

  **Storing is lossy now, and that is the point.** A value finer than its unit allows is
  rounded on the way out, so a logbook cannot hold precision its own units cannot express.
  Nothing currently stored loses anything: of 1740 values in the fixtures, the only three that
  move are a derived average and two figures written in feet, which a file in feet writes back
  as the `92` and `13` a user typed.

  **No exponent**, because `4.56E-5` and `2.0E7` are both JSON and both read back correctly,
  and neither belongs in a file a diver opens in a text editor. A pressure in pascal is
  `20000000`. **No trailing zeros** either: `108000` is what a file holds and `108000.0` is
  noise.

  What this does not buy is an exact round trip through a unit that divides unevenly. 3661
  seconds is 1.0169444… hours and no finite decimal writes it, so the first save rounds it.
  What has to hold is that saving an unchanged logbook produces no diff, and that does: the
  writing is settled after one pass.

  **What a user writes decides the default, not the set.** `l` for litre is kept over the
  `L` the SI brochure permits because `l` is what users write — the ambiguity with `1` is
  a display concern, and every place the application shows a unit can choose its own
  glyph. That is an argument about spelling and about which name comes first; it is not a
  reason to refuse a valid unit for a dimension the model already carries. `m3` and `Pa`
  are valid and are in (`DATA-61`), even though no logbook will be written in them.

  **An unrecognised name makes every number in that file unusable**, in the sense
  `DATA-50` settles — not absent, and not refused. The file still opens, everything
  non-numeric in it still reads, and the interface can name the unit it did not know. A
  fallback to the default was rejected: a depth written in feet and read as metres is
  silently wrong by a factor of three, which is worse than being told nothing is legible.

  That blast radius is the reason to be generous now. One typo costs a whole file, and a
  name added in a later version has the same effect on every version before it, so the
  imperial units are in from the start rather than waiting to be needed.

- **DATA-89 — What order items of one type are listed in.** *Settled:* **the type says so,
  as a list of fields with a direction apiece.** `ItemDescription` carries `orderedBy`, and
  a front end applies it without knowing what it names. Dives and trips are listed on
  `start_date` descending; everything else on `name` ascending.

  It had to live in the description, because `UI-3` gives the terminal front end nothing but
  the descriptions and the whole point of that is that a type added below appears above
  without anything above changing. An order coded in a front end is a second place that has
  to learn about every type, and the four front ends planned would each learn it separately.

  **The direction is written down rather than worked out.** The first shape had it follow the
  kind — text ascending, dates descending — which gave the right answer for all nine types
  and read wrongly: `orderedBy = listOf("start_date")` says *ordered by start date*, and every
  reader takes that to mean oldest first. Being right about the nine types was what made the
  rule look sound. Ascending is the default, so the seven alphabetical types still say one
  word, and the two that reverse say which way in the declaration.

  **Absent goes last in both directions.** A dive with no date is not the oldest and not the
  newest, and it is decided before the direction is applied — the sign that reverses a
  descending step would otherwise lift the unknown to the top.

  **The id breaks every tie**, so the order is total and does not depend on which file an item
  was written in. Without it a type kept in one file and the same type kept in a folder would
  list differently, and `JSON-21` lets a logbook use either.

  Text compares without case. Comparing code points puts `Zoo` before `apples`, which is not a
  list anyone reads. Accents still sort by code point, common Kotlin having no collation, and
  that is a real limitation rather than a decision.

  Sorting is not a third question for `ItemSet`. `DATA-4` holds: the set lists a type and names
  an item, and `inOrder` composes those two over an ordinary list.

- **DATA-51 — Whether typed accessors exist, and for which fields.** *Settled:* three
  ways in, and not one of them is per field.

  - **The raw mapping.** Every field the description names, as it was parsed, by name,
    untyped. Writing back uses it, together with the separate mapping `DATA-65` puts beside
    it for the fields this version does not recognise.
  - **A checked read**, accepting only names the `ItemDescription` carries. This is what
    the logic layer uses. A name the description does not know is a fault in the code,
    not in the data, and is reported as one — which is the distinction that makes a
    misspelt field name findable rather than a silent absent.
  - **Typed convenience methods** — `getInt`, `getText`, `getDate` and the rest — which
    look the field up in the description, confirm it is declared as that kind, and hand
    back the three states of `DATA-50` with the value already typed. A caller asking for
    the wrong kind is told so rather than given a bad cast.

  **How many methods that is, is a language artefact.** It was written as one per kind —
  about ten — because Dart could not do better. *Settled:* **five, one per cardinality,
  each generic over the kind** — `single`, `list`, `keyed`, `series` and `keyedSeries`.
  Reified generics collapse the kind axis. Cardinality does not collapse, because the
  shapes genuinely differ and a series is not a list of anything. The ten were along the
  axis the description model exists to remove; these five are along the other one.

  **The confirmation is against the description, not the value**, so a `FieldDescription`
  declares the type its kind produces. Checking the value would catch a wrong kind only on
  the runs where the field happens to have one, and an optional field is usually absent —
  which would put the silent-absent fault back in a new form.

  One method per kind of value, or one generic method, rather than an accessor per
  field across nine types — so the per-field surface the description design removed does
  not come back. Nothing here names a field, so this layer still knows nothing about
  diving, and the three-state result is unwrapped once per call site instead of being
  rebuilt by hand at each one.

- **DATA-50 — What reading a field gives back.** *Settled:* three states, in one result
  type — **absent**, **usable** with the value, or **unusable** with what the source held and
  why. A nullable value would collapse absent and unusable into one answer, and those are
  the two a user most needs told apart: nothing recorded, against something recorded
  that this version cannot use. Every reader has to handle all three, which is the point
  rather than the cost — the question is asked at each use instead of being forgotten
  once, centrally.

  This fixes more than one signature. It is what a derivation returns, and what `GUI-8`
  renders, so an empty cell and a broken one stop looking alike.

  **What an unusable read carries is a `Stored` node, not text.** It was text while every
  value arrived as text, and stopped being so when `DATA-64` settled that a source hands over
  a tree. What could not be used may be a whole subtree — an object where a date belongs —
  and rendering that to text in order to keep it would write a string where the file had an
  object, which is a worse answer than the one being refused. A member of a collection
  carries the same, for the same reason.

  It follows that **badly shaped data is unusable rather than refused**, at whatever level
  the shape went wrong. A field whose value is the wrong shape is an unusable field; a member
  that is the wrong shape is an unusable member and its neighbours are unaffected. `DATA-77`.

  **A usable value says where it came from**: stored, derived, or overridden where a stored
  value corrects a derivation. A caller needing the difference — an
  interface greying a value nobody typed, an editor deciding whether clearing a field does
  anything — no longer works it out from the description and the raw mapping for itself,
  and that rule lives in one place rather than in each of them.

  All of them, rather than the one that prompted it. They exclude one another, so a flag
  each would allow combinations that are nonsense, and a set that answers the question
  completely raises no question about why it stopped where it did. A defaulted value is
  *derived*, by `DATA-56`.

  It says nothing about what is written back. Writing uses the raw mapping, which the
  origin does not touch — `DATA-56`.

  **It does not make propagation automatic**, and an earlier draft of this entry said it
  did. `DATA-26` is the one to follow: an unusable input propagates *by default*, and
  anything else is decided case by case — by the derivation, which is the only thing that
  knows whether it can manage without.

  Absent is where that matters most, because a missing input is often something a
  derivation has an answer for. `deco` is the settled example: it reads the recording's
  `decostop`, and *failing that* a `no_deco_time` that never reached zero — see `LOGIC-6`
  in [../logic/doc.md](../logic/doc.md). A rule that turned an absent input into an absent
  answer would have made that impossible to write.

  So a derivation is handed the item and decides. What this layer offers is the
  short-circuit for the common case, not a rule that imposes it.
- **DATA-24 — What happens to a value outside a fixed set.** *Settled by `DATA-50`:* it is
  kept on disk exactly as written and read back as **unusable**, never as absent. That
  covers both cases the question named — `brackish` typed by hand, and a value a newer
  version wrote that this one has not heard of — without refusing the file and without
  losing the meaning silently. What the source held stays available, so an interface can say
  what it found rather than showing a blank, and a writer can put back what it was given.

- **DATA-49 — Where a derived value's computation is named.** *Settled by moving the
  descriptions:* a description declares a field derived and carries the computation, and
  both live in the logic layer. There is nothing to register across a boundary and no
  special case for `deco` — see *Where the descriptions live*.

- **DATA-12 — What a certification item is.** *Settled:* the qualification itself —
  an agency's open water award — which a library supplies. A person's holding of one
  is recorded separately, by a *course* owned by that person.
- **DATA-15 — A proposal cannot be a function of the item alone.** *Settled:* it can.
  The item proposes a base from its own data; the collection appends `#<index>` when
  that base is taken.
- **DATA-16 — Whether an index is always present or only added on a clash.** *Settled:*
  *Settled:* per item type. Dives always carry one; everything else omits index zero
  and indexes only on a genuine clash.
- **DATA-14 — Whether regions nest.** *Settled:* they do, and a region may have
  several parents, so the structure is a graph rather than a tree.
- **DATA-18 — Which ancestry to show.** *Relocated:* a presentation question, not a
  data one. Now `GUI-7`.
- **DATA-26 — How an unusable value propagates.** *Settled:* propagate by default, since a
  value built on an unusable one cannot be trusted. Statistics across many items are
  the exception and skip what they cannot use. Anything else is decided case by case.
- **DATA-27 — Whether absent has more than one meaning.** *Settled:* no. There is one
  absent state. Storing the distinction would clutter every item, and deriving it is
  more dangerous than useful.
- **DATA-22 — Whether derived values are cached.** *Settled:* not by default. Caching
  is an implementation matter and is avoided unless performance demands it, and used
  carefully where it is.
- **DATA-28 — Whether an owned item can be singular.** *Settled:* yes. What form a
  person's health grouping takes is a separate question — `DATA-31`.
- **DATA-29 — Derivations that depend on the current date.** *Settled:* no special
  handling. Derived values are never stored, so a value that depends on today never
  reaches a file, and two installations showing a different `days_left` are both right
  rather than in conflict. With caching avoided by default, staleness does not arise.
- **DATA-30 — Sensitive and third-party data in a logbook that leaves the device.** *Settled:*
  *Settled:* the user's decision. Nothing is withheld from a copy that leaves the
  device; a logbook holds what its owner chose to record, which in practice is this
  kind of detail about themselves.
- **DATA-31 — How a group of fields is expressed.** *Settled:* as a singular owned
  item, nested on disk. Grouping and prominence are the same thing, and both follow
  from the stored shape. A person's health details are one such item.
- **DATA-33 — What an organisation is.** *Settled:* a plain string, for now. Turning it
  into a reference later would let an agency carry an address or a website, and is
  worth revisiting only if that is wanted.
- **DATA-13 — Which way a dive trip and its dives point.** *Settled:* the dive names
  its trip and the trip derives its list. See *A relationship is stored once*.
- **DATA-37 — Whether a medical is one item or a history.** *Settled:* one item. A
  medical has no validity of its own, so it takes no part in renewal, and the reason to
  keep a history went with it.
- **DATA-40 — Which two of start, end and duration are stored.** *Settled:* none of
  them, where a primary profile exists — all five time fields derive from it. Failing
  that, `start_date` and the two times are given and `end_date` and `duration`
  follow. All five are overrideable.
- **DATA-41 — Whether a dive records a time zone.** *Settled:* not for now.
- **DATA-42 — Two things called index.** *Settled:* the field is `dive_number`. The
  number in an id keeps its own meaning and is never confused with it.
- **DATA-43 — What "required" means where nothing is enforced.** *Settled:* nothing is
  mandatory in storage, `start_date` included. A proposal only runs when an item is
  created, so a file missing a date keeps the id it already has. Making sure a new
  dive has a date to propose from is the interface's job.
- **DATA-44 — A dive has no `name`.** *Settled:* it has one, derived as the start date
  and the dive's number within that day. Every referenceable type therefore has a
  `name`, and every id is proposed from one, dives included.
- **DATA-10 — Whether dates, times and durations participate in unit scoping.** *Settled:*
  dates and times do not — one notation, everywhere. Durations do, being quantities with
  the time dimension.

  *Amended:* a profile's `gmt_offset` does too. It was exempt, on the grounds that it
  corrects a clock rather than measuring a length of time. It is still that, but the
  distinction bought an exemption the model has no way to state: what a `units` declaration
  reaches is a dimension, so the only way to keep one off a measurement is to say it measures
  nothing. That describes the field wrongly to say something true about it, and an exemption
  nothing can express is one nothing enforces.

  So it is a number of seconds unless its file says otherwise, like every other quantity with
  the time dimension. A file writing its times in minutes writes this in minutes too, which is
  at worst odd to read and never wrong.
- **DATA-39 — How a duration is written.** *Settled:* as a number of seconds, and so
  not a distinct kind of value at all. The only duration left is a dive's, which the
  interface formats for reading; a maintenance item now states a `valid_until` date
  outright rather than a length of time.
- **DATA-48 — Whether a library can span several files.** *Settled:* no. A name is one
  file. The supplied regions are named one continent at a time, and a logbook lists the
  ones it wants.
- **DATA-47 — Whether a library file declares the type of its items.** *Settled:* it
  does not. The logbook names its libraries by type, so a library file stays an ordinary
  id-keyed map and holds exactly one type.
- **DATA-46 — Reserved keys in a file.** *Settled:* `units` is reserved. In a file keyed
  by id it declares the units for that file, and no item may be called `units`. Every
  library file carries one.

  *Amended:* it is reserved in a file holding one item too, where it sits beside that
  item's fields rather than beside other items, so no **field** may be called `units`
  either. The question was written when only the grouped shape was in view, and its title
  said so; both shapes may carry a declaration, so both reserve the name. None of the
  fields in `manual/data-fields.md` was affected.
- **DATA-3 — Where do items live?** *Settled:* in this layer, with the items.
- **DATA-20 — Which layer owns the loaded items.** *Settled:* this one, as
  an **`ItemSet`**. Structural validation belongs with it; domain rules do not, and
  live in logic behind the **Universe** — see [../logic/doc.md](../logic/doc.md).
  *Amended:* the set and the machinery stay here, but the descriptions it is constructed
  with moved to logic — see *Where the descriptions live*. The name was `Items` when
  this was settled, and changed with `Item`.
- **DATA-21 — What an item can do without a set of items.** *Settled:* it never has
  to. An import is read into its own set, so candidate items are complete and
  resolvable before anything is merged.
- **DATA-23 — Whether unsaved work is a second set of items.** *Settled:* yes. The
  logic layer's **Universe** holds whichever sets are open, and reconciliation compares
  two of them.
- **DATA-4 — Query surface.** *Settled:* resolve an id, list a type, and nothing
  else. Filtering and sorting happen above, in memory; items navigate themselves.
- **DATA-5 — Read model.** *Settled:* whole items, of the type asked for, fully
  constructed when the data is read. No summaries and no partial items.
- **DATA-6 — Change notification.** *Settled:* none. Nothing is announced and nothing
  subscribes; a view that may be out of date asks again.
- **DATA-7 — Where migration runs.** *Settled:* once, when the logbook is opened.
  Nothing above ever meets an older shape.
- **DATA-1 — Whether "owned" also means physically inline.** *Settled:* for a profile,
  yes. It sits in the dive and is read with it. The scale is manageable provided samples
  are held columnar in memory rather than as an object per sample; the difference between
  those two is roughly tenfold, and larger than the choice of where to put the file.

  On the JVM *columnar* has to mean primitive arrays rather than a list of numbers, since a
  boxed list pays an object header and an indirection per sample. The tenfold estimate was
  made against a language that boxes less, so it is a floor here rather than a figure.
- **DATA-2 — Whether a dive's number is derived or primary.** *Settled:* primary. The
  manual records it as a field the user writes — their own numbering, kept or not as
  they please — precisely so that finding a forgotten dive renumbers nothing.
- **DATA-11 — Angles and coordinates.** *Settled:* degrees, and they take part in unit
  scoping like any other quantity. `angle` is the dimension; every supplied library
  declares `deg`.
