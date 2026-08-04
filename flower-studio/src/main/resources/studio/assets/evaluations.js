"use strict";

const evaluationState = {
    experiments: [],
    selectedExperimentId: null,
    detail: null,
    selectedCaseId: null,
    activeTab: "cases",
    refreshTimer: null
};

const evaluationElements = {};

document.addEventListener("DOMContentLoaded", () => {
    bindEvaluationElements();
    bindEvaluationEvents();
    refreshEvaluations(false);
    evaluationState.refreshTimer = window.setInterval(
        () => refreshEvaluations(true), 15000);
});

function bindEvaluationElements() {
    [
        "health-dot", "health-label", "evaluation-path", "refresh-button",
        "experiment-count", "evaluation-diagnostic-banner", "experiment-list",
        "experiment-list-empty", "evaluation-loaded-at", "feedback-total",
        "evaluation-welcome-state", "evaluation-detail-view", "evaluation-title",
        "evaluation-status", "evaluation-id", "evaluation-context", "candidate-version",
        "evaluation-metrics", "comparison-band", "cases-tab", "scores-tab",
        "feedback-tab", "case-table-body", "case-inspector", "case-title",
        "close-case-inspector", "case-score-list", "case-input", "case-expected",
        "case-actual", "score-summary-list", "feedback-list", "feedback-empty", "toast"
    ].forEach(id => {
        evaluationElements[id] = document.getElementById(id);
    });
    evaluationElements.tabButtons = Array.from(document.querySelectorAll(".tab-button"));
}

function bindEvaluationEvents() {
    evaluationElements["refresh-button"].addEventListener(
        "click", () => refreshEvaluations(false));
    evaluationElements.tabButtons.forEach(button => {
        button.addEventListener("click", () => selectEvaluationTab(button.dataset.tab));
    });
    evaluationElements["close-case-inspector"].addEventListener("click", () => {
        evaluationState.selectedCaseId = null;
        evaluationElements["case-inspector"].hidden = true;
        renderEvaluationCases();
    });
}

async function refreshEvaluations(silent) {
    try {
        const [health, list] = await Promise.all([
            fetchEvaluationJson("/api/health"),
            fetchEvaluationJson("/api/evaluations")
        ]);
        renderEvaluationHealth(health);
        evaluationState.experiments = list.experiments || [];
        renderEvaluationDiagnostics(list);
        renderExperimentList();

        if (!evaluationState.selectedExperimentId && evaluationState.experiments.length) {
            evaluationState.selectedExperimentId = evaluationState.experiments[0].experimentId;
        }
        if (evaluationState.selectedExperimentId) {
            const exists = evaluationState.experiments.some(
                item => item.experimentId === evaluationState.selectedExperimentId);
            if (!exists) {
                evaluationState.selectedExperimentId = evaluationState.experiments.length
                    ? evaluationState.experiments[0].experimentId : null;
            }
        }
        if (evaluationState.selectedExperimentId) {
            await loadEvaluationDetail(evaluationState.selectedExperimentId, true);
        } else {
            showEvaluationWelcome();
        }
    } catch (error) {
        setEvaluationHealth("DOWN");
        if (!silent) {
            showEvaluationToast(error.message || "Evaluation refresh failed");
        }
    }
}

function renderEvaluationHealth(health) {
    setEvaluationHealth(health.status || "DOWN");
    const diagnostics = health.evaluationDiagnostics || {};
    evaluationElements["evaluation-path"].textContent = diagnostics.file || "Evaluation source";
}

function setEvaluationHealth(status) {
    const selected = String(status || "DOWN").toUpperCase();
    evaluationElements["health-label"].textContent = selected;
    evaluationElements["health-dot"].className = "health-dot "
        + (selected === "UP" ? "health-up"
            : selected === "DEGRADED" ? "health-degraded" : "health-down");
}

function renderEvaluationDiagnostics(list) {
    const result = list.resultDiagnostics || {};
    const feedback = list.feedbackDiagnostics || {};
    evaluationElements["evaluation-loaded-at"].textContent = result.loadedAt
        ? "Loaded " + formatEvaluationDate(result.loadedAt) : "Not loaded";
    evaluationElements["feedback-total"].textContent = number(feedback.recordCount)
        + " feedback";
    const notices = [];
    if (!result.exists) {
        notices.push("Evaluation result file is not present.");
    }
    if (number(result.malformedCount) > 0) {
        notices.push(number(result.malformedCount) + " malformed result lines skipped.");
    }
    if (number(feedback.malformedCount) > 0) {
        notices.push(number(feedback.malformedCount) + " malformed feedback lines skipped.");
    }
    const banner = evaluationElements["evaluation-diagnostic-banner"];
    banner.hidden = notices.length === 0;
    banner.textContent = notices.join(" ");
}

function renderExperimentList() {
    const list = evaluationElements["experiment-list"];
    list.replaceChildren();
    evaluationElements["experiment-count"].textContent =
        String(evaluationState.experiments.length);
    evaluationElements["experiment-list-empty"].hidden =
        evaluationState.experiments.length !== 0;

    evaluationState.experiments.forEach(experiment => {
        const button = node("button", "experiment-item");
        button.type = "button";
        button.setAttribute("role", "option");
        button.setAttribute("aria-selected",
            experiment.experimentId === evaluationState.selectedExperimentId ? "true" : "false");
        if (experiment.experimentId === evaluationState.selectedExperimentId) {
            button.classList.add("is-selected");
        }

        const top = node("div", "experiment-item-top");
        top.appendChild(textNode("span", "experiment-item-name", experiment.name));
        top.appendChild(statusBadge(experimentStatus(experiment.summary)));
        const meta = node("div", "experiment-item-meta");
        meta.appendChild(textNode(
            "span", "", experiment.candidateId + " / " + experiment.candidateVersion));
        meta.appendChild(textNode(
            "span", "", experiment.datasetId + "@" + experiment.datasetVersion));
        const result = node("div", "experiment-result-line");
        result.appendChild(textNode(
            "strong", "", formatPercent(experiment.summary && experiment.summary.passRate)));
        result.appendChild(textNode(
            "span", "", number(experiment.summary && experiment.summary.passed) + " passed / "
            + number(experiment.summary && experiment.summary.total) + " cases"));
        result.appendChild(textNode(
            "span", "", formatEvaluationDate(experiment.completedAt)));
        button.append(top, meta, result);
        button.addEventListener("click", () => selectExperiment(experiment.experimentId));
        list.appendChild(button);
    });
}

async function selectExperiment(experimentId) {
    if (evaluationState.selectedExperimentId === experimentId && evaluationState.detail) {
        return;
    }
    evaluationState.selectedExperimentId = experimentId;
    evaluationState.selectedCaseId = null;
    renderExperimentList();
    await loadEvaluationDetail(experimentId, false);
}

async function loadEvaluationDetail(experimentId, silent) {
    try {
        evaluationState.detail = await fetchEvaluationJson(
            "/api/evaluations/" + encodeURIComponent(experimentId));
        renderExperimentList();
        renderEvaluationDetail();
    } catch (error) {
        if (!silent) {
            showEvaluationToast(error.message || "Could not load the experiment");
        }
    }
}

function renderEvaluationDetail() {
    const detail = evaluationState.detail;
    if (!detail || !detail.experiment) {
        showEvaluationWelcome();
        return;
    }
    const experiment = detail.experiment;
    evaluationElements["evaluation-welcome-state"].hidden = true;
    evaluationElements["evaluation-detail-view"].hidden = false;
    evaluationElements["evaluation-title"].textContent = experiment.name;
    evaluationElements["evaluation-id"].textContent = experiment.experimentId;
    const status = experimentStatus(experiment.summary);
    applyStatusBadge(evaluationElements["evaluation-status"], status);
    evaluationElements["candidate-version"].textContent =
        experiment.candidateId + " / " + experiment.candidateVersion;
    evaluationElements["evaluation-context"].replaceChildren(
        textNode("span", "", experiment.datasetName + " @ " + experiment.datasetVersion),
        textNode("span", "", formatEvaluationDate(experiment.startedAt)),
        textNode("span", "", formatDuration(experiment.summary && experiment.summary.durationMillis))
    );

    renderEvaluationMetrics(experiment.summary || {});
    renderEvaluationComparison(detail);
    renderEvaluationCases();
    renderEvaluationScores();
    renderEvaluationFeedback();
    selectEvaluationTab(evaluationState.activeTab);
}

function renderEvaluationMetrics(summary) {
    const values = [
        ["Pass rate", formatPercent(summary.passRate)],
        ["Mean score", formatPercent(summary.meanScore)],
        ["Cases", number(summary.total)],
        ["Errors", number(summary.errors)],
        ["Input tokens", formatInteger(summary.inputTokens)],
        ["Tool calls", formatInteger(summary.toolCalls)]
    ];
    const strip = evaluationElements["evaluation-metrics"];
    strip.replaceChildren();
    values.forEach(value => {
        const metric = node("div", "metric");
        metric.append(
            textNode("span", "metric-label", value[0]),
            textNode("span", "metric-value", String(value[1]))
        );
        strip.appendChild(metric);
    });
}

function renderEvaluationComparison(detail) {
    const band = evaluationElements["comparison-band"];
    band.replaceChildren();
    if (detail.comparisonStatus === "NOT_CONFIGURED") {
        band.hidden = true;
        return;
    }
    band.hidden = false;
    const heading = node("div", "comparison-heading");
    heading.appendChild(textNode("h3", "", "Baseline comparison"));
    heading.appendChild(textNode(
        "span", "status-badge status-unknown", detail.comparisonStatus.replaceAll("_", " ")));
    band.appendChild(heading);
    if (!detail.comparison) {
        band.appendChild(textNode(
            "div", "comparison-examples", "The configured baseline could not be compared."));
        return;
    }
    const comparison = detail.comparison;
    const stats = node("div", "comparison-stat-list");
    stats.append(
        comparisonStat("Pass rate delta", formatSignedPercent(comparison.passRateDelta), comparison.passRateDelta),
        comparisonStat("Mean score delta", formatSignedPercent(comparison.meanScoreDelta), comparison.meanScoreDelta),
        comparisonStat("Regressions", String((comparison.regressedExampleIds || []).length),
            -(comparison.regressedExampleIds || []).length),
        comparisonStat("Improvements", String((comparison.improvedExampleIds || []).length),
            (comparison.improvedExampleIds || []).length)
    );
    band.appendChild(stats);
    const details = [];
    if ((comparison.regressedExampleIds || []).length) {
        details.push("Regressed: " + comparison.regressedExampleIds.join(", "));
    }
    if ((comparison.improvedExampleIds || []).length) {
        details.push("Improved: " + comparison.improvedExampleIds.join(", "));
    }
    if (details.length) {
        band.appendChild(textNode("div", "comparison-examples", details.join(" / ")));
    }
}

function comparisonStat(label, value, delta) {
    const stat = node("div", "comparison-stat");
    const strong = textNode("strong", "", value);
    if (delta > 0) {
        strong.classList.add("delta-positive");
    } else if (delta < 0) {
        strong.classList.add("delta-negative");
    }
    stat.append(strong, document.createTextNode(label));
    return stat;
}

function renderEvaluationCases() {
    const body = evaluationElements["case-table-body"];
    body.replaceChildren();
    const cases = evaluationState.detail && evaluationState.detail.experiment
        ? evaluationState.detail.experiment.cases || [] : [];
    cases.forEach(selected => {
        const row = document.createElement("tr");
        if (selected.exampleId === evaluationState.selectedCaseId) {
            row.classList.add("is-selected");
        }
        const exampleCell = document.createElement("td");
        const button = textNode("button", "text-button case-link", selected.exampleId);
        button.type = "button";
        button.addEventListener("click", () => selectEvaluationCase(selected.exampleId));
        exampleCell.appendChild(button);
        const statusCell = document.createElement("td");
        statusCell.appendChild(statusBadge(selected.status));
        row.append(
            exampleCell,
            statusCell,
            textCell(formatPercent(meanCaseScore(selected))),
            textCell(formatInteger(metric(selected, "inputTokens") + metric(selected, "outputTokens"))),
            textCell(formatInteger(metric(selected, "toolCalls"))),
            textCell(selected.traceId || "-")
        );
        body.appendChild(row);
    });
    renderCaseInspector(cases.find(item => item.exampleId === evaluationState.selectedCaseId));
}

function selectEvaluationCase(exampleId) {
    evaluationState.selectedCaseId = exampleId;
    renderEvaluationCases();
}

function renderCaseInspector(selected) {
    const inspector = evaluationElements["case-inspector"];
    if (!selected) {
        inspector.hidden = true;
        return;
    }
    inspector.hidden = false;
    evaluationElements["case-title"].textContent = selected.exampleId;
    const scores = evaluationElements["case-score-list"];
    scores.replaceChildren();
    (selected.scores || []).forEach(score => {
        const item = node("div", "case-score");
        item.append(
            textNode("strong", scoreTone(score.verdict),
                score.evaluatorId + " / " + score.verdict),
            textNode("span", "", formatPercent(score.value) + " / " + (score.reason || "No reason"))
        );
        scores.appendChild(item);
    });
    evaluationElements["case-input"].textContent = prettyJson(selected.input);
    evaluationElements["case-expected"].textContent = prettyJson(selected.expected);
    evaluationElements["case-actual"].textContent = prettyJson(selected.actual);
}

function renderEvaluationScores() {
    const summaries = new Map();
    const cases = evaluationState.detail && evaluationState.detail.experiment
        ? evaluationState.detail.experiment.cases || [] : [];
    cases.forEach(selected => {
        (selected.scores || []).forEach(score => {
            const aggregate = summaries.get(score.evaluatorId)
                || {id: score.evaluatorId, sum: 0, count: 0, pass: 0, fail: 0, error: 0};
            if (score.verdict === "ERROR") {
                aggregate.error += 1;
            } else {
                aggregate.sum += number(score.value);
                aggregate.count += 1;
                if (score.verdict === "PASS") {
                    aggregate.pass += 1;
                } else {
                    aggregate.fail += 1;
                }
            }
            summaries.set(score.evaluatorId, aggregate);
        });
    });
    const list = evaluationElements["score-summary-list"];
    list.replaceChildren();
    summaries.forEach(summary => {
        const row = node("div", "score-row");
        row.append(
            textNode("strong", "", summary.id),
            textNode("span", "score-value", formatPercent(
                summary.count ? summary.sum / summary.count : 0)),
            textNode("span", "score-value", String(summary.pass)),
            textNode("span", "score-value", String(summary.fail)),
            textNode("span", "score-value", String(summary.error))
        );
        list.appendChild(row);
    });
}

function renderEvaluationFeedback() {
    const feedback = evaluationState.detail ? evaluationState.detail.feedback || [] : [];
    const list = evaluationElements["feedback-list"];
    list.replaceChildren();
    evaluationElements["feedback-empty"].hidden = feedback.length !== 0;
    feedback.forEach(selected => {
        const row = node("article", "feedback-row");
        const heading = node("div", "feedback-heading");
        heading.append(
            textNode("strong", ratingClass(selected.rating), selected.rating),
            textNode("span", "", selected.exampleId || "Experiment feedback"),
            textNode("time", "", formatEvaluationDate(selected.createdAt))
        );
        row.appendChild(heading);
        if (selected.comment) {
            row.appendChild(textNode("p", "", selected.comment));
        }
        if ((selected.labels || []).length) {
            row.appendChild(textNode(
                "div", "feedback-labels", "Labels: " + selected.labels.join(", ")));
        }
        if (selected.traceId) {
            row.appendChild(textNode("div", "feedback-meta", "Trace: " + selected.traceId));
        }
        list.appendChild(row);
    });
}

function selectEvaluationTab(tab) {
    evaluationState.activeTab = tab;
    evaluationElements.tabButtons.forEach(button => {
        const active = button.dataset.tab === tab;
        button.classList.toggle("is-active", active);
        button.setAttribute("aria-selected", active ? "true" : "false");
    });
    ["cases", "scores", "feedback"].forEach(name => {
        evaluationElements[name + "-tab"].hidden = name !== tab;
    });
}

function showEvaluationWelcome() {
    evaluationState.detail = null;
    evaluationElements["evaluation-welcome-state"].hidden = false;
    evaluationElements["evaluation-detail-view"].hidden = true;
}

function experimentStatus(summary) {
    if (!summary) {
        return "ERROR";
    }
    if (number(summary.errors) > 0) {
        return "ERROR";
    }
    return number(summary.failed) > 0 ? "FAIL" : "PASS";
}

function statusBadge(status) {
    const badge = node("span", "status-badge");
    applyStatusBadge(badge, status);
    return badge;
}

function applyStatusBadge(badge, status) {
    const selected = String(status || "ERROR").toUpperCase();
    badge.textContent = selected;
    badge.className = "status-badge "
        + (selected === "PASS" ? "status-completed" : "status-failed");
}

function scoreTone(verdict) {
    return verdict === "PASS" ? "delta-positive"
        : verdict === "FAIL" ? "delta-negative" : "";
}

function ratingClass(rating) {
    return "rating-" + String(rating || "partial").toLowerCase();
}

function meanCaseScore(selected) {
    const scores = (selected.scores || []).filter(score => score.verdict !== "ERROR");
    if (!scores.length) {
        return 0;
    }
    return scores.reduce((total, score) => total + number(score.value), 0) / scores.length;
}

function metric(selected, name) {
    return number(selected.metrics && selected.metrics[name]);
}

function formatPercent(value) {
    return (number(value) * 100).toFixed(1) + "%";
}

function formatSignedPercent(value) {
    const selected = number(value) * 100;
    return (selected > 0 ? "+" : "") + selected.toFixed(1) + "%";
}

function formatInteger(value) {
    return new Intl.NumberFormat("en-US", {maximumFractionDigits: 0}).format(number(value));
}

function formatDuration(value) {
    const milliseconds = number(value);
    if (milliseconds < 1000) {
        return milliseconds + " ms";
    }
    return (milliseconds / 1000).toFixed(1) + " s";
}

function formatEvaluationDate(value) {
    if (!value) {
        return "-";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function prettyJson(value) {
    return JSON.stringify(value || {}, null, 2);
}

function number(value) {
    const selected = Number(value);
    return Number.isFinite(selected) ? selected : 0;
}

function node(tag, className) {
    const selected = document.createElement(tag);
    if (className) {
        selected.className = className;
    }
    return selected;
}

function textNode(tag, className, value) {
    const selected = node(tag, className);
    selected.textContent = value == null ? "" : String(value);
    return selected;
}

function textCell(value) {
    return textNode("td", "", value);
}

async function fetchEvaluationJson(url) {
    const response = await fetch(url, {headers: {"Accept": "application/json"}});
    let body = null;
    try {
        body = await response.json();
    } catch (ignored) {
        body = null;
    }
    if (!response.ok) {
        throw new Error(body && body.error ? body.error : "Request failed: " + response.status);
    }
    return body;
}

function showEvaluationToast(message) {
    const toast = evaluationElements.toast;
    toast.textContent = message;
    toast.hidden = false;
    window.setTimeout(() => {
        toast.hidden = true;
    }, 4000);
}
