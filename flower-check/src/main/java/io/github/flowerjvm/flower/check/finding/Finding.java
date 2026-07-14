package io.github.flowerjvm.flower.check.finding;

import io.github.flowerjvm.flower.check.rule.Severity;

import java.util.Objects;

/**
 * One reported violation.
 *
 * <p>Every finding must explain itself with three fields: {@code what} was
 * found, {@code why} it is risky, and the {@code fix} (what to do instead).
 * This is a hard product requirement — see {@code docs/01-architecture.md}.
 *
 * <p>Immutable. No parser or AST type is allowed in this package, so findings
 * stay decoupled from how source was analyzed.
 */
public final class Finding {

    private final String ruleId;
    private final Severity severity;
    private final String file;
    private final int line;
    private final int column;
    private final String what;
    private final String why;
    private final String fix;

    private Finding(Builder b) {
        this.ruleId = Objects.requireNonNull(b.ruleId, "ruleId");
        this.severity = Objects.requireNonNull(b.severity, "severity");
        this.file = Objects.requireNonNull(b.file, "file");
        this.line = b.line;
        this.column = b.column;
        this.what = Objects.requireNonNull(b.what, "what");
        this.why = Objects.requireNonNull(b.why, "why");
        this.fix = Objects.requireNonNull(b.fix, "fix");
    }

    public String ruleId() {
        return ruleId;
    }

    public Severity severity() {
        return severity;
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

    public String what() {
        return what;
    }

    public String why() {
        return why;
    }

    public String fix() {
        return fix;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder. A {@link Finding} is invalid without what/why/fix. */
    public static final class Builder {
        private String ruleId;
        private Severity severity;
        private String file;
        private int line = 1;
        private int column = 0;
        private String what;
        private String why;
        private String fix;

        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder file(String file) {
            this.file = file;
            return this;
        }

        public Builder line(int line) {
            this.line = line;
            return this;
        }

        public Builder column(int column) {
            this.column = column;
            return this;
        }

        public Builder what(String what) {
            this.what = what;
            return this;
        }

        public Builder why(String why) {
            this.why = why;
            return this;
        }

        public Builder fix(String fix) {
            this.fix = fix;
            return this;
        }

        public Finding build() {
            return new Finding(this);
        }
    }

    @Override
    public String toString() {
        return ruleId + " " + severity + " " + file + ":" + line;
    }
}
