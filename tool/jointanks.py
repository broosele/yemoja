"""Join a tank and the mix it was breathed on, in dives downloaded before `LOGIC-42`.

A Perdix reported the transmitter's tank and the gas list as separate slots, so a dive on one
cylinder was written as two gas sources: one carrying the pressures and no mix, the other the mix
and no pressures. This joins them where the recording says the mix was the gas the dive began on,
which is the same rule a download now applies.

    python tool/jointanks.py <logbook folder> [--write]

Without `--write` it says what it would do and changes nothing. A dive whose second slot was
switched to later, or whose slots both carry pressures, is left alone: that is two cylinders.
"""

import json
import os
import re
import sys

AT_THE_START = 60


def dives_in(folder):
    out = {}
    at = os.path.join(folder, 'dive')
    if os.path.isdir(at):
        for name in sorted(os.listdir(at)):
            if name.endswith('.json'):
                path = os.path.join(at, name)
                out[path] = json.load(open(path, encoding='utf-8'))
    return out


def joinable(dive):
    """The keys to join as (tank, mix), or None where this dive is not the case."""
    sources = dive.get('gas_sources') or {}
    if len(sources) != 2:
        return None
    pressured = [key for key, held in sources.items()
                 if 'start_pressure' in held or 'end_pressure' in held]
    if len(pressured) != 1:
        return None
    tank = pressured[0]
    mixed = [key for key, held in sources.items() if 'gas_type' in held and key != tank]
    if len(mixed) != 1:
        return None
    mix = mixed[0]
    # The tank slot may carry a mix of its own, and the same one says nothing about two cylinders.
    theirs = sources[tank].get('gas_type')
    if theirs is not None and theirs.lower() != str(sources[mix].get('gas_type')).lower():
        return None
    switches, pressures = [], set()
    for run in (dive.get('profiles') or {}).values():
        for at, named in (run.get('gas_switches') or []):
            switches.append((at, str(named).lstrip('*')))
        for key in (run.get('pressures') or {}):
            pressures.add(key)
    if len(switches) != 1:
        return None
    at, named = switches[0]
    if named != mix or at > AT_THE_START:
        return None
    if mix in pressures:
        return None
    return tank, mix


def main(folder, write):
    joined, left = 0, []
    for path, dive in dives_in(folder).items():
        pair = joinable(dive)
        if pair is None:
            if len(dive.get('gas_sources') or {}) > 1:
                left.append(os.path.basename(path)[:-5])
            continue
        tank, mix = pair
        print('%s: %s and %s are one cylinder' % (os.path.basename(path)[:-5], tank, mix))
        joined += 1
        if write:
            rewrite(path, dive, tank, mix)
    print('%d dives to join; %d with several sources left as they are' % (joined, len(left)))
    if not write:
        print('nothing written; pass --write to do it')


def rewrite(path, dive, tank, mix):
    """Writes [path] with the two sources as one, line by line so the rest of the file stands."""
    sources = dive.get('gas_sources')
    missing = {name: held for name, held in sources[mix].items() if name not in sources[tank]}
    lines = open(path, encoding='utf-8').read().split(chr(10))
    out, at = [], 0
    while at < len(lines):
        line = lines[at]
        opens = re.match(r'^(\s*)"%s": \{' % re.escape(mix), line)
        if opens and inside_sources(lines, at):
            # The block goes, and the line before it loses the comma it carried to nothing.
            ends = next(i for i in range(at, len(lines))
                        if lines[i].rstrip() in (opens.group(1) + '},', opens.group(1) + '}'))
            if lines[ends].rstrip().endswith('}') and out and out[-1].rstrip().endswith(','):
                out[-1] = out[-1].rstrip()[:-1]
            at = ends + 1
            continue
        keeps = re.match(r'^(\s*)"%s": \{' % re.escape(tank), line)
        if keeps and inside_sources(lines, at) and missing:
            out.append(line)
            for name, held in missing.items():
                out.append('%s  "%s": %s,' % (keeps.group(1), name, json.dumps(held)))
            at += 1
            continue
        # Whatever named the mix now names the tank, the two being one source.
        out.append(line.replace('"*%s"' % mix, '"*%s"' % tank))
        at += 1
    open(path, 'w', encoding='utf-8', newline=chr(10)).write(chr(10).join(out))


def inside_sources(lines, at):
    """Whether the line at [at] sits inside the dive's own gas_sources rather than a profile's."""
    for i in range(at, -1, -1):
        if re.match(r'^  "[a-z_]+": [\{\[]', lines[i]):
            return lines[i].startswith('  "gas_sources"')
    return False


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], '--write' in sys.argv)
