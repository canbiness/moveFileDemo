package com.example.filetransfer.service;

import com.example.filetransfer.config.TransferStorageHealthIndicator;
import com.example.filetransfer.domain.ThrottleReason;
import com.example.filetransfer.domain.TransferStatus;
import com.example.filetransfer.domain.VerificationMode;
import com.example.filetransfer.dto.CreateScalableTransferPlanRequest;
import com.example.filetransfer.dto.ScalableTransferActionResponse;
import com.example.filetransfer.dto.ScalableTransferMetricsResponse;
import com.example.filetransfer.dto.ScalableTransferPlanResponse;
import com.example.filetransfer.dto.ScalableTransferTaskSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScalableTransferExecutionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransferStorageHealthIndicator transferStorageHealthIndicator;

    @TempDir
    Path tempDir;

    @Test
    void shouldPlanAndExecuteFilesEndToEnd() throws Exception {
        Path source = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Files.createDirectories(source.resolve("nested"));
        Files.createDirectories(target);
        Files.writeString(source.resolve("hello.txt"), "hello");
        Files.writeString(source.resolve("nested").resolve("world.txt"), "world");

        ScalableTransferPlanResponse planned = createPlan(source, target);
        assertEquals(TransferStatus.PLANNING, planned.status());
        assertEquals(TransferStatus.PLANNED, waitForPlanned(planned.taskId()).status());

        ResponseEntity<ScalableTransferActionResponse> executeResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/execute"),
                null,
                ScalableTransferActionResponse.class
        );
        assertEquals(202, executeResponse.getStatusCode().value());

        ScalableTransferTaskSummaryResponse summary = waitForCompletion(planned.taskId());
        assertNotNull(summary);
        assertEquals(TransferStatus.COMPLETED, summary.status());
        assertEquals(2L, summary.totalFiles());
        assertTrue(Files.exists(target.resolve("hello.txt")));
        assertTrue(Files.exists(target.resolve("nested").resolve("world.txt")));
        assertEquals("hello", Files.readString(target.resolve("hello.txt")));
        assertEquals("world", Files.readString(target.resolve("nested").resolve("world.txt")));
    }

    @Test
    void shouldPauseBeforeExecutionAndResumeSuccessfully() throws Exception {
        Path source = tempDir.resolve("pause-source");
        Path target = tempDir.resolve("pause-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("resume.txt"), "resume-me");

        ScalableTransferPlanResponse planned = createPlan(source, target);
        assertEquals(TransferStatus.PLANNING, planned.status());
        assertEquals(TransferStatus.PLANNED, waitForPlanned(planned.taskId()).status());

        ResponseEntity<ScalableTransferActionResponse> pauseResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/pause"),
                null,
                ScalableTransferActionResponse.class
        );
        assertEquals(202, pauseResponse.getStatusCode().value());

        ScalableTransferTaskSummaryResponse pausedSummary = restTemplate.getForObject(
                baseUrl("/api/transfer/" + planned.taskId()),
                ScalableTransferTaskSummaryResponse.class
        );
        assertNotNull(pausedSummary);
        assertEquals(TransferStatus.PAUSED, pausedSummary.status());

        ResponseEntity<ScalableTransferActionResponse> resumeResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/resume"),
                null,
                ScalableTransferActionResponse.class
        );
        assertEquals(202, resumeResponse.getStatusCode().value());

        ScalableTransferTaskSummaryResponse completedSummary = waitForCompletion(planned.taskId());
        assertNotNull(completedSummary);
        assertEquals(TransferStatus.COMPLETED, completedSummary.status());
        assertEquals("resume-me", Files.readString(target.resolve("resume.txt")));
    }

    @Test
    void shouldExposeRuntimeMetrics() throws Exception {
        Path source = tempDir.resolve("metrics-source");
        Path target = tempDir.resolve("metrics-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("metrics.txt"), "metrics");

        ScalableTransferPlanResponse planned = createPlan(source, target);
        assertEquals(TransferStatus.PLANNING, planned.status());
        assertEquals(TransferStatus.PLANNED, waitForPlanned(planned.taskId()).status());

        ScalableTransferMetricsResponse metrics = restTemplate.getForObject(
                baseUrl("/api/transfer/" + planned.taskId() + "/metrics"),
                ScalableTransferMetricsResponse.class
        );

        assertNotNull(metrics);
        assertEquals(planned.taskId(), metrics.taskId());
        assertEquals(1L, metrics.totalFiles());
        assertEquals(1L, metrics.totalBatches());
        assertNotNull(metrics.batchStatusCounts());
        assertNotNull(metrics.batchTemperatureCounts());
        assertNotNull(metrics.fileStatusCounts());
        assertNotNull(metrics.schedulingStrategy());
        assertNotNull(metrics.inFlightTemperatureCounts());
        assertNotNull(metrics.throttleReasonCounts());
        assertTrue(metrics.throttleReasonCounts().containsKey(ThrottleReason.THREAD_POOL));
        assertNotNull(metrics.transferExecutor());
        assertNotNull(metrics.coordinatorExecutor());
    }

    @Test
    void shouldCancelPlannedTask() throws Exception {
        Path source = tempDir.resolve("cancel-source");
        Path target = tempDir.resolve("cancel-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("cancel.txt"), "cancel-me");

        ScalableTransferPlanResponse planned = createPlan(source, target);
        assertEquals(TransferStatus.PLANNING, planned.status());
        assertEquals(TransferStatus.PLANNED, waitForPlanned(planned.taskId()).status());

        ResponseEntity<ScalableTransferActionResponse> cancelResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/cancel"),
                null,
                ScalableTransferActionResponse.class
        );
        assertEquals(202, cancelResponse.getStatusCode().value());

        ScalableTransferTaskSummaryResponse canceledSummary = restTemplate.getForObject(
                baseUrl("/api/transfer/" + planned.taskId()),
                ScalableTransferTaskSummaryResponse.class
        );
        assertNotNull(canceledSummary);
        assertEquals(TransferStatus.CANCELED, canceledSummary.status());
    }

    @Test
    void shouldRejectPauseAndCancelWhilePlanning() throws Exception {
        Path source = tempDir.resolve("planning-source");
        Path target = tempDir.resolve("planning-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        for (int i = 0; i < 5000; i++) {
            Files.writeString(source.resolve("planning-" + i + ".txt"), "planning-" + i);
        }

        ScalableTransferPlanResponse planned = createPlan(source, target);
        assertEquals(TransferStatus.PLANNING, planned.status());
        assertEquals(TransferStatus.PLANNING, waitForPlanning(planned.taskId()).status());

        ResponseEntity<String> pauseResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/pause"),
                null,
                String.class
        );
        assertEquals(400, pauseResponse.getStatusCode().value());
        assertNotNull(pauseResponse.getBody());

        ResponseEntity<String> cancelResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/cancel"),
                null,
                String.class
        );
        assertEquals(400, cancelResponse.getStatusCode().value());
        assertNotNull(cancelResponse.getBody());
    }

    @Test
    void shouldEchoRequestIdHeader() throws Exception {
        Path source = tempDir.resolve("rid-source");
        Path target = tempDir.resolve("rid-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("rid.txt"), "rid");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "req-demo-123");
        HttpEntity<CreateScalableTransferPlanRequest> entity = new HttpEntity<>(
                new CreateScalableTransferPlanRequest(
                        source.toString(),
                        target.toString(),
                        VerificationMode.SIZE_AND_MTIME
                ),
                headers
        );

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl("/api/transfer"), entity, String.class);
        assertEquals(202, response.getStatusCode().value());
        assertEquals("req-demo-123", response.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void shouldRejectBatchPageSizeOverConfiguredLimit() throws Exception {
        Path source = tempDir.resolve("page-source");
        Path target = tempDir.resolve("page-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("page.txt"), "page");

        ScalableTransferPlanResponse planned = createPlan(source, target);
        assertEquals(TransferStatus.PLANNED, waitForPlanned(planned.taskId()).status());

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl("/api/transfer/" + planned.taskId() + "/batches?page=0&size=1001"),
                String.class
        );
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void shouldExposeActuatorHealth() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void shouldReportHealthyTransferStorage() {
        assertEquals(Status.UP, transferStorageHealthIndicator.health().getStatus());
    }

    @Test
    void shouldExposeHttpServerMetrics() throws Exception {
        Path source = tempDir.resolve("metrics-endpoint-source");
        Path target = tempDir.resolve("metrics-endpoint-target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("metrics-endpoint.txt"), "metrics-endpoint");

        createPlan(source, target);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl("/actuator/metrics/http.server.requests"),
                String.class
        );
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("http.server.requests"));
    }

    @Test
    void shouldIncludeRequestIdInValidationErrorResponse() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "req-invalid-456");
        HttpEntity<CreateScalableTransferPlanRequest> entity = new HttpEntity<>(
                new CreateScalableTransferPlanRequest("", "", null),
                headers
        );

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl("/api/transfer"), entity, String.class);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("req-invalid-456", response.getHeaders().getFirst("X-Request-Id"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"requestId\":\"req-invalid-456\""));
    }

    private ScalableTransferTaskSummaryResponse waitForPlanning(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        ScalableTransferTaskSummaryResponse summary = null;
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<ScalableTransferTaskSummaryResponse> response = restTemplate.getForEntity(
                    baseUrl("/api/transfer/" + taskId),
                    ScalableTransferTaskSummaryResponse.class
            );
            summary = response.getBody();
            if (summary != null && summary.status() == TransferStatus.PLANNING) {
                return summary;
            }
            Thread.sleep(50L);
        }
        return summary;
    }

    private ScalableTransferTaskSummaryResponse waitForPlanned(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        ScalableTransferTaskSummaryResponse summary = null;
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<ScalableTransferTaskSummaryResponse> response = restTemplate.getForEntity(
                    baseUrl("/api/transfer/" + taskId),
                    ScalableTransferTaskSummaryResponse.class
            );
            summary = response.getBody();
            if (summary != null && summary.status() == TransferStatus.PLANNED) {
                return summary;
            }
            if (summary != null && summary.status() == TransferStatus.FAILED) {
                return summary;
            }
            Thread.sleep(200L);
        }
        return summary;
    }

    private ScalableTransferTaskSummaryResponse waitForCompletion(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        ScalableTransferTaskSummaryResponse summary = null;
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<ScalableTransferTaskSummaryResponse> response = restTemplate.getForEntity(
                    baseUrl("/api/transfer/" + taskId),
                    ScalableTransferTaskSummaryResponse.class
            );
            summary = response.getBody();
            if (summary != null && summary.status() == TransferStatus.COMPLETED) {
                return summary;
            }
            if (summary != null && summary.status() == TransferStatus.FAILED) {
                return summary;
            }
            Thread.sleep(200L);
        }
        return summary;
    }

    private ScalableTransferPlanResponse createPlan(Path source, Path target) {
        ResponseEntity<ScalableTransferPlanResponse> planResponse = restTemplate.postForEntity(
                baseUrl("/api/transfer"),
                new CreateScalableTransferPlanRequest(source.toString(), target.toString(), VerificationMode.SIZE_AND_MTIME),
                ScalableTransferPlanResponse.class
        );
        assertEquals(202, planResponse.getStatusCode().value());
        ScalableTransferPlanResponse planned = planResponse.getBody();
        assertNotNull(planned);
        return planned;
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
