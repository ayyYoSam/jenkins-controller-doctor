package io.jenkins.controllerdoctor.checks.plugins;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.PluginInfo;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks for Jenkins plugins that have an available update.
 *
 * <p>This check is read-only. It does not install or update plugins.</p>
 *
 * <p>Plugin updates are reported as warnings rather than critical failures.
 * An outdated plugin is not necessarily broken, but keeping plugins current
 * reduces the risk of known bugs, compatibility problems, and security issues.</p>
 */
public final class PluginUpdateCheck implements Check {

    private static final String ID = "plugins.updates";
    private static final String NAME = "Plugin updates";

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
            List<PluginInfo> plugins = client.getPlugins();

            List<PluginInfo> updates = plugins.stream()
                    .filter(PluginInfo::hasUpdate)
                    .sorted(
                            Comparator.comparing(
                                    PluginUpdateCheck::pluginName,
                                    String.CASE_INSENSITIVE_ORDER
                            )
                    )
                    .toList();

            if (updates.isEmpty()) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.PASS)
                        .summary("All installed plugins are up to date.")
                        .details(
                                "Jenkins reported "
                                        + plugins.size()
                                        + " installed plugin(s) and no "
                                        + "available updates."
                        )
                        .metadata("totalPlugins", plugins.size())
                        .metadata("pluginsWithUpdates", 0)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            CheckStatus status = determineStatus(updates);

            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(status)
                    .summary(
                            updates.size()
                                    + " plugin(s) have an available update."
                    )
                    .details(buildDetails(updates))
                    .recommendation(
                            "Review the available plugin updates and apply "
                                    + "them through your normal Jenkins "
                                    + "maintenance process. Prioritize "
                                    + "security-related updates and verify "
                                    + "plugin compatibility before upgrading."
                    )
                    .metadata("totalPlugins", plugins.size())
                    .metadata("pluginsWithUpdates", updates.size())
                    .metadata(
                            "updatedPlugins",
                            updates.stream()
                                    .map(PluginUpdateCheck::pluginName)
                                    .collect(Collectors.toList())
                    )
                    .duration(elapsed(startedNanos))
                    .build();

        } catch (JenkinsClientException exception) {
            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary("Unable to inspect plugin updates.")
                    .details(exception.getMessage())
                    .recommendation(
                            "Verify Jenkins connectivity, authentication, "
                                    + "permissions, and the plugin manager API."
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

    /**
     * Determines the severity of an update finding.
     *
     * <p>Updates are normally informational maintenance concerns. The actual
     * severity of a particular update cannot be determined from the Jenkins
     * plugin update API alone because it does not establish whether an update
     * is security-critical. Therefore this check deliberately uses WARNING
     * rather than pretending that every available update is a security issue.</p>
     */
    private static CheckStatus determineStatus(
            List<PluginInfo> updates
    ) {
        if (updates.isEmpty()) {
            return CheckStatus.PASS;
        }

        return CheckStatus.WARNING;
    }

    private static String buildDetails(
            List<PluginInfo> updates
    ) {
        StringBuilder details = new StringBuilder();

        details.append("Plugins with available updates:");

        for (PluginInfo plugin : updates) {
            details.append(System.lineSeparator())
                    .append(" - ")
                    .append(pluginName(plugin))
                    .append(" -> ")
                    .append(latestVersion(plugin));
        }

        return details.toString();
    }

    private static String pluginName(PluginInfo plugin) {
        if (plugin.shortName() != null
                && !plugin.shortName().isBlank()) {
            return plugin.shortName();
        }

        if (plugin.longName() != null
                && !plugin.longName().isBlank()) {
            return plugin.longName();
        }

        return "<unknown-plugin>";
    }

    private static String latestVersion(PluginInfo plugin) {
        if (plugin.latestVersion() == null
                || plugin.latestVersion().isBlank()) {
            return "<unknown>";
        }

        return plugin.latestVersion();
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