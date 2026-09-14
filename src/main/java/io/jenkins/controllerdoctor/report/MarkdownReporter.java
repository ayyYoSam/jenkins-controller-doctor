package io.jenkins.controllerdoctor.report;

import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.model.HealthReport;

import java.time.Duration;
import java.util.Map;

/**
 * Renders a {@link HealthReport} as Markdown.
 *
 * <p>The generated document is intended for GitHub pull requests, issue
 * comments, CI artifacts, documentation, and other environments where a
 * structured human-readable report is useful.</p>
 */
public final class MarkdownReporter implements ReportGenerator {

    private static final String FORMAT = "markdown";

    @Override
    public String generate(HealthReport report) {
        validate(report);

        StringBuilder output = new StringBuilder(4096);

        appendTitle(output);
        appendControllerInformation(output, report);
        appendSummary(output, report);
        appendChecks(output, report);

        return output.toString();
    }

    @Override
    public String format() {
        return FORMAT;
    }

    private void appendTitle(StringBuilder output) {
        output.append("# Jenkins Controller Doctor")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append(
                "Automated health report for a Jenkins controller."
        ).append(System.lineSeparator())
          .append(System.lineSeparator());
    }

    private void appendControllerInformation(
            StringBuilder output,
            HealthReport report
    ) {
        output.append("## Controller")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append("| Property | Value |")
                .append(System.lineSeparator());
        output.append("|---|---|")
                .append(System.lineSeparator());

        if (report.jenkinsInfo() != null) {
            appendTableRow(
                    output,
                    "Jenkins version",
                    valueOrUnknown(
                            report.jenkinsInfo().version()
                    )
            );

            appendTableRow(
                    output,
                    "Jenkins URL",
                    valueOrUnknown(
                            report.jenkinsInfo().url()
                    )
            );
        } else {
            appendTableRow(
                    output,
                    "Jenkins",
                    "unknown"
            );
        }

        appendTableRow(
                output,
                "Generated",
                valueOrUnknown(
                        report.completedAt() == null
                                ? null
                                : report.completedAt().toString()
                )
        );

        appendTableRow(
                output,
                "Duration",
                formatDuration(report.duration())
        );

        output.append(System.lineSeparator());
    }

    private void appendSummary(
            StringBuilder output,
            HealthReport report
    ) {
        output.append("## Summary")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append("**Overall status:** ")
                .append(formatStatus(report.overallStatus()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append("| Status | Count |")
                .append(System.lineSeparator());
        output.append("|---|---:|")
                .append(System.lineSeparator());

        appendTableRow(
                output,
                "PASS",
                String.valueOf(report.count(CheckStatus.PASS))
        );

        appendTableRow(
                output,
                "INFO",
                String.valueOf(report.count(CheckStatus.INFO))
        );

        appendTableRow(
                output,
                "WARNING",
                String.valueOf(report.count(CheckStatus.WARNING))
        );

        appendTableRow(
                output,
                "CRITICAL",
                String.valueOf(report.count(CheckStatus.CRITICAL))
        );

        output.append(System.lineSeparator());
    }

    private void appendChecks(
            StringBuilder output,
            HealthReport report
    ) {
        output.append("## Checks")
                .append(System.lineSeparator())
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
        output.append("### ")
                .append(formatStatus(result.status()))
                .append(" ")
                .append(escapeMarkdown(result.checkName()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append("**Check ID:** `")
                .append(escapeCode(result.checkId()))
                .append("`")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append("**Summary:** ")
                .append(formatText(result.summary()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        if (hasText(result.details())) {
            output.append("**Details**")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());

            appendMultilineText(
                    output,
                    result.details()
            );

            output.append(System.lineSeparator());
        }

        if (hasText(result.recommendation())) {
            output.append("**Recommendation**")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());

            appendMultilineText(
                    output,
                    result.recommendation()
            );

            output.append(System.lineSeparator());
        }

        output.append("**Duration:** ")
                .append(formatDuration(result.duration()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        if (!result.metadata().isEmpty()) {
            appendMetadata(output, result.metadata());
        }

        output.append("---")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private void appendMetadata(
            StringBuilder output,
            Map<String, Object> metadata
    ) {
        output.append("<details>")
                .append(System.lineSeparator())
                .append("<summary>Metadata</summary>")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        output.append("| Key | Value |")
                .append(System.lineSeparator());
        output.append("|---|---|")
                .append(System.lineSeparator());

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            appendTableRow(
                    output,
                    entry.getKey(),
                    formatMetadataValue(entry.getValue())
            );
        }

        output.append(System.lineSeparator())
                .append("</details>")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private void appendMultilineText(
            StringBuilder output,
            String text
    ) {
        String normalized = text.trim();

        String[] lines = normalized.split("\\R");

        for (String line : lines) {
            if (line.isBlank()) {
                output.append(System.lineSeparator());
            } else {
                output.append("> ")
                        .append(escapeMarkdown(line))
                        .append(System.lineSeparator());
            }
        }
    }

    private void appendTableRow(
            StringBuilder output,
            String key,
            String value
    ) {
        output.append("| ")
                .append(escapeTableValue(key))
                .append(" | ")
                .append(escapeTableValue(value))
                .append(" |")
                .append(System.lineSeparator());
    }

    private String formatStatus(CheckStatus status) {
        if (status == null) {
            return "⚪ UNKNOWN";
        }

        return switch (status) {
            case PASS -> "🟢 PASS";
            case INFO -> "🔵 INFO";
            case WARNING -> "🟡 WARNING";
            case CRITICAL -> "🔴 CRITICAL";
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

    private String formatMetadataValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();

            builder.append("[");

            boolean first = true;

            for (Object item : iterable) {
                if (!first) {
                    builder.append(", ");
                }

                builder.append(String.valueOf(item));
                first = false;
            }

            builder.append("]");

            return builder.toString();
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

    private String formatText(String value) {
        return hasText(value)
                ? escapeMarkdown(value.trim())
                : "unknown";
    }

    private String escapeMarkdown(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`");
    }

    private String escapeTableValue(String value) {
        if (value == null) {
            return "unknown";
        }

        return escapeMarkdown(value)
                .replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private String escapeCode(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("`", "\\`");
    }

    private String valueOrUnknown(String value) {
        return hasText(value) ? value : "unknown";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}