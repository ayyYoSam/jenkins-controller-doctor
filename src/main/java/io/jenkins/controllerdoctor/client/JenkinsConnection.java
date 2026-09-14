package io.jenkins.controllerdoctor.client;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration describing how to connect to a Jenkins controller.
 *
 * <p>The connection object contains only connection configuration. It does
 * not perform network operations and therefore can safely be passed between
 * the CLI, client and checks.</p>
 */
public final class JenkinsConnection {

    private static final Duration DEFAULT_CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration DEFAULT_REQUEST_TIMEOUT =
            Duration.ofSeconds(30);

    private static final int MAX_TIMEOUT_SECONDS = 300;

    private final URI baseUri;
    private final String username;
    private final String apiToken;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    private JenkinsConnection(Builder builder) {
        this.baseUri = normalizeBaseUri(builder.baseUri);
        this.username = normalizeOptional(builder.username);
        this.apiToken = normalizeOptional(builder.apiToken);
        this.connectTimeout = validateTimeout(
                builder.connectTimeout,
                "connectTimeout"
        );
        this.requestTimeout = validateTimeout(
                builder.requestTimeout,
                "requestTimeout"
        );

        validateAuthentication();
    }

    /**
     * Creates a new connection builder.
     *
     * @return connection builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the normalized Jenkins controller URI.
     *
     * @return controller URI
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Returns the configured username, if authentication is configured.
     *
     * @return username or {@code null}
     */
    public String username() {
        return username;
    }

    /**
     * Returns the configured API token, if authentication is configured.
     *
     * <p>The token should never be included in logs, reports or exception
     * messages.</p>
     *
     * @return API token or {@code null}
     */
    public String apiToken() {
        return apiToken;
    }

    /**
     * Returns the HTTP connection timeout.
     *
     * @return connection timeout
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Returns the HTTP request timeout.
     *
     * @return request timeout
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * Indicates whether HTTP authentication credentials are configured.
     *
     * @return {@code true} when both username and API token are present
     */
    public boolean hasAuthentication() {
        return username != null && apiToken != null;
    }

    /**
     * Returns a safe representation of the connection configuration.
     *
     * <p>Authentication credentials are intentionally omitted.</p>
     *
     * @return safe connection description
     */
    @Override
    public String toString() {
        return "JenkinsConnection{"
                + "baseUri=" + baseUri
                + ", username="
                + (username == null ? "<none>" : "<configured>")
                + ", apiToken="
                + (apiToken == null ? "<none>" : "<configured>")
                + ", connectTimeout=" + connectTimeout
                + ", requestTimeout=" + requestTimeout
                + '}';
    }

    private void validateAuthentication() {
        if ((username == null) != (apiToken == null)) {
            throw new IllegalArgumentException(
                    "username and apiToken must either both be configured "
                            + "or both be omitted"
            );
        }
    }

    private static URI normalizeBaseUri(URI uri) {
        Objects.requireNonNull(uri, "baseUri must not be null");

        String scheme = uri.getScheme();

        if (scheme == null) {
            throw new IllegalArgumentException(
                    "Jenkins URL must include a URI scheme"
            );
        }

        if (!scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(
                    "Jenkins URL must use HTTP or HTTPS"
            );
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "Jenkins URL must contain a valid host"
            );
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Jenkins URL must not contain embedded credentials"
            );
        }

        String value = uri.toString();

        while (value.endsWith("/") && value.length() > scheme.length() + 3) {
            value = value.substring(0, value.length() - 1);
        }

        return URI.create(value);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static Duration validateTimeout(
            Duration timeout,
            String fieldName
    ) {
        Objects.requireNonNull(timeout, fieldName + " must not be null");

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }

        if (timeout.getSeconds() > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed "
                            + MAX_TIMEOUT_SECONDS
                            + " seconds"
            );
        }

        return timeout;
    }

    /**
     * Builder for {@link JenkinsConnection}.
     */
    public static final class Builder {

        private URI baseUri;
        private String username;
        private String apiToken;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

        private Builder() {
        }

        public Builder baseUri(URI baseUri) {
            this.baseUri = baseUri;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "baseUrl must not be blank"
                );
            }

            this.baseUri = URI.create(baseUrl.trim());
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public JenkinsConnection build() {
            return new JenkinsConnection(this);
        }
    }
}