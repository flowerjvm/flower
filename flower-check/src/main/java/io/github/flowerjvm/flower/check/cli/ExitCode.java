package io.github.flowerjvm.flower.check.cli;

/**
 * Process exit codes (see {@code docs/01-architecture.md} CLI And Exit Codes).
 *
 * <pre>
 * 0  no findings at or above failOn
 * 1  findings at or above failOn (build should fail)
 * 2  usage error (bad arguments / unreadable path)
 * </pre>
 */
public final class ExitCode {

    public static final int OK = 0;
    public static final int FINDINGS = 1;
    public static final int USAGE = 2;

    private ExitCode() {
    }
}
