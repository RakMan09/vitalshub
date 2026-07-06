/*
 * VitalsHub pipeline - a faithful in-browser port of the Java backend's core
 * logic (adapters -> normalize -> terminology map -> validate -> store, plus
 * de-identification, consent, and audit). Kept free of DOM/browser APIs so it
 * can also be unit-tested under Node (see tools/pages-selftest.mjs).
 */
(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
  } else {
    root.Pipeline = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const SOURCE = {
    WEARABLE: "https://vitalshub.example/fhir/CodeSystem/wearable",
    SCALE: "https://vitalshub.example/fhir/CodeSystem/scale",
    APPLE: "https://vitalshub.example/fhir/CodeSystem/apple-health",
    HL7: "https://vitalshub.example/fhir/CodeSystem/hl7v2-local",
  };
  const LOINC = "http://loinc.org";
  const SNOMED = "http://snomed.info/sct";
  const STANDARD_SYSTEMS = new Set([LOINC, SNOMED]);
  const CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";

  // A small UCUM allow-list, mirroring what the real FHIR validator accepts for
  // our data (so the invalid "units" from the HL7 sample gets quarantined too).
  const VALID_UCUM = new Set(["kg", "%", "kg/m2", "/min", "{steps}", "{count}", "mg/dL", "1"]);

  const LOINC_TO_CATEGORY = {
    "29463-7": "body-composition",
    "39156-5": "body-composition",
    "41982-0": "body-composition",
    "8867-4": "vitals",
    "40443-4": "vitals",
    "41950-7": "activity",
    "2339-0": "labs",
  };

  // --- small deterministic id (stand-in for the backend's hashed UUID) ---
  function stableId(parts) {
    const raw = parts.join("|");
    let h = 0;
    for (let i = 0; i < raw.length; i++) {
      h = (Math.imul(31, h) + raw.charCodeAt(i)) | 0;
    }
    return "obs-" + (h >>> 0).toString(16) + "-" + (raw.length.toString(16));
  }

  function makeObservation(ctx, sourceSystem, sourceCode, display, value, unit, ucum, effectiveIso) {
    return {
      resourceType: "Observation",
      id: stableId([ctx.patientId, sourceCode, effectiveIso]),
      status: "final",
      category: [{ coding: [{ system: CATEGORY_SYSTEM, code: "vital-signs", display: "Vital Signs" }] }],
      code: { coding: [{ system: sourceSystem, code: sourceCode, display: display }], text: display },
      subject: { reference: "Patient/" + ctx.patientId },
      effectiveDateTime: effectiveIso,
      valueQuantity: { value: value, unit: unit, system: "http://unitsofmeasure.org", code: ucum },
      meta: { tag: [], security: [] },
      _source: ctx.sourceType,
    };
  }

  // ---------------- Adapters ----------------

  function parseWearable(text, ctx) {
    const root = JSON.parse(text);
    const out = [];
    (root["activities-heart"] || []).forEach((day) => {
      const resting = day.value && day.value.restingHeartRate;
      if (typeof resting === "number") {
        out.push(makeObservation(ctx, SOURCE.WEARABLE, "resting_heart_rate", "Resting heart rate",
          resting, "beats/minute", "/min", day.dateTime + "T00:00:00Z"));
      }
    });
    (root["activities-steps"] || []).forEach((day) => {
      if (day.value !== undefined) {
        out.push(makeObservation(ctx, SOURCE.WEARABLE, "steps", "Step count",
          Number(day.value), "steps", "{steps}", day.dateTime + "T00:00:00Z"));
      }
    });
    (root["heartRateSamples"] || []).forEach((s) => {
      if (typeof s.bpm === "number") {
        out.push(makeObservation(ctx, SOURCE.WEARABLE, "heart_rate", "Heart rate",
          s.bpm, "beats/minute", "/min", s.time));
      }
    });
    return out;
  }

  function parseScale(text, ctx) {
    const lines = text.trim().split(/\r?\n/);
    const header = lines[0].split(",").map((s) => s.trim());
    const idx = (name) => header.indexOf(name);
    const cols = [
      { col: "weight_kg", code: "weight_kg", display: "Body weight", unit: "kg", ucum: "kg" },
      { col: "body_fat_pct", code: "body_fat_pct", display: "Body fat percentage", unit: "%", ucum: "%" },
      { col: "bmi", code: "bmi", display: "Body mass index", unit: "kg/m2", ucum: "kg/m2" },
    ];
    const out = [];
    for (let i = 1; i < lines.length; i++) {
      if (!lines[i].trim()) continue;
      const cells = lines[i].split(",").map((s) => s.trim());
      const eff = cells[idx("date")] + "T00:00:00Z";
      cols.forEach((c) => {
        const raw = cells[idx(c.col)];
        if (raw !== undefined && raw !== "") {
          out.push(makeObservation(ctx, SOURCE.SCALE, c.code, c.display, Number(raw), c.unit, c.ucum, eff));
        }
      });
    }
    return out;
  }

  function parseAppleHealth(text, ctx) {
    const out = [];
    const recordRe = /<Record\b([^>]*)\/?>/g;
    const attrRe = /(\w+)="([^"]*)"/g;
    let m;
    while ((m = recordRe.exec(text)) !== null) {
      const attrs = {};
      let a;
      while ((a = attrRe.exec(m[1])) !== null) attrs[a[1]] = a[2];
      if (!attrs.type || !attrs.value) continue;
      const display = attrs.type.replace("HKQuantityTypeIdentifier", "");
      const unit = attrs.unit || "";
      const ucum = unit === "count/min" ? "/min" : unit === "count" ? "{count}" : unit;
      out.push(makeObservation(ctx, SOURCE.APPLE, attrs.type, display,
        Number(attrs.value), unit, ucum, appleDateToIso(attrs.startDate)));
    }
    return out;
  }

  function appleDateToIso(value) {
    // "2024-05-02 07:00:00 -0000" -> "2024-05-02T07:00:00Z"
    const m = /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}) ([+-]\d{4})$/.exec(value.trim());
    if (!m) return value;
    const off = m[3] === "-0000" || m[3] === "+0000" ? "Z" : m[3].slice(0, 3) + ":" + m[3].slice(3);
    return m[1] + "T" + m[2] + off;
  }

  function parseHl7(text, ctx) {
    const segments = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").trim().split("\n");
    const out = [];
    segments.forEach((seg) => {
      const f = seg.split("|");
      if (f[0] === "PV1") {
        const patientClass = f[2];
        if (patientClass) {
          out.push({
            resourceType: "Encounter",
            id: stableId([ctx.patientId, "encounter", patientClass]),
            status: "finished",
            class: hl7Class(patientClass),
            subject: { reference: "Patient/" + ctx.patientId },
            meta: { tag: [], security: [] },
            _source: ctx.sourceType,
          });
        }
      } else if (f[0] === "OBX") {
        const valueType = f[2];
        const idParts = (f[3] || "").split("^");
        const code = idParts[0];
        const display = idParts[1] || code;
        const codingSystemName = idParts[2];
        const value = f[5];
        const unit = (f[6] || "").split("^")[0] || "1";
        const eff = hl7TsToIso(f[13]);
        if (!code) return;
        const system = codingSystemName === "LN" ? LOINC : SOURCE.HL7;
        if (valueType === "NM" && value) {
          out.push(makeObservation(ctx, system, code, display, Number(value), unit, unit, eff));
        }
      }
    });
    return out;
  }

  function hl7Class(hl7) {
    const system = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    switch ((hl7 || "").toUpperCase()) {
      case "I": return { system, code: "IMP", display: "inpatient encounter" };
      case "E": return { system, code: "EMER", display: "emergency" };
      default: return { system, code: "AMB", display: "ambulatory" };
    }
  }

  function hl7TsToIso(ts) {
    if (!ts) return "1970-01-01T00:00:00Z";
    const d = ts.slice(0, 14);
    if (d.length >= 14) {
      return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}T${d.slice(8, 10)}:${d.slice(10, 12)}:${d.slice(12, 14)}Z`;
    }
    return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}T00:00:00Z`;
  }

  const ADAPTERS = {
    "wearable-json": parseWearable,
    "scale-csv": parseScale,
    "apple-health-xml": parseAppleHealth,
    "hl7v2": parseHl7,
  };

  // ---------------- Terminology ----------------

  function parseMappingsCsv(text) {
    const lines = text.trim().split(/\r?\n/);
    const map = {};
    for (let i = 1; i < lines.length; i++) {
      const [sourceSystem, sourceCode, targetSystem, targetCode, display] = lines[i].split(",");
      const key = sourceSystem + "|" + sourceCode;
      (map[key] = map[key] || []).push({ system: targetSystem, code: targetCode, display: display });
    }
    return map;
  }

  function mapTerminology(resource, mappings) {
    const result = { mapped: [], unmapped: [] };
    if (resource.resourceType !== "Observation" || !resource.code) return result;
    const existing = resource.code.coding.slice();
    existing.forEach((coding) => {
      if (STANDARD_SYSTEMS.has(coding.system)) {
        result.mapped.push(coding.system + "|" + coding.code);
        return;
      }
      const targets = mappings[coding.system + "|" + coding.code];
      if (!targets || targets.length === 0) {
        result.unmapped.push(coding.system + "|" + coding.code);
        if (!resource.meta.tag.some((t) => t.code === "unmapped-terminology")) {
          resource.meta.tag.push({
            system: "https://vitalshub.example/fhir/flags",
            code: "unmapped-terminology",
            display: "Unmapped source terminology",
          });
        }
      } else {
        targets.forEach((t) => {
          if (!resource.code.coding.some((c) => c.system === t.system && c.code === t.code)) {
            resource.code.coding.push({ system: t.system, code: t.code, display: t.display });
          }
        });
        result.mapped.push(coding.system + "|" + coding.code);
      }
    });
    return result;
  }

  // ---------------- Validation ----------------

  function validate(resource) {
    if (resource.resourceType === "Observation") {
      if (!resource.status) return { valid: false, reason: "Observation.status is required" };
      if (!resource.code || !resource.code.coding.length) return { valid: false, reason: "Observation.code is required" };
      const ucum = resource.valueQuantity && resource.valueQuantity.code;
      if (ucum && !VALID_UCUM.has(ucum)) {
        return { valid: false, reason: `Error processing unit '${ucum}': The unit '${ucum}' is unknown` };
      }
    }
    return { valid: true, reason: "OK" };
  }

  // ---------------- Category / consent ----------------

  function categoryOf(observation) {
    if (!observation.code) return "other";
    for (const c of observation.code.coding) {
      if (LOINC_TO_CATEGORY[c.code]) return LOINC_TO_CATEGORY[c.code];
    }
    return "other";
  }

  // ---------------- De-identification (Safe Harbor) ----------------

  function deidentifyPatient(patient, pseudonym) {
    const p = JSON.parse(JSON.stringify(patient));
    p.id = pseudonym;
    delete p.name;
    delete p.telecom;
    delete p.address;
    delete p.identifier;
    if (p.birthDate) p.birthDate = String(p.birthDate).slice(0, 4); // year only
    p.meta = p.meta || {};
    p.meta.security = [{ system: "http://terminology.hl7.org/CodeSystem/v3-ObservationValue", code: "ANONYED", display: "anonymized" }];
    return p;
  }

  function deidentifyObservation(observation, pseudonym) {
    const o = JSON.parse(JSON.stringify(observation));
    o.id = "anon-" + Math.random().toString(16).slice(2, 10);
    o.subject = { reference: "Patient/" + pseudonym };
    if (o.effectiveDateTime) o.effectiveDateTime = o.effectiveDateTime.slice(0, 4); // year only
    o.meta = o.meta || {};
    o.meta.security = [{ system: "http://terminology.hl7.org/CodeSystem/v3-ObservationValue", code: "ANONYED", display: "anonymized" }];
    return o;
  }

  // ---------------- Store with un-bypassable audit ----------------

  class Store {
    constructor() {
      this.resources = [];
      this.quarantine = [];
      this.audit = [];
      this.metrics = { processed: 0, stored: 0, quarantined: 0, unmapped: new Set() };
      this._ctx = { who: "you", purpose: "TREATMENT" };
    }
    withActor(who, purpose) {
      this._ctx = { who, purpose };
      return this;
    }
    _log(action, resource, patientRef) {
      if (resource.resourceType === "AuditEvent") return;
      this.audit.push({
        recorded: new Date().toISOString(),
        who: this._ctx.who,
        action: action,
        resource: resource.resourceType + "/" + resource.id,
        patient: patientRef || subjectRef(resource),
        purpose: this._ctx.purpose,
        outcome: "0",
      });
    }
    create(resource) {
      this.resources.push(resource);
      this._log("C", resource);
      return resource;
    }
    read(type, id) {
      const found = this.resources.find((r) => r.resourceType === type && r.id === id) || null;
      if (found) this._log("R", found);
      return found;
    }
    searchObservations(patientId, code) {
      const patientRef = "Patient/" + patientId;
      let result = this.resources.filter((r) => r.resourceType === "Observation" && subjectRef(r) === patientRef);
      if (code) result = result.filter((o) => o.code.coding.some((c) => c.code === code));
      result = result.slice().sort((a, b) => (a.effectiveDateTime || "").localeCompare(b.effectiveDateTime || ""));
      this.audit.push({
        recorded: new Date().toISOString(), who: this._ctx.who, action: "E",
        resource: "Observation", patient: patientRef, purpose: this._ctx.purpose, outcome: "0",
      });
      return result;
    }
    auditForPatient(patientId) {
      return this.audit.filter((a) => a.patient === "Patient/" + patientId);
    }
  }

  function subjectRef(resource) {
    return resource.subject ? resource.subject.reference : null;
  }

  // ---------------- Orchestration ----------------

  /**
   * Runs adapter -> terminology -> validate -> store for one source payload.
   * Returns a per-ingest result summary. Mutates the given store + mappings.
   */
  function ingest(store, mappings, sourceType, rawContent, patientId, actor) {
    const adapter = ADAPTERS[sourceType];
    if (!adapter) throw new Error("No adapter for " + sourceType);
    const ctx = { patientId: patientId, sourceType: sourceType };
    store.withActor(actor || "importer", "INGESTION");

    const resources = adapter(rawContent, ctx);
    const result = { sourceType, stored: [], quarantined: [], unmapped: [] };
    resources.forEach((resource) => {
      const mapping = mapTerminology(resource, mappings);
      mapping.unmapped.forEach((u) => {
        result.unmapped.push(u);
        store.metrics.unmapped.add(u);
      });
      const outcome = validate(resource);
      store.metrics.processed++;
      if (outcome.valid) {
        store.create(resource);
        store.metrics.stored++;
        result.stored.push(resource.id);
      } else {
        store.quarantine.push({ sourceType, resourceType: resource.resourceType, id: resource.id, reason: outcome.reason, resource });
        store.metrics.quarantined++;
        result.quarantined.push({ id: resource.id, reason: outcome.reason });
      }
    });
    return result;
  }

  function share(store, patientId, consent) {
    if (!consent) {
      const err = new Error("No consent on file for this recipient");
      err.code = "CONSENT_DENIED";
      throw err;
    }
    store.withActor(consent.recipient, "SHARE");
    const patient = store.read("Patient", patientId);
    if (!patient) throw new Error("Unknown patient");
    const observations = store.searchObservations(patientId).filter((o) =>
      consent.categories.includes(categoryOf(o))
    );
    const pseudonym = "anon-" + Math.random().toString(16).slice(2, 10);
    const entries = [];
    if (consent.requireDeidentified) {
      entries.push(deidentifyPatient(patient, pseudonym));
      observations.forEach((o) => entries.push(deidentifyObservation(o, pseudonym)));
    } else {
      entries.push(patient);
      observations.forEach((o) => entries.push(o));
    }
    return { resourceType: "Bundle", type: "collection", entry: entries.map((r) => ({ resource: r })) };
  }

  function metricsSnapshot(store) {
    const m = store.metrics;
    const total = m.processed;
    return {
      recordsProcessed: total,
      recordsStored: m.stored,
      recordsQuarantined: m.quarantined,
      validationPassRate: total === 0 ? 1 : m.stored / total,
      quarantineRate: total === 0 ? 0 : m.quarantined / total,
      unmappedCodeCount: m.unmapped.size,
      unmappedCodes: Array.from(m.unmapped).sort(),
    };
  }

  return {
    SOURCE, LOINC, SNOMED,
    parseMappingsCsv,
    parseWearable, parseScale, parseAppleHealth, parseHl7,
    mapTerminology, validate, categoryOf,
    deidentifyPatient, deidentifyObservation,
    Store, ingest, share, metricsSnapshot, subjectRef,
  };
});
