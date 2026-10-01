"""Check the committed markdown docs: relative links resolve, JSON examples parse,
and every glossary term appears in the document said to own it."""
import io
import json
import os
import re
import subprocess
import sys

SKIP_DIRS = {'.git', 'native', '.dart_tool', 'build', '.github'}

# A leading `// path/to/file.json` names the file being shown. It is not part of the
# example, so it is dropped before parsing.
FILENAME_COMMENT = re.compile(r'^\s*//[^\n]*\n')
JSON_BLOCK = re.compile(r'```json\n(.*?)```', re.S)
# Manuals whose examples are some other file than a logbook's, so their names are not fields. They
# are still parsed.
NOT_LOGBOOK = {'manual/planning-from-a-file.md'}
LINK = re.compile(r'\[[^\]]+\]\(([^)]+)\)')


def excluded(root):
    """Paths git is told to ignore. Anything local-only is not ours to check."""
    try:
        out = subprocess.run(
            ['git', 'ls-files', '--others', '--ignored', '--exclude-standard'],
            cwd=root, capture_output=True, text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError):
        return set()
    return {line.strip() for line in out.splitlines() if line.strip()}


def doc_files(root):
    """Every markdown file belonging to the repository, in a stable order."""
    skip = excluded(root)
    found = []
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in sorted(files):
            if not name.endswith('.md'):
                continue
            rel = os.path.relpath(os.path.join(base, name), root).replace(os.sep, '/')
            if rel not in skip:
                found.append(rel)
    return sorted(found)


def check_links(root, files):
    broken = checked = 0
    for rel in files:
        path = os.path.join(root, rel)
        text = io.open(path, encoding='utf-8').read()
        base = os.path.dirname(path)
        for target in LINK.findall(text):
            if target.startswith(('http://', 'https://', '#')):
                continue
            checked += 1
            resolved = os.path.normpath(os.path.join(base, target.split('#')[0]))
            if not os.path.exists(resolved):
                print('BROKEN LINK', rel, '->', target)
                broken += 1
    print('checked %d links across %d files, %d broken' % (checked, len(files), broken))
    return broken


def known_fields():
    """Every field name the manual defines, flattened. Borrowed from the other tool so
    the manual stays the one place a field is written down."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import checkdata
    return set().union(*checkdata.manual_fields().values())


def stale_names(value, fields, found):
    """Names in an example that no field of any type answers to.

    An example is a fragment, so the type it belongs to is not written anywhere. What
    can be said without knowing it: an object whose neighbours are recognised fields is
    an item, and a name in it that no type defines is stale. An object keyed by id --
    a collection -- has no recognised names and is passed over, which is the point of
    requiring two.
    """
    if isinstance(value, list):
        for item in value:
            stale_names(item, fields, found)
    elif isinstance(value, dict):
        known = [k for k in value if k in fields or k == 'units']
        if len(known) >= 2:
            found |= {k for k in value if k not in fields and k != 'units'}
        for item in value.values():
            stale_names(item, fields, found)
    return found


def check_json(root, files):
    """A manual whose examples cannot be copied out is worse than one saying nothing.

    Parsing is the smaller half. An example that parses and names a field nobody has
    defined for two versions is the one a reader copies and believes."""
    fields, blocks, bad = known_fields(), 0, 0
    for rel in files:
        path = os.path.join(root, rel)
        text = io.open(path, encoding='utf-8').read()
        for block in JSON_BLOCK.findall(text):
            blocks += 1
            try:
                parsed = json.loads(FILENAME_COMMENT.sub('', block))
            except ValueError as exc:
                print('INVALID JSON', rel, '-', exc)
                bad += 1
                continue
            if rel.replace(os.sep, '/') in NOT_LOGBOOK:
                continue
            stale = stale_names(parsed, fields, set())
            if stale:
                print('STALE FIELD', rel, '-', ', '.join(sorted(stale)))
                bad += 1
    print('checked %d JSON examples, %d invalid' % (blocks, bad))
    return bad


def check_glossary(root):
    """An index is only useful if it points at something. A term whose owning document
    never mentions it has been renamed on one side and not the other."""
    path = os.path.join(root, 'glossary.md')
    if not os.path.exists(path):
        return 0
    checked = missing = 0
    owner = None
    for line in io.open(path, encoding='utf-8').read().split('\n'):
        if line.startswith('## '):
            owner = None
        found = re.search(r'^Owned by \[([^\]]+)\]', line)
        if found:
            owner = found.group(1)
        row = re.match(r'^\| \*\*`?([^`*|]+)`?\*\*(.*)$', line)
        if not row or 'defined here' in line:
            continue
        term, rest = row.group(1).strip(), row.group(2)
        here = re.findall(r'\]\(([^)]+\.md)\)', rest)
        source = here[-1] if here else owner
        if not source:
            continue
        checked += 1
        text = io.open(os.path.join(root, source), encoding='utf-8').read().lower()
        if term.lower().strip('`<>') not in text.replace('`', ''):
            print('GLOSSARY', term, '-> not found in', source)
            missing += 1
    print('checked %d glossary terms, %d not found in their source' % (checked, missing))
    return missing


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    files = doc_files(root)
    failures = check_links(root, files) + check_json(root, files) + check_glossary(root)
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
