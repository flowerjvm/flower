package io.github.flowerjvm.flower.studio.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.storage.EvaluationResultSnapshot;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationFeedbackSource;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationResultSource;
import io.github.flowerjvm.flower.studio.store.ObservationRepository;
import io.github.flowerjvm.flower.studio.store.StudioSnapshot;
import io.github.flowerjvm.flower.studio.view.EvaluationExperimentDetailView;
import io.github.flowerjvm.flower.studio.view.EvaluationListView;
import io.github.flowerjvm.flower.studio.view.EvaluationProjectionService;
import io.github.flowerjvm.flower.studio.view.MonitoringDashboardView;
import io.github.flowerjvm.flower.studio.view.MonitoringProjectionService;
import io.github.flowerjvm.flower.studio.view.StudioProjectionService;
import io.github.flowerjvm.flower.studio.view.StudioQuery;
import io.github.flowerjvm.flower.studio.view.TraceDetailView;
import io.github.flowerjvm.flower.studio.view.TraceStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Small read-only HTTP server for the local Flower Studio. */
public final class StudioHttpServer implements AutoCloseable {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; "
                    + "img-src 'self' data:; connect-src 'self'; object-src 'none'; "
                    + "base-uri 'none'; frame-ancestors 'none'";

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObservationRepository repository;
    private final StudioProjectionService projections;
    private final EvaluationProjectionService evaluations;
    private final JsonLinesEvaluationResultSource evaluationSource;
    private final MonitoringProjectionService monitoring;
    private final ObjectMapper mapper;
    private final Path artifactRoot;

    public StudioHttpServer(
            InetSocketAddress address,
            ObservationRepository repository,
            Path artifactRoot) throws IOException {
        this(address, repository, artifactRoot, null, null);
    }

    public StudioHttpServer(
            InetSocketAddress address,
            ObservationRepository repository,
            Path artifactRoot,
            JsonLinesEvaluationResultSource evaluationSource,
            JsonLinesEvaluationFeedbackSource feedbackSource) throws IOException {
        if (address == null || repository == null) {
            throw new IllegalArgumentException("address and repository must not be null");
        }
        if ((evaluationSource == null) != (feedbackSource == null)) {
            throw new IllegalArgumentException(
                    "evaluation result and feedback sources must be configured together");
        }
        this.repository = repository;
        this.artifactRoot = artifactRoot == null
                ? null : artifactRoot.toAbsolutePath().normalize();
        this.projections = new StudioProjectionService(this.artifactRoot != null);
        this.evaluations = evaluationSource == null
                ? null : new EvaluationProjectionService(evaluationSource, feedbackSource);
        this.evaluationSource = evaluationSource;
        this.monitoring = new MonitoringProjectionService();
        this.mapper = new ObjectMapper();
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newFixedThreadPool(4, new StudioThreadFactory());
        this.server.setExecutor(executor);
        this.server.createContext("/api/health", new HealthHandler());
        this.server.createContext("/api/traces", new TraceHandler());
        this.server.createContext("/api/evaluations", new EvaluationHandler());
        this.server.createContext("/api/monitoring", new MonitoringHandler());
        this.server.createContext("/api/artifacts", new ArtifactHandler());
        this.server.createContext("/", new StaticHandler());
    }

    private final class MonitoringHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireGet(exchange)) {
                return;
            }
            String rawPath = exchange.getRequestURI().getRawPath();
            if (!"/api/monitoring".equals(rawPath)
                    && !"/api/monitoring/".equals(rawPath)) {
                sendError(exchange, 404, "Monitoring endpoint not found");
                return;
            }
            StudioSnapshot traceSnapshot = repository.load();
            EvaluationResultSnapshot evaluationSnapshot = evaluationSource == null
                    ? null : evaluationSource.load();
            MonitoringDashboardView dashboard = monitoring.project(
                    traceSnapshot,
                    evaluationSnapshot == null
                            ? Collections.<EvaluationExperimentResult>emptyList()
                            : evaluationSnapshot.getExperiments(),
                    evaluationSnapshot == null ? null : evaluationSnapshot.getDiagnostics());
            sendJson(exchange, 200, dashboard);
        }
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireGet(exchange)) {
                return;
            }
            StudioSnapshot snapshot = repository.load();
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            boolean degraded = !snapshot.diagnostics().isTraceFileExists()
                    || snapshot.diagnostics().getMalformedLineCount() > 0L;
            EvaluationListView evaluationList = evaluations == null
                    ? null : evaluations.list();
            if (evaluationList != null) {
                degraded = degraded
                        || evaluationList.getResultDiagnostics().getMalformedCount() > 0L
                        || evaluationList.getFeedbackDiagnostics().getMalformedCount() > 0L;
            }
            String status = degraded ? "DEGRADED" : "UP";
            response.put("status", status);
            response.put("readOnly", true);
            response.put("artifactDownloadsEnabled", artifactRoot != null);
            response.put("artifactRoot", artifactRoot == null ? null : artifactRoot.toString());
            response.put("diagnostics", snapshot.diagnostics());
            response.put("evaluationsEnabled", evaluations != null);
            if (evaluationList != null) {
                response.put("evaluationDiagnostics", evaluationList.getResultDiagnostics());
                response.put("feedbackDiagnostics", evaluationList.getFeedbackDiagnostics());
            }
            sendJson(exchange, 200, response);
        }
    }

    private final class EvaluationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireGet(exchange)) {
                return;
            }
            if (evaluations == null) {
                sendError(exchange, 404, "Evaluation sources are disabled");
                return;
            }
            String rawPath = exchange.getRequestURI().getRawPath();
            if ("/api/evaluations".equals(rawPath)
                    || "/api/evaluations/".equals(rawPath)) {
                sendJson(exchange, 200, evaluations.list());
                return;
            }
            String prefix = "/api/evaluations/";
            if (!rawPath.startsWith(prefix) || rawPath.length() == prefix.length()) {
                sendError(exchange, 404, "Evaluation endpoint not found");
                return;
            }
            String experimentId = decode(rawPath.substring(prefix.length()));
            EvaluationExperimentDetailView detail = evaluations.detail(experimentId);
            if (detail == null) {
                sendError(exchange, 404, "Evaluation experiment not found");
                return;
            }
            sendJson(exchange, 200, detail);
        }
    }

    private final class TraceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireGet(exchange)) {
                return;
            }
            String rawPath = exchange.getRequestURI().getRawPath();
            StudioSnapshot snapshot = repository.load();
            if ("/api/traces".equals(rawPath) || "/api/traces/".equals(rawPath)) {
                try {
                    sendJson(exchange, 200, projections.list(
                            snapshot,
                            query(exchange.getRequestURI())));
                } catch (IllegalArgumentException invalid) {
                    sendError(exchange, 400, invalid.getMessage());
                }
                return;
            }
            String prefix = "/api/traces/";
            if (!rawPath.startsWith(prefix) || rawPath.length() == prefix.length()) {
                sendError(exchange, 404, "Trace endpoint not found");
                return;
            }
            String traceId = decode(rawPath.substring(prefix.length()));
            TraceDetailView detail = projections.detail(snapshot, traceId);
            if (detail == null) {
                sendError(exchange, 404, "Trace not found");
                return;
            }
            sendJson(exchange, 200, detail);
        }
    }

    private final class ArtifactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!requireGet(exchange)) {
                return;
            }
            if (artifactRoot == null) {
                sendError(exchange, 404, "Artifact downloads are disabled");
                return;
            }
            String location = queryParameters(exchange.getRequestURI()).get("location");
            Path target = resolveArtifact(location);
            if (target == null || !Files.isRegularFile(target)) {
                sendError(exchange, 404, "Artifact not found");
                return;
            }
            Headers headers = exchange.getResponseHeaders();
            securityHeaders(headers);
            headers.set("Content-Type", "application/octet-stream");
            headers.set("Content-Disposition", "attachment; filename=\""
                    + safeFilename(target.getFileName().toString()) + "\"");
            headers.set("Cache-Control", "private, no-store");
            long size = Files.size(target);
            exchange.sendResponseHeaders(200, size);
            try (InputStream input = Files.newInputStream(target);
                    OutputStream output = exchange.getResponseBody()) {
                byte[] buffer = new byte[8_192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())
                    && !"HEAD".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String resource;
            String contentType;
            if ("/".equals(path) || "/index.html".equals(path)) {
                resource = "/studio/index.html";
                contentType = "text/html; charset=utf-8";
            } else if ("/evaluations".equals(path)
                    || "/evaluations.html".equals(path)) {
                resource = "/studio/evaluations.html";
                contentType = "text/html; charset=utf-8";
            } else if ("/monitoring".equals(path)
                    || "/monitoring.html".equals(path)) {
                resource = "/studio/monitoring.html";
                contentType = "text/html; charset=utf-8";
            } else if ("/assets/app.css".equals(path)) {
                resource = "/studio/assets/app.css";
                contentType = "text/css; charset=utf-8";
            } else if ("/assets/app.js".equals(path)) {
                resource = "/studio/assets/app.js";
                contentType = "text/javascript; charset=utf-8";
            } else if ("/assets/evaluations.js".equals(path)) {
                resource = "/studio/assets/evaluations.js";
                contentType = "text/javascript; charset=utf-8";
            } else if ("/assets/monitoring.js".equals(path)) {
                resource = "/studio/assets/monitoring.js";
                contentType = "text/javascript; charset=utf-8";
            } else {
                sendError(exchange, 404, "Resource not found");
                return;
            }
            byte[] content = resource(resource);
            Headers headers = exchange.getResponseHeaders();
            securityHeaders(headers);
            headers.set("Content-Type", contentType);
            headers.set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, "HEAD".equals(exchange.getRequestMethod()) ? -1 : content.length);
            if (!"HEAD".equals(exchange.getRequestMethod())) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(content);
                }
            } else {
                exchange.close();
            }
        }
    }

    private StudioQuery query(URI uri) {
        Map<String, String> values = queryParameters(uri);
        TraceStatus status = null;
        String rawStatus = values.get("status");
        if (rawStatus != null && !rawStatus.trim().isEmpty() && !"ALL".equalsIgnoreCase(rawStatus)) {
            try {
                status = TraceStatus.valueOf(rawStatus.trim().toUpperCase());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown trace status");
            }
        }
        int limit = StudioQuery.DEFAULT_LIMIT;
        String rawLimit = values.get("limit");
        if (rawLimit != null && !rawLimit.trim().isEmpty()) {
            try {
                limit = Integer.parseInt(rawLimit);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("limit must be an integer");
            }
        }
        return new StudioQuery(values.get("q"), values.get("source"), status, limit);
    }

    private Path resolveArtifact(String location) {
        if (location == null || location.trim().isEmpty()) {
            return null;
        }
        try {
            Path relative = Paths.get(location).normalize();
            if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) {
                return null;
            }
            Path target = artifactRoot.resolve(relative).normalize();
            if (!target.startsWith(artifactRoot) || !Files.isRegularFile(target)) {
                return null;
            }
            Path realRoot = artifactRoot.toRealPath();
            Path realTarget = target.toRealPath();
            return realTarget.startsWith(realRoot) ? realTarget : null;
        } catch (IOException | RuntimeException invalidPath) {
            return null;
        }
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            return true;
        }
        sendError(exchange, 405, "Method not allowed");
        return false;
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] content = mapper.writeValueAsBytes(value);
        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", status);
        body.put("error", message == null ? "Request failed" : message);
        sendJson(exchange, status, body);
    }

    private static void securityHeaders(Headers headers) {
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = StudioHttpServer.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Studio resource is missing");
            }
            byte[] buffer = new byte[4_096];
            int count;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static Map<String, String> queryParameters(URI uri) {
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String pair : raw.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable", impossible);
        }
    }

    private static String safeFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class StudioThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "flower-studio-http-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }
}
