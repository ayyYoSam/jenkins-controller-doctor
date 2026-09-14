package io.jenkins.controllerdoctor.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents plugin information retrieved from a Jenkins controller.
 */
public final class PluginInfo {

    private final String shortName;
    private final String longName;
    private final String version;
    private final String requiredCore;
    private final boolean enabled;
    private final boolean active;
    private final boolean bundled;
    private final boolean hasUpdate;
    private final String latestVersion;
    private final List<String> dependencies;
    private final Map<String, Object> metadata;

    private PluginInfo(Builder builder) {
        this.shortName = builder.shortName;
        this.longName = builder.longName;
        this.version = builder.version;
        this.requiredCore = builder.requiredCore;
        this.enabled = builder.enabled;
        this.active = builder.active;
        this.bundled = builder.bundled;
        this.hasUpdate = builder.hasUpdate;
        this.latestVersion = builder.latestVersion;
        this.dependencies = List.copyOf(builder.dependencies);
        this.metadata = Collections.unmodifiableMap(
                Map.copyOf(builder.metadata)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public String shortName() {
        return shortName;
    }

    public String longName() {
        return longName;
    }

    public String version() {
        return version;
    }

    public String requiredCore() {
        return requiredCore;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean active() {
        return active;
    }

    public boolean bundled() {
        return bundled;
    }

    public boolean hasUpdate() {
        return hasUpdate;
    }

    public String latestVersion() {
        return latestVersion;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static final class Builder {

        private String shortName;
        private String longName;
        private String version;
        private String requiredCore;
        private boolean enabled;
        private boolean active;
        private boolean bundled;
        private boolean hasUpdate;
        private String latestVersion;

        private final java.util.ArrayList<String> dependencies =
                new java.util.ArrayList<>();

        private final java.util.LinkedHashMap<String, Object> metadata =
                new java.util.LinkedHashMap<>();

        private Builder() {
        }

        public Builder shortName(String shortName) {
            this.shortName = shortName;
            return this;
        }

        public Builder longName(String longName) {
            this.longName = longName;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder requiredCore(String requiredCore) {
            this.requiredCore = requiredCore;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder bundled(boolean bundled) {
            this.bundled = bundled;
            return this;
        }

        public Builder hasUpdate(boolean hasUpdate) {
            this.hasUpdate = hasUpdate;
            return this;
        }

        public Builder latestVersion(String latestVersion) {
            this.latestVersion = latestVersion;
            return this;
        }

        public Builder dependency(String dependency) {
            if (dependency != null && !dependency.isBlank()) {
                dependencies.add(dependency);
            }

            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            if (dependencies != null) {
                dependencies.forEach(this::dependency);
            }

            return this;
        }

        public Builder metadata(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                metadata.put(key, value);
            }

            return this;
        }

        public PluginInfo build() {
            if (shortName == null || shortName.isBlank()) {
                throw new IllegalArgumentException(
                        "shortName must not be blank"
                );
            }

            return new PluginInfo(this);
        }
    }
}