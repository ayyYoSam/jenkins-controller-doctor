package io.jenkins.controllerdoctor.report;

import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.model.HealthReport;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JsonReporterTest {

    @Test
    void shouldGenerateJsonReport() {
        Instant started = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = Instant.parse("2026-01-01T00:00:05Z");

        HealthReport report = HealthReport.builder()
                .startedAt(started)
                .completedAt(completed)
                .jenkinsInfo(
                        JenkinsInfo.builder()
                                .displayName("Jenkins")
                                .version("2.516.1")
                                .url("http://localhost:8080")
                                .build()
                )
                .addResult(
                        CheckResult.builder()
                                .checkId("jenkins.version")
                                .checkName("Jenkins Version")
                                .status(CheckStatus.PASS)
                                .summary("Version is supported.")
                                .startedAt(started)
                                .completedAt(completed)
                                .build()
                )
                .build();

        JsonReporter reporter = new JsonReporter();

        String output = reporter.generate(report);

        assertNotNull(output);
        assertTrue(output.contains("\"overallStatus\""));
        assertTrue(output.contains("PASS"));
        assertTrue(output.contains("2.516.1"));
        assertTrue(output.contains("jenkins.version"));
    }

    @Test
    void shouldExposeFormat() {
        JsonReporter reporter = new JsonReporter();

        assertEquals("json", reporter.format());
    }
}