package io.jenkins.controllerdoctor.report;

import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.model.HealthReport;

import java.time.Duration;
import java.util.Map;

/**
 * Renders a {@link HealthReport} as human-readable console output.
 *
 * <p>The console representation is intended for interactive use by Jenkins
 * administrators and developers. It deliberately avoids exposing internal
 * implementation details while retaining enough information to diagnose
 * unhealthy controller conditions.</p>
 */
public final class ConsoleReporter implements ReportGenerator {

    private static final String FORMAT = "console";

    private static final String HEADER =
            "============================================================";

    private static final String SECTION =
            "------------------------------------------------------------";

    @Override
    public String generate(HealthReport report) {
        validate(report);

        StringBuilder output = new StringBuilder(2048);

        appendHeader(output, report);
        appendSummary(output, report);
        appendChecks(output, report);
        appendFooter(output, report);

        return output.toString();
    }

    @Override
    public String format() {
        return FORMAT;
    }

    private void appendHeader(
            StringBuilder output,
            HealthReport report
    ) {
        output.append(HEADER).append(System.lineSeparator());
        output.append(" Jenkins Controller Doctor")
                .append(System.lineSeparator());
        output.append(HEADER).append(System.lineSeparator());

        if (report.jenkinsInfo() != null) {
            output.append("Jenkins: ")
                    .append(valueOrUnknown(report.jenkinsInfo().version()))
                    .append(System.lineSeparator());
        }

        output.append("Generated: ")
                .append(report.completedAt())
                .append(System.lineSeparator());

        output.append(System.lineSeparator());
    }

    private void appendSummary(
            StringBuilder output,
            HealthReport report
    ) {
        output.append("SUMMARY")
                .append(System.lineSeparator());
        output.append(SECTION)
                .append(System.lineSeparator());

        output.append("Overall status: ")
                .append(formatStatus(report.overallStatus()))
                .append(System.lineSeparator());

        output.append("Checks: ")
                .append(report.results().size())
                .append(System.lineSeparator());

        output.append("Passed: ")
                .append(report.count(CheckStatus.PASS))
                .append(System.lineSeparator());

        output.append("Informational: ")
                .append(report.count(CheckStatus.INFO))
                .append(System.lineSeparator());

        output.append("Warnings: ")
                .append(report.count(CheckStatus.WARNING))
                .append(System.lineSeparator());

        output.append("Critical: ")
                .append(report.count(CheckStatus.CRITICAL))
                .append(System.lineSeparator());

        output.append("Duration: ")
                .append(formatDuration(report.duration()))
                .append(System.lineSeparator());

        output.append(System.lineSeparator());
    }

    private void appendChecks(
            StringBuilder output,
            HealthReport report
    ) {
        output.append("CHECKS")
                .append(System.lineSeparator());
        output.append(SECTION)
                .append(System.lineSeparator());

        if (report.results().isEmpty()) {
            output.append("No health checks were executed.")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            return;
        }

        for (CheckResult result : report.results()) {
            appendCheck(output, result);
        }
    }

    private void appendCheck(
            StringBuilder output,
            CheckResult result
    ) {
        output.append(formatStatus(result.status()))
                .append(" ")
                .append(result.checkName())
                .append(" [")
                .append(result.checkId())
                .append("]")
                .append(System.lineSeparator());

        output.append("  Summary: ")
                .append(valueOrUnknown(result.summary()))
                .append(System.lineSeparator());

        output.append("  Duration: ")
                .append(formatDuration(result.duration()))
                .append(System.lineSeparator());

        if (hasText(result.details())) {
            appendMultilineValue(
                    output,
                    "Details",
                    result.details()
            );
        }

        if (hasText(result.recommendation())) {
            appendMultilineValue(
                    output,
                    "Recommendation",
                    result.recommendation()
            );
        }

        if (!result.metadata().isEmpty()) {
            appendMetadata(output, result.metadata());
        }

        output.append(System.lineSeparator());
    }

    private void appendMetadata(
            StringBuilder output,
            Map<String, Object> metadata
    ) {
        output.append("  Metadata:")
                .append(System.lineSeparator());

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            output.append("    ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(formatValue(entry.getValue()))
                    .append(System.lineSeparator());
        }
    }

    private void appendMultilineValue(
            StringBuilder output,
            String label,
            String value
    ) {
        String normalized = value.trim();

        if (!normalized.contains(System.lineSeparator())
                && !normalized.contains("\n")
                && !normalized.contains("\r")) {

            output.append("  ")
                    .append(label)
                    .append(": ")
                    .append(normalized)
                    .append(System.lineSeparator());

            return;
        }

        output.append("  ")
                .append(label)
                .append(":")
                .append(System.lineSeparator());

        String[] lines = normalized.split("\\R");

        for (String line : lines) {
            output.append("    ")
                    .append(line)
                    .append(System.lineSeparator());
        }
    }

    private void appendFooter(
            StringBuilder output,
            HealthReport report
    ) {
        output.append(HEADER)
                .append(System.lineSeparator());

        output.append("Result: ")
                .append(formatStatus(report.overallStatus()))
                .append(System.lineSeparator());

        output.append(HEADER)
                .append(System.lineSeparator());
    }

    private String formatStatus(CheckStatus status) {
        if (status == null) {
            return "[UNKNOWN]";
        }

        return switch (status) {
            case PASS -> "[PASS]";
            case INFO -> "[INFO]";
            case WARNING -> "[WARNING]";
            case CRITICAL -> "[CRITICAL]";
        };
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "unknown";
        }

        long millis = duration.toMillis();

        if (millis < 1_000) {
            return millis + " ms";
        }

        long seconds = millis / 1_000;

        if (seconds < 60) {
            return seconds + " s";
        }

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        return minutes + " min " + remainingSeconds + " s";
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;

            for (Object item : iterable) {
                if (!first) {
                    builder.append(", ");
                }

                builder.append(String.valueOf(item));
                first = false;
            }

            return builder.append("]").toString();
        }

        if (value.getClass().isArray()) {
            return formatArray(value);
        }

        return String.valueOf(value);
    }

    private String formatArray(Object value) {
        int length = java.lang.reflect.Array.getLength(value);

        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                builder.append(", ");
            }

            builder.append(
                    String.valueOf(
                            java.lang.reflect.Array.get(value, index)
                    )
            );
        }

        return builder.append("]").toString();
    }

    private String valueOrUnknown(String value) {
        return hasText(value) ? value : "unknown";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}