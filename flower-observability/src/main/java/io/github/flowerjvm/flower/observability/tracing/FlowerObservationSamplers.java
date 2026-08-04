package io.github.flowerjvm.flower.observability.tracing;

/** Deterministic samplers keyed by the common {@code traceId}. */
public final class FlowerObservationSamplers {

    private static final double UNSIGNED_INT_RANGE = 4_294_967_296.0d;

    private FlowerObservationSamplers() {
    }

    public static FlowerObservationSampler always() {
        return event -> true;
    }

    public static FlowerObservationSampler never() {
        return event -> false;
    }

    public static FlowerObservationSampler probability(double rate) {
        if (Double.isNaN(rate) || rate < 0.0d || rate > 1.0d) {
            throw new IllegalArgumentException("rate must be between 0.0 and 1.0: " + rate);
        }
        if (rate == 0.0d) {
            return never();
        }
        if (rate == 1.0d) {
            return always();
        }
        return event -> {
            if (event == null) {
                throw new IllegalArgumentException("event must not be null");
            }
            long unsignedHash = Integer.toUnsignedLong(mix(event.traceId().hashCode()));
            return unsignedHash / UNSIGNED_INT_RANGE < rate;
        };
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        return value;
    }
}
