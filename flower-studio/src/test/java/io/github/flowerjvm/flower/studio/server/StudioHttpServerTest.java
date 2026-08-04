package io.github.flowerjvm.flower.studio.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.evaluation.EvaluationCandidate;
import io.github.flowerjvm.flower.evaluation.EvaluationDataset;
import io.github.flowerjvm.flower.evaluation.EvaluationEvaluators;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationExperiment;
import io.github.flowerjvm.flower.evaluation.EvaluationFeedback;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;
import io.github.flowerjvm.flower.evaluation.EvaluationRunner;
import io.github.flowerjvm.flower.evaluation.EvaluationSuite;
import io.github.flowerjvm.flower.evaluation.EvaluationTarget;
import io.github.flowerjvm.flower.evaluation.FeedbackRating;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationFeedbackSink;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationFeedbackSource;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationResultSink;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationResultSource;
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
import java.time.Instant;
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

            Response monitoringPage = request("GET", base + "/monitoring");
            assertThat(monitoringPage.status).isEqualTo(200);
            assertThat(monitoringPage.body).contains("Runtime monitoring");
            assertThat(request("GET", base + "/assets/monitoring.js").status)
                    .isEqualTo(200);
            JsonNode monitoring = json(request("GET", base + "/api/monitoring"));
            assertThat(monitoring.path("overview").path("traceCount").asInt())
                    .isEqualTo(1);
            assertThat(monitoring.path("overview").path("running").asInt())
                    .isEqualTo(1);
            assertThat(monitoring.path("evaluation").path("experimentCount").asInt())
                    .isZero();

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
            assertThat(request("POST", base + "/api/monitoring").status).isEqualTo(405);
            assertThat(request("GET", base + "/api/monitoring/missing").status)
                    .isEqualTo(404);
            assertThat(request("GET", base + "/api/traces/missing").status).isEqualTo(404);
        }
    }

    @Test
    void serves_read_only_evaluation_list_detail_and_comparison() throws Exception {
        Path traceFile = temporaryDirectory.resolve("observations.jsonl");
        Files.write(traceFile, Collections.singletonList(observation()), StandardCharsets.UTF_8);
        Path resultFile = temporaryDirectory.resolve("evaluations.jsonl");
        Path feedbackFile = temporaryDirectory.resolve("feedback.jsonl");
        JsonLinesEvaluationResultSink resultSink = new JsonLinesEvaluationResultSink(resultFile);
        resultSink.publish(evaluation("baseline", null, false));
        resultSink.publish(evaluation("candidate", "baseline", true));
        new JsonLinesEvaluationFeedbackSink(feedbackFile).publish(
                EvaluationFeedback.builder("feedback-1", "candidate", FeedbackRating.POSITIVE)
                        .exampleId("case-1")
                        .createdAt(Instant.parse("2026-08-05T00:00:00Z"))
                        .build());

        try (StudioHttpServer server = new StudioHttpServer(
                new InetSocketAddress("127.0.0.1", 0),
                new JsonLinesObservationRepository(traceFile),
                null,
                new JsonLinesEvaluationResultSource(resultFile, 10),
                new JsonLinesEvaluationFeedbackSource(feedbackFile, 10))) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();

            assertThat(request("GET", base + "/evaluations").body)
                    .contains("Evaluation experiments");
            assertThat(request("GET", base + "/assets/evaluations.js").status)
                    .isEqualTo(200);

            JsonNode list = json(request("GET", base + "/api/evaluations"));
            assertThat(list.path("experiments").size()).isEqualTo(2);
            assertThat(list.path("experiments").get(0).path("experimentId").asText())
                    .isEqualTo("candidate");

            JsonNode detail = json(request("GET", base + "/api/evaluations/candidate"));
            assertThat(detail.path("comparisonStatus").asText()).isEqualTo("AVAILABLE");
            assertThat(detail.path("comparison").path("improvedExampleIds").get(0).asText())
                    .isEqualTo("case-1");
            assertThat(detail.path("feedback").size()).isEqualTo(1);

            JsonNode monitoring = json(request("GET", base + "/api/monitoring"));
            assertThat(monitoring.path("evaluation").path("experimentCount").asInt())
                    .isEqualTo(2);
            assertThat(monitoring.path("evaluation").path("latestExperimentId").asText())
                    .isEqualTo("candidate");
            assertThat(monitoring.path("evaluation").path("improvedExamples").asInt())
                    .isEqualTo(1);
            assertThat(request("POST", base + "/api/evaluations").status).isEqualTo(405);
            assertThat(request("GET", base + "/api/evaluations/missing").status)
                    .isEqualTo(404);
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

    private static io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult evaluation(
            String id,
            String baselineId,
            final boolean actual) throws Exception {
        EvaluationDataset dataset = EvaluationDataset.builder("dataset", "v1")
                .example(EvaluationExample.builder("case-1").expected("ok", true).build())
                .build();
        EvaluationExperiment experiment = EvaluationExperiment.builder(id)
                .baselineExperimentId(baselineId)
                .dataset(dataset)
                .candidate(EvaluationCandidate.builder("agent", id).build())
                .target(new EvaluationTarget() {
                    @Override
                    public EvaluationOutput execute(EvaluationExample example) {
                        return EvaluationOutput.builder()
                                .actual("ok", actual)
                                .traceId("trace-1")
                                .build();
                    }
                })
                .suite(EvaluationSuite.builder()
                        .evaluator(EvaluationEvaluators.expectedEquals("ok"))
                        .build())
                .build();
        return new EvaluationRunner().run(experiment);
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
