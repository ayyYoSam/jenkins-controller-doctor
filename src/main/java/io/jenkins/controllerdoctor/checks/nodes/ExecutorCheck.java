package io.jenkins.controllerdoctor.checks.nodes;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.NodeInfo;

import java.time.Duration;
import java.util.List;

/**
 * Checks executor utilization across Jenkins nodes.
 *
 * <p>This check evaluates executor capacity independently from node
 * availability. A node can be online and healthy while all of its executors
 * are currently busy.</p>
 *
 * <p>The check is read-only and does not modify executor or node state.</p>
 */
public final class ExecutorCheck implements Check {

    private static final String ID = "nodes.executors";
    private static final String NAME = "Executor capacity";

    private static final double WARNING_UTILIZATION = 0.80;
    private static final double CRITICAL_UTILIZATION = 0.95;

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
        long startedNanos = System.nanoTime();

        try {
            List<NodeInfo> nodes = client.getNodes();

            if (nodes.isEmpty()) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.INFO)
                        .summary("No executor capacity was reported.")
                        .details(
                                "The Jenkins API returned no nodes for "
                                        + "executor utilization analysis."
                        )
                        .recommendation(
                                "Verify that the Jenkins computer API is "
                                        + "accessible and returning the "
                                        + "expected nodes."
                        )
                        .metadata("totalNodes", 0)
                        .metadata("totalExecutors", 0)
                        .metadata("busyExecutors", 0)
                        .metadata("idleExecutors", 0)
                        .metadata("utilizationPercent", 0.0)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            ExecutorStatistics statistics =
                    calculateStatistics(nodes);

            if (statistics.totalExecutors() == 0) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.WARNING)
                        .summary(
                                "No executors are configured on the "
                                        + "reported nodes."
                        )
                        .details(
                                "Jenkins reported "
                                        + nodes.size()
                                        + " node(s), but none expose an "
                                        + "executor capacity."
                        )
                        .recommendation(
                                "Verify node executor configuration and "
                                        + "confirm that the controller has "
                                        + "the expected build capacity."
                        )
                        .metadata("totalNodes", nodes.size())
                        .metadata("totalExecutors", 0)
                        .metadata(
                                "busyExecutors",
                                statistics.busyExecutors()
                        )
                        .metadata(
                                "idleExecutors",
                                statistics.idleExecutors()
                        )
                        .metadata("utilizationPercent", 0.0)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            double utilization =
                    statistics.busyExecutors()
                            / (double) statistics.totalExecutors();

            CheckStatus status =
                    determineStatus(utilization);

            String summary =
                    buildSummary(
                            status,
                            statistics,
                            utilization
                    );

            String details =
                    buildDetails(
                            nodes,
                            statistics,
                            utilization
                    );

            String recommendation =
                    buildRecommendation(
                            status,
                            statistics,
                            utilization
                    );

            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(status)
                    .summary(summary)
                    .details(details)
                    .recommendation(recommendation)
                    .metadata("totalNodes", nodes.size())
                    .metadata(
                            "totalExecutors",
                            statistics.totalExecutors()
                    )
                    .metadata(
                            "busyExecutors",
                            statistics.busyExecutors()
                    )
                    .metadata(
                            "idleExecutors",
                            statistics.idleExecutors()
                    )
                    .metadata(
                            "offlineNodes",
                            statistics.offlineNodes()
                    )
                    .metadata(
                            "utilizationPercent",
                            roundPercentage(utilization)
                    )
                    .metadata(
                            "warningThresholdPercent",
                            WARNING_UTILIZATION * 100.0
                    )
                    .metadata(
                            "criticalThresholdPercent",
                            CRITICAL_UTILIZATION * 100.0
                    )
                    .duration(elapsed(startedNanos))
                    .build();

        } catch (JenkinsClientException exception) {
            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary(
                            "Unable to inspect executor capacity."
                    )
                    .details(exception.getMessage())
                    .recommendation(
                            "Verify Jenkins connectivity, authentication, "
                                    + "permissions, and the computer API."
                    )
                    .metadata(
                            "errorReason",
                            exception.reason().name()
                    )
                    .metadata(
                            "statusCode",
                            exception.statusCode()
                    )
                    .duration(elapsed(startedNanos))
                    .build();
        }
    }

    private static ExecutorStatistics calculateStatistics(
            List<NodeInfo> nodes
    ) {
        int totalExecutors = 0;
        int busyExecutors = 0;
        int idleExecutors = 0;
        int offlineNodes = 0;

        for (NodeInfo node : nodes) {
            totalExecutors += node.numExecutors();
            busyExecutors += node.busyExecutors();
            idleExecutors += node.idleExecutors();

            if (node.offline()) {
                offlineNodes++;
            }
        }

        return new ExecutorStatistics(
                totalExecutors,
                busyExecutors,
                idleExecutors,
                offlineNodes
        );
    }

    private static CheckStatus determineStatus(
            double utilization
    ) {
        if (utilization >= CRITICAL_UTILIZATION) {
            return CheckStatus.CRITICAL;
        }

        if (utilization >= WARNING_UTILIZATION) {
            return CheckStatus.WARNING;
        }

        return CheckStatus.PASS;
    }

    private static String buildSummary(
            CheckStatus status,
            ExecutorStatistics statistics,
            double utilization
    ) {
        String percentage =
                formatPercentage(utilization);

        return switch (status) {
            case PASS ->
                    "Executor utilization is healthy at "
                            + percentage
                            + ".";

            case WARNING ->
                    "Executor utilization is high at "
                            + percentage
                            + ".";

            case CRITICAL ->
                    "Executor utilization is critically high at "
                            + percentage
                            + ".";

            case INFO ->
                    "Executor utilization is informational at "
                            + percentage
                            + ".";
        };
    }

    private static String buildDetails(
            List<NodeInfo> nodes,
            ExecutorStatistics statistics,
            double utilization
    ) {
        StringBuilder details = new StringBuilder();

        details.append("Executor capacity: ")
                .append(statistics.busyExecutors())
                .append(" busy / ")
                .append(statistics.totalExecutors())
                .append(" total, ")
                .append(statistics.idleExecutors())
                .append(" idle.");

        details.append(" Utilization: ")
                .append(formatPercentage(utilization))
                .append(".");

        details.append(" Nodes analyzed: ")
                .append(nodes.size())
                .append(".");

        if (statistics.offlineNodes() > 0) {
            details.append(" Offline nodes: ")
                    .append(statistics.offlineNodes())
                    .append(".");
        }

        return details.toString();
    }

    private static String buildRecommendation(
            CheckStatus status,
            ExecutorStatistics statistics,
            double utilization
    ) {
        return switch (status) {
            case PASS ->
                    null;

            case WARNING ->
                    "Monitor queue growth and executor utilization. "
                            + "If high utilization is sustained, review job "
                            + "concurrency and available build capacity.";

            case CRITICAL ->
                    "Investigate sustained executor saturation. Review "
                            + "queue growth, long-running builds, job "
                            + "concurrency, and available node capacity. "
                            + "Consider adding or scaling build capacity "
                            + "only after confirming that the workload "
                            + "requires it.";

            case INFO ->
                    statistics.totalExecutors() == 0
                            ? "Configure executor capacity if this controller "
                            + "is expected to execute builds."
                            : null;
        };
    }

    private static String formatPercentage(
            double value
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "%.1f%%",
                value * 100.0
        );
    }

    private static double roundPercentage(
            double value
    ) {
        return Math.round(value * 1000.0) / 10.0;
    }

    private static Duration elapsed(
            long startedNanos
    ) {
        return Duration.ofNanos(
                Math.max(
                        0,
                        System.nanoTime() - startedNanos
                )
        );
    }

    private record ExecutorStatistics(
            int totalExecutors,
            int busyExecutors,
            int idleExecutors,
            int offlineNodes
    ) {
    }
}