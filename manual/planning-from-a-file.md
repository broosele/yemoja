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
    "lines": [
      {"depth": 40},
      {"depth": 40, "duration": "22:46"}
    ],
    "gases": [{"gas": "air", "size": 24, "fill": 232, "sac": 20}],
    "gradient_factor_low": 1,
    "gradient_factor_high": 1
  }
]
```

**Only the lines are required.** Everything else Yemoja answers with its own default, which is
why a file comparing four rows of a table is short. The gradient factors have no default and
must be given, as proportions: `0.3` for 30, as a logbook writes them.

Numbers may be written as numbers or as text: `40` and `"40"` say the same depth.

### The lines

One for each part of the dive you are describing, in order, the first leaving the surface. The
way up is not written: Yemoja calculates it and adds it.

| Field | What it says |
|---|---|
| `depth` | Metres. Required. |
| `duration` | How long the line takes, in seconds as a logbook writes a time: `1366`, or minutes and seconds as `22:46`. |
| `rate` | Metres a minute, instead of a duration. |
| `gas` | Which cylinder, by its number: `1` for the first. Left out, the line breathes what the line above breathes. |

A line at the same depth as the one before it is a stay and needs a duration. A line that
changes depth takes a duration or a rate; given neither, it moves at the descent or ascent rate.

### The cylinders

| Field | What it says |
|---|---|
| `gas` | The mix: `air`, `EAN32`, `TMX18/45`. |
| `role` | `bottom`, `deco` or `bailout`. `bottom` if left out. |
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
| `gradient_factor_low`, `gradient_factor_high` | GF low, GF high, from 0 to 1 — **no default** |
| `descent_rate`, `ascent_rate` | Descent rate, Ascent rate |
| `last_stop` | Last stop |
| `gas_switch_stops` | Gas switches between stops, `true` or `false`; `false` if left out |
| `safety_stop_depth`, `safety_stop_duration` | Safety stop depth, Safety stop duration |
| `po2_max_bottom`, `po2_max_deco`, `po2_min` | pO₂ max bottom, pO₂ max deco, pO₂ min |
| `water_type` | `salt` or `fresh` |
| `panic_factor`, `problem_solving_time` | Panic stress factor, Problem-solving time |
| `lost_gas_reserve`, `lost_gas` | The *Lost* scenario's switch, and which cylinder it loses by number, left out for the first deco cylinder |
| `shared_gas_reserve` | The *Buddy out of gas* scenario's switch |

[settings.md](settings.md) says what each of them does, and
[decompression.md](decompression.md#planning-a-dive) what the two gas-reserve scenarios are.

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

- `lines` — every part of the dive in one list, the ones you wrote and the ones Yemoja added,
  each with `from_m`, `to_m`, `begins_at_seconds`, `seconds`, `direction`, `gas`, and `added`
  saying which it is.
- `stops` — each held depth in metres and seconds.
- `warnings` — what the model has to say against the plan, each with the second it happened at
  and how serious it is.
- `no_flight_seconds` and `desaturation_seconds`.
- `reserve` — the gas reserve, keyed `lost_gas` and `shared_gas`; a scenario switched off is left
  out. Each holds `worst_seconds` and `worst_m`, the moment it costs the most; `needed_litres` and
  `reserve_bar`, what each cylinder must give up at that moment, by its number; and `shortfall`,
  the first moment a cylinder's own gauge would run short, or nothing where none does. A scenario
  that cannot be worked out — a cylinder with no SAC, most often — is an object with only a
  `refused`, the rest of the plan answered regardless.

A plan that will not calculate is an object with its `name` and a `refused`.

## What it does not do

It cannot follow an earlier dive. A plan that starts with gas still in you needs the dive it
follows, which is a dive in a logbook, and this command opens none. Use the Calculations tab for
that.
