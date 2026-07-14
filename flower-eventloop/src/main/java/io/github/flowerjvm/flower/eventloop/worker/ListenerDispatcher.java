package io.github.flowerjvm.flower.eventloop.worker;

import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;

import java.util.List;

final class ListenerDispatcher {

    private final String workerName;
    private final List<FlowerListener> listeners;

    ListenerDispatcher(String workerName, List<FlowerListener> listeners) {
        this.workerName = workerName;
        this.listeners = listeners;
    }

    List<FlowerListener> listeners() {
        return listeners;
    }

    void flowSubmitted(EventFlow flow) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener listener : listeners) {
            try {
                listener.onFlowSubmitted(snap);
            } catch (Throwable t) {
                listenerError(snap, "onFlowSubmitted", t);
            }
        }
    }

    void stepEntered(EventFlow flow, String stepId) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener listener : listeners) {
            try {
                listener.onStepEntered(snap, stepId);
            } catch (Throwable t) {
                listenerError(snap, "onStepEntered", t);
            }
        }
    }

    void stepExited(EventFlow flow, String stepId) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener listener : listeners) {
            try {
                listener.onStepExited(snap, stepId);
            } catch (Throwable t) {
                listenerError(snap, "onStepExited", t);
            }
        }
    }

    void flowTerminated(EventFlow flow) {
        FlowSnapshot snap = flow.snapshot();
        switch (flow.state()) {
            case FINISHED:
                for (FlowerListener listener : listeners) {
                    try {
                        listener.onFlowFinished(snap);
                    } catch (Throwable t) {
                        listenerError(snap, "onFlowFinished", t);
                    }
                }
                break;
            case FAILED:
            case CHECKPOINT_FAILED:
                Throwable cause = flow.failureCause();
                for (FlowerListener listener : listeners) {
                    try {
                        listener.onFlowFailed(snap, cause);
                    } catch (Throwable t) {
                        listenerError(snap, "onFlowFailed", t);
                    }
                }
                break;
            case CANCELLED:
                for (FlowerListener listener : listeners) {
                    try {
                        listener.onFlowCancelled(snap);
                    } catch (Throwable t) {
                        listenerError(snap, "onFlowCancelled", t);
                    }
                }
                break;
            default:
                // not terminal
        }
    }

    void workerError(Throwable cause) {
        for (FlowerListener listener : listeners) {
            try {
                listener.onWorkerError(workerName, cause);
            } catch (Throwable ignored) {
            }
        }
    }

    private void listenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
        for (FlowerListener listener : listeners) {
            try {
                listener.onListenerError(flow, callbackName, cause);
            } catch (Throwable ignored) {
            }
        }
    }
}
