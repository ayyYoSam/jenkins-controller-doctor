package io.jenkins.controllerdoctor.client;

import io.jenkins.controllerdoctor.model.JenkinsInfo;
import io.jenkins.controllerdoctor.model.NodeInfo;
import io.jenkins.controllerdoctor.model.PluginInfo;
import io.jenkins.controllerdoctor.model.QueueItem;

import java.util.List;

/**
 * Client abstraction used by Jenkins Controller Doctor to retrieve
 * information from a Jenkins controller.
 *
 * <p>The interface deliberately exposes domain models instead of HTTP
 * responses. This keeps health checks independent from the underlying
 * transport and makes them straightforward to unit test.</p>
 */
public interface JenkinsClient {

    /**
     * Retrieves basic information about the Jenkins controller.
     *
     * @return controller information
     * @throws JenkinsClientException when the request fails
     */
    JenkinsInfo getJenkinsInfo() throws JenkinsClientException;

    /**
     * Retrieves installed plugins from the controller.
     *
     * @return installed plugins
     * @throws JenkinsClientException when the request fails
     */
    List<PluginInfo> getPlugins() throws JenkinsClientException;

    /**
     * Retrieves nodes configured in the controller.
     *
     * @return Jenkins nodes
     * @throws JenkinsClientException when the request fails
     */
    List<NodeInfo> getNodes() throws JenkinsClientException;

    /**
     * Retrieves items currently waiting in the Jenkins queue.
     *
     * @return queue items
     * @throws JenkinsClientException when the request fails
     */
    List<QueueItem> getQueueItems() throws JenkinsClientException;

    /**
     * Checks whether the configured controller can be reached and queried.
     *
     * <p>This method is intentionally lightweight and can be used before
     * executing the full check suite.</p>
     *
     * @throws JenkinsClientException when the controller cannot be queried
     */
    void testConnection() throws JenkinsClientException;
}