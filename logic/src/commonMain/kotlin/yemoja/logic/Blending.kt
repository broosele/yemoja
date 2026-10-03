package yemoja.logic

import yemoja.data.Gas
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * What a cylinder holds once a gas has been added to it, and which gases to add to make a mix.
 *
 * See ../../../../../doc.md — `LOGIC-45`.
 */

/**
 * Contents is the gas in a cylinder, as the oxygen, nitrogen and helium each litre of it holds.
 *
 * Held in moles a litre, because that is what adding a gas adds to. A pressure does not add: a real
 * gas at 200 bar is not twice the gas it is at 100, and helium at 200 bar is nearly a tenth less
 * gas than the gauge suggests. The pressure is worked out from the amounts, at [TEMPERATURE].
 * `LOGIC-45`.
 *
 * Immutable.
 */
class Contents internal constructor(
    internal val oxygen: Double,
    internal val nitrogen: Double,
    internal val helium: Double,
) {

    /** Moles in each litre of the cylinder. */
    val amount: Double get() = oxygen + nitrogen + helium

    /** Nought in an empty cylinder, which holds no mix to have a fraction of. */
    val fractionO2: Double get() = if (amount == 0.0) 0.0 else oxygen / amount

    val fractionHe: Double get() = if (amount == 0.0) 0.0 else helium / amount

    val fractionN2: Double get() = if (amount == 0.0) 0.0 else nitrogen / amount

    /** In bar, at [TEMPERATURE]. */
    val pressure: Double get() = pressureOf(oxygen, nitrogen, helium)

    /**
     * The mix in whole percentages, which is the nearest a [Gas] holds.
     *
     * The fractions are finer, and a blend is analysed to a tenth, so a front end shows both.
     */
    val gas: Gas
        get() {
            val percentO2 = (fractionO2 * PERCENT).roundToInt()
            // Two halves rounded up would come to 101 %.
            return Gas(percentO2, (fractionHe * PERCENT).roundToInt().coerceAtMost(PERCENT.toInt() - percentO2))
        }

    /** How many litres this is at one bar in a cylinder of [size] litres, as a diver counts gas. */
    fun litresIn(size: Double): Double = amount * size * GAS_CONSTANT * TEMPERATURE

    internal operator fun plus(other: Contents): Contents =
        Contents(oxygen + other.oxygen, nitrogen + other.nitrogen, helium + other.helium)

    companion object {

        val EMPTY = Contents(0.0, 0.0, 0.0)
    }
}

/**
 * What a cylinder of [gas] holds at [bar].
 *
 * The pressure is taken as the pressure of the gas, so an empty cylinder holds nothing. A gauge
 * reads a bar less than that, which is within what it shows. `LOGIC-45`.
 */
fun contentsOf(gas: Gas, bar: Double): Contents {
    requirePressure(bar)
    return reaching(bar) { amount -> amountOf(gas, amount) }
}

/**
 * What a cylinder holding [start] holds once [with] has been added until it reads [bar].
 *
 * [bar] is no lower than the pressure [start] is at, since adding a gas lets none out.
 */
fun toppedUp(start: Contents, with: Gas, bar: Double): Contents {
    requirePressure(bar)
    require(bar >= start.pressure - CLOSE) {
        "the pressure after a top-up should be at least the ${start.pressure} bar before it, but was $bar"
    }
    return reaching(bar) { amount -> start + amountOf(with, amount) }
}

/** Added is one gas of a blend: which, how much of it, and what the cylinder reads once it is in. */
class Added(val gas: Gas, val contents: Contents, val bar: Double)

/** Blend is how a mix is made in a cylinder that already holds something, or that it cannot be. */
sealed class Blend {

    /**
     * Recipe is the gases to add, in the order they are added.
     *
     * [drained] is the pressure the cylinder is let down to first, and absent where nothing is let
     * out. It is the highest that still leaves room for the mix, so the least gas is thrown away.
     * A gas the mix needs none of is not in [added], which is empty where the cylinder already
     * holds the mix.
     */
    class Recipe(val drained: Double?, val added: List<Added>) : Blend()

    /** Impossible is the answer where no amounts of the gases make the mix, in an empty cylinder either. */
    object Impossible : Blend()
}

/**
 * How [wanted] is made in a cylinder holding [start], by adding gases of [from] in the order listed.
 *
 * The amounts are the ones that leave the cylinder holding exactly [wanted], and each pressure is
 * what the cylinder reads after that gas at [TEMPERATURE], so the recipe is filled by gauge. Where
 * more gases are listed than the mix needs, the fewest are used and the earlier listed preferred.
 *
 * What [start] holds is used as far as it fits. Where it holds too much of something, the cylinder is
 * drained to the highest pressure from which the mix can still be made.
 */
fun blended(start: Contents, wanted: Contents, from: List<Gas>): Blend {
    val gases = from.distinct()
    val target = doubleArrayOf(wanted.oxygen, wanted.nitrogen, wanted.helium)
    val held = doubleArrayOf(start.oxygen, start.nitrogen, start.helium)
    val close = CLOSE * (1 + wanted.amount)
    val chosen = subsetsOf(gases.size, COMPONENTS)

    fun recipe(drained: Double?, kept: Contents, picked: List<Int>, amounts: List<Double>): Blend.Recipe {
        var running = kept
        val added = ArrayList<Added>()
        for ((index, amount) in picked.zip(amounts)) {
            if (amount <= close) continue
            val contents = amountOf(gases[index], amount)
            running += contents
            added += Added(gases[index], contents, running.pressure)
        }
        return Blend.Recipe(drained, added)
    }

    // With nothing let out: the gases make up the difference.
    val missing = DoubleArray(COMPONENTS) { target[it] - held[it] }
    for (picked in chosen) {
        val amounts = solved(picked.map { fractionsOf(gases[it]) }, missing, close) ?: continue
        if (amounts.all { it >= -close }) return recipe(null, start, picked, amounts)
    }

    // With some let out: what is kept is one more gas, and the most of it that fits is wanted.
    var best: Blend.Recipe? = null
    var most = 0.0
    if (start.amount > 0.0) {
        val mix = DoubleArray(COMPONENTS) { held[it] / start.amount }
        for (picked in chosen.filter { it.size < COMPONENTS }) {
            val amounts = solved(listOf(mix) + picked.map { fractionsOf(gases[it]) }, target, close) ?: continue
            if (amounts.any { it < -close } || amounts.first() >= start.amount) continue
            val kept = amounts.first().coerceAtLeast(0.0)
            if (best != null && kept <= most + close) continue
            val left = Contents(mix[0] * kept, mix[1] * kept, mix[2] * kept)
            most = kept
            best = recipe(left.pressure, left, picked, amounts.drop(1))
        }
    }
    return best ?: Blend.Impossible
}

/** The pressure of a mix of these amounts, in bar: a virial equation in the amount of each gas. */
private fun pressureOf(oxygen: Double, nitrogen: Double, helium: Double): Double {
    var sum = oxygen + nitrogen + helium
    for (term in TERMS) {
        sum += term.weight * power(oxygen, term.oxygen) * power(nitrogen, term.nitrogen) * power(helium, term.helium)
    }
    return sum * GAS_CONSTANT * TEMPERATURE
}

private fun power(base: Double, exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= base }
    return result
}

/** [amount] moles a litre of [gas]. */
private fun amountOf(gas: Gas, amount: Double): Contents =
    Contents(gas.fractionO2 * amount, gas.fractionN2 * amount, gas.fractionHe * amount)

private fun fractionsOf(gas: Gas): DoubleArray = doubleArrayOf(gas.fractionO2, gas.fractionN2, gas.fractionHe)

/**
 * What [holding] an amount comes to at the amount that reads [bar].
 *
 * By halving, which needs only that more gas reads a higher pressure.
 */
private fun reaching(bar: Double, holding: (Double) -> Contents): Contents {
    var low = 0.0
    var high = 1.0
    while (holding(high).pressure < bar) high *= 2
    repeat(HALVINGS) {
        val middle = (low + high) / 2
        if (holding(middle).pressure < bar) low = middle else high = middle
    }
    return holding((low + high) / 2)
}

private fun requirePressure(bar: Double) {
    require(bar in 0.0..MOST_PRESSURE) { "a pressure should be 0 to ${MOST_PRESSURE.roundToInt()} bar, but was $bar" }
}

/**
 * How much of each of [columns] adds up to [sum], or null where no amounts do.
 *
 * Least squares, kept only where nothing is left over: fewer gases than components cannot make
 * every mix, and what they cannot make is refused rather than approximated. Null too where two
 * columns are one mix, which has no single answer.
 */
private fun solved(columns: List<DoubleArray>, sum: DoubleArray, close: Double): List<Double>? {
    val count = columns.size
    val rows = Array(count) { row ->
        DoubleArray(count + 1) { column ->
            val other = if (column < count) columns[column] else sum
            (0..<COMPONENTS).sumOf { columns[row][it] * other[it] }
        }
    }
    for (pivot in 0..<count) {
        val largest = (pivot..<count).maxBy { abs(rows[it][pivot]) }
        if (abs(rows[largest][pivot]) < SINGULAR) return null
        rows[pivot] = rows[largest].also { rows[largest] = rows[pivot] }
        for (row in 0..<count) {
            if (row == pivot) continue
            val factor = rows[row][pivot] / rows[pivot][pivot]
            for (column in pivot..count) rows[row][column] -= factor * rows[pivot][column]
        }
    }
    val amounts = List(count) { rows[it][count] / rows[it][it] }
    val exact = (0..<COMPONENTS).all { component ->
        abs(amounts.indices.sumOf { amounts[it] * columns[it][component] } - sum[component]) <= close
    }
    return if (exact) amounts else null
}

/** Every choice of at most [most] of [count] things, the fewest first and then the earliest. */
private fun subsetsOf(count: Int, most: Int): List<List<Int>> {
    var grown = listOf(emptyList<Int>())
    val all = ArrayList(grown)
    repeat(minOf(most, count)) {
        grown = grown.flatMap { chosen -> ((chosen.lastOrNull() ?: -1) + 1..<count).map { chosen + it } }
        all += grown
    }
    return all
}

/**
 * Term is one term of the virial equation: a coefficient and the power of each gas's amount.
 *
 * The powers add up to the term's order, from two to four. The coefficient is in litres a mole to
 * the order less one.
 */
private class Term(val oxygen: Int, val nitrogen: Int, val helium: Int, coefficient: Double) {

    /** The coefficient times the number of orders the same gases can be written in. */
    val weight: Double = coefficient * factorial(oxygen + nitrogen + helium) /
        (factorial(oxygen) * factorial(nitrogen) * factorial(helium))
}

private fun factorial(of: Int): Int = (1..of).fold(1) { product, factor -> product * factor }

/**
 * The virial coefficients of oxygen, nitrogen and helium and of their mixtures at [TEMPERATURE].
 *
 * Fitted to the reference equation of state of each gas and to the GERG-2008 mixing rules, up to
 * [MOST_PRESSURE]. Within 0.1 % of the reference for a pure gas and 0.3 % for a mix. `LOGIC-45`.
 */
private val TERMS: List<Term> = listOf(
    Term(2, 0, 0, -0.0166745),
    Term(1, 1, 0, -0.0101054),
    Term(1, 0, 1, 0.0237298),
    Term(0, 2, 0, -0.0052171),
    Term(0, 1, 1, 0.0208809),
    Term(0, 0, 2, 0.0118708),
    Term(3, 0, 0, 0.000891966),
    Term(2, 1, 0, 0.00085841),
    Term(2, 0, 1, 0.000917899),
    Term(1, 2, 0, 0.000703376),
    Term(1, 1, 1, 0.00105412),
    Term(1, 0, 2, -0.000448794),
    Term(0, 3, 0, 0.00108422),
    Term(0, 2, 1, 0.000583093),
    Term(0, 1, 2, 0.000260282),
    Term(0, 0, 3, 0.000103507),
    Term(4, 0, 0, 2.48079e-05),
    Term(3, 1, 0, 3.09816e-05),
    Term(3, 0, 1, 1.50111e-05),
    Term(2, 2, 0, 4.67454e-05),
    Term(2, 1, 1, 1.60103e-05),
    Term(2, 0, 2, -7.86645e-06),
    Term(1, 3, 0, 5.97215e-05),
    Term(1, 2, 1, 2.12847e-05),
    Term(1, 1, 2, -7.14383e-06),
    Term(1, 0, 3, 7.26363e-05),
    Term(0, 4, 0, 5.577e-05),
    Term(0, 3, 1, 2.88146e-05),
    Term(0, 2, 2, 1.91357e-05),
    Term(0, 1, 3, 1.45604e-06),
    Term(0, 0, 4, 9.9059e-07),
)

/** The highest pressure a cylinder is worked out at, in bar, which is as far as the fit reaches. */
const val MOST_PRESSURE = 340.0

/** The temperature every pressure here is at, in kelvin: twenty degrees Celsius. */
const val TEMPERATURE = 293.15

/** The gas constant in litre bar a mole a kelvin. */
private const val GAS_CONSTANT = 0.08314462618

/** Oxygen, nitrogen and helium. */
private const val COMPONENTS = 3

private const val PERCENT = 100.0

/** How many times a pressure is halved in on, which is past what a double can tell apart. */
private const val HALVINGS = 60

/** How near two amounts or two pressures are before they are the same one. */
private const val CLOSE = 1e-9

/** The pivot below which two gases are one mix. */
private const val SINGULAR = 1e-12
