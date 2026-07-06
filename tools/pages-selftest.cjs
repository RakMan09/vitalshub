/*
 * Node self-test for the browser pipeline (docs/pipeline.js). Verifies the
 * ported logic reproduces the Java backend's behaviour against the same sample
 * data. Run: node tools/pages-selftest.cjs
 */
const fs = require("fs");
const path = require("path");
const assert = require("assert");
const P = require("../docs/pipeline.js");

const dataDir = path.join(__dirname, "..", "docs", "data");
const read = (f) => fs.readFileSync(path.join(dataDir, f), "utf8");

let failures = 0;
function check(name, fn) {
  try {
    fn();
    console.log("  PASS " + name);
  } catch (e) {
    failures++;
    console.log("  FAIL " + name + " -> " + e.message);
  }
}

const mappings = P.parseMappingsCsv(read("mappings.csv"));
const store = new P.Store();

// Seed a patient carrying PHI.
store.withActor("you", "TREATMENT").create({
  resourceType: "Patient",
  id: "demo",
  name: [{ family: "Secret", given: ["Sam"] }],
  telecom: [{ system: "phone", value: "555-1234" }],
  address: [{ city: "Springfield", state: "IL", postalCode: "62704" }],
  identifier: [{ system: "urn:mrn", value: "MRN-999" }],
  birthDate: "1985-03-12",
  meta: { tag: [], security: [] },
});

const rw = P.ingest(store, mappings, "wearable-json", read("wearable.json"), "demo", "importer");
const rs = P.ingest(store, mappings, "scale-csv", read("scale.csv"), "demo", "importer");
const ra = P.ingest(store, mappings, "apple-health-xml", read("apple-health.xml"), "demo", "importer");
const rh = P.ingest(store, mappings, "hl7v2", read("clinic-oru.hl7"), "demo", "importer");

check("wearable stores 4 observations", () => assert.strictEqual(rw.stored.length, 4));
check("scale stores 9 observations", () => assert.strictEqual(rs.stored.length, 9));
check("apple stores 3 observations", () => assert.strictEqual(ra.stored.length, 3));
check("hl7 stores 3 and quarantines 1 (invalid unit)", () => {
  assert.strictEqual(rh.stored.length, 3);
  assert.strictEqual(rh.quarantined.length, 1);
  assert.match(rh.quarantined[0].reason, /unit/);
});
check("hl7 flags unmapped XYZ code", () => assert.ok(rh.unmapped.some((u) => u.includes("XYZ"))));

check("unified weight timeline is date-sorted across sources", () => {
  const weights = store.searchObservations("demo", "29463-7");
  const dates = weights.map((o) => o.effectiveDateTime.slice(0, 10));
  assert.deepStrictEqual(dates, ["2024-05-01", "2024-05-02", "2024-05-08", "2024-05-15"]);
});

check("local code GLU was mapped to LOINC 2339-0", () => {
  const glucose = store.searchObservations("demo", "2339-0");
  assert.strictEqual(glucose.length, 1);
});

check("metrics: 20 processed, 19 stored, 1 quarantined", () => {
  const m = P.metricsSnapshot(store);
  assert.strictEqual(m.recordsProcessed, 20);
  assert.strictEqual(m.recordsStored, 19);
  assert.strictEqual(m.recordsQuarantined, 1);
});

check("share without consent is denied", () => {
  assert.throws(() => P.share(store, "demo", null), /consent/i);
});

check("share honours category + de-identifies", () => {
  const consent = { recipient: "coach", categories: ["body-composition"], requireDeidentified: true };
  const bundle = P.share(store, "demo", consent);
  const observations = bundle.entry.map((e) => e.resource).filter((r) => r.resourceType === "Observation");
  const patient = bundle.entry.map((e) => e.resource).find((r) => r.resourceType === "Patient");
  assert.ok(observations.length > 0, "has observations");
  observations.forEach((o) => {
    const bodyComp = o.code.coding.some((c) => ["29463-7", "39156-5", "41982-0"].includes(c.code));
    assert.ok(bodyComp, "only body-composition shared");
    assert.ok(!o.subject.reference.includes("demo"), "subject pseudonymized");
    assert.strictEqual(o.effectiveDateTime.length, 4, "date generalized to year");
  });
  assert.ok(!patient.name, "patient name removed");
});

check("audit trail records access per patient (who accessed my data)", () => {
  const log = store.auditForPatient("demo");
  assert.ok(log.length >= 5, "audit has entries");
  assert.ok(log.some((a) => a.who === "coach" && a.purpose === "SHARE"), "recipient access logged");
});

console.log(failures === 0 ? "\nALL PAGES PIPELINE CHECKS PASSED" : `\n${failures} CHECK(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);
