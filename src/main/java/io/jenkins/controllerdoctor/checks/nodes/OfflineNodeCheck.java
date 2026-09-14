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
 * Checks for Jenkins nodes that are offline.
 *
 * <p>This check focuses specifically on offline state and reported offline
 * causes. General node availability and executor utilization are handled by
 * separate checks.</p>
 *
 * <p>The check is read-only and never attempts to reconnect, launch, or
 * otherwise modify a Jenkins node.</p>
 */
public final class OfflineNodeCheck implements Check {

    private static final String ID = "nodes.offline";
    private static final String NAME = "Offline nodes";

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

            List<NodeInfo> offlineNodes = nodes.stream()
                    .filter(NodeInfo::offline)
                    .toList();

            List<NodeInfo> temporarilyOfflineNodes = nodes.stream()
                    .filter(NodeInfo::temporarilyOffline)
                    .toList();

            if (offlineNodes.isEmpty()
                    && temporarilyOfflineNodes.isEmpty()) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.PASS)
                        .summary("No offline nodes were detected.")
                        .details(
                                "Jenkins reported "
                                        + nodes.size()
                                        + " node(s), and none are "
                                        + "currently offline or temporarily "
                                        + "offline."
                        )
                        .metadata("totalNodes", nodes.size())
                        .metadata("offlineNodes", 0)
                        .metadata("temporarilyOfflineNodes", 0)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            CheckStatus status = offlineNodes.isEmpty()
                    ? CheckStatus.WARNING
                    : CheckStatus.CRITICAL;

            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(status)
                    .summary(
                            buildSummary(
                                    offlineNodes,
                                    temporarilyOfflineNodes
                            )
                    )
                    .details(
                            buildDetails(
                                    offlineNodes,
                                    temporarilyOfflineNodes
                            )
                    )
                    .recommendation(
                            buildRecommendation(
                                    offlineNodes,
                                    temporarilyOfflineNodes
                            )
                    )
                    .metadata("totalNodes", nodes.size())
                    .metadata("offlineNodes", offlineNodes.size())
                    .metadata(
                            "temporarilyOfflineNodes",
                            temporarilyOfflineNodes.size()
                    )
                    .metadata(
                            "offlineNodeDetails",
                            buildNodeDetails(offlineNodes)
                    )
                    .metadata(
                            "temporarilyOfflineNodeDetails",
                            buildNodeDetails(temporarilyOfflineNodes)
                    )
                    .duration(elapsed(startedNanos))
                    .build();

        } catch (JenkinsClientException exception) {
            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary(
                            "Unable to inspect offline Jenkins nodes."
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

    private static String buildSummary(
            List<NodeInfo> offlineNodes,
            List<NodeInfo> temporarilyOfflineNodes
    ) {
        int offline = offlineNodes.size();
        int temporary = temporarilyOfflineNodes.size();

        if (offline > 0 && temporary > 0) {
            return offline
                    + " node(s) are offline and "
                    + temporary
                    + " node(s) are temporarily offline.";
        }

        if (offline > 0) {
            return offline
                    + " node(s) are offline.";
        }

        return temporary
                + " node(s) are temporarily offline.";
    }

    private static String buildDetails(
            List<NodeInfo> offlineNodes,
            List<NodeInfo> temporarilyOfflineNodes
    ) {
        StringBuilder details = new StringBuilder();

        if (!offlineNodes.isEmpty()) {
            details.append("Offline nodes:")
                    .append(System.lineSeparator());

            appendNodes(details, offlineNodes);
        }

        if (!temporarilyOfflineNodes.isEmpty()) {
            if (details.length() > 0) {
                details.append(System.lineSeparator());
            }

            details.append("Temporarily offline nodes:")
                    .append(System.lineSeparator());

            appendNodes(details, temporarilyOfflineNodes);
        }

        return details.toString().trim();
    }

    private static void appendNodes(
            StringBuilder details,
            List<NodeInfo> nodes
    ) {
        for (NodeInfo node : nodes) {
            details.append(" - ")
                    .append(node.displayName());

            String cause = node.offlineCause();

            if (cause != null && !cause.isBlank()) {
                details.append(": ")
                        .append(cause);
            } else {
                details.append(": no offline cause reported");
            }

            details.append(System.lineSeparator());
        }
    }

    private static String buildRecommendation(
            List<NodeInfo> offlineNodes,
            List<NodeInfo> temporarilyOfflineNodes
    ) {
        if (!offlineNodes.isEmpty()) {
            return "Investigate the offline cause reported for each node. "
                    + "Check agent connectivity, node launch configuration, "
                    + "network connectivity, credentials, disk space, and "
                    + "agent logs. Restore the node only after identifying "
                    + "the underlying cause.";
        }

        if (!temporarilyOfflineNodes.isEmpty()) {
            return "Review temporarily offline nodes and confirm that their "
                    + "state is intentional. If a node should be available, "
                    + "investigate the reason it was marked temporarily "
                    + "offline.";
        }

        return null;
    }

    private static List<String> buildNodeDetails(
            List<NodeInfo> nodes
    ) {
        return nodes.stream()
                .map(node -> {
                    String cause = node.offlineCause();

                    if (cause == null || cause.isBlank()) {
                        cause = "no offline cause reported";
                    }

                    return node.displayName() + ": " + cause;
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(
                Math.max(
                        0,
                        System.nanoTime() - startedNanos
                )
        );
    }
}