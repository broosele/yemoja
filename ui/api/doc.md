# UI layer — API

A programmatic way to drive the [logic layer](../../logic/doc.md): manipulate the
logbook without a person present.

Not planned for now. This file records the intent so the logic layer is designed
with it in mind.

## Purpose

- Scripting and bulk edits that would be tedious by hand.
- Automation: scheduled imports, exports, backups.
- A test surface that exercises the logic layer the way a real client would.

## Scope

- The exposed operations and their shape.
- How data is represented on the way in and out.
- Errors, and how a caller distinguishes "invalid request" from "failed".

## Not in scope

Domain rules of any kind. This is a thin surface over the logic layer; if it grows
behaviour of its own, that behaviour is in the wrong place.

## Open questions

- **API-1 — What kind of interface?** An in-process library binding, a command-line tool,
   or a local server. These serve different users and are not mutually exclusive.
- **API-2 — Who is it for** — the project's own tooling, or third parties? That decides how
   stable the surface must be.
- **API-3 — Whether it may run against a logbook the GUI has open**, and what that means for
   concurrent access.
