package io.github.flowerjvm.flower.studio.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.studio.store.JsonLinesObservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class StudioHttpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void serves_read_only_trace_api_ui_and_safe_artifacts() throws Exception {
        Path traceFile = temporaryDirectory.resolve("observations.jsonl");
        Files.write(traceFile, Collections.singletonList(observation()), StandardCharsets.UTF_8);
        Path artifactRoot = temporaryDirectory.resolve("artifacts");
        Files.createDirectories(artifactRoot.resolve("trace-1"));
        Files.write(
                artifactRoot.resolve("trace-1/evidence.txt"),
                Collections.singletonList("evidence"),
                StandardCharsets.UTF_8);

        try (StudioHttpServer server = new StudioHttpServer(
                new InetSocketAddress("127.0.0.1", 0),
                new JsonLinesObservationRepository(traceFile),
                artifactRoot)) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            Response index = request("GET", base + "/");
            assertThat(index.status).isEqualTo(200);
            assertThat(index.body).contains("Flower Studio");
            assertThat(index.contentSecurityPolicy).contains("default-src 'self'");

            JsonNode health = json(request("GET", base + "/api/health"));
            assertThat(health.path("status").asText()).isEqualTo("UP");
            assertThat(health.path("readOnly").asBoolean()).isTrue();

            JsonNode list = json(request("GET", base + "/api/traces"));
            assertThat(list.path("totalMatched").asInt()).isEqualTo(1);
            assertThat(list.path("traces").get(0).path("traceId").asText())
                    .isEqualTo("trace-1");

            JsonNode detail = json(request("GET", base + "/api/traces/trace-1"));
            assertThat(detail.path("summary").path("status").asText()).isEqualTo("RUNNING");

            Response artifact = request(
                    "GET",
                    base + "/api/artifacts?location=trace-1%2Fevidence.txt");
            assertThat(artifact.status).isEqualTo(200);
            assertThat(artifact.body).contains("evidence");

            assertThat(request(
                    "GET",
                    base + "/api/artifacts?location=..%2Fevidence.txt").status)
                    .isEqualTo(404);
            assertThat(request("POST", base + "/api/traces").status).isEqualTo(405);
            assertThat(request("GET", base + "/api/traces/missing").status).isEqualTo(404);
        }
    }

    private static JsonNode json(Response response) throws Exception {
        assertThat(response.status).isEqualTo(200);
        return MAPPER.readTree(response.body);
    }

    private static Response request(String method, String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        connection.setDoInput(true);
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = input == null ? "" : read(input);
        String policy = connection.getHeaderField("Content-Security-Policy");
        connection.disconnect();
        return new Response(status, body, policy);
    }

    private static String read(InputStream input) throws Exception {
        try (InputStream selected = input;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1_024];
            int count;
            while ((count = selected.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String observation() {
        return "{\"schemaVersion\":1,\"source\":\"flower-agent\","
                + "\"eventId\":\"event-1\",\"eventType\":\"RUN_STARTED\","
                + "\"traceId\":\"trace-1\",\"runId\":\"run-1\",\"sequence\":1,"
                + "\"occurredAt\":\"2026-08-01T00:00:00Z\","
                + "\"attributes\":{\"agent.id\":\"ops-agent\"}}";
    }

    private static final class Response {
        private final int status;
        private final String body;
        private final String contentSecurityPolicy;

        private Response(int status, String body, String contentSecurityPolicy) {
            this.status = status;
            this.body = body;
            this.contentSecurityPolicy = contentSecurityPolicy;
        }
    }
}
