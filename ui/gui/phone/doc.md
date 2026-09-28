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

- **PHONE-5 — The tab switcher.** *Settled:* **accessible, not permanently visible.** Seven
  tabs do not fit across the foot of a phone, and requiring them to would force the same
  compromise on the desktop, where there is ample room. Relocated from `GUI-13`.

## Open questions

- **PHONE-1 — Which features are phone-appropriate at all.** Dive planning on a phone screen
   is possible but may not be worth it; logging and downloading clearly are.
- **PHONE-2 — How aggressively to collapse.** Every field is reachable on a phone by
   decision, so the question is how much stays open by default — grouped detail is
   collapsed here and expanded on desktop, but an item with many groups is still a
   long screen.
- **PHONE-3 — Tablets** — same layouts as phone, as desktop, or their own?
- **PHONE-4 — Whether the phone is the primary dive computer download device**, since it is
   the one people carry. If so, Bluetooth reliability matters most here.
