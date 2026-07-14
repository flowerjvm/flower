package io.github.flowerjvm.flower.spring;

/**
 * Minimal built-in HTML console for inspecting Flower runtime dumps.
 */
final class FlowerConsoleHtml {

    private FlowerConsoleHtml() {
    }

    static String render(String apiPath, long initialPollIntervalMs) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Flower Console</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f7f8fb;
                      --panel: #ffffff;
                      --line: #d8dde8;
                      --line-strong: #aab4c5;
                      --text: #172033;
                      --muted: #667085;
                      --ok: #16794c;
                      --warn: #9a5b00;
                      --bad: #b42318;
                      --run: #175cd3;
                      --idle: #475467;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--text);
                      font: 14px/1.45 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                    }
                    header {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 16px;
                      padding: 14px 18px;
                      border-bottom: 1px solid var(--line);
                      background: var(--panel);
                      position: sticky;
                      top: 0;
                      z-index: 5;
                    }
                    h1 {
                      margin: 0;
                      font-size: 18px;
                      font-weight: 650;
                    }
                    main {
                      padding: 16px;
                      display: grid;
                      gap: 14px;
                    }
                    button, input {
                      height: 34px;
                      border: 1px solid var(--line-strong);
                      background: var(--panel);
                      color: var(--text);
                      border-radius: 6px;
                      padding: 0 10px;
                      font: inherit;
                    }
                    button {
                      cursor: pointer;
                      font-weight: 600;
                    }
                    button.primary {
                      background: #1d4ed8;
                      border-color: #1d4ed8;
                      color: #fff;
                    }
                    button:disabled {
                      color: var(--muted);
                      cursor: default;
                      opacity: .7;
                    }
                    label {
                      display: inline-flex;
                      align-items: center;
                      gap: 6px;
                      color: var(--muted);
                      white-space: nowrap;
                    }
                    input[type="number"] {
                      width: 94px;
                    }
                    .toolbar {
                      display: flex;
                      align-items: center;
                      flex-wrap: wrap;
                      gap: 8px;
                    }
                    .status-line {
                      display: flex;
                      align-items: center;
                      flex-wrap: wrap;
                      gap: 10px;
                      color: var(--muted);
                    }
                    .pill {
                      display: inline-flex;
                      align-items: center;
                      min-height: 24px;
                      padding: 2px 8px;
                      border: 1px solid var(--line);
                      border-radius: 999px;
                      background: #fff;
                      color: var(--idle);
                      font-size: 12px;
                      font-weight: 700;
                    }
                    .pill.RUNNING, .pill.READY, .pill.CREATED { color: var(--run); }
                    .pill.FINISHED { color: var(--ok); }
                    .pill.FAILED, .pill.STOPPED, .pill.CANCELLED { color: var(--bad); }
                    .summary {
                      display: grid;
                      grid-template-columns: repeat(4, minmax(120px, 1fr));
                      gap: 10px;
                    }
                    .metric {
                      border: 1px solid var(--line);
                      background: var(--panel);
                      border-radius: 8px;
                      padding: 10px 12px;
                    }
                    .metric .label {
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .metric .value {
                      margin-top: 2px;
                      font-size: 22px;
                      font-weight: 700;
                    }
                    .section {
                      border: 1px solid var(--line);
                      background: var(--panel);
                      border-radius: 8px;
                      overflow: hidden;
                    }
                    .section-head {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 12px;
                      padding: 10px 12px;
                      border-bottom: 1px solid var(--line);
                      background: #fbfcfe;
                    }
                    .section-title {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                      font-weight: 700;
                    }
                    .table-wrap { overflow-x: auto; }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                      table-layout: fixed;
                    }
                    th, td {
                      padding: 9px 10px;
                      border-bottom: 1px solid var(--line);
                      text-align: left;
                      vertical-align: top;
                    }
                    th {
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                      background: #fbfcfe;
                    }
                    tr:last-child td { border-bottom: 0; }
                    code {
                      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                      font-size: 12px;
                    }
                    .mono {
                      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                      font-size: 12px;
                    }
                    .muted { color: var(--muted); }
                    .error {
                      border: 1px solid #f3b4af;
                      background: #fff6f5;
                      color: var(--bad);
                      border-radius: 8px;
                      padding: 10px 12px;
                    }
                    .steps {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 6px;
                    }
                    .step {
                      display: inline-flex;
                      align-items: center;
                      max-width: 240px;
                      min-height: 24px;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 2px 7px;
                      background: #fff;
                      color: var(--muted);
                    }
                    .step.current {
                      border-color: #1d4ed8;
                      color: #1d4ed8;
                      background: #eff6ff;
                      font-weight: 700;
                    }
                    .step.done {
                      color: var(--ok);
                      background: #f0fdf4;
                      border-color: #b7e4c7;
                    }
                    .empty {
                      color: var(--muted);
                      padding: 18px;
                    }
                    @media (max-width: 760px) {
                      header { align-items: flex-start; flex-direction: column; }
                      .summary { grid-template-columns: repeat(2, minmax(120px, 1fr)); }
                      th, td { padding: 8px; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>Flower Console</h1>
                      <div class="status-line">
                        <span id="observeState" class="pill">STOPPED</span>
                        <span id="lastUpdated">Not loaded</span>
                        <span id="endpoint" class="mono"></span>
                      </div>
                    </div>
                    <div class="toolbar">
                      <button id="startBtn" class="primary">Start</button>
                      <button id="stopBtn">Stop</button>
                      <button id="refreshBtn">Refresh</button>
                      <label>Interval <input id="pollMs" type="number" min="500" step="500"> ms</label>
                    </div>
                  </header>
                  <main>
                    <div id="error"></div>
                    <section class="summary" id="summary"></section>
                    <section id="workers"></section>
                  </main>
                  <script>
                    const API_PATH = "__API_PATH__";
                    const DEFAULT_POLL_MS = __POLL_MS__;
                    const state = {
                      timer: null,
                      observing: false,
                      lastDump: null,
                      pollMs: Number(localStorage.getItem("flower.console.pollMs") || DEFAULT_POLL_MS)
                    };

                    const el = (id) => document.getElementById(id);
                    const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, ch => ({
                      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
                    }[ch]));
                    const pill = (value) => `<span class="pill ${escapeHtml(value)}">${escapeHtml(value || "-")}</span>`;

                    el("endpoint").textContent = API_PATH;
                    el("pollMs").value = state.pollMs;
                    el("stopBtn").disabled = true;

                    el("pollMs").addEventListener("change", () => {
                      const next = Math.max(500, Number(el("pollMs").value || DEFAULT_POLL_MS));
                      state.pollMs = next;
                      el("pollMs").value = next;
                      localStorage.setItem("flower.console.pollMs", String(next));
                      if (state.observing) restartTimer();
                    });
                    el("startBtn").addEventListener("click", start);
                    el("stopBtn").addEventListener("click", stop);
                    el("refreshBtn").addEventListener("click", () => loadDump());

                    function start() {
                      state.observing = true;
                      el("observeState").textContent = "RUNNING";
                      el("observeState").className = "pill RUNNING";
                      el("startBtn").disabled = true;
                      el("stopBtn").disabled = false;
                      loadDump();
                      restartTimer();
                    }

                    function stop() {
                      state.observing = false;
                      clearInterval(state.timer);
                      state.timer = null;
                      el("observeState").textContent = "STOPPED";
                      el("observeState").className = "pill STOPPED";
                      el("startBtn").disabled = false;
                      el("stopBtn").disabled = true;
                    }

                    function restartTimer() {
                      clearInterval(state.timer);
                      state.timer = setInterval(loadDump, state.pollMs);
                    }

                    async function loadDump() {
                      try {
                        const res = await fetch(API_PATH, { cache: "no-store", credentials: "same-origin" });
                        if (!res.ok) throw new Error(`HTTP ${res.status}`);
                        const dump = await res.json();
                        state.lastDump = dump;
                        render(dump);
                        el("error").innerHTML = "";
                        el("lastUpdated").textContent = `Updated ${new Date().toLocaleTimeString()}`;
                      } catch (err) {
                        el("error").innerHTML = `<div class="error">Failed to load Flower dump: ${escapeHtml(err.message || err)}</div>`;
                      }
                    }

                    function render(dump) {
                      const workers = dump.workers || [];
                      const flows = workers.flatMap(w => (w.flows || []).map(f => ({ worker: w, flow: f })));
                      const runningFlows = flows.filter(x => x.flow.state === "RUNNING" || x.flow.state === "READY").length;
                      const failedFlows = flows.filter(x => x.flow.state === "FAILED").length;

                      el("summary").innerHTML = [
                        metric("Engine", pill(dump.engineState)),
                        metric("Workers", workers.length),
                        metric("Active flows", flows.length),
                        metric("Running", `${runningFlows}${failedFlows ? " / failed " + failedFlows : ""}`)
                      ].join("");

                      el("workers").innerHTML = workers.length
                        ? workers.map(renderWorker).join("")
                        : `<section class="section"><div class="empty">No workers found.</div></section>`;
                    }

                    function metric(label, value) {
                      return `<div class="metric"><div class="label">${escapeHtml(label)}</div><div class="value">${value}</div></div>`;
                    }

                    function renderWorker(worker) {
                      const flows = worker.flows || [];
                      const body = flows.length
                        ? `<div class="table-wrap"><table>
                            <thead><tr>
                              <th style="width:19%">Flow</th>
                              <th style="width:12%">State</th>
                              <th style="width:18%">Current</th>
                              <th style="width:35%">Steps</th>
                              <th style="width:16%">Context</th>
                            </tr></thead>
                            <tbody>${flows.map(renderFlow).join("")}</tbody>
                           </table></div>`
                        : `<div class="empty">No active flows.</div>`;
                      return `<section class="section">
                        <div class="section-head">
                          <div class="section-title"><span>${escapeHtml(worker.name)}</span>${pill(worker.state)}</div>
                          <div class="muted">interval ${escapeHtml(worker.intervalMillis)} ms</div>
                        </div>
                        ${body}
                      </section>`;
                    }

                    function renderFlow(flow) {
                      const current = `${flow.currentStepId || "-"}${Number.isInteger(flow.currentStepIndex) && flow.currentStepIndex >= 0 ? " #" + flow.currentStepIndex : ""}<br><span class="muted">stepNo ${escapeHtml(flow.currentStepNo)}</span>`;
                      return `<tr>
                        <td><div><code>${escapeHtml(flow.flowType)}/${escapeHtml(flow.flowKey)}</code></div></td>
                        <td>${pill(flow.state)}</td>
                        <td>${current}</td>
                        <td>${renderSteps(flow)}</td>
                        <td>${renderContext(flow.executionContext)}${flow.failureCause ? `<div class="error">${escapeHtml(flow.failureCause)}</div>` : ""}</td>
                      </tr>`;
                    }

                    function renderSteps(flow) {
                      const currentIndex = flow.currentStepIndex;
                      const steps = flow.steps || [];
                      if (!steps.length) return `<span class="muted">No step metadata</span>`;
                      return `<div class="steps">${steps.map(step => {
                        const cls = step.index === currentIndex ? "current" : (step.index < currentIndex ? "done" : "");
                        const flags = [step.guarded ? "guard" : "", step.recoverable ? "durable" : ""].filter(Boolean).join(", ");
                        const title = `${step.stepType || ""}${flags ? " | " + flags : ""}`;
                        return `<span class="step ${cls}" title="${escapeHtml(title)}"><code>${escapeHtml(step.index)} ${escapeHtml(step.stepId)}</code></span>`;
                      }).join("")}</div>`;
                    }

                    function renderContext(ctx) {
                      if (!ctx) return `<span class="muted">-</span>`;
                      const entries = Object.entries(ctx).filter(([, value]) => value !== null && value !== undefined && value !== "");
                      if (!entries.length) return `<span class="muted">empty</span>`;
                      return entries.map(([key, value]) => `<div><span class="muted">${escapeHtml(key)}</span> <code>${escapeHtml(value)}</code></div>`).join("");
                    }
                  </script>
                </body>
                </html>
                """
                .replace("__API_PATH__", jsString(apiPath))
                .replace("__POLL_MS__", Long.toString(initialPollIntervalMs > 0L ? initialPollIntervalMs : 3000L));
    }

    private static String jsString(String value) {
        String v = value == null || value.isEmpty() ? "/internal/flower/console/dump" : value;
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
