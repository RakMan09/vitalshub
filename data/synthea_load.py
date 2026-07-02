#!/usr/bin/env python3
"""
Load synthetic health data into a running VitalsHub instance and exercise the
full pipeline end to end.

This is a lightweight stand-in for a full Synthea run: rather than downloading
and running the Synthea generator, it creates a synthetic patient and ingests
the bundled sample source files (wearable JSON, smart-scale CSV, HL7 v2, and
Apple-Health XML). No real PHI is ever used.

To use real Synthea instead, generate FHIR R4 patient bundles with Synthea and
POST each contained Patient/Observation to the /fhir endpoints; the rest of the
pipeline (terminology, validation, de-id, audit) is identical.

Usage:
    python data/synthea_load.py [BASE_URL]     # default http://localhost:8080
"""
import json
import os
import sys
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
SAMPLES = os.path.join(os.path.dirname(__file__), "samples")


def request(method, path, data=None, headers=None, raw=False):
    url = BASE + path
    body = data if isinstance(data, (bytes, type(None))) else data.encode("utf-8")
    req = urllib.request.Request(url, data=body, method=method)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    with urllib.request.urlopen(req) as resp:
        text = resp.read().decode("utf-8")
    return text if raw else (json.loads(text) if text else {})


def create_patient():
    patient = {
        "resourceType": "Patient",
        "name": [{"family": "Synthetic", "given": ["Sam"]}],
        "gender": "male",
        "birthDate": "1985-03-12",
    }
    result = request("POST", "/fhir/Patient",
                     data=json.dumps(patient),
                     headers={"Content-Type": "application/fhir+json", "X-Actor": "loader"})
    pid = result["id"]
    print(f"Created Patient/{pid}")
    return pid


def ingest(source_type, filename, patient_id):
    with open(os.path.join(SAMPLES, filename), "rb") as fh:
        content = fh.read()
    result = request("POST", f"/api/ingest/{source_type}?patientId={patient_id}",
                     data=content,
                     headers={"Content-Type": "application/octet-stream", "X-Actor": "loader"})
    print(f"  {source_type:18s} stored={len(result.get('storedIds', []))} "
          f"quarantined={len(result.get('quarantined', []))} "
          f"unmapped={result.get('unmappedCodes', [])}")


def main():
    print(f"VitalsHub loader -> {BASE}")
    pid = create_patient()

    print("Ingesting heterogeneous sources:")
    ingest("wearable-json", "wearable.json", pid)
    ingest("scale-csv", "scale.csv", pid)
    ingest("apple-health-xml", "apple-health.xml", pid)
    ingest("hl7v2", "clinic-oru.hl7", pid)

    print("\nUnified weight timeline (LOINC 29463-7):")
    bundle = request("GET",
                     f"/fhir/Observation?patient=Patient/{pid}&code=29463-7&_sort=date",
                     headers={"X-Actor": "loader"})
    for entry in bundle.get("entry", []):
        obs = entry["resource"]
        value = obs.get("valueQuantity", {})
        eff = obs.get("effectiveDateTime", "?")
        print(f"  {eff[:10]}  {value.get('value')} {value.get('unit')}")

    print("\nGranting consent to 'coach' for body-composition (de-identified) and sharing:")
    request("POST", "/api/consent",
            data=json.dumps({"patientId": pid, "recipient": "coach",
                             "categories": ["body-composition"], "requireDeidentified": True}),
            headers={"Content-Type": "application/json"})
    shared = request("GET", f"/api/patients/{pid}/share?recipient=coach", raw=True)
    shared_bundle = json.loads(shared)
    print(f"  shared bundle entries: {len(shared_bundle.get('entry', []))} (de-identified)")

    print("\nData-quality metrics:")
    print("  " + json.dumps(request("GET", "/api/metrics/data-quality"), indent=2).replace("\n", "\n  "))

    print("\nWho accessed this patient's data (audit trail):")
    for row in request("GET", f"/api/patients/{pid}/access-log"):
        print(f"  {row.get('recorded')}  {row.get('who'):12s} {row.get('action')}  "
              f"{row.get('resource')}  purpose={row.get('purpose')}")


if __name__ == "__main__":
    main()
