"use strict";

const monitoringState = {
    dashboard: null,
    health: null,
    refreshTimer: null
};

const monitoringElements = {};

document.addEventListener("DOMContentLoaded", () => {
    bindMonitoringElements();
    monitoringElements["refresh-button"].addEventListener(
        "click", () => refreshMonitoring(false));
    refreshMonitoring(false);
    monitoringState.refreshTimer = window.setInterval(
        () => refreshMonitoring(true), 15000);
});

function bindMonitoringElements() {
    [
        "health-dot", "health-label", "monitoring-path", "refresh-button",
        "monitoring-window", "monitoring-loaded-at", "monitoring-diagnostic-banner",
        "monitoring-metrics", "activity-chart", "activity-empty",
        "status-distribution", "status-list", "runtime-facts", "operation-count",
        "operation-table-body", "operation-empty", "transition-table-body",
        "transition-empty", "source-list", "source-empty", "quality-content",
        "quality-empty", "toast"
    ].forEach(id => {
        monitoringElements[id] = document.getElementById(id);
    });
}

async function refreshMonitoring(silent) {
    const button = monitoringElements["refresh-button"];
    button.disabled = true;
    try {
        const responses = await Promise.all([
            fetchMonitoringJson("/api/health"),
            fetchMonitoringJson("/api/monitoring")
        ]);
        monitoringState.health = responses[0];
        monitoringState.dashboard = responses[1];
        renderMonitoringHealth(responses[0]);
        renderMonitoringDashboard(responses[1]);
    } catch (error) {
        setMonitoringHealth("DOWN");
        if (!silent) {
            showMonitoringToast(error.message || "Monitoring refresh failed");
        }
    } finally {
        button.disabled = false;
    }
}

function renderMonitoringHealth(health) {
    setMonitoringHealth(health.status || "DOWN");
    const diagnostics = health.diagnostics || {};
    const path = diagnostics.traceFile || "Observation source";
    monitoringElements["monitoring-path"].textContent = path;
    monitoringElements["monitoring-path"].title = path;
}

function setMonitoringHealth(status) {
    const selected = String(status || "DOWN").toUpperCase();
    monitoringElements["health-label"].textContent = selected;
    monitoringElements["health-dot"].className = "health-dot "
        + (selected === "UP" ? "health-up"
            : selected === "DEGRADED" ? "health-degraded" : "health-down");
}

function renderMonitoringDashboard(dashboard) {
    const overview = dashboard.overview || {};
    const diagnostics = dashboard.traceDiagnostics || {};
    monitoringElements["monitoring-window"].textContent = overview.windowStart
        ? formatMonitoringDate(overview.windowStart) + " - "
            + formatMonitoringDate(overview.windowEnd)
        : "No observation window";
    monitoringElements["monitoring-loaded-at"].textContent = diagnostics.loadedAt
        ? "Loaded " + formatMonitoringDate(diagnostics.loadedAt) : "Not loaded";

    renderMonitoringDiagnostics(dashboard);
    renderMonitoringMetrics(overview);
    renderMonitoringActivity(dashboard.activity || []);
    renderMonitoringStatuses(dashboard.statuses || [], overview);
    renderMonitoringOperations(dashboard.operations || []);
    renderMonitoringTransitions(dashboard.transitions || []);
    renderMonitoringSources(dashboard.sources || []);
    renderMonitoringQuality(dashboard.evaluation || {});
}

function renderMonitoringDiagnostics(dashboard) {
    const trace = dashboard.traceDiagnostics || {};
    const evaluation = dashboard.evaluationDiagnostics || {};
    const notices = [];
    if (trace.traceFileExists === false) {
        notices.push("Observation file is not present.");
    }
    if (numeric(trace.malformedLineCount) > 0) {
        notices.push(numeric(trace.malformedLineCount)
            + " malformed observation lines skipped.");
    }
    if (numeric(trace.truncatedEventCount) > 0) {
        notices.push(numeric(trace.truncatedEventCount)
            + " older observation events omitted by the read limit.");
    }
    if (numeric(evaluation.malformedCount) > 0) {
        notices.push(numeric(evaluation.malformedCount)
            + " malformed evaluation records skipped.");
    }
    const banner = monitoringElements["monitoring-diagnostic-banner"];
    banner.hidden = notices.length === 0;
    banner.textContent = notices.join(" ");
}

function renderMonitoringMetrics(overview) {
    const metrics = [
        ["Traces", formatMonitoringNumber(overview.traceCount)],
        ["Completed", formatMonitoringNumber(overview.completed)],
        ["Failed", formatMonitoringNumber(overview.failed)],
        ["Waiting", formatMonitoringNumber(overview.waiting)],
        ["Average terminal", formatMonitoringDuration(overview.averageTraceDurationMillis)],
        ["Model calls", formatMonitoringNumber(overview.modelCalls)],
        ["Tool failures", formatMonitoringNumber(overview.toolFailures)
            + " / " + formatMonitoringNumber(overview.toolCalls)],
        ["Tokens", formatMonitoringCompact(
            numeric(overview.inputTokens) + numeric(overview.outputTokens))]
    ];
    const container = monitoringElements["monitoring-metrics"];
    container.replaceChildren();
    metrics.forEach(metric => {
        const item = monitoringNode("div", "metric monitoring-metric");
        item.append(
            monitoringText("span", "metric-label", metric[0]),
            monitoringText("strong", "metric-value", metric[1])
        );
        container.appendChild(item);
    });
}

function renderMonitoringActivity(activity) {
    const chart = monitoringElements["activity-chart"];
    chart.replaceChildren();
    monitoringElements["activity-empty"].hidden = activity.length !== 0;
    chart.hidden = activity.length === 0;
    if (!activity.length) {
        return;
    }
    let maximum = 1;
    activity.forEach(bucket => {
        maximum = Math.max(
            maximum,
            numeric(bucket.traces),
            numeric(bucket.modelCalls),
            numeric(bucket.toolCalls));
    });
    activity.forEach((bucket, index) => {
        const column = monitoringNode("div", "activity-column");
        const bars = monitoringNode("div", "activity-bars");
        bars.append(
            activityBar("trace", bucket.traces, maximum),
            activityBar("model", bucket.modelCalls, maximum),
            activityBar("tool", bucket.toolCalls, maximum)
        );
        const label = monitoringText(
            "span",
            "activity-time",
            index === 0 || index === activity.length - 1
                ? formatMonitoringTime(bucket.startedAt) : ""
        );
        column.title = formatMonitoringDate(bucket.startedAt)
            + ": " + numeric(bucket.traces) + " traces, "
            + numeric(bucket.modelCalls) + " model, "
            + numeric(bucket.toolCalls) + " Tool";
        column.append(bars, label);
        chart.appendChild(column);
    });
}

function activityBar(kind, value, maximum) {
    const level = numeric(value) === 0
        ? 0 : Math.max(1, Math.ceil(numeric(value) / maximum * 10));
    const bar = monitoringNode("span", "activity-bar activity-" + kind + " level-" + level);
    bar.setAttribute("aria-label", kind + " " + numeric(value));
    return bar;
}

function renderMonitoringStatuses(statuses, overview) {
    const distribution = monitoringElements["status-distribution"];
    const list = monitoringElements["status-list"];
    distribution.replaceChildren();
    list.replaceChildren();
    const populated = statuses.filter(status => numeric(status.count) > 0);
    const cells = 20;
    for (let index = 0; index < cells; index += 1) {
        const position = (index + 0.5) / cells;
        let cumulative = 0;
        let selected = populated.length ? populated[populated.length - 1] : {status: "UNKNOWN"};
        for (const status of populated) {
            cumulative += numeric(status.ratio);
            if (position <= cumulative) {
                selected = status;
                break;
            }
        }
        const cell = monitoringNode(
            "span", "status-segment status-" + String(selected.status).toLowerCase());
        cell.title = selected.status || "UNKNOWN";
        distribution.appendChild(cell);
    }
    statuses.forEach(status => {
        if (numeric(status.count) === 0) {
            return;
        }
        const row = monitoringNode("div", "status-row");
        const label = monitoringNode("span", "status-row-label");
        label.append(
            monitoringNode("i", "status-dot status-" + String(status.status).toLowerCase()),
            monitoringText("span", "", titleCase(status.status))
        );
        row.append(
            label,
            monitoringText("strong", "", formatMonitoringNumber(status.count)),
            monitoringText("span", "status-ratio", formatMonitoringPercent(status.ratio))
        );
        list.appendChild(row);
    });

    const facts = [
        ["Actions", overview.actions],
        ["Approvals", overview.approvalsRequested],
        ["Waits", overview.waits],
        ["Timeouts", overview.timeouts],
        ["Input tokens", formatMonitoringCompact(overview.inputTokens)],
        ["Output tokens", formatMonitoringCompact(overview.outputTokens)]
    ];
    const factList = monitoringElements["runtime-facts"];
    factList.replaceChildren();
    facts.forEach(fact => {
        factList.append(
            monitoringText("dt", "", fact[0]),
            monitoringText("dd", "", typeof fact[1] === "string"
                ? fact[1] : formatMonitoringNumber(fact[1]))
        );
    });
}

function renderMonitoringOperations(operations) {
    const body = monitoringElements["operation-table-body"];
    body.replaceChildren();
    monitoringElements["operation-count"].textContent = operations.length + " operations";
    monitoringElements["operation-empty"].hidden = operations.length !== 0;
    operations.forEach(operation => {
        const row = document.createElement("tr");
        const categoryCell = document.createElement("td");
        categoryCell.appendChild(monitoringText(
            "span",
            "operation-category category-" + String(operation.category).toLowerCase(),
            operation.category
        ));
        row.append(
            categoryCell,
            monitoringTableCell(operation.name, "operation-name"),
            monitoringTableCell(formatMonitoringNumber(operation.count)),
            monitoringTableCell(formatMonitoringNumber(operation.completed)),
            monitoringTableCell(formatMonitoringNumber(operation.failures),
                numeric(operation.failures) > 0 ? "value-danger" : ""),
            monitoringTableCell(formatMonitoringPercent(operation.failureRate),
                numeric(operation.failures) > 0 ? "value-danger" : ""),
            monitoringTableCell(formatMonitoringDuration(operation.averageDurationMillis))
        );
        body.appendChild(row);
    });
}

function renderMonitoringTransitions(transitions) {
    const body = monitoringElements["transition-table-body"];
    body.replaceChildren();
    monitoringElements["transition-empty"].hidden = transitions.length !== 0;
    transitions.forEach(transition => {
        const row = document.createElement("tr");
        row.append(
            monitoringTableCell(transition.stepId, "operation-name"),
            monitoringTableCell(transition.outcome),
            monitoringTableCell(transition.targetStepId),
            monitoringTableCell(formatMonitoringNumber(transition.count)),
            monitoringTableCell(formatMonitoringPercent(transition.ratioForStep))
        );
        body.appendChild(row);
    });
}

function renderMonitoringSources(sources) {
    const container = monitoringElements["source-list"];
    container.replaceChildren();
    monitoringElements["source-empty"].hidden = sources.length !== 0;
    let maximum = 1;
    sources.forEach(source => {
        maximum = Math.max(maximum, numeric(source.eventCount));
    });
    sources.forEach(source => {
        const row = monitoringNode("div", "source-monitor-row");
        const heading = monitoringNode("div", "source-monitor-heading");
        heading.append(
            monitoringText("strong", "", source.source),
            monitoringText("span", "", formatMonitoringNumber(source.eventCount)
                + " events / " + formatMonitoringNumber(source.traceCount) + " traces")
        );
        const progress = document.createElement("progress");
        progress.max = maximum;
        progress.value = numeric(source.eventCount);
        progress.setAttribute("aria-label", source.source + " event volume");
        row.append(heading, progress);
        container.appendChild(row);
    });
}

function renderMonitoringQuality(quality) {
    const content = monitoringElements["quality-content"];
    const empty = monitoringElements["quality-empty"];
    content.replaceChildren();
    const hasQuality = numeric(quality.experimentCount) > 0;
    content.hidden = !hasQuality;
    empty.hidden = hasQuality;
    if (!hasQuality) {
        return;
    }
    const values = [
        ["Experiments", formatMonitoringNumber(quality.experimentCount)],
        ["All-case pass rate", formatMonitoringPercent(quality.passRate)],
        ["Latest pass rate", formatMonitoringPercent(quality.latestPassRate)],
        ["Regressions", formatMonitoringNumber(quality.regressedExamples)],
        ["Improvements", formatMonitoringNumber(quality.improvedExamples)]
    ];
    values.forEach(value => {
        const metric = monitoringNode("div", "quality-metric");
        metric.append(
            monitoringText("span", "", value[0]),
            monitoringText("strong", value[0] === "Regressions"
                && numeric(quality.regressedExamples) > 0 ? "value-danger" : "", value[1])
        );
        content.appendChild(metric);
    });
    const candidate = monitoringNode("div", "quality-candidate");
    candidate.append(
        monitoringText("span", "", "Latest candidate"),
        monitoringText("strong", "", (quality.latestCandidateId || "Unknown")
            + " / " + (quality.latestCandidateVersion || "Unknown")),
        monitoringText("small", "", quality.latestExperimentId || "")
    );
    content.appendChild(candidate);
}

async function fetchMonitoringJson(url) {
    const response = await window.fetch(url, {headers: {Accept: "application/json"}});
    if (!response.ok) {
        let message = "Request failed (" + response.status + ")";
        try {
            const body = await response.json();
            message = body.error || message;
        } catch (ignored) {
            // Keep the HTTP status fallback.
        }
        throw new Error(message);
    }
    return response.json();
}

function monitoringTableCell(value, className) {
    return monitoringText("td", className || "", value == null ? "" : String(value));
}

function monitoringNode(tag, className) {
    const element = document.createElement(tag);
    if (className) {
        element.className = className;
    }
    return element;
}

function monitoringText(tag, className, value) {
    const element = monitoringNode(tag, className);
    element.textContent = value == null ? "" : String(value);
    return element;
}

function numeric(value) {
    const selected = Number(value);
    return Number.isFinite(selected) ? selected : 0;
}

function formatMonitoringNumber(value) {
    return numeric(value).toLocaleString("en-US");
}

function formatMonitoringCompact(value) {
    return new Intl.NumberFormat("en-US", {
        notation: "compact",
        maximumFractionDigits: 1
    }).format(numeric(value));
}

function formatMonitoringPercent(value) {
    return (numeric(value) * 100).toFixed(1) + "%";
}

function formatMonitoringDuration(value) {
    const milliseconds = numeric(value);
    if (milliseconds < 1000) {
        return Math.round(milliseconds) + " ms";
    }
    if (milliseconds < 60000) {
        return (milliseconds / 1000).toFixed(milliseconds < 10000 ? 1 : 0) + " s";
    }
    return (milliseconds / 60000).toFixed(1) + " min";
}

function formatMonitoringDate(value) {
    if (!value) {
        return "Unknown";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function formatMonitoringTime(value) {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "" : date.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit"
    });
}

function titleCase(value) {
    const selected = String(value || "").toLowerCase();
    return selected ? selected.charAt(0).toUpperCase() + selected.slice(1) : "Unknown";
}

function showMonitoringToast(message) {
    const toast = monitoringElements.toast;
    toast.textContent = message;
    toast.hidden = false;
    window.setTimeout(() => {
        toast.hidden = true;
    }, 3500);
}
