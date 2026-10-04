"""Write the list of Rijkswaterstaat water-level stations the tide calculators choose from.

The list is logic/tides/rijkswaterstaat.txt, one station a line, and the tide calculators in the logic
layer read it to say which dive sites they cover without asking the network. It is taken from the
WaterWebservices, which are CC0, and written again whenever a station comes or goes:

    python tool/tidestations.py

A line is five tab-separated cells: the station's code as the service names it, its name, its
latitude and longitude in degrees, and the letters of the series it carries. Those are `a` for the
astronomical prediction, `f` for the forecast and `m` for the measurement, each a water height
against NAP in centimetres.

Only stations with an astronomical prediction are written: a gauge on a river or a lake measures a
height too, and a calculator reading it would call the river's rise a tide.

The catalogue names the candidates and is not believed about what they carry. It lists series a
station stopped delivering, so each is asked: for today's computed extremes, for today's forecast,
and for a week of measurements, a gauge being out for a day now and then. A letter is written only
where the service answered with readings, which takes a minute or two for the hundred there are.
"""

import datetime
import json
import os
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "logic", "tides", "rijkswaterstaat.txt")

SERVICE = "https://ddapi20-waterwebservices.rijkswaterstaat.nl"
CATALOGUE = SERVICE + "/METADATASERVICES/OphalenCatalogus"
OBSERVATIONS = SERVICE + "/ONLINEWAARNEMINGENSERVICES/OphalenWaarnemingen"

# The grouping the computed extremes of the astronomical series are filed under.
EXTREMES = "GETETBRKD2"


def posted(url, body):
    request = urllib.request.Request(url, json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=120) as answer:
        if answer.status != 200:
            return None
        return json.load(answer)


def candidates():
    """Every station the catalogue says has an astronomical water height against NAP."""
    read = posted(CATALOGUE, {"CatalogusFilter": {"Grootheden": True, "Hoedanigheden": True,
                                                  "Compartimenten": True, "ProcesTypes": True,
                                                  "Groeperingen": True}})
    if not read or not read.get("Succesvol"):
        raise SystemExit("the catalogue was refused: " + str(read and read.get("Foutmelding")))
    wanted = {
        metadata["AquoMetadata_MessageID"]
        for metadata in read["AquoMetadataLijst"]
        if metadata["Grootheid"]["Code"] == "WATHTE" and metadata["Compartiment"]["Code"] == "OW"
        and metadata["Hoedanigheid"]["Code"] == "NAP" and metadata.get("ProcesType") == "astronomisch"
    }
    carrying = {link["Locatie_MessageID"] for link in read["AquoMetadataLocatieLijst"]
                if link["AquoMetaData_MessageID"] in wanted}
    return [location for location in read["LocatieLijst"] if location["Locatie_MessageID"] in carrying]


def answers(code, process, grouping, start, end):
    """Whether the service has readings of a series at a station between two days."""
    body = {
        "AquoPlusWaarnemingMetadata": {"AquoMetadata": {
            "Compartiment": {"Code": "OW"}, "Grootheid": {"Code": "WATHTE"},
            "Hoedanigheid": {"Code": "NAP"}, "Groepering": {"Code": grouping}, "ProcesType": process}},
        "Locatie": {"Code": code},
        "Periode": {"Begindatumtijd": f"{start}T00:00:00.000+01:00", "Einddatumtijd": f"{end}T00:00:00.000+01:00"},
    }
    try:
        read = posted(OBSERVATIONS, body)
    except urllib.error.HTTPError:
        return False
    return bool(read and any(observed["MetingenLijst"] for observed in read.get("WaarnemingenLijst", [])))


def main():
    today = datetime.date.today()
    tomorrow = today + datetime.timedelta(days=1)
    week = today - datetime.timedelta(days=7)
    lines = []
    for location in sorted(candidates(), key=lambda found: found["Code"]):
        code = location["Code"]
        series = ""
        if answers(code, "astronomisch", EXTREMES, today, tomorrow):
            series += "a"
        if answers(code, "verwachting", "", today, tomorrow):
            series += "f"
        if answers(code, "meting", "", week, today):
            series += "m"
        print(f"{code}: {series or 'nothing'}")
        if "a" in series:
            lines.append((code, location["Naam"].strip(), location["Lat"], location["Lon"], series))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        for code, name, latitude, longitude, series in lines:
            out.write(f"{code}\t{name}\t{latitude}\t{longitude}\t{series}\n")
    print(f"{len(lines)} stations written to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
