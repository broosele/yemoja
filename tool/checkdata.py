"""Check the test fixtures and the libraries against the field lists in the manual.

The manual is the definition of every data field, so it is the thing to check against:
if a fixture disagrees with it, one of the two is wrong and it is worth knowing which.
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANUAL = os.path.join(ROOT, 'manual', 'data-fields.md')
DESCRIPTIONS = os.path.join(
    ROOT, 'logic', 'src', 'commonMain', 'kotlin', 'yemoja', 'logic', 'Types.kt')

# What each kind in the manual is written as in the logic layer. The manual names a kind
# in prose -- `(text)`, `(true or false)` -- and one class implements each. A kind absent
# here is one nothing describes yet, and is passed over rather than guessed at.
KINDS = {
    'text': 'TextDescription',
    'multiline text': 'MultilineTextDescription',
    'fixed set': 'TextDescription',
    'number': 'NumberDescription',
    'whole number': 'WholeNumberDescription',
    'date': 'DateDescription',
    'time': 'TimeDescription',
    'true or false': 'BooleanDescription',
    'gas': 'GasDescription',
    'reference': 'ReferenceDescription',
    'key reference': 'KeyReferenceDescription',
    'owned item': 'OwnedItemDescription',
}

# Defined in the manual's introduction rather than in any one type's list. A key is
# not among them: an entry sits *under* its key and never carries one as a field.
COMMON = {'remarks'}
# Fields holding a single owned item, and the section describing it.
OWNED = {
    'details': 'Details', 'environment': 'Environment', 'gear': 'Dive gear',
    'medical': 'Medical', 'insurance': 'Insurance', 'buoyancy': 'Buoyancy',
}
# Fields holding several owned items, each under a key. Both are JSON objects, so
# only this tells them apart: here the values are the entries, there the fields are.
KEYED = {
    'courses': 'Course', 'maintenances': 'Maintenance', 'gas_sources': 'Gas source',
    'profiles': 'Profile',
}
# `pressures` holds a series under each gas-source key, not owned items, so there is
# nothing to walk into and no field list to check it against.
OPAQUE = {'pressures'}
# Where each kind of item lives inside a logbook: a folder of its own name holding one
# file per item, or a single file of that name holding them all. Fixed by convention, so
# that a logbook contains no paths at all, and the name is the type's own, so there is no
# table here mapping one to the other.
TYPE_NAMES = {
    'region': 'Region', 'gear': 'Gear', 'certification': 'Certification',
    'dive_site': 'Dive site', 'person': 'Person', 'operator': 'Operator',
    'dive_trip': 'Dive trip', 'dive': 'Dive', 'wreck': 'Wreck',
}


# A fixed set that borrows another's vocabulary rather than repeating it. The manual
# says "the same six steps" and is right not to write them twice; the tool has to be
# told, because it cannot read that.
SHARED = {('Environment', 'waves'): ('Environment', 'current')}


def manual_vocabularies():
    """What a fixed set may hold, and what range a number may fall in.

    Both are stated in prose, so both are read conservatively. A fixed set collects
    every backticked value in its bullet, including the ones the prose repeats while
    explaining the steps; a stray field name caught that way widens the set by one
    word nobody would write, which is the safe direction to be wrong in. A range is
    read only from the exact phrase "from N to M".
    """
    text = io.open(MANUAL, encoding='utf-8').read()
    sets, ranges, current, bullet, field, kind = {}, {}, None, None, None, None
    # Prose inside a bullet backticks other fields while explaining itself. Those are
    # names, not values, and every one of them is already known.
    known_fields = set().union(*manual_fields().values())

    def close():
        if not bullet or not field:
            return
        if kind == 'fixed set':
            values = set(re.findall(r'`([a-z0-9_ ]+)`', bullet)) - known_fields
            if values:
                sets[(current, field)] = values
        span = re.search(r'from (\d+) to (\d+)', bullet)
        if span:
            ranges[(current, field)] = (int(span.group(1)), int(span.group(2)))

    for line in text.split('\n'):
        heading = re.match(r'^#{3,5} (.+)$', line)
        start = re.match(r'^- `([a-z_]+)` \((fixed set|whole number|number)\)(.*)$', line)
        if heading or start or (line.startswith('- ') and bullet):
            close()
            bullet, field, kind = None, None, None
        if heading:
            current = heading.group(1)
        elif start:
            field, kind, bullet = start.group(1), start.group(2), start.group(3)
        elif bullet is not None and line.startswith('  '):
            bullet += ' ' + line.strip()
    close()

    for target, source in SHARED.items():
        if source in sets:
            sets[target] = sets[source]
    return sets, ranges


def manual_fields():
    """Every field the manual defines, per item type and owned item.

    The whole chapter is field lists, so there is nothing to slice out: a heading
    names an item type or an owned item, and a bullet opening with backticked names
    defines fields on whatever heading is in force.
    """
    text = io.open(MANUAL, encoding='utf-8').read()
    fields, current = {}, None
    for line in text.split('\n'):
        heading = re.match(r'^#{3,5} (.+)$', line)
        if heading:
            current = heading.group(1)
            fields[current] = set(COMMON)
        item = re.match(r'^- (`[a-z_]+`(?:, `[a-z_]+`)*)', line)
        if item and current:
            fields[current] |= set(re.findall(r'`([a-z_]+)`', item.group(1)))
    return fields


def manual_kinds():
    """The kind the manual gives each field, per item type and owned item.

    One bullet may open with several names sharing a kind -- `west`, `east`, `south`,
    `north` are all numbers -- so every name in the opening run takes the same one.
    `remarks` is defined in the introduction rather than in a bullet, and is the only
    multiline text there is.
    """
    text = io.open(MANUAL, encoding='utf-8').read()
    kinds, current = {}, None
    for line in text.split('\n'):
        heading = re.match(r'^#{3,5} (.+)$', line)
        if heading:
            current = heading.group(1)
            kinds[(current, 'remarks')] = 'multiline text'
        bullet = re.match(r'^- (`[a-z_]+`(?:, `[a-z_]+`)*) \(([a-z ]+?)\)', line)
        if bullet and current:
            for name in re.findall(r'`([a-z_]+)`', bullet.group(1)):
                kinds[(current, name)] = bullet.group(2)
    return kinds


def described_types():
    """Every item type the logic layer describes, as {storage name: {field: class}}.

    Read out of the source rather than by running it, so this stays one Python script
    with no Kotlin toolchain behind it. A field written through a shared value --
    `REMARKS` -- is resolved from that value's own declaration.
    """
    if not os.path.exists(DESCRIPTIONS):
        return {}
    text = re.sub(r'//[^\n]*', '', io.open(DESCRIPTIONS, encoding='utf-8').read())
    shared = {
        name: (kind, field)
        for name, kind, field in re.findall(
            r'val ([A-Z_]+)[^=\n]*=\s*(\w+Description)\("([a-z_]+)"', text)
    }
    types = {}
    for match in re.finditer(
            r'ItemDescription\(\s*"([a-z_]+)"\s*,\s*listOf\(', text):
        body = balanced(text, text.index('(', match.end() - len('listOf(')))
        fields = {field: kind for kind, field in
                  re.findall(r'(\w+Description)\("([a-z_]+)"', body)}
        for name in re.findall(r'\b([A-Z_]{2,})\b', body):
            if name in shared:
                fields[shared[name][1]] = shared[name][0]
        types[match.group(1)] = fields
    return types


def balanced(text, opening):
    """What sits between [opening] and the bracket closing it, quotes respected."""
    depth, at = 0, opening
    while at < len(text):
        if text[at] == '"':
            at = text.index('"', at + 1)
        elif text[at] == '(':
            depth += 1
        elif text[at] == ')':
            depth -= 1
            if depth == 0:
                return text[opening + 1:at]
        at += 1
    raise ValueError('unbalanced brackets in %s' % DESCRIPTIONS)


def check_descriptions(fields, kinds):
    """The logic layer's item types against the manual, which defines them.

    Only one direction is a fault. A field described here that the manual does not
    define is a field nobody agreed to, and a kind that disagrees is one of the two
    documents being wrong. A field the manual defines and nothing describes yet is work
    not done, so it is counted rather than complained about.
    """
    problems, described, total = [], 0, 0
    for storage, present in sorted(described_types().items()):
        heading = TYPE_NAMES.get(storage)
        if heading is None or heading not in fields:
            problems.append('%s is described and is no item type the manual names'
                            % storage)
            continue
        total += len(fields[heading])
        for field, kind in sorted(present.items()):
            if field not in fields[heading]:
                problems.append('%s.%s is described and the manual does not define it'
                                % (storage, field))
                continue
            described += 1
            wanted = KINDS.get(kinds.get((heading, field)))
            if wanted and wanted != kind:
                problems.append('%s.%s is %s here and %s in the manual'
                                % (storage, field, kind, kinds[(heading, field)]))
    return problems, described, total


class Checker:
    def __init__(self, fields, sets, ranges):
        self.fields = fields
        self.sets = sets
        self.ranges = ranges
        self.problems = []
        self.ids = set()
        # Where each id was seen. One id names one item across every type, since a
        # reference carries no type and has only the id to go on. A second sighting is
        # legitimate only as shadowing: same type, and laid over rather than beside —
        # a logbook item over a supplied one, or one library over another.
        self.seen_ids = {}
        self.keyed = set()
        self.references = []
        self.items = 0

    def item(self, rec, kind, where, ident=None):
        self.items += 1
        if kind == 'Region':
            self.extent(rec, where)
        self.walk(rec, kind, where, ident)

    def extent(self, rec, where):
        """A region's box. `east` may be numerically below `west`, which is what a box
        crossing the antimeridian looks like; `north` may not be below `south`, since
        latitude does not wrap. Neither pair may leave its range."""
        box = {k: rec[k] for k in ('west', 'east', 'south', 'north')
               if isinstance(rec.get(k), (int, float))}
        for edges, limit in ((('west', 'east'), 180), (('south', 'north'), 90)):
            for edge in edges:
                if edge in box and not -limit <= box[edge] <= limit:
                    self.problems.append('%s: %s is %s, outside +/-%d'
                                         % (where, edge, box[edge], limit))
        if 'south' in box and 'north' in box and box['south'] > box['north']:
            self.problems.append(
                '%s: south %s is above north %s, and latitude does not wrap'
                % (where, box['south'], box['north']))

    def walk(self, rec, kind, where, ident=None):
        for key, value in rec.items():
            if key == 'units':
                continue
            if key in OPAQUE:
                continue
            if key in KEYED and not isinstance(value, dict):
                self.problems.append(
                    '%s: %s should be an object keyed by entry, not %s'
                    % (where, key, type(value).__name__))
                continue
            if key in KEYED and isinstance(value, dict):
                for entry_key in value:
                    if ident is not None:
                        self.keyed.add('%s*%s' % (ident, entry_key))
                for entry_key, entry in value.items():
                    if not isinstance(entry, dict):
                        self.problems.append('%s: %s/%s is not an entry'
                                             % (where, key, entry_key))
                        continue
                    if 'key' in entry:
                        self.problems.append(
                            '%s: %s/%s carries a `key` field; the key is where it sits'
                            % (where, key, entry_key))
                    self.walk(entry, KEYED[key], where)
                continue
            # A name is not guaranteed to mean the same thing on every type: what is
            # an owned item on one may be a plain field on another. Descend only where
            # the value is actually nested.
            if key in OWNED and isinstance(value, dict):
                self.walk(value, OWNED[key], where, ident)
                continue
            if key not in self.fields.get(kind, set()):
                self.problems.append('%s: "%s" is not a field of %s' % (where, key, kind))
            self.value(kind, key, value, where)
            for item in (value if isinstance(value, list) else [value]):
                if isinstance(item, str) and item.startswith('@'):
                    self.references.append((where, item[1:]))

    def identify(self, ident, kind, where, library=False):
        if ident in self.seen_ids:
            first_kind, first_where, first_library = self.seen_ids[ident]
            if kind != first_kind:
                self.problems.append('%s: id %s is the %s in %s, and cannot also be a %s'
                                     % (where, ident, first_kind.lower(), first_where,
                                        kind.lower()))
            elif not library and not first_library:
                self.problems.append('%s: id %s is already used in %s'
                                     % (where, ident, first_where))
        # A shadowing item does not replace what was recorded first: the message should
        # name where the id was introduced, not the last place it was laid over.
        self.seen_ids.setdefault(ident, (kind, where, library))
        self.ids.add(ident)

    def value(self, kind, key, value, where):
        allowed = self.sets.get((kind, key))
        if allowed is not None:
            for item in (value if isinstance(value, list) else [value]):
                if isinstance(item, str) and item not in allowed:
                    self.problems.append('%s: %s is "%s", which is not one of %s'
                                         % (where, key, item, ', '.join(sorted(allowed))))
        span = self.ranges.get((kind, key))
        if span and isinstance(value, (int, float)) and not span[0] <= value <= span[1]:
            self.problems.append('%s: %s is %s, outside %d to %d'
                                 % (where, key, value, span[0], span[1]))

    def resolve(self):
        for where, target in self.references:
            # `@id*key` reaches an entry inside another item: the id must resolve, and
            # the key is checked where that item's collections are known.
            ident = target.split('*')[0]
            if ident not in self.ids:
                self.problems.append('%s: nothing named @%s' % (where, ident))
            elif '*' in target and target not in self.keyed:
                self.problems.append('%s: @%s has no entry %s'
                                     % (where, ident, target.split('*', 1)[1]))


def load(path):
    return json.load(io.open(path, encoding='utf-8'))


def check_logbook(checker, folder):
    """A logbook: yemoja.json, the files it points at, and the libraries it names."""
    meta = load(os.path.join(folder, 'yemoja.json'))

    for kind, names in meta.get('libraries', {}).items():
        for name in (names if isinstance(names, list) else [names]):
            checker.libraries_seen.add(name)
            path = os.path.join(ROOT, 'libraries', name + '.json')
            if not os.path.exists(path):
                checker.problems.append('yemoja.json: no library named %s' % name)
                continue
            for ident, rec in load(path).items():
                if ident == 'units':
                    continue
                checker.identify(ident, TYPE_NAMES[kind], name, library=True)
                checker.item(rec, TYPE_NAMES[kind], name, ident)

    # No paths: each kind lives under its own name, as either a folder of one file
    # per item or a single file holding them all. Whichever is present is used.
    for name in TYPE_NAMES:
        stem = name
        as_dir = os.path.join(folder, stem)
        as_file = as_dir + '.json'
        if os.path.isdir(as_dir) and os.path.exists(as_file):
            checker.problems.append('%s: both %s/ and %s.json exist'
                                    % (os.path.basename(folder), stem, stem))
            continue
        if os.path.isdir(as_dir):
            for fn in sorted(os.listdir(as_dir)):
                checker.identify(fn[:-len('.json')], TYPE_NAMES[name],
                                 os.path.basename(folder) + '/' + fn)
                checker.item(load(os.path.join(as_dir, fn)), TYPE_NAMES[name],
                             os.path.basename(folder) + '/' + fn, fn[:-len('.json')])
        elif os.path.exists(as_file):
            for ident, rec in load(as_file).items():
                if ident == 'units':
                    continue
                checker.identify(ident, TYPE_NAMES[name],
                                 os.path.basename(folder) + '/' + stem + '.json')
                checker.item(rec, TYPE_NAMES[name],
                             os.path.basename(folder) + '/' + stem + '.json', ident)


def unchecked_libraries(seen):
    """Library files no fixture declares. Their type is unknown, so their fields
    cannot be checked — which is worth saying rather than passing silently."""
    root = os.path.join(ROOT, 'libraries')
    rest = []
    for base, _, files in os.walk(root):
        for fn in sorted(files):
            if not fn.endswith('.json'):
                continue
            full = os.path.join(base, fn)
            name = os.path.relpath(full, root).replace(os.sep, '/')[:-len('.json')]
            if name in seen:
                continue
            load(full)  # at least it must parse
            rest.append(name)
    return rest


def main():
    sets, ranges = manual_vocabularies()
    checker = Checker(manual_fields(), sets, ranges)
    checker.libraries_seen = set()
    fixtures = os.path.join(ROOT, 'fixtures')
    for name in sorted(os.listdir(fixtures)):
        folder = os.path.join(fixtures, name)
        if os.path.isdir(folder) and os.path.exists(os.path.join(folder, 'yemoja.json')):
            check_logbook(checker, folder)
    checker.resolve()

    described, count, total = check_descriptions(checker.fields, manual_kinds())
    checker.problems.extend(described)

    print('checked %d items and %d references, against %d fixed sets and %d ranges'
          % (checker.items, len(checker.references), len(sets), len(ranges)))
    print('the logic layer describes %d of the %d fields those types define'
          % (count, total))
    for problem in checker.problems:
        print(' ', problem)
    rest = unchecked_libraries(checker.libraries_seen)
    if rest:
        print('%d libraries parse but are declared by no fixture, so their fields are '
              'unchecked:' % len(rest))
        for name in rest:
            print('  ', name)
    print('%d problems' % len(checker.problems))
    return 1 if checker.problems else 0


if __name__ == '__main__':
    sys.exit(main())
