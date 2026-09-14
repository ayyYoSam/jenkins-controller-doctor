package io.jenkins.controllerdoctor.cli;

import io.jenkins.controllerdoctor.checks.Check;
import io.jenkins.controllerdoctor.checks.CheckResult;
import io.jenkins.controllerdoctor.checks.CheckStatus;
import io.jenkins.controllerdoctor.checks.core.JenkinsVersionCheck;
import io.jenkins.controllerdoctor.checks.core.SecurityConfigurationCheck;
import io.jenkins.controllerdoctor.checks.nodes.ExecutorCheck;
import io.jenkins.controllerdoctor.checks.nodes.NodeHealthCheck;
import io.jenkins.controllerdoctor.checks.nodes.OfflineNodeCheck;
import io.jenkins.controllerdoctor.checks.plugins.PluginDependencyCheck;
import io.jenkins.controllerdoctor.checks.plugins.PluginHealthCheck;
import io.jenkins.controllerdoctor.checks.plugins.PluginUpdateCheck;
import io.jenkins.controllerdoctor.checks.queue.QueueHealthCheck;
import io.jenkins.controllerdoctor.checks.storage.DiskSpaceCheck;
import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.model.HealthReport;
import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.report.ConsoleReporter;
import io.jenkins.controllerdoctor.report.JsonReporter;
import io.jenkins.controllerdoctor.report.MarkdownReporter;
import io.jenkins.controllerdoctor.report.ReportGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Executes the Jenkins Controller Doctor diagnostic workflow.
 *
 * <p>The command coordinates the Jenkins client, health checks and report
 * generation. It deliberately contains no HTTP implementation details:
 * transport concerns belong to {@link JenkinsClient}, while individual
 * diagnostics belong to {@link Check} implementations.</p>
 *
 * <p>The command supports two construction modes. Application code and tests
 * may inject a {@link JenkinsClient} directly, while Picocli may construct the
 * command as a subcommand and obtain the configured client from its parent
 * {@link CommandLine} command.</p>
 */
@Command(
        name = "doctor",
        description = "Diagnoses the health of a Jenkins controller.",
        mixinStandardHelpOptions = true,
        version = "Jenkins Controller Doctor"
)
public final class DoctorCommand implements Callable<Integer> {

    private static final String DEFAULT_FORMAT = "console";

    private final JenkinsClient client;
    private final List<Check> checks;

    @ParentCommand
    private CommandLine parentCommand;

    @Option(
            names = {"-f", "--format"},
            description = {
                    "Report format.",
                    "Supported values: console, json, markdown.",
                    "Default: ${DEFAULT-VALUE}"
            },
            defaultValue = DEFAULT_FORMAT
    )
    private String format;

    @Option(
            names = {"--fail-on"},
            description = {
                    "Exit with a failure code when the overall status is at",
                    "least the specified severity.",
                    "Values: INFO, WARNING, CRITICAL.",
                    "Default: ${DEFAULT-VALUE}"
            },
            defaultValue = "CRITICAL"
    )
    private String failOn;

    @Option(
            names = {"--skip-connection-test"},
            description = {
                    "Skip the initial lightweight Jenkins connection test."
            }
    )
    private boolean skipConnectionTest;

    /**
     * Constructor used by Picocli when this command is registered as a
     * subcommand.
     *
     * <p>The actual Jenkins client is obtained from the parent command during
     * execution. This keeps CLI configuration in {@link CommandLine} while
     * preserving dependency injection for tests and programmatic use.</p>
     */
    public DoctorCommand() {
        this.client = null;
        this.checks = defaultChecks();
    }

    /**
     * Creates a doctor command using the supplied Jenkins client.
     *
     * @param client Jenkins client used to query the controller
     */
    public DoctorCommand(JenkinsClient client) {
        this(client, defaultChecks());
    }

    /**
     * Creates a doctor command with an explicit check suite.
     *
     * <p>This constructor is particularly useful for tests and for future
     * command composition where a caller needs to control the diagnostic
     * checks that are executed.</p>
     *
     * @param client Jenkins client used to query the controller
     * @param checks checks executed by the command
     */
    public DoctorCommand(
            JenkinsClient client,
            List<Check> checks
    ) {
        this.client = Objects.requireNonNull(
                client,
                "client must not be null"
        );

        Objects.requireNonNull(
                checks,
                "checks must not be null"
        );

        if (checks.isEmpty()) {
            throw new IllegalArgumentException(
                    "checks must not be empty"
            );
        }

        this.checks = List.copyOf(checks);
    }

    /**
     * Executes the complete diagnostic workflow.
     *
     * @return process exit code
     */
    @Override
    public Integer call() {
        Instant startedAt = Instant.now();

        JenkinsInfo jenkinsInfo = null;
        List<CheckResult> results = new ArrayList<>();

        JenkinsClient effectiveClient;

        try {
            effectiveClient = resolveClient();

            if (!skipConnectionTest) {
                effectiveClient.testConnection();
            }

            jenkinsInfo = effectiveClient.getJenkinsInfo();

            for (Check check : checks) {
                results.add(
                        executeCheck(
                                effectiveClient,
                                check,
                                jenkinsInfo
                        )
                );
            }
        } catch (JenkinsClientException exception) {
            results.add(connectionFailureResult(exception));
        } catch (RuntimeException exception) {
            results.add(unexpectedFailureResult(exception));
        }

        Instant completedAt = Instant.now();

        HealthReport report = HealthReport.builder()
                .startedAt(startedAt)
                .completedAt(completedAt)
                .jenkinsInfo(jenkinsInfo)
                .results(results)
                .build();

        ReportGenerator reporter = createReporter(format);

        System.out.println(reporter.generate(report));

        return determineExitCode(report);
    }

    /**
     * Resolves the Jenkins client used by this command.
     *
     * <p>When the command was created programmatically, the injected client is
     * used. When Picocli created the command as a subcommand, the client is
     * created from the parent command's connection configuration.</p>
     *
     * @return Jenkins client used by the diagnostic workflow
     */
    private JenkinsClient resolveClient() {
        if (client != null) {
            return client;
        }

        if (parentCommand == null) {
            throw new IllegalStateException(
                    "No Jenkins client was configured and no parent command "
                            + "is available."
            );
        }

        return parentCommand.createClient();
    }

    /**
     * Executes one health check while isolating failures from the remaining
     * check suite.
     *
     * <p>A failure in one check must not prevent unrelated diagnostics from
     * running. The individual check is responsible for handling expected
     * Jenkins client failures and returning a {@link CheckResult}. This method
     * provides a defensive boundary around unexpected implementation failures.</p>
     *
     * @param effectiveClient Jenkins client used by the check
     * @param check health check to execute
     * @param jenkinsInfo controller information
     * @return result produced by the check
     */
    private CheckResult executeCheck(
            JenkinsClient effectiveClient,
            Check check,
            JenkinsInfo jenkinsInfo
    ) {
        Instant startedAt = Instant.now();

        try {
            return Objects.requireNonNull(
                    check.execute(
                            effectiveClient,
                            jenkinsInfo
                    ),
                    "check returned null result"
            );
        } catch (RuntimeException exception) {
            Instant completedAt = Instant.now();

            return CheckResult.builder()
                    .checkId(check.id())
                    .checkName(check.name())
                    .status(CheckStatus.CRITICAL)
                    .summary(
                            "The check failed unexpectedly."
                    )
                    .details(exceptionMessage(exception))
                    .recommendation(
                            "Review the diagnostic output and investigate "
                                    + "the failing check implementation."
                    )
                    .metadata(
                            "exception",
                            exception.getClass().getName()
                    )
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .build();
        }
    }

    /**
     * Creates a critical result when the Jenkins client cannot establish the
     * initial controller connection or retrieve controller information.
     *
     * @param exception client exception
     * @return diagnostic result describing the connection failure
     */
    private CheckResult connectionFailureResult(
            JenkinsClientException exception
    ) {
        Instant now = Instant.now();

        return CheckResult.builder()
                .checkId("controller.connection")
                .checkName("Controller connection")
                .status(CheckStatus.CRITICAL)
                .summary(
                        "The Jenkins controller could not be reached "
                                + "or queried."
                )
                .details(exceptionMessage(exception))
                .recommendation(
                        "Verify the Jenkins URL, credentials, network "
                                + "connectivity and API permissions."
                )
                .metadata(
                        "reason",
                        exception.reason().name()
                )
                .metadata(
                        "statusCode",
                        exception.statusCode()
                )
                .metadata(
                        "authenticationFailure",
                        exception.isAuthenticationFailure()
                )
                .metadata(
                        "authorizationFailure",
                        exception.isAuthorizationFailure()
                )
                .metadata(
                        "retryable",
                        exception.isRetryable()
                )
                .startedAt(now)
                .completedAt(now)
                .build();
    }

    /**
     * Creates a defensive critical result for failures outside the expected
     * Jenkins client exception hierarchy.
     *
     * @param exception unexpected runtime exception
     * @return diagnostic result describing the execution failure
     */
    private CheckResult unexpectedFailureResult(
            RuntimeException exception
    ) {
        Instant now = Instant.now();

        return CheckResult.builder()
                .checkId("doctor.execution")
                .checkName("Doctor execution")
                .status(CheckStatus.CRITICAL)
                .summary(
                        "The diagnostic execution failed unexpectedly."
                )
                .details(exceptionMessage(exception))
                .recommendation(
                        "Review the diagnostic output to identify "
                                + "the underlying failure."
                )
                .metadata(
                        "exception",
                        exception.getClass().getName()
                )
                .startedAt(now)
                .completedAt(now)
                .build();
    }

    /**
     * Creates the default diagnostic suite.
     *
     * <p>The order is intentionally deterministic so console, JSON and
     * Markdown reports are stable across executions.</p>
     *
     * @return default health-check suite
     */
    private static List<Check> defaultChecks() {
        return List.of(
                new JenkinsVersionCheck(),
                new SecurityConfigurationCheck(),

                new PluginHealthCheck(),
                new PluginUpdateCheck(),
                new PluginDependencyCheck(),

                new NodeHealthCheck(),
                new ExecutorCheck(),
                new OfflineNodeCheck(),

                new QueueHealthCheck(),

                new DiskSpaceCheck()
        );
    }

    /**
     * Creates the requested report generator.
     *
     * @param requestedFormat requested report format
     * @return configured report generator
     */
    private static ReportGenerator createReporter(
            String requestedFormat
    ) {
        String normalized = requestedFormat == null
                ? DEFAULT_FORMAT
                : requestedFormat.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "console" -> new ConsoleReporter();
            case "json" -> new JsonReporter();
            case "markdown", "md" -> new MarkdownReporter();
            default -> throw new IllegalArgumentException(
                    "Unsupported report format: "
                            + requestedFormat
                            + ". Supported formats: console, json, markdown."
            );
        };
    }

    /**
     * Converts the overall diagnostic status into a process exit code.
     *
     * <p>Exit code zero means that the configured failure threshold was not
     * reached. Non-zero values allow the command to be used directly in CI
     * pipelines and automation.</p>
     *
     * @param report completed health report
     * @return process exit code
     */
    private int determineExitCode(HealthReport report) {
        CheckStatus threshold = parseFailureThreshold(failOn);
        CheckStatus overall = report.overallStatus();

        if (overall.severity() >= threshold.severity()) {
            return switch (overall) {
                case PASS, INFO -> 0;
                case WARNING -> 2;
                case CRITICAL -> 3;
            };
        }

        return 0;
    }

    /**
     * Parses the configured failure threshold.
     *
     * @param value configured threshold
     * @return parsed threshold
     */
    private static CheckStatus parseFailureThreshold(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return CheckStatus.CRITICAL;
        }

        try {
            CheckStatus status = CheckStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );

            if (status == CheckStatus.PASS) {
                throw new IllegalArgumentException(
                        "PASS is not a valid --fail-on threshold"
                );
            }

            return status;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid --fail-on value: "
                            + value
                            + ". Supported values: INFO, WARNING, CRITICAL.",
                    exception
            );
        }
    }

    /**
     * Returns a useful exception message without exposing an empty message.
     *
     * @param exception exception to inspect
     * @return exception message or exception class name
     */
    private static String exceptionMessage(
            Exception exception
    ) {
        String message = exception.getMessage();

        if (message != null && !message.isBlank()) {
            return message;
        }

        return exception.getClass().getSimpleName();
    }
}