package io.jenkins.controllerdoctor.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents basic information retrieved from a Jenkins controller.
 *
 * <p>This model intentionally contains normalized controller information
 * rather than exposing Jackson-specific JSON structures throughout the
 * application.</p>
 */
public final class JenkinsInfo {

    private final String displayName;
    private final String fullName;
    private final String description;
    private final String url;
    private final String version;
    private final boolean secure;
    private final boolean useSecurity;
    private final Map<String, Object> metadata;

    private JenkinsInfo(Builder builder) {
        this.displayName = builder.displayName;
        this.fullName = builder.fullName;
        this.description = builder.description;
        this.url = builder.url;
        this.version = builder.version;
        this.secure = builder.secure;
        this.useSecurity = builder.useSecurity;
        this.metadata = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.metadata)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public String displayName() {
        return displayName;
    }

    public String fullName() {
        return fullName;
    }

    public String description() {
        return description;
    }

    public String url() {
        return url;
    }

    public String version() {
        return version;
    }

    public boolean secure() {
        return secure;
    }

    public boolean useSecurity() {
        return useSecurity;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static final class Builder {

        private String displayName;
        private String fullName;
        private String description;
        private String url;
        private String version;
        private boolean secure;
        private boolean useSecurity;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public Builder useSecurity(boolean useSecurity) {
            this.useSecurity = useSecurity;
            return this;
        }

        public Builder metadata(String key, Object value) {
            Objects.requireNonNull(key, "metadata key must not be null");

            if (key.isBlank()) {
                throw new IllegalArgumentException(
                        "metadata key must not be blank"
                );
            }

            if (value != null) {
                metadata.put(key, value);
            }

            return this;
        }

        public JenkinsInfo build() {
            return new JenkinsInfo(this);
        }
    }
}