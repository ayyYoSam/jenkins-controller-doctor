package io.jenkins.controllerdoctor.checks;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result produced by a controller health check.
 *
 * <p>A result contains both human-readable information and structured
 * metadata so that the same check can be rendered consistently as console,
 * JSON, or Markdown output.</p>
 */
public final class CheckResult {

    private final String checkId;
    private final String checkName;
    private final CheckStatus status;
    private final String summary;
    private final String details;
    private final String recommendation;
    private final Map<String, Object> metadata;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Duration duration;

    private CheckResult(Builder builder) {
        this.checkId = requireText(builder.checkId, "checkId");
        this.checkName = requireText(builder.checkName, "checkName");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.summary = requireText(builder.summary, "summary");
        this.details = builder.details;
        this.recommendation = builder.recommendation;
        this.metadata = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.metadata)
        );
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.duration = builder.duration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String checkId() {
        return checkId;
    }

    public String checkName() {
        return checkName;
    }

    public CheckStatus status() {
        return status;
    }

    public String summary() {
        return summary;
    }

    public String details() {
        return details;
    }

    public String recommendation() {
        return recommendation;
    }

    public Map<String, Object> metadata() {
        return metadata;
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

    public boolean isSuccessful() {
        return status == CheckStatus.PASS || status == CheckStatus.INFO;
    }

    public static final class Builder {

        private String checkId;
        private String checkName;
        private CheckStatus status;
        private String summary;
        private String details;
        private String recommendation;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private Instant startedAt;
        private Instant completedAt;
        private Duration duration;

        private Builder() {
        }

        public Builder checkId(String checkId) {
            this.checkId = checkId;
            return this;
        }

        public Builder checkName(String checkName) {
            this.checkName = checkName;
            return this;
        }

        public Builder status(CheckStatus status) {
            this.status = status;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder recommendation(String recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("metadata key must not be blank");
            }

            if (value != null) {
                this.metadata.put(key, value);
            }

            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                metadata.forEach(this::metadata);
            }

            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public CheckResult build() {
            validateTiming();

            return new CheckResult(this);
        }

        private void validateTiming() {
            if (startedAt != null && completedAt != null
                    && completedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException(
                        "completedAt must not be before startedAt"
                );
            }

            if (duration != null && duration.isNegative()) {
                throw new IllegalArgumentException(
                        "duration must not be negative"
                );
            }
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value;
    }
}