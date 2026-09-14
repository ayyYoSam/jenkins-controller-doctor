package io.jenkins.controllerdoctor.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a Jenkins node and its executor state.
 */
public final class NodeInfo {

    private final String displayName;
    private final String description;
    private final String offlineCause;
    private final boolean offline;
    private final boolean temporarilyOffline;
    private final int numExecutors;
    private final int busyExecutors;
    private final int idleExecutors;
    private final Map<String, Object> metadata;

    private NodeInfo(Builder builder) {
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.offlineCause = builder.offlineCause;
        this.offline = builder.offline;
        this.temporarilyOffline = builder.temporarilyOffline;
        this.numExecutors = builder.numExecutors;
        this.busyExecutors = builder.busyExecutors;
        this.idleExecutors = builder.idleExecutors;
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

    public String description() {
        return description;
    }

    public String offlineCause() {
        return offlineCause;
    }

    public boolean offline() {
        return offline;
    }

    public boolean temporarilyOffline() {
        return temporarilyOffline;
    }

    public int numExecutors() {
        return numExecutors;
    }

    public int busyExecutors() {
        return busyExecutors;
    }

    public int idleExecutors() {
        return idleExecutors;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public boolean hasAvailableExecutor() {
        return !offline && idleExecutors > 0;
    }

    public boolean isFullyBusy() {
        return numExecutors > 0 && busyExecutors >= numExecutors;
    }

    public static final class Builder {

        private String displayName;
        private String description;
        private String offlineCause;
        private boolean offline;
        private boolean temporarilyOffline;
        private int numExecutors;
        private int busyExecutors;
        private int idleExecutors;

        private final Map<String, Object> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder offlineCause(String offlineCause) {
            this.offlineCause = offlineCause;
            return this;
        }

        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder temporarilyOffline(boolean temporarilyOffline) {
            this.temporarilyOffline = temporarilyOffline;
            return this;
        }

        public Builder numExecutors(int numExecutors) {
            this.numExecutors = requireNonNegative(
                    numExecutors,
                    "numExecutors"
            );
            return this;
        }

        public Builder busyExecutors(int busyExecutors) {
            this.busyExecutors = requireNonNegative(
                    busyExecutors,
                    "busyExecutors"
            );
            return this;
        }

        public Builder idleExecutors(int idleExecutors) {
            this.idleExecutors = requireNonNegative(
                    idleExecutors,
                    "idleExecutors"
            );
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                metadata.put(key, value);
            }

            return this;
        }

        public NodeInfo build() {
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException(
                        "displayName must not be blank"
                );
            }

            if (busyExecutors > numExecutors) {
                throw new IllegalArgumentException(
                        "busyExecutors must not exceed numExecutors"
                );
            }

            if (idleExecutors > numExecutors) {
                throw new IllegalArgumentException(
                        "idleExecutors must not exceed numExecutors"
                );
            }

            return new NodeInfo(this);
        }

        private static int requireNonNegative(int value, String field) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        field + " must not be negative"
                );
            }

            return value;
        }
    }
}