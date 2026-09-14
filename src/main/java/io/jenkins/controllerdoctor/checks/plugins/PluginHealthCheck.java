package io.jenkins.controllerdoctor.checks.plugins;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.PluginInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the runtime health state of installed Jenkins plugins.
 *
 * <p>This check is intentionally read-only. It inspects the plugin state
 * reported by Jenkins and identifies plugins that are disabled or not active.
 * It does not attempt to enable, disable, install, remove, or update plugins.</p>
 */
public final class PluginHealthCheck implements Check {

    private static final String ID = "plugins.health";
    private static final String NAME = "Plugin health";

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

            if (plugins.isEmpty()) {
                return result(
                        CheckStatus.INFO,
                        "No plugins were returned by the Jenkins API.",
                        "The controller did not report any installed plugins.",
                        "Verify that the Jenkins plugin manager API is accessible.",
                        plugins,
                        startedNanos
                );
            }

            List<PluginInfo> disabledPlugins = plugins.stream()
                    .filter(plugin -> !plugin.enabled())
                    .toList();

            List<PluginInfo> inactivePlugins = plugins.stream()
                    .filter(plugin ->
                            plugin.enabled() && !plugin.active()
                    )
                    .toList();

            List<PluginInfo> unhealthyPlugins = new ArrayList<>(
                    disabledPlugins
            );

            inactivePlugins.forEach(plugin -> {
                if (!unhealthyPlugins.contains(plugin)) {
                    unhealthyPlugins.add(plugin);
                }
            });

            if (unhealthyPlugins.isEmpty()) {
                return result(
                        CheckStatus.PASS,
                        "All reported plugins are enabled and active.",
                        "Jenkins reported "
                                + plugins.size()
                                + " installed plugins and none were found "
                                + "in an unhealthy runtime state.",
                        null,
                        plugins,
                        startedNanos
                );
            }

            CheckStatus status = determineStatus(
                    disabledPlugins,
                    inactivePlugins
            );

            String summary = buildSummary(
                    disabledPlugins,
                    inactivePlugins
            );

            String details = buildDetails(
                    disabledPlugins,
                    inactivePlugins,
                    plugins.size()
            );

            String recommendation = buildRecommendation(
                    disabledPlugins,
                    inactivePlugins
            );

            return result(
                    status,
                    summary,
                    details,
                    recommendation,
                    plugins,
                    startedNanos
            );

        } catch (JenkinsClientException exception) {
            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary("Unable to inspect Jenkins plugins.")
                    .details(exception.getMessage())
                    .recommendation(
                            "Verify Jenkins connectivity, authentication, "
                                    + "permissions, and the plugin manager API."
                    )
                    .metadata("errorReason", exception.reason().name())
                    .metadata("statusCode", exception.statusCode())
                    .duration(elapsed(startedNanos))
                    .build();
        }
    }

    private static CheckStatus determineStatus(
            List<PluginInfo> disabledPlugins,
            List<PluginInfo> inactivePlugins
    ) {
        /*
         * A plugin explicitly disabled by an administrator is not necessarily
         * a fault. It is reported as WARNING rather than CRITICAL.
         *
         * An enabled plugin that is not active is more concerning because
         * Jenkins may be unable to provide the functionality expected from it.
         */
        if (!inactivePlugins.isEmpty()) {
            return CheckStatus.CRITICAL;
        }

        return CheckStatus.WARNING;
    }

    private static String buildSummary(
            List<PluginInfo> disabledPlugins,
            List<PluginInfo> inactivePlugins
    ) {
        int disabled = disabledPlugins.size();
        int inactive = inactivePlugins.size();

        if (inactive > 0 && disabled > 0) {
            return inactive
                    + " enabled plugin(s) are inactive and "
                    + disabled
                    + " plugin(s) are disabled.";
        }

        if (inactive > 0) {
            return inactive
                    + " enabled plugin(s) are inactive.";
        }

        return disabled
                + " plugin(s) are disabled.";
    }

    private static String buildDetails(
            List<PluginInfo> disabledPlugins,
            List<PluginInfo> inactivePlugins,
            int totalPlugins
    ) {
        StringBuilder details = new StringBuilder();

        details.append("Jenkins reported ")
                .append(totalPlugins)
                .append(" installed plugin(s).");

        if (!disabledPlugins.isEmpty()) {
            details.append(" Disabled plugins: ")
                    .append(formatPluginNames(disabledPlugins))
                    .append(".");
        }

        if (!inactivePlugins.isEmpty()) {
            details.append(" Inactive plugins: ")
                    .append(formatPluginNames(inactivePlugins))
                    .append(".");
        }

        return details.toString();
    }

    private static String buildRecommendation(
            List<PluginInfo> disabledPlugins,
            List<PluginInfo> inactivePlugins
    ) {
        if (!inactivePlugins.isEmpty()) {
            return "Investigate the inactive plugin(s), including their "
                    + "startup logs and dependencies. A plugin that is "
                    + "enabled but inactive may indicate a loading failure, "
                    + "missing dependency, incompatible Jenkins core version, "
                    + "or another initialization problem.";
        }

        if (!disabledPlugins.isEmpty()) {
            return "Review disabled plugins and confirm that each disabled "
                    + "plugin is intentionally disabled. No action is "
                    + "required when the disabled state is expected.";
        }

        return null;
    }

    private static String formatPluginNames(List<PluginInfo> plugins) {
        return plugins.stream()
                .map(PluginHealthCheck::pluginName)
                .sorted()
                .reduce(
                        (first, second) -> first + ", " + second
                )
                .orElse("none");
    }

    private static String pluginName(PluginInfo plugin) {
        if (plugin.shortName() == null
                || plugin.shortName().isBlank()) {
            return plugin.longName();
        }

        if (plugin.version() == null
                || plugin.version().isBlank()) {
            return plugin.shortName();
        }

        return plugin.shortName()
                + " ("
                + plugin.version()
                + ")";
    }

    private static CheckResult result(
            CheckStatus status,
            String summary,
            String details,
            String recommendation,
            List<PluginInfo> plugins,
            long startedNanos
    ) {
        long disabledCount = plugins.stream()
                .filter(plugin -> !plugin.enabled())
                .count();

        long inactiveCount = plugins.stream()
                .filter(plugin ->
                        plugin.enabled() && !plugin.active()
                )
                .count();

        return CheckResult.builder()
                .checkId(ID)
                .checkName(NAME)
                .status(status)
                .summary(summary)
                .details(details)
                .recommendation(recommendation)
                .metadata("totalPlugins", plugins.size())
                .metadata("disabledPlugins", disabledCount)
                .metadata("inactivePlugins", inactiveCount)
                .duration(elapsed(startedNanos))
                .build();
    }

    private static java.time.Duration elapsed(long startedNanos) {
        return java.time.Duration.ofNanos(
                Math.max(
                        0,
                        System.nanoTime() - startedNanos
                )
        );
    }
}