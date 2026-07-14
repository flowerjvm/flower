package io.github.flowerjvm.flower.check.model;

import java.util.Objects;

/**
 * Parser-neutral fact gathered in pass 1 and consumed by a rule in pass 2.
 *
 * <p>The fact kind is deliberately semantic rather than parser-specific. This
 * lets rules stay free of JavaParser types while still using AST-derived
 * structure.
 */
public final class AnalysisFact {

    public static final String BLOCKING_CALL = "blocking-call";
    public static final String PROVIDER_CALL = "provider-call";
    public static final String FLOW_DRIVE_CALL = "flow-drive-call";
    public static final String MISSING_TIMEOUT = "missing-timeout";
    public static final String DURABLE_STEP_MISSING_RECOVERY = "durable-step-missing-recovery";
    public static final String GOTO_UNKNOWN_TARGET = "goto-unknown-target";
    public static final String CALLBACK_CONTROL = "callback-control";
    public static final String RUNTIME_OWNERSHIP = "runtime-ownership";
    public static final String RAW_SUBSCRIPTION = "raw-subscription";
    public static final String DUPLICATE_STEP_ID = "duplicate-step-id";
    public static final String EXECUTION_CONTEXT_BUSINESS_USE = "execution-context-business-use";
    public static final String SHARED_STEP_INSTANCE = "shared-step-instance";
    public static final String UNAPPROVED_RECURRING_SCHEDULER = "unapproved-recurring-scheduler";
    public static final String AGENT_WRITE_BYPASS = "agent-write-bypass";
    public static final String AGENT_MISSING_AUDIT = "agent-missing-audit";
    public static final String APPROVAL_DIRECT_EXECUTION = "approval-direct-execution";

    private final String kind;
    private final String file;
    private final int line;
    private final int column;
    private final String subject;
    private final String detail;

    public AnalysisFact(String kind, String file, int line, int column, String subject, String detail) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.file = Objects.requireNonNull(file, "file");
        this.line = line <= 0 ? 1 : line;
        this.column = column;
        this.subject = subject == null ? "" : subject;
        this.detail = detail == null ? "" : detail;
    }

    public String kind() {
        return kind;
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public String subject() {
        return subject;
    }

    public String detail() {
        return detail;
    }
}
