package io.jenkins.controllerdoctor.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.model.HealthReport;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a {@link HealthReport} as structured JSON.
 *
 * <p>The generated JSON is intended for automation, CI/CD pipelines,
 * monitoring systems, log processors, and other tools that need to consume
 * Jenkins controller health information programmatically.</p>
 *
 * <p>The reporter deliberately constructs the JSON representation instead of
 * serializing the domain model directly. This keeps the external report
 * format stable even if the internal model evolves.</p>
 */
public final class JsonReporter implements ReportGenerator {

    private static final String FORMAT = "json";

    private final ObjectMapper objectMapper;

    /**
     * Creates a JSON reporter using a new default Jackson mapper.
     */
    public JsonReporter() {
        this(new ObjectMapper());
    }

    /**
     * Creates a JSON reporter using the supplied Jackson mapper.
     *
     * <p>Injecting the mapper makes the reporter easier to configure and test
     * while keeping the default constructor convenient for the CLI.</p>
     *
     * @param objectMapper mapper used to serialize the generated JSON
     */
    public JsonReporter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    @Override
    public String generate(HealthReport report) {
        validate(report);

        ObjectNode root = buildReportNode(report);

        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to generate JSON health report",
                    exception
            );
        }
    }

    @Override
    public String format() {
        return FORMAT;
    }

    private ObjectNode buildReportNode(HealthReport report) {
        ObjectNode root = objectMapper.createObjectNode();

        root.put("reportVersion", "1");
        root.put("generatedAt", report.completedAt().toString());

        if (report.startedAt() != null) {
            root.put("startedAt", report.startedAt().toString());
        }

        if (report.completedAt() != null) {
            root.put("completedAt", report.completedAt().toString());
        }

        root.put(
                "durationMillis",
                durationMillis(report.duration())
        );

        if (report.overallStatus() != null) {
            root.put(
                    "overallStatus",
                    report.overallStatus().name()
            );
        } else {
            root.putNull("overallStatus");
        }

        appendJenkinsInfo(root, report);
        appendSummary(root, report);
        appendResults(root, report);

        return root;
    }

    private void appendJenkinsInfo(
            ObjectNode root,
            HealthReport report
    ) {
        if (report.jenkinsInfo() == null) {
            root.putNull("jenkins");
            return;
        }

        ObjectNode jenkins = root.putObject("jenkins");

        if (report.jenkinsInfo().version() != null) {
            jenkins.put(
                    "version",
                    report.jenkinsInfo().version()
            );
        }

        if (report.jenkinsInfo().url() != null) {
            jenkins.put(
                    "url",
                    report.jenkinsInfo().url()
            );
        }

        if (report.jenkinsInfo().metadata() != null
                && !report.jenkinsInfo().metadata().isEmpty()) {

            jenkins.set(
                    "metadata",
                    objectMapper.valueToTree(
                            report.jenkinsInfo().metadata()
                    )
            );
        }
    }

    private void appendSummary(
            ObjectNode root,
            HealthReport report
    ) {
        ObjectNode summary = root.putObject("summary");

        summary.put(
                "totalChecks",
                report.results().size()
        );

        summary.put(
                "passed",
                report.count(
                        io.jenkins.controllerdoctor.checks.CheckStatus.PASS
                )
        );

        summary.put(
                "informational",
                report.count(
                        io.jenkins.controllerdoctor.checks.CheckStatus.INFO
                )
        );

        summary.put(
                "warnings",
                report.count(
                        io.jenkins.controllerdoctor.checks.CheckStatus.WARNING
                )
        );

        summary.put(
                "critical",
                report.count(
                        io.jenkins.controllerdoctor.checks.CheckStatus.CRITICAL
                )
        );
    }

    private void appendResults(
            ObjectNode root,
            HealthReport report
    ) {
        ArrayNode checks = root.putArray("checks");

        for (CheckResult result : report.results()) {
            checks.add(buildCheckNode(result));
        }
    }

    private ObjectNode buildCheckNode(CheckResult result) {
        ObjectNode check = objectMapper.createObjectNode();

        check.put("id", result.checkId());
        check.put("name", result.checkName());

        if (result.status() != null) {
            check.put("status", result.status().name());
        } else {
            check.putNull("status");
        }

        if (result.summary() != null) {
            check.put("summary", result.summary());
        } else {
            check.putNull("summary");
        }

        if (result.details() != null) {
            check.put("details", result.details());
        } else {
            check.putNull("details");
        }

        if (result.recommendation() != null) {
            check.put(
                    "recommendation",
                    result.recommendation()
            );
        } else {
            check.putNull("recommendation");
        }

        if (result.startedAt() != null) {
            check.put(
                    "startedAt",
                    result.startedAt().toString()
            );
        }

        if (result.completedAt() != null) {
            check.put(
                    "completedAt",
                    result.completedAt().toString()
            );
        }

        check.put(
                "durationMillis",
                durationMillis(result.duration())
        );

        appendMetadata(check, result.metadata());

        return check;
    }

    private void appendMetadata(
            ObjectNode check,
            Map<String, Object> metadata
    ) {
        if (metadata == null || metadata.isEmpty()) {
            check.putObject("metadata");
            return;
        }

        check.set(
                "metadata",
                objectMapper.valueToTree(metadata)
        );
    }

    private long durationMillis(Duration duration) {
        if (duration == null) {
            return 0L;
        }

        return duration.toMillis();
    }
}