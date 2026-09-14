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
 * Checks the general health state of Jenkins build nodes.
 *
 * <p>This check inspects node availability and executor capacity without
 * making any changes to the Jenkins controller.</p>
 */
public final class NodeHealthCheck implements Check {

    private static final String ID = "nodes.health";
    private static final String NAME = "Node health";

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
                        .summary("No build nodes were reported.")
                        .details(
                                "The Jenkins API did not return any build "
                                        + "nodes for health analysis."
                        )
                        .recommendation(
                                "Verify that the controller API is returning "
                                        + "the expected computer information."
                        )
                        .metadata("totalNodes", 0)
                        .metadata("offlineNodes", 0)
                        .metadata("busyNodes", 0)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            long offlineNodes = nodes.stream()
                    .filter(NodeInfo::offline)
                    .count();

            long temporarilyOfflineNodes = nodes.stream()
                    .filter(NodeInfo::temporarilyOffline)
                    .count();

            long busyNodes = nodes.stream()
                    .filter(NodeInfo::isFullyBusy)
                    .count();

            int totalExecutors = nodes.stream()
                    .mapToInt(NodeInfo::numExecutors)
                    .sum();

            int busyExecutors = nodes.stream()
                    .mapToInt(NodeInfo::busyExecutors)
                    .sum();

            int idleExecutors = nodes.stream()
                    .mapToInt(NodeInfo::idleExecutors)
                    .sum();

            if (offlineNodes > 0) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.CRITICAL)
                        .summary(
                                offlineNodes
                                        + " node(s) are offline."
                        )
                        .details(
                                buildDetails(
                                        nodes,
                                        offlineNodes,
                                        temporarilyOfflineNodes,
                                        busyNodes,
                                        totalExecutors,
                                        busyExecutors,
                                        idleExecutors
                                )
                        )
                        .recommendation(
                                "Investigate offline nodes and their offline "
                                        + "causes. Check agent connectivity, "
                                        + "launch configuration, network "
                                        + "connectivity, and node logs."
                        )
                        .metadata("totalNodes", nodes.size())
                        .metadata("offlineNodes", offlineNodes)
                        .metadata(
                                "temporarilyOfflineNodes",
                                temporarilyOfflineNodes
                        )
                        .metadata("busyNodes", busyNodes)
                        .metadata("totalExecutors", totalExecutors)
                        .metadata("busyExecutors", busyExecutors)
                        .metadata("idleExecutors", idleExecutors)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            if (temporarilyOfflineNodes > 0) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.WARNING)
                        .summary(
                                temporarilyOfflineNodes
                                        + " node(s) are temporarily offline."
                        )
                        .details(
                                buildDetails(
                                        nodes,
                                        offlineNodes,
                                        temporarilyOfflineNodes,
                                        busyNodes,
                                        totalExecutors,
                                        busyExecutors,
                                        idleExecutors
                                )
                        )
                        .recommendation(
                                "Review temporarily offline nodes and "
                                        + "confirm that their state is "
                                        + "intentional."
                        )
                        .metadata("totalNodes", nodes.size())
                        .metadata("offlineNodes", offlineNodes)
                        .metadata(
                                "temporarilyOfflineNodes",
                                temporarilyOfflineNodes
                        )
                        .metadata("busyNodes", busyNodes)
                        .metadata("totalExecutors", totalExecutors)
                        .metadata("busyExecutors", busyExecutors)
                        .metadata("idleExecutors", idleExecutors)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.PASS)
                    .summary(
                            "All reported Jenkins nodes are online."
                    )
                    .details(
                            buildDetails(
                                    nodes,
                                    offlineNodes,
                                    temporarilyOfflineNodes,
                                    busyNodes,
                                    totalExecutors,
                                    busyExecutors,
                                    idleExecutors
                            )
                    )
                    .metadata("totalNodes", nodes.size())
                    .metadata("offlineNodes", offlineNodes)
                    .metadata(
                            "temporarilyOfflineNodes",
                            temporarilyOfflineNodes
                    )
                    .metadata("busyNodes", busyNodes)
                    .metadata("totalExecutors", totalExecutors)
                    .metadata("busyExecutors", busyExecutors)
                    .metadata("idleExecutors", idleExecutors)
                    .duration(elapsed(startedNanos))
                    .build();

        } catch (JenkinsClientException exception) {
            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary("Unable to inspect Jenkins nodes.")
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

    private static String buildDetails(
            List<NodeInfo> nodes,
            long offlineNodes,
            long temporarilyOfflineNodes,
            long busyNodes,
            int totalExecutors,
            int busyExecutors,
            int idleExecutors
    ) {
        StringBuilder details = new StringBuilder();

        details.append("Nodes: ")
                .append(nodes.size())
                .append(". ");

        details.append("Offline: ")
                .append(offlineNodes)
                .append(". ");

        details.append("Temporarily offline: ")
                .append(temporarilyOfflineNodes)
                .append(". ");

        details.append("Fully busy: ")
                .append(busyNodes)
                .append(". ");

        details.append("Executors: ")
                .append(busyExecutors)
                .append("/")
                .append(totalExecutors)
                .append(" busy, ")
                .append(idleExecutors)
                .append(" idle.");

        List<NodeInfo> problematicNodes = nodes.stream()
                .filter(node ->
                        node.offline()
                                || node.temporarilyOffline()
                )
                .toList();

        if (!problematicNodes.isEmpty()) {
            details.append(System.lineSeparator())
                    .append("Nodes requiring attention:");

            for (NodeInfo node : problematicNodes) {
                details.append(System.lineSeparator())
                        .append(" - ")
                        .append(node.displayName());

                if (node.offlineCause() != null
                        && !node.offlineCause().isBlank()) {
                    details.append(": ")
                            .append(node.offlineCause());
                }
            }
        }

        return details.toString();
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