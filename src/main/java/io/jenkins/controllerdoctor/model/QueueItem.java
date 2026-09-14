package io.jenkins.controllerdoctor.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an item currently waiting in the Jenkins queue.
 */
public final class QueueItem {

    private final long id;
    private final String taskName;
    private final String why;
    private final String stuckReason;
    private final boolean blocked;
    private final boolean buildable;
    private final boolean stuck;
    private final long inQueueSince;
    private final Map<String, Object> metadata;

    private QueueItem(Builder builder) {
        this.id = builder.id;
        this.taskName = builder.taskName;
        this.why = builder.why;
        this.stuckReason = builder.stuckReason;
        this.blocked = builder.blocked;
        this.buildable = builder.buildable;
        this.stuck = builder.stuck;
        this.inQueueSince = builder.inQueueSince;
        this.metadata = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.metadata)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public long id() {
        return id;
    }

    public String taskName() {
        return taskName;
    }

    public String why() {
        return why;
    }

    public String stuckReason() {
        return stuckReason;
    }

    public boolean blocked() {
        return blocked;
    }

    public boolean buildable() {
        return buildable;
    }

    public boolean stuck() {
        return stuck;
    }

    public long inQueueSince() {
        return inQueueSince;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public long queueTimeMillis(long nowMillis) {
        if (inQueueSince <= 0 || nowMillis <= inQueueSince) {
            return 0;
        }

        return nowMillis - inQueueSince;
    }

    public static final class Builder {

        private long id;
        private String taskName;
        private String why;
        private String stuckReason;
        private boolean blocked;
        private boolean buildable;
        private boolean stuck;
        private long inQueueSince;

        private final Map<String, Object> metadata =
                new LinkedHashMap<>();

        private Builder() {
        }

        public Builder id(long id) {
            if (id < 0) {
                throw new IllegalArgumentException("id must not be negative");
            }

            this.id = id;
            return this;
        }

        public Builder taskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public Builder why(String why) {
            this.why = why;
            return this;
        }

        public Builder stuckReason(String stuckReason) {
            this.stuckReason = stuckReason;
            return this;
        }

        public Builder blocked(boolean blocked) {
            this.blocked = blocked;
            return this;
        }

        public Builder buildable(boolean buildable) {
            this.buildable = buildable;
            return this;
        }

        public Builder stuck(boolean stuck) {
            this.stuck = stuck;
            return this;
        }

        public Builder inQueueSince(long inQueueSince) {
            if (inQueueSince < 0) {
                throw new IllegalArgumentException(
                        "inQueueSince must not be negative"
                );
            }

            this.inQueueSince = inQueueSince;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                metadata.put(key, value);
            }

            return this;
        }

        public QueueItem build() {
            return new QueueItem(this);
        }
    }
}