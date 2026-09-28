"""Lift a dive's `entry` on to the site it names, in a logbook written before `DATA-123`.

A dive used to carry `entry` and `exit`; a site now carries `entry`. This reads every dive,
writes each site the entry its dives agree on, drops both fields from the dives, and names the
dives that disagreed so a remark can be written for each.

    python tool/liftentry.py <logbook folder> [--write]

Without `--write` it says what it would do and changes nothing. The rewrite is line by line, so
every other line of a file is left exactly as it was written.

A word a site's dives disagree on is not guessed at: the site is left alone and every dive there
is reported. The same goes for `exit`, which is dropped wherever it repeats the entry and
reported wherever it does not.
"""

import collections
import json
import os
import re
import sys

DROPPED = re.compile(r'^\s*"(entry|exit)":')


def dives_in(folder):
    """Every dive file in [folder], by path."""
    out = {}
    at = os.path.join(folder, 'dive')
    if os.path.isdir(at):
        for name in sorted(os.listdir(at)):
            if name.endswith('.json'):
                out[os.path.join(at, name)] = json.load(open(os.path.join(at, name), encoding='utf-8'))
    return out


def main(folder, write):
    dives = dives_in(folder)
    said = collections.defaultdict(collections.Counter)
    differs = []
    for path, dive in dives.items():
        entry, exit_ = dive.get('entry'), dive.get('exit')
        site = (dive.get('dive_site') or '').lstrip('@')
        if entry and site:
            said[site][entry] += 1
        if exit_ and exit_ != entry:
            differs.append((os.path.basename(path)[:-5], 'exit %s against entry %s' % (exit_, entry)))
    agreed, split = {}, {}
    for site, counted in said.items():
        if len(counted) == 1:
            agreed[site] = next(iter(counted))
        else:
            split[site] = dict(counted)
    print('%d dives, %d sites with an entry: %d agree, %d do not'
          % (len(dives), len(said), len(agreed), len(split)))
    for site, counted in sorted(split.items()):
        print('  %s is written %s: left alone, and every dive there wants a remark'
              % (site, counted))
    for name, why in differs:
        print('  %s: %s' % (name, why))
    if not write:
        print('nothing written; pass --write to do it')
        return
    for path in dives:
        lines = open(path, encoding='utf-8').read().split('\n')
        kept = [line for line in lines if not DROPPED.match(line)]
        if kept != lines:
            with open(path, 'w', encoding='utf-8', newline='\n') as file:
                file.write('\n'.join(kept))
    written = 0
    for site, entry in sorted(agreed.items()):
        if writeSite(folder, site, entry):
            written += 1
    print('dropped the fields from the dives, and wrote entry on %d sites' % written)


def writeSite(folder, site, entry):
    """Writes [entry] on to [site], after its `water_type` or at its end. Whether it landed."""
    one = os.path.join(folder, 'dive_site', site + '.json')
    if os.path.isfile(one):
        lines = open(one, encoding='utf-8').read().split('\n')
        out = insert(lines, entry, '  ')
        if out is None:
            return False
        open(one, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
        return True
    grouped = os.path.join(folder, 'dive_site.json')
    if not os.path.isfile(grouped):
        return False
    lines = open(grouped, encoding='utf-8').read().split('\n')
    at = next((i for i, line in enumerate(lines) if re.match(r'^  "%s": \{' % re.escape(site), line)), None)
    if at is None:
        return False
    ends = next(i for i in range(at + 1, len(lines)) if lines[i].rstrip() in ('  },', '  }'))
    out = insert(lines[at:ends], entry, '    ')
    if out is None:
        return False
    lines[at:ends] = out
    open(grouped, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
    return True


def insert(lines, entry, indent):
    """[lines] with an `entry` line after the water type, or None where it already has one."""
    if any(re.match(r'^\s*"entry":', line) for line in lines):
        return None
    written = '%s"entry": "%s",' % (indent, entry)
    for i, line in enumerate(lines):
        if re.match(r'^\s*"water_type":', line):
            return lines[:i + 1] + [written] + lines[i + 1:]
    # No water type to follow, so it goes first among the fields.
    for i, line in enumerate(lines):
        if re.match(r'^\s*"[a-z_]+":', line):
            return lines[:i] + [written] + lines[i:]
    return None


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], '--write' in sys.argv)
