package yemoja.logic.divecomputer

import yemoja.data.Moment

/*
 * Putting back together a dive a computer cut in two.
 *
 * See ../../../../../../doc.md — `LOGIC-25` in logic/doc.md is the decision.
 */

/** The surface a dive may hold and still be one dive, in seconds. Ten minutes. `LOGIC-25`. */
internal const val SURFACED: Long = 10 * 60

/**
 * [recordings] with the stretches of one dive laid end to end, each pair as one recording.
 *
 * A computer ends a dive when a diver reaches the surface and stays there, so a diver who
 * surfaces for a minute to sort a mask or to find the boat comes home with two recordings of
 * one dive. Two stretches from one device less than [SURFACED] apart are taken to be one dive.
 * `LOGIC-25`.
 *
 * Lazy, and it holds one recording back: a device reports in an order of its own, so a stretch
 * is compared with the one beside it whichever way round the two arrive. A dive cut into three
 * joins the same way, the pair already joined being what the third is measured against.
 */
internal fun joined(recordings: Sequence<Recording>): Sequence<Recording> = sequence {
    var pending: Recording? = null
    for (recording in recordings) {
        val held = pending
        pending = when {
            held == null -> recording
            continues(held, recording) -> onto(held, recording)
            continues(recording, held) -> onto(recording, held)
            else -> {
                yield(held)
                recording
            }
        }
    }
    pending?.let { yield(it) }
}

/** Whether [next] carries on from [first]: the same device, and back under the surface soon. */
private fun continues(first: Recording, next: Recording): Boolean {
    if (!sameDevice(first, next)) return false
    val ended = endOf(first) ?: return false
    val began = beganAt(next) ?: return false
    return ended.secondsUntil(began) in 0..SURFACED
}

/**
 * Whether the two were recorded by one device.
 *
 * A download reads one device, so this is nearly always so; it is asked because the sequence
 * does not promise it. What neither says cannot tell them apart and does not try to.
 */
private fun sameDevice(first: Recording, next: Recording): Boolean =
    (first.serial == null || next.serial == null || first.serial == next.serial) &&
        (first.computer == null || next.computer == null || first.computer == next.computer)

/** When a recording began, or absent where it does not say both halves of that. */
private fun beganAt(held: Recording): Moment? {
    val date = held.began ?: return null
    val time = held.at ?: return null
    return Moment(date, time)
}

/**
 * When a recording ended, by whichever of its two answers runs longer.
 *
 * A device's own figure and its last sample rarely agree to the second, and taking the later
 * of them is what keeps the joined samples in order.
 */
private fun endOf(held: Recording): Moment? {
    val began = beganAt(held) ?: return null
    val lasted = maxOf(held.duration?.toLong() ?: 0L, held.samples.lastOrNull()?.at?.toLong() ?: 0L)
    return began.plusSeconds(lasted)
}

/**
 * [first] with [next] laid after it, as the one recording a dive cut in two should have been.
 *
 * What the device said about the dive as a whole is taken from the pair: the deeper of the two
 * depths, the colder of the two temperatures, and a length running from the first stretch's
 * start to the second's end, surface and all. The average depth is weighted by how long each
 * stretch lasted, the surface between them being no depth anybody recorded.
 *
 * The second stretch's samples are moved along by the time between the two starts, since a
 * sample's time is seconds from the start of its recording and the start is now the first
 * stretch's. Its gases are matched against the first's and appended where they are new, and
 * every index naming one moves with them. A sample that would not follow the one before it is
 * dropped, a series running forwards.
 */
private fun onto(first: Recording, next: Recording): Recording {
    val from = beganAt(first)
    val to = beganAt(next)
    val apart = if (from != null && to != null) from.secondsUntil(to) else 0L
    val gases = first.gases.toMutableList()
    val moved = next.gases.map { gas ->
        gases.indexOf(gas).takeIf { it >= 0 } ?: gases.size.also { gases += gas }
    }
    val last = first.samples.lastOrNull()?.at ?: Int.MIN_VALUE
    val after = next.samples.map { sample ->
        sample.copy(
            at = sample.at + apart.toInt(),
            gas = sample.gas?.let { moved.getOrNull(it) ?: it },
            pressures = sample.pressures.mapKeys { moved.getOrNull(it.key) ?: it.key },
        )
    }
    return first.copy(
        fingerprints = first.fingerprints + next.fingerprints,
        duration = lastedOf(first, next, apart),
        maxDepth = deeperOf(first.maxDepth, next.maxDepth),
        averageDepth = meanOf(first, next),
        coldest = colderOf(first.coldest, next.coldest),
        surface = first.surface ?: next.surface,
        gases = gases,
        samples = first.samples + after.filter { it.at > last },
    )
}

/** How long the pair ran: the time between their starts, and then the second's own length. */
private fun lastedOf(first: Recording, next: Recording, apart: Long): Double? {
    val end = spanOf(next) ?: return first.duration
    return apart + end
}

/** How long one stretch ran, by its own figure or by its last sample. */
private fun spanOf(held: Recording): Double? =
    held.duration ?: held.samples.lastOrNull()?.at?.toDouble()

/** The two averages weighted by how long each stretch lasted. */
private fun meanOf(first: Recording, next: Recording): Double? {
    val one = first.averageDepth ?: return next.averageDepth
    val two = next.averageDepth ?: return one
    val a = spanOf(first) ?: 1.0
    val b = spanOf(next) ?: 1.0
    return if (a + b > 0.0) (one * a + two * b) / (a + b) else (one + two) / 2.0
}

private fun deeperOf(one: Double?, two: Double?): Double? =
    if (one == null || two == null) one ?: two else maxOf(one, two)

private fun colderOf(one: Double?, two: Double?): Double? =
    if (one == null || two == null) one ?: two else minOf(one, two)
