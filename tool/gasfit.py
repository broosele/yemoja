"""Fit the virial coefficients the gas mix calculation works a cylinder's pressure out with.

The coefficients are the table in logic/src/commonMain/kotlin/yemoja/logic/Blending.kt, and
`LOGIC-45` in logic/doc.md says why they are what they are. They are fitted to the reference
equation of state of oxygen, nitrogen and helium and to the GERG-2008 mixing rules for their
mixtures, both as CoolProp computes them, at twenty degrees Celsius and up to 340 bar. Run again
only to move the temperature or the range, or to check the table:

    pip install CoolProp numpy
    python tool/gasfit.py

It prints how far the fit is from the reference, on the points it was fitted to and on four
hundred mixes it was not, and then the table as Blending.kt writes it. Nothing in the build or
the checks runs it.
"""
import itertools
import random
from collections import Counter
from math import factorial

import CoolProp.CoolProp as CP
import numpy as np

GAS_CONSTANT = 0.08314462618    # litre bar a mole a kelvin
TEMPERATURE = 293.15            # kelvin
MOST_PRESSURE = 340             # bar
GASES = ['Oxygen', 'Nitrogen', 'Helium']
ORDERS = (2, 3, 4)
STEPS = 20                      # a mix every five per cent of each gas


def density(fractions, bar):
    """Moles a litre of a mix at a pressure, by the reference."""
    parts = [(gas, part) for gas, part in zip(GASES, fractions) if part > 0]
    mix = parts[0][0] if len(parts) == 1 else '&'.join(f'{gas}[{part}]' for gas, part in parts)
    return CP.PropsSI('Dmolar', 'T', TEMPERATURE, 'P', bar * 1e5, 'HEOS::' + mix) / 1000


def points(mixes, pressures):
    """Each mix at each pressure: the fractions, the density and the compressibility."""
    found = []
    for fractions in mixes:
        for bar in pressures:
            moles = density(fractions, bar)
            found.append((fractions, moles, bar / (moles * GAS_CONSTANT * TEMPERATURE)))
    return found


TERMS = [term for order in ORDERS for term in itertools.combinations_with_replacement(range(3), order)]


def weights(fractions, moles):
    """What each coefficient is multiplied by in the compressibility of a mix, less one."""
    row = []
    for term in TERMS:
        orders = factorial(len(term))
        for count in Counter(term).values():
            orders //= factorial(count)
        row.append(orders * np.prod([fractions[gas] for gas in term]) * moles ** (len(term) - 1))
    return row


def fitted(found, held, free):
    """The free coefficients that bring the fit nearest the points, those in held staying."""
    rows = np.array([weights(fractions, moles) for fractions, moles, _ in found])
    reference = np.array([z for *_, z in found])
    rest = (reference - 1 - rows[:, list(held)] @ np.array(list(held.values()))) / reference
    answer, *_ = np.linalg.lstsq(rows[:, free] / reference[:, None], rest, rcond=None)
    return {index: float('%.6g' % value) for index, value in zip(free, answer)}


def furthest(found, coefficients):
    """How far the fit is from the reference at worst and on the whole, in per cent."""
    values = np.array([coefficients[index] for index in range(len(TERMS))])
    off = [(np.dot(weights(fractions, moles), values) + 1 - z) / z for fractions, moles, z in found]
    return 100 * max(abs(each) for each in off), 100 * float(np.sqrt(np.mean(np.square(off))))


def main():
    coefficients = {}
    # Each gas by itself first, so that a mix which is one gas is that gas's own curve.
    for gas in range(3):
        alone = tuple(1.0 if other == gas else 0.0 for other in range(3))
        own = [index for index, term in enumerate(TERMS) if set(term) == {gas}]
        coefficients.update(fitted(points([alone], [2, 5] + list(range(10, MOST_PRESSURE + 1, 10))), {}, own))
    # Then what two and three gases share, with the pure gases held.
    grid = [(a / STEPS, b / STEPS, (STEPS - a - b) / STEPS) for a in range(STEPS + 1) for b in range(STEPS + 1 - a)]
    mixes = points(grid, [2, 5, 10, 20] + list(range(40, MOST_PRESSURE + 1, 20)))
    shared = [index for index, term in enumerate(TERMS) if len(set(term)) > 1]
    coefficients.update(fitted(mixes, dict(coefficients), shared))

    print('fitted on %d points: %.3f %% at worst, %.3f %% on the whole' % ((len(mixes),) + furthest(mixes, coefficients)))
    random.seed(1)
    others = []
    for _ in range(400):
        first, second = sorted((random.random(), random.random()))
        others += points([(first, second - first, 1 - second)], [random.uniform(1, MOST_PRESSURE)])
    print('checked on %d others: %.3f %% at worst, %.3f %% on the whole' % ((len(others),) + furthest(others, coefficients)))
    pure = points([(1, 0, 0), (0, 1, 0), (0, 0, 1)], range(10, MOST_PRESSURE + 1, 10))
    print('the pure gases: %.3f %% at worst, %.3f %% on the whole' % furthest(pure, coefficients))
    print()
    for index, term in enumerate(TERMS):
        powers = ', '.join(str(term.count(gas)) for gas in range(3))
        print('    Term(%s, %s),' % (powers, repr(coefficients[index])))


if __name__ == '__main__':
    main()
