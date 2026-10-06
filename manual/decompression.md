# The decompression model

What a decompression model says about a dive: how loaded your tissues were, how shallow you
could have gone at any moment, and how long you had before stops became necessary. It says the
same about a dive you have not made yet, which is what planning one is.

## What Yemoja shows today

Yemoja runs Bühlmann ZH-L16C, and will answer for any profile carrying gradient factors and a
water type: what the ceiling was at each moment, how much longer you could have stayed, how much
gas each cylinder gave up, what the oxygen clocks reached, how long before you may fly, and what
it thinks you did wrong. The same questions, asked of a plan, are a dive plan.

**There is no screen for any of it yet.** What you see on a dive's graph is still what your dive
computer recorded at the time:

- the **NDL** on the graph's right axis is your computer's own no-decompression time;
- the **stepped line** is the stop your computer set, and the water above it is shaded red
  while a stop stood;
- whether a dive was a **decompression dive** is read off those same two recordings, as
  [data-fields.md](data-fields.md) explains under `deco`.

The two answers will sit beside each other rather than one replacing the other. Your computer
decided at the time, with you in the water and with settings this application cannot reproduce;
Yemoja's is a second opinion arrived at afterwards, and both are honest answers to different
questions.

## What this is, and what it is not

A model's figures are arithmetic. On a recording they are calculated after the fact; on a plan
they are calculated from assumptions you supplied about a dive that has not happened.

**It is not a dive computer, and it has been neither certified nor validated as one, nor as
planning software.** The figures are one model's estimate. They will disagree with what a dive
computer says, sometimes considerably, and they may simply be wrong. A plan is only as good as
what you told it, and no dive follows its plan exactly.

**You use all of it entirely at your own risk.** No one involved in making Yemoja accepts any
responsibility for a dive planned, made or judged with its help, or for anything that follows from
one. Check a plan against your training and your tables before you rely on it. Never carry one
into the water as the thing you follow: your dive computer and your own judgement decide the dive,
and nothing here overrides either, or your training, or your tables.

Nothing the model computes is stored in your logbook. A plan keeps what you entered and never the
answer, which is calculated again whenever you look, so a later version of Yemoja that calculates
differently will change what you see — for a plan as much as for a recording. That is deliberate:
what you recorded or entered is the fact, and the model is only an opinion about it.

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

Depth is what a diver reads. Pressure is what the body responds to, so a model works in
pressure throughout and converts once at the start.

Two adjustments matter and are easy to miss:

- **The water itself.** Salt water is denser than fresh, so the same ten metres results in
  slightly more pressure in the sea than in a quarry. This is why a recording notes the water
  type its computer was set to: the depths it wrote down were converted with it.
- **Your lungs are wet.** The gas in them is saturated with water vapour at body
  temperature, which occupies about 0.06 bar and displaces the gas you are breathing.
  The amount of nitrogen actually available to dissolve is therefore slightly lower than the
  proportion in your cylinder suggests, and at shallow depths that fraction is not
  negligible.

## The Bühlmann model

Albert Bühlmann published a family of decompression models from the 1960s onwards,
refined over decades of chamber work and diving in Zurich — including at altitude, which
the model was built to handle. ZH-L16C is the version this chapter describes. The
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
against a moving target, along the whole profile: descent, bottom, every ascent and every
stop.

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

The gradient factors a recording holds are the ones your computer was set to. Yemoja does
not recalculate a logged dive with them, or with any others.

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

### Time to surface and GF99

**Time to surface**, TTS, is how long the way up would take if you started it now: every stop
the model asks for, and the rise between them. A plan's TTS rises at the plan's own ascent rate
and takes its own last stop. Your computer's recording says neither, so Yemoja assumes nine
metres a minute and a last stop at three metres. Those are fixed figures rather than your
settings, so what Yemoja says about a dive you have done does not change when a setting does.

**GF99** is how close your most loaded compartment is to its limit at your current depth, as a
gradient factor: 0 % is no excess pressure at all, and 100 % is the M-value itself. On the
bottom it reads 0 %, because your tissues are still taking gas on. It rises on the way up, and
a stop holds it near the gradient factor the plan uses there. A figure past your GF high means
you are closer to the limit than you chose to go.

### Equivalent narcotic depth

**END** is the depth of air that would be as narcotic as what you are breathing. Air's END is its
depth; helium is not narcotic, so a trimix's is shallower. Whether oxygen is narcotic, agencies
disagree: counted, nitrox has the same END as air at the same depth; not counted, its END is its
equivalent air depth. A plan chooses with *O₂ narcotic*, and counts it unless you say otherwise.

Yemoja tracks the END through every dive on the gas you are breathing, and warns where it goes
past the plan's *END max*, 50 m unless you set another. Your computer's recordings carry no limit
of their own, so they are held to 50 m with oxygen counted.

## Planning a dive

A plan is a profile you write instead of one your computer wrote: the depths against time, the
cylinders you will take, and how conservative you want to be. It sits on a dive like a recording
does, and [data-fields.md](data-fields.md) lists every field of one under *Profile*.

**What you write is the dive itself, stops and all.** A plan's depths run to the surface, so it
holds the descent, the bottom, the ascent and every stop as ordinary points. You can ask Yemoja to
work the ascent out and write it in for you, giving it a rate to rise at and a depth to take the
shallowest stop at; it puts the stops on the threes divers count in and moves you to the richest
gas each depth allows where it stops. Asked to, it also stops for a switch at the deepest three
a richer gas may be breathed at, and holds every switch at least a minute.

**An ascent written that way is then fixed, like anything else you wrote.** Change a gas or a
gradient factor afterwards and the stops do not move by themselves — but asking the model about the
plan again will tell you at once that they no longer hold. That is the trade for a plan that means
the same thing every time it is read, rather than a schedule quietly rewriting itself.

Asked about a plan, the model answers what it answers about any dive: the ceiling throughout, the
time left before stops become necessary, the gas each cylinder gives up and what its gauge would
read, the two oxygen clocks, how long before you may fly, and a list of what it objects to — going
above the ceiling, a cylinder that runs out, a mix too rich for the depth it is breathed at, a mix
with too little oxygen for it. **The CNS clock is not defined above 1.6 bar**: the published
limits stop there, so past it the clock runs no faster than it does at 1.6, and the objection
beside it is the answer rather than the figure.

**Planning a second dive of the day** means telling the plan which earlier run you are carrying
gas from, and the model then carries your tissues across the surface interval into it. Two plans
for the morning are two things that might happen, so the afternoon's plan says which of them it
assumes — and you can keep a chain of plans beside the chain of dives you actually made. In the
planner that is **After**, beside the plan's start, and the surface interval is the time between
the earlier dive's end and that start. A saved plan keeps it in its `previous_profile` field.

**What it costs in gas** comes from the SAC rate you write on each cylinder: how fast you breathe,
in litres a minute at the surface. That is a guess about yourself, and the better your guess the
better the answer. Your own past dives are where to get it, since Yemoja calculates one from every
recording that has cylinder pressures in it.

**Whether you carry enough gas for trouble** is a different question from what the dive costs.
Yemoja answers it with two things that can go wrong, and keeps back enough for the worse of them.

- **Lost gas.** One cylinder is gone, the one chosen on the *Lost* line, your
  first deco gas unless you choose another, and you must still reach the surface on what is left: every stop the model asks for, and your
  safety stop, breathing at your usual SAC.
- **Buddy out of gas.** Your buddy has lost their bottom gas, and the two of you breathe from yours
  until you are shallow enough for your deco gas. There each of you switches to your own. Two
  people are breathing from one cylinder, and both are stressed, so it costs twice your SAC times
  the *stress factor*, which is 2 unless you change it. Any stop deeper than your deco gas's
  depth is shared too. With no deco gas planned you share all the way to the surface.

Both scenarios begin with **problem-solving time**, two minutes unless you change it: you stay at the
depth where it happened while you notice the problem and, when sharing, find your buddy and get the
gas going. That time is breathed at the scenario's rate and loads your tissues too, so it can add
a stop to the way up as well as the gas for itself.

The second scenario assumes your buddy breathes as fast as you, carries the same deco gas, and
cannot use your bailout. Leave either scenario out where it does not apply: the second on a solo
dive, by unticking it, and the first where losing your deco gas is not something you plan for, by
choosing *None* as the gas lost.

**The reserve is what each cylinder should still hold when you surface**, in bar. Every moment of
the plan is tried, in each scenario. At each one, the way up in trouble costs some gas, and part of
that is gas the plan would have breathed from there anyway. What is left over is extra, and the
moment with the most extra is that cylinder's worst moment. Surface with at least that much, and
the cylinder held enough at every moment of the dive; your gauge reads more than that during the
dive, by what the rest of the plan still breathes. A cylinder's reserve is the larger of the two
scenarios'. If a cylinder ends with less, Yemoja says which, what it ends with, and in which
scenario.

The worst moment is often the end of the bottom. Losing your deco gas just before you would have
switched to it can be worse: from there the plan breathes no more bottom gas, but without the deco
gas the whole way up is on it. So can the moment on the way up just shallower than a deco gas's or a
bailout's own limit, from where it is breathed the whole way, and Yemoja tries that depth as well
as the plan's own points. A bailout cylinder is breathed on the way
up when gas is lost, since trouble is what it is carried for. Where no gas you have left may be
breathed at the depth trouble starts, the reserve is worked out on the leanest anyway, and Yemoja
warns of it at the deepest such moment. A cylinder with no SAC cannot be
costed, and one with no size or fill can be costed in litres but not checked against what it holds.

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
- **What happened before.** Recent diving can be given to a model, but flights, altitude,
  and dives it has not been told about are simply absent.
- **Whether the recording is right.** A depth reading that drifted, a gap in the data,
  the wrong gas — the arithmetic will proceed regardless and give a confident answer.
- **Whether a plan will be followed.** It answers for the dive you wrote down, not the one you
  make, and the two differ the moment anything does: the current, the cold, a buddy's gas, a
  cylinder filled to less than you assumed.

Decompression models are estimates fitted to what has happened to other people. Two
divers with identical profiles can have different outcomes, and the model has nothing
to say about why.

## Other models

Bühlmann ZH-L16C is the model explained here. Others exist. The bubble models — such as VPM-B and RGBM — track the growth of gas
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
