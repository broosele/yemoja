# Settings

Your preferences: the units you want to be shown, how dates are written, and whatever
else the application lets you choose. They live in your logbook folder, in two files
beside `yemoja.json`.

**These files belong to Yemoja, not to your data.** Everything in `data-format.md` and
`data-fields.md` is yours — a format that stays readable and that Yemoja promises not to
break. Settings carry no such promise. What they hold, and how, changes as the
application changes, and a newer version may write things an older one does not
understand. Nothing is lost if that happens: a setting Yemoja cannot make sense of is
ignored, and you are shown the default instead.

## The two files

`settings.json` holds the choices that follow you. It is synced and backed up with the
logbook, so a new phone or a reinstalled desktop starts where you left off.

`settings.local.json` holds the choices that belong to this device alone. It is never
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

A setting that is expected to differ between kinds of device is not left to that
ordering. It is named for what it applies to — a setting beginning `phone_` is read only
on a phone, `desktop_` only on a desktop — so both can sit in `settings.json` and travel
together without contradicting each other.

## What is in them

The list will grow. At present:

- `default_gf_low`, `default_gf_high` — the gradient factors a new dive plan starts
  with.

Those two are worth a word, because their name is doing real work. They are *defaults*
for a new plan and nothing more. A plan keeps the gradient factors it was made with, so
changing these does not alter a plan you have already made, and it does not alter
anything Yemoja tells you about a dive you have already done. Change them freely; nothing
recorded moves.

Gradient factors are explained in [decompression.md](decompression.md).

## Deleting them

Safe, at any time. Delete `settings.local.json` and this device falls back to the
logbook's choices. Delete both and you get Yemoja's defaults. No dive, site, person or
piece of gear is touched either way — none of your data is in these files.

You can edit them by hand like any other file in the logbook, with the same warning:
close the logbook in Yemoja first, or your changes may be overwritten.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
