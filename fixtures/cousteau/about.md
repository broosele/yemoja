# The cousteau fixture

A complete logbook: 22 dives, two of them not yet made, 11 people, 10 dive sites, 12 gear
items, 3 regions, 1 certification, 5 operators and 6 trips. All of it is invented except
two ships, said in the remarks of two sites, which are real — see *Real data, deliberately*
below.

It exists to be a *realistic whole* rather than a minimal case, so that anything reading
a logbook can be pointed at it. Narrower fixtures should be added beside it for
situations this one does not reach.

Its settings are in `settings.json`, beside `yemoja.json` rather than inside it. There
is no `settings.local.json`: that file belongs to an installation, not to a logbook,
and a fixture is neither.

`yemoja.json` names the libraries and the owner and nothing else: no paths, and no
list of where the items are. Which layout each kind uses is visible in the folder.

It uses both storage layouts: dives are one file each in `dive/`, everything else is
grouped into a file per type.

It uses the supplied libraries — world regions, PADI and CMAS certifications, generic
gear — and so holds very few regions or certifications of its own: only what its owner
added or corrected. That is what a real logbook looks like.

## What it deliberately exercises

Each of these is here because it is awkward, not because it is typical.

**Ids**

- `john_smith` and `john_smith#1` — two different people whose names propose the same
  id.
- Three dives on 2025-05-30, numbered `#0`, `#1`, `#2`.
- `pieter_bakker` overrides his derived `name`, because everyone calls him Piet. His is
  the only id here that deliberately disagrees with its name; everything else was
  aligned before the fixture was first used, since an id is fixed once assigned.

**Derived values, corrected**

- `2026-08-08#0` sets `buddy_count` to 5 while naming two people.
- `2025-07-19#0` is a dive at altitude with no recording, so it carries no
  `atmospheric_pressure` at all: the field is a run's, and this dive has no run. `DATA-124`.
- `provence_week` overrides its dates to cover the travelling days.
- `altitude_trial` has no dives at all, so it has no dates to derive.

**Dates and times**

- `2025-09-06#0` starts at 23:20 and ends at 00:05, so its end date is the following
  day.
- `anna_devries` was born on a leap day.
- `maria_ferreira`'s insurance has expired; nothing renews it.
- `tom_janssen`'s medical is years old, which is recorded but not judged.

**Units**

- `gear.json` declares units for the whole file, matching the default. A declaration
  that agrees with the default still has to be read, kept and written back.
- `2026-03-14#0` declares its own, in feet, while the dive beside it on the same day
  uses metres. Both are separate files, which is the only way two items can differ.
- Temperatures stay in degrees Celsius in both, since only length was overridden.
- `yemoja.json` declares no units. A declaration reaches no further than its own file, so
  there is no logbook-wide setting for one to shadow.

**Keyed collections**

- `courses`, `maintenances` and `gas_sources` are objects keyed by entry, not arrays. No
  entry carries a `key` field: the key is where it sits.
- `primary_regulator` and `steel_12` both use `k1` — keys are local, so the same one in
  two owners is not a collision.

**References**

- `Marco` appears as a one-off on two dives and is deliberately *not* the same person
  either time.
- `jacques_cousteau` is both the logbook's owner and a buddy on dives.
- `simone_cousteau` and `jacques_cousteau` name each other as emergency contacts.
- Courses point at the dives that formed them.
- `jacques_cousteau`'s first course carries a card `number`, and `tom_janssen` who taught it
  carries an `instructor_number`. The two sit apart on purpose: a card number names one
  award and an instructor's number names the instructor, so the course holds the first and
  the person the second, with the course's reference joining them.
- **Everyone who teaches here can.** `tom_janssen` and `lena_okafor` both hold
  `padi_open_water_scuba_instructor`, dated before the first course each of them taught,
  and Lena taught Tom's. They held only a divemaster until this was noticed, which made
  every open water course in the fixture one a divemaster cannot certify — and Lena's own
  divemaster was dated 2018 while she certified Tom's in 2012, which is a paradox rather
  than a technicality. Her divemaster moved back to 2008.
- `maria_ferreira` taught Lena's trimix and has no courses recorded at all. That is
  deliberate and is not the same fault: an absent qualification claims nothing, and nobody
  writes down the full history of the person who taught them.

**Sparse and absent**

- `amber_shallows` is the one site with no `entry`, the rest carrying one each: `steps`,
  `shore`, `jetty`, `rope` and `boat`. `poolside` is the one word of the six no site uses.
- `2026-05-02#0` has a date, a time and a remark, and nothing else.
- `omar_haddad` has a first and last name and nothing else.
- `old_lock_basin` has no coordinates.

**Libraries**

- Most regions and every agency certification resolve to a library, not to this logbook.
- `netherlands` is defined here as well, shadowing the supplied item — the same
  content with a remark of the user's own.
- `ravensgate_valley` is a local region whose parent is that shadowed item.
- `provence` is a local addition the supplied regions do not have.
- `quarry_club_diver` is a local certification no agency would carry, and
  `john_smith` holds it alongside a CMAS one from a library.
- `club_lead` is borrowed gear with no serial and no history.

**Real data, deliberately**

- Two ships are real and the facts about them are meant to be true: the *Thistlegorm*,
  bombed in 1941 and dived by Cousteau in the fifties, and the *Britannic*, which he located
  in 1975. They are said in the remarks of `windlass_wreck` and `the_copper_barge`, there
  being no wreck item. Everything else in this fixture is invented.
- The sites they are said at are not real, so no pairing here is a claim about where
  anything lies.
- Cylinder weights and displaced volumes are meant to behave correctly: aluminium
  floats when empty, steel does not, and a twin set is heavily negative. They were
  worked back from published empty-buoyancy figures rather than measured, so they are
  right about what a cylinder *does* without claiming to be any one product's
  specification.
- `northshore_diving` is the only operator with `alternative_names`, holding the Dutch
  name it traded under before it was bought. It is there so the field is exercised on an
  operator and not only on sites — and the older dives at that centre are
  logged against the item, not the name, which is the point of `DATA-63`.
- Neither ship's remarks give a displacement, on purpose. The figures the books give for
  both are gross tonnages, which are volumes and not weights, and `DATA-62` says a figure
  you had to guess the units of is worse than no figure. The fixture takes its own advice.
- The three dives of 30 May 2025 and the one of 21 June 2026 carry a `time_zone_offset` of
  7200: summer time in Provence and in the North Sea alike, both two hours ahead of GMT. All
  three of a chained day carry it, so their surface intervals come out as their local times
  say; the dives that say nothing are taken to be on GMT, which compares the same way.
- The borrowed Aladin on 21 June 2026 has a `recorded_time_offset` of `-60`: its clock ran a
  minute slow, so its recording says 08:49 for the 08:50 the other computer says. Nothing in
  the fixture exercises a computer left on home time in another country, which is the larger
  case the field exists for.
- Repetitive dives are chained with `previous_dive` where the gap was short: both dives
  of 15 June 2024 and of 21 September 2024, all three of 30 May 2025, and both of 14 March
  2026 — which makes a chain three long. The overnight pairs are deliberately *not*
  chained, on 16 June 2024 and 20 July 2025, because that is the judgement the field
  exists to record and a fixture that only ever said yes would not exercise it.
- `2026-08-08#0` writes `surface_interval` by hand and names no `previous_dive`. It stands
  for the dive whose predecessor was never logged — the case that makes the field
  correctable rather than purely worked out.
- Every `compressible_fraction` is worked out the same way, from the published rule of
  thumb that a neoprene suit loses about half its surface buoyancy at ten metres and two
  thirds at twenty. Both figures give the same answer — the compressible volume equals the
  surface buoyancy — so the fraction is `(displaced_volume - mass) / displaced_volume`.
  They are meant to behave correctly rather than to describe any particular suit.
- The suits split their volume the way the model asks. `my_drysuit` displaces 6.5 litres
  with just enough gas in it not to squeeze and can take on 11 more, so the fixture
  exercises a `lift_volume` on something that is not a wing. The wetsuits carry none:
  soaking a wetsuit changes nothing in the water, and there is no field for it.

**Recordings**

Four dives carry profiles, and the other eighteen deliberately do not — a logbook where
every dive came off a computer would not be one anybody has.

- `2025-05-30#2` is the simple case: **one profile, so no `primary_profile`**, since there
  is nothing to choose between. The dive writes no `start_date`, `start_time`, `duration`,
  `max_depth` or `deco` at all; every one of them derives from the recording, which is the
  whole point of having it. Its cylinder was rented, so the gas source names no `cylinder`
  and writes `volume` by hand — the case the manual describes, where nothing can be worked
  out from a gear item that does not exist.

  Its profile records `no_deco_time` and no `decostop`, which is how a computer writes an
  ordinary recreational dive — so `deco` derives as false from the absence of stops plus
  the presence of time remaining, rather than being unanswered. That is the second half of
  `LOGIC-6`, and this is the dive that exercises it.
- `2026-06-21#0` is the awkward one: **two computers and two gases**. `primary_profile`
  names `*p1`; `p2` was borrowed and its `dive_computer` is a plain name rather than a
  reference. The two disagree on purpose — `p2` starts a minute earlier, reads about a
  metre shallower throughout and was set to `en13319` where `p1` was set to `salt`, so the
  same dive has two depths and two densities behind them.

  Those two densities come out differently on purpose. `p2` is `en13319`, which is 1020
  whatever recorded it, so its borrowed computer being a plain name costs nothing. `p1` is
  salt, so it asks `reef_computer`, which carries `salt_density` 1025 — not the 1030 Yemoja
  falls back to, so a derivation that ignored the field would be visible here. What the
  fixture does *not* exercise is that fallback: a salt recording off a computer with no
  figure on it wants a fixture of its own.

  It keeps `max_depth` written down at 52.4 while `p1`'s deepest sample is 51.9. That is
  the documented correction, not an inconsistency: a computer usually reports a better
  figure than the profile it kept, which is sampled every few seconds.

  `p1` records what it was computed with: Bühlmann at 30/70. `p2` records nothing of the
  kind, its computer having been borrowed — which is the ordinary case, since only a
  download or a careful user fills those in. Neither carries a `conservatism`: gradient
  factors and a dial position are alternatives, and this computer offers the first.

**Dives not yet made**

`2026-10-03#0` and `2026-10-03#1` hold plans and no recording, so both read as `planned`. They
are the only dives here that have not happened, and they are what anything counting dives has to
leave out.

- Both profiles set `planned`, and `primary_profile` names `*a`, so the dive's date, depth and
  duration are the main plan's. A plan naming a primary is not the awkward case `2026-06-21#0`
  is: there are two, and one of them is the intention.
- Each plan keeps **gas sources of its own**, which is what the dive's own `gas_sources` being
  absent shows. `a` carries two, a bottom mix and a deco mix switched to at 21 metres, and its
  `gas_switches` name entries of its own collection rather than the dive's. `b` carries one.
- `a`'s `g1` names `@steel_12` and takes its volume from it, while `g2` writes a `volume` for a
  stage nobody here owns an item for. Both write a `sac`, which is what the gas a plan needs is
  worked out from and what no recording of a dive not yet made could supply.
- `b` writes an `atmospheric_pressure` of `0.995` where `a` writes none, because it is a plan
  for another day. Each run carries its own air, and nothing derives one from the dive.
- Both plans run their depths to the surface, stops included. `a` holds a 40-metre bottom and
  four stops; `b` holds a 30-metre bottom and none, being the shallow alternative for a day
  with swell.
- `a` keeps what the planner was set to, every setting and three typed lines under `runtime`,
  and `b` keeps none, as a plan saved before they were kept. `a`'s lines were written by hand
  rather than by the planner, so the way up the model works out from them is not the four stops
  its points hold, and it opens from its points: the case of a plan changed after it was saved.
  `a` names its deco mix as the `lost_gas`, through the same `*g2` its switches use.
- `2026-10-03#1` is the afternoon, and the reason `previous_profile` exists. Its dive names
  `2026-10-03#0` as the dive before, whose primary is `*a`; its plan writes
  `"@2026-10-03#0*b"` instead, so it is planned against the shallow alternative rather than
  against the wreck. That is the only reference in this fixture that reaches an owned item
  inside another item, and the only place the two markers are written together.

  The gas sources are `g1`, back-mounted trimix, and `g2`, a staged deco mix. `pressures`
  holds a series under each of those keys, `gas_switches` points at the second with `*g2`,
  and `decostop`, `alarms`, `no_deco_time`, `cns` and `otu` are all there.

  **Its one switch is the switch to the deco gas**, at twenty-four minutes, and nothing records
  the trimix it went down on — which is how a computer reporting only its changes writes a dive.
  That is deliberate and kept: it is the case the model refuses rather than guesses, saying what
  is missing, and a fixture that filled in the starting gas would hide it.
  `tolerances` records what the thinning was allowed to drop.

Between them they exercise every profile and gas source field except `density` and
`duration`, which are derived and never written.

**Awkward data**

- `pacific_ocean` crosses the antimeridian, so its `east` is numerically below its
  `west`. Its edges are named for compass points precisely so that this reads as a fact
  rather than as a transposition.
- `provence` has two parents, a country and a sea.
- `provence_week` is a trip with two legs beneath it, each a trip of its own naming it as
  `parent`. The dives point at the first leg; the second has none logged. Every other trip
  here is a single item with no parent, which is the ordinary case.
- All three water types appear, including `en13319` at `silt_harbour`.
- `old_lock_basin` has no `environment_type` at all. The field is a fixed set, so a
  missing value is the only way to say nothing about a site.
- Every step of the `current` and `waves` scale appears at least once, including
  `2026-03-14#0` with a very hard current and no waves at all — a running tide in
  sheltered water, which a single flag could not have told apart from a rough day.
- `primary_regulator` has an inspection that became a repair, which sets the following
  work back to an inspection.
- `steel_12` carries a yearly inspection and a five-yearly pressure test running side by
  side, neither hiding the other. Neither sets a `follow_up_type`, because each is its own
  obligation.
- Several `remarks` run to more than one line.
- `remarks` on an item inside an item: on a medical, on a dive's environment and on
  its gear. Every kind of item has one, owned items included, and only the maintenance
  under `my_drysuit` exercised that until now.
- `2024-06-15#0` mentions four items in its remarks and gives an e-mail address in the
  same breath, which is the awkward case whichever interface acts on `JSON-23` first will
  meet. Between them they carry every part of the rule: a mention ended by a comma, one
  ended by a full stop, one whose own name holds a dot — `@generic_0.5_kg_lead_weight`,
  from the libraries — and an e-mail address, whose `@example.invalid` is a candidate
  like any other that resolves to nothing. Nothing in the data layer tells them apart,
  which is the point: what makes the address harmless is that no item is called that.

## What it does not cover

Deliberate gaps, needing fixtures of their own:

- **Conflicts and merges.** Two versions of the same item, with and without a common
  ancestor.
- **Damage.** Malformed JSON, a value outside a fixed set, a reference to something that
  was deleted, a number outside its range. Everything here is well-formed on purpose, so
  nothing exercises the *unusable* half of reading a field.

  `tool/checkdata.py` catches all four in fixture data, so the fixture that exercises
  them cannot live here: it would have to be a logbook the checker is told to expect
  failures from.
