"""Write the list of places Rijkswaterstaat's Scaldis-Oost model gives the current at.

The list is logic/tides/scaldis-oost.txt, one place a line, and the current calculator in the
logic layer reads it to say which dive sites it covers without asking the network. It is taken from
the data service behind Rijkswaterstaat's RWsOS viewer, and written again whenever a place comes or
goes:

    python tool/currentpoints.py

A line is four tab-separated cells: the place's code as the service names it, its name, and its
latitude and longitude in degrees. A place is written only where the service holds the model's
current speed there.
"""

import json
import os
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "logic", "tides", "scaldis-oost.txt")

# Every place holding the model's current speed, which is series SG.1 of source SOF_6.
PLACES = "https://rwsos.rws.nl/wb-api/dd/2.0/timeseries?observationTypeId=SG.1&sourceName=SOF_6"


def main():
    with urllib.request.urlopen(PLACES, timeout=120) as answer:
        read = json.load(answer)
    lines = []
    for result in read["results"]:
        feature = result["location"]
        longitude, latitude = feature["geometry"]["coordinates"][:2]
        name = " ".join(feature["properties"]["locationName"].split())
        lines.append((feature["properties"]["locationId"], name, latitude, longitude))
    lines.sort()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        for code, name, latitude, longitude in lines:
            out.write(f"{code}\t{name}\t{latitude}\t{longitude}\n")
    print(f"{len(lines)} places written to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
