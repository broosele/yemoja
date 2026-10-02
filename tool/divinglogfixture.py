"""Write the Diving Log databases the importer's tests read, as Kotlin.

    python tool/divinglogfixture.py

Everything in them is invented. The tables and columns are Diving Log 4.2's, as logic/divinglog.md
describes them, with only the columns the importer reads or is meant to leave; the rows are made
up to reach each rule it follows:

- dive 1, a night dive in a quarry whose own water is not given, so it takes the fresh its dives
  were logged in; a buddy, a trip, an operator; a profile of depth, temperature, the
  no-decompression limit and the CNS clock; visibility, entry and weather for the remarks.
- dive 2, a decompression dive on EAN30 with EAN50 in the first of three Tank slots, the other
  two being copies; a stop at 6 m while the limit sits at one minute, which stays in its column
  after it clears; a switch to the deco gas; a CNS clock that reaches its ceiling; and samples
  after surfacing, which are cut.
- dive 3, with no profile, so the logged time is its duration; a user-defined field, a fish
  seen, and a picture, which is counted and left.
- the user, with a medical, a blood group and one certification.

A second database has no Logbook table, and is not Diving Log's.
"""

import base64
import os
import sqlite3
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'logic', 'src', 'commonTest', 'kotlin', 'yemoja', 'logic', 'divinglog', 'Fixture.kt')

SCHEMA = '''
CREATE TABLE Logbook (ID INTEGER PRIMARY KEY, Number INTEGER, Divedate TEXT, Entrytime TEXT,
  Country TEXT, CountryID INTEGER, Place TEXT, PlaceID INTEGER, Divetime REAL, Depth REAL,
  Buddy TEXT, BuddyIDs TEXT, Comments TEXT, Water INTEGER, Entry INTEGER, Divetype TEXT,
  Tanktype INTEGER, Tanksize REAL, PresS REAL, PresE REAL, Gas TEXT, Weather TEXT, UWCurrent TEXT,
  Surface TEXT, Visibility INTEGER, Airtemp REAL, Watertemp REAL, Weight REAL, Deco INTEGER,
  Rep INTEGER, Altitude TEXT, Divesuit TEXT, Computer TEXT, ProfileInt INTEGER, Profile TEXT,
  Profile2 TEXT, Profile3 TEXT, DepthAvg REAL, UUID TEXT, Profile4 TEXT, Profile5 TEXT, CNS TEXT,
  PGStart TEXT, PGEnd TEXT, Divemaster TEXT, Boat TEXT, Rating INTEGER, O2 REAL, He REAL,
  DblTank INTEGER, ShopID INTEGER, TripID INTEGER, UtcOffset INTEGER);
CREATE TABLE Tank (ID INTEGER PRIMARY KEY, LogID INTEGER, TankID INTEGER, SortOrd INTEGER,
  Tanktype INTEGER, Tanksize REAL, PresS REAL, PresE REAL, O2 REAL, He REAL, DblTank INTEGER);
CREATE TABLE Place (ID INTEGER PRIMARY KEY, CountryID INTEGER, Place TEXT, Rating INTEGER,
  MaxDepth REAL, Lat TEXT, Lon TEXT, Comments TEXT, Water INTEGER, Altitude TEXT, WaterName TEXT,
  Difficulty TEXT);
CREATE TABLE Country (ID INTEGER PRIMARY KEY, Country TEXT, Gmt INTEGER);
CREATE TABLE Buddy (ID INTEGER PRIMARY KEY, FirstName TEXT, LastName TEXT, Street TEXT,
  Address2 TEXT, Zip TEXT, City TEXT, State TEXT, Country TEXT, Phone TEXT, Mobile TEXT, Email TEXT,
  Birthdate TEXT, Comments TEXT, URL TEXT);
CREATE TABLE Shop (ID INTEGER PRIMARY KEY, ShopName TEXT, ShopType TEXT, Street TEXT, Zip TEXT,
  City TEXT, Country TEXT, Phone TEXT, Email TEXT, URL TEXT, Comments TEXT, Rating INTEGER);
CREATE TABLE Trip (ID INTEGER PRIMARY KEY, ShopID INTEGER, TripName TEXT, StartDate TEXT,
  EndDate TEXT, Comments TEXT, Rating INTEGER);
CREATE TABLE Divetype (ID INTEGER PRIMARY KEY, Typename TEXT);
CREATE TABLE Personal (ID INTEGER PRIMARY KEY, FirstName TEXT, LastName TEXT, Email TEXT,
  Birthdate TEXT, LastMediCheck TEXT, Bloodgroup TEXT, EmergContact TEXT, Comments TEXT);
CREATE TABLE Brevets (ID INTEGER PRIMARY KEY, Brevet TEXT, Org TEXT, CertDate TEXT, Number TEXT,
  Instructor TEXT, InstructorNo TEXT, SortOrd INTEGER);
CREATE TABLE Equipment (ID INTEGER PRIMARY KEY, Object TEXT, Manufacturer TEXT, Serial TEXT,
  DateP TEXT, Comments TEXT);
CREATE TABLE Userdefined (ID INTEGER PRIMARY KEY, LogID INTEGER, Solo TEXT, Field2 TEXT);
CREATE TABLE Fish (ID INTEGER PRIMARY KEY, CommonName TEXT, ScientificName TEXT);
CREATE TABLE FishRel (ID INTEGER PRIMARY KEY, LogID INTEGER, FishID INTEGER);
CREATE TABLE Pictures (ID INTEGER PRIMARY KEY, LogID INTEGER, Path TEXT);
CREATE TABLE DBInfo (PrgName TEXT, DBVersion TEXT);
'''


def depth(cm, flag='0'):
    return '%05d%s000000' % (cm, flag)


def second(tenths_c, tenths_bar, cylinder):
    return '%03d%04d%d000' % (tenths_c, tenths_bar, cylinder)


def fourth(limit, stop_minutes, stop_metres):
    return '%03d%03d%03d' % (limit, stop_minutes, stop_metres)


def fifth(hundredths):
    return '0' * 13 + '%03d' % hundredths + '000'


def build(path):
    db = sqlite3.connect(path)
    # A small page keeps the file short, every table taking at least one.
    db.execute('PRAGMA page_size = 512')
    db.executescript(SCHEMA)
    db.execute("INSERT INTO DBInfo VALUES ('Diving Log', '4.2.0')")
    db.execute("INSERT INTO Country VALUES (1, 'Atlantis', 3600)")
    db.executemany('INSERT INTO Place VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', [
        (1, 1, 'Nowhere Quarry', 4, 22.0, '12°30\'00.00"N', '3°15\'00.00"W', 'A quarry that is not.', 0,
         '0 m - 300 m', 'Lake Nowhere', 'easy'),
        (2, 1, 'Fictional Reef', 0, None, None, None, None, 1, None, None, None),
    ])
    db.execute("INSERT INTO Buddy VALUES (1, 'Ada', 'Example', 'Test Street 1', NULL, '1000', 'Nowhere', NULL, "
               "'Atlantis', '+00 000', '+00 111', 'ada@example.invalid', '1990-01-02', 'Brings cake.', NULL)")
    db.execute("INSERT INTO Shop VALUES (1, 'Pretend Divers', 'dive centre', NULL, NULL, 'Nowhere', NULL, "
               "'+00 222', 'shop@example.invalid', 'https://example.invalid', NULL, 3)")
    db.execute("INSERT INTO Trip VALUES (1, 1, 'Invented Week', '2030-05-01', '2030-05-07', 'Calm seas.', 0)")
    db.executemany('INSERT INTO Divetype VALUES (?, ?)', [(1, 'Night'), (2, 'Wreck'), (3, 'Mountain Lake')])
    db.execute("INSERT INTO Personal VALUES (1, 'Una', 'User', 'una@example.invalid', '1985-03-04', "
               "'2029-12-01', '0+', 'Someone at home', NULL)")
    db.execute("INSERT INTO Brevets VALUES (1, 'Open Water Diver', 'Some Agency', '2020-06-01', 'OW-1', "
               "'Ivan Instructor', '42', 1)")
    db.execute("INSERT INTO Equipment VALUES (1, 'Computer', 'Brandless', 'SN-0', '2028-01-01', NULL)")
    db.execute("INSERT INTO Fish VALUES (1, 'Pretend wrasse', 'Fictus wrasse')")

    # Dive 1: thirty-second samples, to 12 m and back. The limit sits at 99 until 90 s, then falls.
    profile = ''.join(depth(cm) for cm in (0, 600, 1200, 1200, 600, 0))
    profile2 = ''.join(second(t, 0, 0) for t in (180, 175, 170, 170, 172, 175))
    profile4 = ''.join(fourth(limit, 0, 0) for limit in (99, 99, 99, 80, 99, 99))
    profile5 = ''.join(fifth(h) for h in (0, 0, 100, 100, 200, 200))
    db.execute('INSERT INTO Logbook (ID, Number, Divedate, Entrytime, PlaceID, Divetime, Depth, BuddyIDs, '
               'Comments, Water, Entry, Divetype, Tanksize, PresS, PresE, Weather, Visibility, Airtemp, '
               'Watertemp, Weight, Deco, Computer, ProfileInt, Profile, Profile2, Profile4, Profile5, DepthAvg, '
               'Rating, O2, He, ShopID, TripID) VALUES '
               '(11, 1, \'2030-05-02\', \'21:05\', 1, 3, 12.0, \'1\', ?, 2, 0, '
               '\'1,3\', 12, 200, 120, \'clear\', 1, 15, 17.0, 6, 0, \'Pretend Computer\', 30, ?, ?, ?, ?, 7.5, '
               '4, 21, 0, 1, 1)', ('First line.\r\nSecond line.', profile, profile2, profile4, profile5))

    # Dive 2: a minute apart. Down to 30 m, a stop at 6 m held at one minute, a switch to the deco gas,
    # and three samples after surfacing that are not the dive.
    depths = (0, 3000, 3000, 2100, 600, 600, 600, 0, 0, 0, 0)
    cylinder = (0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1)
    bar = (2100, 1900, 1700, 2000, 1950, 1900, 1850, 1800, 1800, 1800, 1800)
    limits = (99, 20, 1, 1, 1, 1, 99, 99, 99, 99, 99)
    stops = (0, 0, 6, 6, 6, 6, 6, 6, 6, 6, 6)
    cns = (100, 300, 600, 900, 999, 999, 999, 999, 999, 999, 999)
    profile = ''.join(depth(cm) for cm in depths)
    profile2 = ''.join(second(150, b, c) for b, c in zip(bar, cylinder))
    profile4 = ''.join(fourth(limit, 1 if stop else 0, stop) for limit, stop in zip(limits, stops))
    profile5 = ''.join(fifth(h) for h in cns)
    db.execute('INSERT INTO Logbook (ID, Number, Divedate, Entrytime, PlaceID, Divetime, Depth, Water, Entry, '
               'Tanksize, PresS, PresE, Visibility, Watertemp, Deco, Computer, ProfileInt, Profile, Profile2, '
               'Profile4, Profile5, O2, He, Boat, Divemaster) VALUES '
               '(12, 2, \'2030-05-03\', \'09:30\', 2, 7, 30.0, 1, 2, 15, 210, 180, 3, 15, 1, '
               '\'Pretend Computer\', 60, ?, ?, ?, ?, 30, 0, \'Blue Pretender\', \'Dee Master\')',
               (profile, profile2, profile4, profile5))
    db.executemany('INSERT INTO Tank (LogID, TankID, SortOrd, O2, He, Tanksize) VALUES (?, 1, ?, 50, 0, ?)',
                   [(12, slot, 7) for slot in (1, 2, 3)])

    # Dive 3: no samples at all, so its logged time is its duration.
    db.execute('INSERT INTO Logbook (ID, Number, Divedate, Entrytime, PlaceID, Divetime, Depth, Water, '
               'Visibility, Tanksize, O2) VALUES (13, 3, \'2030-05-03\', \'14:00\', 2, 42, 18.5, 1, 0, 12, 32)')
    db.execute("INSERT INTO Userdefined (LogID, Solo, Field2) VALUES (13, 'yes', NULL)")
    db.execute('INSERT INTO FishRel (LogID, FishID) VALUES (13, 1)')
    db.execute("INSERT INTO Pictures (LogID, Path) VALUES (13, 'C:\\\\nowhere\\\\picture.jpg')")
    db.commit()
    db.close()


def not_diving_log(path):
    db = sqlite3.connect(path)
    db.execute('CREATE TABLE recipes (name TEXT)')
    db.execute("INSERT INTO recipes VALUES ('bread')")
    db.commit()
    db.close()


def encoded(make):
    folder = tempfile.mkdtemp()
    path = os.path.join(folder, 'fixture.db')
    make(path)
    with open(path, 'rb') as held:
        text = base64.b64encode(held.read()).decode('ascii')
    os.remove(path)
    os.rmdir(folder)
    return text


def constant(name, text):
    lines = [text[at:at + 96] for at in range(0, len(text), 96)]
    return 'internal const val %s: String =\n%s\n' % (name, '\n'.join('    "%s" +' % line for line in lines)[:-2])


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='\n') as out:
        out.write('package yemoja.logic.divinglog\n\n')
        out.write('// Written by tool/divinglogfixture.py, which says what the databases hold. Not edited by hand.\n\n')
        out.write(constant('DATABASE', encoded(build)))
        out.write('\n')
        out.write(constant('NOT_DIVING_LOG', encoded(not_diving_log)))
    print('wrote', OUT)


if __name__ == '__main__':
    main()
