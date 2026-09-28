"""Lift a dive's `details` block into the dive itself, in a logbook written before `DATA-122`.

A dive used to hold `dive_trip`, `operator` and `tags` inside an owned item called `details`,
with remarks of its own. They are now the dive's own fields, and a block's remarks have to join
the dive's by hand, which is reported rather than done.

    python tool/liftdetails.py <logbook folder>

The rewrite is line by line: the block's lines move out and lose one level of indent, and every
other line of the file is left exactly as it was written. A file holding no `details` is left
alone, so a second run finds nothing to do.
"""

import os
import re
import sys

OPENS = re.compile(r'^(\s*)"details": \{\s*$')


def lifted(lines):
    """[lines] with the details block lifted, and what could not be lifted, or None if there is none."""
    for at, line in enumerate(lines):
        opens = OPENS.match(line)
        if opens:
            break
    else:
        return None, []
    indent = opens.group(1)
    closes = next(i for i in range(at + 1, len(lines)) if lines[i].rstrip() in (indent + '},', indent + '}'))
    inner = lines[at + 1:closes]
    left = [name for name in re.findall(r'^\s*"([a-z_]+)":', '\n'.join(inner), re.M) if name == 'remarks']
    # The block's last field carries no comma; the field before the block's neighbour does.
    out = []
    for line in inner:
        out.append(line[2:] if line.startswith(indent + '  ') else line)
    if out and not out[-1].rstrip().endswith(',') and lines[closes].rstrip().endswith(','):
        out[-1] = out[-1].rstrip() + ','
    return lines[:at] + out + lines[closes + 1:], left


def main(folder):
    dives = os.path.join(folder, 'dive')
    paths = []
    if os.path.isdir(dives):
        paths = [os.path.join(dives, n) for n in sorted(os.listdir(dives)) if n.endswith('.json')]
    at = os.path.join(folder, 'dive.json')
    if os.path.isfile(at):
        paths.append(at)
    changed, owed = 0, []
    for path in paths:
        with open(path, encoding='utf-8') as file:
            lines = file.read().split('\n')
        out, left = lifted(lines)
        while out is not None:
            changed += 1 if out is not lines else 0
            lines = out
            if left:
                owed.append(path)
            out, left = lifted(lines)
        with open(path, 'w', encoding='utf-8', newline='\n') as file:
            file.write('\n'.join(lines))
    print('lifted details in %d of %d dive files' % (changed, len(paths)))
    for path in owed:
        print('%s held remarks on its details: fold them into the dive by hand' % path)


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1])
