package io.jenkins.controllerdoctor.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an item currently present in the Jenkins build queue.
 *
 * <p>The model captures the queue information required by the controller
 * health checks while preserving additional API information through the
 * metadata map.</p>
 *
 * <p>Jenkins represents queue timestamps in milliseconds since the Unix
 * epoch. The {@code inQueueSince} field follows that convention.</p>
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

    /**
     * Creates a new queue item builder.
     *
     * @return new builder
     */
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

    /**
     * Calculates the amount of time that this item has been waiting.
     *
     * @param nowMillis current time in milliseconds since the Unix epoch
     * @return queue waiting time in milliseconds
     */
    public long queueTimeMillis(long nowMillis) {
        if (inQueueSince <= 0) {
            return 0;
        }

        return Math.max(0L, nowMillis - inQueueSince);
    }

    /**
     * Indicates whether this item has been waiting longer than the supplied
     * timestamp.
     *
     * @param nowMillis current time in milliseconds since the Unix epoch
     * @param thresholdMillis waiting-time threshold in milliseconds
     * @return true when the item has exceeded the threshold
     */
    public boolean hasWaitedLongerThan(
            long nowMillis,
            long thresholdMillis
    ) {
        if (thresholdMillis < 0) {
            throw new IllegalArgumentException(
                    "thresholdMillis must not be negative"
            );
        }

        return queueTimeMillis(nowMillis) >= thresholdMillis;
    }

    /**
     * Returns whether this queue item represents a potentially problematic
     * scheduling condition.
     *
     * @return true when the item is blocked or stuck
     */
    public boolean hasSchedulingProblem() {
        return blocked || stuck;
    }

    /**
     * Builder for {@link QueueItem}.
     */
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
                throw new IllegalArgumentException(
                        "id must not be negative"
                );
            }

            this.id = id;
            return this;
        }

        public Builder taskName(String taskName) {
            this.taskName = normalizeText(taskName);
            return this;
        }

        public Builder why(String why) {
            this.why = normalizeText(why);
            return this;
        }

        public Builder stuckReason(String stuckReason) {
            this.stuckReason = normalizeText(stuckReason);
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
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "metadata key must not be blank"
                );
            }

            if (value != null) {
                metadata.put(key, value);
            }

            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                metadata.forEach(this::metadata);
            }

            return this;
        }

        public QueueItem build() {
            return new QueueItem(this);
        }
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }
}