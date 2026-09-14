package io.jenkins.controllerdoctor.client;

/**
 * Exception thrown when communication with a Jenkins controller fails.
 *
 * <p>The exception distinguishes between connection failures, HTTP failures
 * and response parsing failures so callers can provide useful diagnostics
 * without depending directly on the underlying HTTP implementation.</p>
 */
public class JenkinsClientException extends Exception {

    /**
     * Categories of client failures.
     */
    public enum Reason {

        /**
         * The controller could not be reached.
         */
        CONNECTION,

        /**
         * The controller returned an unsuccessful HTTP status.
         */
        HTTP,

        /**
         * The controller response could not be interpreted.
         */
        PARSING,

        /**
         * The request exceeded its configured timeout.
         */
        TIMEOUT,

        /**
         * The request was interrupted.
         */
        INTERRUPTED
    }

    private final Reason reason;
    private final int statusCode;

    /**
     * Creates a client exception without an HTTP status.
     *
     * @param reason failure reason
     * @param message human-readable message
     */
    public JenkinsClientException(
            Reason reason,
            String message
    ) {
        super(message);

        this.reason = reason;
        this.statusCode = -1;
    }

    /**
     * Creates a client exception with a cause.
     *
     * @param reason failure reason
     * @param message human-readable message
     * @param cause underlying cause
     */
    public JenkinsClientException(
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);

        this.reason = reason;
        this.statusCode = -1;
    }

    /**
     * Creates an HTTP failure.
     *
     * @param statusCode HTTP response status
     * @param message human-readable message
     */
    public JenkinsClientException(
            int statusCode,
            String message
    ) {
        super(message);

        this.reason = Reason.HTTP;
        this.statusCode = statusCode;
    }

    /**
     * Creates an HTTP failure with an underlying cause.
     *
     * @param statusCode HTTP response status
     * @param message human-readable message
     * @param cause underlying cause
     */
    public JenkinsClientException(
            int statusCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);

        this.reason = Reason.HTTP;
        this.statusCode = statusCode;
    }

    /**
     * Returns the failure category.
     *
     * @return failure reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return status code, or {@code -1} when not applicable
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Indicates whether the failure was caused by authentication.
     *
     * @return {@code true} for HTTP 401
     */
    public boolean isAuthenticationFailure() {
        return statusCode == 401;
    }

    /**
     * Indicates whether the failure was caused by insufficient permissions.
     *
     * @return {@code true} for HTTP 403
     */
    public boolean isAuthorizationFailure() {
        return statusCode == 403;
    }

    /**
     * Indicates whether the requested Jenkins resource was not found.
     *
     * @return {@code true} for HTTP 404
     */
    public boolean isNotFound() {
        return statusCode == 404;
    }

    /**
     * Indicates whether the operation can potentially succeed if retried.
     *
     * @return {@code true} for timeout or connection failures
     */
    public boolean isRetryable() {
        return reason == Reason.CONNECTION
                || reason == Reason.TIMEOUT;
    }
}