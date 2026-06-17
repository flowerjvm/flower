package io.github.parkkevinsb.flower.eventloop;

/** Thrown (as a flow failure cause) when an event step's deadline elapses. */
public class EventTimeoutException extends RuntimeException {

    public EventTimeoutException(String message) {
        super(message);
    }
}
