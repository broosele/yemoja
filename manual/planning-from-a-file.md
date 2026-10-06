# Planning from a file

Yemoja can calculate dive plans without a window. You write the plans in a file, run one
command, and get back what each of them comes to — as a table you can open in a spreadsheet, or
in full so another program can be put beside it.

It opens no logbook and changes nothing. A plan is arithmetic over what the file says, so there
is nothing for it to read and nothing for it to spoil.

**Everything in [decompression.md](decompression.md) applies here.** These figures come from a
model, not a dive computer, and it has been neither certified nor validated as one. They may be
wrong. Nothing about asking for a hundred of them at once makes any one of them safer.

## Running it

```
yemoja plan <file>
yemoja plan <file> --json
```

The first writes a table, one row a plan. The second writes every line of every dive, which is
what you want if you are comparing against another program rather than against a table.

Both write to the screen, so send it to a file where you want one:

```
yemoja plan mine.json > mine.csv
```

## Writing the file

The file holds a plan, or a list of them. A plan is an object:

```json
[
  {
    "name": "40 m for 25 minutes, air",
    "runtime": [
      {"depth": 40},
      {"depth": 40, "duration": "22:46"}
    ],
    "gases": [{"gas": "air", "size": 24, "fill": 232, "sac": 20}],
    "gradient_factor_low": 1,
    "gradient_factor_high": 1
  }
]
```

**Only the runtime is required.** Everything else Yemoja answers with its own default, which is
why a file comparing four rows of a table is short. The gradient factors, where given, are
proportions: `0.3` for 30, as a logbook writes them.

Numbers may be written as numbers or as text: `40` and `"40"` say the same depth.

### The runtime

A list with one line for each part of the dive you are describing, in order, the first leaving
the surface, as the planner's runtime shows them. The way up is not written: Yemoja calculates it
and adds it.

| Field | What it says |
|---|---|
| `depth` | Metres. Required. |
| `duration` | How long the line takes, in seconds as a logbook writes a time: `1366`, or minutes and seconds as `22:46`. |
| `rate` | Metres a minute, instead of a duration. |
| `gas` | Which cylinder, by its number: `1` for the first, or `loop` on a rebreather. Left out, the line breathes what the line above breathes. |

A line at the same depth as the one before it is a stay and needs a duration. A line that
changes depth takes a duration or a rate; given neither, it moves at the descent or ascent rate.

### The cylinders

| Field | What it says |
|---|---|
| `gas` | The mix: `air`, `EAN32`, `TMX18/45`. |
| `role` | `bottom`, `deco` or `bailout`, and on a rebreather `diluent` or `rich`. `bottom` if left out. |
| `size` | Litres of water the cylinder holds. |
| `fill` | Bar it was filled to. |
| `sac` | Litres a minute at the surface. |

`size`, `fill` and `sac` are only needed for the gas figures. A plan without them still gives a
schedule; it just cannot say how much gas the dive takes.

The way up may switch to a `deco` or `bottom` cylinder by itself where the mix allows it. It
never switches to a `bailout` one.

### The settings

Each is the same setting the window has, and each may be left out. Each is written as a logbook
writes the field of the same name: a time in seconds, a rate in metres a minute, and a gradient
factor as a proportion.

| Field | The window's name |
|---|---|
| `gradient_factor_low`, `gradient_factor_high` | GF low, GF high, from 0 to 1; 0.3 and 0.7 if left out |
| `descent_rate`, `ascent_rate` | Descent rate, Ascent rate |
| `last_stop` | Last stop |
| `gas_switch_stops` | Gas switches between stops, `true` or `false`; `false` if left out |
| `safety_stop_depth`, `safety_stop_duration` | Safety stop depth, Safety stop duration |
| `po2_max_bottom`, `po2_max_deco`, `po2_min` | pO₂ max bottom, pO₂ max deco, pO₂ min |
| `end_max` | END max, in metres |
| `oxygen_narcotic` | O₂ narcotic, `true` or `false` |
| `water_type` | `salt` or `fresh` |
| `atmospheric_pressure` | Atmospheric pressure, in bar |
| `stress_factor`, `problem_solving_time` | Stress factor, Problem-solving time |
| `lost_gas_reserve`, `lost_gas` | The *Lost* scenario's switch, and which cylinder it loses by number, left out for the first deco cylinder |
| `shared_gas_reserve` | The *Buddy out of gas* scenario's switch |

[settings.md](settings.md) says what each of them does, and
[decompression.md](decompression.md#planning-a-dive) what the two gas-reserve scenarios are.

### On a closed-circuit rebreather

A case may be planned on a closed-circuit rebreather, which the window does not offer. The loop
holds its oxygen at a setpoint, and the rest of what you breathe is inert gas in the proportion
your diluent holds it.

| Field | What it says |
|---|---|
| `dive_mode` | `ccr` for a rebreather; `oc`, open circuit, if left out |
| `setpoint_low` | The setpoint from the surface down to `setpoint_switch_depth`, in bar; 0.7 if left out |
| `setpoint_high` | The setpoint from there on, the way up included, in bar; 1.3 if left out |
| `setpoint_switch_depth` | Where the descent changes to the high setpoint, in metres; 6 if left out |
| `bailout_reserve` | Whether the bailout reserve is tried, `true` or `false`; `true` if left out |
| `co2_hit_factor` | Times its usual SAC a bailout is breathed at during the CO₂ hit; 4 if left out |
| `co2_hit_time` | How long the CO₂ hit holds you at the depth it happened, in seconds; 600 if left out |

**Two roles belong to the loop.** The cylinder whose `role` is `diluent` is the loop's diluent,
and a rebreather case has exactly one. One whose `role` is `rich` is its oxygen, which nothing
counts yet. A rebreather case that gives no diluent breathes `air` as one, and one that gives
no `rich` cylinder draws on `O2`: each is added after the cylinders you gave, so their numbers stay
as you wrote them. No line breathes either: a line chooses `loop` or a `bottom`, `deco` or `bailout`
cylinder, which it breathes on open circuit. The first line breathes the loop unless it names a
cylinder, and every line after breathes what the one above does until it names another, so a
dive may leave the loop and come back. Gas breathed off the loop is counted as on open circuit.

The way up continues on what your last line breathes. From the loop it stays on the loop and
switches to nothing. From a cylinder it stays on open circuit and switches to a richer `bottom` or
`deco` cylinder as an open-circuit plan does, never to a bailout and never back to the loop.

A setpoint may be no higher than `po2_max_bottom`. Where your diluent alone holds more oxygen than
the setpoint, deep on a rich diluent, the loop breathes the diluent itself, and the pO₂ warning
says so; a diluent too lean to breathe at the surface is warned of too.

**The reserve is the bailout.** A rebreather case's one gas-reserve scenario is the loop failing
at the worst moment and the way up on open circuit, on the cylinders whose `role` is `bailout`
and no others: not the diluent, and not a deco cylinder. The way up starts on the richest bailout
its limit allows, switches among them as it rises, and breathes each at its own `sac`. It begins
with a CO₂ hit: `co2_hit_time` at the depth the loop failed, breathed on the bailout at
`co2_hit_factor` times its `sac`, which loads your tissues as well. A rebreather case uses these two
instead of `stress_factor` and `problem_solving_time`. Each bailout is said to
keep what it needs at the end of the dive. Only moments on the loop are tried, up to the one it is
left at, there being no loop to lose off it. `bailout_reserve` switches it on or off, `true`
unless it says `false`. With no bailout cylinder it says so.

**Not yet:** the gas the loop itself uses, and its scrubber. A rebreather case's answer leaves the
diluent and the rich cylinder out of the litres rather than giving open-circuit figures that would
be wrong.

### Following an earlier case

A case may follow an earlier case in the same file, so a repetitive dive is planned with the
nitrogen and the oxygen clock the first one leaves behind:

```json
[
  {"name": "first", "runtime": [{"depth": 30}, {"depth": 30, "duration": "20:00"}]},
  {"name": "second", "follows": "first", "surface_interval": 3600,
   "runtime": [{"depth": 30}, {"depth": 30, "duration": "20:00"}]}
]
```

| Field | What it says |
|---|---|
| `follows` | The `name` of an earlier case in the same file. |
| `surface_interval` | Seconds spent on the surface between the two, breathing air. Required with `follows`. |

The case followed must come earlier in the file, and must itself calculate: a case following one
that was refused is refused with it. Chains may be as long as the file: a third case may follow
the second.

## The table

One row a plan, with these columns:

| Column | What it says |
|---|---|
| `name` | What you called the plan, or `case 1` where you called it nothing. |
| `max_depth_m` | The deepest the plan goes. |
| `bottom_minutes` | The runtime at the end of the last line you wrote, which is what a table's row is chosen by. |
| `runtime_minutes` | Surface to surface. |
| `stop_minutes` | How long is spent holding a depth on the way up, which is what a table's column adds to. |
| `deepest_stop_m` | The deepest depth held. |
| `stops` | Every stop, deepest first, as `depth@minutes`: `18@1 15@2 12@3`. |
| `cns_percent` | The central nervous system's oxygen clock at the end. |
| `otu` | Oxygen tolerance units taken. |
| `gas_litres` | Litres at the surface from each cylinder, as `1:3073 2:736`. |
| `refused` | Why this plan has no answer, and empty where it has one. |

**A plan that will not calculate takes a row of its own** with the reason in it, and the others
are still answered. One bad case in fifty should not cost you the other forty-nine.

## The whole answer

`--json` gives an array, one object a plan, holding everything the table holds and these as
well:

- `runtime` — every part of the dive in one list, the lines you wrote and the ones Yemoja
  added, each with `from_m`, `to_m`, `begins_at_seconds`, `seconds`, `direction`, `gas`, and
  `added` saying which it is.
- `stops` — each held depth in metres and seconds.
- `dive_mode` — `oc` or `ccr`, as the case said.
- `ceiling`, `no_deco_seconds`, `tts_seconds`, `gf99_series`, `end_series`, `setpoint_series`, `po2_series`,
  `cns_series`, `otu_series`, `pressures_bar` — what the model works out through the dive, each written as a series is
  written everywhere: pairs of a second and the value then. The ceiling is the shallowest allowed
  depth in metres, and is empty where the dive owes none. The no-decompression time is in
  seconds, the stretches already owing a stop left out. The time to surface is in seconds. GF99
  is a percentage and may run below nought while a compartment is still taking gas on. The END is
  in metres, on the gas breathed at each moment. The setpoint is in bar, and empty on open
  circuit. The pO₂ is the oxygen breathed, in bar: the mix's on open circuit, and on a rebreather
  the setpoint, or the diluent's own where it holds more. The two
  oxygen clocks run as a percentage and a count. The pressures are each cylinder's gauge in bar,
  by its number, for the cylinders that say how big they are and what they were filled to.
- `warnings` — what the model has to say against the plan, each as the sentence the window
  shows in `said`, the cylinder and the moment in it, with beside it the `second` it happened
  at, its `severity`, and the number of the cylinder it is about in `gas` where it is about one.
- `no_flight_seconds` and `desaturation_seconds`.
- `reserve` — the gas reserve, keyed `lost_gas` and `shared_gas`, or `bailout` on a rebreather; a scenario switched off is left
  out. Each holds `said`, the scenario in the sentence the window's contingency line shows;
  `escape`, the way up it is costed on, from the moment asking most of any cylinder, as pairs of a
  second and a depth in metres, the time held there first;
  `shortfall`, the cylinders that end the dive with less than they keep, in a sentence, or
  nothing; `unchecked`, the cylinders costed in litres only for want of a size or a fill, in a
  sentence, or nothing; and `kept`, what each cylinder should still hold at the end of the dive,
  by its number, leaving out a cylinder that needs nothing: `litres`, `bar` on its own gauge,
  `end_bar` what the plan leaves on it, `short` where that is less, and `worst_seconds` and
  `worst_m`, the moment that asks it. A scenario
  that cannot be worked out — a cylinder with no SAC, most often — is an object with only a
  `refused`, the rest of the plan answered regardless.

A plan that will not calculate is an object with its `name` and a `refused`. Where the fault is
in a line of the runtime, it also holds `runtime` with the lines above that one, each as above,
so a form can still show what they come to while the faulty line is put right, and
`needs_duration`, the number of every line that stays at its depth without a duration, so each
can be marked at once rather than one at a time.

## What it does not do

It cannot follow a dive in your logbook. A plan that starts from a dive you actually made needs
the logbook that dive is in, and this command opens none. It can follow an earlier case in the
same file — see above — and for a logged dive there is the Calculations tab.
