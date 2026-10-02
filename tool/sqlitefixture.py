"""Write the SQLite database the reader's tests read, as Kotlin.

    python tool/sqlitefixture.py

The database is made by Python's own sqlite3, so it is laid out by SQLite itself rather than by
anything this project wrote. It is built to reach the awkward parts of the format: every kind of
value, a row too long for its page, a table deep enough for interior pages, a column added after
rows were written, names in quotes, and a table WITHOUT ROWID. A small page keeps it short.
"""

import base64
import os
import sqlite3
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'data', 'src', 'commonTest', 'kotlin', 'yemoja', 'data', 'sqlite', 'Fixture.kt')


def build(path):
    db = sqlite3.connect(path)
    db.execute('PRAGMA page_size = 512')
    db.execute('PRAGMA journal_mode = DELETE')
    db.execute('CREATE TABLE kinds (id INTEGER PRIMARY KEY, small INT, big INT, real REAL, text TEXT, blob BLOB, absent TEXT)')
    db.executemany('INSERT INTO kinds VALUES (?, ?, ?, ?, ?, ?, ?)', [
        (1, 7, 2 ** 40, 1.5, 'Plongée à 40 m', bytes([0, 255]), None),
        (2, -3, -(2 ** 62), -0.25, '', b'', None),
        (3, 0, 1, 1e100, 'x', bytes([1]), None),
        (4, 300, -70000, 0.0, 'y', bytes([2]), None),
    ])
    db.execute('CREATE TABLE lines (n INTEGER, label TEXT)')
    db.executemany('INSERT INTO lines VALUES (?, ?)', [(n, 'line %d' % n) for n in range(1, 301)])
    db.execute('INSERT INTO lines VALUES (?, ?)', (301, 'long ' + 'abcdefghij' * 300))
    db.execute('CREATE INDEX lines_by_label ON lines (label)')
    db.execute('CREATE TABLE altered (a TEXT)')
    db.execute("INSERT INTO altered VALUES ('before')")
    db.execute('ALTER TABLE altered ADD COLUMN b INTEGER DEFAULT 5')
    db.execute("INSERT INTO altered VALUES ('after', 9)")
    db.execute('CREATE TABLE "odd table" ("first col" TEXT, [second] INTEGER, `third` REAL, PRIMARY KEY ("first col"))')
    db.execute('INSERT INTO "odd table" VALUES (?, ?, ?)', ('one, two', 2, 3.25))
    db.execute('CREATE TABLE keyed (k TEXT PRIMARY KEY, v TEXT) WITHOUT ROWID')
    db.execute("INSERT INTO keyed VALUES ('a', 'b')")
    db.commit()
    db.close()


def main():
    folder = tempfile.mkdtemp()
    path = os.path.join(folder, 'fixture.db')
    build(path)
    with open(path, 'rb') as held:
        encoded = base64.b64encode(held.read()).decode('ascii')
    os.remove(path)
    os.rmdir(folder)
    lines = [encoded[at:at + 96] for at in range(0, len(encoded), 96)]
    body = '\n'.join('    "%s" +' % line for line in lines)[:-2]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as out:
        out.write('package yemoja.data.sqlite\n\n')
        out.write('// Written by tool/sqlitefixture.py, which says what the database holds. Not edited by hand.\n\n')
        out.write('internal const val FIXTURE: String =\n')
        out.write(body + '\n')
    print('wrote', OUT, len(encoded), 'characters')


if __name__ == '__main__':
    main()
