package io.jenkins.controllerdoctor.report;

import io.jenkins.controllerdoctor.model.HealthReport;

/**
 * Defines the contract for rendering a Jenkins controller health report.
 *
 * <p>Report generators are intentionally separated from health checks so that
 * the same diagnostic results can be rendered in multiple formats without
 * coupling the checks to presentation concerns.</p>
 */
public interface ReportGenerator {

    /**
     * Generates a formatted representation of the supplied health report.
     *
     * @param report the completed health report
     * @return the rendered report
     * @throws IllegalArgumentException if {@code report} is {@code null}
     */
    String generate(HealthReport report);

    /**
     * Returns the format identifier used by this reporter.
     *
     * <p>The identifier is intended for CLI selection and future extension,
     * for example {@code console}, {@code json}, or {@code markdown}.</p>
     *
     * @return the report format identifier
     */
    String format();

    /**
     * Validates that the supplied report can be rendered.
     *
     * <p>Implementations may override this method when they require
     * format-specific validation. The default implementation only checks
     * that the report itself is not {@code null}.</p>
     *
     * @param report the report to validate
     * @throws IllegalArgumentException if {@code report} is {@code null}
     */
    default void validate(HealthReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
    }
}