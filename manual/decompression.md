# The decompression model

Yemoja can work out what a decompression model would have said about a dive you have
already made: how loaded your tissues were, how shallow you could have gone at any
moment, and how long you had before stops became necessary.

## What this is, and what it is not

This is arithmetic performed on a recording, after the fact. It is offered so you can
look at a dive you have already done and understand it better.

**It is not dive planning software, it is not a dive computer, and it has been neither
certified nor validated as either.** The figures are one model's estimate. They will
disagree with what your computer told you at the time, sometimes considerably, and they
may simply be wrong.

Never plan a dive from them. Never take them into the water. Never let them override
your training, your computer, your tables or your own judgement.

Nothing computed here is stored in your logbook. It is worked out when you ask and
forgotten afterwards, so a later version of Yemoja that calculates differently will
change what you see. That is deliberate: the recording is the fact, and the model is
only an opinion about it.

## Why decompression happens

### Pressure

At the surface you carry about one bar of atmosphere. Water is far heavier than air, so
descending adds roughly another bar for every ten metres. At thirty metres you are at
about four bar — four times the pressure your body is built around.

Altitude changes the starting point. A lake at two thousand metres begins below one bar,
so the same depth is less pressure than it would be at the coast, and — more importantly
— the surface pressure you eventually return to is lower than the one your body is used to.

### Gas going in

The air you breathe is roughly a fifth oxygen and four fifths nitrogen. Your body
consumes oxygen; nitrogen it can only dissolve. How much dissolves depends on the
pressure the gas is under, and pressure increases with depth.

So on the way down and along the bottom, nitrogen moves steadily from your lungs into
your blood and from your blood into your tissues. It keeps going as long as the
pressure in your lungs is higher than the pressure of the gas already dissolved in you.

Different parts of the body take it up at wildly different speeds. Blood-rich tissue
fills within minutes. Fat, cartilage and bone take hours. The same is true in reverse,
which is what creates problems.

### Gas coming out

Ascending drops the pressure, and now the balance runs the other way: you hold more
dissolved gas than the new pressure supports, and it comes back out. That is not in
itself a problem — it is what needs to happen.

The problem is speed. Dissolved gas leaves safely as long as it can travel through
blood to the lungs and be breathed away. Go up faster than the body can carry it, and
it comes out of solution where it is, as bubbles. Bubbles in the wrong places cause
decompression sickness.

Every model of decompression is an attempt to answer one question: **how much faster than
equilibrium can you go before that happens?**

### Working in pressure, not depth

Depth is what a diver reads. Pressure is what the body responds to, so the model works
in pressure throughout and converts once at the start.

Two adjustments matter and are easy to miss:

- **The water itself.** Salt water is denser than fresh, so the same ten metres results in
  slightly more pressure in the sea than in a quarry. This is why a dive site records
  its water type.
- **Your lungs are wet.** The gas in them is saturated with water vapour at body
  temperature, which occupies about 0.06 bar and displaces the gas you are breathing.
  The amount of nitrogen actually available to dissolve is therefore slightly lower than the
  proportion in your cylinder suggests, and at shallow depths that fraction is not
  negligible.

## The Bühlmann model

Albert Bühlmann published a family of decompression models from the 1960s onwards,
refined over decades of chamber work and diving in Zurich — including at altitude,
which is why the model handles it properly. ZHL-16C is the version Yemoja uses. The
sixteen is the number of compartments; the C is the third revision of its limits, and
the most conservative of the three.

### Sixteen compartments

The model does not attempt to represent real organs. It represents sixteen
**compartments**: imaginary tissues, each defined by nothing more than how fast it takes
gas up and lets it go.

That speed is the compartment's **half-time** — how long it takes to close half the gap
between what it holds and what it would hold at equilibrium. The fastest compartment
has a half-time of a few minutes, the slowest of over ten hours. Helium moves through
the body about two and a half times faster than nitrogen, so each compartment has a
separate, shorter half-time for it.

Sixteen is a convenience, not a discovery. Real tissue is a continuum; sixteen points
happen to be enough to cover the behaviour that matters.

### How a compartment fills

Each compartment approaches the pressure it is being fed, quickly at first and then
ever more slowly. One half-time closes half the gap, two half-times three quarters,
three half-times seven eighths. It never quite arrives.

This is why a long shallow dive can load slow compartments more than a short deep one,
and why a fast compartment can fill and empty several times over during a dive that
barely moves the slow ones at all.

When depth is changing rather than steady, the same arithmetic applies continuously
against a moving target. Yemoja does this along the whole profile: descent, bottom,
every ascent and every stop.

### How much a compartment tolerates

A compartment can hold more gas than the surrounding pressure supports without anything
going wrong. Otherwise offgassing would not be possible. There is a limit, though, and
Bühlmann expressed it for each compartment as two coefficients, conventionally called
**a** and **b**.

Together they give the lowest ambient pressure a compartment can be brought to for a
given load. The coefficients are not free parameters: they follow from the half-times
by a fixed rule, so a slow compartment is automatically less tolerant of a large
overpressure than a fast one. That relationship is the substance of the model.

Run the calculation for every compartment and the answer for the diver is whichever is
most demanding at that moment. Early in an ascent that will be a fast compartment; hours
later it will be a slow one.

### Gradient factors

Bühlmann's limits are limits — the point at which the model expects trouble, not a place
to aim for. Diving to them exactly leaves nothing in hand.

Gradient factors scale that back. A gradient factor is a percentage of the way from
where you are to the limit the model allows:

- **GF Low** applies at the deepest point where a stop becomes necessary.
- **GF High** applies at the surface.
- Between the two, the factor slides smoothly as you ascend.

A setting of 30/70 means the first stop is taken when only thirty percent of the
permitted overpressure has been used, easing to seventy percent by the time you surface.
Lower numbers mean a more cautious ascent, deeper first stops and a longer one.

The pair does two different jobs, which is why there are two. GF Low decides how deep
the ascent is held back; GF High decides how much loading you are willing to reach the
surface carrying.

### The ceiling

The **ceiling** is the shallowest depth the model would allow you at a given moment —
the pressure at which the most demanding compartment reaches its limit.

Below the surface, a ceiling means stops are required. At zero, you may ascend directly.
The ceiling moves as the dive goes on: deeper as you load gas, and gradually shallower
once you begin ascending and the gas starts coming back out.

### The no-decompression limit

The **no-decompression limit** is how much longer you could stay at your current depth
before the ceiling drops below the surface — the point after which going straight up stops
being an option.

It is not a countdown to danger. It is the boundary between an ascent you may make at
your own pace and one the model wants you to interrupt.

## What the model does not know

The model is a description of gas moving through an idealised body. Yours is not one,
and it knows nothing about:

- **You.** Age, weight, fitness, hydration, fatigue, injury, a heavy cold, alcohol the
  night before. All affect decompression, none appear in the arithmetic.
- **Temperature.** Being cold changes circulation, and circulation is exactly what the
  model assumes is constant.
- **Exertion.** Working hard at depth takes up more gas than drifting does.
- **Bubbles.** ZHL-16 tracks dissolved gas only. It has no concept of a bubble that has
  already formed, which is the thing that actually causes harm.
- **What happened before.** The model can be seeded with recent diving, but flights,
  altitude, and dives it has not been told about are simply absent.
- **Whether the recording is right.** A depth reading that drifted, a gap in the data,
  the wrong gas — the arithmetic will proceed regardless and give a confident answer.

Decompression models are estimates fitted to what has happened to other people. Two
divers with identical profiles can have different outcomes, and the model has nothing
to say about why.

## Other models

Only Bühlmann ZHL-16C is available in Yemoja.

Others exist. The bubble models — such as VPM-B and RGBM — track the growth of gas
nuclei rather than dissolved gas alone, and generally call for deeper early stops and
shorter shallow ones. DCIEM and other tabulated schedules come from experimental work
rather than a compartment model at all.

## Further reading

- A. A. Bühlmann, *Tauchmedizin* — the original presentation of the ZHL models, their
  coefficients and the altitude work behind them.
- Erik C. Baker's articles on M-values and gradient factors, widely circulated in the
  diving community, are the clearest published account of how the limits are applied in
  practice.
- The proceedings of the Undersea and Hyperbaric Medical Society, for the research the
  models are fitted to.

---

*This chapter is dedicated to the public domain under CC0 1.0. Copy it, quote it,
translate it, build on it — no permission needed and no attribution required.*
