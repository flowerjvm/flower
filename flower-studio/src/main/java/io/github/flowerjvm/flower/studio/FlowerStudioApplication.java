package io.github.flowerjvm.flower.studio;

import io.github.flowerjvm.flower.studio.server.StudioHttpServer;
import io.github.flowerjvm.flower.studio.store.JsonLinesObservationRepository;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

/** Runnable entry point for the local, read-only Flower Studio. */
public final class FlowerStudioApplication {

    private FlowerStudioApplication() {
    }

    public static void main(String[] args) throws Exception {
        final StudioOptions options = StudioOptions.parse(args);
        if (options.help()) {
            System.out.print(StudioOptions.helpText());
            return;
        }

        JsonLinesObservationRepository repository = new JsonLinesObservationRepository(
                options.traceFile(),
                options.maxEvents());
        final StudioHttpServer server = new StudioHttpServer(
                new InetSocketAddress(options.host(), options.port()),
                repository,
                options.artifactRoot());
        final CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.close();
                stopped.countDown();
            }
        }, "flower-studio-shutdown"));

        server.start();
        System.out.println("Flower Studio: http://" + options.host() + ":" + server.port());
        System.out.println("Observation file: " + options.traceFile());
        System.out.println("Read-only local reference application. Press Ctrl+C to stop.");
        stopped.await();
    }
}
