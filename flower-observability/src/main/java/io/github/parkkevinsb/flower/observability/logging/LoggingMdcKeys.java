package io.github.parkkevinsb.flower.observability.logging;

/**
 * SLF4J {@code MDC} key names used by Flower observability bindings.
 *
 * <p>Both {@link LoggingFlowerListener} and {@link StepLogger} populate these
 * keys around log statements so that downstream JSON log appenders can index
 * messages by flow type / key / step.
 */
public final class LoggingMdcKeys {

    public static final String FLOW_TYPE = "flower.flow.type";
    public static final String FLOW_KEY = "flower.flow.key";
    public static final String STEP_ID = "flower.step.id";
    public static final String STEP_NO = "flower.step.no";

    private LoggingMdcKeys() {
    }
}
