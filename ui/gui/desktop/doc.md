# GUI — desktop

The desktop form factor: large screen, keyboard and mouse, resizable window,
multiple things visible at once.

Shared rules live in [../doc.md](../doc.md), including the view structure and field
definitions that both form factors share — this document covers only how that
structure is placed on a desktop screen, and every settled decision about that placement is
here rather than there. Platform specifics live one level down:
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

## Settled

- **DESK-1 — How many windows.** *Settled, as built:* **one.** The tabs across the top are the
  navigation, a chosen item opens on the right of its tab, and the agent's panel opens beside the
  tabs rather than in a window of its own, `GUI-38`. A dive cannot be opened beside the list in a
  second window; following a link moves to the tab that holds it.
- **DESK-2 — The menu bar.** *Settled, as built:* **there is none.** Everything lives in the
  application's own controls — the deeds on the home screen, the buttons on a card — so the window
  behaves the same on every desktop rather than differing where a platform expects a menu.
- **DESK-3 — Density.** *Settled, as built:* **one density, and no compact mode.** A long logbook
  is handled by the table folding by year and the tree folding by region rather than by drawing
  everything smaller.
- **DESK-4 — Whether editing opens a window.** *Settled, as built:* **never.** A card turns over
  into its form in place, `GUI-29`, and a dialog is used only to confirm a deletion or to pick a
  folder or a file.

Each of these places something the shared register decides. The decision is named beside it, and
what it means is argued there; what is here is where it goes on a large screen.

- **DESK-5 — How a desktop lays an item out.** *Settled:* **the plain fields flow into two
  columns, in the type's order.** An owned item is set into a box of its own, titled, and shown in
  full the same way, boxes nesting where an owned item owns one. A keyed owned item is such a box
  with a tab per entry, the tabs small buttons on the title's own line, each as wide as its name,
  the first open; a tab is called by the entry's name where it has one, else by what the first
  reference on it points at — a recording by the computer that made it, a course by its
  certification — and by its key only where nothing on it says anything. A series is not laid out
  at all: the graph is where it is read. Which fields are shown at all is `GUI-16`.

  **A list of dives on gear, a person, a dive site or an operator sits at the foot**, in a box of
  its own as wide as the card, after every owned item. It runs to hundreds of names, and in one of
  two columns it was a narrow ribbon that pushed every field after it off the screen. The box is
  left out where the list is empty. The form keeps the field among the rest, nobody typing into it.

- **DESK-6 — How a desktop draws a section.** *Settled:* **a section in the flow gets its label
  over its fields; a section drawn as a box gets the frame an owned item gets.**

  The fields of a type flow two to a row, `DESK-5`, and a section is a run of them under a heading
  rather than a new kind of thing. A section marked *box* is drawn exactly as an owned item is, so
  a dive's *Details* looks as it did before `DATA-122` moved its three fields onto the dive. What
  a section is, and that it is never stored, is `DATA-121`. Relocated from `GUI-46`.

  **The edit form ignores sections for now.** It has its own arrangement — what follows from a
  category comes forward, the rest folds away, `GUI-29` — and a grouping laid over that is a
  second thing deciding where a field sits. A section there is worth doing once the form is next
  opened up.

- **DESK-8 — The planner's frame.** *Settled:* **the top half is two columns, the runtime on the
  left, the settings, the contingency and the gases on the right, each in a box of its own.** Below
  them come the whole dive's clocks on one line, what the model objects to, and the graph. What a
  plan holds and how it is worked out is `GUI-43`.

  **The start and the run followed are a row of their own**, under the heading and the save row
  and above the two columns, with what they come to after them on the same line. They belong to
  the dive rather than to one of its boxes, and the settings box is already as tall as the gases
  can spare.

  **The top is a fixed height, and the runtime scrolls past it**, with a bar where the platform
  draws one. The boxes beside it stay where they are however long the dive is. The gases take what
  the two boxes above them leave of the same height, so the two columns end on one line, and they
  scroll in the same way once there are more than fit, their headings staying above them. The lines
  of all three parts are dense, in smaller type than a form's, so about eighteen lines of the
  runtime and about four cylinders show before either scrolls.

  **Every box, column and figure says what it is when pointed at.** The lines are too dense for a
  label beside each box, and the runtime has no headings at all, so a tooltip stands in for them: a
  phrase naming the field as a label would, then what it does to the plan where the name leaves
  that unclear. The words live together in `PlannerTips`, the one place to read them over. A cross
  on a gas that a line breathes says why it cannot be taken out instead. A form factor with no
  pointer will need another answer, this one being a hover.

- **DESK-9 — Where the agent panel sits.** *Settled:* **beside whichever tab is showing, at a
  fixed width, opened from a button at the right of the tab row.** What the panel is for, what it
  refuses and how it stages are `GUI-38`.

  A question comes up wherever the reader is, and a deed on home would send them there first. The
  button shows a sparkle, the mark readers have come to know for an AI feature, and says *Ask an
  agent* over itself while the pointer rests there: an icon keeps the row the tabs' own, and the
  name is one hover away rather than lost. The same button shuts the panel.

  **What it holds, top to bottom**: a title naming the agent the command names, with *Stop* beside
  it; the conversation, a view of its own for copying; what the stance says while an agent is
  starting or thinking; how much is staged and the deed to review it; and what to ask, with the two
  boxes under each other to the left of *Ask*, a box to a line. A table of before and
  after wants the window's width, which is why a review is a screen rather than a panel.

- **DESK-10 — What a pointer does.** *Settled:* **a drag selects, a click chooses and clears the
  selection, and delete reddens under the pointer.**

  Which items may be selected together, and that a selection stays inside the view it began in, are
  `GUI-36`; that a deletion asks first is `GUI-35`. What is here is the input. A button red all the
  time is an alarm on a card that is read a hundred times and meant once; one that reddens as it is
  reached for says the same thing at the moment it matters. A form factor with no pointer keeps the
  question and needs another answer to the gesture.

- **DESK-11 — The edit form's frame.** *Settled:* **two columns and the insets with their tabs, as
  the item view has them, with *Save* and *Cancel* on the title line.**

  The title line, with its Save and Cancel, stays put while the fields scroll under
  it, so what is being looked at and the way out of it are never scrolled away. The text fields are
  the height of their text rather than the platform's fifty-six pixels: a form of twenty fields in
  tall boxes is one nobody scrolls to the end of. What a field is edited with, and what folds away, are `GUI-29`.

- **DESK-7 — How a dive trip is reached.** *Settled:* **by the column that names it.** The
  trip column holds one cell per trip, spanning the consecutive dives on it, and clicking that
  cell selects the trip where clicking anywhere else in the row selects the dive. So a trip is
  reached from any dive on it and needs no list of its own, and the column earns its width
  twice: it says which trip a dive was on, and it is the way in. Relocated from `GUI-19`.

  *Amended:* **the name wrapped down its run.** A trip's name is centred on its run and wraps
  onto as many lines as the run is tall, ending in an ellipsis where it needs more; a run of one
  dive holds one line. It rides on the run's last row and rises over the rows above,
  which are drawn before it. A single word wider than the column still breaks within itself.

- **DESK-10 — Which of Java the installer carries.** *Settled:* **the modules the code uses, and
  three it loads by name; not the whole runtime.** Decided on 2026-10-02, at the author's word.

  The installer carried every module of the runtime, 156 MB of a 271 MB installation, because a
  module left out fails only when the code needing it runs, and for Bluetooth that is halfway
  through a download. What the shipped jars use is what `jdeps` finds over every one of them:
  `java.base`, `java.desktop`, `java.instrument`, `java.logging`, `java.management` and
  `jdk.unsupported`. Three more are loaded by name and so invisible to it: `jdk.crypto.ec` for
  secure connections, `jdk.charsets` for text in other encodings, and `jdk.accessibility` for a
  screen reader. The runtime is 79 MB with them, and the whole test suite passes on it.

  **Locale data is left out**, 30 MB of it. Without it numbers are written the one way the code
  assumes, with a point, whatever the machine's language: the code writes `1.5` and then trims
  its zeros by looking for a point, which a comma would have defeated.

  A new library means running `jdeps --print-module-deps` again over `ui/build/install/yemoja/lib`.

- **DESK-11 — Where the icons the core set lacks come from.** *Settled:* **eleven are copied in,
  under their own licence; the extended set is not shipped.** Decided on 2026-10-02, at the
  author's word.

  The window uses twenty-one Material icons. Ten are in Compose's core set; the other eleven, a
  diver, a cylinder, a map and a book among them, were the only reason for the extended set, 37 MB
  of some two thousand icons. Shrinking the build with ProGuard would have dropped the rest without
  copying anything, but every library that loads a class by name, JNA, Bluetooth and the agent's
  protocol among them, would need a rule, and a missing one fails only when its feature runs.

  So the eleven are written out from the library's own drawing data into
  `ui/src/commonMain/kotlin/yemoja/ui/icons/`, which keeps Google's copyright and the Apache
  License beside it and is listed in `LICENSE` as not covered by the repository's own. A new icon
  is taken from the core set where it has one, and copied the same way where it does not.

## Open questions

