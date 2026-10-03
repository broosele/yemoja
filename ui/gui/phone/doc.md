# GUI — phone

The phone form factor: small screen, touch, one thing at a time, interruptions,
and a device that is often actually at the dive site.

Shared rules live in [../doc.md](../doc.md), including the view structure and field
definitions that both form factors share — this document covers only how that
structure is placed on a phone screen, and what a phone context demands of it.
Platform specifics live one level down: [android](android/doc.md) (second priority),
[iphone](iphone/doc.md).

## Scope

- Layouts for a narrow screen: one subject at a time, navigation between rather
  than beside.
- Touch as the primary input: target sizes, gestures, reachability, and text entry
  that is bearable on a phone keyboard.
- Behaviour under interruption: backgrounding, being killed, rotation, and
  resuming without losing work in progress.
- Battery and data awareness, particularly during dive computer downloads.
- Being usable on a boat: bright sunlight, wet or gloved hands, no connectivity.

## Not in scope

Anything specific to one mobile OS — that goes in that platform's own doc.

## Settled

- **PHONE-1 — Which features belong on a phone.** *Settled:* **all of them but the agent.** The
  screens are shared code, so every tab the desktop has, the planner included, comes along and
  what is left to do is layout. The agent panel does not: it starts a program on the machine,
  `API-4`, which Android does not let an app do.
- **PHONE-2 — How the desktop layout folds.** *Settled:* **the list, then the item.** The
  selector fills the screen; choosing opens the item full-screen, and back returns to the list.
  Fields stand in one column and insets one under another, and fields rarely used stay behind
  *more fields* as gear's already do. The dive table's trip column is 80 wide rather than the
  desktop's 170, giving the site the room, and a trip's name wraps down its run, `DESK-7`. Hover
  does not exist on a touch screen, so what a tooltip says over a greyed button is said on a long
  press, the platform's own gesture for it.

  **Manuals folds the same way.** The chapters fill the screen, a chapter or a section of one opens
  it at the screen's whole width, and back returns to the chapters. Side by side, as the desktop
  has them, the chapter was a column a word wide.

  **The planner's rows wrap rather than run off the screen**: the row a plan is saved from, whose
  later deeds could not be reached, the start and the dive it follows, and the figures under a
  plan. **Its runtime shares out the width there is.** Where a line does not fit with its gas and
  its two buttons, the four columns typed in are narrowed, and their units leave the boxes for a
  line over the columns, which also says what each column is where no pointer can rest on it. The
  contingency's scenarios go under its settings, there being no room beside them for what each
  came to. Tried at 411 and at 360 wide, the widths phones mostly have.

  **The planner's four parts stand in the order a plan is made**, settings, gases, runtime,
  contingency, and each folds away under its caption at a press, so that the one column need not
  hold all four at once. A box that holds a number asks the phone for its number keys.
- **PHONE-3 — Tablets.** *Settled:* **the desktop's layout.** A tablet is treated as a small
  desktop, side by side whichever way it is held. Choosing by width was the alternative, and
  costs a layout that changes under the reader when a tablet is turned.
- **PHONE-5 — The tab switcher.** *Settled:* **accessible, not permanently visible.** Seven
  tabs do not fit across the foot of a phone, and requiring them to would force the same
  compromise on the desktop, where there is ample room. Relocated from `GUI-13`.

## Open questions

- **PHONE-4 — Whether the phone is the primary dive computer download device**, since it is
   the one people carry. If so, Bluetooth reliability matters most here.
