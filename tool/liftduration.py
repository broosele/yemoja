"""Turn a dive's end time into a duration, in a logbook written before `DATA-125`.

A dive used to carry `end_date` and `end_time`; it now carries `duration` and nothing about its
end. This writes the duration its two times came to, in seconds, and drops both fields. A dive
whose end time is earlier than its start is taken to have crossed midnight, which is the rule
that applied while it was written.

    python tool/liftduration.py <logbook folder> [--write]

Without `--write` it says what it would do and changes nothing. A dive with a recording needs
nothing: its duration comes from the samples, so both fields are dropped and no duration written.
"""

import json
import os
import re
import sys

DAY = 24 * 60 * 60
DROPPED = re.compile(r'^\s*"(end_date|end_time)":')


def seconds_of(said):
    """The seconds a `hh:mm:ss` says, or None where it says something else."""
    parts = (said or '').split(':')
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        return None
    hours, minutes, seconds = (int(part) for part in parts)
    return hours * 3600 + minutes * 60 + seconds


def ran_for(dive):
    """How long [dive] ran by its own times, or None where they do not place both ends."""
    start, end = seconds_of(dive.get('start_time')), seconds_of(dive.get('end_time'))
    if start is None or end is None:
        return None
    return end - start + (DAY if end < start else 0)


def main(folder, write):
    at = os.path.join(folder, 'dive')
    paths = [os.path.join(at, name) for name in sorted(os.listdir(at)) if name.endswith('.json')]
    written, dropped, owed = 0, 0, []
    for path in paths:
        dive = json.load(open(path, encoding='utf-8'))
        if 'end_time' not in dive and 'end_date' not in dive:
            continue
        recorded = any(not run.get('planned') for run in (dive.get('profiles') or {}).values())
        ran = None if recorded or 'duration' in dive else ran_for(dive)
        if not recorded and ran is None and 'duration' not in dive:
            owed.append(os.path.basename(path)[:-5])
        dropped += 1
        if ran is not None:
            written += 1
        if write:
            rewrite(path, ran)
    print('%d dives carry an end: %d get a duration from it' % (dropped, written))
    for name in owed:
        print('  %s has an end and no start time, so nothing says how long it ran' % name)
    if not write:
        print('nothing written; pass --write to do it')


def rewrite(path, ran):
    """Drops the two fields from [path], writing [ran] as a duration where there is one."""
    lines = open(path, encoding='utf-8').read().split('\n')
    out = []
    for line in lines:
        if DROPPED.match(line):
            if ran is not None and line.lstrip().startswith('"end_time"'):
                out.append('%s"duration": %d%s'
                           % (line[:len(line) - len(line.lstrip())], ran,
                              ',' if line.rstrip().endswith(',') else ''))
                continue
            # A field that ended its object leaves the one before it carrying a comma to nothing.
            if not line.rstrip().endswith(',') and out and out[-1].rstrip().endswith(','):
                out[-1] = out[-1].rstrip()[:-1]
            continue
        out.append(line)
    open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], '--write' in sys.argv)
