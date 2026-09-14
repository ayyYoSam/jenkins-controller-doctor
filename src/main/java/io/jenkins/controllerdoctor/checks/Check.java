package io.jenkins.controllerdoctor.checks;

import io.jenkins.controllerdoctor.client.JenkinsClient;
import io.jenkins.controllerdoctor.model.JenkinsInfo;

/**
 * Contract implemented by every Jenkins controller health check.
 *
 * <p>Checks are intentionally small and independent. A check receives the
 * Jenkins client and the controller information required to perform its
 * inspection and returns a structured {@link CheckResult}.</p>
 */
public interface Check {

    /**
     * Returns the stable machine-readable identifier of this check.
     *
     * <p>The identifier is used by CLI filtering, reports, automation and
     * future configuration. It should therefore remain stable once
     * published.</p>
     *
     * @return stable check identifier
     */
    String id();

    /**
     * Returns the human-readable name of this check.
     *
     * @return display name
     */
    String name();

    /**
     * Executes the check against the Jenkins controller.
     *
     * @param client Jenkins API client
     * @param jenkinsInfo basic controller information
     * @return structured check result
     */
    CheckResult execute(JenkinsClient client, JenkinsInfo jenkinsInfo);
}