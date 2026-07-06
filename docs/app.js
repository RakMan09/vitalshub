/* VitalsHub interactive demo - wires the browser pipeline (pipeline.js) to the UI. */
(function () {
  "use strict";
  const P = window.Pipeline;
  const PATIENT_ID = "demo";

  const SOURCES = [
    { type: "wearable-json", file: "data/wearable.json", title: "Wearable (Fitbit-style JSON)", desc: "Deeply nested daily summaries + heart-rate samples." },
    { type: "scale-csv", file: "data/scale.csv", title: "Smart scale (CSV)", desc: "weight_kg, body_fat_pct, bmi per day." },
    { type: "apple-health-xml", file: "data/apple-health.xml", title: "Apple Health (XML)", desc: "HealthKit <Record> elements." },
    { type: "hl7v2", file: "data/clinic-oru.hl7", title: "Clinic (HL7 v2 ORU^R01)", desc: "Pipe-delimited PID / PV1 / OBX segments." },
  ];

  const state = { store: null, mappings: {}, samples: {}, charts: {} };

  function seedPatient(store) {
    store.withActor("you", "TREATMENT").create({
      resourceType: "Patient", id: PATIENT_ID,
      name: [{ family: "Rivera", given: ["Sam"] }],
      telecom: [{ system: "phone", value: "+1-555-0142" }],
      address: [{ city: "Springfield", state: "IL", postalCode: "62704" }],
      identifier: [{ system: "urn:mrn", value: "MRN-00099" }],
      gender: "male", birthDate: "1985-03-12",
      meta: { tag: [], security: [] },
    });
  }

  function newStore() {
    const store = new P.Store();
    seedPatient(store);
    return store;
  }

  function el(id) { return document.getElementById(id); }

  function renderSourceCards() {
    const container = el("sourceCards");
    container.innerHTML = "";
    SOURCES.forEach((s) => {
      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML =
        `<span class="src-type">${s.type}</span>
         <h4>${s.title}</h4>
         <div class="desc">${s.desc}</div>
         <pre class="raw">${escapeHtml((state.samples[s.type] || "").trim())}</pre>
         <div class="card-foot">
           <span class="status pending" data-status="${s.type}">Not ingested</span>
           <button class="btn primary" data-ingest="${s.type}">Ingest</button>
         </div>`;
      container.appendChild(card);
    });
    container.querySelectorAll("[data-ingest]").forEach((btn) => {
      btn.addEventListener("click", () => ingestOne(btn.getAttribute("data-ingest")));
    });
  }

  function ingestOne(type) {
    const result = P.ingest(state.store, state.mappings, type, state.samples[type], PATIENT_ID, "you");
    const status = document.querySelector(`[data-status="${type}"]`);
    if (status) {
      status.textContent = `${result.stored.length} stored` + (result.quarantined.length ? `, ${result.quarantined.length} quarantined` : "");
      status.className = "status done";
    }
    refreshAll();
  }

  function ingestAll() {
    SOURCES.forEach((s) => {
      // avoid double-ingesting the same source
      const status = document.querySelector(`[data-status="${s.type}"]`);
      if (status && status.classList.contains("done")) return;
      ingestOne(s.type);
    });
  }

  function obsByCode(code) {
    return state.store.resources
      .filter((r) => r.resourceType === "Observation" && r.code.coding.some((c) => c.code === code))
      .sort((a, b) => a.effectiveDateTime.localeCompare(b.effectiveDateTime));
  }

  function lineChart(id, label, color) {
    const ctx = el(id).getContext("2d");
    return new Chart(ctx, {
      type: "line",
      data: { labels: [], datasets: [{ label, data: [], borderColor: color, backgroundColor: color + "22", tension: 0.3, fill: true, pointRadius: 4 }] },
      options: { responsive: true, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: false } } },
    });
  }

  function refreshCharts() {
    const specs = [
      { chart: "weight", code: "29463-7" },
      { chart: "hr", code: "8867-4" },
      { chart: "steps", code: "41950-7" },
    ];
    specs.forEach((spec) => {
      const obs = obsByCode(spec.code);
      const chart = state.charts[spec.chart];
      chart.data.labels = obs.map((o) => o.effectiveDateTime.slice(0, 10));
      chart.data.datasets[0].data = obs.map((o) => o.valueQuantity.value);
      chart.update();
    });
  }

  function refreshObsTable() {
    const rows = [];
    state.store.resources
      .filter((r) => r.resourceType === "Observation")
      .forEach((o) => {
        const src = o.code.coding.find((c) => c.system.includes("vitalshub.example")) || o.code.coding[0];
        const loinc = o.code.coding.find((c) => c.system === P.LOINC);
        const snomed = o.code.coding.find((c) => c.system === P.SNOMED);
        const flags = (o.meta.tag || []).some((t) => t.code === "unmapped-terminology")
          ? `<span class="pill flag">unmapped</span>` : "";
        rows.push(
          `<tr>
            <td><span class="pill src">${o._source}</span></td>
            <td>${escapeHtml(src.code)}</td>
            <td>${loinc ? `<span class="pill loinc">LOINC ${loinc.code}</span>` : ""}
                ${snomed ? `<span class="pill snomed">SNOMED ${snomed.code}</span>` : ""} ${flags}</td>
            <td>${o.valueQuantity.value} ${escapeHtml(o.valueQuantity.unit)}</td>
            <td>${o.effectiveDateTime.slice(0, 10)}</td>
          </tr>`);
      });
    state.store.quarantine.forEach((q) => {
      rows.push(
        `<tr>
          <td><span class="pill src">${q.sourceType}</span></td>
          <td>${escapeHtml(q.resource.code ? q.resource.code.coding[0].code : "")}</td>
          <td><span class="pill flag">quarantined</span></td>
          <td colspan="2" title="${escapeHtml(q.reason)}">${escapeHtml(q.reason.slice(0, 60))}…</td>
        </tr>`);
    });
    el("obsTable").innerHTML = rows.length
      ? `<table><thead><tr><th>Source</th><th>Source code</th><th>Mapped terminology</th><th>Value</th><th>Date</th></tr></thead><tbody>${rows.join("")}</tbody></table>`
      : `<p class="section-sub">Ingest a source to see normalized observations.</p>`;
  }

  function refreshMetrics() {
    const m = P.metricsSnapshot(state.store);
    const tiles = [
      { val: m.recordsProcessed, lbl: "records processed" },
      { val: m.recordsStored, lbl: "stored" },
      { val: m.recordsQuarantined, lbl: "quarantined" },
      { val: (m.validationPassRate * 100).toFixed(0) + "%", lbl: "validation pass rate" },
      { val: m.unmappedCodeCount, lbl: "unmapped codes" },
    ];
    el("metricTiles").innerHTML = tiles.map((t) => `<div class="tile"><div class="val">${t.val}</div><div class="lbl">${t.lbl}</div></div>`).join("");
  }

  function refreshAudit() {
    const log = state.store.auditForPatient(PATIENT_ID);
    if (!log.length) { el("auditTable").innerHTML = `<p class="section-sub">No access yet — ingest or share to generate audit events.</p>`; return; }
    const actionName = { C: "create", R: "read", U: "update", D: "delete", E: "search" };
    const rows = log.slice().reverse().map((a) =>
      `<tr><td>${a.recorded.slice(11, 19)}</td><td>${escapeHtml(a.who)}</td><td>${actionName[a.action] || a.action}</td><td>${escapeHtml(a.resource)}</td><td>${escapeHtml(a.purpose)}</td></tr>`).join("");
    el("auditTable").innerHTML = `<table><thead><tr><th>Time</th><th>Who</th><th>Action</th><th>Resource</th><th>Purpose</th></tr></thead><tbody>${rows}</tbody></table>`;
  }

  function refreshOriginalPatient() {
    const patient = state.store.resources.find((r) => r.resourceType === "Patient" && r.id === PATIENT_ID);
    el("originalPatient").textContent = patient ? JSON.stringify(patient, replacer, 2) : "—";
  }

  function replacer(key, value) { return key === "_source" ? undefined : value; }

  function doShare(withConsent) {
    const msg = el("shareMsg");
    try {
      let consent = null;
      if (withConsent) {
        const categories = Array.from(document.querySelectorAll('#consentForm fieldset input:checked')).map((c) => c.value);
        consent = {
          recipient: el("recipient").value || "recipient",
          categories,
          requireDeidentified: el("deidToggle").checked,
        };
      }
      const bundle = P.share(state.store, PATIENT_ID, consent);
      const patient = bundle.entry.map((e) => e.resource).find((r) => r.resourceType === "Patient");
      const obsCount = bundle.entry.filter((e) => e.resource.resourceType === "Observation").length;
      el("sharedPatient").textContent = JSON.stringify(patient, replacer, 2);
      msg.className = "share-msg ok";
      msg.textContent = `Shared ${obsCount} observation(s) with "${consent.recipient}"${consent.requireDeidentified ? " (de-identified)" : ""}. Access recorded in the audit trail.`;
    } catch (e) {
      el("sharedPatient").textContent = "—";
      msg.className = "share-msg err";
      msg.textContent = "Denied: " + e.message;
    }
    refreshAudit();
  }

  function refreshAll() {
    refreshCharts();
    refreshObsTable();
    refreshMetrics();
    refreshAudit();
    refreshOriginalPatient();
  }

  function reset() {
    state.store = newStore();
    document.querySelectorAll("[data-status]").forEach((s) => { s.textContent = "Not ingested"; s.className = "status pending"; });
    el("sharedPatient").textContent = "—";
    el("shareMsg").textContent = "";
    refreshAll();
  }

  function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  async function loadSamples() {
    const [mappingsText] = await Promise.all([
      fetch("data/mappings.csv").then((r) => r.text()),
    ]);
    state.mappings = P.parseMappingsCsv(mappingsText);
    await Promise.all(SOURCES.map(async (s) => {
      state.samples[s.type] = await fetch(s.file).then((r) => r.text());
    }));
  }

  async function init() {
    if (window.mermaid) { window.mermaid.initialize({ startOnLoad: true, theme: "neutral" }); }
    state.charts.weight = lineChart("weightChart", "kg", "#0ea5a5");
    state.charts.hr = lineChart("hrChart", "bpm", "#dc2626");
    state.charts.steps = lineChart("stepsChart", "steps", "#2563eb");

    try {
      await loadSamples();
    } catch (e) {
      el("sourceCards").innerHTML = `<p class="share-msg err">Could not load sample data (${e.message}). Serve this folder over HTTP (GitHub Pages) rather than opening the file directly.</p>`;
      return;
    }

    state.store = newStore();
    renderSourceCards();
    refreshAll();

    el("ingestAll").addEventListener("click", ingestAll);
    el("resetBtn").addEventListener("click", reset);
    el("consentForm").addEventListener("submit", (e) => { e.preventDefault(); doShare(true); });
    el("noConsentBtn").addEventListener("click", () => doShare(false));
  }

  document.addEventListener("DOMContentLoaded", init);
})();
