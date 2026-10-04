# Settings

Your preferences: what a new dive plan starts from today, and in time the units you want to be
shown, how dates are written, and whatever else the application lets you choose. They live in your
logbook folder, in two files beside `yemoja.json`.

**They are in the System tab**, in a box of their own, ready to change. Each shows what it holds and where
that came from: *set on this device*, *set in this logbook*, *the default*, or *not set*. Change
any box and press **Save**. Empty a box and save to take your choice away, so the next place in
line answers again.

**A default shows through an empty box, in italics**, the way every value Yemoja works out for
itself is shown. So you can see at a glance which of your settings you have actually chosen: those
are in ordinary type. Typing over an italic value chooses it; emptying the box again gives the
default back.

**These files belong to Yemoja, not to your data.** Everything in `data-format.md` and
`data-fields.md` is yours — a format that stays readable and that Yemoja promises not to
break. Settings carry no such promise. What they hold, and how, changes as the
application changes, and a newer version may write things an older one does not
understand. Nothing is lost if that happens: a setting Yemoja cannot make sense of is
ignored, and you are shown the default instead.

## The two files

`settings.json` holds the choices that follow you. It travels with the logbook, so once
syncing and backup exist a new phone or a reinstalled desktop starts where you left off.

`settings.local.json` holds the choices that belong to this device alone. It will never be
synced and never backed up.

The second exists because some choices should not travel. A window size means nothing on
a phone. And you may genuinely want different answers in different places — metres on the
desktop where you write dives up, feet on the phone you take on a boat abroad.

## Which one wins

Yemoja looks in three places, in order, and stops at the first that has an answer:

1. `settings.local.json` — this device.
2. `settings.json` — this logbook, wherever it is opened.
3. What Yemoja does when nothing says otherwise.

So you can set something once for every device you own, and still overrule it on one of
them without that overruling following you everywhere.

**Where *Save* writes.** A setting you have not chosen before goes into `settings.json`, so it
follows the logbook to every device. One already in `settings.local.json` stays there, since
somebody put it on this device on purpose. To keep a choice to one device, move its line into
`settings.local.json` by hand; from then on *Save* keeps it there.

A setting that is expected to differ between kinds of device is not left to that
ordering. It is named for what it applies to — a setting beginning `phone_` is read only
on a phone, `desktop_` only on a desktop — so both can sit in `settings.json` and travel
together without contradicting each other.

## What is in them

The list will grow. At present, each in the unit a logbook file uses when it declares none, so a
time is in seconds. That holds whatever units your logbook's files are written in, since a
settings file cannot declare any. Each is named after the field of a plan it fills, with
`default_` in front:

- `default_gradient_factor_low`, `default_gradient_factor_high` — the gradient factors a new dive
  plan starts with, written from 0 to 1. What divers write as 20/80 is `0.2` and `0.8`; the
  *Settings* form takes them as percentages, 20 and 80. Without a choice, 30 and 70: a common
  middle setting, and not a recommendation. How conservative a plan is, is yours to decide, so
  set your own.
- `default_descent_rate` — how fast a new plan descends, in metres a minute, from 1 to 60.
  Without a choice, 18.
- `default_ascent_rate` — how fast an ascent is written to rise, in metres a minute, from 1
  to 30. Without a choice, 9.
- `default_last_stop` — the depth an ascent takes its shallowest stop at, in metres, from 0
  to 12. Without a choice, 3.
- `default_po2_max_bottom` — the most oxygen a new dive plan breathes a bottom or bailout gas at, in
  bar, from 0.5 to 2. Without a choice, 1.4.
- `default_po2_max_deco` — the most oxygen a new dive plan breathes a deco gas at, in bar, from 0.5
  to 2. Without a choice, 1.6.
- `default_po2_min` — the least oxygen a new dive plan breathes any gas at, in bar, from 0.1 to
  0.5. A hypoxic gas breathed shallower than this is warned of. Without a choice, 0.18.
- `default_safety_stop_depth` — how deep a new dive plan's safety stop is, in metres, from 1 to
  12. Without a choice, 6.
- `default_safety_stop_duration` — how long a new dive plan's safety stop lasts, in seconds, from
  0 to 900. 0 means no safety stop. Without a choice, 180. The *Settings* form shows it in minutes.
- `default_stress_factor` — how many times their usual SAC each of two divers sharing gas breathes
  at, in a new dive plan's gas reserve, from 1 to 10. It is stress alone: the second diver is
  counted separately. Without a choice, 2.
- `default_problem_solving_time` — how long a new dive plan's gas reserve spends at the depth
  trouble starts before the way up begins, in seconds, from 0 to 600: the time to notice the
  problem and, when sharing, to find your buddy and get the gas going. 0 means none. Without a
  choice, 120. The *Settings* form shows it in minutes.
- `default_atmospheric_pressure` — the air pressure a new dive plan is dived under, in bar,
  from 0.4 to 1.1: less than one atmosphere for a lake at altitude. Without a choice, 1.013,
  one standard atmosphere.
- `default_water_type` — the water a new dive plan is dived in: `salt` or `fresh`. Without a
  choice, `salt`.

A value that is not a number, or lies outside its range, is ignored, and the next place in line
answers instead.

All of them are worth a word, because their names are doing real work. They are *defaults*
for making a plan and nothing more. A plan keeps the gradient factors it was made with and
the depths its ascent was written with, so changing these does not alter a plan you have
already made, and it does not alter anything Yemoja tells you about a dive you have already
done. Change them freely; nothing recorded moves.

Gradient factors are explained in [decompression.md](decompression.md).

## The agent command

- `desktop_agent_command` — the command that starts your agent, typed as one line. Only ever in
  `settings.local.json`, and ignored if it turns up in `settings.json`: a command often names a
  folder on one computer, and `settings.json` travels.

**The command is the agent's adapter, not the agent's own program.** Yemoja talks to an agent
over the Agent Client Protocol, and the program you type at in a terminal does not speak it:
`claude` on its own is Claude Code's terminal, and it will sit there reading until Yemoja gives up
on it. What speaks to Yemoja is the adapter the agent's maker publishes for editors — for Claude
Code that is `npx @zed-industries/claude-code-acp`, and for Codex `npx @zed-industries/codex-acp`.
Whatever the agent's own instructions give for using it *from Zed or another editor* is the
command to type here.

Those `npx` commands need Node.js installed, which is where `npx` comes from; without it the
panel says the command is not installed. If you would rather not install it, the full path to a
`node.exe` and to the adapter's `index.js` works in its place.

Once it is saved, the sparkle button at the right of the tabs comes alive; how to use it is in
[getting-started.md](getting-started.md#asking-an-agent).


## Deleting them

Safe, at any time. Delete `settings.local.json` and this device falls back to the
logbook's choices. Delete both and you get Yemoja's defaults. No dive, site, person or
piece of gear is touched either way — none of your data is in these files.

You can edit them by hand like any other file in the logbook, with the same warning:
close the logbook in Yemoja first, or your changes may be overwritten.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
