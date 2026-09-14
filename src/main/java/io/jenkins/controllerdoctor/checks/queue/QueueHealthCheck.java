package io.jenkins.controllerdoctor.checks.queue;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.QueueItem;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Performs health analysis of the Jenkins build queue.
 *
 * <p>The Jenkins queue is an important indicator of controller health.
 * A queue may contain blocked, stuck, or buildable items, and excessive
 * queue wait times can indicate insufficient executor capacity, node
 * availability problems, scheduling constraints, or controller pressure.</p>
 *
 * <p>This check intentionally does not attempt to determine the root cause
 * of every queue problem. Instead, it reports observable queue conditions
 * and provides enough structured metadata for console, JSON, and Markdown
 * reporters to present useful diagnostic information.</p>
 */
public final class QueueHealthCheck implements Check {

    /**
     * Unique identifier of this check.
     */
    public static final String ID = "queue.health";

    /**
     * Human-readable check name.
     */
    public static final String NAME = "Queue health";

    /**
     * Queue wait time after which the check reports a warning.
     */
    public static final Duration WARNING_WAIT_TIME =
            Duration.ofMinutes(10);

    /**
     * Queue wait time after which the check reports a critical condition.
     */
    public static final Duration CRITICAL_WAIT_TIME =
            Duration.ofMinutes(30);

    /**
     * Queue size after which the check reports a warning.
     */
    public static final int WARNING_QUEUE_SIZE = 10;

    /**
     * Queue size after which the check reports a critical condition.
     */
    public static final int CRITICAL_QUEUE_SIZE = 50;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Executes the queue health analysis.
     *
     * @param client Jenkins API client
     * @param jenkinsInfo controller information
     * @return structured queue health result
     */
    @Override
    public CheckResult execute(
            JenkinsClient client,
            JenkinsInfo jenkinsInfo
    ) {
        Objects.requireNonNull(client, "client must not be null");

        Instant startedAt = Instant.now();

        try {
            List<QueueItem> queueItems = client.getQueueItems();

            if (queueItems == null) {
                queueItems = List.of();
            }

            long nowMillis = System.currentTimeMillis();

            QueueStatistics statistics =
                    calculateStatistics(queueItems, nowMillis);

            CheckStatus status = determineStatus(statistics);

            Instant completedAt = Instant.now();

            CheckResult.Builder result = CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(status)
                    .summary(buildSummary(statistics, status))
                    .details(buildDetails(statistics))
                    .metadata("queueSize", statistics.queueSize())
                    .metadata("blockedItems", statistics.blockedItems())
                    .metadata("stuckItems", statistics.stuckItems())
                    .metadata("buildableItems", statistics.buildableItems())
                    .metadata(
                            "longWaitingItems",
                            statistics.longWaitingItems()
                    )
                    .metadata(
                            "criticalWaitingItems",
                            statistics.criticalWaitingItems()
                    )
                    .metadata(
                            "longestWaitMillis",
                            statistics.longestWaitMillis()
                    )
                    .metadata(
                            "longestWaitSeconds",
                            statistics.longestWaitMillis() / 1000L
                    )
                    .metadata(
                            "warningWaitThresholdMillis",
                            WARNING_WAIT_TIME.toMillis()
                    )
                    .metadata(
                            "criticalWaitThresholdMillis",
                            CRITICAL_WAIT_TIME.toMillis()
                    )
                    .metadata(
                            "warningWaitThresholdSeconds",
                            WARNING_WAIT_TIME.toSeconds()
                    )
                    .metadata(
                            "criticalWaitThresholdSeconds",
                            CRITICAL_WAIT_TIME.toSeconds()
                    )
                    .metadata(
                            "warningQueueSize",
                            WARNING_QUEUE_SIZE
                    )
                    .metadata(
                            "criticalQueueSize",
                            CRITICAL_QUEUE_SIZE
                    )
                    .metadata(
                            "problematicItems",
                            formatProblematicItems(
                                    statistics.originalItems(),
                                    nowMillis
                            )
                    )
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .duration(
                            Duration.between(startedAt, completedAt)
                    );

            String recommendation =
                    buildRecommendation(statistics, status);

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
                    .summary("Unable to inspect Jenkins build queue")
                    .details(
                            exception.getMessage() != null
                                    ? exception.getMessage()
                                    : "The Jenkins queue API could not be read."
                    )
                    .recommendation(
                            "Verify Jenkins connectivity and ensure the "
                                    + "configured credentials can access the "
                                    + "queue API."
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

    /**
     * Calculates all queue metrics in one pass.
     *
     * @param queueItems queue items returned by Jenkins
     * @param nowMillis current timestamp
     * @return calculated queue statistics
     */
    private static QueueStatistics calculateStatistics(
            List<QueueItem> queueItems,
            long nowMillis
    ) {
        long blockedItems = 0;
        long stuckItems = 0;
        long buildableItems = 0;
        long longWaitingItems = 0;
        long criticalWaitingItems = 0;
        long longestWaitMillis = 0;

        for (QueueItem item : queueItems) {
            if (item == null) {
                continue;
            }

            if (item.blocked()) {
                blockedItems++;
            }

            if (item.stuck()) {
                stuckItems++;
            }

            if (item.buildable()) {
                buildableItems++;
            }

            long waitMillis = item.queueTimeMillis(nowMillis);

            if (waitMillis > longestWaitMillis) {
                longestWaitMillis = waitMillis;
            }

            if (waitMillis >= WARNING_WAIT_TIME.toMillis()) {
                longWaitingItems++;
            }

            if (waitMillis >= CRITICAL_WAIT_TIME.toMillis()) {
                criticalWaitingItems++;
            }
        }

        return new QueueStatistics(
                queueItems.size(),
                blockedItems,
                stuckItems,
                buildableItems,
                longWaitingItems,
                criticalWaitingItems,
                longestWaitMillis,
                queueItems
        );
    }

    /**
     * Determines the overall status based on queue conditions.
     *
     * <p>Critical conditions take precedence over warnings. Queue wait
     * time, stuck items, and excessive queue size are treated as critical
     * indicators when they exceed their respective thresholds.</p>
     */
    private static CheckStatus determineStatus(
            QueueStatistics statistics
    ) {
        if (statistics.criticalWaitingItems() > 0) {
            return CheckStatus.CRITICAL;
        }

        if (statistics.stuckItems() > 0) {
            return CheckStatus.CRITICAL;
        }

        if (statistics.queueSize() >= CRITICAL_QUEUE_SIZE) {
            return CheckStatus.CRITICAL;
        }

        if (statistics.longWaitingItems() > 0) {
            return CheckStatus.WARNING;
        }

        if (statistics.blockedItems() > 0) {
            return CheckStatus.WARNING;
        }

        if (statistics.queueSize() >= WARNING_QUEUE_SIZE) {
            return CheckStatus.WARNING;
        }

        return CheckStatus.PASS;
    }

    /**
     * Creates the primary human-readable summary.
     */
    private static String buildSummary(
            QueueStatistics statistics,
            CheckStatus status
    ) {
        return switch (status) {
            case PASS -> {
                if (statistics.queueSize() == 0) {
                    yield "Jenkins build queue is empty";
                }

                yield "Jenkins build queue is healthy";
            }

            case INFO ->
                    "Jenkins build queue information available";

            case WARNING ->
                    "Jenkins build queue requires attention";

            case CRITICAL ->
                    "Jenkins build queue is in a critical state";
        };
    }

    /**
     * Builds detailed information suitable for human-readable reports.
     */
    private static String buildDetails(
            QueueStatistics statistics
    ) {
        if (statistics.queueSize() == 0) {
            return "No items are currently waiting in the Jenkins "
                    + "build queue.";
        }

        StringBuilder details = new StringBuilder();

        details.append("Queue size: ")
                .append(statistics.queueSize())
                .append(". ");

        details.append("Blocked items: ")
                .append(statistics.blockedItems())
                .append(". ");

        details.append("Stuck items: ")
                .append(statistics.stuckItems())
                .append(". ");

        details.append("Buildable items: ")
                .append(statistics.buildableItems())
                .append(". ");

        details.append("Items waiting at least ")
                .append(WARNING_WAIT_TIME.toMinutes())
                .append(" minutes: ")
                .append(statistics.longWaitingItems())
                .append(". ");

        details.append("Items waiting at least ")
                .append(CRITICAL_WAIT_TIME.toMinutes())
                .append(" minutes: ")
                .append(statistics.criticalWaitingItems())
                .append(". ");

        details.append("Longest wait: ")
                .append(formatDuration(
                        statistics.longestWaitMillis()
                ))
                .append(".");

        return details.toString();
    }

    /**
     * Determines the most useful recommendation for the detected condition.
     */
    private static String buildRecommendation(
            QueueStatistics statistics,
            CheckStatus status
    ) {
        if (status == CheckStatus.PASS) {
            return null;
        }

        if (statistics.stuckItems() > 0) {
            return "Investigate stuck queue items and determine why Jenkins "
                    + "cannot schedule or start them.";
        }

        if (statistics.criticalWaitingItems() > 0) {
            return "Investigate queue items waiting for more than "
                    + CRITICAL_WAIT_TIME.toMinutes()
                    + " minutes. Check executor capacity, node availability, "
                    + "labels, and scheduling constraints.";
        }

        if (statistics.queueSize() >= CRITICAL_QUEUE_SIZE) {
            return "Investigate Jenkins executor capacity and scheduling "
                    + "constraints because the build queue is unusually large.";
        }

        if (statistics.blockedItems() > 0) {
            return "Investigate blocked queue items and verify node labels, "
                    + "executor availability, and scheduling constraints.";
        }

        if (statistics.longWaitingItems() > 0) {
            return "Investigate queue items waiting longer than "
                    + WARNING_WAIT_TIME.toMinutes()
                    + " minutes and verify available executor capacity.";
        }

        if (statistics.queueSize() >= WARNING_QUEUE_SIZE) {
            return "Review Jenkins executor capacity and scheduling "
                    + "constraints because the build queue is growing.";
        }

        return "Review Jenkins queue conditions.";
    }

    /**
     * Converts problematic queue entries into structured human-readable
     * strings for report metadata.
     */
    private static List<String> formatProblematicItems(
            List<QueueItem> queueItems,
            long nowMillis
    ) {
        List<QueueItem> sortedItems = new ArrayList<>();

        for (QueueItem item : queueItems) {
            if (item != null) {
                sortedItems.add(item);
            }
        }

        sortedItems.sort(
                Comparator.comparingLong(
                        (QueueItem item) ->
                                item.queueTimeMillis(nowMillis)
                ).reversed()
        );

        List<String> result = new ArrayList<>();

        for (QueueItem item : sortedItems) {
            long waitMillis = item.queueTimeMillis(nowMillis);

            boolean problematic =
                    item.blocked()
                            || item.stuck()
                            || waitMillis >= WARNING_WAIT_TIME.toMillis();

            if (!problematic) {
                continue;
            }

            result.add(formatQueueItem(item, waitMillis));
        }

        return result;
    }

    /**
     * Formats a single queue item.
     */
    private static String formatQueueItem(
            QueueItem item,
            long waitMillis
    ) {
        StringBuilder description = new StringBuilder();

        description.append("id=")
                .append(item.id());

        if (item.taskName() != null && !item.taskName().isBlank()) {
            description.append(", task=")
                    .append(item.taskName());
        }

        description.append(", waitSeconds=")
                .append(waitMillis / 1000L);

        description.append(", blocked=")
                .append(item.blocked());

        description.append(", buildable=")
                .append(item.buildable());

        description.append(", stuck=")
                .append(item.stuck());

        if (item.why() != null && !item.why().isBlank()) {
            description.append(", why=")
                    .append(item.why());
        }

        if (item.stuckReason() != null
                && !item.stuckReason().isBlank()) {
            description.append(", stuckReason=")
                    .append(item.stuckReason());
        }

        return description.toString();
    }

    /**
     * Formats a duration represented in milliseconds.
     */
    private static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0 seconds";
        }

        Duration duration = Duration.ofMillis(millis);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format(
                    "%dh %dm %ds",
                    hours,
                    minutes,
                    seconds
            );
        }

        if (minutes > 0) {
            return String.format(
                    "%dm %ds",
                    minutes,
                    seconds
            );
        }

        return seconds + " seconds";
    }

    /**
     * Immutable internal representation of calculated queue metrics.
     */
    private record QueueStatistics(
            int queueSize,
            long blockedItems,
            long stuckItems,
            long buildableItems,
            long longWaitingItems,
            long criticalWaitingItems,
            long longestWaitMillis,
            List<QueueItem> originalItems
    ) {
        private QueueStatistics {
            originalItems = List.copyOf(originalItems);
        }
    }
}