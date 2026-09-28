"""Move what a computer measured on to the recording that measured it, for `DATA-124`.

A download used to write what it measured on to the dive: `atmospheric_pressure`,
`bottom_temperature` and `surface_temperature` on the environment, and `max_depth`,
`average_depth` and `duration` on the dive itself. Each recording now carries its own. This moves
the named fields on to the dive's primary recording, or on to its only one.

    python tool/liftconditions.py <logbook folder> [--fields=a,b] [--write]

Without `--write` it says what it would do and changes nothing. `--fields` names what to move, and
defaults to the pressure alone, that being the one field a dive no longer has anywhere to keep. A
dive with several recordings and none named primary is reported and left alone, there being no way
to tell which computer measured what; a dive with no recording is left alone too, its figures
being its own.
"""

import json
import os
import re
import sys

ENVIRONMENT = ('atmospheric_pressure', 'bottom_temperature', 'surface_temperature')


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


def held_by(dive, fields):
    """What [dive] holds of [fields], on itself or on its environment, by name."""
    out = {}
    for name in fields:
        where = dive.get('environment') or {} if name in ENVIRONMENT else dive
        if name in where:
            out[name] = where[name]
    return out


def main(folder, fields, write):
    dives, owed, split = dives_in(folder), 0, []
    for path, dive in dives.items():
        held = held_by(dive, fields)
        if not held:
            continue
        key = primary_of(dive)
        if key is None:
            # A dive with no recording keeps its own figures; one with several and no primary
            # cannot say which computer measured them.
            if any(not run.get('planned') for run in (dive.get('profiles') or {}).values()):
                split.append(os.path.basename(path)[:-5])
            continue
        owed += 1
        if write:
            move(path, key, held)
    print('%d dives carry %s to move, %d cannot be placed'
          % (owed, ' or '.join(fields), len(split)))
    for name in split:
        print('  %s has no one recording to move them to' % name)
    if not write:
        print('nothing written; pass --write to do it')


def move(path, key, held):
    """Writes [held] on to the recording under [key], and takes each field off the dive."""
    lines = open(path, encoding='utf-8').read().split('\n')
    lines = without(lines, held)
    at = next((i for i, line in enumerate(lines)
               if re.match(r'^\s*"%s": \{' % re.escape(key), line)), None)
    if at is None:
        return
    lines[at + 1:at + 1] = ['      "%s": %s,' % (name, json.dumps(value))
                            for name, value in held.items()]
    open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))


def without(lines, held):
    """[lines] with each moved field taken out, and an environment dropped where it empties."""
    gone = re.compile(r'^\s*"(%s)":' % '|'.join(re.escape(name) for name in held))
    out = []
    for line in lines:
        if gone.match(line) and not line.startswith('        '):
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
    named = next((arg[len('--fields='):] for arg in sys.argv if arg.startswith('--fields=')), None)
    main(sys.argv[1], tuple(named.split(',')) if named else ('atmospheric_pressure',),
         '--write' in sys.argv)
