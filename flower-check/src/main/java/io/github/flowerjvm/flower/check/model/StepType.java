package io.github.flowerjvm.flower.check.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A class identified as a Flower Step: it extends {@code Step},
 * {@code DurableStep}, or {@code EventStep} (directly or transitively within
 * the source set; core Step base classes may also be configured).
 *
 * <p>Records which lifecycle methods it overrides so "in-Step" rules scan only
 * those method bodies, not the whole class (see {@code docs/02-rule-catalog.md}
 * "Scope Of A Step Lifecycle Method").
 *
 * <p>Skeleton: a data holder. {@link ProjectModelBuilder} populates it from the
 * AST once the real parser is in place.
 */
public final class StepType {

    private final String simpleName;
    private final String file;
    private final boolean durable;
    private final boolean eventDriven;
    private final Set<String> overriddenLifecycleMethods;

    public StepType(String simpleName, String file, boolean durable, Set<String> overriddenLifecycleMethods) {
        this(simpleName, file, durable, false, overriddenLifecycleMethods);
    }

    public StepType(String simpleName,
                    String file,
                    boolean durable,
                    boolean eventDriven,
                    Set<String> overriddenLifecycleMethods) {
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
        this.file = Objects.requireNonNull(file, "file");
        this.durable = durable;
        this.eventDriven = eventDriven;
        this.overriddenLifecycleMethods =
                Collections.unmodifiableSet(new LinkedHashSet<>(overriddenLifecycleMethods));
    }

    public String simpleName() {
        return simpleName;
    }

    public String file() {
        return file;
    }

    public boolean durable() {
        return durable;
    }

    public boolean eventDriven() {
        return eventDriven;
    }

    /** Lifecycle methods for the Step's tick-driven or event-driven runtime. */
    public Set<String> overriddenLifecycleMethods() {
        return overriddenLifecycleMethods;
    }
}
