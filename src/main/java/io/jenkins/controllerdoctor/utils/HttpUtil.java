package io.jenkins.controllerdoctor.utils;

import io.jenkins.controllerdoctor.client.JenkinsClientException;
import io.jenkins.controllerdoctor.client.JenkinsConnection;

import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Low-level HTTP utility used by the Jenkins client.
 *
 * <p>This class intentionally knows nothing about Jenkins domain objects.
 * Its responsibility is limited to performing safe HTTP GET requests and
 * returning the response body and metadata required by higher layers.</p>
 *
 * <p>The utility uses the JDK HTTP client rather than introducing another
 * HTTP stack. This keeps the runtime dependency surface small and gives the
 * application explicit control over redirects, timeouts and headers.</p>
 */
public final class HttpUtil {

    /**
     * Maximum response body accepted by the client.
     *
     * <p>Jenkins API responses should normally be relatively small for the
     * endpoints consumed by Controller Doctor. The limit protects the CLI
     * from unexpectedly large responses.</p>
     */
    public static final long DEFAULT_MAX_RESPONSE_BYTES =
            10L * 1024L * 1024L;

    private static final String ACCEPT_HEADER =
            "application/json";

    private static final String USER_AGENT =
            "jenkins-controller-doctor/0.1.0";

    private static final Set<Integer> SUCCESS_STATUS_CODES =
            Set.of(200);

    private final HttpClient httpClient;
    private final long maxResponseBytes;

    /**
     * Creates an HTTP utility using the default response limit.
     */
    public HttpUtil() {
        this(DEFAULT_MAX_RESPONSE_BYTES);
    }

    /**
     * Creates an HTTP utility with a custom maximum response size.
     *
     * @param maxResponseBytes maximum accepted response body size
     */
    public HttpUtil(long maxResponseBytes) {
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxResponseBytes must be greater than zero"
            );
        }

        this.maxResponseBytes = maxResponseBytes;

        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Creates an HTTP utility using a supplied HTTP client.
     *
     * <p>This constructor is useful for tests where the HTTP client can be
     * replaced with a controlled implementation.</p>
     *
     * @param httpClient HTTP client
     * @param maxResponseBytes maximum accepted response size
     */
    public HttpUtil(
            HttpClient httpClient,
            long maxResponseBytes
    ) {
        this.httpClient = Objects.requireNonNull(
                httpClient,
                "httpClient must not be null"
        );

        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxResponseBytes must be greater than zero"
            );
        }

        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * Performs an authenticated or unauthenticated HTTP GET request.
     *
     * @param connection Jenkins connection configuration
     * @param path path relative to the Jenkins base URL
     * @return HTTP response
     * @throws JenkinsClientException when the request fails
     */
    public Response get(
            JenkinsConnection connection,
            String path
    ) throws JenkinsClientException {

        Objects.requireNonNull(
                connection,
                "connection must not be null"
        );

        String normalizedPath = normalizePath(path);
        URI target = buildUri(connection.baseUri(), normalizedPath);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(target)
                .timeout(connection.requestTimeout())
                .header("Accept", ACCEPT_HEADER)
                .header("User-Agent", USER_AGENT)
                .GET();

        if (connection.hasAuthentication()) {
            requestBuilder.header(
                    "Authorization",
                    basicAuthentication(
                            connection.username(),
                            connection.apiToken()
                    )
            );
        }

        HttpRequest request = requestBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            validateResponseSize(response);

            if (!SUCCESS_STATUS_CODES.contains(response.statusCode())) {
                throw new JenkinsClientException(
                        response.statusCode(),
                        createHttpErrorMessage(response)
                );
            }

            return new Response(
                    response.statusCode(),
                    response.headers(),
                    response.body()
            );

        } catch (JenkinsClientException exception) {
            throw exception;

        } catch (IOException exception) {
            if (isTimeout(exception)) {
                throw new JenkinsClientException(
                        JenkinsClientException.Reason.TIMEOUT,
                        "Jenkins request timed out",
                        exception
                );
            }

            if (exception instanceof ConnectException) {
                throw new JenkinsClientException(
                        JenkinsClientException.Reason.CONNECTION,
                        "Unable to connect to Jenkins controller",
                        exception
                );
            }

            throw new JenkinsClientException(
                    JenkinsClientException.Reason.CONNECTION,
                    "HTTP request to Jenkins controller failed",
                    exception
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new JenkinsClientException(
                    JenkinsClientException.Reason.INTERRUPTED,
                    "Jenkins request was interrupted",
                    exception
            );
        }
    }

    /**
     * Returns the maximum accepted response body size.
     *
     * @return maximum response size in bytes
     */
    public long maxResponseBytes() {
        return maxResponseBytes;
    }

    private void validateResponseSize(
            HttpResponse<String> response
    ) throws JenkinsClientException {

        Optional<String> contentLength =
                response.headers().firstValue("Content-Length");

        if (contentLength.isEmpty()) {
            return;
        }

        try {
            long length = Long.parseLong(contentLength.get());

            if (length > maxResponseBytes) {
                throw new JenkinsClientException(
                        JenkinsClientException.Reason.CONNECTION,
                        "Jenkins response exceeds the configured "
                                + "maximum response size"
                );
            }

        } catch (NumberFormatException ignored) {
            /*
             * Invalid Content-Length is ignored here. The actual body is
             * still returned by HttpClient and JSON parsing will validate
             * its contents later.
             */
        }
    }

    private static String createHttpErrorMessage(
            HttpResponse<String> response
    ) {
        int status = response.statusCode();

        return switch (status) {
            case 401 ->
                    "Jenkins authentication failed (HTTP 401)";
            case 403 ->
                    "Jenkins authorization failed (HTTP 403)";
            case 404 ->
                    "Jenkins endpoint was not found (HTTP 404)";
            case 408 ->
                    "Jenkins request timed out (HTTP 408)";
            case 429 ->
                    "Jenkins rate-limited the request (HTTP 429)";
            default ->
                    "Jenkins returned HTTP status " + status;
        };
    }

    private static String basicAuthentication(
            String username,
            String apiToken
    ) {
        String credentials = username + ":" + apiToken;

        String encoded = Base64.getEncoder().encodeToString(
                credentials.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );

        return "Basic " + encoded;
    }

    private static URI buildUri(
            URI baseUri,
            String path
    ) {
        String base = baseUri.toString();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return URI.create(base + "/" + path);
    }

    private static String normalizePath(String path) {
        Objects.requireNonNull(path, "path must not be null");

        String normalized = path.trim();

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "path must not be blank"
            );
        }

        return normalized;
    }

    private static boolean isTimeout(IOException exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    /**
     * Immutable HTTP response representation.
     */
    public static final class Response {

        private final int statusCode;
        private final HttpHeaders headers;
        private final String body;

        private Response(
                int statusCode,
                HttpHeaders headers,
                String body
        ) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }

        public int statusCode() {
            return statusCode;
        }

        public HttpHeaders headers() {
            return headers;
        }

        public String body() {
            return body;
        }

        /**
         * Returns a response header.
         *
         * @param name header name
         * @return header value if present
         */
        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }

        /**
         * Returns the Jenkins version from the X-Jenkins header.
         *
         * @return Jenkins version if supplied by the controller
         */
        public Optional<String> jenkinsVersion() {
            return header("X-Jenkins");
        }
    }
}