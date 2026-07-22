package io.github.flowerjvm.flower.check.model;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.flowerjvm.flower.check.config.FlowerCheckConfig;
import io.github.flowerjvm.flower.check.parse.SourceUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pass 1: walk every parsed unit once and build the {@link ProjectModel}.
 * Produces parser-neutral facts; rules turn those facts into findings.
 */
public final class ProjectModelBuilder {

    private static final Set<String> CORE_LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
            "onEnter", "onTick", "onExit", "onReset", "onResume"));
    private static final Set<String> EVENT_LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
            "onEnter", "onEvent", "onTimeout", "onRecover", "onExit"));
    private static final Set<String> LIFECYCLE_METHODS = new HashSet<>(Arrays.asList(
            "onEnter", "onTick", "onEvent", "onTimeout", "onRecover",
            "onExit", "onReset", "onResume"));
    private static final Set<String> FLOW_DRIVE_METHODS = new HashSet<>(Arrays.asList(
            "tick", "tickOnce", "start", "stop", "attach"));
    private static final Set<String> FLOW_RUNTIME_TYPES = new HashSet<>(Arrays.asList(
            "Flow", "Worker", "Engine", "EventWorker"));
    private static final Set<String> OWNED_RUNTIME_TYPES = new HashSet<>(Arrays.asList(
            "Worker", "Engine", "EventWorker"));
    private static final Set<String> TIMEOUT_METHODS = new HashSet<>(Arrays.asList(
            "startTimeout", "timedOut", "elapsedMillis"));
    private static final Set<String> SIGNAL_WAIT_METHODS = new HashSet<>(Arrays.asList(
            "subscribe", "hasSignal", "consumeSignal", "signalPayload"));
    private static final Set<String> EXECUTION_CONTEXT_BUSINESS_METHODS = new HashSet<>(Arrays.asList(
            "metadata", "role", "roles", "permission", "permissions", "policyDecision",
            "approvalState", "agentId", "actionId", "domainObject", "domainState"));
    private static final Set<String> AGENT_WRITE_METHODS = new HashSet<>(Arrays.asList(
            "save", "update", "delete", "insert", "execute", "dispatch", "post", "put",
            "send", "create", "write", "mutate"));
    private static final Set<String> BLOCKING_METHODS = new HashSet<>(Arrays.asList(
            "sleep", "wait", "join"));
    private static final Set<String> ASYNC_BOUNDARY_METHODS = new HashSet<>(Arrays.asList(
            "runAsync", "supplyAsync", "thenRunAsync"));
    private static final Set<String> GUARD_SIDE_EFFECT_METHODS = new HashSet<>(Arrays.asList(
            "save", "saveAll", "update", "delete", "deleteAll", "insert", "execute",
            "dispatch", "publish", "send", "submit", "schedule", "subscribe", "signal",
            "cancel", "startTimeout", "setStepNo", "runAsync", "supplyAsync",
            "thenRun", "thenRunAsync"));
    private static final Set<String> SCHEDULER_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "Scheduled", "Schedules", "EnableScheduling"));
    private static final Set<String> RECURRING_SCHEDULER_METHODS = new HashSet<>(Arrays.asList(
            "scheduleAtFixedRate", "scheduleWithFixedDelay",
            "addCronTask", "addFixedRateTask", "addFixedDelayTask"));
    private static final List<String> DEFAULT_PROVIDER_NAMES = Arrays.asList(
            "OpenAI", "Anthropic", "ChatClient", "LlmClient", "LLMClient", "AiClient", "AIClient");

    private final FlowerCheckConfig config;

    public ProjectModelBuilder(FlowerCheckConfig config) {
        this.config = config;
    }

    public ProjectModel build(List<SourceUnit> units) {
        List<UnitAst> asts = parsedAsts(units);
        List<ClassInfo> classes = collectClasses(asts);
        resolveStepTypes(classes);

        Map<String, ClassInfo> classesByName = classesByName(classes);
        List<StepType> stepTypes = toStepTypes(classes);
        List<FlowBuilderSite> flowBuilders = new ArrayList<>();
        List<AnalysisFact> facts = new ArrayList<>();
        Map<String, Set<String>> constantsByFile = new HashMap<>();

        for (UnitAst ast : asts) {
            Map<String, String> constants = collectStringConstants(ast.compilationUnit);
            constantsByFile.put(ast.file, new LinkedHashSet<>(constants.values()));
            analyzeStepClasses(ast, classesByName, facts);
            analyzeGuardClasses(ast, classesByName, facts);
            analyzeFlowFactories(ast, classesByName, constants, flowBuilders, facts);
            analyzeSharedStepInstances(ast, facts);
            analyzeSchedulerUsage(ast, facts);
            analyzeAgentFacts(ast, facts);
        }

        addGoToFacts(asts, constantsByFile, flowBuilders, facts);
        return new ProjectModel(stepTypes, flowBuilders, facts);
    }

    private List<UnitAst> parsedAsts(List<SourceUnit> units) {
        List<UnitAst> asts = new ArrayList<>();
        for (SourceUnit unit : units) {
            Optional<Object> ast = unit.ast();
            if (unit.parsed() && ast.isPresent() && ast.get() instanceof CompilationUnit) {
                asts.add(new UnitAst(unit.file().relativePath(), (CompilationUnit) ast.get()));
            }
        }
        return asts;
    }

    private List<ClassInfo> collectClasses(List<UnitAst> asts) {
        List<ClassInfo> classes = new ArrayList<>();
        for (UnitAst ast : asts) {
            for (ClassOrInterfaceDeclaration declaration
                    : ast.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                ClassInfo info = new ClassInfo(ast.file, declaration);
                for (ClassOrInterfaceType extended : declaration.getExtendedTypes()) {
                    info.extendedSimpleNames.add(simpleType(extended.getNameAsString()));
                }
                for (ClassOrInterfaceType implemented : declaration.getImplementedTypes()) {
                    info.implementedSimpleNames.add(simpleType(implemented.getNameAsString()));
                }
                classes.add(info);
            }
        }
        return classes;
    }

    private void resolveStepTypes(List<ClassInfo> classes) {
        Map<String, ClassInfo> byName = classesByName(classes);
        boolean changed;
        do {
            changed = false;
            for (ClassInfo info : classes) {
                boolean coreStep = info.coreStep || extendsKnownCoreStep(info, byName);
                boolean eventStep = info.eventStep || extendsKnownEventStep(info, byName);
                boolean durable = info.durable || extendsKnownDurableStep(info, byName);
                boolean guard = info.guard || implementsKnownGuard(info, byName);
                boolean step = coreStep || eventStep;
                if (step != info.step || coreStep != info.coreStep || eventStep != info.eventStep
                        || durable != info.durable || guard != info.guard) {
                    info.step = step;
                    info.coreStep = coreStep;
                    info.eventStep = eventStep;
                    info.durable = durable;
                    info.guard = guard;
                    changed = true;
                }
            }
        } while (changed);
    }

    private boolean extendsKnownCoreStep(ClassInfo info, Map<String, ClassInfo> byName) {
        for (String extended : info.extendedSimpleNames) {
            if ("Step".equals(extended) || "DurableStep".equals(extended) || isConfiguredStepBase(extended)) {
                return true;
            }
            ClassInfo parent = byName.get(extended);
            if (parent != null && parent.coreStep) {
                return true;
            }
        }
        return false;
    }

    private boolean extendsKnownEventStep(ClassInfo info, Map<String, ClassInfo> byName) {
        for (String extended : info.extendedSimpleNames) {
            if ("EventStep".equals(extended)) {
                return true;
            }
            ClassInfo parent = byName.get(extended);
            if (parent != null && parent.eventStep) {
                return true;
            }
        }
        return false;
    }

    private boolean implementsKnownGuard(ClassInfo info, Map<String, ClassInfo> byName) {
        if (info.implementedSimpleNames.contains("Guard")) {
            return true;
        }
        for (String implemented : info.implementedSimpleNames) {
            ClassInfo parent = byName.get(implemented);
            if (parent != null && parent.guard) {
                return true;
            }
        }
        for (String extended : info.extendedSimpleNames) {
            if ("Guard".equals(extended)) {
                return true;
            }
            ClassInfo parent = byName.get(extended);
            if (parent != null && parent.guard) {
                return true;
            }
        }
        return false;
    }

    private boolean extendsKnownDurableStep(ClassInfo info, Map<String, ClassInfo> byName) {
        for (String extended : info.extendedSimpleNames) {
            if ("DurableStep".equals(extended)) {
                return true;
            }
            ClassInfo parent = byName.get(extended);
            if (parent != null && parent.durable) {
                return true;
            }
        }
        return false;
    }

    private boolean isConfiguredStepBase(String typeName) {
        String simple = simpleType(typeName);
        for (String configured : config.stepBaseClasses()) {
            if (simpleType(configured).equals(simple) || configured.equals(typeName)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, ClassInfo> classesByName(List<ClassInfo> classes) {
        Map<String, ClassInfo> byName = new HashMap<>();
        for (ClassInfo info : classes) {
            byName.put(info.simpleName, info);
        }
        return byName;
    }

    private List<StepType> toStepTypes(List<ClassInfo> classes) {
        List<StepType> stepTypes = new ArrayList<>();
        for (ClassInfo info : classes) {
            if (!info.step) {
                continue;
            }
            Set<String> lifecycle = new LinkedHashSet<>();
            for (MethodDeclaration method : info.declaration.getMethods()) {
                if (lifecycleMethods(info).contains(method.getNameAsString())) {
                    lifecycle.add(method.getNameAsString());
                }
            }
            stepTypes.add(new StepType(
                    info.simpleName, info.file, info.durable, info.eventStep, lifecycle));
        }
        return stepTypes;
    }

    private Set<String> lifecycleMethods(ClassInfo info) {
        return info.eventStep ? EVENT_LIFECYCLE_METHODS : CORE_LIFECYCLE_METHODS;
    }

    private Map<String, String> collectStringConstants(CompilationUnit compilationUnit) {
        Map<String, String> constants = new HashMap<>();
        for (VariableDeclarator variable : compilationUnit.findAll(VariableDeclarator.class)) {
            Optional<Expression> initializer = variable.getInitializer();
            if (initializer.isPresent() && initializer.get().isStringLiteralExpr()) {
                constants.put(variable.getNameAsString(), initializer.get().asStringLiteralExpr().asString());
            }
        }
        return constants;
    }

    private void analyzeStepClasses(UnitAst ast, Map<String, ClassInfo> classesByName, List<AnalysisFact> facts) {
        for (ClassOrInterfaceDeclaration declaration
                : ast.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            ClassInfo info = classesByName.get(declaration.getNameAsString());
            if (info == null || !info.step) {
                continue;
            }
            analyzeBlockingCalls(info, facts);
            if (info.eventStep) {
                analyzeEventStepWait(info, facts);
            } else {
                analyzeStepWait(info, facts);
            }
            analyzeRuntimeOwnership(info, facts);
            for (MethodDeclaration method : declaration.getMethods()) {
                if (!lifecycleMethods(info).contains(method.getNameAsString())) {
                    continue;
                }
                analyzeProviderCalls(info, method, facts);
                analyzeFlowDriveCalls(info.file, info.simpleName, method, true, facts);
                analyzeSubscribeCallbacks(info, method, facts);
                analyzeRawSubscriptions(info, method, facts);
                analyzeExecutionContextUses(info.file, method, facts);
            }
            for (ConstructorDeclaration constructor : declaration.getConstructors()) {
                analyzeExecutionContextUses(info.file, constructor, facts);
            }
        }
    }

    private void analyzeGuardClasses(UnitAst ast,
                                     Map<String, ClassInfo> classesByName,
                                     List<AnalysisFact> facts) {
        for (ClassOrInterfaceDeclaration declaration
                : ast.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            ClassInfo info = classesByName.get(declaration.getNameAsString());
            if (info == null || !info.guard || declaration.isInterface()) {
                continue;
            }
            Set<MethodDeclaration> inScope = methodsAndPrivateHelpers(
                    info, Collections.singleton("check"));
            analyzeBlockingCalls(info, inScope, facts);
            Set<String> fieldNames = fieldNames(declaration);
            for (MethodDeclaration method : inScope) {
                analyzeProviderCalls(info, method, facts);
                analyzeGuardSideEffects(
                        info.file, info.simpleName, method, fieldNames, true, facts);
            }
        }
    }

    private Set<String> fieldNames(ClassOrInterfaceDeclaration declaration) {
        Set<String> names = new LinkedHashSet<>();
        for (FieldDeclaration field : declaration.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                names.add(variable.getNameAsString());
            }
        }
        return names;
    }

    private void analyzeGuardSideEffects(String file,
                                         String owner,
                                         Node body,
                                         Set<String> fieldNames,
                                         boolean allowOwnerFieldMutation,
                                         List<AnalysisFact> facts) {
        Set<String> emitted = new LinkedHashSet<>();
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (!GUARD_SIDE_EFFECT_METHODS.contains(name) && !looksLikeSetter(name)) {
                continue;
            }
            addGuardSideEffect(file, owner, call, call.toString(), emitted, facts);
        }
        for (AssignExpr assignment : body.findAll(AssignExpr.class)) {
            if (isNonLocalGuardTarget(
                    assignment.getTarget(), fieldNames, allowOwnerFieldMutation)) {
                addGuardSideEffect(
                        file, owner, assignment, assignment.toString(), emitted, facts);
            }
        }
        for (UnaryExpr unary : body.findAll(UnaryExpr.class)) {
            if (isIncrementOrDecrement(unary)
                    && isNonLocalGuardTarget(
                    unary.getExpression(), fieldNames, allowOwnerFieldMutation)) {
                addGuardSideEffect(file, owner, unary, unary.toString(), emitted, facts);
            }
        }
    }

    private boolean looksLikeSetter(String methodName) {
        return methodName.length() > 3
                && methodName.startsWith("set")
                && Character.isUpperCase(methodName.charAt(3));
    }

    private boolean isNonLocalGuardTarget(Expression target,
                                          Set<String> fieldNames,
                                          boolean allowOwnerFieldMutation) {
        if (target instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) target;
            return !allowOwnerFieldMutation || !field.getScope().isThisExpr();
        }
        return target instanceof NameExpr
                && fieldNames.contains(((NameExpr) target).getNameAsString())
                && !allowOwnerFieldMutation;
    }

    private boolean isIncrementOrDecrement(UnaryExpr unary) {
        UnaryExpr.Operator operator = unary.getOperator();
        return operator == UnaryExpr.Operator.PREFIX_INCREMENT
                || operator == UnaryExpr.Operator.PREFIX_DECREMENT
                || operator == UnaryExpr.Operator.POSTFIX_INCREMENT
                || operator == UnaryExpr.Operator.POSTFIX_DECREMENT;
    }

    private void addGuardSideEffect(String file,
                                    String owner,
                                    Node node,
                                    String operation,
                                    Set<String> emitted,
                                    List<AnalysisFact> facts) {
        String key = line(node) + ":" + column(node);
        if (!emitted.add(key)) {
            return;
        }
        facts.add(new AnalysisFact(
                AnalysisFact.GUARD_SIDE_EFFECT,
                file,
                line(node),
                column(node),
                owner,
                operation));
    }

    private void analyzeStepWait(ClassInfo info, List<AnalysisFact> facts) {
        boolean hasStay = false;
        boolean hasSignalWait = false;
        boolean hasTimeout = false;
        boolean finiteHint = hasFiniteWaitHint(info.simpleName);
        int line = line(info.declaration);

        for (MethodDeclaration method : info.declaration.getMethods()) {
            if (!CORE_LIFECYCLE_METHODS.contains(method.getNameAsString())) {
                continue;
            }
            String text = method.toString();
            finiteHint = finiteHint || hasFiniteWaitHint(text);
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                String name = call.getNameAsString();
                if (isStepResultCall(call, "stay")) {
                    hasStay = true;
                    line = line(call);
                }
                if (SIGNAL_WAIT_METHODS.contains(name)) {
                    hasSignalWait = true;
                }
                if (TIMEOUT_METHODS.contains(name)) {
                    hasTimeout = true;
                }
            }
        }

        if (hasStay && hasSignalWait && !hasTimeout && finiteHint) {
            facts.add(new AnalysisFact(
                    AnalysisFact.MISSING_TIMEOUT,
                    info.file,
                    line,
                    0,
                    info.simpleName,
                    "waits for a signal/event and returns StepResult.stay() without a timeout"));
        }
    }

    private void analyzeEventStepWait(ClassInfo info, List<AnalysisFact> facts) {
        for (MethodDeclaration method : info.declaration.getMethods()) {
            if (!EVENT_LIFECYCLE_METHODS.contains(method.getNameAsString())) {
                continue;
            }
            boolean finiteHint = hasFiniteWaitHint(info.simpleName) || hasFiniteWaitHint(method.toString());
            if (!finiteHint) {
                continue;
            }
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                if (!isResultCall(call, "EventStepResult", "await")) {
                    continue;
                }
                boolean externalWait = containsAwaitCondition(call, "event")
                        || containsAwaitCondition(call, "signal");
                boolean deadline = containsAwaitCondition(call, "deadlineIn");
                if (externalWait && !deadline) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.MISSING_EVENT_AWAIT_DEADLINE,
                            info.file,
                            line(call),
                            column(call),
                            info.simpleName,
                            "finite EventStep await has no AwaitCondition.deadlineIn(...)"));
                }
            }
        }
    }

    private boolean containsAwaitCondition(MethodCallExpr awaitCall, String conditionName) {
        for (Expression argument : awaitCall.getArguments()) {
            if (argument instanceof MethodCallExpr) {
                MethodCallExpr direct = (MethodCallExpr) argument;
                if (conditionName.equals(direct.getNameAsString())
                        && isScopeNamed(direct, "AwaitCondition")) {
                    return true;
                }
            }
            for (MethodCallExpr nested : argument.findAll(MethodCallExpr.class)) {
                if (conditionName.equals(nested.getNameAsString())
                        && isScopeNamed(nested, "AwaitCondition")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void analyzeBlockingCalls(ClassInfo info, List<AnalysisFact> facts) {
        Set<MethodDeclaration> inScope = methodsAndPrivateHelpers(info, lifecycleMethods(info));
        analyzeBlockingCalls(info, inScope, facts);
    }

    private void analyzeBlockingCalls(ClassInfo info,
                                      Set<MethodDeclaration> inScope,
                                      List<AnalysisFact> facts) {
        Set<String> emitted = new LinkedHashSet<>();
        for (MethodDeclaration method : inScope) {
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                String blocking = blockingCallName(call, method, info, inScope);
                if (blocking == null) {
                    continue;
                }
                String key = info.file + ":" + line(call) + ":" + column(call) + ":" + blocking;
                if (!emitted.add(key)) {
                    continue;
                }
                facts.add(new AnalysisFact(
                        AnalysisFact.BLOCKING_CALL,
                        info.file,
                        line(call),
                        column(call),
                        blocking,
                        "blocking call in " + info.simpleName + "." + method.getNameAsString()));
            }
            for (WhileStmt loop : method.findAll(WhileStmt.class)) {
                if (looksLikeBusyWait(loop) && !isInsideAsyncBoundary(loop)) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.BLOCKING_CALL,
                            info.file,
                            line(loop),
                            column(loop),
                            "busy wait loop",
                            "busy-wait loop in " + info.simpleName + "." + method.getNameAsString()));
                }
            }
            for (ForStmt loop : method.findAll(ForStmt.class)) {
                if (looksLikeBusyWait(loop) && !isInsideAsyncBoundary(loop)) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.BLOCKING_CALL,
                            info.file,
                            line(loop),
                            column(loop),
                            "busy wait loop",
                            "busy-wait loop in " + info.simpleName + "." + method.getNameAsString()));
                }
            }
        }
    }

    private void analyzeBlockingNode(String file,
                                     String owner,
                                     Node body,
                                     List<AnalysisFact> facts) {
        Set<String> emitted = new LinkedHashSet<>();
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            String blocking = blockingCallName(
                    call, null, null, Collections.<MethodDeclaration>emptySet());
            if (blocking == null) {
                continue;
            }
            String key = line(call) + ":" + column(call) + ":" + blocking;
            if (emitted.add(key)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.BLOCKING_CALL,
                        file,
                        line(call),
                        column(call),
                        blocking,
                        "blocking call in " + owner));
            }
        }
        for (WhileStmt loop : body.findAll(WhileStmt.class)) {
            if (looksLikeBusyWait(loop) && !isInsideAsyncBoundary(loop)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.BLOCKING_CALL,
                        file,
                        line(loop),
                        column(loop),
                        "busy wait loop",
                        "busy-wait loop in " + owner));
            }
        }
        for (ForStmt loop : body.findAll(ForStmt.class)) {
            if (looksLikeBusyWait(loop) && !isInsideAsyncBoundary(loop)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.BLOCKING_CALL,
                        file,
                        line(loop),
                        column(loop),
                        "busy wait loop",
                        "busy-wait loop in " + owner));
            }
        }
    }

    private Set<MethodDeclaration> methodsAndPrivateHelpers(ClassInfo info, Set<String> entryMethodNames) {
        Map<String, MethodDeclaration> privateMethods = new HashMap<>();
        Set<MethodDeclaration> inScope = new LinkedHashSet<>();
        for (MethodDeclaration method : info.declaration.getMethods()) {
            if (method.isPrivate()) {
                privateMethods.put(method.getNameAsString(), method);
            }
            if (entryMethodNames.contains(method.getNameAsString())) {
                inScope.add(method);
            }
        }
        List<MethodDeclaration> queue = new ArrayList<>(inScope);
        for (int i = 0; i < queue.size(); i++) {
            MethodDeclaration current = queue.get(i);
            for (MethodCallExpr call : current.findAll(MethodCallExpr.class)) {
                MethodDeclaration helper = privateMethods.get(call.getNameAsString());
                if (helper != null && isLocalHelperCall(call) && inScope.add(helper)) {
                    queue.add(helper);
                }
            }
        }
        return inScope;
    }

    private boolean isLocalHelperCall(MethodCallExpr call) {
        if (!call.getScope().isPresent()) {
            return true;
        }
        return "this".equals(call.getScope().get().toString());
    }

    private String blockingCallName(MethodCallExpr call,
                                    MethodDeclaration method,
                                    ClassInfo info,
                                    Set<MethodDeclaration> inScope) {
        if (isInsideAsyncBoundary(call)) {
            return null;
        }
        String name = call.getNameAsString();
        if (BLOCKING_METHODS.contains(name)) {
            if ("sleep".equals(name) && !isScopeNamed(call, "Thread")) {
                return null;
            }
            if ("join".equals(name) && completionAlreadyObserved(call, method, info, inScope)) {
                return null;
            }
            return callLabel(call);
        }
        if ("get".equals(name) && call.getArguments().isEmpty() && looksLikeFutureGet(call)) {
            if (completionAlreadyObserved(call, method, info, inScope)) {
                return null;
            }
            return callLabel(call);
        }
        if (looksLikeBlockingIo(call)) {
            return callLabel(call);
        }
        return null;
    }

    private boolean isInsideAsyncBoundary(Node node) {
        Optional<Node> parent = node.getParentNode();
        while (parent.isPresent()) {
            Node candidate = parent.get();
            if (candidate instanceof MethodCallExpr
                    && ASYNC_BOUNDARY_METHODS.contains(
                    ((MethodCallExpr) candidate).getNameAsString())) {
                return true;
            }
            parent = candidate.getParentNode();
        }
        return false;
    }

    private boolean completionAlreadyObserved(MethodCallExpr blockingCall,
                                              MethodDeclaration method,
                                              ClassInfo info,
                                              Set<MethodDeclaration> inScope) {
        if (!blockingCall.getScope().isPresent()) {
            return false;
        }
        Expression receiver = blockingCall.getScope().get();
        if (completionKnownAt(receiver, blockingCall)) {
            return true;
        }
        if (method == null || info == null) {
            return false;
        }

        String receiverName = expressionKey(receiver);
        int parameterIndex = -1;
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (receiverName.equals(method.getParameter(i).getNameAsString())
                    && looksLikeCompletionType(method.getParameter(i).getType().asString())) {
                parameterIndex = i;
                break;
            }
        }
        if (parameterIndex < 0 || !method.isPrivate()
                || hasAmbiguousOverload(info.declaration, method)) {
            return false;
        }
        if (method.getBody().isPresent()
                && writesTargetBefore(method.getBody().get(), blockingCall, receiverName)) {
            return false;
        }

        boolean foundCallSite = false;
        for (MethodDeclaration caller : inScope) {
            for (MethodCallExpr invocation : caller.findAll(MethodCallExpr.class)) {
                if (!method.getNameAsString().equals(invocation.getNameAsString())
                        || invocation.getArguments().size() != method.getParameters().size()
                        || !isLocalHelperCall(invocation)) {
                    continue;
                }
                foundCallSite = true;
                if (!completionKnownAt(invocation.getArgument(parameterIndex), invocation)) {
                    return false;
                }
            }
        }
        return foundCallSite;
    }

    private boolean looksLikeCompletionType(String typeName) {
        String simple = simpleType(typeName).toLowerCase(Locale.ROOT);
        return simple.contains("future") || simple.contains("completionstage");
    }

    private boolean hasAmbiguousOverload(ClassOrInterfaceDeclaration declaration,
                                         MethodDeclaration target) {
        int matches = 0;
        for (MethodDeclaration candidate : declaration.getMethods()) {
            if (candidate.getNameAsString().equals(target.getNameAsString())
                    && candidate.getParameters().size() == target.getParameters().size()) {
                matches++;
            }
        }
        return matches > 1;
    }

    private boolean completionKnownAt(Expression target, Node location) {
        String targetKey = expressionKey(target);
        Node child = location;
        Optional<Node> parent = child.getParentNode();
        while (parent.isPresent()) {
            Node currentParent = parent.get();
            if (currentParent instanceof IfStmt) {
                IfStmt conditional = (IfStmt) currentParent;
                if (conditional.getThenStmt() == child
                        && conditionImpliesDone(conditional.getCondition(), targetKey, true)
                        && !writesTargetBefore(conditional.getThenStmt(), location, targetKey)) {
                    return true;
                }
                if (conditional.getElseStmt().isPresent()
                        && conditional.getElseStmt().get() == child
                        && conditionImpliesDone(conditional.getCondition(), targetKey, false)
                        && !writesTargetBefore(conditional.getElseStmt().get(), location, targetKey)) {
                    return true;
                }
            }
            if (currentParent instanceof BlockStmt && child instanceof Statement
                    && priorGuardImpliesDone((BlockStmt) currentParent, (Statement) child, targetKey)) {
                return true;
            }
            child = currentParent;
            parent = child.getParentNode();
        }
        return false;
    }

    private boolean priorGuardImpliesDone(BlockStmt block,
                                          Statement location,
                                          String targetKey) {
        int index = block.getStatements().indexOf(location);
        for (int i = index - 1; i >= 0; i--) {
            Statement statement = block.getStatement(i);
            if (statement instanceof IfStmt) {
                IfStmt conditional = (IfStmt) statement;
                if (alwaysExits(conditional.getThenStmt())
                        && conditionImpliesDone(conditional.getCondition(), targetKey, false)) {
                    return true;
                }
                if (conditional.getElseStmt().isPresent()
                        && alwaysExits(conditional.getElseStmt().get())
                        && conditionImpliesDone(conditional.getCondition(), targetKey, true)) {
                    return true;
                }
            }
            if (writesTarget(statement, targetKey)) {
                return false;
            }
        }
        return false;
    }

    private boolean writesTargetBefore(Node container, Node location, String targetKey) {
        for (AssignExpr assignment : container.findAll(AssignExpr.class)) {
            if (appearsBefore(assignment, location)
                    && targetKey.equals(expressionKey(assignment.getTarget()))) {
                return true;
            }
        }
        return false;
    }

    private boolean appearsBefore(Node candidate, Node location) {
        return line(candidate) < line(location)
                || (line(candidate) == line(location) && column(candidate) < column(location));
    }

    private boolean writesTarget(Node node, String targetKey) {
        for (AssignExpr assignment : node.findAll(AssignExpr.class)) {
            if (targetKey.equals(expressionKey(assignment.getTarget()))) {
                return true;
            }
        }
        return false;
    }

    private boolean alwaysExits(Statement statement) {
        if (statement instanceof ReturnStmt || statement instanceof ThrowStmt) {
            return true;
        }
        if (statement instanceof BlockStmt) {
            BlockStmt block = (BlockStmt) statement;
            return !block.getStatements().isEmpty()
                    && alwaysExits(block.getStatement(block.getStatements().size() - 1));
        }
        if (statement instanceof IfStmt) {
            IfStmt conditional = (IfStmt) statement;
            return conditional.getElseStmt().isPresent()
                    && alwaysExits(conditional.getThenStmt())
                    && alwaysExits(conditional.getElseStmt().get());
        }
        return false;
    }

    private boolean conditionImpliesDone(Expression condition,
                                         String targetKey,
                                         boolean conditionValue) {
        if (condition instanceof EnclosedExpr) {
            return conditionImpliesDone(((EnclosedExpr) condition).getInner(), targetKey, conditionValue);
        }
        if (condition instanceof UnaryExpr
                && ((UnaryExpr) condition).getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            return conditionImpliesDone(
                    ((UnaryExpr) condition).getExpression(), targetKey, !conditionValue);
        }
        if (condition instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) condition;
            return conditionValue
                    && "isDone".equals(call.getNameAsString())
                    && call.getArguments().isEmpty()
                    && call.getScope().isPresent()
                    && targetKey.equals(expressionKey(call.getScope().get()));
        }
        if (!(condition instanceof BinaryExpr)) {
            return false;
        }

        BinaryExpr binary = (BinaryExpr) condition;
        if (binary.getOperator() == BinaryExpr.Operator.AND) {
            if (conditionValue) {
                return conditionImpliesDone(binary.getLeft(), targetKey, true)
                        || conditionImpliesDone(binary.getRight(), targetKey, true);
            }
            return conditionImpliesDone(binary.getLeft(), targetKey, false)
                    && conditionImpliesDone(binary.getRight(), targetKey, false);
        }
        if (binary.getOperator() == BinaryExpr.Operator.OR) {
            if (conditionValue) {
                return conditionImpliesDone(binary.getLeft(), targetKey, true)
                        && conditionImpliesDone(binary.getRight(), targetKey, true);
            }
            return conditionImpliesDone(binary.getLeft(), targetKey, false)
                    || conditionImpliesDone(binary.getRight(), targetKey, false);
        }
        return false;
    }

    private String expressionKey(Expression expression) {
        if (expression instanceof EnclosedExpr) {
            return expressionKey(((EnclosedExpr) expression).getInner());
        }
        if (expression instanceof NameExpr) {
            return ((NameExpr) expression).getNameAsString();
        }
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expression;
            if (field.getScope() instanceof ThisExpr) {
                return field.getNameAsString();
            }
        }
        return expression.toString();
    }

    private boolean looksLikeFutureGet(MethodCallExpr call) {
        if (!call.getScope().isPresent()) {
            return false;
        }
        String scope = call.getScope().get().toString().toLowerCase(Locale.ROOT);
        return scope.contains("future") || scope.contains("completionstage") || scope.contains("promise");
    }

    private boolean looksLikeBlockingIo(MethodCallExpr call) {
        String name = call.getNameAsString();
        if (!("read".equals(name) || "executeQuery".equals(name) || "executeUpdate".equals(name)
                || "execute".equals(name) || "send".equals(name))) {
            return false;
        }
        if (!call.getScope().isPresent()) {
            return false;
        }
        String scope = call.getScope().get().toString().toLowerCase(Locale.ROOT);
        return scope.contains("socket")
                || scope.contains("stream")
                || scope.contains("reader")
                || scope.contains("jdbc")
                || scope.contains("statement")
                || scope.contains("connection")
                || scope.contains("http")
                || scope.contains("client");
    }

    private boolean looksLikeBusyWait(WhileStmt loop) {
        String text = loop.toString().toLowerCase(Locale.ROOT);
        return (text.contains("while (true)") || text.contains("while(true)")
                || text.contains("isdone()") || text.contains("iscomplete()")
                || text.contains("isready()"))
                && !text.contains("timedout()")
                && !text.contains("starttimeout(")
                && !text.contains("thread.sleep")
                && !text.contains("return ");
    }

    private boolean looksLikeBusyWait(ForStmt loop) {
        String text = loop.toString().toLowerCase(Locale.ROOT);
        return (text.contains("for (;;)") || text.contains("for(;;)"))
                && !text.contains("timedout()")
                && !text.contains("thread.sleep")
                && !text.contains("return ");
    }

    private String callLabel(MethodCallExpr call) {
        if (call.getScope().isPresent()) {
            return call.getScope().get().toString() + "." + call.getNameAsString() + "(...)";
        }
        return call.getNameAsString() + "(...)";
    }

    private boolean hasFiniteWaitHint(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("payment")
                || lower.contains("approval")
                || lower.contains("approved")
                || lower.contains("confirm")
                || lower.contains("complete")
                || lower.contains("inventory")
                || lower.contains("shipment")
                || lower.contains("fulfill")
                || lower.contains("request")
                || lower.contains("response")
                || lower.contains("callback");
    }

    private void analyzeProviderCalls(ClassInfo info, MethodDeclaration method, List<AnalysisFact> facts) {
        analyzeProviderCalls(info.file, method, facts);
    }

    private void analyzeProviderCalls(String file, Node body, List<AnalysisFact> facts) {
        List<String> providerNames = providerNames();
        if (providerNames.isEmpty()) {
            return;
        }
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (!ASYNC_BOUNDARY_METHODS.contains(call.getNameAsString())
                    && !isInsideAsyncBoundary(call)
                    && matchesAnyProvider(call.toString(), providerNames)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.PROVIDER_CALL,
                        file,
                        line(call),
                        column(call),
                        call.getNameAsString(),
                        call.toString()));
            }
        }
        for (ObjectCreationExpr creation : body.findAll(ObjectCreationExpr.class)) {
            if (!isInsideAsyncBoundary(creation)
                    && matchesAnyProvider(creation.getType().asString(), providerNames)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.PROVIDER_CALL,
                        file,
                        line(creation),
                        column(creation),
                        creation.getType().asString(),
                        creation.toString()));
            }
        }
    }

    private List<String> providerNames() {
        List<String> names = new ArrayList<>(DEFAULT_PROVIDER_NAMES);
        names.addAll(config.providerClientNames());
        return names;
    }

    private boolean matchesAnyProvider(String text, List<String> providerNames) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String providerName : providerNames) {
            if (!providerName.trim().isEmpty()
                    && lower.contains(providerName.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void analyzeFlowFactories(UnitAst ast,
                                      Map<String, ClassInfo> classesByName,
                                      Map<String, String> constants,
                                      List<FlowBuilderSite> flowBuilders,
                                      List<AnalysisFact> facts) {
        for (ClassOrInterfaceDeclaration declaration
                : ast.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean flowFactory = declaration.getNameAsString().endsWith("FlowFactory");
            if (flowFactory) {
                for (MethodDeclaration method : declaration.getMethods()) {
                    analyzeFlowDriveCalls(ast.file, declaration.getNameAsString(), method, false, facts);
                }
            }
        }

        for (MethodCallExpr call : ast.compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"build".equals(call.getNameAsString())) {
                continue;
            }
            FlowChain chain = flowChain(call, constants);
            if (chain == null) {
                continue;
            }
            flowBuilders.add(new FlowBuilderSite(
                    ast.file,
                    line(chain.builderCall),
                    chain.flowType,
                    chain.durable,
                    chain.eventDriven,
                    chain.declaredStepIds,
                    Collections.<String>emptySet(),
                    chain.allDurableStepsRecoverable));
            addFlowChainFacts(ast.file, chain, classesByName, facts);
        }
    }

    private FlowChain flowChain(MethodCallExpr buildCall, Map<String, String> constants) {
        List<MethodCallExpr> reversed = new ArrayList<>();
        Expression cursor = buildCall;
        MethodCallExpr builder = null;
        while (cursor instanceof MethodCallExpr) {
            MethodCallExpr current = (MethodCallExpr) cursor;
            reversed.add(current);
            if ("builder".equals(current.getNameAsString())
                    && (isScopeNamed(current, "Flow") || isScopeNamed(current, "EventFlow"))) {
                builder = current;
                break;
            }
            Optional<Expression> scope = current.getScope();
            if (!scope.isPresent()) {
                return null;
            }
            cursor = scope.get();
        }
        if (builder == null) {
            return null;
        }
        Collections.reverse(reversed);
        FlowChain chain = new FlowChain(builder);
        chain.eventDriven = isScopeNamed(builder, "EventFlow");
        if (!builder.getArguments().isEmpty()) {
            chain.flowType = stringValue(builder.getArgument(0), constants);
        }
        for (MethodCallExpr call : reversed) {
            String name = call.getNameAsString();
            if ("durable".equals(name)) {
                chain.durable = true;
            } else if ("step".equals(name) || "durableStep".equals(name)) {
                String stepId = call.getArguments().isEmpty()
                        ? null
                        : stringValue(call.getArgument(0), constants);
                if (stepId != null) {
                    chain.declaredStepIds.add(stepId);
                    chain.stepCalls.add(new StepCall(name, stepId, call));
                }
            }
        }
        return chain;
    }

    private void addFlowChainFacts(String file,
                                   FlowChain chain,
                                   Map<String, ClassInfo> classesByName,
                                   List<AnalysisFact> facts) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (StepCall stepCall : chain.stepCalls) {
            if (!seen.add(stepCall.stepId) && duplicated.add(stepCall.stepId)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.DUPLICATE_STEP_ID,
                        file,
                        line(stepCall.call),
                        column(stepCall.call),
                        stepCall.stepId,
                        "duplicate step id in one Flow.builder chain"));
            }
            analyzeInlineGuard(file, stepCall, facts);
        }

        if (!chain.durable) {
            return;
        }
        if (chain.eventDriven) {
            addDurableEventStepFacts(file, chain, classesByName, facts);
            return;
        }
        for (StepCall stepCall : chain.stepCalls) {
            if ("durableStep".equals(stepCall.methodName)) {
                continue;
            }
            String stepType = stepArgumentType(stepCall.call);
            if (stepType != null && isKnownDurableStep(stepType, classesByName)) {
                continue;
            }
            chain.allDurableStepsRecoverable = false;
            facts.add(new AnalysisFact(
                    AnalysisFact.DURABLE_STEP_MISSING_RECOVERY,
                    file,
                    line(stepCall.call),
                    column(stepCall.call),
                    stepCall.stepId,
                    stepType == null
                            ? "durable flow step does not expose a statically known DurableStep type"
                            : "durable flow step uses " + stepType + " without an explicit recovery policy"));
        }
    }

    private String stepArgumentType(MethodCallExpr stepCall) {
        if (stepCall.getArguments().size() < 2) {
            return null;
        }
        Expression arg = stepCall.getArgument(1);
        if (arg.isObjectCreationExpr()) {
            return simpleType(arg.asObjectCreationExpr().getType().asString());
        }
        return null;
    }

    private boolean isKnownDurableStep(String stepType, Map<String, ClassInfo> classesByName) {
        ClassInfo info = classesByName.get(simpleType(stepType));
        return info != null && info.durable;
    }

    private void addGoToFacts(List<UnitAst> asts,
                              Map<String, Set<String>> constantsByFile,
                              List<FlowBuilderSite> flowBuilders,
                              List<AnalysisFact> facts) {
        Set<String> declared = new LinkedHashSet<>();
        for (FlowBuilderSite site : flowBuilders) {
            declared.addAll(site.declaredStepIds());
        }
        if (declared.isEmpty()) {
            return;
        }
        for (UnitAst ast : asts) {
            Map<String, String> constants = new HashMap<>();
            Set<String> values = constantsByFile.get(ast.file);
            if (values != null) {
                for (String value : values) {
                    constants.put(value, value);
                }
            }
            constants.putAll(collectStringConstants(ast.compilationUnit));
            for (MethodCallExpr call : ast.compilationUnit.findAll(MethodCallExpr.class)) {
                if (!isFlowerResultCall(call, "goTo") || call.getArguments().isEmpty()) {
                    continue;
                }
                String target = stringValue(call.getArgument(0), constants);
                if (target != null && !declared.contains(target)) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.GOTO_UNKNOWN_TARGET,
                            ast.file,
                            line(call),
                            column(call),
                            target,
                            "goTo target is not among literal step ids declared in analyzed Flow builders"));
                }
            }
        }
    }

    private void analyzeFlowDriveCalls(String file,
                                       String owner,
                                       Node body,
                                       boolean insideStep,
                                       List<AnalysisFact> facts) {
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (isDirectLifecycleInvocation(call) || isWorkerOrEngineControl(call)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.FLOW_DRIVE_CALL,
                        file,
                        line(call),
                        column(call),
                        owner,
                        (insideStep ? "Step lifecycle" : "FlowFactory") + " calls " + call));
            }
        }
    }

    private boolean isDirectLifecycleInvocation(MethodCallExpr call) {
        if (!LIFECYCLE_METHODS.contains(call.getNameAsString())
                || !call.getScope().isPresent()) {
            return false;
        }
        if (call.getScope().get() instanceof ThisExpr) {
            return true;
        }
        Optional<String> type = declaredTypeOf(call.getScope().get(), call);
        return type.isPresent() && looksLikeStepType(type.get());
    }

    private boolean isWorkerOrEngineControl(MethodCallExpr call) {
        if (!FLOW_DRIVE_METHODS.contains(call.getNameAsString())) {
            return false;
        }
        Optional<Expression> scope = call.getScope();
        if (!scope.isPresent()) {
            return false;
        }
        Optional<String> type = declaredTypeOf(scope.get(), call);
        return type.isPresent() && FLOW_RUNTIME_TYPES.contains(simpleType(type.get()));
    }

    private Optional<String> declaredTypeOf(Expression expression, Node location) {
        if (expression instanceof EnclosedExpr) {
            return declaredTypeOf(((EnclosedExpr) expression).getInner(), location);
        }
        if (expression instanceof ObjectCreationExpr) {
            return Optional.of(((ObjectCreationExpr) expression).getType().asString());
        }
        if (expression instanceof NameExpr) {
            String name = ((NameExpr) expression).getNameAsString();
            if (FLOW_RUNTIME_TYPES.contains(simpleType(name))) {
                return Optional.of(name);
            }
            return declaredVariableType(name, location);
        }
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expression;
            if (field.getScope() instanceof ThisExpr) {
                return declaredVariableType(field.getNameAsString(), location);
            }
        }
        return Optional.empty();
    }

    private Optional<String> declaredVariableType(String name, Node location) {
        Optional<MethodDeclaration> method = ancestor(location, MethodDeclaration.class);
        if (method.isPresent()) {
            Optional<String> local = localVariableType(method.get(), name, location);
            if (local.isPresent()) {
                return local;
            }
            for (int i = 0; i < method.get().getParameters().size(); i++) {
                if (name.equals(method.get().getParameter(i).getNameAsString())) {
                    return Optional.of(method.get().getParameter(i).getType().asString());
                }
            }
        }

        Optional<ConstructorDeclaration> constructor = ancestor(location, ConstructorDeclaration.class);
        if (constructor.isPresent()) {
            Optional<String> local = localVariableType(constructor.get(), name, location);
            if (local.isPresent()) {
                return local;
            }
            for (int i = 0; i < constructor.get().getParameters().size(); i++) {
                if (name.equals(constructor.get().getParameter(i).getNameAsString())) {
                    return Optional.of(constructor.get().getParameter(i).getType().asString());
                }
            }
        }

        Optional<ClassOrInterfaceDeclaration> owner = ancestor(location, ClassOrInterfaceDeclaration.class);
        if (owner.isPresent()) {
            for (FieldDeclaration field : owner.get().getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    if (name.equals(variable.getNameAsString())) {
                        return Optional.of(declaredVariableType(variable));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> localVariableType(Node callable, String name, Node location) {
        VariableDeclarator nearest = null;
        for (VariableDeclarator variable : callable.findAll(VariableDeclarator.class)) {
            if (!name.equals(variable.getNameAsString()) || line(variable) > line(location)) {
                continue;
            }
            if (nearest == null || line(variable) > line(nearest)) {
                nearest = variable;
            }
        }
        return nearest == null
                ? Optional.<String>empty()
                : Optional.of(declaredVariableType(nearest));
    }

    private String declaredVariableType(VariableDeclarator variable) {
        String type = variable.getType().asString();
        if ("var".equals(type) && variable.getInitializer().isPresent()
                && variable.getInitializer().get() instanceof ObjectCreationExpr) {
            return ((ObjectCreationExpr) variable.getInitializer().get()).getType().asString();
        }
        return type;
    }

    private <T extends Node> Optional<T> ancestor(Node node, Class<T> type) {
        Optional<Node> parent = node.getParentNode();
        while (parent.isPresent()) {
            if (type.isInstance(parent.get())) {
                return Optional.of(type.cast(parent.get()));
            }
            parent = parent.get().getParentNode();
        }
        return Optional.empty();
    }

    private void analyzeSubscribeCallbacks(ClassInfo info, MethodDeclaration method, List<AnalysisFact> facts) {
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            if (!"subscribe".equals(call.getNameAsString()) || call.getArguments().size() < 2) {
                continue;
            }
            Expression callback = call.getArgument(1);
            if (callback instanceof LambdaExpr) {
                LambdaExpr lambda = (LambdaExpr) callback;
                if (lambdaDecidesControl(lambda)) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.CALLBACK_CONTROL,
                            info.file,
                            line(lambda),
                            column(lambda),
                            info.simpleName,
                            "subscribe callback changes Step control flow instead of only recording a signal"));
                }
            } else if (callback instanceof MethodReferenceExpr) {
                MethodReferenceExpr ref = (MethodReferenceExpr) callback;
                if (LIFECYCLE_METHODS.contains(ref.getIdentifier())) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.CALLBACK_CONTROL,
                            info.file,
                            line(ref),
                            column(ref),
                            info.simpleName,
                            "subscribe callback references a Step lifecycle method"));
                }
            }
        }
    }

    private boolean lambdaDecidesControl(LambdaExpr lambda) {
        for (MethodCallExpr call : lambda.findAll(MethodCallExpr.class)) {
            if ("setStepNo".equals(call.getNameAsString()) || isStepResultCall(call, null)) {
                return true;
            }
        }
        return false;
    }

    private void analyzeRuntimeOwnership(ClassInfo info, List<AnalysisFact> facts) {
        for (FieldDeclaration field : info.declaration.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (OWNED_RUNTIME_TYPES.contains(simpleType(declaredVariableType(variable)))) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.RUNTIME_OWNERSHIP,
                            info.file,
                            line(variable),
                            column(variable),
                            info.simpleName,
                            "Step stores Flower runtime infrastructure: " + variable.getType()));
                }
            }
        }
        for (MethodCallExpr call : info.declaration.findAll(MethodCallExpr.class)) {
            Optional<String> scopeType = call.getScope().isPresent()
                    ? declaredTypeOf(call.getScope().get(), call)
                    : Optional.<String>empty();
            boolean builder = "builder".equals(call.getNameAsString())
                    && scopeType.isPresent()
                    && OWNED_RUNTIME_TYPES.contains(simpleType(scopeType.get()));
            if (builder) {
                facts.add(new AnalysisFact(
                        AnalysisFact.RUNTIME_OWNERSHIP,
                        info.file,
                        line(call),
                        column(call),
                        info.simpleName,
                        "Step creates or controls Flower runtime: " + call));
            }
        }
        for (ObjectCreationExpr creation : info.declaration.findAll(ObjectCreationExpr.class)) {
            if (OWNED_RUNTIME_TYPES.contains(simpleType(creation.getType().asString()))) {
                facts.add(new AnalysisFact(
                        AnalysisFact.RUNTIME_OWNERSHIP,
                        info.file,
                        line(creation),
                        column(creation),
                        info.simpleName,
                        "Step creates Flower runtime infrastructure: " + creation.getType()));
            }
        }
    }

    private void analyzeInlineGuard(String file, StepCall stepCall, List<AnalysisFact> facts) {
        Optional<Expression> guard = guardArgument(stepCall);
        if (!guard.isPresent()
                || (!(guard.get() instanceof LambdaExpr)
                && !(guard.get() instanceof ObjectCreationExpr))) {
            return;
        }
        String owner = "inline Guard for step '" + stepCall.stepId + "'";
        analyzeBlockingNode(file, owner, guard.get(), facts);
        analyzeProviderCalls(file, guard.get(), facts);
        Optional<ClassOrInterfaceDeclaration> enclosing = ancestor(
                guard.get(), ClassOrInterfaceDeclaration.class);
        Set<String> fields = enclosing.isPresent()
                ? fieldNames(enclosing.get())
                : Collections.<String>emptySet();
        analyzeGuardSideEffects(file, owner, guard.get(), fields, false, facts);
    }

    private Optional<Expression> guardArgument(StepCall stepCall) {
        int expectedArguments = "durableStep".equals(stepCall.methodName) ? 4 : 3;
        if (stepCall.call.getArguments().size() < expectedArguments) {
            return Optional.empty();
        }
        return Optional.of(stepCall.call.getArgument(2));
    }

    private void addDurableEventStepFacts(String file,
                                          FlowChain chain,
                                          Map<String, ClassInfo> classesByName,
                                          List<AnalysisFact> facts) {
        for (StepCall stepCall : chain.stepCalls) {
            EventStepShape shape = eventStepShape(stepCall.call, classesByName);
            if (!shape.known || !shape.awaits) {
                continue;
            }
            if (!shape.recovers) {
                chain.allDurableStepsRecoverable = false;
                facts.add(new AnalysisFact(
                        AnalysisFact.DURABLE_EVENT_STEP_NOT_RECOVERABLE,
                        file,
                        line(stepCall.call),
                        column(stepCall.call),
                        stepCall.stepId,
                        "awaiting EventStep does not override onRecover(...)"));
            }
            if (shape.predicateAwait) {
                chain.allDurableStepsRecoverable = false;
                facts.add(new AnalysisFact(
                        AnalysisFact.DURABLE_EVENT_STEP_NOT_RECOVERABLE,
                        file,
                        line(stepCall.call),
                        column(stepCall.call),
                        stepCall.stepId,
                        "durable EventStep uses predicate-based AwaitCondition.event(...)"));
            }
        }
    }

    private EventStepShape eventStepShape(MethodCallExpr stepCall,
                                          Map<String, ClassInfo> classesByName) {
        if (stepCall.getArguments().size() < 2
                || !(stepCall.getArgument(1) instanceof ObjectCreationExpr)) {
            return EventStepShape.unknown();
        }
        ObjectCreationExpr creation = (ObjectCreationExpr) stepCall.getArgument(1);
        String typeName = simpleType(creation.getType().asString());
        boolean known = "EventStep".equals(typeName);
        boolean awaits = containsEventAwait(creation);
        boolean recovers = overridesMethod(creation, "onRecover");
        boolean predicateAwait = containsPredicateEventAwait(creation);

        ClassInfo info = classesByName.get(typeName);
        if (info != null && info.eventStep) {
            known = true;
            awaits = awaits || hierarchyContainsEventAwait(info, classesByName, new HashSet<String>());
            recovers = recovers || hierarchyOverridesMethod(
                    info, "onRecover", classesByName, new HashSet<String>());
            predicateAwait = predicateAwait || hierarchyContainsPredicateEventAwait(
                    info, classesByName, new HashSet<String>());
        }
        return new EventStepShape(known, awaits, recovers, predicateAwait);
    }

    private boolean hierarchyContainsEventAwait(ClassInfo info,
                                                Map<String, ClassInfo> classesByName,
                                                Set<String> visited) {
        if (!visited.add(info.simpleName)) {
            return false;
        }
        if (containsEventAwait(info.declaration)) {
            return true;
        }
        for (String parentName : info.extendedSimpleNames) {
            ClassInfo parent = classesByName.get(parentName);
            if (parent != null && hierarchyContainsEventAwait(parent, classesByName, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean hierarchyOverridesMethod(ClassInfo info,
                                             String methodName,
                                             Map<String, ClassInfo> classesByName,
                                             Set<String> visited) {
        if (!visited.add(info.simpleName)) {
            return false;
        }
        if (overridesMethod(info.declaration, methodName)) {
            return true;
        }
        for (String parentName : info.extendedSimpleNames) {
            ClassInfo parent = classesByName.get(parentName);
            if (parent != null
                    && hierarchyOverridesMethod(parent, methodName, classesByName, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean hierarchyContainsPredicateEventAwait(
            ClassInfo info,
            Map<String, ClassInfo> classesByName,
            Set<String> visited) {
        if (!visited.add(info.simpleName)) {
            return false;
        }
        if (containsPredicateEventAwait(info.declaration)) {
            return true;
        }
        for (String parentName : info.extendedSimpleNames) {
            ClassInfo parent = classesByName.get(parentName);
            if (parent != null
                    && hierarchyContainsPredicateEventAwait(parent, classesByName, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEventAwait(Node node) {
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            if (isResultCall(call, "EventStepResult", "await")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPredicateEventAwait(Node node) {
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            if (isScopeNamed(call, "AwaitCondition")
                    && "event".equals(call.getNameAsString())
                    && call.getArguments().size() > 1) {
                return true;
            }
        }
        return false;
    }

    private boolean overridesMethod(Node node, String methodName) {
        for (MethodDeclaration method : node.findAll(MethodDeclaration.class)) {
            if (methodName.equals(method.getNameAsString())) {
                return true;
            }
        }
        return false;
    }

    private void analyzeRawSubscriptions(ClassInfo info, MethodDeclaration method, List<AnalysisFact> facts) {
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            if (!"subscribe".equals(call.getNameAsString())) {
                continue;
            }
            Optional<Expression> scope = call.getScope();
            if (scope.isPresent() && scope.get() instanceof MethodCallExpr
                    && "eventBus".equals(((MethodCallExpr) scope.get()).getNameAsString())) {
                facts.add(new AnalysisFact(
                        AnalysisFact.RAW_SUBSCRIPTION,
                        info.file,
                        line(call),
                        column(call),
                        info.simpleName,
                        (info.eventStep ? "EventStep" : "Step")
                                + " subscribes through ctx.eventBus().subscribe(...)"));
            }
        }
    }

    private void analyzeExecutionContextUses(String file, Node node, List<AnalysisFact> facts) {
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (!EXECUTION_CONTEXT_BUSINESS_METHODS.contains(name)) {
                continue;
            }
            String text = call.toString();
            if (text.contains("executionContext()") || text.contains("ExecutionContext.builder()")) {
                facts.add(new AnalysisFact(
                        AnalysisFact.EXECUTION_CONTEXT_BUSINESS_USE,
                        file,
                        line(call),
                        column(call),
                        name,
                        "ExecutionContext is used as a business context via " + name + "()"));
            }
        }
    }

    private void analyzeSharedStepInstances(UnitAst ast, List<AnalysisFact> facts) {
        for (FieldDeclaration field : ast.compilationUnit.findAll(FieldDeclaration.class)) {
            if (!field.isStatic()) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                if (looksLikeStepType(variable.getType().asString())
                        || (variable.getInitializer().isPresent()
                        && variable.getInitializer().get().isObjectCreationExpr()
                        && looksLikeStepType(variable.getInitializer().get()
                        .asObjectCreationExpr().getType().asString()))) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.SHARED_STEP_INSTANCE,
                            ast.file,
                            line(variable),
                            column(variable),
                            variable.getNameAsString(),
                            "static Step instance can be reused across Flows"));
                }
            }
        }

        for (BodyDeclaration<?> body : ast.compilationUnit.findAll(BodyDeclaration.class)) {
            Map<String, VariableDeclarator> stepLocals = new HashMap<>();
            for (VariableDeclarator variable : body.findAll(VariableDeclarator.class)) {
                if (variable.getInitializer().isPresent()
                        && variable.getInitializer().get().isObjectCreationExpr()
                        && looksLikeStepType(variable.getInitializer().get()
                        .asObjectCreationExpr().getType().asString())) {
                    stepLocals.put(variable.getNameAsString(), variable);
                }
            }
            Map<String, Integer> usageCounts = new HashMap<>();
            for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
                if (!("step".equals(call.getNameAsString()) || "durableStep".equals(call.getNameAsString()))
                        || call.getArguments().size() < 2) {
                    continue;
                }
                Expression stepArg = call.getArgument(1);
                if (stepArg instanceof NameExpr && stepLocals.containsKey(((NameExpr) stepArg).getNameAsString())) {
                    String name = ((NameExpr) stepArg).getNameAsString();
                    usageCounts.put(name, usageCounts.containsKey(name) ? usageCounts.get(name) + 1 : 1);
                }
            }
            for (Map.Entry<String, Integer> entry : usageCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    VariableDeclarator variable = stepLocals.get(entry.getKey());
                    facts.add(new AnalysisFact(
                            AnalysisFact.SHARED_STEP_INSTANCE,
                            ast.file,
                            line(variable),
                            column(variable),
                            entry.getKey(),
                            "same Step instance is passed to multiple builder steps"));
                }
            }
        }
    }

    private boolean looksLikeStepType(String typeName) {
        return simpleType(typeName).endsWith("Step");
    }

    private void analyzeSchedulerUsage(UnitAst ast, List<AnalysisFact> facts) {
        for (MethodDeclaration method : ast.compilationUnit.findAll(MethodDeclaration.class)) {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String name = simpleType(annotation.getNameAsString());
                if (SCHEDULER_ANNOTATIONS.contains(name) && !hasSchedulerApproval(method)) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.UNAPPROVED_RECURRING_SCHEDULER,
                            ast.file,
                            line(annotation),
                            column(annotation),
                            "@" + name,
                            "recurring scheduler annotation @" + name
                                    + " has no explicit user approval annotation"));
                }
            }
        }

        for (ClassOrInterfaceDeclaration declaration
                : ast.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            for (AnnotationExpr annotation : declaration.getAnnotations()) {
                String name = simpleType(annotation.getNameAsString());
                if ("EnableScheduling".equals(name) && !hasSchedulerApproval(declaration)) {
                    facts.add(new AnalysisFact(
                            AnalysisFact.UNAPPROVED_RECURRING_SCHEDULER,
                            ast.file,
                            line(annotation),
                            column(annotation),
                            "@" + name,
                            "Spring scheduling is enabled without an explicit user approval annotation"));
                }
            }
        }

        for (MethodCallExpr call : ast.compilationUnit.findAll(MethodCallExpr.class)) {
            if (isRecurringSchedulerCall(call) && !hasSchedulerApproval(call)) {
                facts.add(new AnalysisFact(
                        AnalysisFact.UNAPPROVED_RECURRING_SCHEDULER,
                        ast.file,
                        line(call),
                        column(call),
                        call.getNameAsString() + "(...)",
                        "recurring scheduler call " + call.getNameAsString()
                                + "(...) has no explicit user approval annotation"));
            }
        }
    }

    private boolean isRecurringSchedulerCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        if (RECURRING_SCHEDULER_METHODS.contains(name)) {
            return true;
        }
        if ("schedule".equals(name) && call.getArguments().size() >= 3 && call.getScope().isPresent()) {
            return call.getScope().get().toString().toLowerCase(Locale.ROOT).contains("timer");
        }
        return false;
    }

    private boolean hasSchedulerApproval(Node node) {
        if (node instanceof MethodDeclaration
                && hasSchedulerApprovalAnnotation(((MethodDeclaration) node).getAnnotations())) {
            return true;
        }
        if (node instanceof ClassOrInterfaceDeclaration
                && hasSchedulerApprovalAnnotation(((ClassOrInterfaceDeclaration) node).getAnnotations())) {
            return true;
        }
        MethodDeclaration method = enclosingMethod(node);
        if (method != null && hasSchedulerApprovalAnnotation(method.getAnnotations())) {
            return true;
        }
        ClassOrInterfaceDeclaration declaration = enclosingClass(node);
        return declaration != null && hasSchedulerApprovalAnnotation(declaration.getAnnotations());
    }

    private boolean hasSchedulerApprovalAnnotation(Iterable<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            String name = simpleType(annotation.getNameAsString());
            for (String approved : config.schedulerApprovalAnnotations()) {
                if (name.equals(simpleType(approved))) {
                    return true;
                }
            }
        }
        return false;
    }

    private MethodDeclaration enclosingMethod(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent()) {
            Node parent = current.get();
            if (parent instanceof MethodDeclaration) {
                return (MethodDeclaration) parent;
            }
            current = parent.getParentNode();
        }
        return null;
    }

    private ClassOrInterfaceDeclaration enclosingClass(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent()) {
            Node parent = current.get();
            if (parent instanceof ClassOrInterfaceDeclaration) {
                return (ClassOrInterfaceDeclaration) parent;
            }
            current = parent.getParentNode();
        }
        return null;
    }

    private void analyzeAgentFacts(UnitAst ast, List<AnalysisFact> facts) {
        for (ClassOrInterfaceDeclaration declaration
                : ast.compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean agentishClass = containsAgentActionName(declaration.getName());
            boolean approvalClass = containsApprovalRequiredName(declaration.getName());
            String classText = declaration.toString().toLowerCase(Locale.ROOT);
            boolean hasGate = classText.contains("actionregistry") || classText.contains("policygate");
            boolean hasAudit = classText.contains("audit") || classText.contains("operationevent")
                    || classText.contains("publish(") || classText.contains("emit(");

            for (MethodDeclaration method : declaration.getMethods()) {
                boolean agentish = agentishClass || containsAgentActionName(method.getName());
                boolean approval = approvalClass || containsApprovalRequiredName(method.getName());
                if (!agentish && !approval) {
                    continue;
                }
                for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                    if (!AGENT_WRITE_METHODS.contains(call.getNameAsString())) {
                        continue;
                    }
                    if (!hasGate) {
                        facts.add(new AnalysisFact(
                                AnalysisFact.AGENT_WRITE_BYPASS,
                                ast.file,
                                line(call),
                                column(call),
                                declaration.getNameAsString(),
                                "agent/action write call is not visibly routed through ActionRegistry/PolicyGate"));
                    }
                    if (!hasAudit) {
                        facts.add(new AnalysisFact(
                                AnalysisFact.AGENT_MISSING_AUDIT,
                                ast.file,
                                line(call),
                                column(call),
                                declaration.getNameAsString(),
                                "agent/action write call has no visible audit or operation event"));
                    }
                    if (approval) {
                        facts.add(new AnalysisFact(
                                AnalysisFact.APPROVAL_DIRECT_EXECUTION,
                                ast.file,
                                line(call),
                                column(call),
                                declaration.getNameAsString(),
                                "approval-required action executes a write inline"));
                    }
                }
            }
        }
    }

    private boolean containsAgentActionName(SimpleName name) {
        String text = name.asString().toLowerCase(Locale.ROOT);
        return text.contains("agent") || text.contains("action");
    }

    private boolean containsApprovalRequiredName(SimpleName name) {
        String text = name.asString().toLowerCase(Locale.ROOT);
        return text.contains("approvalrequired") || text.contains("requiresapproval");
    }

    private String stringValue(Expression expression, Map<String, String> constants) {
        if (expression instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expression).asString();
        }
        if (expression instanceof NameExpr) {
            return constants.get(((NameExpr) expression).getNameAsString());
        }
        return null;
    }

    private boolean isStepResultCall(MethodCallExpr call, String expectedName) {
        return isResultCall(call, "StepResult", expectedName);
    }

    private boolean isFlowerResultCall(MethodCallExpr call, String expectedName) {
        return isResultCall(call, "StepResult", expectedName)
                || isResultCall(call, "EventStepResult", expectedName)
                || isResultCall(call, "GuardResult", expectedName);
    }

    private boolean isResultCall(MethodCallExpr call, String resultType, String expectedName) {
        if (expectedName != null && !expectedName.equals(call.getNameAsString())) {
            return false;
        }
        return isScopeNamed(call, resultType);
    }

    private boolean isScopeNamed(MethodCallExpr call, String name) {
        Optional<Expression> scope = call.getScope();
        return scope.isPresent() && name.equals(scope.get().toString());
    }

    private static String simpleType(String typeName) {
        String simple = typeName == null ? "" : typeName.trim();
        int generic = simple.indexOf('<');
        if (generic >= 0) {
            simple = simple.substring(0, generic);
        }
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) {
            simple = simple.substring(dot + 1);
        }
        return simple;
    }

    private static int line(Node node) {
        return node.getBegin().isPresent() ? node.getBegin().get().line : 1;
    }

    private static int column(Node node) {
        return node.getBegin().isPresent() ? node.getBegin().get().column : 0;
    }

    private static final class UnitAst {
        private final String file;
        private final CompilationUnit compilationUnit;

        private UnitAst(String file, CompilationUnit compilationUnit) {
            this.file = file;
            this.compilationUnit = compilationUnit;
        }
    }

    private static final class ClassInfo {
        private final String file;
        private final ClassOrInterfaceDeclaration declaration;
        private final String simpleName;
        private final Set<String> extendedSimpleNames = new LinkedHashSet<>();
        private final Set<String> implementedSimpleNames = new LinkedHashSet<>();
        private boolean step;
        private boolean coreStep;
        private boolean eventStep;
        private boolean durable;
        private boolean guard;

        private ClassInfo(String file, ClassOrInterfaceDeclaration declaration) {
            this.file = file;
            this.declaration = declaration;
            this.simpleName = declaration.getNameAsString();
        }
    }

    private static final class EventStepShape {
        private final boolean known;
        private final boolean awaits;
        private final boolean recovers;
        private final boolean predicateAwait;

        private EventStepShape(boolean known,
                               boolean awaits,
                               boolean recovers,
                               boolean predicateAwait) {
            this.known = known;
            this.awaits = awaits;
            this.recovers = recovers;
            this.predicateAwait = predicateAwait;
        }

        private static EventStepShape unknown() {
            return new EventStepShape(false, false, false, false);
        }
    }

    private static final class FlowChain {
        private final MethodCallExpr builderCall;
        private String flowType;
        private boolean durable;
        private boolean eventDriven;
        private boolean allDurableStepsRecoverable = true;
        private final List<String> declaredStepIds = new ArrayList<>();
        private final List<StepCall> stepCalls = new ArrayList<>();

        private FlowChain(MethodCallExpr builderCall) {
            this.builderCall = builderCall;
        }
    }

    private static final class StepCall {
        private final String methodName;
        private final String stepId;
        private final MethodCallExpr call;

        private StepCall(String methodName, String stepId, MethodCallExpr call) {
            this.methodName = methodName;
            this.stepId = stepId;
            this.call = call;
        }
    }
}
