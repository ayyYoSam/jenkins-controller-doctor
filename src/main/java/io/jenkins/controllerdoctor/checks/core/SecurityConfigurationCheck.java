package io.jenkins.controllerdoctor.checks.core;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Checks security-related Jenkins controller configuration exposed by the
 * controller API.
 *
 * <p>This check does not attempt to change Jenkins configuration. It is
 * strictly read-only and reports security signals that are available through
 * the controller information model.</p>
 */
public final class SecurityConfigurationCheck implements Check {

    public static final String ID = "core.security-configuration";

    public static final String NAME = "Security configuration";

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

            if (info == null) {
                return buildUnavailableResult(
                        startedAt,
                        "Jenkins controller information was unavailable."
                );
            }

            SecuritySignals signals =
                    inspectSecuritySignals(info.metadata());

            CheckStatus status =
                    determineStatus(signals);

            Instant completedAt = Instant.now();

            CheckResult.Builder result = CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(status)
                    .summary(buildSummary(status, signals))
                    .details(buildDetails(signals))
                    .metadata(
                            "securityEnabled",
                            signals.securityEnabled()
                    )
                    .metadata(
                            "csrfProtectionEnabled",
                            signals.csrfProtectionEnabled()
                    )
                    .metadata(
                            "anonymousReadAccess",
                            signals.anonymousReadAccess()
                    )
                    .metadata(
                            "anonymousOverallAccess",
                            signals.anonymousOverallAccess()
                    )
                    .metadata(
                            "warnings",
                            signals.warnings()
                    )
                    .metadata(
                            "checksEvaluated",
                            signals.checksEvaluated()
                    )
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .duration(
                            Duration.between(startedAt, completedAt)
                    );

            String recommendation =
                    buildRecommendation(signals);

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
                    .summary(
                            "Unable to inspect Jenkins security configuration"
                    )
                    .details(
                            exception.getMessage() != null
                                    ? exception.getMessage()
                                    : "The Jenkins controller configuration "
                                            + "could not be retrieved."
                    )
                    .recommendation(
                            "Verify controller connectivity and credentials "
                                    + "before evaluating security configuration."
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

    private static SecuritySignals inspectSecuritySignals(
            Map<String, Object> metadata
    ) {
        if (metadata == null || metadata.isEmpty()) {
            return SecuritySignals.notEvaluated();
        }

        Boolean securityEnabled = findBoolean(
                metadata,
                "securityEnabled",
                "useSecurity",
                "security"
        );

        Boolean csrfProtectionEnabled = findBoolean(
                metadata,
                "csrfProtectionEnabled",
                "csrfEnabled",
                "crumbIssuerEnabled"
        );

        Boolean anonymousReadAccess = findBoolean(
                metadata,
                "anonymousReadAccess",
                "anonymousCanRead",
                "anonymousRead"
        );

        Boolean anonymousOverallAccess = findBoolean(
                metadata,
                "anonymousOverallAccess",
                "anonymousAccess",
                "anonymousAllowed"
        );

        List<String> warnings = new ArrayList<>();

        if (Boolean.FALSE.equals(securityEnabled)) {
            warnings.add(
                    "Jenkins security is disabled."
            );
        }

        if (Boolean.FALSE.equals(csrfProtectionEnabled)) {
            warnings.add(
                    "CSRF protection is disabled."
            );
        }

        if (Boolean.TRUE.equals(anonymousOverallAccess)) {
            warnings.add(
                    "Anonymous users appear to have overall access."
            );
        }

        if (Boolean.TRUE.equals(anonymousReadAccess)) {
            warnings.add(
                    "Anonymous users appear to have read access."
            );
        }

        int evaluated = 0;

        if (securityEnabled != null) {
            evaluated++;
        }

        if (csrfProtectionEnabled != null) {
            evaluated++;
        }

        if (anonymousReadAccess != null) {
            evaluated++;
        }

        if (anonymousOverallAccess != null) {
            evaluated++;
        }

        return new SecuritySignals(
                securityEnabled,
                csrfProtectionEnabled,
                anonymousReadAccess,
                anonymousOverallAccess,
                warnings,
                evaluated
        );
    }

    private static CheckStatus determineStatus(
            SecuritySignals signals
    ) {
        if (Boolean.FALSE.equals(signals.securityEnabled())) {
            return CheckStatus.CRITICAL;
        }

        if (Boolean.TRUE.equals(signals.anonymousOverallAccess())) {
            return CheckStatus.CRITICAL;
        }

        if (Boolean.FALSE.equals(signals.csrfProtectionEnabled())) {
            return CheckStatus.WARNING;
        }

        if (Boolean.TRUE.equals(signals.anonymousReadAccess())) {
            return CheckStatus.WARNING;
        }

        if (signals.checksEvaluated() == 0) {
            return CheckStatus.INFO;
        }

        return CheckStatus.PASS;
    }

    private static String buildSummary(
            CheckStatus status,
            SecuritySignals signals
    ) {
        return switch (status) {
            case PASS ->
                    "Jenkins security configuration passed the evaluated checks";

            case WARNING ->
                    "Jenkins security configuration requires attention";

            case CRITICAL ->
                    "Jenkins security configuration contains a critical issue";

            case INFO ->
                    "Jenkins security configuration could not be fully evaluated";
        };
    }

    private static String buildDetails(
            SecuritySignals signals
    ) {
        if (signals.checksEvaluated() == 0) {
            return "No recognized security configuration metadata was "
                    + "available from the Jenkins controller information.";
        }

        return String.format(
                Locale.ROOT,
                "Evaluated %d security configuration signals. "
                        + "Security enabled: %s. "
                        + "CSRF protection enabled: %s. "
                        + "Anonymous read access: %s. "
                        + "Anonymous overall access: %s.",
                signals.checksEvaluated(),
                formatBoolean(signals.securityEnabled()),
                formatBoolean(signals.csrfProtectionEnabled()),
                formatBoolean(signals.anonymousReadAccess()),
                formatBoolean(signals.anonymousOverallAccess())
        );
    }

    private static String buildRecommendation(
            SecuritySignals signals
    ) {
        if (signals.warnings().isEmpty()) {
            if (signals.checksEvaluated() == 0) {
                return "Expose Jenkins security configuration information "
                        + "through the client before relying on this check.";
            }

            return null;
        }

        return "Review Jenkins global security configuration and ensure "
                + "authentication, authorization, and CSRF protection are "
                + "configured according to your organization's security policy.";
    }

    private static CheckResult buildUnavailableResult(
            Instant startedAt,
            String details
    ) {
        Instant completedAt = Instant.now();

        return CheckResult.builder()
                .checkId(ID)
                .checkName(NAME)
                .status(CheckStatus.INFO)
                .summary(
                        "Jenkins security configuration could not be evaluated"
                )
                .details(details)
                .recommendation(
                        "Ensure the Jenkins API exposes the security "
                                + "configuration required by this check."
                )
                .metadata("checksEvaluated", 0)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .duration(
                        Duration.between(startedAt, completedAt)
                )
                .build();
    }

    private static Boolean findBoolean(
            Map<String, Object> metadata,
            String... keys
    ) {
        for (String key : keys) {
            Object value = metadata.get(key);

            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }

            if (value instanceof String text) {
                String normalized = text.trim()
                        .toLowerCase(Locale.ROOT);

                if ("true".equals(normalized)) {
                    return true;
                }

                if ("false".equals(normalized)) {
                    return false;
                }
            }
        }

        return null;
    }

    private static String formatBoolean(Boolean value) {
        if (value == null) {
            return "not available";
        }

        return value.toString();
    }

    private record SecuritySignals(
            Boolean securityEnabled,
            Boolean csrfProtectionEnabled,
            Boolean anonymousReadAccess,
            Boolean anonymousOverallAccess,
            List<String> warnings,
            int checksEvaluated
    ) {

        private SecuritySignals {
            warnings = List.copyOf(warnings);
        }

        private static SecuritySignals notEvaluated() {
            return new SecuritySignals(
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    0
            );
        }
    }
}