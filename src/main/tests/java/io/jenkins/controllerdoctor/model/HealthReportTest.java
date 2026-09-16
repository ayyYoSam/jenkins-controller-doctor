package io.jenkins.controllerdoctor.model;

import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HealthReportTest {

    @Test
    void shouldCalculateOverallStatus() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:00:10Z");

        HealthReport report = HealthReport.builder()
                .startedAt(started)
                .completedAt(completed)
                .addResult(result("version", CheckStatus.PASS))
                .addResult(result("plugins", CheckStatus.WARNING))
                .addResult(result("queue", CheckStatus.CRITICAL))
                .build();

        assertEquals(CheckStatus.CRITICAL, report.overallStatus());
    }

    @Test
    void shouldCountResultsByStatus() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:00:10Z");

        HealthReport report = HealthReport.builder()
                .startedAt(started)
                .completedAt(completed)
                .addResult(result("one", CheckStatus.PASS))
                .addResult(result("two", CheckStatus.WARNING))
                .addResult(result("three", CheckStatus.WARNING))
                .build();

        assertEquals(1, report.count(CheckStatus.PASS));
        assertEquals(2, report.count(CheckStatus.WARNING));
        assertEquals(0, report.count(CheckStatus.CRITICAL));
    }

    @Test
    void shouldDetectHealthyReport() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:00:05Z");

        HealthReport report = HealthReport.builder()
                .startedAt(started)
                .completedAt(completed)
                .addResult(result("version", CheckStatus.PASS))
                .addResult(result("info", CheckStatus.INFO))
                .build();

        assertTrue(report.isHealthy());
        assertFalse(report.hasWarnings());
        assertFalse(report.hasCriticalResults());
    }

    @Test
    void shouldCalculateDuration() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:00:15Z");

        HealthReport report = HealthReport.builder()
                .startedAt(started)
                .completedAt(completed)
                .build();

        assertEquals(15, report.duration().getSeconds());
    }

    private CheckResult result(
            String id,
            CheckStatus status
    ) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        return CheckResult.builder()
                .checkId(id)
                .checkName(id)
                .status(status)
                .summary("test")
                .startedAt(now)
                .completedAt(now)
                .build();
    }
}