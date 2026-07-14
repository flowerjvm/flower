package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.finding.Finding;
import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.parse.SourceUnit;
import io.github.flowerjvm.flower.check.rule.AbstractRule;
import io.github.flowerjvm.flower.check.rule.RuleContext;
import io.github.flowerjvm.flower.check.rule.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FLOWER-CHECK-001 - No blocking on the Worker thread.
 *
 * <p>Primary detection is AST-backed via {@link AnalysisFact#BLOCKING_CALL}.
 * The text scanner remains only as a conservative fallback for files that could
 * not be parsed.
 */
public final class BlockingCallRule extends AbstractRule {

    private static final String NEEDLE = "Thread.sleep(";
    private static final Pattern CLASS_DECLARATION =
            Pattern.compile("\\bclass\\s+\\w+\\b[^\\{;]*\\bextends\\s+([^\\s\\{]+)");
    private static final Pattern LIFECYCLE_METHOD =
            Pattern.compile("\\b(onEnter|onTick|onExit|onReset|onResume)\\s*\\(");

    public BlockingCallRule() {
        super("FLOWER-CHECK-001", Severity.ERROR, "No blocking on the Worker thread");
    }

    @Override
    public List<Finding> apply(SourceUnit unit, RuleContext ctx) {
        if (unit.parsed()) {
            return astFindings(unit, ctx);
        }
        return textFallbackFindings(unit, ctx);
    }

    private List<Finding> astFindings(SourceUnit unit, RuleContext ctx) {
        List<Finding> findings = new ArrayList<>();
        for (AnalysisFact fact : ctx.projectModel().facts(AnalysisFact.BLOCKING_CALL)) {
            if (!unit.file().relativePath().equals(fact.file())) {
                continue;
            }
            findings.add(blockingFinding(ctx, fact.file(), fact.line(), fact.column(), fact.subject()));
        }
        return findings;
    }

    private List<Finding> textFallbackFindings(SourceUnit unit, RuleContext ctx) {
        List<Finding> findings = new ArrayList<>();
        List<String> lines = unit.file().lines();
        ScanState state = new ScanState(ctx);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String code = stripCommentsAndLiterals(line);
            boolean inLifecycleMethod = state.beforeLine(code);
            int column = code.indexOf(NEEDLE);
            if (inLifecycleMethod && column >= 0) {
                findings.add(blockingFinding(
                        ctx,
                        unit.file().relativePath(),
                        i + 1,
                        column + 1,
                        "Thread.sleep(...)"));
            }
            state.afterLine(code);
        }
        return findings;
    }

    private Finding blockingFinding(RuleContext ctx, String file, int line, int column, String call) {
        return finding(ctx, file, line)
                .column(column)
                .what(call + " blocks the calling thread; inside a Step lifecycle method it freezes the Worker tick.")
                .why("One Worker ticks every Flow it owns on a single thread. A blocking call stalls all "
                        + "other Flows on that Worker until it returns. Flower relies on quick, repeatable ticks.")
                .fix("Start the wait in onEnter (ctx.startTimeout / ctx.subscribe) and return "
                        + "StepResult.stay() until a signal or ctx.timedOut() resolves it.")
                .build();
    }

    private static String stripCommentsAndLiterals(String line) {
        StringBuilder out = new StringBuilder(line.length());
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (!inString && !inChar && c == '/' && next == '/') {
                while (i < line.length()) {
                    out.append(' ');
                    i++;
                }
                break;
            }
            if (!inChar && c == '"' && !escaped) {
                inString = !inString;
                out.append(' ');
            } else if (!inString && c == '\'' && !escaped) {
                inChar = !inChar;
                out.append(' ');
            } else {
                out.append(inString || inChar ? ' ' : c);
            }
            escaped = (inString || inChar) && c == '\\' && !escaped;
            if (c != '\\') {
                escaped = false;
            }
        }
        return out.toString();
    }

    private static boolean isStepClassDeclaration(String code, RuleContext ctx) {
        Matcher matcher = CLASS_DECLARATION.matcher(code);
        if (!matcher.find()) {
            return false;
        }
        String extendedType = normalizeTypeName(matcher.group(1));
        if ("Step".equals(extendedType) || "DurableStep".equals(extendedType)) {
            return true;
        }
        for (String configured : ctx.config().stepBaseClasses()) {
            String configuredType = normalizeTypeName(configured);
            if (configuredType.equals(extendedType) || configured.equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTypeName(String typeName) {
        String normalized = typeName;
        int generic = normalized.indexOf('<');
        if (generic >= 0) {
            normalized = normalized.substring(0, generic);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.trim();
    }

    private static MethodDeclaration lifecycleMethodDeclaration(String code) {
        Matcher matcher = LIFECYCLE_METHOD.matcher(code);
        while (matcher.find()) {
            int idx = matcher.start(1);
            int immediatePrevious = idx - 1;
            if (immediatePrevious >= 0 && Character.isJavaIdentifierPart(code.charAt(immediatePrevious))) {
                continue;
            }
            int previous = previousNonWhitespace(code, idx - 1);
            if (previous >= 0 && code.charAt(previous) == '.') {
                continue;
            }
            int brace = code.indexOf('{', idx);
            int semicolon = code.indexOf(';', idx);
            if (semicolon >= 0 && (brace < 0 || semicolon < brace)) {
                continue;
            }
            return new MethodDeclaration(brace >= 0);
        }
        return null;
    }

    private static int previousNonWhitespace(String text, int start) {
        for (int i = start; i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int braceDelta(String code) {
        int delta = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                delta++;
            } else if (c == '}') {
                delta--;
            }
        }
        return delta;
    }

    private static final class MethodDeclaration {
        private final boolean opensOnThisLine;

        private MethodDeclaration(boolean opensOnThisLine) {
            this.opensOnThisLine = opensOnThisLine;
        }
    }

    private static final class ClassFrame {
        private final int bodyDepth;
        private final boolean stepClass;

        private ClassFrame(int bodyDepth, boolean stepClass) {
            this.bodyDepth = bodyDepth;
            this.stepClass = stepClass;
        }
    }

    private static final class ScanState {
        private final RuleContext ctx;
        private final List<ClassFrame> classes = new ArrayList<>();
        private int braceDepth;
        private int lifecycleMethodDepth = -1;
        private Boolean pendingClassStep;
        private boolean pendingLifecycleMethod;
        private boolean methodStartsThisLine;

        private ScanState(RuleContext ctx) {
            this.ctx = ctx;
        }

        private boolean beforeLine(String code) {
            methodStartsThisLine = false;
            boolean topStepClass = isInsideStepClass();
            if (topStepClass && lifecycleMethodDepth < 0) {
                if (pendingLifecycleMethod && code.indexOf('{') >= 0) {
                    methodStartsThisLine = true;
                    pendingLifecycleMethod = false;
                } else if (!pendingLifecycleMethod) {
                    MethodDeclaration method = lifecycleMethodDeclaration(code);
                    if (method != null) {
                        methodStartsThisLine = method.opensOnThisLine;
                        pendingLifecycleMethod = !method.opensOnThisLine;
                    }
                }
            }
            return (topStepClass && lifecycleMethodDepth >= 0) || methodStartsThisLine;
        }

        private void afterLine(String code) {
            Boolean classToPush = classOpeningOnThisLine(code);
            int previousDepth = braceDepth;
            int newDepth = braceDepth + braceDelta(code);

            if (classToPush != null && newDepth > previousDepth) {
                classes.add(new ClassFrame(newDepth, classToPush));
            }

            if (methodStartsThisLine && lifecycleMethodDepth < 0 && newDepth > previousDepth) {
                lifecycleMethodDepth = newDepth;
            } else if (pendingLifecycleMethod && isInsideStepClass()
                    && code.indexOf('{') >= 0 && newDepth > previousDepth) {
                lifecycleMethodDepth = newDepth;
                pendingLifecycleMethod = false;
            }

            if (lifecycleMethodDepth >= 0 && newDepth < lifecycleMethodDepth) {
                lifecycleMethodDepth = -1;
            }

            while (!classes.isEmpty() && newDepth < classes.get(classes.size() - 1).bodyDepth) {
                classes.remove(classes.size() - 1);
            }
            braceDepth = newDepth;
        }

        private Boolean classOpeningOnThisLine(String code) {
            if (pendingClassStep != null && code.indexOf('{') >= 0) {
                Boolean stepClass = pendingClassStep;
                pendingClassStep = null;
                return stepClass;
            }
            Matcher classMatcher = CLASS_DECLARATION.matcher(code);
            if (!classMatcher.find()) {
                return null;
            }
            boolean stepClass = isStepClassDeclaration(code, ctx);
            if (code.indexOf('{', classMatcher.start()) >= 0) {
                return stepClass;
            }
            pendingClassStep = stepClass;
            return null;
        }

        private boolean isInsideStepClass() {
            return !classes.isEmpty() && classes.get(classes.size() - 1).stepClass;
        }
    }
}
