package io.jenkins.controllerdoctor.model;

import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete result of a Jenkins Controller Doctor execution.
 */
public final class HealthReport {

    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration duration;
    private final JenkinsInfo jenkinsInfo;
    private final List<CheckResult> results;

    private HealthReport(Builder builder) {
        this.startedAt = Objects.requireNonNull(
                builder.startedAt,
                "startedAt must not be null"
        );

        this.completedAt = Objects.requireNonNull(
                builder.completedAt,
                "completedAt must not be null"
        );

        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "completedAt must not be before startedAt"
            );
        }

        this.duration = Duration.between(startedAt, completedAt);
        this.jenkinsInfo = builder.jenkinsInfo;

        this.results = Collections.unmodifiableList(
                new ArrayList<>(builder.results)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Duration duration() {
        return duration;
    }

    public JenkinsInfo jenkinsInfo() {
        return jenkinsInfo;
    }

    public List<CheckResult> results() {
        return results;
    }

    /**
     * Returns the most severe status produced by all checks.
     *
     * @return overall report status
     */
    public CheckStatus overallStatus() {
        CheckStatus overall = CheckStatus.PASS;

        for (CheckResult result : results) {
            overall = CheckStatus.worst(overall, result.status());
        }

        return overall;
    }

    public long count(CheckStatus status) {
        return results.stream()
                .filter(result -> result.status() == status)
                .count();
    }

    public boolean hasCriticalResults() {
        return count(CheckStatus.CRITICAL) > 0;
    }

    public boolean hasWarnings() {
        return count(CheckStatus.WARNING) > 0;
    }

    public boolean isHealthy() {
        CheckStatus status = overallStatus();

        return status == CheckStatus.PASS
                || status == CheckStatus.INFO;
    }

    public static final class Builder {

        private Instant startedAt;
        private Instant completedAt;
        private JenkinsInfo jenkinsInfo;

        private final List<CheckResult> results =
                new ArrayList<>();

        private Builder() {
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder jenkinsInfo(JenkinsInfo jenkinsInfo) {
            this.jenkinsInfo = jenkinsInfo;
            return this;
        }

        public Builder addResult(CheckResult result) {
            results.add(
                    Objects.requireNonNull(
                            result,
                            "result must not be null"
                    )
            );

            return this;
        }

        public Builder results(List<CheckResult> results) {
            this.results.clear();

            if (results != null) {
                results.forEach(this::addResult);
            }

            return this;
        }

        public HealthReport build() {
            return new HealthReport(this);
        }
    }
}