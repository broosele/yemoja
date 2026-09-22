# UI layer — TUI

A deliberately raw terminal interface: tables of items with new, edit and delete.
No polish, no visual design.

**Reading and writing are both built.** It opens a logbook, shows what is in it, and changes
it: a value is typed over, an item is made or deleted, and an entry is added to a list or to a
keyed collection. Every change goes to the Universe and is saved as it is made — there is no
save key and nothing is held back. What it cannot do is a series, which nobody types into a
terminal. `TUI-3`.

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

**Both orders come from the descriptions.** The tabs are the types the item set was built
with, in that order, and the list under each is what that type's `orderedBy` asks for — dives
and trips newest first, everything else alphabetical. Nothing here names a type or a field to
do it, so a type that changes its mind about its own order changes nothing in this front end.
`DATA-89`.

The screen is handed a logbook and nothing else. It once took the type list beside it, which
was the same list twice by two routes and left a way for them to disagree: a screen given one
list over a set built from another would draw a tab for a type the set could never hold, and
nothing would object. Taking them from the set makes that unsayable.

The list is worked out once per tab and kept until the set says it has changed. A sort key can
be worked out from a whole profile, so listing on every keystroke would walk the logbook to
redraw one row. Nothing announces a change — `DATA-6` — so the set carries a number that moves
when an item is added or taken out, and the list is kept against that. A field edited does not
move it, so a list whose order turns on an edited field is stale until the tab is left and come
back to; closing that properly is `TUI-3`'s to do.

**The bottom row says what the keys do**, and says what they do *here*: the list and an open
field answer to different things, so a reader is told the ones in front of them rather than
all of them. Where the screen is too narrow it stops at the first that will not fit, rather
than passing over it for a shorter one — a bar that keeps its order is one whose front a
reader learns.

**What it keeps is the order of what a reader could not have worked out.** Leaving leads the
list's bar, then the keys that change something, then the ones that go somewhere: `[q] quit`,
`[n] new`, `[del] delete`, `[enter]`, `[space] follow`, and the movement last. Anybody presses
an arrow without being told to and nobody presses `n`, so the hints worth the width are the
ones for keys nothing else would suggest. Inside a field the same order runs without the first
of them, `q` not working there.

The order used to run the other way, movement first, which was harmless while every key was a
movement and became a fault the moment two of them changed the logbook: an eighty-column
console — what Windows opens on — showed the arrows and neither `n` nor delete, so the two
keys that do the most were the two nothing mentioned. Eighty columns now carries both, and
what falls off there is `[<,>] item` and `[^,v] field`. The cost is real and is the smaller
one: the item list answering to left and right is odd enough to be worth saying, and it is
still found by pressing left.

**The bar draws the arrow keys and spells the rest** — `[<,>]`, `[^,v]`, against `[esc]`,
`[enter]`, `[space]` and `[(shift)-tab]`. Each is what its own key cap shows. Drawn in ASCII
because no arrow character survives a console on code page 437, which is what this one opens
on: `←` shows as `?` there, and so does `◀`. That code page does hold arrow glyphs, at
0x18 to 0x1b, but those are the control-code positions and nothing encodes to them. Drawing
them also buys back the width that spelling them out would cost, which is what lets an
eighty-column bar reach `[space] follow`.

| Key | Does |
|---|---|
| tab, shift-tab | change tab, round the ring — the types, or the keys of an open field |
| left, right | move through the list of items, round the ring |
| up, down | move through whatever fields are in front of you |
| space | open the item the chosen field names |
| enter | go one step further in |
| escape | come one step back out, and nothing where there is nothing to |
| `n` | make one of what is in front of you — an item, an entry, an owned item |
| `+` | open the import screen |
| insert | take the arriving item the cursor is on into the logbook |
| delete | take out what the cursor is on, asking first where it is an item |
| `q` | leave, where nothing is open |
| ctrl-C | leave, from wherever you are and whatever you are typing |

While a value is being typed the map is a shorter one, every printable character being a
character:

| Key | Does |
|---|---|
| enter | save what was typed, or take the suggestion the cursor is on |
| escape | abandon the edit and leave the value as it was |
| backspace | rub out the last character |
| up, down | move over the suggestions a reference offers |

And while one is being chosen, shorter still:

| Key | Does |
|---|---|
| up, down | move over the choices, round the ends |
| enter | take the one the cursor is on, or start typing on *other* |
| escape | leave the value as it was |

**Nothing else moves while either is open.** A key that changed which field was chosen would
leave the editor saving into somewhere the reader is no longer looking at, so the tab, the item
list and the field cursor are all still until the edit is finished or abandoned.

**What a printable character means is settled by the screen, not by the keyboard.** `keyOf`
reports a character as itself and gives a meaning to nothing but ctrl-C, which has no other. That
is what `q` and space being ordinary letters costs and buys: the screen knows whether an editor
is open and the keyboard cannot, so `TUI-6`'s *once enter opens an editor, `q` is a character
somebody is typing* is answerable in one place rather than two.

**Each key does one job and does it everywhere.** Tab is for tabs, whether those are the
types or the keys of an open field; the arrows are for what sits under them. That is what
decided the map: the alternative gave a key two meanings depending on which half of the
screen was live, which meant the screen had to say which half that was, and a reader had to
read it before every press.

**Escape is held to that too, which is why it does not leave.** It used to, where nothing was
open, and that put ending the session one key past the mildest thing a reader does. Escape
comes back out, one level at a time, and what it comes out to is the list. `TUI-6`.

**Leaving is ctrl-C from anywhere, and `q` from the list.** `q` used to work at any depth, and
it stopped because a reader four levels into a keyed field is not looking for the way out of
the application — they are looking for the way out of the field, which is escape. Being able to
end the session from there bought nothing and spent a letter. Ctrl-C is untouched and works
from inside an editor, where every printable key is a character.

The bar says `[q] quit` on the list and nothing about leaving anywhere else. Ctrl-C is left
unsaid deliberately: it is the one key nobody has to be told about, and the bars where it is the
only way out have three keys to explain and no width to spare.

The cost is that the item list answers to left and right rather than to up and down, which
reads oddly against a column of ids. It was taken knowingly: an odd direction is learnt once,
and a key whose meaning moves is read every time.

## An import

**`+` opens the import screen**, which offers the two an import can come from: a path, or a dive
computer. Escape comes back out.

**A dive computer opens onto what is attached**, one row apiece, and enter reads the one the
cursor is on. Where nothing answers — nothing plugged in, or a platform with no way to look — it
says so and stays where it is. What is read is only what this logbook has not seen: the newest
recording each computer made carries the token the device knows it by, and handing that back
means the earlier dives are never transferred. `DATA-90`.

**The interface stops while a download runs.** It takes minutes on a full computer, and nothing
here says so or offers to give up. That is what this screen owes next: the port hands dives over
as they are read, and nothing yet paints between them.

**One prompt takes one path, and what is there says how it is read.** A folder is another Yemoja
logbook and a file is a UDDF document. Asking which would be putting a question to somebody who
already knows what they are pointing at, and the answer is on the disk. Nothing after that
differs: both arrive as a set of items and both are reviewed the same way.

**What arrives is not a screen of its own.** The incoming items are listed in the tab their type
would put them in, above the ones already there and set in italic, so what is arriving is read
where it will end up and against what is already there. There is nothing to learn: the tabs, the
arrows, the fields and the editor are the ones already in use.

**Italic means the same thing here as on a value**: this logbook does not hold it as written. A
worked-out value is not stored and an arriving item is not held, and neither is a claim about
what is on disk.

**Insert asks what the item is, where nothing could be matched by id.** Two answers: *as new*,
or *the same as* one already held, chosen from a list of them. It starts on whichever the layer
below proposes — one overlapping in time, or one of the same name — and on *as new* where it
proposes none. A proposal rather than a decision, so being over-eager costs a keystroke.
[../../logic/reconciliation.md](../../logic/reconciliation.md) has the two rules.

Where the ids were carried, as they are between two Yemoja logbooks, nothing is asked: the id
already says which item it is. Nor is anything asked where there is nothing it could be confused
with.

**Insert takes the item in; delete leaves it out.** Either way it goes from the list, so what is
still in italic is what has not been decided. Delete asks twice, as it does for any item — what
is staged may have been corrected by hand, and that correction is nowhere else. Insert does not
ask: taking something in is what an import is for, and it can be deleted afterwards.

An item that cannot go in stays where it is and the bar says why. That is the one place a
refusal survives a keystroke, because the thing refused is still on the screen and the reader
has to be able to fix it and try again.

**An arriving item is edited where it arrived.** Correcting a downloaded dive before taking it in
writes to the staged logbook, not to this one, so nothing is touched until insert is pressed. It
is edited by the same keys as anything else, because it is an ordinary item in an ordinary
logbook — which is the whole reason there is no separate review screen.
[../../logic/reconciliation.md](../../logic/reconciliation.md) has the rest.

**Choosing goes round the ends; scrolling stops at them.** The tabs, the item list, the
fields and the values of an open field all wrap, so the far end of any of them is one key
away. Scrolling does not: a value too long to see is one thing being looked through rather
than several being picked between, and running off the bottom of it back to the top would
hide that there was no more to read.

The item list stopped at its ends until `TUI-7`, on the grounds that wrapping loses the
user's place on a long one. What that overlooked is that the key is the same key: four
things move under the arrows, and three of them wrapping made the fourth read as broken
rather than as careful. Leaving takes two keys of its own because raw mode swallows the usual
one, and `[q] quit` leads the list's bar: the key a reader wants without hunting for it is the
one that gets them out, so it is the one thing a screen too narrow for anything else still
says.

**Input ending is leaving too.** A closed terminal or a pipe running out is a session over
rather than a fault, so the loop asks Mordant for a key *or null* and stops on the null. Its
other reader throws instead, which would have put a stack trace where a clean exit belongs.

**Up and down move through whatever fields are in front of you**: the chosen item's, or the
fields of an item inside an open field, or the values of an open field that holds several, or
the rows themselves where an open field holds one value longer than the screen. A field holds
one or the other and never both, so one key does all of them without ever being ambiguous —
and a list of 75 regions, which `children` will bring, needs a cursor rather than a page of
bullets nobody can point at.

**The layout is the descriptions and nothing else.** Nothing in this front end names a type
or a field, so a type added to the logic layer appears here without this changing. `UI-3`.

Three things it shows that a prettier interface would hide. A field with nothing in it is
still listed, because what a type *can* hold is half of what this is for. A value that could
not be read shows why — `! north should be within -90.0..90.0 deg, but was 91.0 deg` — rather
than a blank, which is what an absent field looks like and is not the same thing. And a value
is shown in the form a file writes it, `DATA-76`: a date as `2026-02-23`, a reference with its
`@`, a mix as `EAN32`.

**That is the written form, not the written file.** Two things on screen were never on disk
and are not meant to be. A worked-out value is shown like any other, in italic, because what
a type holds is the point here and half of it is worked out. And a measurement is shown in
the default unit whatever its file declared, reading having converted it — a dive written in
feet is read as metres and shown as metres. Whether a front end should show a value back in
the unit its file used is `UI-2`'s to answer, units being a setting rather than a fact about
the item.

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

## Going into an item

Enter opens the chosen field alone, and goes on opening from there. The top line is the way in
— `dive / 2025-05-30#2 / profiles / p1 / pressures / g1` — so a reader who has gone four steps
down knows where they are, and escape brings them back up one at a time.

**A field holding several is a tab apiece**, the open one in brackets, with what is under it
below — the same row of names the types get, and the same key moving along it, because it is
the same thing: several of a kind, one of them open. Tab is left alone where an open field has
no keys, there being nothing for it to move between. Opening such a field arrives at the first
key rather than at a list of them, and **a key is not a stop of its own**: coming back out of
one goes to the field that held it.

**Up and down move over whatever is in front of you, and the bar names it.** Inside an item
that is its fields; at a list it is the values; and at a single value there is nothing to move
between, so they scroll instead.

The cursor lands on what was just left rather than at the top, the way each tab keeps the row
it was on: coming out of something is not the same as arriving somewhere.

**Only one level is shown at a time here.** A reader at an item is choosing which field to go
into, and the rows of everything inside would be rows they cannot choose. Where the whole of an
item is wanted at once, that is the column beside the list.

A field holding values says where it came from, then what the field *is*, then the whole of
what it holds.

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

What it holds is the value uncut, wrapped over as many rows as it takes. A field holding an
item shows what is in it rather than the value it has none of, without naming the field a
second time — the heading above has done that.

 **How it came to hold
it goes in brackets beside that heading** — `What it holds (derived)` — rather than on a row
of its own, since it is one word about the whole of what follows and a reader would otherwise
count it among the values. A field holding nothing says so there and adds nothing under it. A
value that could not be read shows both the reason and what was written, the two together being
what a reader needs in order to fix it.

**Enter edits what it cannot open.** A value is as far in as a reader goes, so a second press
there starts typing rather than doing nothing. Editing is described below.

**A field holding several values** shows them on the row separated by commas, and where they
do not fit, as many as do followed by how many were left out — `@world ... (8 others)`. Saying
how many is what a plain cut cannot: three dots at the end of a list of regions could mean one
more or forty, and which it is decides whether opening the field is worth it. A list somebody
wrote with nothing in it says `(empty)` on the row, since that is not the same as a field nobody
wrote. Opened, the two look alike — one empty place either way — and it is the heading that
tells them apart, `written` against `nothing`.

**A field holding a series** says how many samples it has and no more — `1000 samples`. A
profile holds thousands, and the first two of them say nothing a reader wants from a row.
Opened, it is one row per sample: when it was taken and what was read, the times lined up on
the right so the values stand in a column, a profile being read down rather than across. The
time is seconds from the start of the recording, which is what the file holds whatever else it
declares — `DATA-58`.

There is no cursor over a series and up and down scroll it, there being nothing in one to
follow.

Opened, a list is a bullet apiece and nothing is left out, which is what makes it possible to
see where one value ends and the next begins. One bullet is set apart, the same way the chosen
field is on the row, and up and down move between them.

**A list of n values has n+1 places, and up and down move over all of them.** A value goes
between two others, before the first or after the last, so the places are the gaps rather than
the values, and there is always one more gap than there are values. The last of them is a row of
its own under the bullets, carrying none itself: there is nothing there yet, and a bullet with
nothing after it reads as a value somebody left blank.

That is what makes an empty list something a reader can use. It has exactly one place, and so
does a list nobody ever wrote, so enter there types the first value in. Before this the open
view walked the values it had, which for an empty list was none: enter opened an editor over a
value that did not exist, and what was typed into it was dropped without a word when it was
saved.

**`n` puts a new value at the place the cursor is on**, pushing what was there down, so a buddy
can go between two others rather than only after them all. At the last place it does what enter
does — there is nothing there to push — and the bar says only enter there, two names for one key
being worse than one. Delete takes out the value the cursor is on and does nothing at a place
that holds none, which the bar says by offering nothing.

**Underlining is finer here than on the row.** The row has one piece of text for the whole
field, so it underlines a field that names items. Open, each value stands alone, so what is
underlined is each value that names one — a plain name asserting no id is not, and neither is
an entry that would not read, because neither opens anything and the underline is the promise
that something will.

**Space opens the item a field names, and does not come back.** On the row that is the first
one that can be opened,
which is the one the row is showing — so a region with one parent needs no drilling in. In an
open field it is the value the cursor is on, which is how one of several is chosen. Following
closes the open field, the reader having arrived somewhere else.

**A mention written in a remark is followed the same way.** `JSON-23` makes `@willy` in free
text name an item by convention, and this is the first interface to act on one. Opened, a
remark's mentions are what up and down move over — the same cursor a list's values get,
because they are the same thing: several followable things in one open field — and space
follows the one it is on.

**Only what resolves is marked.** `JSON-23` asks that of a reader and gives the reason: an
interface that underlined every candidate would light up each address and each `@media` in the
logbook. So `@example.invalid` inside an address stays the text it is, is not underlined, and is
not a stop the cursor moves over. A remark with nothing to follow has nothing to move between,
so up and down scroll it as they always did.

The mention is marked where it sits in the sentence rather than listed away from it, which is
what makes it obvious which `@willy` is meant when a remark holds two. Underlined for what it
is, and reversed for the one the cursor is on, exactly as a chosen bullet is.

Not on the row beside the list. That row shows a cut, escaped line, and there is nowhere in it
to put a cursor.

Nothing records where a reader came from, and nothing will: every item is a tab and a few rows
away, and each tab remembers the row it was on. `TUI-5`.

A plain name asserting no id is not followed, there being nothing to open, and neither is a
value that would not read. Both still count as a value the cursor moves over: it moves over
what a reader sees, not over what happens to be followable.

**A painted line holds no line break.** `remarks` is the one multiline field and a terminal row
is not multiline, so on the row beside the list a break is shown as `\n`, the escape a file
writes it with. Taken instead, one remark would occupy three rows while measuring as one, and in
raw mode leave the cursor wherever the last of them ended. A remark longer than the column is cut
there.

**Opened, the breaks are taken**, one row per line, each set in by the same two spaces so the
value stays in one column. That does not contradict the rule above, which is about a painted
line: three written lines become three painted lines rather than one holding a break. The open
field is where a value is shown whole, and a remark written as three lines that arrives as one
run of text with `\n` in it has been shown in part.

A line still too wide for the screen carries on below, and the rows after the first are set in as
far as the line itself is, so a value that wraps stays in its own column rather than running back
to the edge.

**A row is cut span by span**, so a mention or a reference straddling the break keeps its
underline on both halves. Painting each row with the first span's styles was easier and lost the
mark off anything long enough to need a second row — and the first span of a value row is the
indent, which is styled with nothing at all.

Both go through one function, and it flattens whatever it is given whether or not the text fits.
Measuring the escaped form and painting the unescaped one is exactly the bug that was there: a
break short enough to fit was painted as a break, the terminal took it, and the rest of the value
landed at column 0 outside the rectangle. Nothing reaches that function holding a break now — a
remark is broken into rows before it, and every other text is refused one by the layer below —
but the flattening is what makes a painted line one row, and that should not rest on who calls
it.

The supplied libraries load, so a logbook declaring them shows them under its own items —
they travel inside the application and need no second argument, `LIB-6`.

**An item inside an item is indented under the name of the field holding it**, and each of its
fields is a row that can be chosen like any other — up and down walk all of them, at whatever
depth they sit. Values line up in one column however deep their names sit, which is what lets an eye
run down them, and only the chosen row is set apart, not the rows under it: a whole item
reversed is a wall rather than a cursor.

**A field holding several says its keys and no more** — `Profiles  p1, p2`, through the same
cut and count a list gets. What is under them is unbounded: a dive with three profiles of
twenty fields would be sixty rows of somebody else's business, so they are shown whole where a
reader asks for that field rather than in front of one who did not.

That is the line between the two: **what a field holds is expanded where it is bounded and
summarised where it is not.** One item is a handful of fields and always the same handful; a
list, a series and a set of keys are however many somebody wrote.

The column scrolls, since an item holding items is easily taller than a screen. Up and down
move it, keeping the chosen row in view.

Every shape the model has is reachable now that a dive is described: its profiles under keys,
a profile's depth against time, and its pressures one series per gas source, which is the only
keyed series there is.

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

## Changing what is there

**Enter opens until there is nothing further in, and then it edits.** A field holding an item
is opened into; a value is where the path stops, so enter there seeds an editor with what the
field holds and every printable key types into it. Enter saves, escape abandons, backspace rubs
out.

A field that holds an item is never typed into, and that includes one holding none: what an
editor reaches is that item's own fields, one at a time. An empty one is made with `n`.

Seeded with the written form — `2026-02-23`, `@willy`, `EAN32` — which is the form the row
already shows and the form reading it back accepts. `DATA-76`. So correcting a date is editing
a date rather than typing one from nothing. A value that would not read is offered exactly as
the file holds it, which is the one thing a reader wants to see when fixing a typo.

**Saving nothing clears the field.** An empty editor is how a field is emptied, there being no
second key for it, and it is what a reader would try. On a field that would otherwise be worked
out, clearing the correction gives the worked-out value back — a duration typed over the times
that imply it goes back to following them. On one entry of a list it takes that entry out, a
list having no room for a value that is not there.

**A value that will not do says so under it, as it is typed**, in the words the layer below
refuses it with, and enter does not take it: the editor stays open with the text still in it.
The check is the same door the save goes through, so the screen cannot approve something the
Universe will not have.

**A line break is typed as the two characters a row shows it as.** Enter saves, so it cannot
also make a break in a remark, and `\n` is already how a break is painted on a row. Typing
those two characters makes one.

**A field whose values are known is chosen from rather than typed into.** Enter offers them one
per row with the cursor on what the field holds; up and down move over them and enter takes one.
Typing `salt` into a field that accepts four words is spelling out what the application already
knows, and getting it wrong is a refusal a reader did not need to meet.

Three kinds have known values. A **fixed set** — `DATA-24`, a value outside it reads back
unusable — is a list of its words. A **boolean** is the same list with a box on each row, since
two values that exclude each other is what a box says; a set of words gets none, a column of
boxes down a vocabulary of six saying nothing the cursor has not.

A **suggested set** is offered the same way and carries one row more: *other:*, which opens an
editor rather than saving. `DATA-25` offers those values without enforcing them, so a chooser
that could not leave them would refuse what the field takes. Enter there types, enter again
saves, and escape comes back to the choices rather than out of them — a step in is undone by a
step out, and picking *other* was a step in.

A value the suggestions do not offer sits on that row, shown there — `other: archipelago` — and
that is where the cursor starts. Nothing else on the screen would say where the value had gone.

**The last row is the field holding nothing.** Absent is a state every field has, and an empty
box is how a typed field is cleared; a chooser has no empty box to leave, so it has a row. It is
also where the cursor starts when the field holds nothing, and where a value outside a *fixed*
set leaves it — sitting on *none of these* is how it says so, there being no *other* row to
hold it.

The rows are in the order the type declares them, not sorted. `none, light, moderate, strong` is
an order somebody chose and alphabetical would be `light, moderate, none, strong`. The same order
is used where the open field says `one of` and `usually`, and in the refusal a fixed set gives —
one vocabulary written one way wherever it appears.

The values are the written form, `DATA-76`, which is why a boolean reads `true` and `false`
rather than yes and no: what is picked here is what the row shows and what the file holds.

**The suggestions are the shipped presets joined with what the logbook already uses**, which the
Universe works out — `DATA-25`. A word written once through the *other* row is a row of its own
from then on, so it is typed once rather than once per item, and a second spelling of it is
visible beside the first rather than hidden. The presets keep their declared order and what the
logbook adds follows, alphabetically, there being no order of its own to keep.

That is also why the *other* row rarely shows a value: what an item holds is in use by
definition, so it is offered. What lands there is a value that could not be read — `"category":
5` — which nothing offers and which the row is then the only place to see.

**A reference offers the items it could name, and narrows them as they are typed.** They sit
under what is being typed, in the order that type's own tab lists it in — `DATA-89` — and up and
down move onto them. Enter takes the one the cursor is on. The typed text is a stop in the same
ring, so moving past the last suggestion arrives back at it rather than sticking somewhere a
reader cannot type from.

**A suggestion is an offer, not a list to pick from.** A reference that allows a plain name takes
one — `@willy` is an id and *Someone Else* is a name somebody wrote — so enter on the typed text
saves the typed text, whether or not anything answers to it. That is also why a reference is not
a chooser: a logbook holds thousands of dives, and what a reader needs is to type three letters
and see the four that match, not to scroll a list of everything.

Matched anywhere in the id rather than at its front, since a dive's id opens with its date and
somebody looking for one is as likely to remember the rest of it. The `@` is how a reference is
written rather than part of the id, so it is not matched on.

**Eight at a time**, with how many were left out said under them — `(4 others)`. Enough to
recognise the one wanted and few enough to leave the typed line on a short screen; what is past
them is reached by typing more. Saying how many is what a column that simply stopped cannot: it
would read as the whole of what there is.

The suggestions come from the item set, so they are the items that exist rather than the ids that
have been used. A reference naming something deleted is not offered, and one naming something
never entered is still saveable.

**`n` makes one of whatever is in front of the reader.** An item of the open type where the
list is shown, an entry at the end of an open list, an entry under a new key in a keyed
collection, and the owned item a field holds where it holds none. One key for one job wherever
it is done, which is the rule the rest of the map follows.

What it makes is empty, and empty is what an id and a key are then proposed from: a person made
here is `unknown_person` and a course is `course`, both `#1` and up where those are taken. The
name is not revised when the fields are filled in — an id given out is a thing other items point
at. `JSON-18`, `DATA-84`.

**Delete takes out what the cursor is on, and asks first where that is an item.** Nothing can
be undone until there is a journal — `FEAT-4` — and a dive holds a recording nobody can type
again, so removing one takes two presses of the same key and any other key answers no. An entry
of a list or of a collection does not ask: it is one value, and putting it back is typing it.

Deleting an item leaves references to it alone, which is `Change.Delete`'s default and the
logic layer's decision rather than this one's. A reference to an item that is gone shows as
what it is.

**The bar offers both keys by the name of what they would act on** — `[n] new medical`,
`[del] delete entry` — and offers neither where neither would do anything. It is one question
asked once: the key and the bar work from the same answer about where the reader is standing, so
the bar cannot offer what the key would refuse. The question it asks is *what am I standing on*,
and a key is not a stop of its own there either — a reader inside an entry of a collection is
standing on the collection, which is what makes `n` mean another entry.

Both sit near the front of the bar, behind `[q] quit` and ahead of everything that only moves
the cursor, so an eighty-column console says them. That is the ordering rule above at work: a
reader finds the arrows without being told and finds `n` only by being told.

**The confirmation replaces the bar and names the item** — `delete anna? | [del] delete |
[any key] keep`. Naming it is not decoration: the list shows whatever follows under the cursor
once the item is gone, and a reader who guessed wrong would find out afterwards.

**Wrapping past the end of a list stays as it is.** `TUI-7` left this open, on the grounds that
going round the ends is cheap while every key is a movement and not obviously cheap once a key
can change something. It turns out to cost nothing: the keys that change something are `n`,
delete and enter, and none of them is a movement, so wrapping cannot carry a reader into a
change they did not ask for. What it can do is land the cursor on a different item than the one
they thought — which is why deleting one names it.

**Nothing here decides what may be written.** A worked-out field refuses an editor because its
description says so, not because this front end knows which fields those are. `TUI-3`.

## Open questions

One is open. The rest are settled and kept here, since a decision not to relitigate is worth as
much as one still to make.

- **TUI-8 — What a job that takes minutes looks like.** *Open.* Reading a dive computer is the
  first thing this interface does that does not finish between one keystroke and the next. It
  can take minutes on a full computer, and at present the screen simply stops: nothing says what
  is happening, nothing says how far along it is, and nothing offers to give up. Choosing *a dive
  computer* on the import screen now stops for four seconds before the list appears, which is
  the Bluetooth scan listening; that is the same silence, shorter. One thing does speak during
  a download: a computer that shows a code has it asked for on the bottom line, typed and
  entered, or given up with escape. `LOGIC-24`. That is the first question put mid-download,
  and it is put by the terminal rather than by the screen, which cannot read a line while the
  download holds it.

  The pieces are there and none of them is wired. The port hands dives over one at a time, so
  progress is knowable. `dc_device_set_cancel` exists, so giving up is possible. What is missing
  is a shape for it — whether the interface keeps painting while something else reads, which
  means a thread and everything a thread brings, or whether a screen that says *reading, 12 of
  40, [esc] to stop* between dives is enough. The second needs no concurrency and would not
  answer a device that hangs.

  It bears on `LOGIC-21`, which asks where a download's report of what it dropped goes: both are
  what a download has to say for itself.

- **TUI-1 — Full-screen interactive or a command-driven REPL?** *Settled:* full-screen
  interactive. The tabs, the list and the cursor keys are what was asked for, and a REPL
  would be less code but would not show a whole item at once, which is the thing this exists
  to do.
- **TUI-5 — Whether following a reference can be undone.** *Settled:* **no**, and nothing
  records where a reader came from.

  A stack would be state kept for a journey nobody has to retrace. There is no hierarchy to be
  lost in: every item in the logbook is a tab and a few rows away, and the row a reader was on
  is still chosen when they come back to its tab, because each tab remembers its own. What a
  back key would buy over that is one keystroke, against a second meaning for escape, which
  `TUI-6` has just finished reducing to one.

  It also keeps the interface honest about what it is. A reader following references is
  reading, not navigating a history, and the thing they were looking at has not gone anywhere.

- **TUI-6 — Whether escape leaves the interface.** *Settled:* **no.** Escape comes one step
  back out and does nothing where there is nothing to come out of. Leaving is ctrl-C from
  anywhere and `q` from the list.

  It used to leave where nothing was open, which put ending the session one key past the
  mildest thing a reader does: escape out of one level too many and the application is gone.
  A step back should not be a thing to be careful with.

  Nothing is lost, because nothing was gained. Quitting was already available at any depth —
  what was missing was the bar saying so, which is why escape looked like the only way out and
  so had to be given a second job. `[q] quit` now leads the list's bar and the problem
  dissolves.

  `q` was later confined to the list, which does not reopen this. Escape still comes out one
  level at a time and still never leaves; what changed is that the reader has to be out before
  the letter means anything, and ctrl-C is what leaves from where they are.

  The alternatives were a confirmation at the top level, and a double press. Both rehabilitate
  a meaning worth dropping, and both add state to do it: one a prompt, the other a timeout or
  a nag line.

  Editing tested this and did not change it. Once enter opens an editor `q` is a character
  somebody is typing and escape means *cancel this edit*, so escape has two jobs — but they are
  the same job at two depths: it undoes the last step in, whether that step was opening a field
  or opening an editor. Ctrl-C survives intact and is the way out from inside an editor, which
  is the whole reason it is carried.

- **TUI-7 — Whether the item list wraps.** *Settled:* **yes**, and so do the values of an
  open field. Everything a reader chooses between now goes round its ends: the tabs, the item
  list, the fields, and the values inside a field. Scrolling a value too long to see is the
  one thing that still stops, because it is one thing being looked through rather than several
  being picked between.

  The list used to stop, on the argument that wrapping loses the user's place on a long one —
  a logbook holds thousands of dives, and a jump from the last to the first is not a step. That
  argument still holds on its own terms and was not enough, because the reader does not meet
  four lists. They meet two arrow keys, and a key that goes round three times out of four and
  stops the fourth reads as a fault rather than as a kindness.

  The line that would have been drawn instead is between what the type bounds and what the
  data bounds: ten tabs and forty-odd fields against a logbook's worth of items. It is a real
  line and it is invisible from the keyboard, which is where the decision is felt.

  What the old rule was protecting turned out to cost nothing once editing arrived. The keys
  that change something — `n`, delete and enter — are none of them movements, so wrapping cannot
  carry a reader into a change they did not ask for. It can leave the cursor on an item they did
  not expect, which is why deleting one names it and asks.

- **TUI-2 — Whether it is shipped to users or stays a development tool.** *Settled:*
  **shipped**, as a power tool. It reads and writes now, so the reason to hold it back is
  gone; what remains is the application it would ship with. It is built as something a user
  will meet rather than as a workbench that happens to run.

  That decides arguments rather than features. Input validation and error recovery are held
  to the standard the rest of the application is, not to what a developer will put up with,
  and a message reaching the screen is a message a user reads.

  The raw look stays. Raw is what a power tool is for, and *no polish* was never the same
  claim as *no care*. Nor does this make the TUI the GUI in a terminal: what is out of scope
  above stays out, and `TUI-3` is still open about what it may edit.
- **TUI-3 — Whether it can edit fields the GUI cannot.** *Settled:* **no, and the question
  turns out not to arise.**

  It was written against a real risk: data written in one front end that another cannot show
  or correct, leaving a logbook only one interface can maintain. What closes it is a decision
  the GUI had already taken. `ui/gui/doc.md` splits that interface in two — the item view
  presents and never changes anything, the edit view is *"every field the item has, laid out
  to be filled in rather than admired"*. So the GUI hides fields from **reading**, which
  `GUI-16` leaves to each item type, and from writing it hides none.

  Both front ends therefore reach every field, and this one keeps its purpose — getting at
  everything without waiting for a screen to be grown for it — without ever holding a licence
  the other lacks.

  **What may not be written is a property of the field, not of the interface.** A `Role.Derived`
  field refuses a written value wherever it is offered: a dive's `name` is the id, and writing
  one would be renaming the dive, which belongs to the Universe with its references. No front
  end can get that wrong, because none of them decides it.

  The limit that does exist runs the other way. A series is thousands of samples, and nothing
  will type one into a terminal — so there are shapes this interface cannot practically edit
  and the GUI can. That is a gap in this front end rather than a hazard from it, and a value it
  cannot edit it can still show.
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
