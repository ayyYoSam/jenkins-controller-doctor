package io.jenkins.controllerdoctor.checks;

/**
 * Represents the outcome severity of a controller health check.
 *
 * <p>The status is intentionally ordered from the least severe state
 * ({@link #PASS}) to the most severe state ({@link #CRITICAL}). This makes
 * it possible for report generators and the command-line interface to
 * determine an overall health state without knowing the implementation
 * details of individual checks.</p>
 */
public enum CheckStatus {

    /**
     * The check completed successfully and found no relevant issue.
     */
    PASS(0),

    /**
     * The check completed successfully and has information worth reporting,
     * but no action is required.
     */
    INFO(1),

    /**
     * The check identified a condition that may require investigation
     * or corrective action.
     */
    WARNING(2),

    /**
     * The check identified a serious controller condition that should be
     * addressed as soon as possible.
     */
    CRITICAL(3);

    private final int severity;

    CheckStatus(int severity) {
        this.severity = severity;
    }

    /**
     * Returns the numeric severity used to determine the worst status
     * across multiple checks.
     *
     * @return severity value
     */
    public int severity() {
        return severity;
    }

    /**
     * Determines whether this status is more severe than the supplied status.
     *
     * @param other status to compare against
     * @return {@code true} when this status is more severe
     */
    public boolean isMoreSevereThan(CheckStatus other) {
        if (other == null) {
            return true;
        }

        return severity > other.severity;
    }

    /**
     * Returns the most severe status between two values.
     *
     * @param first first status
     * @param second second status
     * @return the more severe status
     */
    public static CheckStatus worst(CheckStatus first, CheckStatus second) {
        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return first.isMoreSevereThan(second) ? first : second;
    }
}