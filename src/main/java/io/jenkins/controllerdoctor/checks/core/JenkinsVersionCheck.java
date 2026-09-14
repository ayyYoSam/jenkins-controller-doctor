package io.jenkins.controllerdoctor.checks.core;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the Jenkins controller version.
 *
 * <p>The check identifies obviously outdated Jenkins versions and reports
 * version information in structured metadata. It intentionally avoids
 * hard-coding a specific LTS release as a permanent requirement because
 * Jenkins release lines evolve over time.</p>
 */
public final class JenkinsVersionCheck implements Check {

    public static final String ID = "core.jenkins-version";

    public static final String NAME = "Jenkins version";

    /**
     * Versions below this major/minor line are considered legacy.
     *
     * <p>This is deliberately conservative. The check is intended to identify
     * controllers that clearly require modernization rather than becoming
     * stale whenever a new LTS release is published.</p>
     */
    private static final int LEGACY_MAJOR_VERSION = 2;

    private static final int LEGACY_MINOR_VERSION = 400;

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)(?:[.-].*)?$");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult execute(
            JenkinsClient client,
            JenkinsInfo jenkinsInfo
    ) {
        Objects.requireNonNull(client, "client must not be null");

        Instant startedAt = Instant.now();

        try {
            JenkinsInfo info =
                    jenkinsInfo != null
                            ? jenkinsInfo
                            : client.getJenkinsInfo();

            if (info == null
                    || info.version() == null
                    || info.version().isBlank()) {
                return buildUnavailableResult(
                        startedAt,
                        "Jenkins controller version was not available."
                );
            }

            String version = info.version().trim();

            CheckStatus status = determineStatus(version);

            Instant completedAt = Instant.now();

            CheckResult.Builder result = CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(status)
                    .summary(buildSummary(version, status))
                    .metadata("version", version)
                    .metadata(
                            "legacyThreshold",
                            LEGACY_MAJOR_VERSION
                                    + "."
                                    + LEGACY_MINOR_VERSION
                    )
                    .metadata(
                            "versionFormatRecognized",
                            parseVersion(version) != null
                    )
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .duration(
                            Duration.between(startedAt, completedAt)
                    );

            String recommendation =
                    buildRecommendation(version, status);

            if (recommendation != null) {
                result.recommendation(recommendation);
            }

            return result.build();

        } catch (JenkinsClientException exception) {
            Instant completedAt = Instant.now();

            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary("Unable to determine Jenkins version")
                    .details(
                            exception.getMessage() != null
                                    ? exception.getMessage()
                                    : "The Jenkins controller information "
                                            + "could not be retrieved."
                    )
                    .recommendation(
                            "Verify connectivity and credentials, then "
                                    + "retry the controller health check."
                    )
                    .metadata(
                            "errorReason",
                            exception.reason().name()
                    )
                    .metadata(
                            "httpStatus",
                            exception.statusCode()
                    )
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .duration(
                            Duration.between(startedAt, completedAt)
                    )
                    .build();
        }
    }

    private static CheckStatus determineStatus(String version) {
        Version parsed = parseVersion(version);

        if (parsed == null) {
            return CheckStatus.INFO;
        }

        if (parsed.major() < LEGACY_MAJOR_VERSION) {
            return CheckStatus.CRITICAL;
        }

        if (parsed.major() == LEGACY_MAJOR_VERSION
                && parsed.minor() < LEGACY_MINOR_VERSION) {
            return CheckStatus.WARNING;
        }

        return CheckStatus.PASS;
    }

    private static String buildSummary(
            String version,
            CheckStatus status
    ) {
        return switch (status) {
            case PASS ->
                    "Jenkins version " + version + " is within the expected baseline";

            case WARNING ->
                    "Jenkins version " + version + " is on an old release line";

            case CRITICAL ->
                    "Jenkins version " + version + " is significantly outdated";

            case INFO ->
                    "Jenkins version " + version + " was detected";
        };
    }

    private static String buildRecommendation(
            String version,
            CheckStatus status
    ) {
        return switch (status) {
            case PASS, INFO ->
                    null;

            case WARNING ->
                    "Plan an upgrade to a currently supported Jenkins LTS "
                            + "release and review plugin compatibility first.";

            case CRITICAL ->
                    "Upgrade Jenkins to a currently supported LTS release "
                            + "as soon as practical. Review plugins and "
                            + "configuration compatibility before upgrading.";
        };
    }

    private static CheckResult buildUnavailableResult(
            Instant startedAt,
            String details
    ) {
        Instant completedAt = Instant.now();

        return CheckResult.builder()
                .checkId(ID)
                .checkName(NAME)
                .status(CheckStatus.CRITICAL)
                .summary("Jenkins version is unavailable")
                .details(details)
                .recommendation(
                        "Verify that the Jenkins controller API is reachable "
                                + "and that the configured credentials are valid."
                )
                .startedAt(startedAt)
                .completedAt(completedAt)
                .duration(
                        Duration.between(startedAt, completedAt)
                )
                .build();
    }

    private static Version parseVersion(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value);

        if (!matcher.matches()) {
            return null;
        }

        try {
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record Version(
            int major,
            int minor
    ) {
    }
}