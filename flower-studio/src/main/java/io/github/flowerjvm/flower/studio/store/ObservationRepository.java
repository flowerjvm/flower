package io.github.flowerjvm.flower.studio.store;

import java.io.IOException;

/** Read-only source used by Flower Studio. */
public interface ObservationRepository {

    StudioSnapshot load() throws IOException;
}
