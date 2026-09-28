"""Move what a computer measured on to the recording that measured it, for `DATA-124`.

A download used to write `atmospheric_pressure`, `bottom_temperature` and `surface_temperature`
on to a dive's environment. Each recording now carries its own. This moves them on to the dive's
primary recording, or on to its only one, and drops the pressure from the environment, which no
longer has that field.

    python tool/liftconditions.py <logbook folder> [--write]

Without `--write` it says what it would do and changes nothing. The two temperatures are left on
the environment as well, where they now read as written rather than derived; pass `--only-pressure`
to move nothing else. A dive with several recordings and none named primary is reported and left
alone, there being no way to tell which computer measured what.
"""

import json
import os
import re
import sys

MOVED = ('atmospheric_pressure', 'bottom_temperature', 'surface_temperature')


def dives_in(folder):
    out = {}
    at = os.path.join(folder, 'dive')
    if os.path.isdir(at):
        for name in sorted(os.listdir(at)):
            if name.endswith('.json'):
                path = os.path.join(at, name)
                out[path] = json.load(open(path, encoding='utf-8'))
    return out


def primary_of(dive):
    """The key of the recording a dive's figures come from, or None where there is no one answer."""
    profiles = {key: value for key, value in (dive.get('profiles') or {}).items()
                if not value.get('planned')}
    if not profiles:
        return None
    named = dive.get('primary_profile')
    if isinstance(named, str) and named.lstrip('*') in profiles:
        return named.lstrip('*')
    if len(profiles) == 1:
        return next(iter(profiles))
    return None


def main(folder, write, only_pressure):
    moving = [field for field in MOVED if field == 'atmospheric_pressure' or not only_pressure]
    dives, owed, split = dives_in(folder), 0, []
    for path, dive in dives.items():
        held = {name: value for name, value in (dive.get('environment') or {}).items()
                if name in moving}
        if not held:
            continue
        key = primary_of(dive)
        if key is None:
            split.append(os.path.basename(path)[:-5])
            continue
        owed += 1
        if not write:
            continue
        move(path, key, held, only_pressure)
    print('%d dives carry conditions to move, %d cannot be placed' % (owed, len(split)))
    for name in split:
        print('  %s names no primary recording, and has more than one' % name)
    if not write:
        print('nothing written; pass --write to do it')


def move(path, key, held, only_pressure):
    """Writes [held] on to the recording under [key], and takes the pressure off the environment."""
    lines = open(path, encoding='utf-8').read().split('\n')
    lines = without_pressure(lines)
    at = next((i for i, line in enumerate(lines)
               if re.match(r'^\s*"%s": \{' % re.escape(key), line)), None)
    if at is None:
        return
    written = ['      "%s": %s,' % (name, json.dumps(value)) for name, value in held.items()
               if name == 'atmospheric_pressure' or not only_pressure]
    lines[at + 1:at + 1] = written
    open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))


def without_pressure(lines):
    """[lines] with the environment's pressure taken out, and the block dropped where it empties."""
    out = []
    for line in lines:
        if re.match(r'^\s{4}"atmospheric_pressure":', line):
            # A field that ended its object leaves the one before it carrying a comma to nothing.
            if not line.rstrip().endswith(',') and out and out[-1].rstrip().endswith(','):
                out[-1] = out[-1].rstrip()[:-1]
            continue
        out.append(line)
    at = next((i for i, line in enumerate(out) if line.rstrip() == '  "environment": {'), None)
    if at is not None and out[at + 1].rstrip() in ('  },', '  }'):
        # An environment holding nothing is not written at all.
        last = out[at + 1].rstrip() == '  }'
        del out[at:at + 2]
        if last and at > 0 and out[at - 1].rstrip().endswith(','):
            out[at - 1] = out[at - 1].rstrip()[:-1]
    return out


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], '--write' in sys.argv, '--only-pressure' in sys.argv)
