package io.jenkins.controllerdoctor.checks.plugins;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.PluginInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks plugin dependency consistency on a Jenkins controller.
 *
 * <p>The check examines dependencies reported by Jenkins for installed
 * plugins and identifies dependencies that are missing or disabled.</p>
 *
 * <p>This check is read-only and never attempts to install, enable, disable,
 * remove, or otherwise modify plugins.</p>
 */
public final class PluginDependencyCheck implements Check {

    private static final String ID = "plugins.dependencies";
    private static final String NAME = "Plugin dependencies";

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
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.INFO)
                        .summary(
                                "No plugins were returned by the Jenkins API."
                        )
                        .details(
                                "Dependency analysis could not identify "
                                        + "plugin relationships because the "
                                        + "controller returned no plugins."
                        )
                        .recommendation(
                                "Verify that the Jenkins plugin manager API "
                                        + "is accessible."
                        )
                        .metadata("totalPlugins", 0)
                        .metadata("missingDependencies", 0)
                        .metadata("disabledDependencies", 0)
                        .duration(elapsed(startedNanos))
                        .build();
            }

            DependencyAnalysis analysis = analyze(plugins);

            if (analysis.hasProblems()) {
                return CheckResult.builder()
                        .checkId(ID)
                        .checkName(NAME)
                        .status(CheckStatus.CRITICAL)
                        .summary(
                                buildProblemSummary(analysis)
                        )
                        .details(
                                buildProblemDetails(analysis)
                        )
                        .recommendation(
                                "Install missing dependencies or restore "
                                        + "required dependencies to an enabled "
                                        + "state. Before making changes, "
                                        + "review plugin compatibility and "
                                        + "Jenkins startup logs."
                        )
                        .metadata("totalPlugins", plugins.size())
                        .metadata(
                                "missingDependencies",
                                analysis.missingDependencies().size()
                        )
                        .metadata(
                                "disabledDependencies",
                                analysis.disabledDependencies().size()
                        )
                        .metadata(
                                "affectedPlugins",
                                analysis.affectedPlugins().size()
                        )
                        .metadata(
                                "missingDependencyDetails",
                                analysis.missingDependencyDetails()
                        )
                        .metadata(
                                "disabledDependencyDetails",
                                analysis.disabledDependencyDetails()
                        )
                        .duration(elapsed(startedNanos))
                        .build();
            }

            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.PASS)
                    .summary(
                            "Plugin dependencies are consistent."
                    )
                    .details(
                            "Jenkins reported "
                                    + plugins.size()
                                    + " installed plugin(s), and all "
                                    + "declared plugin dependencies were "
                                    + "found installed and enabled."
                    )
                    .metadata("totalPlugins", plugins.size())
                    .metadata("missingDependencies", 0)
                    .metadata("disabledDependencies", 0)
                    .metadata("affectedPlugins", 0)
                    .duration(elapsed(startedNanos))
                    .build();

        } catch (JenkinsClientException exception) {
            return CheckResult.builder()
                    .checkId(ID)
                    .checkName(NAME)
                    .status(CheckStatus.CRITICAL)
                    .summary(
                            "Unable to inspect plugin dependencies."
                    )
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

    private static DependencyAnalysis analyze(
            List<PluginInfo> plugins
    ) {
        Map<String, PluginInfo> installedPlugins = new HashMap<>();

        for (PluginInfo plugin : plugins) {
            if (plugin.shortName() == null
                    || plugin.shortName().isBlank()) {
                continue;
            }

            installedPlugins.put(
                    normalizePluginId(plugin.shortName()),
                    plugin
            );
        }

        Set<String> missingDependencies = new HashSet<>();
        Set<String> disabledDependencies = new HashSet<>();
        Set<String> affectedPlugins = new HashSet<>();

        List<String> missingDetails = new ArrayList<>();
        List<String> disabledDetails = new ArrayList<>();

        for (PluginInfo plugin : plugins) {
            if (plugin.dependencies() == null
                    || plugin.dependencies().isEmpty()) {
                continue;
            }

            for (String dependency : plugin.dependencies()) {
                DependencyReference reference =
                        parseDependency(dependency);

                if (reference.pluginId() == null
                        || reference.pluginId().isBlank()) {
                    continue;
                }

                String normalizedId =
                        normalizePluginId(reference.pluginId());

                PluginInfo installed =
                        installedPlugins.get(normalizedId);

                if (installed == null) {
                    missingDependencies.add(reference.pluginId());
                    affectedPlugins.add(plugin.shortName());

                    missingDetails.add(
                            plugin.shortName()
                                    + " -> "
                                    + reference.pluginId()
                    );

                    continue;
                }

                if (!installed.enabled()) {
                    disabledDependencies.add(reference.pluginId());
                    affectedPlugins.add(plugin.shortName());

                    disabledDetails.add(
                            plugin.shortName()
                                    + " -> "
                                    + reference.pluginId()
                    );
                }
            }
        }

        return new DependencyAnalysis(
                missingDependencies,
                disabledDependencies,
                affectedPlugins,
                missingDetails,
                disabledDetails
        );
    }

    /**
     * Jenkins dependency strings can contain optional version information.
     *
     * <p>Examples that this parser accepts include:</p>
     *
     * <ul>
     *     <li>{@code git}</li>
     *     <li>{@code git:5.0.0}</li>
     *     <li>{@code git:5.0.0;resolution:=optional}</li>
     * </ul>
     *
     * <p>The check currently uses the plugin identifier for consistency
     * analysis. Version compatibility is intentionally handled separately
     * from dependency presence.</p>
     */
    private static DependencyReference parseDependency(
            String dependency
    ) {
        if (dependency == null || dependency.isBlank()) {
            return new DependencyReference(null);
        }

        String value = dependency.trim();

        int semicolonIndex = value.indexOf(';');

        if (semicolonIndex >= 0) {
            value = value.substring(0, semicolonIndex);
        }

        int colonIndex = value.indexOf(':');

        if (colonIndex >= 0) {
            value = value.substring(0, colonIndex);
        }

        value = value.trim();

        if (value.isBlank()) {
            return new DependencyReference(null);
        }

        return new DependencyReference(value);
    }

    private static String normalizePluginId(String pluginId) {
        return pluginId
                .trim()
                .toLowerCase();
    }

    private static String buildProblemSummary(
            DependencyAnalysis analysis
    ) {
        int missing = analysis.missingDependencies().size();
        int disabled = analysis.disabledDependencies().size();

        if (missing > 0 && disabled > 0) {
            return missing
                    + " missing and "
                    + disabled
                    + " disabled plugin dependency issue(s) detected.";
        }

        if (missing > 0) {
            return missing
                    + " missing plugin dependency issue(s) detected.";
        }

        return disabled
                + " disabled plugin dependency issue(s) detected.";
    }

    private static String buildProblemDetails(
            DependencyAnalysis analysis
    ) {
        StringBuilder details = new StringBuilder();

        if (!analysis.missingDependencyDetails().isEmpty()) {
            details.append("Missing dependencies:")
                    .append(System.lineSeparator());

            analysis.missingDependencyDetails()
                    .stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(entry ->
                            details.append(" - ")
                                    .append(entry)
                                    .append(System.lineSeparator())
                    );
        }

        if (!analysis.disabledDependencyDetails().isEmpty()) {
            details.append("Disabled dependencies:")
                    .append(System.lineSeparator());

            analysis.disabledDependencyDetails()
                    .stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(entry ->
                            details.append(" - ")
                                    .append(entry)
                                    .append(System.lineSeparator())
                    );
        }

        return details.toString().trim();
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(
                Math.max(
                        0,
                        System.nanoTime() - startedNanos
                )
        );
    }

    private record DependencyReference(
            String pluginId
    ) {
    }

    private static final class DependencyAnalysis {

        private final Set<String> missingDependencies;
        private final Set<String> disabledDependencies;
        private final Set<String> affectedPlugins;
        private final List<String> missingDependencyDetails;
        private final List<String> disabledDependencyDetails;

        private DependencyAnalysis(
                Set<String> missingDependencies,
                Set<String> disabledDependencies,
                Set<String> affectedPlugins,
                List<String> missingDependencyDetails,
                List<String> disabledDependencyDetails
        ) {
            this.missingDependencies = Set.copyOf(
                    missingDependencies
            );

            this.disabledDependencies = Set.copyOf(
                    disabledDependencies
            );

            this.affectedPlugins = Set.copyOf(
                    affectedPlugins
            );

            this.missingDependencyDetails = List.copyOf(
                    missingDependencyDetails
            );

            this.disabledDependencyDetails = List.copyOf(
                    disabledDependencyDetails
            );
        }

        private boolean hasProblems() {
            return !missingDependencies.isEmpty()
                    || !disabledDependencies.isEmpty();
        }

        private Set<String> missingDependencies() {
            return missingDependencies;
        }

        private Set<String> disabledDependencies() {
            return disabledDependencies;
        }

        private Set<String> affectedPlugins() {
            return affectedPlugins;
        }

        private List<String> missingDependencyDetails() {
            return missingDependencyDetails;
        }

        private List<String> disabledDependencyDetails() {
            return disabledDependencyDetails;
        }
    }
}