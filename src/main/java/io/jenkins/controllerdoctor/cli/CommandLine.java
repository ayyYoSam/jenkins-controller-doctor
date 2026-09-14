package io.jenkins.controllerdoctor.cli;

import io.jenkins.controllerdoctor.client.JenkinsConnection;
import io.jenkins.controllerdoctor.client.JenkinsHttpClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Root command for Jenkins Controller Doctor.
 *
 * <p>This class is responsible for configuring the Jenkins connection
 * and exposing the diagnostic command through Picocli.</p>
 *
 * <p>The root command owns connection-related configuration while
 * {@link DoctorCommand} owns the diagnostic workflow.</p>
 */
@Command(
        name = "jenkins-controller-doctor",
        description = "Diagnoses the health of a Jenkins controller.",
        mixinStandardHelpOptions = true,
        version = "Jenkins Controller Doctor",
        subcommands = {
                DoctorCommand.class
        }
)
public final class CommandLine
        implements Callable<Integer> {

    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 30;
    private static final long MAX_TIMEOUT_SECONDS = 300;

    @Option(
            names = {"-u", "--url"},
            description = "Jenkins controller URL.",
            required = true
    )
    private String url;

    @Option(
            names = {"-U", "--username"},
            description = "Jenkins username used for API authentication."
    )
    private String username;

    @Option(
            names = {"-t", "--api-token"},
            description = "Jenkins API token used for authentication.",
            interactive = true
    )
    private String apiToken;

    @Option(
            names = "--connect-timeout",
            description = {
                    "Connection timeout in seconds.",
                    "Default: ${DEFAULT-VALUE}"
            },
            defaultValue = "" + DEFAULT_CONNECT_TIMEOUT_SECONDS
    )
    private long connectTimeoutSeconds;

    @Option(
            names = "--timeout",
            description = {
                    "HTTP request timeout in seconds.",
                    "Default: ${DEFAULT-VALUE}"
            },
            defaultValue = "" + DEFAULT_REQUEST_TIMEOUT_SECONDS
    )
    private long requestTimeoutSeconds;

    /**
     * Creates the root command.
     */
    public CommandLine() {
    }

    /**
     * Executes the root command.
     *
     * <p>The root command itself does not perform diagnostics. The actual
     * diagnostic workflow is implemented by {@link DoctorCommand}.</p>
     *
     * @return process exit code
     */
    @Override
    public Integer call() {
        return 0;
    }

    /**
     * Creates a Jenkins client from the configured command-line options.
     *
     * <p>The returned client is intentionally read-only. It is responsible
     * for communicating with the Jenkins controller API and does not perform
     * any diagnostic logic itself.</p>
     *
     * @return configured Jenkins HTTP client
     * @throws IllegalArgumentException if the URL or timeout configuration
     *         is invalid
     */
    public JenkinsHttpClient createClient() {
        URI baseUri = parseBaseUri(url);

        validateTimeout(
                connectTimeoutSeconds,
                "connect-timeout"
        );

        validateTimeout(
                requestTimeoutSeconds,
                "timeout"
        );

        JenkinsConnection.Builder builder =
                JenkinsConnection.builder()
                        .baseUri(baseUri)
                        .connectTimeout(
                                Duration.ofSeconds(
                                        connectTimeoutSeconds
                                )
                        )
                        .requestTimeout(
                                Duration.ofSeconds(
                                        requestTimeoutSeconds
                                )
                        );

        configureAuthentication(builder);

        return new JenkinsHttpClient(
                builder.build()
        );
    }

    /**
     * Parses and validates the Jenkins controller URL.
     *
     * @param value configured URL
     * @return validated URI
     */
    private URI parseBaseUri(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Option --url must not be blank."
            );
        }

        final URI uri;

        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid Jenkins URL: " + value,
                    exception
            );
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(
                    "Jenkins URL must use HTTP or HTTPS: " + value
            );
        }

        if (uri.getHost() == null
                || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "Jenkins URL must contain a valid host: " + value
            );
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Jenkins URL must not contain embedded credentials."
            );
        }

        return uri;
    }

    /**
     * Configures optional API authentication.
     *
     * <p>Authentication is only enabled when both username and API token are
     * supplied. Partial credentials are rejected by {@link JenkinsConnection}
     * during construction.</p>
     *
     * @param builder Jenkins connection builder
     */
    private void configureAuthentication(
            JenkinsConnection.Builder builder
    ) {
        boolean hasUsername =
                username != null && !username.isBlank();

        boolean hasApiToken =
                apiToken != null && !apiToken.isBlank();

        if (hasUsername) {
            builder.username(username.trim());
        }

        if (hasApiToken) {
            builder.apiToken(apiToken);
        }
    }

    /**
     * Validates a timeout value.
     *
     * @param seconds timeout in seconds
     * @param optionName command-line option name
     */
    private void validateTimeout(
            long seconds,
            String optionName
    ) {
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    "Option --"
                            + optionName
                            + " must be greater than zero."
            );
        }

        if (seconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException(
                    "Option --"
                            + optionName
                            + " must not exceed "
                            + MAX_TIMEOUT_SECONDS
                            + " seconds."
            );
        }
    }
}