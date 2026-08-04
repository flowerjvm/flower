"use strict";

const state = {
    traces: [],
    totalMatched: 0,
    sources: [],
    diagnostics: null,
    selectedTraceId: null,
    detail: null,
    selectedEventId: null,
    activeTab: "timeline",
    category: "ALL",
    refreshTimer: null
};

const elements = {};

document.addEventListener("DOMContentLoaded", () => {
    bindElements();
    bindEvents();
    refresh(false);
    state.refreshTimer = window.setInterval(() => refresh(true), 10000);
});

function bindElements() {
    [
        "health-dot", "health-label", "trace-path", "refresh-button",
        "trace-count", "search-input", "status-filter", "source-filter",
        "diagnostic-banner", "trace-list", "trace-list-empty", "loaded-at",
        "event-total", "welcome-state", "detail-view", "detail-title",
        "detail-status", "detail-trace-id", "detail-times", "source-badges",
        "metric-strip", "timeline-tab", "runs-tab", "events-tab",
        "category-filter", "timeline-count", "timeline-list", "run-list",
        "event-table-body", "event-inspector", "close-inspector",
        "inspector-category", "inspector-title", "inspector-metadata",
        "artifact-section", "artifact-list", "attribute-json", "toast"
    ].forEach(id => {
        elements[id] = document.getElementById(id);
    });
    elements.tabButtons = Array.from(document.querySelectorAll(".tab-button"));
    elements.detailContent = document.querySelector(".detail-content");
}

function bindEvents() {
    elements["refresh-button"].addEventListener("click", () => refresh(false));
    elements["status-filter"].addEventListener("change", () => loadTraceList(false));
    elements["source-filter"].addEventListener("change", () => loadTraceList(false));
    elements["category-filter"].addEventListener("change", event => {
        state.category = event.target.value;
        renderTimeline();
    });
    let searchDelay = null;
    elements["search-input"].addEventListener("input", () => {
        window.clearTimeout(searchDelay);
        searchDelay = window.setTimeout(() => loadTraceList(false), 180);
    });
    elements.tabButtons.forEach(button => {
        button.addEventListener("click", () => selectTab(button.dataset.tab));
    });
    elements["close-inspector"].addEventListener("click", closeInspector);
}

async function refresh(silent) {
    try {
        await Promise.all([loadHealth(), loadTraceList(silent)]);
        if (state.selectedTraceId) {
            await loadTraceDetail(state.selectedTraceId, true);
        }
    } catch (error) {
        setHealth("DOWN");
        if (!silent) {
            showToast(error.message || "Studio refresh failed");
        }
    }
}

async function loadHealth() {
    const health = await fetchJson("/api/health");
    setHealth(health.status);
    const diagnostics = health.diagnostics || {};
    elements["trace-path"].textContent = diagnostics.traceFile || "";
    elements["trace-path"].title = diagnostics.traceFile || "";
}

async function loadTraceList(silent) {
    const parameters = new URLSearchParams();
    const query = elements["search-input"].value.trim();
    const status = elements["status-filter"].value;
    const source = elements["source-filter"].value;
    if (query) parameters.set("q", query);
    if (status && status !== "ALL") parameters.set("status", status);
    if (source) parameters.set("source", source);
    parameters.set("limit", "250");

    try {
        const response = await fetchJson(`/api/traces?${parameters.toString()}`);
        state.traces = response.traces || [];
        state.totalMatched = response.totalMatched || 0;
        state.sources = response.sources || [];
        state.diagnostics = response.diagnostics || null;
        renderSourceOptions();
        renderTraceList(state.totalMatched);
        renderDiagnostics();
    } catch (error) {
        if (!silent) throw error;
    }
}

async function loadTraceDetail(traceId, preserveInspector) {
    const detail = await fetchJson(`/api/traces/${encodeURIComponent(traceId)}`);
    state.selectedTraceId = traceId;
    state.detail = detail;
    if (!preserveInspector) {
        state.selectedEventId = null;
    }
    renderTraceList(state.totalMatched);
    renderDetail();
}

function renderSourceOptions() {
    const select = elements["source-filter"];
    const selected = select.value;
    select.replaceChildren(option("", "All"));
    state.sources.forEach(source => select.appendChild(option(source, sourceLabel(source))));
    select.value = state.sources.includes(selected) ? selected : "";
}

function renderTraceList(totalMatched) {
    const list = elements["trace-list"];
    list.replaceChildren();
    state.traces.forEach(trace => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `trace-item${trace.traceId === state.selectedTraceId ? " is-selected" : ""}`;
        button.setAttribute("role", "option");
        button.setAttribute("aria-selected", trace.traceId === state.selectedTraceId ? "true" : "false");
        button.addEventListener("click", () => loadTraceDetail(trace.traceId, false).catch(showError));

        const top = div("trace-item-top");
        top.append(text("span", "trace-item-name", trace.displayName || trace.traceId));
        top.append(statusBadge(trace.status));
        button.append(top);
        button.append(text("span", "trace-item-id", trace.traceId));

        const meta = div("trace-item-meta");
        meta.append(text("span", "", formatDate(trace.updatedAt)));
        meta.append(text("span", "", formatDuration(trace.durationMillis)));
        button.append(meta);

        const metrics = div("trace-item-metrics");
        metrics.append(text("span", "", `${trace.runCount} runs`));
        metrics.append(text("span", "", `${trace.eventCount} events`));
        if (trace.failures) metrics.append(text("span", "", `${trace.failures} failures`));
        if (trace.waits) metrics.append(text("span", "", `${trace.waits} waits`));
        button.append(metrics);
        list.append(button);
    });
    elements["trace-count"].textContent = String(totalMatched);
    elements["trace-list-empty"].hidden = state.traces.length !== 0;
}

function renderDiagnostics() {
    const diagnostics = state.diagnostics;
    if (!diagnostics) return;
    elements["event-total"].textContent = `${formatNumber(diagnostics.eventCount || 0)} events`;
    elements["loaded-at"].textContent = diagnostics.loadedAt
        ? `Loaded ${formatTime(diagnostics.loadedAt)}` : "Not loaded";
    const messages = [];
    if (!diagnostics.traceFileExists) messages.push("Observation file not found");
    if (diagnostics.malformedLineCount) messages.push(`${diagnostics.malformedLineCount} malformed lines skipped`);
    if (diagnostics.duplicateEventCount) messages.push(`${diagnostics.duplicateEventCount} duplicate events skipped`);
    if (diagnostics.truncatedEventCount) messages.push(`${diagnostics.truncatedEventCount} older events truncated`);
    elements["diagnostic-banner"].hidden = messages.length === 0;
    elements["diagnostic-banner"].textContent = messages.join(" / ");
}

function renderDetail() {
    if (!state.detail) return;
    const summary = state.detail.summary;
    elements["welcome-state"].hidden = true;
    elements["detail-view"].hidden = false;
    elements["detail-title"].textContent = summary.displayName || summary.traceId;
    elements["detail-trace-id"].textContent = summary.traceId;
    elements["detail-status"].replaceWith(statusBadge(summary.status, "detail-status"));
    elements["detail-status"] = document.getElementById("detail-status");

    elements["detail-times"].replaceChildren(
        text("span", "", formatDate(summary.startedAt)),
        text("span", "", formatDuration(summary.durationMillis)),
        text("span", "", `${summary.eventCount} events`)
    );
    elements["source-badges"].replaceChildren(...summary.sources.map(sourceBadge));
    renderMetrics(summary);
    renderTimeline();
    renderRuns();
    renderEventTable();
    selectTab(state.activeTab);

    if (state.selectedEventId) {
        const selected = state.detail.events.find(event => event.eventId === state.selectedEventId);
        if (selected) openInspector(selected);
        else closeInspector();
    }
}

function renderMetrics(summary) {
    const metrics = [
        ["Runs", summary.runCount],
        ["Model calls", summary.modelCalls],
        ["Tool calls", summary.toolCalls],
        ["Actions", summary.actions],
        ["Waits", summary.waits],
        ["Failures", summary.failures],
        ["Tokens", (summary.inputTokens || 0) + (summary.outputTokens || 0)]
    ];
    elements["metric-strip"].replaceChildren(...metrics.map(([label, value]) => {
        const item = div("metric");
        item.append(text("span", "metric-label", label));
        item.append(text("strong", "metric-value", formatNumber(value || 0)));
        return item;
    }));
}

function renderTimeline() {
    if (!state.detail) return;
    const events = state.category === "ALL"
        ? state.detail.events
        : state.detail.events.filter(event => event.category === state.category);
    elements["timeline-count"].textContent = `${events.length} events`;
    elements["timeline-list"].replaceChildren(...events.map(event => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `timeline-event tone-${event.tone}${event.eventId === state.selectedEventId ? " is-selected" : ""}`;
        button.addEventListener("click", () => openInspector(event));
        button.append(text("span", "event-offset", `+${formatDuration(event.relativeMillis)}`));
        button.append(div("timeline-marker"));

        const main = div("event-main");
        main.append(text("span", "event-type", humanize(event.eventType)));
        main.append(text("span", "event-summary", event.summary || event.operationName || ""));
        button.append(main);

        const context = div("event-context");
        context.append(text("span", "event-source", sourceLabel(event.source)));
        context.append(text("span", "event-run", event.runId));
        button.append(context);
        button.append(text("span", "event-duration", event.durationMillis == null ? "" : formatDuration(event.durationMillis)));
        return button;
    }));
}

function renderRuns() {
    if (!state.detail) return;
    elements["run-list"].replaceChildren(...state.detail.runs.map(run => {
        const row = div("run-row run-grid");
        const label = div("run-label");
        label.style.paddingLeft = `${Math.min(run.depth, 12) * 18}px`;
        label.append(text("strong", "", run.label));
        label.append(text("span", "", `${sourceLabel(run.source)} / ${run.runId}`));
        row.append(label);
        row.append(statusBadge(run.status));
        row.append(text("span", "run-cell-muted", formatNumber(run.eventCount)));
        row.append(text("span", "run-cell-muted", formatDuration(run.durationMillis)));
        return row;
    }));
}

function renderEventTable() {
    if (!state.detail) return;
    elements["event-table-body"].replaceChildren(...state.detail.events.map(event => {
        const row = document.createElement("tr");
        row.tabIndex = 0;
        row.addEventListener("click", () => openInspector(event));
        row.addEventListener("keydown", keyboardEvent => {
            if (keyboardEvent.key === "Enter" || keyboardEvent.key === " ") {
                keyboardEvent.preventDefault();
                openInspector(event);
            }
        });
        [
            formatTime(event.occurredAt),
            sourceLabel(event.source),
            humanize(event.eventType),
            event.runId,
            event.operationName || event.operationId || ""
        ].forEach(value => row.append(text("td", "", value)));
        return row;
    }));
}

function selectTab(tab) {
    state.activeTab = tab;
    elements.tabButtons.forEach(button => {
        const active = button.dataset.tab === tab;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-selected", active ? "true" : "false");
    });
    elements["timeline-tab"].hidden = tab !== "timeline";
    elements["runs-tab"].hidden = tab !== "runs";
    elements["events-tab"].hidden = tab !== "events";
}

function openInspector(event) {
    state.selectedEventId = event.eventId;
    elements["event-inspector"].hidden = false;
    elements.detailContent.classList.add("has-inspector");
    elements["inspector-category"].textContent = event.category;
    elements["inspector-title"].textContent = humanize(event.eventType);
    const metadata = [
        ["Time", formatDate(event.occurredAt)],
        ["Source", event.source],
        ["Run", event.runId],
        ["Parent", event.parentRunId || ""],
        ["Operation", event.operationName || ""],
        ["Operation ID", event.operationId || ""],
        ["Event ID", event.eventId],
        ["Sequence", String(event.sequence)],
        ["Duration", event.durationMillis == null ? "" : formatDuration(event.durationMillis)]
    ];
    elements["inspector-metadata"].replaceChildren(...metadata.flatMap(([label, value]) => [
        text("dt", "", label), text("dd", "", value || "-")
    ]));
    elements["attribute-json"].textContent = JSON.stringify(event.attributes || {}, null, 2);
    renderArtifacts(event.artifacts || []);
    renderTimeline();
}

function renderArtifacts(artifacts) {
    elements["artifact-section"].hidden = artifacts.length === 0;
    elements["artifact-list"].replaceChildren(...artifacts.map(artifact => {
        const link = document.createElement("a");
        link.className = "artifact-link";
        link.href = artifact.downloadUrl;
        link.append(text("strong", "", artifact.artifactId || artifact.location));
        link.append(text("small", "", `${artifact.mediaType || "application/octet-stream"} / ${formatBytes(artifact.sizeBytes || 0)}`));
        return link;
    }));
}

function closeInspector() {
    state.selectedEventId = null;
    elements["event-inspector"].hidden = true;
    elements.detailContent.classList.remove("has-inspector");
    renderTimeline();
}

function setHealth(status) {
    const selected = String(status || "DOWN").toUpperCase();
    elements["health-label"].textContent = selected === "UP" ? "Ready" : selected === "DEGRADED" ? "Needs attention" : "Unavailable";
    elements["health-dot"].className = `health-dot health-${selected.toLowerCase()}`;
}

async function fetchJson(url) {
    const response = await fetch(url, {headers: {"Accept": "application/json"}, cache: "no-store"});
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(body.error || `Request failed (${response.status})`);
    }
    return body;
}

function statusBadge(status, id) {
    const value = String(status || "UNKNOWN").toUpperCase();
    const badge = text("span", `status-badge status-${value.toLowerCase()}`, humanize(value));
    if (id) badge.id = id;
    return badge;
}

function sourceBadge(source) {
    return text("span", `source-badge source-${source}`, sourceLabel(source));
}

function sourceLabel(source) {
    const labels = {
        "flower-core": "Core",
        "flower-agent": "Agent",
        "flower-ai-harness": "AI Harness",
        "flower-action-runtime": "Action"
    };
    return labels[source] || source;
}

function option(value, label) {
    const item = document.createElement("option");
    item.value = value;
    item.textContent = label;
    return item;
}

function div(className) {
    const item = document.createElement("div");
    item.className = className;
    return item;
}

function text(tag, className, value) {
    const item = document.createElement(tag);
    if (className) item.className = className;
    item.textContent = value == null ? "" : String(value);
    return item;
}

function humanize(value) {
    return String(value || "").toLowerCase().split("_").map(word => word ? word[0].toUpperCase() + word.slice(1) : "").join(" ");
}

function formatDate(value) {
    if (!value) return "-";
    return new Intl.DateTimeFormat(undefined, {
        year: "numeric", month: "short", day: "2-digit",
        hour: "2-digit", minute: "2-digit", second: "2-digit"
    }).format(new Date(value));
}

function formatTime(value) {
    if (!value) return "-";
    return new Intl.DateTimeFormat(undefined, {
        hour: "2-digit", minute: "2-digit", second: "2-digit"
    }).format(new Date(value));
}

function formatDuration(milliseconds) {
    const value = Number(milliseconds || 0);
    if (value < 1000) return `${Math.round(value)} ms`;
    if (value < 60000) return `${(value / 1000).toFixed(value < 10000 ? 1 : 0)} s`;
    const minutes = Math.floor(value / 60000);
    const seconds = Math.floor((value % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
}

function formatBytes(bytes) {
    const value = Number(bytes || 0);
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function formatNumber(value) {
    return new Intl.NumberFormat().format(Number(value || 0));
}

function showError(error) {
    showToast(error && error.message ? error.message : "Request failed");
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.hidden = false;
    window.setTimeout(() => {
        elements.toast.hidden = true;
    }, 4000);
}
