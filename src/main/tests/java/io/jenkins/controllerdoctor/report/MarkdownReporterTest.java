package io.jenkins.controllerdoctor.report;

import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.model.HealthReport;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownReporterTest {

    @Test
    void shouldGenerateMarkdownReport() {
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

        MarkdownReporter reporter = new MarkdownReporter();

        String output = reporter.generate(report);

        assertNotNull(output);
        assertTrue(output.contains("# Jenkins Controller Doctor"));
        assertTrue(output.contains("## Summary"));
        assertTrue(output.contains("PASS"));
        assertTrue(output.contains("2.516.1"));
        assertTrue(output.contains("jenkins.version"));
    }

    @Test
    void shouldExposeFormat() {
        MarkdownReporter reporter = new MarkdownReporter();

        assertEquals("markdown", reporter.format());
    }
}